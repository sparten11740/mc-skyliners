package dev.skyliners.client;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.RocketDustRing;
import dev.skyliners.autopilot.RocketPlume;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Draws a tick's worth of a rocket's clouds, falling flames or lift-off dust, as told by the server (see {@link
 * RocketPlume} and {@link RocketDustRing}): great white clouds rolling out every way over the ground from anywhere
 * under her, with smoke lingering among them, or flames just outside her skin, left standing in the air as she falls,
 * with sparks among them, or a ring of dust racing out. Long-distance, so that those aboard, high over her base, see it
 * too.
 */
public final class RocketPlumeParticles {
    /**
     * How many clouds roll out over the ground a tick, and how many of them are vanilla's smoke that lingers among
     * them; how fast they roll out, at least and at most, and rise, in blocks a tick.
     */
    private static final int PUFFS = 14;
    private static final int LINGERING = 3;
    private static final double SLOWEST_ROLL = 0.25;
    private static final double FASTEST_ROLL = 0.85;
    private static final double RISE = 0.06;
    /**
     * Where flames stream up from round her base: at so many spots round her each tick, so many at each, and so many
     * sparks of lava among them; and how far round each spot they spread, in blocks.
     */
    private static final int FLAME_SPOTS = 6;
    private static final int FLAMES_A_SPOT = 4;
    private static final int SPARKS = 2;
    private static final double FLAME_SPREAD = 0.6;
    /**
     * A lift-off's ring of dust: its puffs a tick, spread evenly round it; how high over the ground it rolls, in
     * blocks; and how fast its puffs rise, gently and the tossed ones, in blocks a tick.
     */
    private static final int RING_PUFFS = 40;
    private static final double DUST_HIGH = 3.5;
    private static final double LIFT = 0.06;
    private static final double TOSS = 0.22;

    private RocketPlumeParticles() {
    }

    public static void show(RocketPlume plume) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && plume.falling()) {
            flames(level, plume);
        } else if (level != null) {
            clouds(level, plume);
        }
    }

    /** A tick's dust from a lift-off's ring, rolling along the ground under it and out with its front. */
    public static void ring(RocketDustRing ring) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        RandomSource random = level.getRandom();
        for (int puff = 0; puff < RING_PUFFS; puff++) {
            double way = (puff + random.nextDouble()) * Mth.TWO_PI / RING_PUFFS;
            double x = ring.x() + Math.cos(way) * ring.front();
            double z = ring.z() + Math.sin(way) * ring.front();
            double y = dustFloor(level, ring, x, z) + random.nextDouble() * DUST_HIGH;
            double rise = puff % 3 == 0 ? TOSS : LIFT * (1 + random.nextDouble());
            level.addParticle(SkylinersRegistry.ROCKET_DUST.get(), true, x, y, z, Math.cos(way) * ring.speed(), rise, Math.sin(way) * ring.speed());
        }
    }

    /**
     * The height the ring's dust rolls over at a column: its ground, but past something standing far taller than where
     * the blast began, a launch pad's tower or its mast, at the height it began, not up on top of it. Not the height
     * without leaves the server uses: clients are never sent it, and read the world's bottom.
     */
    private static double dustFloor(ClientLevel level, RocketDustRing ring, double x, double z) {
        double top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        return top - ring.y() > RocketDustRing.HIGHEST_GROUND ? ring.y() : top;
    }

    private static void clouds(ClientLevel level, RocketPlume plume) {
        RandomSource random = level.getRandom();
        for (int puff = 0; puff < PUFFS; puff++) {
            double way = random.nextDouble() * Mth.TWO_PI;
            double speed = Mth.lerp(random.nextDouble(), SLOWEST_ROLL, FASTEST_ROLL);
            double outX = Math.cos(way);
            double outZ = Math.sin(way);
            double from = plume.round() * random.nextDouble();
            ParticleOptions kind = puff < LINGERING ? ParticleTypes.CAMPFIRE_COSY_SMOKE : SkylinersRegistry.ROCKET_CLOUD.get();
            level.addParticle(kind, true, plume.x() + outX * from, plume.y(), plume.z() + outZ * from, outX * speed,
                RISE * (1 + random.nextDouble()), outZ * speed);
        }
    }

    private static void flames(ClientLevel level, RocketPlume plume) {
        RandomSource random = level.getRandom();
        for (int spot = 0; spot < FLAME_SPOTS + SPARKS; spot++) {
            double way = random.nextDouble() * Mth.TWO_PI;
            double x = plume.x() + Math.cos(way) * plume.round();
            double z = plume.z() + Math.sin(way) * plume.round();
            boolean spark = spot >= FLAME_SPOTS;
            for (int i = 0; i < (spark ? 1 : FLAMES_A_SPOT); i++) {
                level.addParticle(spark ? ParticleTypes.LAVA : ParticleTypes.FLAME, true, x + random.nextGaussian() * FLAME_SPREAD,
                    plume.y() + random.nextGaussian() * FLAME_SPREAD / 4, z + random.nextGaussian() * FLAME_SPREAD, 0, 0, 0);
            }
        }
    }
}
