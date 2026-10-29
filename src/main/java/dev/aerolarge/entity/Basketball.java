package dev.aerolarge.entity;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.HoopBlock;
import dev.aerolarge.compat.SableFlight;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 *
 * <p>Inside an assembled airliner's hull the cabin's air moves along with the airliner, so the ball lives among the
 * airliner's blocks, in Sable's plot, and Sable carries it along with them (see {@link SableFlight#keepWithCabin}): it
 * is thrown, slowed and bounced in the airliner's frame, off the cabin's blocks as they are, and a ball thrown in flight
 * keeps up with the airliner instead of flying off to the back. Whoever throws or hits it stands in the world, so the
 * throw is turned into the airliner's frame. Out of the hull it is sent into the world at the airliner's speed.
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
    /**
     * A throw aimed this near a hoop, in degrees either side across the floor, from at most this far across it, in
     * blocks, is helped into an arc that drops through the ring (see {@link #aimForHoop}); and how far up and down from
     * the ball a hoop is looked for.
     */
    private static final double ASSIST_CONE = 15;
    private static final int ASSIST_RANGE = 20;
    private static final int ASSIST_REACH_UP = 10;
    /**
     * How high over the higher of the ball and the ring the helped arc peaks, in blocks, and how far under the ceiling
     * at most: room for the ball's top half and a little more, so a hoop hung from a ceiling still has an arc into it.
     */
    private static final double ARC_RISE = 1.5;
    private static final double CEILING_ROOM = 0.3;

    @Nullable
    private UUID thrower;
    private int pickupDelay;
    private int scoredCooldown;

    public Basketball(EntityType<? extends Basketball> type, Level level) {
        super(type, level);
    }

    /**
     * A ball thrown by the player, the way they look, from just in front of their eyes; softly if they sneak. Thrown
     * inside an assembled airliner's hull, it starts among the airliner's blocks, the throw relative to the cabin. A
     * full throw aimed near a hoop is helped through it (see {@link #aimForHoop}); a soft one is left as thrown, for
     * those who would rather aim for themselves.
     */
    public static Basketball thrown(Level level, Player player) {
        Basketball ball = new Basketball(AerolargeRegistry.BASKETBALL.get(), level);
        Vec3 look = player.getLookAngle();
        ball.setPos(player.getX() + look.x * 0.5, player.getEyeY() - 0.2 + look.y * 0.5, player.getZ() + look.z * 0.5);
        Vec3 carried = player.getDeltaMovement();
        ball.setDeltaMovement(look.scale(player.isShiftKeyDown() ? SOFT_THROW : THROW).add(carried.x, player.onGround() ? 0 : carried.y, carried.z));
        ball.thrower = player.getUUID();
        ball.pickupDelay = PICKUP_DELAY;
        SableFlight.takeAboard(ball);
        if (!player.isShiftKeyDown()) {
            ball.aimForHoop();
        }
        return ball;
    }

    /**
     * Helps a throw through a hoop, as games do for a shot that is near enough: if a hoop is ahead, within
     * {@link #ASSIST_CONE} of the way the ball goes and {@link #ASSIST_RANGE} of it across the floor, the ball is sent
     * on an arc that peaks {@link #ARC_RISE} over the higher of it and the ring (kept under the ceiling) and drops its
     * middle through the middle of the ring. Worked out in the ball's own frame, among an airliner's blocks or in the
     * world, as the hoops it can drop through are.
     */
    private void aimForHoop() {
        Vec3 middle = position().add(0, getBbHeight() / 2, 0);
        BlockPos hoop = hoopAhead(middle, getDeltaMovement());
        if (hoop == null) {
            return;
        }
        Vec3 ring = HoopBlock.ringMiddle(level(), hoop);
        double peak = Math.min(Math.max(middle.y, ring.y) + ARC_RISE, ceilingOver(middle.lerp(ring, 0.5)) - CEILING_ROOM);
        if (peak <= ring.y || peak <= middle.y) {
            return;
        }
        // Each tick the ball falls GRAVITY faster, then moves: after t ticks it has risen t * up - GRAVITY * t (t + 1) / 2.
        double up = Math.sqrt(2 * GRAVITY * (peak - middle.y));
        double b = GRAVITY / 2 - up;
        double room = b * b - 2 * GRAVITY * (ring.y - middle.y);
        if (room <= 0) {
            // Under a ceiling this low no arc comes down through the ring: the throw goes as thrown.
            return;
        }
        double ticks = (-b + Math.sqrt(room)) / GRAVITY;
        // Across the floor the air leaves AIR_DRAG of its speed each tick: in t ticks it goes (1 - AIR_DRAG^t) / (1 - AIR_DRAG) of it.
        Vec3 across = new Vec3(ring.x - middle.x, 0, ring.z - middle.z);
        double speed = across.length() * (1 - AIR_DRAG) / (1 - Math.pow(AIR_DRAG, ticks));
        setDeltaMovement(across.normalize().scale(speed).add(0, up, 0));
    }

    /** The nearest hoop ahead of a ball thrown this way, within the assist's reach and cone; null if none. */
    @Nullable
    private BlockPos hoopAhead(Vec3 middle, Vec3 throwing) {
        Vec3 way = new Vec3(throwing.x, 0, throwing.z);
        if (way.lengthSqr() < 1e-6) {
            return null;
        }
        double leastCos = Math.cos(Math.toRadians(ASSIST_CONE));
        BlockPos centre = BlockPos.containing(middle);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-ASSIST_RANGE, -ASSIST_REACH_UP, -ASSIST_RANGE),
            centre.offset(ASSIST_RANGE, ASSIST_REACH_UP, ASSIST_RANGE))) {
            if (!(level().getBlockState(pos).getBlock() instanceof HoopBlock)) {
                continue;
            }
            Vec3 to = HoopBlock.ringMiddle(level(), pos).subtract(middle).multiply(1, 0, 1);
            double distance = to.length();
            if (distance <= ASSIST_RANGE && distance < bestDistance && to.normalize().dot(way.normalize()) >= leastCos) {
                best = pos.immutable();
                bestDistance = distance;
            }
        }
        return best;
    }

    /** The underside of the first solid block over a point, or a long way up if there is none near. */
    private double ceilingOver(Vec3 point) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(point).mutable();
        for (int up = 0; up <= ASSIST_REACH_UP * 2; up++, pos.move(Direction.UP)) {
            if (!level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()) {
                return pos.getY();
            }
        }
        return Double.MAX_VALUE;
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
        if (!level().isClientSide) {
            SableFlight.keepWithCabin(this);
        }
        pickupDelay = Math.max(0, pickupDelay - 1);
        scoredCooldown = Math.max(0, scoredCooldown - 1);
        Vec3 velocity = getDeltaMovement().add(0, -GRAVITY, 0);
        Vec3 from = position();
        move(MoverType.SELF, velocity);
        Vec3 moved = position().subtract(from);
        // Each way the move came up short, it hit something: bounce back off it.
        double x = Math.abs(moved.x - velocity.x) > 1e-4 ? -velocity.x * WALL_BOUNCE : velocity.x;
        double z = Math.abs(moved.z - velocity.z) > 1e-4 ? -velocity.z * WALL_BOUNCE : velocity.z;
        double y = velocity.y;
        if (Math.abs(moved.y - velocity.y) > 1e-4) {
            y = Math.abs(velocity.y) > MIN_BOUNCE ? -velocity.y * BOUNCE : 0;
            if (y != 0 && !level().isClientSide) {
                level().playSound(null, getX(), getY(), getZ(), AerolargeRegistry.BALL_BOUNCE.get(), SoundSource.NEUTRAL,
                    (float) Math.min(1, Math.abs(velocity.y) * 2), 0.9F + random.nextFloat() * 0.2F);
            }
        }
        double keep = onGround() && y == 0 ? ROLLING : AIR_DRAG;
        setDeltaMovement(new Vec3(x * keep, y, z * keep));
        if (level() instanceof ServerLevel server && scoredCooldown == 0 && position().y < from.y) {
            Vec3 middle = new Vec3(0, getBbHeight() / 2, 0);
            dropThrough(server, from.add(middle), position().add(middle));
        }
    }

    /**
     * Scores if the step its middle just took dropped it through a hoop's ring, in the world or aboard an airliner. A
     * ball among an airliner's blocks is in the frame of the hoops there already; a ball in the world that drops
     * through a hoop on anything Sable flies is seen in that hoop's frame, as it lay a tick ago and lies now.
     */
    private void dropThrough(ServerLevel level, Vec3 from, Vec3 to) {
        for (Vec3 point : new Vec3[]{to, from}) {
            BlockPos hoop = SableFlight.find(level, point, state -> state.getBlock() instanceof HoopBlock);
            if (hoop != null && HoopBlock.dropsThrough(level, hoop, SableFlight.toPlot(level, hoop, from, true), SableFlight.toPlot(level, hoop, to, false))) {
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
        setDeltaMovement(SableFlight.turnToPlot(level(), blockPosition(), player.getLookAngle()).scale(KNOCK).add(0, KNOCK_LIFT, 0));
        hurtMarked = true;
        thrower = player.getUUID();
        level().playSound(null, getX(), getY(), getZ(), AerolargeRegistry.BALL_BOUNCE.get(), SoundSource.NEUTRAL, 0.8F, 1.1F);
        return true;
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
