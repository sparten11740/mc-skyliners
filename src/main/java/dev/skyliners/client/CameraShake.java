package dev.skyliners.client;

import dev.skyliners.SkylinersClientConfig;
import dev.skyliners.SkylinersRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Feeling the airliner from inside it: a rumble through the floor on the runway that grows with speed, a thump on
 * touchdown as hard as the landing, a slight sway while flying, a jolting shake through rough air, and the view
 * dropping away and swinging back as it falls into an air pocket (the server says when, see
 * {@link dev.skyliners.cabin.Turbulence} and {@link dev.skyliners.cabin.AirPockets}). Only for the player riding it, only from the camera (nothing in
 * the world moves), and off with the client setting "cameraShake". An airliner blowing up shakes the view of everyone
 * near, aboard or not (see {@link BlastFlash}); a rocket's engines lighting shakes it as rough air does, for those
 * aboard her, and the ground shakes so under those standing near her while they fire low over it, her lift-off's
 * shockwave jolting them as a blast does as it passes (see {@link RocketRoar}). A ship running into something kicks
 * the view of everyone aboard her, as hard as she hit (see {@link dev.skyliners.impact.Impacts}).
 */
public final class CameraShake {
    /** The rumble's strength on the runway at full speed, and the speed (blocks a second) it reaches it at. */
    private static final float RUMBLE = 0.35F;
    private static final double RUMBLE_SPEED = 40;
    /** The sway's strength in flight, in degrees. */
    private static final float SWAY = 0.25F;
    /** Degrees of thump per block a second the airliner was sinking at touchdown, the most it gets, and its fade. */
    private static final float THUMP_PER_SINK = 0.4F;
    private static final float MAX_THUMP = 3F;
    /**
     * The touchdown heard, from what share of the hardest thump, and how loud at the softest heard and at the hardest:
     * the wheels' chirp and thump is a cue of its own, heard whatever the camera shake is set to.
     */
    private static final float HEARD_THUMP = 0.1F;
    /** How fast, in blocks a second, a touchdown counts as a landing run, its tyres chirping however soft it is. */
    private static final double LANDING_RUN = 10;
    private static final float SOFTEST = 0.4F;
    private static final float HARDEST = 1.0F;
    private static final float THUMP_FADE = 0.82F;
    /**
     * The shake through the roughest air of clear skies (strength 1; a storm's is stronger), in degrees, and how many
     * ticks it takes to die away at the end.
     */
    private static final float TURBULENCE = 1.6F;
    private static final float TURBULENCE_FADE_TICKS = 20;
    /**
     * How far the view drops as the airliner falls into an air pocket of depth 1, in degrees, how long the dip lasts in
     * ticks, and the share of it the view takes to drop: fast down, then slowly back as the air catches the airliner.
     */
    private static final float POCKET_DIP = 7;
    private static final int POCKET_TICKS = 30;
    private static final float POCKET_DROP = 0.2F;
    /**
     * An airliner blowing up near the player, as Ultrasplosive's nuke shakes the view: how long it shakes, in ticks,
     * and how hard at the edge of its reach and right by it, as a share of the full sway; how it dies away (slowly at
     * first, then quickly, the higher the slower); the full sway's yaw, pitch and roll, each a slow heave with a fast
     * rattle on it, as the heave's and the rattle's size in degrees and their speeds in radians a tick; and the most
     * any of them turns, in degrees.
     */
    private static final int BLAST_TICKS = 90;
    private static final float BLAST_EDGE = 0.35F;
    private static final float BLAST_NEAR = 1;
    private static final double BLAST_SETTLING = 1.3;
    private static final float[] BLAST_YAW = {3.2F, 0.9F, 1.8F, 5.1F};
    private static final float[] BLAST_PITCH = {2.6F, 0.8F, 2.2F, 6.3F};
    private static final float[] BLAST_ROLL = {1.3F, 0.4F, 1.4F, 4.4F};
    private static final float BLAST_MOST = 16;
    /**
     * A ship the player rides running into something: the view's kick at the hardest smash, in degrees, the share of
     * it the view rolls by, and the share of the kick left each tick as it dies away, within half a second.
     */
    private static final float JOLT = 6;
    private static final float JOLT_ROLL = 0.7F;
    private static final float JOLT_FADE = 0.8F;

    private static final RandomSource RANDOM = RandomSource.create();
    /** Whether the airliner the player rides is on its wheels. */
    private static final GroundContact GROUND = new GroundContact();
    private static float thump;
    private static float rumble;
    private static float sway;
    private static float rough;
    private static int roughLeft;
    private static float roughStrength;
    private static float pocket;
    private static int pocketTicks = POCKET_TICKS;
    private static float time;
    private static boolean wasOnGround = true;
    private static double lastSink;
    /** How hard the last blast shakes the view, and the ticks since it went off. */
    private static float blast;
    private static int blastTicks = BLAST_TICKS;
    /** How hard the view is kicked by the ship the player rides running into something, in degrees. */
    private static float jolt;

    private CameraShake() {
    }

    /**
     * An airliner has blown up near the player, or a rocket's lift-off shockwave has reached them, shaking their view
     * this hard: above 0 from the edge of its reach, 1 by it; 0, too far off to shake at all.
     */
    public static void blast(float strength) {
        if (strength <= 0) {
            return;
        }
        blast = Mth.lerp(strength, BLAST_EDGE, BLAST_NEAR);
        blastTicks = 0;
    }

