package dev.skyliners.bowling;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.compat.SableFlight;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A bowling ball, rolling: bowled from the hand along the floor the way the bowler looks, it rolls on and on, as a
 * heavy ball on a polished lane does, knocking down every bowling pin it rolls into (see {@link BowlingPinBlock}),
 * each taking a little of its speed. Once it has stopped, or after it has rolled a long while, it goes back down the
 * nearest ball return's track (see {@link BallReturnBlock}), vanishing with a rumble; with none near, it stays where it
 * stopped, to be picked up by walking over it or using it. It moves the same on both sides, so it rolls smoothly
 * between updates.
 *
 * <p>Bowled inside an assembled ship's hull, it lives among her blocks in Sable's plot, as the basketball does, so it
 * rolls down her lane in her frame as she flies, and the pins it meets are her blocks (see
 * {@link SableFlight#keepWithCabin}).
 */
public class BowlingBall extends Entity implements ItemSupplier {
    /** Speed a bowl leaves the hand at, and a gentle one, sneaking, in blocks a tick. */
    private static final double BOWL = 0.55;
    private static final double GENTLE_BOWL = 0.35;
    /** How far ahead of the bowler's feet it is let go, and how high over them, in blocks. */
    private static final double LET_GO_AHEAD = 0.7;
    private static final double LET_GO_UP = 0.1;
    /** Gravity, in blocks a tick each tick; what rolling leaves of its speed each tick, and the air. */
    private static final double GRAVITY = 0.06;
    private static final double ROLLING = 0.995;
    private static final double AIR_DRAG = 0.98;
    /**
     * What a wall leaves of the speed into it, and what rolling leaves of its speed each tick once it has hit one: a
     * ball in the pit at the lane's end, or in the gutter, thuds against its wall and comes to rest there rather than
     * rolling all the way back up the lane.
     */
    private static final double WALL_BOUNCE = 0.2;
    private static final double ROLLING_SPENT = 0.8;
    /** What each pin it knocks down leaves of its speed. */
    private static final double PIN_DRAG = 0.94;
    /** How far along its way each tick it looks for pins it touched, at most, in blocks: well under a pin's width. */
    private static final double SWEEP_STEP = 0.1;
    /** How high a ledge it rolls up onto, in blocks: a lane's boards over the floor round it. */
    private static final float STEP = 0.25F;
    /** How far a move may come up short of what was asked, in blocks, and not have hit anything. */
    private static final double SHORT = 1e-4;
    /** Slower than this, in blocks a tick, it has stopped. */
    private static final double STOPPED = 0.02;
    /**
     * How long it lies stopped, and how long it may roll at most, before it first looks for a ball return to go back
     * down; how often it looks again while none is near, so one put up later still takes it; and how long it lies loose
     * at most with none near before it is left as a dropped ball, to be picked up or to go as dropped things do, so a
     * lane with no return gathers no balls for good; in ticks.
     */
    private static final int RETURN_AFTER = 30;
    private static final int LONGEST_ROLL = 400;
    private static final int LOOK_AGAIN = 100;
    private static final int LONGEST_LOOSE = 6000;
    /** How far round it a ball return is looked for, in blocks across and up or down. */
    private static final int RETURN_REACH = 32;
    private static final int RETURN_REACH_UP = 3;
    /** How many puffs it vanishes in, and how loud and high the rumble down the return's track is. */
    private static final int RETURN_PUFFS = 6;
    private static final float RETURN_VOLUME = 0.6F;
    private static final float RETURN_PITCH = 1.3F;
    /** How loud and how high it sounds as it is picked up: a low pop, for a heavy ball. */
    private static final float PICKUP_VOLUME = 0.4F;
    private static final float PICKUP_PITCH = 0.8F;
    /** How far round it, across and up, its puffs spread as it goes back, in blocks, and how fast. */
    private static final double PUFFS_ACROSS = 0.15;
    private static final double PUFFS_UP = 0.1;
    private static final double PUFFS_SPEED = 0.02;
    /** Ticks after a bowl before it can be picked up, so it leaves the hand. */
    private static final int PICKUP_DELAY = 20;

    private int pickupDelay;
    private int stoppedTicks;
    /** Whether it has hit a wall, so it is slowing to rest. */
    private boolean spent;

    public BowlingBall(EntityType<? extends BowlingBall> type, Level level) {
        super(type, level);
    }

    /**
     * A ball bowled by the player along the floor the way they look, level whatever their pitch, from just ahead of
     * their feet, always at the same speed, so it goes where they aim; gently if they sneak. Bowled inside an assembled
     * ship's hull, it starts among her blocks, the bowl relative to her.
     */
    public static BowlingBall bowled(Level level, Player player) {
        BowlingBall ball = new BowlingBall(SkylinersRegistry.BOWLING_BALL.get(), level);
        Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
        ball.setPos(player.getX() + look.x * LET_GO_AHEAD, player.getY() + LET_GO_UP, player.getZ() + look.z * LET_GO_AHEAD);
        ball.setDeltaMovement(look.scale(player.isShiftKeyDown() ? GENTLE_BOWL : BOWL));
        ball.pickupDelay = PICKUP_DELAY;
        SableFlight.takeAboard(ball);
        return ball;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public float maxUpStep() {
        return STEP;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            SableFlight.keepWithCabin(this);
        }
        pickupDelay = Math.max(0, pickupDelay - 1);
        Vec3 velocity = getDeltaMovement().add(0, -GRAVITY, 0);
        Vec3 from = position();
        move(MoverType.SELF, velocity);
        Vec3 moved = position().subtract(from);
        // Each way across the floor the move came up short, it hit a wall: it bounces back off it, softly, spent.
        boolean blockedX = Math.abs(moved.x - velocity.x) > SHORT;
        boolean blockedZ = Math.abs(moved.z - velocity.z) > SHORT;
        spent |= blockedX || blockedZ;
        double x = blockedX ? -velocity.x * WALL_BOUNCE : velocity.x;
        double z = blockedZ ? -velocity.z * WALL_BOUNCE : velocity.z;
        double y = onGround() ? 0 : velocity.y;
        double keep = onGround() ? (spent ? ROLLING_SPENT : ROLLING) : AIR_DRAG;
        setDeltaMovement(new Vec3(x * keep, y, z * keep));
        if (level() instanceof ServerLevel server) {
            knockPins(server, from);
            goBackWhenDone(server);
        }
    }

    /**
     * Knocks down every pin it touched on its way from where it was a tick ago to where it now is, looked at every
     * {@link #SWEEP_STEP} along the way so that a fast ball cannot pass over a thin pin between ticks, each pin taking
     * a little of its speed.
     */
    private void knockPins(ServerLevel level, Vec3 from) {
        Vec3 way = position().subtract(from);
        int steps = Math.max(1, (int) Math.ceil(way.length() / SWEEP_STEP));
        for (int step = 1; step <= steps; step++) {
            knockPinsTouching(level, getBoundingBox().move(way.scale((double) step / steps - 1)));
        }
    }

    /** Knocks down every standing pin a box of the ball touches, each taking a little of its speed. */
    private void knockPinsTouching(ServerLevel level, AABB box) {
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BowlingPinBlock && BowlingPinBlock.touches(state, pos, box)) {
                BowlingPinBlock.knock(level, pos.immutable(), state);
                setDeltaMovement(getDeltaMovement().multiply(PIN_DRAG, 1, PIN_DRAG));
            }
        }
    }

    /**
     * Once it has lain stopped a moment, or rolled its longest, it goes back down the nearest ball return's track,
     * vanishing with a puff and a rumble; with none near, it stays where it is, looking again now and then, and at last
     * is left as a dropped ball.
     */
    private void goBackWhenDone(ServerLevel level) {
        boolean stopped = onGround() && getDeltaMovement().horizontalDistance() < STOPPED;
        stoppedTicks = stopped ? stoppedTicks + 1 : 0;
        if (tickCount >= LONGEST_LOOSE) {
            spawnAtLocation(getItem());
            discard();
            return;
        }
        boolean firstLook = stoppedTicks == RETURN_AFTER || tickCount == LONGEST_ROLL;
        if (!firstLook && (tickCount < LONGEST_ROLL || tickCount % LOOK_AGAIN != 0)) {
            return;
        }
        BlockPos ballReturn = nearestReturn(level);
        if (ballReturn == null) {
            return;
        }
        level.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() / 2, getZ(), RETURN_PUFFS, PUFFS_ACROSS, PUFFS_UP, PUFFS_ACROSS,
            PUFFS_SPEED);
        level.playSound(null, ballReturn, SkylinersRegistry.BOWLING_ROLL.get(), SoundSource.BLOCKS, RETURN_VOLUME, RETURN_PITCH);
        discard();
    }

    /** The nearest ball return round it, in its own frame, among a ship's blocks or in the world; null if none. */
    @Nullable
    private BlockPos nearestReturn(ServerLevel level) {
        BlockPos centre = blockPosition();
        BlockPos nearest = null;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-RETURN_REACH, -RETURN_REACH_UP, -RETURN_REACH),
            centre.offset(RETURN_REACH, RETURN_REACH_UP, RETURN_REACH))) {
            if (level.getBlockState(pos).getBlock() instanceof BallReturnBlock && (nearest == null || pos.distSqr(centre) < nearest.distSqr(centre))) {
                nearest = pos.immutable();
            }
        }
        return nearest;
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
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, PICKUP_VOLUME, PICKUP_PITCH);
            discard();
        }
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(SkylinersRegistry.BOWLING_BALL_ITEM.get());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("PickupDelay", pickupDelay);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        pickupDelay = tag.getInt("PickupDelay");
    }

    /** Picked, its item. */
    @Override
    public ItemStack getPickResult() {
        return getItem();
    }
}
