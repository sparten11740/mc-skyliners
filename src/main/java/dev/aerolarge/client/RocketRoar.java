package dev.aerolarge.client;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.Craft;
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
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A rocket's engines heard from this client, from how hard her autopilot shows them firing (see
 * {@code dev.aerolarge.autopilot.RocketSounds}). While they fire, their roar: a seamless loop played where her exhaust
 * leaves, at the foot of her axis between her fins, so that in flight Sable carries it along, as the airliner's engines
 * are (see {@link EngineSounds}), louder and higher the harder they push, and heard far across the ground. As her
 * engines light at lift-off, their ignition, the loop rising in under it; as they stop, their cut-off, the loop dying
 * away into it; and stopping as she lands, her touchdown on her fins with it. Aboard her, all of it comes through her
 * hull instead, muffled and quieter, round the listener wherever they stand in her.
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
     * How fast the loop swells or fades otherwise, as the listener boards her or leaves her, or first hears her, a share
     * of full a tick; and how fast its pitch follows her thrust, a share a tick.
     */
    private static final float FADE = 0.1F;
    private static final float PITCH_RATE = 0.01F;
    /** How long after one cut-off before another is heard, in ticks, so that her bouncing on her fins makes none. */
    private static final int AGAIN = 60;
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

    /** Where her exhaust leaves, among her blocks: the foot of her axis, level with her fins' feet. */
    private static Vec3 exhaust(AutopilotBlockEntity rocket) {
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
        /** When her engines last lit at lift-off, and the game time before which no cut-off is heard again. */
        private long litAt = NEVER;
        private long quietUntil = NEVER;

        /** Each tick of hers: her ignition or her cut-off as her engines light or stop, and her roar kept up. */
        void tick(Minecraft minecraft, AutopilotBlockEntity rocket, long now) {
            float thrust = rocket.rocketThrust();
            boolean inside = SableClient.isAboard(minecraft.player, rocket);
            if (heard == 0 && thrust > 0 && rocket.phase() == AutopilotBlockEntity.Phase.TAKEOFF) {
                litAt = now;
                once(minecraft, rocket, AerolargeRegistry.ROCKET_IGNITION, AerolargeRegistry.ROCKET_IGNITION_ABOARD, IGNITION_VOLUME, inside);
            }
            if (heard > 0 && thrust <= 0) {
                stopping(minecraft, rocket, inside, now);
            }
            heard = thrust;
            // Lighting, the loop waits a moment, then rises in under the ignition as that falls away; stopping, it dies
            // away into the cut-off, falling in pitch.
            boolean stopped = thrust <= 0;
            float share = Mth.clamp(thrust, 0, 1);
            float volume = stopped || now - litAt < IGNITION_LEAD ? 0 : Mth.lerp(share, LEAST_VOLUME, MOST_VOLUME);
            float pitch = stopped ? CUT_PITCH : Mth.lerp(share, LEAST_PITCH, MOST_PITCH);
            float step = stopped ? 1F / CUTOFF_FADE : now - litAt < IGNITION_LEAD + IGNITION_RISE ? 1F / IGNITION_RISE : FADE;
            float pitchStep = stopped ? (MOST_PITCH - CUT_PITCH) / CUTOFF_FADE : PITCH_RATE;
            outside = keep(minecraft, rocket, outside, false, inside ? 0 : volume, pitch, step, pitchStep);
            aboard = keep(minecraft, rocket, aboard, true, inside ? volume * ABOARD_SHARE : 0, pitch, step, pitchStep);
        }

        /** Her engines stopping: their cut-off, and down on her fins in her landing burn, her touchdown with it. */
        private void stopping(Minecraft minecraft, AutopilotBlockEntity rocket, boolean inside, long now) {
            if (now < quietUntil) {
                return;
            }
            quietUntil = now + AGAIN;
            if (rocket.phase() == AutopilotBlockEntity.Phase.LAND) {
                once(minecraft, rocket, AerolargeRegistry.ROCKET_TOUCHDOWN, AerolargeRegistry.ROCKET_TOUCHDOWN, TOUCHDOWN_VOLUME, inside);
            }
            float roaring = Mth.lerp(Mth.clamp(heard, 0, 1), LEAST_VOLUME, MOST_VOLUME);
            once(minecraft, rocket, AerolargeRegistry.ROCKET_CUTOFF, AerolargeRegistry.ROCKET_CUTOFF_ABOARD, roaring, inside);
        }

        /**
         * A roar kept going toward how loud and how high it should be, at so much a tick: begun once it should be
         * heard, let go once it has died away.
         */
        @Nullable
        private static Roar keep(Minecraft minecraft, AutopilotBlockEntity rocket, @Nullable Roar roar, boolean aboard, float volume, float pitch,
                                 float step, float pitchStep) {
            Roar kept = roar != null && roar.isStopped() ? null : roar;
            if (kept == null && volume > 0) {
                kept = new Roar(rocket, aboard, pitch);
                minecraft.getSoundManager().play(kept);
            }
            if (kept != null) {
                kept.toward(volume, pitch, step, pitchStep);
            }
            return kept;
        }
    }

    /**
     * Her roar, looped, going toward how loud and how high it should be: outside, where her exhaust leaves among her
     * blocks; aboard, as it is heard through her hull, round the listener.
     */
    private static final class Roar extends AbstractTickableSoundInstance {
        private final AutopilotBlockEntity rocket;
        private float wanted;
        private float wantedPitch;
        private float step;
        private float pitchStep;

        Roar(AutopilotBlockEntity rocket, boolean aboard, float pitch) {
            super((aboard ? AerolargeRegistry.ROCKET_ROAR_ABOARD : AerolargeRegistry.ROCKET_ROAR).get(), SoundSource.BLOCKS,
                SoundInstance.createUnseededRandom());
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
