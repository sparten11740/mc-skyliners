package dev.aerolarge.client;

import dev.aerolarge.AerolargeClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Feeling the airliner from inside it: a rumble through the floor on the runway that grows with speed, a thump on
 * touchdown as hard as the landing, and a slight sway while flying. Only for the player riding it, only from the
 * camera (nothing in the world moves), and off with the client setting "cameraShake".
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
    private static final float THUMP_FADE = 0.82F;
    /** The airliner is on its wheels while it sits within this many blocks of the height it rests at on the ground. */
    private static final double ON_GROUND = 1.0;

    private static final RandomSource RANDOM = RandomSource.create();
    private static float thump;
    private static float rumble;
    private static float sway;
    private static float time;
    private static boolean wasOnGround = true;
    private static double lastSink;
    /** How far above the ground the airliner's middle sits when it stands on its wheels; learned while it stands still. */
    private static double restHeight = Double.NaN;

    private CameraShake() {
    }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        thump *= THUMP_FADE;
        time++;
        rumble = 0;
        sway = 0;
        Player player = minecraft.player;
        SableClient.Ride ride = player != null ? SableClient.ride(player) : null;
        if (ride == null) {
            restHeight = Double.NaN;
            wasOnGround = true;
            return;
        }
        double speed = Math.sqrt(ride.velocity().x * ride.velocity().x + ride.velocity().z * ride.velocity().z);
        if (speed < 0.5 && Math.abs(ride.velocity().y) < 0.3) {
            restHeight = ride.heightAboveGround();
        }
        boolean onGround = !Double.isNaN(restHeight) && ride.heightAboveGround() < restHeight + ON_GROUND;
        if (onGround && !wasOnGround) {
            thump = Math.min(MAX_THUMP, (float) lastSink * THUMP_PER_SINK);
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
        if (!AerolargeClientConfig.CAMERA_SHAKE.get() || rumble + thump + sway <= 0.001F) {
            return;
        }
        float partial = (float) event.getPartialTick();
        float t = time + partial;
        float jitter = rumble * (RANDOM.nextFloat() * 2 - 1);
        float pitch = jitter + thump * Mth.sin(t * 1.7F) + sway * Mth.sin(t * 0.05F);
        float roll = rumble * (RANDOM.nextFloat() * 2 - 1) * 0.6F + sway * Mth.sin(t * 0.037F + 1.3F);
        event.setPitch(event.getPitch() + pitch);
        event.setRoll(event.getRoll() + roll);
    }
}
