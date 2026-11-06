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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
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
    private static final float CUT_PITCH = 0.7F;
    /** How loud the ignition and the touchdown are, as shares of full; the cut-off is as loud as the roar it ends. */
    private static final float IGNITION_VOLUME = 1.0F;
    private static final float TOUCHDOWN_VOLUME = 1.0F;
    /** How loud all of it is heard aboard her, through her hull, as a share of how loud it is outside. */
    private static final float ABOARD_SHARE = 0.7F;
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
     * How fast the loop swells or fades otherwise, as the listener boards her or leaves her, or first hears her, a share
     * of full a tick; and how fast its pitch follows her thrust, a share a tick.
     */
    private static final float FADE = 0.1F;
    private static final float PITCH_RATE = 0.01F;
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
     * from it, in blocks; and for how many ticks it is kept up each tick, as long as the shake takes to die away
     * (CameraShake's TURBULENCE_FADE_TICKS), so it holds at full while renewed and fades once she has climbed away.
     */
    private static final float GROUND_SHAKE = 0.9F;
    private static final double SHAKE_FELT = 80;
    private static final int SHAKE_KEPT = 20;
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
    /** Her thrust before her engines are first heard. */
    private static final float UNHEARD = -1;
    /** Long before any game time: never. */
    private static final long NEVER = Long.MIN_VALUE / 2;

    private RocketRoar() {
    }

    /** Once a tick: every rocket loaded on this client heard as her engines fire. */
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            ROCKETS.clear();
            return;
        }
        ROCKETS.keySet().removeIf(rocket -> rocket.isRemoved() || rocket.getLevel() != level);
        for (AutopilotBlockEntity autopilot : AutopilotBlockEntity.loaded(level)) {
            if (autopilot.craft() == Craft.ROCKET && autopilot.built()) {
                ROCKETS.computeIfAbsent(autopilot, rocket -> new Engines()).tick(minecraft, autopilot, level.getGameTime());
            }
        }
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
        if (inside) {
            minecraft.getSoundManager().play(new SimpleSoundInstance(aboard.get().getLocation(), SoundSource.BLOCKS, volume * ABOARD_SHARE, 1.0F,
                RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
            return;
        }
        Vec3 at = exhaust(rocket);
        minecraft.getSoundManager().play(new SimpleSoundInstance(outside.get(), SoundSource.BLOCKS, volume, 1.0F, RandomSource.create(), at.x, at.y, at.z));
    }

    /** What one rocket's engines were last heard doing: her thrust, her roar outside and aboard, and their moments. */
    private static final class Engines {
        private float heard = UNHEARD;
        @Nullable
        private Roar outside;
        @Nullable
        private Roar aboard;
        @Nullable
        private Roar near;
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
            float pitch = stopped ? CUT_PITCH : Mth.lerp(share, LEAST_PITCH, MOST_PITCH) + (boosting ? BOOST_PITCH : 0);
            float step = stopped ? 1F / CUTOFF_FADE : now - litAt < IGNITION_LEAD + IGNITION_RISE ? 1F / IGNITION_RISE : FADE;
            float pitchStep = stopped ? (MOST_PITCH - CUT_PITCH) / CUTOFF_FADE : PITCH_RATE;
            Vec3 exhaust = SableClient.toWorld(minecraft.level, exhaust(rocket));
            float low = lowness(minecraft.level, exhaust);
            outside = keep(minecraft, rocket, outside, SkylinersRegistry.ROCKET_ROAR.get(), false, inside ? 0 : volume, pitch, step, pitchStep);
            near = keep(minecraft, rocket, near, SkylinersRegistry.ROCKET_ROAR_NEAR.get(), false, inside ? 0 : volume * NEAR_SHARE * low, pitch, step,
                pitchStep);
            aboard = keep(minecraft, rocket, aboard, SkylinersRegistry.ROCKET_ROAR_ABOARD.get(), true, inside ? volume * ABOARD_SHARE : 0, pitch, step,
                pitchStep);
            if (!inside && !stopped) {
                shakeGround(minecraft, exhaust, low);
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

        /** How low over the ground her exhaust is, for her roar's thunder close by: 1 near it, 0 high over it. */
        private static float lowness(ClientLevel level, Vec3 exhaust) {
            double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(exhaust.x), Mth.floor(exhaust.z));
            return (float) Mth.clamp(1 - (exhaust.y - ground - NEAR_LOW) / (NEAR_HIGH - NEAR_LOW), 0, 1);
        }

        /** The ground shaking under this player standing near her as her engines fire low over it. */
        private static void shakeGround(Minecraft minecraft, Vec3 exhaust, float low) {
            double off = minecraft.player.position().distanceTo(exhaust);
            float near = (float) Mth.clamp(1 - off / SHAKE_FELT, 0, 1);
            if (near * low > 0) {
                CameraShake.turbulence(GROUND_SHAKE * near * low, SHAKE_KEPT);
            }
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

        /**
         * A roar kept going toward how loud and how high it should be, at so much a tick: begun once it should be
         * heard, let go once it has died away.
         */
        @Nullable
        private static Roar keep(Minecraft minecraft, AutopilotBlockEntity rocket, @Nullable Roar roar, SoundEvent sound, boolean aboard, float volume,
                                 float pitch, float step, float pitchStep) {
            Roar kept = roar != null && roar.isStopped() ? null : roar;
            if (kept == null && volume > 0) {
                kept = new Roar(rocket, sound, aboard, pitch);
                minecraft.getSoundManager().play(kept);
            }
            if (kept != null) {
                kept.toward(volume, pitch, step, pitchStep);
            }
            return kept;
        }
    }

    /**
     * One of her roars, looped, going toward how loud and how high it should be: outside, where her exhaust leaves
     * among her blocks, her roar and its thunder close by; aboard, as it is heard through her hull, round the listener.
     */
    private static final class Roar extends AbstractTickableSoundInstance {
        private final AutopilotBlockEntity rocket;
        private float wanted;
        private float wantedPitch;
        private float step;
        private float pitchStep;

        Roar(AutopilotBlockEntity rocket, SoundEvent sound, boolean aboard, float pitch) {
            super(sound, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.rocket = rocket;
            looping = true;
            delay = 0;
            volume = 0;
            this.pitch = pitch;
            wantedPitch = pitch;
            relative = aboard;
            attenuation = aboard ? Attenuation.NONE : Attenuation.LINEAR;
            Vec3 at = aboard ? Vec3.ZERO : exhaust(rocket);
            x = at.x;
            y = at.y;
            z = at.z;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        /** How loud and how high it should be, and how far it may go toward each a tick. */
        void toward(float volume, float pitch, float step, float pitchStep) {
            wanted = volume;
            wantedPitch = pitch;
            this.step = step;
            this.pitchStep = pitchStep;
        }

        @Override
        public void tick() {
            if (rocket.isRemoved()) {
                stop();
                return;
            }
            volume = volume < wanted ? Math.min(wanted, volume + step) : Math.max(wanted, volume - step);
            pitch = pitch < wantedPitch ? Math.min(wantedPitch, pitch + pitchStep) : Math.max(wantedPitch, pitch - pitchStep);
            if (wanted <= 0 && volume <= 0) {
                stop();
            }
        }
    }
}
