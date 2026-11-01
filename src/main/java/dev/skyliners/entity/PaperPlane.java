package dev.skyliners.entity;

import dev.skyliners.Skyliners;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.FirstFlight;
import dev.skyliners.block.LandingTargetBlockEntity;
import dev.skyliners.compat.SableFlight;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
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
 * using it in flight. It flies the same on both sides, from the same throw and on the same clock (its age goes to
 * clients with it), so a client draws it gliding smoothly between updates, easing it toward where the server has it
 * rather than putting it there: a busy server runs slower than its clients, and each update would pull it back. Only
 * the server takes it aboard an airliner and sends it out again.
 *
 * <p>Inside an assembled airliner's hull it lives among the airliner's blocks, in Sable's plot, and Sable carries it
 * along with them (see {@link SableFlight#keepWithCabin}), so it glides through the cabin's air as the basketball
 * bounces: thrown in the lounge in flight it flies down the lounge, not off to the back at the airliner's speed. Out
 * through a door it is sent into the world at the airliner's speed, meets the still air outside, slows, and glides on
 * from the height it left at, a long way down: from an airliner in flight it can come down far from where it was
 * thrown (Air Mail). From high up, off a zeppelin's Sky Deck, it glides hundreds of blocks, further than the land
 * round any player is kept going, so while it glides it keeps the land round it going itself, as a thrown ender pearl
 * does in later versions of the game, and lets it go as it comes down.
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
    /**
     * What keeps the chunks round a gliding plane going: a ticket over the chunk it is in, the chunks round it among
     * them so it always glides on into one that is going, held for a second and a half after it was last renewed,
     * renewed every half second, so it lapses soon after the plane comes down.
     */
    private static final TicketType<Integer> GLIDING = TicketType.create(Skyliners.MOD_ID + ":paper_plane", Integer::compare, 30);
    private static final int GLIDING_REACH = 3;
    private static final int GLIDING_RENEWAL = 10;
    /**
     * On a client, the share of the way to where the server last had it that its plane is eased each tick, and how far
     * from there, in blocks, it is put there at once instead: further than it glides between updates, as when the
     * server takes it aboard an airliner or sends it out.
     */
    private static final double EASING = 0.1;
    private static final double PUT_THERE = 4;
    /**
     * On a client, how far its plane's velocity may be from the server's, in blocks a tick, and still be eased toward
     * it rather than given it at once: more than a lagging server's plane, wavering a few ticks behind, is off from
     * its client's, and less than the cabin's speed it gains or loses as the server takes it aboard or sends it out.
     */
    private static final double TAKE_VELOCITY = 0.25;

    @Nullable
    private UUID thrower;
    private int catchDelay;
    /** Where in the world it was thrown from, and whether that was aboard an airliner in flight. */
    private Vec3 origin = Vec3.ZERO;
    private boolean thrownInFlight;
    /** On a client, how far its plane still has to be eased to where the server last had it, in blocks. */
    private Vec3 offServer = Vec3.ZERO;
    /**
     * On a client, where the server last had it and how it pointed there, not yet taken in: kept for its next tick, so
     * of several updates arriving together, as when a lagging server catches up, only the newest counts. An older one,
     * from before the plane flew on, would put it back.
     */
    @Nullable
    private Vec3 serverAt;
    private float serverYRot;
    private float serverXRot;
    /**
     * On a client, the velocity the server last gave it, not yet taken in, and how far its plane's still has to be
     * eased to that, in blocks a tick: given at once, each update would turn its nose with a jerk, as a lagging
     * server's plane wavers a few ticks behind.
     */
    @Nullable
    private Vec3 serverVelocity;
    private Vec3 velocityOffServer = Vec3.ZERO;

    public PaperPlane(EntityType<? extends PaperPlane> type, Level level) {
        super(type, level);
    }

    /**
     * A plane thrown by the player, the way they look, from just in front of their eyes; softly if they sneak. Thrown
     * inside an assembled airliner's hull, it starts among the airliner's blocks, the throw relative to the cabin.
     */
    public static PaperPlane thrown(Level level, Player player) {
        PaperPlane plane = new PaperPlane(SkylinersRegistry.PAPER_PLANE.get(), level);
        Vec3 look = player.getLookAngle();
        plane.setPos(player.getX() + look.x * 0.5, player.getEyeY() - 0.2 + look.y * 0.5, player.getZ() + look.z * 0.5);
        plane.setRot(player.getYRot(), player.getXRot());
        Vec3 carried = player.getDeltaMovement();
        plane.setDeltaMovement(look.scale(player.isShiftKeyDown() ? SOFT_THROW : THROW).add(carried.x, player.onGround() ? 0 : carried.y, carried.z));
        plane.thrower = player.getUUID();
        plane.catchDelay = CATCH_DELAY;
        plane.origin = player.position();
        SableFlight.takeAboard(plane);
        plane.thrownInFlight = SableFlight.velocity(level, plane.position()).length() >= IN_FLIGHT;
        return plane;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            easeToServer();
        } else {
            SableFlight.keepWithCabin(this);
            keepLandGoing();
        }
        catchDelay = Math.max(0, catchDelay - 1);
        Vec3 velocity = glide(getDeltaMovement());
        Vec3 from = position();
        move(MoverType.SELF, velocity);
        Vec3 moved = position().subtract(from);
        boolean blocked = Math.abs(moved.x - velocity.x) > 1e-4 || Math.abs(moved.y - velocity.y) > 1e-4 || Math.abs(moved.z - velocity.z) > 1e-4;
        boolean down = blocked || isInWater() || isInLava();
        if (down || tickCount > LONGEST_FLIGHT) {
            setDeltaMovement(Vec3.ZERO);
            if (!level().isClientSide) {
                scoreAtTarget(down);
                land();
            }
            return;
        }
        setDeltaMovement(velocity);
    }

    /**
     * One tick of gliding from the given velocity through the still air around it, in blocks a tick: it wavers and
     * drifts round, its weight pulls it down, its wings lift it the faster it flies and turn its sinking into speed
     * forward, and the air slows it. It points the way it flies.
     */
    private Vec3 glide(Vec3 velocity) {
        double turn = Math.toRadians(turn(tickCount));
        double cos = Math.cos(turn);
        double sin = Math.sin(turn);
        double x = velocity.x * cos - velocity.z * sin;
        double z = velocity.x * sin + velocity.z * cos;
        double speed = Math.hypot(x, z);
        double y = velocity.y - GRAVITY + Math.min(LIFT * speed * speed, MOST_LIFT);
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
        // Turned the short way from where it points, never across from +180 to -180: drawn, it would spin round.
        setYRot(getYRot() + Mth.wrapDegrees((float) Math.toDegrees(yaw) - getYRot()));
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
     * Down: it lies where it came down as a paper plane to pick up. Sable keeps no dropped item among an airliner's
     * blocks, so one come down in the cabin is put out into the world where it lies, moving along with the cabin; before
     * it is added, as Sable puts out what is added among them itself, without the airliner's speed, and in flight it
     * would stop dead in the air. Thrown aboard an airliner in flight and come down outside it far enough away, it is Air
     * Mail for whoever threw it.
     */
    private void land() {
        if (isRemoved()) {
            return;
        }
        boolean outside = !SableFlight.inPlot(this);
        if (thrownInFlight && outside && Math.hypot(getX() - origin.x, getZ() - origin.z) >= AIR_MAIL && thrower() instanceof ServerPlayer player) {
            FirstFlight.AIR_MAIL.award(player);
        }
        ItemEntity dropped = new ItemEntity(level(), getX(), getY(), getZ(), getItem(), 0, 0, 0);
        dropped.setDefaultPickUpDelay();
        SableFlight.sendOut(dropped);
        level().addFreshEntity(dropped);
        discard();
    }

    /** Out over the land, out of every airframe, it keeps the chunks round it going while it glides (see {@link #GLIDING}). */
    private void keepLandGoing() {
        if (tickCount % GLIDING_RENEWAL == 0 && level() instanceof ServerLevel server && !SableFlight.inPlot(this)) {
            server.getChunkSource().addRegionTicket(GLIDING, chunkPosition(), GLIDING_REACH, getId());
        }
    }

    /**
     * Come down on the ground, out of every airframe, it is scored for whoever threw it at the nearest landing target;
     * not when it is let fall out of the air after its longest flight.
     */
    private void scoreAtTarget(boolean down) {
        if (down && !isRemoved() && !SableFlight.inPlot(this) && thrower() instanceof ServerPlayer player) {
            LandingTargetBlockEntity.scorePlane(player, position());
        }
    }

    /**
     * On a client, a share of the way still to go to where the server last had it and to the velocity it gave it, or,
     * when either is far off, given it at once.
     */
    private void easeToServer() {
        takeServerPosition();
        takeServerVelocity();
        Vec3 step = offServer.scale(EASING);
        offServer = offServer.subtract(step);
        setPos(position().add(step));
        Vec3 speedUp = velocityOffServer.scale(EASING);
        velocityOffServer = velocityOffServer.subtract(speedUp);
        setDeltaMovement(getDeltaMovement().add(speedUp));
    }

    private void takeServerPosition() {
        if (serverAt != null && serverAt.distanceToSqr(position()) > PUT_THERE * PUT_THERE) {
            super.lerpTo(serverAt.x, serverAt.y, serverAt.z, serverYRot, serverXRot, 0);
            setOldPosAndRot();
            offServer = Vec3.ZERO;
        } else if (serverAt != null) {
            offServer = serverAt.subtract(position());
        }
        serverAt = null;
    }

    private void takeServerVelocity() {
        if (serverVelocity != null && serverVelocity.distanceToSqr(getDeltaMovement()) > TAKE_VELOCITY * TAKE_VELOCITY) {
            super.lerpMotion(serverVelocity.x, serverVelocity.y, serverVelocity.z);
            velocityOffServer = Vec3.ZERO;
        } else if (serverVelocity != null) {
            velocityOffServer = serverVelocity.subtract(getDeltaMovement());
        }
        serverVelocity = null;
    }

    /** On a client, where the server has it now, taken in on its next tick (see {@link #easeToServer}). */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        serverAt = new Vec3(x, y, z);
        serverYRot = yRot;
        serverXRot = xRot;
    }

    /** On a client, the velocity the server gave it now, taken in on its next tick (see {@link #easeToServer}). */
    @Override
    public void lerpMotion(double x, double y, double z) {
        serverVelocity = new Vec3(x, y, z);
    }

    /** Its age goes to clients with it, so they waver it as the server does. */
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity, tickCount);
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        tickCount = packet.getData();
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
        land();
        return true;
    }

    private void caught(Player player) {
        if (isAlive() && player.getInventory().add(getItem())) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 1.6F);
            discard();
        }
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
        return new ItemStack(SkylinersRegistry.PAPER_PLANE_ITEM.get());
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
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        thrower = tag.hasUUID("Thrower") ? tag.getUUID("Thrower") : null;
        catchDelay = tag.getInt("CatchDelay");
        origin = new Vec3(tag.getDouble("OriginX"), tag.getDouble("OriginY"), tag.getDouble("OriginZ"));
        thrownInFlight = tag.getBoolean("ThrownInFlight");
    }
}
