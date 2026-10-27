package dev.aerolarge.entity;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.FirstFlight;
import dev.aerolarge.block.CargoCrateBlock;
import dev.aerolarge.block.SignalFlareBlockEntity;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A care package on its way down: shoved off, it slides on until it leaves the floor it stood on (out of the cargo
 * door, over the edge), falls free for a moment, then its parachute opens and it floats down, drifting to a stop
 * sideways, and lands as the crate it was, its contents and all. Landing, it says how far from the nearest
 * burning signal flare it came down, to everyone nearby and to whoever dropped it. Anything else it carries whose item
 * knows how to land (see {@link Landing}) lands as that item says instead, as a zeppelin's mail sack does.
 */
public class FallingCrate extends Entity {
    private static final EntityDataAccessor<ItemStack> CRATE = SynchedEntityData.defineId(FallingCrate.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> CHUTE = SynchedEntityData.defineId(FallingCrate.class, EntityDataSerializers.BOOLEAN);
    /** Ticks of free fall, from leaving the floor, before the parachute opens. */
    private static final int FREE_FALL = 25;
    /** Gravity, and what the air leaves of its speed each tick falling free, in blocks a tick. */
    private static final double GRAVITY = 0.04;
    private static final double AIR_DRAG = 0.98;
    /** Under the parachute: the sink it settles to, how quickly it gets there, and what is left of its drift each tick. */
    private static final double SINK = 0.12;
    private static final double BRAKING = 0.2;
    private static final double DRIFT = 0.95;
    /** What sliding along a floor leaves of its speed each tick: a shove carries it a few blocks. */
    private static final double FLOOR_FRICTION = 0.85;
    /** A flare this near, across the ground, counts; players this near the landing hear of it. */
    private static final double FLARE_REACH = 128;
    private static final double AUDIENCE = 128;
    /** Landing this close to a flare, or closer, it is a special delivery for whoever dropped it. */
    private static final double ON_TARGET = 10;
    /** How far up from where it stops it looks for room to stand as a crate. */
    private static final int ROOM_ABOVE = 4;

    @Nullable
    private UUID dropper;
    /** Ticks since it left the floor it was shoved along, none before; only after that does touching down count as landing. */
    private int falling;

    public FallingCrate(EntityType<? extends FallingCrate> type, Level level) {
        super(type, level);
    }

    /** An item that, carried down on a parachute, lands its own way rather than as a crate. */
    public interface Landing {
        /** Lands what it carried where it came to rest, on the server, dropped by whoever is given. */
        void land(ServerLevel level, Vec3 at, ItemStack carried, @Nullable UUID dropper);
    }

    /** A crate falling from the point given, moving as given (blocks a tick), dropped by whoever is given. */
    public static FallingCrate of(Level level, Vec3 at, Vec3 velocity, ItemStack crate, @Nullable UUID dropper) {
        FallingCrate falling = new FallingCrate(AerolargeRegistry.FALLING_CRATE.get(), level);
        falling.setPos(at);
        falling.setDeltaMovement(velocity);
        falling.entityData.set(CRATE, crate.copy());
        falling.dropper = dropper;
        return falling;
    }

    /** The crate it carries, for its renderer. */
    public ItemStack crate() {
        return entityData.get(CRATE);
    }

    public boolean chuteOpen() {
        return entityData.get(CHUTE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(CRATE, ItemStack.EMPTY);
        builder.define(CHUTE, false);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 velocity = getDeltaMovement();
        if (chuteOpen()) {
            double down = velocity.y - GRAVITY;
            down += (-SINK - down) * BRAKING;
            velocity = new Vec3(velocity.x * DRIFT, Math.max(down, velocity.y - GRAVITY), velocity.z * DRIFT);
        } else {
            velocity = velocity.add(0, -GRAVITY, 0).scale(AIR_DRAG);
        }
        move(MoverType.SELF, velocity);
        // Standing on something, it slides along it, slowing, rather than gathering speed into it.
        setDeltaMovement(onGround() ? new Vec3(velocity.x * FLOOR_FRICTION, Math.max(velocity.y, 0), velocity.z * FLOOR_FRICTION) : velocity);
        if (level() instanceof ServerLevel level) {
            if (falling > 0 || !onGround()) {
                falling++;
            }
            if (!chuteOpen() && falling >= FREE_FALL) {
                entityData.set(CHUTE, true);
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_ELYTRA.value(), SoundSource.NEUTRAL, 1.0F, 0.7F);
            }
            if (falling > 1 && (onGround() || isInWater())) {
                land(level);
            }
        }
    }

    /** Down: stands where it came to rest as the crate it was, or as its item if there is no room, and says where. */
    private void land(ServerLevel level) {
        ItemStack crate = crate();
        if (crate.getItem() instanceof Landing landing) {
            landing.land(level, position(), crate, dropper);
            discard();
            return;
        }
        BlockPos at = blockPosition();
        for (int i = 0; i < ROOM_ABOVE && !level.getBlockState(at).canBeReplaced(); i++) {
            at = at.above();
        }
        if (crate.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof CargoCrateBlock
            && level.getBlockState(at).canBeReplaced()) {
            level.setBlock(at, blockItem.getBlock().defaultBlockState(), Block.UPDATE_ALL);
            BlockEntity placed = level.getBlockEntity(at);
            if (placed != null) {
                placed.applyComponentsFromItemStack(crate);
                placed.setChanged();
            }
        } else {
            spawnAtLocation(crate);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
        report(level);
        discard();
    }

    /** Tells everyone near the landing, and whoever dropped it, how near the nearest flare it came down. */
    private void report(ServerLevel level) {
        double nearest = FLARE_REACH;
        for (SignalFlareBlockEntity flare : SignalFlareBlockEntity.all(level)) {
            BlockPos pos = flare.getBlockPos();
            nearest = Math.min(nearest, Math.hypot(getX() - (pos.getX() + 0.5), getZ() - (pos.getZ() + 0.5)));
        }
        Component message = nearest < FLARE_REACH
            ? Component.translatable("message.aerolarge.cargo_crate.landed_near", String.format(Locale.ROOT, "%.1f", nearest))
            : Component.translatable("message.aerolarge.cargo_crate.landed");
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(this) < AUDIENCE * AUDIENCE || player.getUUID().equals(dropper)) {
                player.sendSystemMessage(message);
            }
            if (nearest <= ON_TARGET && player.getUUID().equals(dropper)) {
                FirstFlight.SPECIAL_DELIVERY.award(player);
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (!crate().isEmpty()) {
            tag.put("Crate", crate().save(registryAccess()));
        }
        tag.putBoolean("Chute", chuteOpen());
        tag.putInt("Falling", falling);
        if (dropper != null) {
            tag.putUUID("Dropper", dropper);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(CRATE, ItemStack.parseOptional(registryAccess(), tag.getCompound("Crate")));
        entityData.set(CHUTE, tag.getBoolean("Chute"));
        falling = tag.getInt("Falling");
        dropper = tag.hasUUID("Dropper") ? tag.getUUID("Dropper") : null;
    }
}
