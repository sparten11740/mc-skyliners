package dev.skyliners.client;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.turret.CannonRocket;
import dev.skyliners.turret.RocketBlast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A laser cannon's rockets on this client (see {@link CannonRocket}): each one's smoke trail and the flame out of its
 * nozzle, made here every tick along the way it flew that tick, a burst of smoke out of its tube as it leaves, and its
 * motor's roar, which follows it, rising as it comes and falling as it goes, until it goes off; and where one goes off,
 * as the server says (see {@link RocketBlast}), a flash, a fireball billowing out, soot rolling up from it and sparks,
 * the view of whoever is near jolted. The smoke and fire are puffs drawn as the self-destruct's are (see
 * {@link BlastPuffParticle}), made here rather than sent, so they cost the network nothing, and seen as far as the
 * rocket is.
 */
public final class CannonRockets {
    /** How far apart along its flight its smoke puffs are made, in blocks, and the most made in a tick. */
    private static final double TRAIL_EVERY = 0.5;
    private static final int TRAIL_MOST = 8;
    /** How fast its smoke puffs drift off as they are made, at most, in blocks a tick. */
    private static final double TRAIL_DRIFT = 0.02;
    /**
     * How near the eye no smoke puff is made, in blocks: the gunner's sight is just behind the pods, and smoke swelling
     * round it would hide the view for seconds after every launch.
     */
    private static final double SMOKE_CLEAR_OF_EYE = 4;
    /** How far behind its middle its nozzle is, in blocks. */
    private static final double NOZZLE_BACK = 0.45;
    /** How many flame puffs leave its nozzle each tick, and how fast back from it, in blocks a tick. */
    private static final int FLAMES = 2;
    private static final double FLAME_SPEED = 0.15;
    /**
     * How many puffs burst out of its tube as it leaves, how many of them flame, the rest smoke, and how fast, in
     * blocks a tick.
     */
    private static final int LAUNCH_PUFFS = 10;
    private static final int LAUNCH_FLAMES = 3;
    private static final double LAUNCH_PUFF_SPEED = 0.12;
    /**
     * Where one goes off: how many puffs of fireball and of soot, and how fast each flies out at most, in blocks a
     * tick; how many sparks.
     */
    private static final int FIREBALLS = 20;
    private static final double FIREBALL_SPEED = 0.55;
    private static final int SOOT = 12;
    private static final double SOOT_SPEED = 0.2;
    private static final int SPARKS = 10;
    /**
     * How near where one goes off a player's view is jolted, in blocks; how hard right by it, as the roughest air of
     * clear skies is 1, and for how long, in ticks.
     */
    private static final double JOLT_REACH = 24;
    private static final float JOLT = 1.2F;
    private static final int JOLT_TICKS = 12;
    /**
     * How loud its roar is, past full so it carries further than the game's own sounds, and how much higher or lower it
     * sounds for each block a tick it comes toward or goes away from whoever hears it, and how far at most either way.
     */
    private static final float ROAR_VOLUME = 2.0F;
    private static final float ROAR_SHIFT = 0.07F;
    private static final float ROAR_HIGHEST = 1.35F;
    private static final float ROAR_LOWEST = 0.7F;

    private CannonRockets() {
    }

    /** Each client tick of a rocket: its smoke and flame along its way, and as it leaves, its burst and its roar. */
    public static void tick(CannonRocket rocket) {
        ClientLevel level = (ClientLevel) rocket.level();
        Vec3 before = new Vec3(rocket.xo, rocket.yo, rocket.zo);
        Vec3 nose = rocket.nose(1);
        if (rocket.flown(0) <= 1) {
            leaving(level, before, nose);
            Minecraft.getInstance().getSoundManager().play(new Roar(rocket));
        }
        if (rocket.burning()) {
            trail(level, before.subtract(nose.scale(NOZZLE_BACK)), rocket.position().subtract(nose.scale(NOZZLE_BACK)), nose);
        }
    }

    /** The burst of smoke and flame out of its tube as it leaves, blown back the way it came. */
    private static void leaving(ClientLevel level, Vec3 tube, Vec3 nose) {
        RandomSource random = level.random;
        for (int puff = 0; puff < LAUNCH_PUFFS; puff++) {
            Vec3 out = nose.scale(-1).add(scattered(random, 0.6)).normalize().scale(LAUNCH_PUFF_SPEED * (0.5 + random.nextDouble()));
            if (puff < LAUNCH_FLAMES) {
                level.addAlwaysVisibleParticle(SkylinersRegistry.CANNON_ROCKET_FLAME.get(), tube.x, tube.y, tube.z, out.x, out.y, out.z);
            } else {
                smoke(level, tube, out);
            }
        }
    }

