package dev.aerolarge.autopilot;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.airframe.RocketSilhouette;
import dev.aerolarge.compat.SableFlight;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A rocket's flight as it looks round her from outside, beyond her engines' own plumes: as she lifts off or comes in
 * to land, her engines lit near the ground, smoke billows out over it from under her and rolls away, and closer still
 * flames and embers burst from the ground under her; and falling back from space fast, her base glows and a trail
 * of flame streams up behind it, as a heat shield's does coming back in. It is not as hot as that, but it looks the
 * part. The particles are sent from the server to everyone near enough to see her, long-distance, so that those
 * aboard, high over her base, see it too. The server config's {@code rocket.plumes} leaves them out.
 */
final class RocketPlumes {
    /** How high over the ground her foot may be for her lit engines to raise smoke from it, in blocks. */
    private static final double SMOKE_UNDER = 40;
    /**
     * How many puffs roll out over the ground a tick, each a packet of its own to give it its way out, one in so many
     * of them smoke that lingers and the rest white cloud; how far out from under her foot they start and how high over
     * the ground, in blocks; how fast they roll out, at least and at most, and rise, in blocks a tick.
     */
    private static final int PUFFS = 6;
    private static final int LINGERING = 3;
    private static final double SMOKE_FROM = 2;
    private static final double SMOKE_OVER = 0.5;
    private static final double SLOWEST_ROLL = 0.3;
    private static final double FASTEST_ROLL = 0.9;
    private static final double RISE = 0.08;
    /**
     * Close over the ground, her engines blazing on it: how high her foot may be, in blocks; how many flames a tick
     * burst from the ground under her, how far round under her they spread on a rocket built at scale 1, in blocks,
     * and how fast they fly, in blocks a tick; and how many embers of lava a tick leap from among them. No flash
     * lights it: one every few ticks, her landing burn's last ten seconds long, would be a strobe.
     */
    private static final double BLAZE_UNDER = 15;
    private static final int BLAZE_FLAMES = 40;
    private static final double BLAZE_SPREAD = 1.25;
    private static final double BLAZE_SPEED = 0.15;
    private static final int EMBERS = 3;
    /** How high she must be and how fast falling for her base to glow, in world blocks and blocks a second. */
    private static final double GLOW_FROM = 500;
    private static final double GLOW_FALL = 20;
    /**
     * Where flames stream up from round her base: at so many spots round her each tick, so many at each, and so many
     * sparks of lava among them; how far outside her skin they start, in metres, and how far round each spot they
     * spread, in blocks. They stand in the air where they start as she falls, so they must be clear of her to be seen
     * streaming up her sides.
     */
    private static final int FLAME_SPOTS = 6;
    private static final int FLAMES_A_SPOT = 4;
    private static final int SPARKS = 2;
    private static final double FLAMES_CLEAR = 1;
    private static final double FLAME_SPREAD = 0.6;
    /** How far off those who see the particles may be, in blocks: the length of her and more, so all aboard do. */
    private static final double SEEN_WITHIN = 256;

    private RocketPlumes() {
    }

    /**
     * Each tick of an assembled rocket's: smoke at the ground under her lit engines near it, fire on it closer still,
     * and flame streaming from her base as she falls back from space, given her radio altitude, how far under her
     * middle her foot is, in blocks, and her engines' share of full thrust.
     */
    static void tick(ServerLevel level, SableFlight.State flight, double radio, int footBelow, double thrust) {
        if (!AerolargeConfig.ROCKET_PLUMES.get()) {
            return;
        }
        Vec3 foot = flight.position().subtract(flight.up().scale(footBelow));
        if (thrust > 0 && radio < SMOKE_UNDER) {
            smoke(level, foot);
        }
        if (thrust > 0 && radio < BLAZE_UNDER) {
            blaze(level, foot, footBelow);
        }
        if (flight.position().y > GLOW_FROM && flight.velocity().y < -GLOW_FALL) {
            flames(level, foot, footBelow);
        }
    }

    /** Puffs of white cloud rolling out every way over the ground under her foot, and smoke lingering after them. */
    private static void smoke(ServerLevel level, Vec3 foot) {
        Vec3 under = ground(level, foot);
        for (int puff = 0; puff < PUFFS; puff++) {
            double way = level.getRandom().nextDouble() * Mth.TWO_PI;
            double speed = Mth.lerp(level.getRandom().nextDouble(), SLOWEST_ROLL, FASTEST_ROLL);
            Vec3 out = new Vec3(Math.cos(way), 0, Math.sin(way));
            Vec3 at = under.add(out.scale(SMOKE_FROM));
            roll(level, puff % LINGERING == 0 ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.CLOUD, at, out.scale(speed).add(0, RISE, 0));
        }
    }

    /** Flames bursting from the ground under her in one packet, spread as wide as she is built, and embers leaping. */
    private static void blaze(ServerLevel level, Vec3 foot, int footBelow) {
        Vec3 under = ground(level, foot);
        double spread = BLAZE_SPREAD * RocketSilhouette.scaleOf(footBelow);
        send(level, ParticleTypes.FLAME, under, BLAZE_FLAMES, spread, BLAZE_SPEED);
        send(level, ParticleTypes.LAVA, under, EMBERS, spread, 0);
    }

    /** The spot just over the ground straight under her foot, where her smoke and her fire start. */
    private static Vec3 ground(ServerLevel level, Vec3 foot) {
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(foot.x), Mth.floor(foot.z));
        return new Vec3(foot.x, ground + SMOKE_OVER, foot.z);
    }

    /**
     * Flames round her foot, just outside her skin, left standing in the air as she falls, streaming up her sides, and
     * sparks among them; her radius from how far her foot is under her control head, which is as she was built.
     */
    private static void flames(ServerLevel level, Vec3 foot, int footBelow) {
        double out = (RocketSilhouette.body(RocketSilhouette.height() / 2) + FLAMES_CLEAR) * RocketSilhouette.scaleOf(footBelow);
        for (int spot = 0; spot < FLAME_SPOTS + SPARKS; spot++) {
            double way = level.getRandom().nextDouble() * Mth.TWO_PI;
            Vec3 at = new Vec3(foot.x + Math.cos(way) * out, foot.y, foot.z + Math.sin(way) * out);
            boolean spark = spot >= FLAME_SPOTS;
            send(level, spark ? ParticleTypes.LAVA : ParticleTypes.FLAME, at, spark ? 1 : FLAMES_A_SPOT, FLAME_SPREAD, 0);
        }
    }

    /** One particle moving a way, in blocks a tick, sent to everyone near enough, however far that is. */
    private static void roll(ServerLevel level, ParticleOptions kind, Vec3 at, Vec3 motion) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at) < SEEN_WITHIN * SEEN_WITHIN) {
                level.sendParticles(player, kind, true, at.x, at.y, at.z, 0, motion.x, motion.y, motion.z, 1);
            }
        }
    }

    /**
     * So many particles about a spot, spread round it so far, in blocks, and moving at so much, in blocks a tick, every
     * way: one packet to everyone near enough, however far that is.
     */
    private static void send(ServerLevel level, ParticleOptions kind, Vec3 at, int count, double spread, double speed) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at) < SEEN_WITHIN * SEEN_WITHIN) {
                level.sendParticles(player, kind, true, at.x, at.y, at.z, count, spread, spread / 4, spread, speed);
            }
        }
    }
}