    /**
     * The airliner the player rides has run into rough air, this rough (up to 1 in clear skies), for this many ticks; or
     * the rocket they ride has lit her engines, or the one they stand near fires them low over the ground.
     */
    public static void turbulence(float strength, int ticks) {
        roughStrength = strength;
        roughLeft = ticks;
    }

    /** The ship the player rides has run into something this hard: from 0, a bump, to 1, the hardest smash. */
    public static void impact(float strength) {
        jolt = Math.max(jolt, JOLT * Mth.clamp(strength, 0, 1));
    }

    /** The airliner the player rides has dropped into an air pocket this deep (about 1, deeper in a storm). */
    public static void airPocket(float depth) {
        pocket = depth;
        pocketTicks = 0;
    }

    /**
     * The wheels touching down, as hard as given, a share of the hardest, at a speed in blocks a second: their thump
     * and the tyres' chirp, heard at any landing run, however soft, and at a hard enough bump.
     */
    private static void touchdown(Player player, float hard, double speed) {
        if (speed >= LANDING_RUN || hard >= HEARD_THUMP) {
            player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), SkylinersRegistry.TOUCHDOWN.get(), SoundSource.BLOCKS,
                Mth.lerp(hard, SOFTEST, HARDEST), 1.0F, false);
        }
    }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        thump *= THUMP_FADE;
        jolt *= JOLT_FADE;
        time++;
        roughLeft = Math.max(0, roughLeft - 1);
        pocketTicks = Math.min(POCKET_TICKS, pocketTicks + 1);
        blastTicks = Math.min(BLAST_TICKS, blastTicks + 1);
        rough = TURBULENCE * roughStrength * Math.min(1, roughLeft / TURBULENCE_FADE_TICKS);
        rumble = 0;
        sway = 0;
        Player player = minecraft.player;
        SableClient.Ride ride = player != null ? SableClient.ride(player) : null;
        if (ride == null) {
            GROUND.forget();
            wasOnGround = true;
            return;
        }
        double speed = Math.hypot(ride.velocity().x, ride.velocity().z);
        boolean onGround = GROUND.onGround(ride);
        if (onGround && !wasOnGround) {
            thump = Math.min(MAX_THUMP, (float) lastSink * THUMP_PER_SINK);
            touchdown(player, thump / MAX_THUMP, speed);
        }
        if (onGround) {
            rumble = RUMBLE * (float) Math.min(1, speed / RUMBLE_SPEED);
        } else {
            sway = SWAY * (float) Math.min(1, speed / RUMBLE_SPEED);
        }
        wasOnGround = onGround;
        lastSink = Math.max(0, -ride.velocity().y);
    }

    public static void angles(ViewportEvent.ComputeCameraAngles event) {
        float partial = (float) event.getPartialTick();
        float dip = dip(partial);
        float shaken = blastTicks + partial;
        float heave = blastTicks < BLAST_TICKS ? blast * (float) Math.pow(1 - Math.min(1, shaken / BLAST_TICKS), BLAST_SETTLING) : 0;
        if (!SkylinersClientConfig.CAMERA_SHAKE.get() || rumble + thump + sway + rough + dip + heave + jolt <= 0.001F) {
            return;
        }
        float t = time + partial;
        float jitter = rumble * (RANDOM.nextFloat() * 2 - 1);
        // Rough air: two uneven waves, so the jolts never settle into a rhythm.
        float bump = rough * (Mth.sin(t * 0.9F) + 0.6F * Mth.sin(t * 2.3F + 1.1F));
        float pitch = jitter + thump * Mth.sin(t * 1.7F) + sway * Mth.sin(t * 0.05F) + bump + dip + jolt * Mth.sin(t * 2.9F);
        float roll = rumble * (RANDOM.nextFloat() * 2 - 1) * 0.6F + sway * Mth.sin(t * 0.037F + 1.3F) + rough * Mth.sin(t * 1.3F + 0.4F)
            + jolt * JOLT_ROLL * Mth.sin(t * 3.7F + 0.5F);
        // A blast: on each axis a heavy sway with a fast rattle riding on it.
        event.setYaw(event.getYaw() + Mth.clamp(heave * swayed(BLAST_YAW, shaken), -BLAST_MOST, BLAST_MOST));
        event.setPitch(event.getPitch() + pitch + Mth.clamp(heave * swayed(BLAST_PITCH, shaken), -BLAST_MOST, BLAST_MOST));
        event.setRoll(event.getRoll() + roll + Mth.clamp(heave * swayed(BLAST_ROLL, shaken), -BLAST_MOST, BLAST_MOST));
    }

    /** An axis's sway this many ticks into a blast, in degrees, at its full: its heave and its rattle together. */
    private static float swayed(float[] axis, float t) {
        return Mth.sin(t * axis[2]) * axis[0] + Mth.sin(t * axis[3]) * axis[1];
    }

    /** How far the view has dropped into an air pocket, in degrees down: quickly at first, then easing back up. */
    private static float dip(float partial) {
        float t = Math.min(1, (pocketTicks + partial) / POCKET_TICKS);
        if (t >= 1) {
            return 0;
        }
        float shape = t < POCKET_DROP ? t / POCKET_DROP : (1 - t) / (1 - POCKET_DROP);
        return POCKET_DIP * pocket * shape * shape * (3 - 2 * shape);
    }
}
