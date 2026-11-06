package dev.skyliners.client;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.airframe.RocketSilhouette;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Craft;
import dev.skyliners.autopilot.RocketFlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A rocket's exhaust plume, drawn on this client from how hard her autopilot shows her engines firing (see the
 * autopilot's RocketSounds): while they fire, puffs of fire stream from her base across its middle, white-hot where
 * they leave her and cooling into smoke as they swell (see {@link BlastPuffParticle}), more of them the harder her
 * engines push, straight back from her whichever way she points and faster on a bigger rocket, some twenty blocks long
 * at the scale she is first built at. Boosting (see the autopilot's RocketControls), half as many puffs again stream
 * from her, round a blue-white core of soul fire down her middle. Drawn here rather than sent from the server, so a
 * long flight costs the network nothing; the server config's {@code rocket.plumes} leaves it out with the rest of her
 * flight's show (see the autopilot's RocketPlumes).
 */
public final class RocketExhaust {
    /**
     * How many puffs stream from under her a tick at full thrust, and at the least while her engines fire at all; how
     * far across her base they leave, as a share of her body's widest radius; how fast they stream away from her, in
     * blocks a tick on a rocket built at scale 1, and how much faster or slower each is, as a share either way; and
     * how far to either side of straight back they spread, as a share of that speed.
     */
    private static final int PUFFS = 48;
    private static final int LEAST_PUFFS = 12;
    private static final double ACROSS = 0.55;
    private static final double SPEED = 0.85;
    private static final double SPEED_SPREAD = 0.2;
    private static final double SIDEWAYS = 0.12;
    /**
     * Boosting: how many more puffs stream from her, as a multiple; and her plume's blue-white core, how many flames a
     * tick, how far across her base they leave, as a share of the puffs' spread, and how fast, as a share of theirs.
     */
    private static final double BOOST_PUFFS = 1.5;
    private static final int CORE_FLAMES = 8;
    private static final double CORE_ACROSS = 0.25;
    private static final double CORE_SPEED = 1.2;

    private RocketExhaust() {
    }

    /** Once a tick: the plume of every rocket loaded on this client whose engines fire. */
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused() || !SkylinersConfig.ROCKET_PLUMES.get()) {
            return;
        }
        for (AutopilotBlockEntity autopilot : AutopilotBlockEntity.loaded(level)) {
            if (autopilot.craft() == Craft.ROCKET && autopilot.built() && RocketFlight.thrustOf(autopilot) > 0) {
                plume(level, autopilot);
            }
        }
    }

    /**
     * A tick of one rocket's plume: puffs leaving her base, her foot and its ways in the world worked out from where
     * they lie among her blocks.
     */
    private static void plume(ClientLevel level, AutopilotBlockEntity rocket) {
        BoundingBox frame = rocket.frame();
        if (frame == null) {
            return;
        }
        Vec3 footAmongBlocks = RocketRoar.exhaust(rocket);
        Vec3 foot = SableClient.toWorld(level, footAmongBlocks);
        Vec3 back = foot.subtract(SableClient.toWorld(level, footAmongBlocks.add(0, 1, 0)));
        Vec3 right = SableClient.toWorld(level, footAmongBlocks.add(1, 0, 0)).subtract(foot);
        Vec3 ahead = SableClient.toWorld(level, footAmongBlocks.add(0, 0, 1)).subtract(foot);
        double scale = RocketSilhouette.scaleOf(rocket.getBlockPos().getY() - frame.minY());
        double across = RocketSilhouette.body(RocketSilhouette.height() / 2) * scale * ACROSS;
        boolean boosting = RocketFlight.controlsOf(rocket).boosting(level.getGameTime());
        int puffs = (int) Math.round(Math.max(LEAST_PUFFS, PUFFS * Mth.clamp(RocketFlight.thrustOf(rocket), 0, 1)) * (boosting ? BOOST_PUFFS : 1));
        RandomSource random = level.getRandom();
        if (boosting) {
            core(level, foot, back.scale(SPEED * scale * CORE_SPEED), right, ahead, across * CORE_ACROSS);
        }
        for (int puff = 0; puff < puffs; puff++) {
            double way = random.nextDouble() * Mth.TWO_PI;
            Vec3 side = right.scale(Math.cos(way)).add(ahead.scale(Math.sin(way)));
            Vec3 at = foot.add(side.scale(across * Math.sqrt(random.nextDouble())));
            double speed = SPEED * scale * (1 + SPEED_SPREAD * (2 * random.nextDouble() - 1));
            Vec3 going = back.scale(speed).add(side.scale(SPEED * scale * SIDEWAYS * random.nextDouble()));
            level.addAlwaysVisibleParticle(SkylinersRegistry.ROCKET_EXHAUST.get(), true, at.x, at.y, at.z, going.x, going.y, going.z);
        }
    }

    /** A tick of her boost's core: blue-white soul flames streaming straight back from the middle of her base. */
    private static void core(ClientLevel level, Vec3 foot, Vec3 going, Vec3 right, Vec3 ahead, double across) {
        RandomSource random = level.getRandom();
        for (int flame = 0; flame < CORE_FLAMES; flame++) {
            double way = random.nextDouble() * Mth.TWO_PI;
            Vec3 at = foot.add(right.scale(Math.cos(way)).add(ahead.scale(Math.sin(way))).scale(across * random.nextDouble()));
            level.addAlwaysVisibleParticle(ParticleTypes.SOUL_FIRE_FLAME, true, at.x, at.y, at.z, going.x, going.y, going.z);
        }
    }
}
