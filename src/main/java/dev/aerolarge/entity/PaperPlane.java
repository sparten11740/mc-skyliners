package dev.aerolarge.entity;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.FirstFlight;
import dev.aerolarge.compat.SableFlight;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A paper plane in flight: thrown from the hand, it glides. Faster than its gliding speed it lifts a little, slower it
 * sinks, and the sinking carries it on forward, so it settles into a long gentle glide; it wavers from side to side
 * as it goes, and drifts one way or the other a little, as no two paper planes fold alike. Touching anything solid or
 * water, it lands and lies there as a paper plane to pick up; a player it flies into catches it, and so does one
 * using it in flight. It moves the same on both sides, so it glides smoothly between updates.
 *
 * <p>Inside an airliner in flight the cabin's air moves along with it, so the plane glides relative to the cabin, as
 * the basketball bounces: thrown in the lounge it flies down the lounge, not off to the back at the airliner's speed.
 * Out through a door it meets the still air outside at the airliner's speed, slows, and glides on from the height it
 * left at, a long way down: from an airliner in flight it can come down far from where it was thrown (Air Mail).
 */
public class PaperPlane extends Entity {
    /** Speed a throw leaves the hand at, and a soft one, sneaking, in blocks a tick. */
    private static final double THROW = 0.7;
    private static final double SOFT_THROW = 0.45;
    /** Gravity, in blocks a tick each tick: much lighter than a ball's, it is paper. */
    private static final double GRAVITY = 0.03;
    /**
     * The lift its wings give at a speed, in blocks a tick each tick per square of the speed in blocks a tick, and the
     * most they give, a little over its weight: a hard throw climbs a little and levels out, but never loops.
     */
    private static final double LIFT = 0.07;
    private static final double MOST_LIFT = 0.036;
    /** The share of its sinking each tick that the wings turn into speed forward: what makes it glide, not fall. */
    private static final double GLIDE = 0.15;
    /** What the air takes of its speed each tick: a share, and a further share per block a tick it flies at. */
    private static final double DRAG = 0.01;
    private static final double DRAG_PER_SPEED = 0.03;
    /**
     * How much it wavers, at most, in degrees it turns a tick, and how fast, in radians of the waver a tick; and the
     * most it drifts steadily to one side, in degrees a tick.
     */
    private static final double WAVER = 0.6;
    private static final double WAVER_RATE = 0.2;
    private static final double MOST_DRIFT = 0.15;
    /** How far it banks into a turn, in degrees per degree it turns a tick. */
    private static final float BANK = 25;
    /** Ticks after a throw before a player it flies into catches it, so it leaves the thrower's hand. */
    private static final int CATCH_DELAY = 10;
    /** The longest it glides, in ticks (three minutes), before it comes down wherever it is. */
    private static final int LONGEST_FLIGHT = 3 * 60 * 20;
    /**
     * The least the cabin has to be moving when it is thrown, in blocks a second, for the throw to count as aboard an
     * airliner in flight, and how far from where it was thrown it has to come down outside it for Air Mail, in blocks.
     */
    private static final double IN_FLIGHT = 10;
    private static final double AIR_MAIL = 100;

    @Nullable
    private UUID thrower;
    private int catchDelay;
    /** Where in the world it was thrown from, and whether that was aboard an airliner in flight and it has left it since. */
    private Vec3 origin = Vec3.ZERO;
    private boolean thrownInFlight;
    private boolean leftCabin;

    public PaperPlane(EntityType<? extends PaperPlane> type, Level level) {
        super(type, level);
    }

    /** A plane thrown by the player, the way they look, from just in front of their eyes; softly if they sneak. */
    public static PaperPlane thrown(Level level, Player player) {
        PaperPlane plane = new PaperPlane(AerolargeRegistry.PAPER_PLANE.get(), level);
        Vec3 look = player.getLookAngle();
        plane.setPos(player.getX() + look.x * 0.5, player.getEyeY() - 0.2 + look.y * 0.5, player.getZ() + look.z * 0.5);
        plane.setRot(player.getYRot(), player.getXRot());
        Vec3 cabin = plane.cabin();
        Vec3 carried = player.getDeltaMovement();
        plane.setDeltaMovement(look.scale(player.isShiftKeyDown() ? SOFT_THROW : THROW).add(carried.x, player.onGround() ? 0 : carried.y, carried.z)
            .add(cabin));
        plane.thrower = player.getUUID();
        plane.catchDelay = CATCH_DELAY;
        plane.origin = player.position();
        plane.thrownInFlight = cabin.length() * SharedConstants.TICKS_PER_SECOND >= IN_FLIGHT;
        return plane;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        catchDelay = Math.max(0, catchDelay - 1);
        Vec3 cabin = cabin();
        if (thrownInFlight && cabin.lengthSqr() == 0) {
            leftCabin = true;
        }
        Vec3 velocity = cabin.add(glide(getDeltaMovement().subtract(cabin)));
        Vec3 from = position();
        move(MoverType.SELF, velocity);
        Vec3 moved = position().subtract(from);
        boolean blocked = Math.abs(moved.x - velocity.x) > 1e-4 || Math.abs(moved.y - velocity.y) > 1e-4 || Math.abs(moved.z - velocity.z) > 1e-4;
        if (blocked || isInWater() || isInLava() || tickCount > LONGEST_FLIGHT) {
            setDeltaMovement(cabin);
            if (!level().isClientSide) {
                land(cabin);
            }
            return;
        }
        setDeltaMovement(velocity);
    }

