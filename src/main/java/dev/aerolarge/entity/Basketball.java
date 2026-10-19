package dev.aerolarge.entity;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.HoopBlock;
import dev.aerolarge.compat.SableFlight;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A basketball, loose: thrown from the hand, it flies, bounces off the floor and walls losing a little each time,
 * and rolls to a stop. Dropping down through a hoop scores (see {@link HoopBlock}). Walk over it or use it to pick it
 * up; hit it to knock it on its way. It moves the same on both sides, so it bounces smoothly between updates.
 * Inside an airliner in flight the cabin's air moves along with it, so the ball is thrown, slowed and bounced relative
 * to the cabin: a ball thrown in flight keeps up with the airliner instead of flying off to the back.
 */
public class Basketball extends Entity implements ItemSupplier {
    /** Speed a throw leaves the hand at (a soft one, sneaking), and a knock gives it, in blocks a tick, and the lift a knock adds. */
    private static final double THROW = 0.9;
    private static final double SOFT_THROW = 0.6;
    private static final double KNOCK = 0.55;
    private static final double KNOCK_LIFT = 0.25;
    /** Gravity, in blocks a tick each tick, and what the air leaves of its speed each tick. */
    private static final double GRAVITY = 0.05;
    private static final double AIR_DRAG = 0.99;
    /** What a bounce leaves of the speed into the floor or ceiling, and into a wall. */
    private static final double BOUNCE = 0.75;
    private static final double WALL_BOUNCE = 0.6;
    /** Slower than this into the floor, in blocks a tick, and it stops bouncing and rolls; what rolling leaves each tick. */
    private static final double MIN_BOUNCE = 0.1;
    private static final double ROLLING = 0.92;
    /** Ticks after a throw before it can be picked up, so it leaves the hand. */
    private static final int PICKUP_DELAY = 15;
    /** Ticks after a basket before the next can count, so one drop through the ring scores once. */
    private static final int SCORED_COOLDOWN = 20;

    @Nullable
    private UUID thrower;
    private int pickupDelay;
    private int scoredCooldown;

    public Basketball(EntityType<? extends Basketball> type, Level level) {
        super(type, level);
    }

    /** A ball thrown by the player, the way they look, from just in front of their eyes; softly if they sneak. */
    public static Basketball thrown(Level level, Player player) {
        Basketball ball = new Basketball(AerolargeRegistry.BASKETBALL.get(), level);
        Vec3 look = player.getLookAngle();
        ball.setPos(player.getX() + look.x * 0.5, player.getEyeY() - 0.2 + look.y * 0.5, player.getZ() + look.z * 0.5);
        Vec3 carried = player.getDeltaMovement();
        ball.setDeltaMovement(look.scale(player.isShiftKeyDown() ? SOFT_THROW : THROW).add(carried.x, player.onGround() ? 0 : carried.y, carried.z)
            .add(ball.cabin()));
        ball.thrower = player.getUUID();
        ball.pickupDelay = PICKUP_DELAY;
        return ball;
    }

    /** Who threw or last knocked it, if they are still about. */
    @Nullable
    public Player thrower() {
        return thrower == null ? null : level().getPlayerByUUID(thrower);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        pickupDelay = Math.max(0, pickupDelay - 1);
        scoredCooldown = Math.max(0, scoredCooldown - 1);
        Vec3 cabin = cabin();
        Vec3 velocity = getDeltaMovement().add(0, -GRAVITY, 0);
        Vec3 from = position();
        move(MoverType.SELF, velocity);
        Vec3 moved = position().subtract(from);
        // Each way the move came up short, it hit something: bounce back off it, as it moves relative to the cabin.
        Vec3 relative = velocity.subtract(cabin);
        double x = Math.abs(moved.x - velocity.x) > 1e-4 ? -relative.x * WALL_BOUNCE : relative.x;
        double z = Math.abs(moved.z - velocity.z) > 1e-4 ? -relative.z * WALL_BOUNCE : relative.z;
        double y = relative.y;
        if (Math.abs(moved.y - velocity.y) > 1e-4) {
            y = Math.abs(relative.y) > MIN_BOUNCE ? -relative.y * BOUNCE : 0;
            if (y != 0 && !level().isClientSide) {
                level().playSound(null, getX(), getY(), getZ(), AerolargeRegistry.BALL_BOUNCE.get(), SoundSource.NEUTRAL,
                    (float) Math.min(1, Math.abs(relative.y) * 2), 0.9F + random.nextFloat() * 0.2F);
            }
        }
        double keep = onGround() && y == 0 ? ROLLING : AIR_DRAG;
        setDeltaMovement(cabin.add(x * keep, y, z * keep));
        if (level() instanceof ServerLevel server && scoredCooldown == 0 && position().y < from.y) {
            Vec3 middle = new Vec3(0, getBbHeight() / 2, 0);
            dropThrough(server, from.add(middle), position().add(middle));
        }
    }

    /** Scores if the step its middle just took dropped it through a hoop's ring, in the world or aboard an airliner. */
    private void dropThrough(ServerLevel level, Vec3 from, Vec3 to) {
        for (Vec3 point : new Vec3[]{to, from}) {
            BlockPos hoop = SableFlight.find(level, point, state -> state.getBlock() instanceof HoopBlock);
            if (hoop != null && HoopBlock.dropsThrough(hoop, SableFlight.toPlot(level, hoop, from, true), SableFlight.toPlot(level, hoop, to, false))) {
                scoredCooldown = SCORED_COOLDOWN;
                HoopBlock.score(level, hoop, thrower());
                return;
            }
        }
    }

    @Override
    public void playerTouch(Player player) {
        if (!level().isClientSide && pickupDelay == 0) {
            pickUp(player);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!level().isClientSide) {
            pickUp(player);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    private void pickUp(Player player) {
        if (isAlive() && player.getInventory().add(getItem())) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.2F);
            discard();
        }
    }

    /** Hit, it is knocked the way the hitter looks, and counts as theirs. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || !(source.getEntity() instanceof Player player)) {
            return false;
        }
        setDeltaMovement(player.getLookAngle().scale(KNOCK).add(0, KNOCK_LIFT, 0).add(cabin()));
        hurtMarked = true;
        thrower = player.getUUID();
        level().playSound(null, getX(), getY(), getZ(), AerolargeRegistry.BALL_BOUNCE.get(), SoundSource.NEUTRAL, 0.8F, 1.1F);
        return true;
    }

    /** How fast the cabin around the ball moves, in blocks a tick: an airliner's in flight, still anywhere else. */
    private Vec3 cabin() {
        return SableFlight.cabinVelocity(level(), position()).scale(1.0 / SharedConstants.TICKS_PER_SECOND);
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(AerolargeRegistry.BASKETBALL_ITEM.get());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (thrower != null) {
            tag.putUUID("Thrower", thrower);
        }
        tag.putInt("PickupDelay", pickupDelay);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        thrower = tag.hasUUID("Thrower") ? tag.getUUID("Thrower") : null;
        pickupDelay = tag.getInt("PickupDelay");
    }
}
