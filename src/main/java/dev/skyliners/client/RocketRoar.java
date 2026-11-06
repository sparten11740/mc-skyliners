package dev.skyliners.client;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Craft;
import dev.skyliners.autopilot.RocketFlight;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A rocket's engines heard from this client, from how hard her autopilot shows them firing (see
 * {@code dev.skyliners.autopilot.RocketSounds}). While they fire, their roar: a seamless loop played where her exhaust
 * leaves, at the foot of her axis between her fins, so that in flight Sable carries it along, as the airliner's engines
 * are (see {@link EngineSounds}), louder and higher the harder they push, and heard far across the ground. As her
 * engines light at lift-off, their ignition, the loop rising in under it; as they stop, their cut-off, the loop dying
 * away into it; and stopping as she lands, her touchdown on her fins with it. Close by her, low over the ground as
 * she lifts off and lands, a heavier, crackling roar thunders beside it, heard only near her, and the ground shakes
 * under whoever stands near; her lift-off's shockwave booms and jolts them as it reaches them (see {@link #shockwave}
 * and the autopilot's RocketLaunchBlast). Aboard her, all of it comes through her hull instead, muffled and quieter,
 * round the listener wherever they stand in her, and her engines lighting shakes their view (see
 * {@link CameraShake}). Boosting (see the autopilot's RocketControls), her engines kick up with a roar of their own,
 * her loop goes to full and a little higher, and her boost shakes the view of those aboard for as long as it lasts;
 * as it ends, its boosters cut out.
 */
public final class RocketRoar {
    /** What each rocket's engines were last heard doing, until her autopilot goes away. */
    private static final Map<AutopilotBlockEntity, Engines> ROCKETS = new WeakHashMap<>();
    /**
     * How loud the roar is, as a share of full, its engines barely firing and at full thrust; its pitch likewise; and
     * the pitch it falls to as it dies away at cut-off. How far it carries is set with the sound (see
     * tools/generate_resources.mjs).
     */
    private static final float LEAST_VOLUME = 0.5F;
    private static final float MOST_VOLUME = 1.0F;
    private static final float LEAST_PITCH = 0.8F;
    private static final float MOST_PITCH = 1.1F;
    /** How loud the ignition and the touchdown are, as shares of full; the cut-off is as loud as the roar it ends. */
    private static final float IGNITION_VOLUME = 1.0F;
    private static final float TOUCHDOWN_VOLUME = 1.0F;
    /**
     * How the loop hands over to the ignition and the cut-off, which tools/generate_sounds.mjs shapes to it (its
     * ROCKET.handoverAt, handover and fadeOut), in ticks: lighting, it waits {@link #IGNITION_LEAD} before it rises in
     * over {@link #IGNITION_RISE}; stopping, it dies away over {@link #CUTOFF_FADE}.
     */
    private static final int IGNITION_LEAD = 15;
    private static final int IGNITION_RISE = 25;
    private static final int CUTOFF_FADE = 4;
    /**
     * How hard her engines lighting shakes the view of those aboard, as a share of the roughest clear air (see
     * {@link CameraShake#turbulence}), and for how long, in ticks, fading out at its end: her lift-off felt through
     * her floor; the client setting {@code cameraShake} leaves it out with every shake.
     */
    private static final float LIFT_OFF_SHAKE = 0.8F;
    private static final int LIFT_OFF_SHAKE_TICKS = 60;
    /**
     * Her roar close by: as a share of her roar, how loud its thunder beside it is while her exhaust is up to
     * {@link #NEAR_LOW} blocks over the ground, fading out by {@link #NEAR_HIGH}; how far it carries is set with the
     * sound (see tools/generate_resources.mjs).
     */
    private static final float NEAR_SHARE = 1.0F;
    private static final double NEAR_LOW = 15;
    private static final double NEAR_HIGH = 60;
    /**
     * The ground shaking under those standing near her while her engines fire low over it, as a share of the roughest
     * clear air (see {@link CameraShake#turbulence}): this hard right by her exhaust, easing off to nothing this far
     * from it, in blocks.
     */
    private static final float GROUND_SHAKE = 0.9F;
    private static final double SHAKE_FELT = 80;
    /** How loud her shockwave's boom is, as a share of full, heard from beyond its reach and right by her. */
    private static final float FAR_BOOM = 0.35F;
    private static final float NEAR_BOOM = 1.0F;
    /** How long after one cut-off before another is heard, in ticks, so that her bouncing on her fins makes none. */
    private static final int AGAIN = 60;
    /**
     * Boosting: how much higher her roar is, in pitch, how loud the boost's kick and its cut-out are, as shares of
     * full, and how hard it shakes the view of those aboard, as a share of the roughest clear air.
     */
    private static final float BOOST_PITCH = 0.1F;
    private static final float BOOST_VOLUME = 1.0F;
    private static final float BOOST_CUTOUT_VOLUME = 0.8F;
    private static final float BOOST_SHAKE = 0.6F;
    /** Long before any game time: never. */
    private static final long NEVER = Long.MIN_VALUE / 2;

    private RocketRoar() {
    }

    /** Once a tick: every rocket loaded on this client heard as her engines fire. */
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        EngineRoar.forEach(ROCKETS, Craft.ROCKET, Engines::new,
            (rocket, engines) -> engines.tick(minecraft, rocket, rocket.getLevel().getGameTime()));
    }

    /**
     * Her lift-off's shockwave reaching this player, this hard, from 0 beyond its reach to 1 right by her: its boom,
     * louder the nearer, and a jolt through their view (see {@link CameraShake#blast}).
     */
    public static void shockwave(float strength) {
        Minecraft minecraft = Minecraft.getInstance();
        float volume = Mth.lerp(Mth.clamp(strength, 0, 1), FAR_BOOM, NEAR_BOOM);
        minecraft.getSoundManager().play(new SimpleSoundInstance(SkylinersRegistry.ROCKET_SHOCKWAVE.get().getLocation(), SoundSource.BLOCKS, volume,
            1.0F, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
        CameraShake.blast(strength);
    }

    /** Where her exhaust leaves, among her blocks: the foot of her axis, level with her fins' feet. */
    static Vec3 exhaust(AutopilotBlockEntity rocket) {
        BoundingBox frame = rocket.frame();
        return frame == null ? Vec3.atCenterOf(rocket.getBlockPos())
            : new Vec3((frame.minX() + frame.maxX() + 1) / 2.0, frame.minY(), (frame.minZ() + frame.maxZ() + 1) / 2.0);
    }

    /**
     * One of her engines' sounds, once, as loud as given: where her exhaust leaves, or, aboard her, as it is heard
     * through her hull, round the listener.
     */
    private static void once(Minecraft minecraft, AutopilotBlockEntity rocket, Supplier<SoundEvent> outside, Supplier<SoundEvent> aboard,
                             float volume, boolean inside) {
        EngineRoar.once(minecraft, exhaust(rocket), outside.get(), aboard.get(), volume, inside, EngineRoar.ABOARD_SHARE);
    }

    /** What one rocket's engines were last heard doing: her thrust, her roar outside and aboard, and their moments. */
    private static final class Engines {
        private float heard = EngineRoar.UNHEARD;
        @Nullable
        private EngineRoar outside;
        @Nullable
        private EngineRoar aboard;
        @Nullable
        private EngineRoar near;
        /** When her engines last lit at lift-off, and the game time before which no cut-off is heard again. */
        private long litAt = NEVER;
        private long quietUntil = NEVER;
        /** Whether she was boosting last tick. */
        private boolean boosted;

        /** Each tick of hers: her ignition or her cut-off as her engines light or stop, and her roar kept up. */
        void tick(Minecraft minecraft, AutopilotBlockEntity rocket, long now) {
            float thrust = RocketFlight.thrustOf(rocket);
            boolean inside = SableClient.isAboard(minecraft.player, rocket);
            if (heard == 0 && thrust > 0 && rocket.phase() == AutopilotBlockEntity.Phase.TAKEOFF) {
                litAt = now;
                once(minecraft, rocket, SkylinersRegistry.ROCKET_IGNITION, SkylinersRegistry.ROCKET_IGNITION_ABOARD, IGNITION_VOLUME, inside);
                if (inside) {
                    CameraShake.turbulence(LIFT_OFF_SHAKE, LIFT_OFF_SHAKE_TICKS);
                }
            }
            if (heard > 0 && thrust <= 0) {
                stopping(minecraft, rocket, inside, now);
            }
            heard = thrust;
            boolean boosting = thrust > 0 && RocketFlight.controlsOf(rocket).boosting(now);
            boost(minecraft, rocket, boosting, inside, now);
            // Lighting, the loop waits a moment, then rises in under the ignition as that falls away; stopping, it dies
            // away into the cut-off, falling in pitch.
            boolean stopped = thrust <= 0;
            float share = Mth.clamp(thrust, 0, 1);
            float volume = stopped || now - litAt < IGNITION_LEAD ? 0 : boosting ? MOST_VOLUME : Mth.lerp(share, LEAST_VOLUME, MOST_VOLUME);
            float pitch = stopped ? EngineRoar.CUT_PITCH : Mth.lerp(share, LEAST_PITCH, MOST_PITCH) + (boosting ? BOOST_PITCH : 0);
            float step = stopped ? 1F / CUTOFF_FADE : now - litAt < IGNITION_LEAD + IGNITION_RISE ? 1F / IGNITION_RISE : EngineRoar.FADE;
            float pitchStep = stopped ? (MOST_PITCH - EngineRoar.CUT_PITCH) / CUTOFF_FADE : EngineRoar.PITCH_RATE;
            Vec3 at = exhaust(rocket);
            Vec3 exhaust = SableClient.toWorld(minecraft.level, at);
            float low = EngineRoar.lowness(minecraft.level, exhaust, NEAR_LOW, NEAR_HIGH);
            outside = EngineRoar.keep(minecraft, rocket, outside, SkylinersRegistry.ROCKET_ROAR.get(), at, false, inside ? 0 : volume, pitch, step,
                pitchStep);
            float nearVolume = inside ? 0 : volume * NEAR_SHARE * low;
            near = EngineRoar.keep(minecraft, rocket, near, SkylinersRegistry.ROCKET_ROAR_NEAR.get(), at, false, nearVolume, pitch, step, pitchStep);
            float aboardVolume = inside ? volume * EngineRoar.ABOARD_SHARE : 0;
            aboard = EngineRoar.keep(minecraft, rocket, aboard, SkylinersRegistry.ROCKET_ROAR_ABOARD.get(), at, true, aboardVolume, pitch, step,
                pitchStep);
            if (!inside && !stopped) {
                EngineRoar.shakeGround(minecraft, exhaust, low, GROUND_SHAKE, SHAKE_FELT);
            }
        }

        /**
         * A boost beginning, its kick heard and, aboard, its shake felt for as long as it lasts; or ending, its
         * boosters cutting out.
         */
        private void boost(Minecraft minecraft, AutopilotBlockEntity rocket, boolean boosting, boolean inside, long now) {
            if (boosting && !boosted) {
                once(minecraft, rocket, SkylinersRegistry.ROCKET_BOOST, SkylinersRegistry.ROCKET_BOOST_ABOARD, BOOST_VOLUME, inside);
                if (inside) {
                    CameraShake.turbulence(BOOST_SHAKE, RocketFlight.controlsOf(rocket).boostLeft(now));
                }
            }
            if (boosted && !boosting) {
                once(minecraft, rocket, SkylinersRegistry.BOOSTER_CUTOUT, SkylinersRegistry.BOOSTER_CUTOUT, BOOST_CUTOUT_VOLUME, inside);
            }
            boosted = boosting;
        }

        /** Her engines stopping: their cut-off, and down on her fins in her landing burn, her touchdown with it. */
        private void stopping(Minecraft minecraft, AutopilotBlockEntity rocket, boolean inside, long now) {
            if (now < quietUntil) {
                return;
            }
            quietUntil = now + AGAIN;
            if (rocket.phase() == AutopilotBlockEntity.Phase.LAND) {
                once(minecraft, rocket, SkylinersRegistry.ROCKET_TOUCHDOWN, SkylinersRegistry.ROCKET_TOUCHDOWN, TOUCHDOWN_VOLUME, inside);
            }
            float roaring = Mth.lerp(Mth.clamp(heard, 0, 1), LEAST_VOLUME, MOST_VOLUME);
            once(minecraft, rocket, SkylinersRegistry.ROCKET_CUTOFF, SkylinersRegistry.ROCKET_CUTOFF_ABOARD, roaring, inside);
        }
    }
}