    /**
     * One tick of gliding, through air moving at the given velocity relative to it, in blocks a tick: it wavers and
     * drifts round, its weight pulls it down, its wings lift it the faster it flies and turn its sinking into speed
     * forward, and the air slows it. It points the way it flies.
     */
    private Vec3 glide(Vec3 air) {
        double turn = Math.toRadians(turn(tickCount));
        double cos = Math.cos(turn);
        double sin = Math.sin(turn);
        double x = air.x * cos - air.z * sin;
        double z = air.x * sin + air.z * cos;
        double speed = Math.hypot(x, z);
        double y = air.y - GRAVITY + Math.min(LIFT * speed * speed, MOST_LIFT);
        // Its heading: the way it flies, or the way it points when it hardly moves across at all.
        double yaw = speed > 1e-4 ? Math.atan2(-x, z) : Math.toRadians(getYRot()) + turn;
        if (y < 0) {
            double forward = -y * GLIDE;
            y += forward;
            x -= Math.sin(yaw) * forward;
            z += Math.cos(yaw) * forward;
        }
        Vec3 next = new Vec3(x, y, z);
        next = next.scale(Math.max(0, 1 - DRAG - DRAG_PER_SPEED * next.length()));
        setYRot((float) Math.toDegrees(yaw));
        setXRot((float) Math.toDegrees(Math.atan2(-next.y, next.horizontalDistance())));
        return next;
    }

    /**
     * How far it turns in a tick, in degrees: wavering one way and the other, and drifting steadily, by how it was
     * folded. Both are drawn from its id, the same on both sides.
     */
    private double turn(float age) {
        long folds = getUUID().getLeastSignificantBits();
        double phase = (folds & 0xFFFF) / 65536.0 * Mth.TWO_PI;
        double drift = (((folds >>> 16) & 0xFFFF) / 65536.0 * 2 - 1) * MOST_DRIFT;
        return WAVER * Math.sin(age * WAVER_RATE + phase) + drift;
    }

    /** How far it banks, in degrees: into the way it is turning. */
    public float roll(float partialTick) {
        return (float) turn(tickCount + partialTick) * BANK;
    }

    /**
     * Down: it lies where it came down as a paper plane to pick up, moving along with the cabin around it. Thrown
     * aboard an airliner in flight and come down outside it far enough away, it is Air Mail for whoever threw it.
     */
    private void land(Vec3 cabin) {
        if (isRemoved()) {
            return;
        }
        if (thrownInFlight && leftCabin && Math.hypot(getX() - origin.x, getZ() - origin.z) >= AIR_MAIL && thrower() instanceof ServerPlayer player) {
            FirstFlight.AIR_MAIL.award(player);
        }
        ItemEntity dropped = spawnAtLocation(getItem());
        if (dropped != null) {
            dropped.setDeltaMovement(cabin);
        }
        discard();
    }

    /** Who threw it, if they are still about. */
    @Nullable
    private Player thrower() {
        return thrower == null ? null : level().getPlayerByUUID(thrower);
    }

    @Override
    public void playerTouch(Player player) {
        if (!level().isClientSide && catchDelay == 0) {
            caught(player);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!level().isClientSide) {
            caught(player);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Hit, it is knocked out of the air and lands. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || !(source.getEntity() instanceof Player)) {
            return false;
        }
        land(cabin());
        return true;
    }

    private void caught(Player player) {
        if (isAlive() && player.getInventory().add(getItem())) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 1.6F);
            discard();
        }
    }

    /** How fast the air around the plane moves, in blocks a tick: an airliner's cabin's in flight, still anywhere else. */
    private Vec3 cabin() {
        return SableFlight.cabinVelocity(level(), position()).scale(1.0 / SharedConstants.TICKS_PER_SECOND);
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public ItemStack getPickResult() {
        return getItem();
    }

    private ItemStack getItem() {
        return new ItemStack(AerolargeRegistry.PAPER_PLANE_ITEM.get());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (thrower != null) {
            tag.putUUID("Thrower", thrower);
        }
        tag.putInt("CatchDelay", catchDelay);
        tag.putDouble("OriginX", origin.x);
        tag.putDouble("OriginY", origin.y);
        tag.putDouble("OriginZ", origin.z);
        tag.putBoolean("ThrownInFlight", thrownInFlight);
        tag.putBoolean("LeftCabin", leftCabin);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        thrower = tag.hasUUID("Thrower") ? tag.getUUID("Thrower") : null;
        catchDelay = tag.getInt("CatchDelay");
        origin = new Vec3(tag.getDouble("OriginX"), tag.getDouble("OriginY"), tag.getDouble("OriginZ"));
        thrownInFlight = tag.getBoolean("ThrownInFlight");
        leftCabin = tag.getBoolean("LeftCabin");
    }
}
