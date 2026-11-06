package dev.skyliners.client;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Craft;
import dev.skyliners.autopilot.FreighterFlight;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The freighter's engines heard from this client, from how hard her autopilot shows her lift thrusters and her mains
 * firing (see the autopilot's FreighterFlight), made of the same parts as the rocket's roar (see {@link EngineRoar}).
 * While they fire, her engines' roar, a seamless loop played at her stern, louder and higher the harder they push, and
 * heard far across the ground; and her lift jets' blast under her belly, loudest low over the ground as she lifts off
 * and lands. As her lift thrusters light, a deep thump, the view of those aboard shaking with it; as her mains light,
 * their whine spooling up. Close by her, low over the ground, the ground shakes under whoever stands near. Aboard her,
 * all of it comes through her hull instead as her engines' hum, round the listener wherever they stand in her. And
 * going by at speed, a long rolling thunder as she passes, heard once at her closest.
 */
public final class FreighterRoar {
    /** What each freighter's engines were last heard doing, until her autopilot goes away. */
    private static final Map<AutopilotBlockEntity, Engines> FREIGHTERS = new WeakHashMap<>();
    /**
     * Her lift thrusters' share of full that counts as her engines working at full, as heard: she hovers near a third,
     * and climbs hard on half.
     */
    private static final double LIFT_FULL = 0.5;
    /**
     * How much of her engines' working, as heard, her lift gives and her mains give, at their full each: her mains
     * pushing her ahead are what roar loudest.
     */
    private static final double LIFT_PART = 0.6;
    private static final double MAINS_PART = 0.6;
    /**
     * How loud her roar is, as a share of full, her engines barely working and at full; its pitch likewise, and the
     * pitch it falls to as it dies away when they stop. How far it carries is set with the sound (see
     * tools/generate_resources.mjs).
     */
    private static final float LEAST_VOLUME = 0.5F;
    private static final float MOST_VOLUME = 1.0F;
    private static final float LEAST_PITCH = 0.85F;
    private static final float MOST_PITCH = 1.15F;
    /**
     * How loud her lift jets' blast is, as a share of full: high over the ground, and while her belly is up to
     * {@link #NEAR_LOW} blocks over it, rising between them from {@link #NEAR_HIGH}.
     */
    private static final float HIGH_JETS = 0.2F;
    private static final float LOW_JETS = 1.0F;
    private static final double NEAR_LOW = 12;
    private static final double NEAR_HIGH = 48;
    /** How loud her lift thrusters' thump and her mains' spooling up are, as shares of full. */
    private static final float IGNITION_VOLUME = 1.0F;
    private static final float SPOOL_VOLUME = 0.9F;
    /**
     * How hard her lift thrusters lighting shakes the view of those aboard, as a share of the roughest clear air (see
     * {@link CameraShake#turbulence}), and for how long, in ticks: her lift-off felt through her deck.
     */
    private static final float LIFT_OFF_SHAKE = 0.5F;
    private static final int LIFT_OFF_SHAKE_TICKS = 50;
    /**
     * The ground shaking under those standing near her while her lift thrusters fire low over it, as a share of the
     * roughest clear air: this hard right under her, easing off to nothing this far from her belly, in blocks.
     */
    private static final float GROUND_SHAKE = 0.6F;
    private static final double SHAKE_FELT = 50;
    /** How many ticks her loops take to die away as her engines stop: her thrusters spinning down. */
    private static final int CUT_FADE = 20;
    /** How long after her mains last spooled up before they are heard doing so again, in ticks. */
    private static final int SPOOL_AGAIN = 100;
    /**
     * Her going by: how near her middle passes this player at its closest for it to be heard, in blocks, how fast she
     * must be going then, in blocks a second, and how long after before it is heard again, in ticks.
     */
    private static final double FLYBY_WITHIN = 60;
    private static final double FLYBY_SPEED = 10;
    private static final int FLYBY_AGAIN = 200;
    /** How high over her legs' feet her lift thrusters fire from, in blocks: her belly. */
    private static final double BELLY_UP = 2;

    private FreighterRoar() {
    }

    /** Once a tick: every freighter loaded on this client heard as her engines fire. */
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        EngineRoar.forEach(FREIGHTERS, Craft.FREIGHTER, Engines::new, (freighter, engines) -> engines.tick(minecraft, freighter));
    }

    /** How hard her engines work as heard, from 0 to 1, from her lift and her mains as her autopilot shows them. */
    private static float working(float lift, float mains) {
        return (float) Mth.clamp(LIFT_PART * lift / LIFT_FULL + MAINS_PART * mains, 0, 1);
    }

    /**
     * What one freighter's engines were last heard doing, her loops outside and aboard, and their moments, counted in
     * her own ticks on this client, as the level's time snaps back whenever the server runs behind.
     */
    private static final class Engines {
        /** Her engines' lift and mains as last heard; unheard until first heard, so nothing lights at first sight. */
        private float heardLift = EngineRoar.UNHEARD;
        private float heardMains = EngineRoar.UNHEARD;
        /** How many ticks she has been heard on this client. */
        private long ticks;
        @Nullable
        private EngineRoar roar;
        @Nullable
        private EngineRoar jets;
        @Nullable
        private EngineRoar aboard;
        /** The tick of hers from which her mains may next be heard spooling up, and her going by heard. */
        private long spoolQuietUntil;
        private long flybyQuietUntil;
        /** Where her middle was in the world last tick, and how far from this player, to hear her going by. */
        @Nullable
        private Vec3 lastMiddle;
        private double lastDistance = Double.MAX_VALUE;

        /** Each tick of hers: her thump and her spooling up as her engines light, her loops kept up, her going by. */
        void tick(Minecraft minecraft, AutopilotBlockEntity freighter) {
            ClientLevel level = minecraft.level;
            Vec3 middle = FreighterFlight.middleOf(freighter);
            if (middle == null) {
                return;
            }
            long now = ++ticks;
            float lift = FreighterFlight.liftOf(freighter);
            float mains = FreighterFlight.mainsOf(freighter);
            boolean inside = SableClient.isAboard(minecraft.player, freighter);
            Vec3 belly = new Vec3(middle.x, freighter.frame() == null ? middle.y : freighter.frame().minY() + BELLY_UP, middle.z);
            Vec3 stern = FreighterFlight.sternOf(freighter);
            lighting(minecraft, freighter, belly, stern, lift, mains, inside, now);
            heardLift = lift;
            heardMains = mains;
            boolean stopped = lift <= 0;
            float work = working(lift, mains);
            float volume = stopped ? 0 : Mth.lerp(work, LEAST_VOLUME, MOST_VOLUME);
            float pitch = stopped ? EngineRoar.CUT_PITCH : Mth.lerp(work, LEAST_PITCH, MOST_PITCH);
            float step = stopped ? 1F / CUT_FADE : EngineRoar.FADE;
            float pitchStep = stopped ? (MOST_PITCH - EngineRoar.CUT_PITCH) / CUT_FADE : EngineRoar.PITCH_RATE;
            Vec3 bellyInWorld = SableClient.toWorld(level, belly);
            float low = EngineRoar.lowness(level, bellyInWorld, NEAR_LOW, NEAR_HIGH);
            float blast = stopped ? 0 : Mth.lerp(low, HIGH_JETS, LOW_JETS);
            roar = EngineRoar.keep(minecraft, freighter, roar, SkylinersRegistry.FREIGHTER_ENGINE_ROAR.get(), stern, false, inside ? 0 : volume,
                pitch, step, pitchStep);
            jets = EngineRoar.keep(minecraft, freighter, jets, SkylinersRegistry.FREIGHTER_LIFT_JETS.get(), belly, false, inside ? 0 : blast, 1,
                step, 0);
            aboard = EngineRoar.keep(minecraft, freighter, aboard, SkylinersRegistry.FREIGHTER_ENGINE_IDLE.get(), middle, true,
                inside ? volume * EngineRoar.ABOARD_SHARE : 0, pitch, step, pitchStep);
            if (!inside && !stopped) {
                EngineRoar.shakeGround(minecraft, bellyInWorld, low, GROUND_SHAKE, SHAKE_FELT);
            }
            goingBy(minecraft, SableClient.toWorld(level, middle), middle, inside, now);
        }

        /**
         * Her lift thrusters lighting as she lifts off, a deep thump and, aboard, her deck shaking with it; her mains
         * lighting, their whine spooling up, not too often.
         */
        private void lighting(Minecraft minecraft, AutopilotBlockEntity freighter, Vec3 belly, Vec3 stern, float lift, float mains, boolean inside,
                              long now) {
            if (heardLift == 0 && lift > 0 && freighter.phase() == AutopilotBlockEntity.Phase.TAKEOFF) {
                SoundEvent thump = SkylinersRegistry.FREIGHTER_IGNITION.get();
                EngineRoar.once(minecraft, belly, thump, thump, IGNITION_VOLUME, inside, EngineRoar.ABOARD_SHARE);
                if (inside) {
                    CameraShake.turbulence(LIFT_OFF_SHAKE, LIFT_OFF_SHAKE_TICKS);
                }
            }
            if (heardMains == 0 && mains > 0 && now >= spoolQuietUntil) {
                spoolQuietUntil = now + SPOOL_AGAIN;
                SoundEvent spool = SkylinersRegistry.FREIGHTER_SPOOL.get();
                EngineRoar.once(minecraft, stern, spool, spool, SPOOL_VOLUME, inside, EngineRoar.ABOARD_SHARE);
            }
        }

        /**
         * Her going by this player outside her, fast: heard once, just as she is at her closest and starts drawing
         * away, from among her blocks, so the thunder rolls away with her.
         */
        private void goingBy(Minecraft minecraft, Vec3 middleInWorld, Vec3 middle, boolean inside, long now) {
            double distance = minecraft.player.position().distanceTo(middleInWorld);
            double speed = lastMiddle == null ? 0 : middleInWorld.distanceTo(lastMiddle) * SharedConstants.TICKS_PER_SECOND;
            boolean closest = distance > lastDistance && lastDistance < FLYBY_WITHIN;
            if (closest && !inside && speed > FLYBY_SPEED && now >= flybyQuietUntil) {
                flybyQuietUntil = now + FLYBY_AGAIN;
                SoundEvent flyby = SkylinersRegistry.FREIGHTER_FLYBY.get();
                EngineRoar.once(minecraft, middle, flyby, flyby, MOST_VOLUME, false, EngineRoar.ABOARD_SHARE);
            }
            lastMiddle = middleInWorld;
            lastDistance = distance;
        }
    }
}
