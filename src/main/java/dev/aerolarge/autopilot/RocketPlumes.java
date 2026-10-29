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
 * to land, her engines lit near the ground, smoke billows out over it from under her and rolls away; and falling back
 * from space fast, her base glows and a trail of flame streams up behind it, as a heat shield's does coming back in.
 * It is not as hot as that, but it looks the part. The particles are sent from the server to everyone near enough to
 * see her, long-distance, so that those aboard, high over her base, see it too. The server config's
 * {@code rocket.plumes} leaves them out.
 */
final class RocketPlumes {
    /** How high over the ground her foot may be for her lit engines to raise smoke from it, in blocks. */
    private static final double SMOKE_UNDER = 40;
    /**
     * How many puffs of smoke a tick, how fast they roll out over the ground, in blocks a tick, and how far out from
     * under her they start, in blocks.
     */
    private static final int PUFFS = 12;
    private static final double ROLL_OUT = 0.6;
    private static final double SMOKE_FROM = 2;
    /** How fast the puffs rise as they roll out, in blocks a tick, and one in how many is smoke that lingers, not cloud. */
    private static final double RISE = 0.08;
    private static final int LINGERING = 3;
    /** How high she must be and how fast falling for her base to glow, in world blocks and blocks a second. */
    private static final double GLOW_FROM = 500;
    private static final double GLOW_FALL = 20;
    /**
     * How many flames a tick stream up from round her base, and how far outside her skin they start, in metres: they
     * stand in the air where they start as she falls, so they must be clear of her to be seen streaming up her sides.
     */
    private static final int FLAMES = 24;
    private static final double FLAMES_CLEAR = 1;
    /** How far off those who see the particles may be, in blocks: the length of her and more, so all aboard do. */
    private static final double SEEN_WITHIN = 256;

    private RocketPlumes() {
    }

    /**
     * Each tick of an assembled rocket's: smoke at the ground under her lit engines near it, and flame streaming from
     * her base as she falls back from space, given her radio altitude, how far under her middle her foot is, in blocks,
     * and her engines' share of full thrust.
     */
    static void tick(ServerLevel level, SableFlight.State flight, double radio, int footBelow, double thrust) {
        if (!AerolargeConfig.ROCKET_PLUMES.get()) {
            return;
        }
        Vec3 foot = flight.position().subtract(flight.up().scale(footBelow));
        if (thrust > 0 && radio < SMOKE_UNDER) {
            smoke(level, foot);
        }
        if (flight.position().y > GLOW_FROM && flight.velocity().y < -GLOW_FALL) {
            flames(level, foot);
        }
    }

    /** Puffs of white cloud billowing out every way over the ground under her foot, and smoke that lingers after them. */
    private static void smoke(ServerLevel level, Vec3 foot) {
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(foot.x), Mth.floor(foot.z));
        for (int puff = 0; puff < PUFFS; puff++) {
            double way = level.getRandom().nextDouble() * Mth.TWO_PI;
            double x = Math.cos(way);
            double z = Math.sin(way);
            double speed = ROLL_OUT * (0.5 + level.getRandom().nextDouble());
            ParticleOptions kind = puff % LINGERING == 0 ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.CLOUD;
            send(level, kind, foot.x + x * SMOKE_FROM, ground + 0.5, foot.z + z * SMOKE_FROM, x * speed, RISE, z * speed);
        }
    }

    /** Flames round her foot, just outside her skin, left standing in the air as she falls, streaming up her sides. */
    private static void flames(ServerLevel level, Vec3 foot) {
        for (int flame = 0; flame < FLAMES; flame++) {
            double way = level.getRandom().nextDouble() * Mth.TWO_PI;
            double out = (RocketSilhouette.body(RocketSilhouette.height() / 2) + FLAMES_CLEAR) * AerolargeConfig.SCALE.get();
            ParticleOptions kind = flame % 3 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME;
            send(level, kind, foot.x + Math.cos(way) * out, foot.y, foot.z + Math.sin(way) * out, 0, 0, 0);
        }
    }

    /** One particle, moving as given in blocks a tick, sent to everyone near enough, however far that is. */
    private static void send(ServerLevel level, ParticleOptions kind, double x, double y, double z, double dx, double dy, double dz) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) < SEEN_WITHIN * SEEN_WITHIN) {
                level.sendParticles(player, kind, true, x, y, z, 0, dx, dy, dz, 1);
            }
        }
    }
}
