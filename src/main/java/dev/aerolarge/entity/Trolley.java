package dev.aerolarge.entity;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.compat.SableFlight;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The cabin crew's trolley: pushed down the aisle by walking into it, rolling to a stop with a squeak now and then,
 * and serving whoever uses it the next of its meals ("Chicken or pasta?"), pretzels or coffee while its stock lasts.
 * The galley restocks it (see {@link dev.aerolarge.block.GalleyBlock}). Sneak and hit it to fold it up and carry it.
 *
 * <p>Inside an assembled airliner's hull it stands among the airliner's blocks, in Sable's plot, and Sable carries it
 * along with them (see {@link SableFlight#keepWithCabin}): it rolls on the cabin floor in flight as on the ground.
 * Whoever pushes or hits it stands in the world, so their push is turned into the airliner's frame first.
 */
public class Trolley extends Entity {
    /** Servings a full trolley carries. */
    public static final int FULL = 24;
    /** Gravity, and what rolling leaves of its speed each tick, in blocks a tick. */
    private static final double GRAVITY = 0.04;
    private static final double ROLLING = 0.8;
    /** How hard a push from someone walking into it moves it, and a hit. */
    private static final double PUSH = 0.05;
    private static final double KNOCK = 0.3;
    /** Moving faster than this, in blocks a tick, it squeaks every so many ticks. */
    private static final double SQUEAK_SPEED = 0.02;
    private static final int SQUEAK_EVERY = 25;
    /** What it serves, in turn. */
    private static final List<Supplier<? extends Item>> MENU = List.of(
        AerolargeRegistry.CHICKEN_MEAL, AerolargeRegistry.AIRLINE_COFFEE, AerolargeRegistry.PASTA_MEAL, AerolargeRegistry.PRETZELS
    );

    private int stock = FULL;
    private int nextServing;

    public Trolley(EntityType<? extends Trolley> type, Level level) {
        super(type, level);
    }

    /** A full trolley standing at the point given. */
    public static Trolley at(Level level, Vec3 position, float yaw) {
        Trolley trolley = new Trolley(AerolargeRegistry.TROLLEY.get(), level);
        trolley.setPos(position);
        trolley.setYRot(yaw);
        return trolley;
    }

    public int stock() {
        return stock;
    }

    /** Fills it up again; whether it needed it. */
    public boolean restock() {
        if (stock >= FULL) {
            return false;
        }
        stock = FULL;
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    /** The yaw of something facing the given way across the floor. */
    public static float yaw(Vec3 facing) {
        return (float) Math.toDegrees(Math.atan2(-facing.x, facing.z));
    }

    /**
     * A look in the world, the way it points across the floor at a position, however short: level in the world first,
     * then turned into the frame of an airliner the position is among the blocks of, and level with its floor.
     * Levelled the other way round, a look steeply down at a banked airliner's floor would swing round with the bank.
     */
    public static Vec3 across(Level level, BlockPos at, Vec3 look) {
        return SableFlight.turnToPlot(level, at, look.multiply(1, 0, 1)).multiply(1, 0, 1);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            SableFlight.keepWithCabin(this);
        }
        Vec3 velocity = getDeltaMovement().add(0, -GRAVITY, 0);
        move(MoverType.SELF, velocity);
        setDeltaMovement(onGround() ? new Vec3(velocity.x * ROLLING, Math.max(velocity.y, 0), velocity.z * ROLLING) : velocity);
        if (!level().isClientSide && tickCount % SQUEAK_EVERY == 0 && getDeltaMovement().horizontalDistance() > SQUEAK_SPEED) {
            level().playSound(null, getX(), getY(), getZ(), AerolargeRegistry.TROLLEY_SQUEAK.get(), SoundSource.NEUTRAL, 0.6F, 0.9F + random.nextFloat() * 0.3F);
        }
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    /** Walked into, it rolls away from whoever pushed it. */
    @Override
    public void push(Entity by) {
        if (by.isPassengerOfSameVehicle(this)) {
            return;
        }
        Vec3 away = position().subtract(SableFlight.toPlot(level(), blockPosition(), by.position(), false)).multiply(1, 0, 1);
        if (away.lengthSqr() > 1e-4) {
            setDeltaMovement(getDeltaMovement().add(away.normalize().scale(PUSH)));
            setYRot(yaw(away));
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (stock <= 0) {
            player.displayClientMessage(Component.translatable("message.aerolarge.trolley.empty"), true);
            return InteractionResult.SUCCESS;
        }
        ItemStack serving = new ItemStack(MENU.get(nextServing).get());
        nextServing = (nextServing + 1) % MENU.size();
        stock--;
        // Named before it is handed over: in the inventory the stack is used up.
        Component name = serving.getHoverName();
        player.getInventory().placeItemBackInInventory(serving);
        player.displayClientMessage(Component.translatable("message.aerolarge.trolley.served", name, stock), true);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.5F, 1.2F);
        return InteractionResult.SUCCESS;
    }

    /**
     * Hit, it is knocked on its way; hit while sneaking, it folds up into the hitter's hands (at their feet if they have
     * no room), not onto the floor: an item dropped among an airliner's blocks is put out into the world without the
     * airliner's speed, and in flight would be left behind.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || !(source.getEntity() instanceof Player player)) {
            return false;
        }
        if (player.isShiftKeyDown()) {
            if (!player.getAbilities().instabuild) {
                player.getInventory().placeItemBackInInventory(new ItemStack(AerolargeRegistry.TROLLEY_ITEM.get()));
            }
            discard();
        } else {
            setDeltaMovement(getDeltaMovement().add(across(level(), blockPosition(), player.getLookAngle()).normalize().scale(KNOCK)));
        }
        return true;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Stock", stock);
        tag.putInt("NextServing", nextServing);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        stock = tag.contains("Stock") ? tag.getInt("Stock") : FULL;
        nextServing = Math.floorMod(tag.getInt("NextServing"), MENU.size());
    }

    /** Picked, its item. */
    @Override
    public ItemStack getPickResult() {
        return new ItemStack(AerolargeRegistry.TROLLEY_ITEM.get());
    }
}
