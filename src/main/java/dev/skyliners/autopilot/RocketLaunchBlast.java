package dev.skyliners.autopilot;

import dev.skyliners.Aboard;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.airframe.RocketSilhouette;
import dev.skyliners.compat.SableFlight;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A rocket's lift-off as it is felt round her: as her engines light on the ground, their blast slams into it and a
 * shockwave races out from under her, a ring of dust rolling out over the ground, fast at first and slowing as it
 * spreads. As it passes, it shoves everyone and everything on the ground out of its way once, players, animals,
 * dropped things, harder the nearer they stand, and each player hears its boom and feels it shake them (see
 * {@link LaunchShock}); farther off, beyond the ring, its boom still rolls in, later and quieter. Those aboard her feel
 * nothing of it: her lift-off shakes them through her floor instead. The server config's {@code rocket.launchBlast}
 * leaves it out.
 */
final class RocketLaunchBlast {
    /** How near the ground her foot must be as her engines light for the blast to slam into it, in blocks. */
    private static final double ON_THE_GROUND = 3;
    /**
     * The ring: how long it takes to race out, in ticks; how far it reaches from her middle on a rocket built at scale
     * 1, in blocks.
     */
    private static final int RING_TICKS = 60;
    private static final double REACH_PER_SCALE = 32;
    /**
     * Its boom beyond the ring: how far off it is still heard, as a multiple of the ring's reach, and how fast it rolls
     * on out there, in blocks a tick.
     */
    private static final double HEARD_FAR = 3;
    private static final double ROLLING_ON = 4;
    /**
     * How hard it shoves what it passes, once, out from her and up, in blocks a tick: this hard from her skin in,
     * falling off to nothing at the ring's reach. Enough to lift a player off their feet and throw them some six blocks
     * back from 25 blocks off her, never so high that the fall back to where they stood hurts them; a ledge they are
     * thrown off still may.
     */
    private static final double SHOVE = 2.6;
    private static final double SHOVE_UP = 0.6;
    /** How high over and under the ground round her what it shoves may be, in blocks. */
    private static final double SHOVED_OVER = RocketDustRing.HIGHEST_GROUND;
    private static final double SHOVED_UNDER = 4;

    /** Whether her engines fired last tick. */
    private boolean firing;
    /** Where the blast slammed into the ground, under her middle; null with none under way. */
    @Nullable
    private Vec3 centre;
    /** How far out from the centre her skin is, where the blast hits hardest, and how far it reaches, in blocks. */
    private double skin;
    private double reach;
    /** Ticks since her engines lit, and how far out the front is, in blocks. */
    private int age;
    private double front;
    /** What it has shoved and the players who have heard it, each only once. */
    private final Set<UUID> shoved = new HashSet<>();
    private final Set<UUID> heard = new HashSet<>();

    /**
     * Each tick of an assembled rocket's: a blast begun as her engines light on the ground, and one under way carried
     * on; given her radio altitude, how far under her middle her foot is, in blocks, and her engines' share of full
     * thrust.
     */
    void tick(ServerLevel level, AutopilotBlockEntity rocket, SableFlight.State flight, double radio, int footBelow, double thrust) {
        boolean lit = thrust > 0 && !firing;
        firing = thrust > 0;
        if (lit && radio < ON_THE_GROUND && rocket.phase() == AutopilotBlockEntity.Phase.TAKEOFF && SkylinersConfig.ROCKET_LAUNCH_BLAST.get()) {
            begin(level, flight, footBelow);
        }
        if (centre != null) {
            spread(level, rocket);
        }
    }

    /** The blast slamming into the ground under her, its front starting out from under her middle. */
    private void begin(ServerLevel level, SableFlight.State flight, int footBelow) {
        double scale = RocketSilhouette.scaleOf(footBelow);
        Vec3 middle = flight.position();
        centre = new Vec3(middle.x, ground(level, middle.x, middle.z), middle.z);
        skin = RocketSilhouette.body(RocketSilhouette.height() / 2) * scale;
        reach = Math.max(skin + 1, REACH_PER_SCALE * scale);
        age = 0;
        front = 0;
        shoved.clear();
        heard.clear();
    }

    /** A tick of the blast: its ring of dust, and what its front reaches this tick shoved and shaken. */
    private void spread(ServerLevel level, AutopilotBlockEntity rocket) {
        age++;
        front = frontAt(age);
        if (age <= RING_TICKS) {
            ring(level);
            shove(level, rocket);
        }
        for (ServerPlayer player : level.players()) {
            double off = player.position().distanceTo(centre);
            if (off <= front && !Aboard.isAboard(player, rocket) && heard.add(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, new LaunchShock((float) strength(off)));
            }
        }
        if (front >= reach * HEARD_FAR) {
            centre = null;
            shoved.clear();
            heard.clear();
        }
    }

    /**
     * How far out the front is so many ticks after the blast, in blocks: easing out over the ring, then rolling on.
     */
    private double frontAt(int ticks) {
        if (ticks >= RING_TICKS) {
            return reach + (ticks - RING_TICKS) * ROLLING_ON;
        }
        double t = ticks / (double) RING_TICKS;
        return reach * (1 - (1 - t) * (1 - t));
    }

    /** How hard the blast hits so far from its centre: 1 out to her skin, falling to 0 at the ring's reach. */
    private double strength(double off) {
        return Mth.clamp(1 - (off - skin) / (reach - skin), 0, 1);
    }

    /** How far from the blast's centre a point is, across the ground. */
    private double across(Vec3 point) {
        return Math.hypot(point.x - centre.x, point.z - centre.z);
    }

    /** The ring's dust for this tick, to everyone near enough: the client rolls it along the ground under the ring. */
    private void ring(ServerLevel level) {
        RocketPlumes.tell(level, centre, new RocketDustRing(centre.x, centre.y, centre.z, (float) front, (float) (frontAt(age) - frontAt(age - 1))));
    }

    /**
     * Everything on the ground the front has reached and not shoved yet, shoved out from her and up, harder the nearer
     * it stood, its fall counted afresh from there.
     */
    private void shove(ServerLevel level, AutopilotBlockEntity rocket) {
        AABB round = new AABB(centre.x - front, centre.y - SHOVED_UNDER, centre.z - front, centre.x + front, centre.y + SHOVED_OVER, centre.z + front);
        for (Entity entity : level.getEntities((Entity) null, round, RocketLaunchBlast::shoveable)) {
            double off = across(entity.position());
            if (off <= front && !Aboard.isAboard(entity, rocket) && shoved.add(entity.getUUID())) {
                double hard = strength(off);
                Vec3 out = off > 0 ? new Vec3(entity.getX() - centre.x, 0, entity.getZ() - centre.z).normalize() : Vec3.ZERO;
                entity.push(out.x * SHOVE * hard, SHOVE_UP * hard, out.z * SHOVE * hard);
                entity.resetFallDistance();
                entity.hurtMarked = true;
            }
        }
    }

    /** What the blast shoves: the living, but for spectators and what rides something, and dropped things. */
    private static boolean shoveable(Entity entity) {
        boolean thing = entity instanceof LivingEntity || entity instanceof ItemEntity || entity instanceof ExperienceOrb;
        return thing && !entity.isSpectator() && !entity.isPassenger();
    }

    /** The top of the ground in a column, where the dust rolls. */
    private static double ground(ServerLevel level, double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
    }
}
