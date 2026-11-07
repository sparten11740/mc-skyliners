package dev.skyliners.turret;

import dev.skyliners.Aboard;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.compat.SableFlight;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A rocket launched from a laser cannon's pod (see {@link LaserCannonBlockEntity}): it leaves its tube slowly, carried
 * along at the speed of the ship it was launched from, and its motor drives it faster and faster along its way, the
 * ship's speed falling away behind it, until it strikes something and goes off (see {@link TurretBlast#rocket}), or
 * burns out and goes off in the air. Launched while the cannon's sight is locked on to a hostile mob, it homes in on
 * that mob, turning gently, and goes off as it passes close. It flies through her own blocks and past anyone who is not
 * a hostile mob, and goes off only against the world's blocks, another ship's blocks or a hostile mob not aboard her;
 * its blast spares every ship's blocks and hurts hostile mobs only.
 *
 * <p>It flies on the server, which tells the clients where it is every tick; each client carries it on between, draws
 * it (the client's {@code CannonRocketRenderer}), and trails its smoke and flame and plays its motor's roar itself (the
 * client's {@code CannonRockets}), so its smoke costs the network nothing. It is never saved: one in flight as the
 * world closes is gone.
 */
public class CannonRocket extends Projectile {
    /**
     * How fast it leaves its tube, how fast its motor drives it at most, and how much faster each tick, in blocks a
     * tick, apart from the ship's speed: a second to full speed, about sixty blocks a second.
     */
    private static final double LAUNCH_SPEED = 0.5;
    private static final double TOP_SPEED = 3.0;
    private static final double THRUST = 0.2;
    /** The share of the ship's speed it keeps from one tick to the next, its own speed taking over. */
    private static final double DRIFT_KEPT = 0.9;
    /** How long its motor burns, in ticks, before it goes off in the air: some two hundred blocks out. */
    public static final int BURN_TICKS = 80;
    /** How far it turns toward the mob it homes in on, at most, in degrees a tick: gently, so a quick one can dodge. */
    private static final double TURN = 6;
    /** How near the middle of the mob it homes in on it goes off, in blocks. */
    private static final double FUSE = 1.5;
    /** How much bigger than its box a mob is to it, in blocks each way, so a near miss still strikes. */
    private static final double GRAZE = 0.3;
    /**
     * How many of her own blocks along one tick's flight it passes through at most, looking for the world's beyond, and
     * how far past each it looks on from, in blocks.
     */
    private static final int PASSES_THROUGH = 8;
    private static final double PAST_HER = 0.75;
    /**
     * How far back along its flight from the face of a block it strikes it goes off, in blocks, so that its blast is
     * not inside that block and smothered by it.
     */
    private static final double BACK_OFF = 0.2;
    /** How far off its blast is shown to clients, in blocks. */
    private static final double BLAST_SEEN = 160;
    /** How loud its bang is: past full, so that the server sends it further, sixteen blocks for each. */
    private static final float BLAST_VOLUME = 6;
    /** How far off it is drawn, in blocks. */
    private static final double DRAWN = 160;
    /** How long past its burn a client carries it on unheard from before leaving it be, in ticks. */
    private static final int CARRIED_PAST_BURN = 20;

    /** Its own way and speed, and the speed of the ship that launched it, fading, in blocks a tick, on the server. */
    private Vec3 heading = new Vec3(0, 0, 1);
    private double speed = LAUNCH_SPEED;
    private Vec3 drift = Vec3.ZERO;
    /** The hostile mob it homes in on, none for none, on the server. */
    @Nullable
    private Entity target;
    /**
     * The cannon that launched it, and the ship that cannon is part of, none for one standing in the world, on the
     * server.
     */
    @Nullable
    private LaserCannonBlockEntity cannon;
    @Nullable
    private UUID her;
    /** How many ticks it has flown, on either side. */
    private int flown;

    /** Trails its smoke and flame and plays its motor's roar on a client each tick; the client sets it. */
    public static Consumer<CannonRocket> clientTicker = rocket -> { };

    public CannonRocket(EntityType<? extends CannonRocket> type, Level level) {
        super(type, level);
    }

    /**
     * Launches a rocket from a cannon, fired by its gunner, from a point in the world along a way, carried along at a
     * ship's speed there, in blocks a tick, homing in on a hostile mob, or none.
     */
    static void launch(ServerLevel level, LaserCannonBlockEntity cannon, @Nullable Entity gunner, Vec3 at, Vec3 way, Vec3 drift,
        @Nullable Entity target) {
        CannonRocket rocket = new CannonRocket(SkylinersRegistry.CANNON_ROCKET.get(), level);
        rocket.setOwner(gunner);
        rocket.cannon = cannon;
        rocket.her = SableFlight.bodyAt(level, cannon.getBlockPos());
        rocket.target = target;
        rocket.heading = way.normalize();
        rocket.drift = drift;
        rocket.setPos(at);
        rocket.setDeltaMovement(rocket.heading.scale(LAUNCH_SPEED).add(drift));
        rocket.face(rocket.heading);
        rocket.yRotO = rocket.getYRot();
        rocket.xRotO = rocket.getXRot();
        level.addFreshEntity(rocket);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    /**
     * Each tick: on the server it homes in, speeds up, and flies a tick's way, going off where it strikes something,
     * where it would fly out of the land kept going, or once its motor burns out; on a client it is carried on as it
     * last moved, its smoke and flame trailed and its roar heard.
     */
    @Override
    public void tick() {
        super.tick();
        flown++;
        if (level().isClientSide) {
            if (burning()) {
                setPos(position().add(getDeltaMovement()));
            }
            clientTicker.accept(this);
            return;
        }
        ServerLevel level = (ServerLevel) level();
        Vec3 from = position();
        home();
        speed = Math.min(TOP_SPEED, speed + THRUST);
        drift = drift.scale(DRIFT_KEPT);
        Vec3 step = heading.scale(speed).add(drift);
        Vec3 to = from.add(step);
        if (flown > BURN_TICKS || !level.isPositionEntityTicking(BlockPos.containing(to))) {
            goOff(from);
            return;
        }
        Vec3 struck = struck(from, to);
        if (struck != null) {
            goOff(struck);
            return;
        }
        setDeltaMovement(step);
        setPos(to);
        face(heading);
    }

    /** Turns it toward the mob it homes in on, at most {@link #TURN} a tick; it lets go of one gone or dead. */
    private void home() {
        if (target == null) {
            return;
        }
        if (!target.isAlive() || target.level() != level()) {
            target = null;
            return;
        }
        Vec3 toward = target.getBoundingBox().getCenter().subtract(position());
        if (toward.lengthSqr() > Mth.EPSILON) {
            heading = turned(heading, toward.normalize(), Math.toRadians(TURN));
        }
    }

    /**
     * A way turned toward another, both of length one, by an angle in radians at most: the other itself if it is that
     * near, and the way as it is if the other is straight behind it.
     */
    static Vec3 turned(Vec3 way, Vec3 toward, double most) {
        double angle = Math.acos(Mth.clamp(way.dot(toward), -1, 1));
        if (angle <= most) {
            return toward;
        }
        Vec3 axis = way.cross(toward);
        if (axis.lengthSqr() < Mth.EPSILON) {
            return way;
        }
        axis = axis.normalize();
        return way.scale(Math.cos(most)).add(axis.cross(way).scale(Math.sin(most))).normalize();
    }

    /**
     * Where along a tick's flight it strikes something, if it does: a block of the world or of another ship, or a
     * hostile mob.
     */
    @Nullable
    private Vec3 struck(Vec3 from, Vec3 to) {
        Vec3 wall = wallAhead(from, to);
        Vec3 mob = mobAhead(from, wall == null ? to : wall);
        return mob != null ? mob : wall;
    }

    /**
     * Where along a tick's flight it first meets a block that is not one of her own, in the world: through any of hers
     * it meets on the way, which it flies through.
     */
    @Nullable
    private Vec3 wallAhead(Vec3 from, Vec3 to) {
        Vec3 at = from;
        for (int pass = 0; pass < PASSES_THROUGH; pass++) {
            BlockHitResult met = level().clip(new ClipContext(at, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (met.getType() == HitResult.Type.MISS) {
                return null;
            }
            Vec3 there = SableFlight.toWorld(level(), met.getLocation());
            if (her == null || !her.equals(SableFlight.bodyAt(level(), met.getBlockPos()))) {
                return there.subtract(to.subtract(from).normalize().scale(BACK_OFF));
            }
            at = there.add(to.subtract(from).normalize().scale(PAST_HER));
            if (to.subtract(at).dot(to.subtract(from)) <= 0) {
                return null;
            }
        }
        return null;
    }

    /**
     * Where along a tick's flight it first meets a hostile mob it may strike, or passes close enough to the one it
     * homes in on to go off: the nearest along its way.
     */
    @Nullable
    private Vec3 mobAhead(Vec3 from, Vec3 to) {
        AABB swept = new AABB(from, to).inflate(Math.max(GRAZE, FUSE) + 1);
        Vec3 nearest = null;
        for (Entity mob : level().getEntities(this, swept, this::strikes)) {
            Vec3 met = meets(mob, from, to);
            if (met != null && (nearest == null || from.distanceToSqr(met) < from.distanceToSqr(nearest))) {
                nearest = met;
            }
        }
        return nearest;
    }

    /**
     * Where along a tick's flight it meets a mob: where it enters the mob's box, grown by {@link #GRAZE}, or, for the
     * mob it homes in on, where it passes within {@link #FUSE} of its middle; none if it misses.
     */
    @Nullable
    private Vec3 meets(Entity mob, Vec3 from, Vec3 to) {
        AABB box = mob.getBoundingBox().inflate(GRAZE);
        if (box.contains(from)) {
            return from;
        }
        Optional<Vec3> entered = box.clip(from, to);
        if (entered.isPresent()) {
            return entered.get();
        }
        if (mob != target) {
            return null;
        }
        Vec3 nearest = nearestAlong(from, to, box.getCenter());
        return nearest.distanceTo(box.getCenter()) <= FUSE ? nearest : null;
    }

    /** The point of a stretch from one point to another nearest a third. */
    private static Vec3 nearestAlong(Vec3 from, Vec3 to, Vec3 point) {
        Vec3 way = to.subtract(from);
        double length = way.lengthSqr();
        double along = length < Mth.EPSILON ? 0 : Mth.clamp(point.subtract(from).dot(way) / length, 0, 1);
        return from.add(way.scale(along));
    }

    /** Whether it may strike someone: a hostile mob, alive and not aboard her. */
    private boolean strikes(Entity entity) {
        return entity instanceof Enemy && entity.isAlive() && !entity.isSpectator() && notAboard(entity);
    }

    /** Whether someone is not aboard the ship that launched it: anyone, for a cannon gone or standing in the world. */
    private boolean notAboard(Entity entity) {
        return cannon == null || cannon.isRemoved() || !Aboard.isAboard(entity, cannon);
    }

    /**
     * It goes off at a point: its blast, breaking blocks as the server config's {@code freighter.turretBlasts} allows,
     * its bang, heard far off, and its fireball for the clients near to draw.
     */
    private void goOff(Vec3 at) {
        ServerLevel level = (ServerLevel) level();
        setPos(at);
        TurretBlast.rocket(level, this, at, this::notAboard, SkylinersConfig.FREIGHTER_TURRET_BLASTS.get());
        level.playSound(null, at.x, at.y, at.z, SkylinersRegistry.CANNON_ROCKET_BLAST.get(), SoundSource.BLOCKS, BLAST_VOLUME,
            0.9F + random.nextFloat() * 0.2F);
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, BLAST_SEEN, new RocketBlast(at));
        discard();
    }

    /** Turns it to point its nose along a way, which the clients are told with its moves. */
    private void face(Vec3 way) {
        setYRot((float) (Mth.atan2(way.x, way.z) * Mth.RAD_TO_DEG));
        setXRot((float) (Mth.atan2(way.y, way.horizontalDistance()) * Mth.RAD_TO_DEG));
    }

    /** How many ticks it has flown, on either side, at a partial tick. */
    public float flown(float partialTick) {
        return flown + partialTick;
    }

    /** Whether its motor still burns, on either side: a client stops carrying it on unheard from once it burns out. */
    public boolean burning() {
        return flown <= BURN_TICKS + CARRIED_PAST_BURN;
    }

    /** The way its nose points at a partial tick, on either side, as it was last turned. */
    public Vec3 nose(float partialTick) {
        double turn = Math.toRadians(Mth.rotLerp(partialTick, yRotO, getYRot()));
        double tilt = Math.toRadians(Mth.lerp(partialTick, xRotO, getXRot()));
        return new Vec3(Math.sin(turn) * Math.cos(tilt), Math.sin(tilt), Math.cos(turn) * Math.cos(tilt));
    }

    /**
     * Its speed as the server tells a client, and nothing else: a projectile's own turns it to point along its first
     * speed, which, launched from a ship in flight, carries her speed and points well off its way, and the server only
     * tells a client how it points once that changes.
     */
    @Override
    public void lerpMotion(double x, double y, double z) {
        setDeltaMovement(x, y, z);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < DRAWN * DRAWN;
    }

    /** Water never pushes it about: its motor drives it straight through. */
    @Override
    public boolean isPushedByFluid() {
        return false;
    }
}