    /** Its smoke along the way its nozzle went this tick, and its flame leaving the nozzle where it is now. */
    private static void trail(ClientLevel level, Vec3 from, Vec3 to, Vec3 nose) {
        RandomSource random = level.random;
        int puffs = Math.min(TRAIL_MOST, (int) Math.ceil(from.distanceTo(to) / TRAIL_EVERY));
        for (int puff = 0; puff < puffs; puff++) {
            smoke(level, from.lerp(to, (puff + random.nextDouble()) / puffs), scattered(random, TRAIL_DRIFT));
        }
        for (int flame = 0; flame < FLAMES; flame++) {
            Vec3 back = nose.scale(-FLAME_SPEED).add(scattered(random, TRAIL_DRIFT));
            level.addAlwaysVisibleParticle(SkylinersRegistry.CANNON_ROCKET_FLAME.get(), to.x, to.y, to.z, back.x, back.y, back.z);
        }
    }

    /**
     * A rocket has gone off near this player, as the server says: a flash, its fireball and soot bursting out, sparks,
     * and their view jolted the harder the nearer they are.
     */
    public static void blast(RocketBlast blast) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Vec3 at = blast.at();
        level.addAlwaysVisibleParticle(ParticleTypes.FLASH, at.x, at.y, at.z, 0, 0, 0);
        burst(level, SkylinersRegistry.CANNON_ROCKET_FIREBALL.get(), at, FIREBALLS, FIREBALL_SPEED);
        burst(level, SkylinersRegistry.CANNON_ROCKET_SOOT.get(), at, SOOT, SOOT_SPEED);
        for (int spark = 0; spark < SPARKS; spark++) {
            level.addAlwaysVisibleParticle(ParticleTypes.LAVA, at.x, at.y, at.z, 0, 0, 0);
        }
        double away = minecraft.gameRenderer.getMainCamera().getPosition().distanceTo(at);
        if (away < JOLT_REACH) {
            CameraShake.jolt(JOLT * (float) (1 - away / JOLT_REACH), JOLT_TICKS);
        }
    }

    /** A puff of its smoke at a point, drifting so fast, in blocks a tick; none near the eye. */
    private static void smoke(ClientLevel level, Vec3 at, Vec3 drift) {
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (eye.distanceToSqr(at) < SMOKE_CLEAR_OF_EYE * SMOKE_CLEAR_OF_EYE) {
            return;
        }
        level.addAlwaysVisibleParticle(SkylinersRegistry.CANNON_ROCKET_TRAIL.get(), at.x, at.y, at.z, drift.x, drift.y, drift.z);
    }

    /** So many puffs bursting out of a point every way, each up to so fast, in blocks a tick. */
    private static void burst(ClientLevel level, ParticleOptions puff, Vec3 at, int count, double fastest) {
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 out = scattered(random, 1).normalize().scale(fastest * (0.4 + 0.6 * random.nextDouble()));
            level.addAlwaysVisibleParticle(puff, at.x, at.y, at.z, out.x, out.y, out.z);
        }
    }

    /** A random way, so long at most along each axis. */
    private static Vec3 scattered(RandomSource random, double most) {
        return new Vec3((random.nextDouble() * 2 - 1) * most, (random.nextDouble() * 2 - 1) * most, (random.nextDouble() * 2 - 1) * most);
    }

    /**
     * A rocket's motor roaring, a seamless loop heard from where it flies, higher as it comes toward whoever hears it
     * and lower as it goes away; it stops once the rocket goes off or burns out.
     */
    private static final class Roar extends AbstractTickableSoundInstance {
        private final CannonRocket rocket;

        Roar(CannonRocket rocket) {
            super(SkylinersRegistry.CANNON_ROCKET_FLIGHT.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.rocket = rocket;
            looping = true;
            delay = 0;
            volume = ROAR_VOLUME;
            follow();
        }

        @Override
        public void tick() {
            if (rocket.isRemoved() || !rocket.burning()) {
                stop();
                return;
            }
            follow();
        }

        /** Where the rocket is now, and how high it sounds by how fast it comes toward whoever hears it. */
        private void follow() {
            Vec3 at = rocket.position();
            x = at.x;
            y = at.y;
            z = at.z;
            Vec3 toEar = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().subtract(at);
            double coming = toEar.lengthSqr() < Mth.EPSILON ? 0 : rocket.getDeltaMovement().dot(toEar.normalize());
            pitch = Mth.clamp(1 + (float) coming * ROAR_SHIFT, ROAR_LOWEST, ROAR_HIGHEST);
        }
    }
}
