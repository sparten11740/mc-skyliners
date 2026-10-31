package dev.aerolarge.client;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * The machined ring round the hole in the ring of ceiling over the rocket's cockpit (see {@link OrreryDome}), which
 * turns the stepped edge of that hole into one crisp circle: a band of dark gunmetal standing just inside the hole's
 * nearest blocks, from the ceiling's underside to a little over its top, with brass lips along its top and bottom, and a
 * flange out over the ceiling on either side covering the steps behind it.
 *
 * <p>Round its middle thirty lamps show what her autopilot is doing, without a word and never flashing faster than three
 * times a second: a dim cyan while it is off; through her countdown, a tenth of the ring turning from red to green with
 * each second launch control counts, all of it a slowly breathing red while the count waits to begin; a white light
 * chasing round as she lifts off and climbs; a slow blue breathing in space; and an amber pulse as she comes down.
 */
public final class OculusRing {
    /** How many flat faces go round the band, and how many lamps, one on every other face. */
    private static final int FACES = 60;
    private static final int LAMPS = FACES / 2;
    private static final float[] COS = new float[FACES + 1];
    private static final float[] SIN = new float[FACES + 1];
    /** How far inside the nearest block of the ring of ceiling the band stands, in blocks. */
    private static final float INSET = 0.05F;
    /** The narrowest hole in the ring of ceiling the ring is put round, from her axis to its edge, in blocks. */
    private static final float NARROWEST = 1;
    /** How far over the ring of ceiling's top the band stands, in blocks. */
    private static final float RISE = 0.1F;
    /** How far under the ring of ceiling's underside the band's foot and its flange are, in blocks, so neither flickers. */
    private static final float UNDER = 0.004F;
    /** How tall the brass lips along the band's top and bottom are, in blocks. */
    private static final float LIP = 0.12F;
    /** How far out over the ring of ceiling the flanges reach from the band, in blocks: past every step of its edge. */
    private static final float FLANGE = 1.0F;
    /** How brightly the band, the flange under it and the flange over it are lit, each a share. */
    private static final float BAND_LIGHT = 0.85F;
    private static final float UNDER_LIGHT = 0.7F;
    private static final float OVER_LIGHT = 0.95F;
    /** How tall a lamp's window is, in blocks, and how much of its face's width it takes, a share. */
    private static final float WINDOW_HEIGHT = 0.22F;
    private static final float WINDOW_SHARE = 0.5F;
    /** How far in front of the band a lamp's window is, and its light in front of that, in blocks. */
    private static final float WINDOW_OFF = 0.003F;
    private static final float LIGHT_OFF = 0.006F;
    /** The lamps' colours, each a share: cyan, red, green, white, blue and amber. */
    private static final float[] CYAN = {0.15F, 0.55F, 0.6F};
    private static final float[] RED = {1.0F, 0.12F, 0.08F};
    private static final float[] GREEN = {0.15F, 1.0F, 0.3F};
    private static final float[] WHITE = {1.0F, 1.0F, 1.0F};
    private static final float[] BLUE = {0.25F, 0.5F, 1.0F};
    private static final float[] AMBER = {1.0F, 0.62F, 0.1F};
    /** How bright the lamps glow cyan with her autopilot off, a share. */
    private static final float IDLE = 0.4F;
    /**
     * Breathing, chasing and pulsing: how long a breath of red while the count waits takes, and of blue in space, how
     * long the white light takes to chase once round, and how long each amber pulse takes, in ticks.
     */
    private static final float WAITING_BREATH = 60;
    private static final float SPACE_BREATH = 80;
    private static final float CHASE = 30;
    private static final float PULSE = 20;
    /** How dim a breathing or pulsing lamp gets, a share of its brightest. */
    private static final float DIMMEST = 0.25F;
    /** How long the white light's tail is, as a share of the ring, and how bright the lamps behind it stay, a share. */
    private static final float TAIL = 0.35F;
    private static final float CHASE_FLOOR = 0.12F;
    /** How many seconds launch control counts down from, a tenth of the ring for each. */
    private static final int COUNT_FROM = AerolargeRegistry.LAUNCH_COUNT.size();
    /** How long after launch control's last word the count is still taken to be running, in ticks: past the next. */
    private static final int COUNT_HEARD_FOR = 30;
    /** The seconds left as launch control last said, and when that was, in the level's game time; none said yet. */
    private static int heardLeft = -1;
    private static long heardAt;

    static {
        for (int k = 0; k <= FACES; k++) {
            COS[k] = Mth.cos(Mth.TWO_PI * k / FACES);
            SIN[k] = Mth.sin(Mth.TWO_PI * k / FACES);
        }
    }

    private OculusRing() {
    }

    /** A sound is about to play: launch control saying how many seconds are left starts or moves on the count. */
    public static void heard(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        ClientLevel level = Minecraft.getInstance().level;
        if (sound == null || level == null) {
            return;
        }
        for (int left = 1; left <= COUNT_FROM; left++) {
            if (AerolargeRegistry.LAUNCH_COUNT.get(left - 1).getId().equals(sound.getLocation())) {
                heardLeft = left;
                heardAt = level.getGameTime();
                return;
            }
        }
    }

    /** Whether the ring of ceiling under a dome has a hole wide enough for the ring to stand in. */
    static boolean fits(OrreryDome dome) {
        return dome.oculus() >= NARROWEST;
    }

    /** The ring: its band, its lips, its two flanges and the windows of its lamps. */
    static void draw(OrreryDome dome, OrreryBrush sheet) {
        float radius = dome.oculus() - INSET;
        float foot = dome.floor() - UNDER;
        float top = dome.floor() + 1 + RISE;
        for (int face = 0; face < FACES; face++) {
            band(sheet, face, radius, foot, foot + LIP, OrrerySheet.BRASS);
            band(sheet, face, radius, foot + LIP, top - LIP, OrrerySheet.GUNMETAL);
            band(sheet, face, radius, top - LIP, top, OrrerySheet.BRASS);
            flange(sheet, face, radius, foot, OrrerySheet.GUNMETAL, UNDER_LIGHT);
            flange(sheet, face, radius, top, OrrerySheet.BRASS, OVER_LIGHT);
        }
        float middle = (foot + top) / 2;
        for (int lamp = 0; lamp < LAMPS; lamp++) {
            window(sheet, 2 * lamp, radius, middle, WINDOW_OFF, OrrerySheet.LAMP_WINDOW, 1, 1, 1);
        }
    }

    /** A piece of the band on one face, from one height to another, its picture across the face. */
    private static void band(OrreryBrush sheet, int face, float radius, float low, float high, OrreryBrush.Patch patch) {
        float x0 = OrreryDome.AXIS + COS[face] * radius;
        float z0 = OrreryDome.AXIS + SIN[face] * radius;
        float x1 = OrreryDome.AXIS + COS[face + 1] * radius;
        float z1 = OrreryDome.AXIS + SIN[face + 1] * radius;
        sheet.vertex(x0, low, z0, patch.u(0), patch.v(1), BAND_LIGHT, BAND_LIGHT, BAND_LIGHT);
        sheet.vertex(x1, low, z1, patch.u(1), patch.v(1), BAND_LIGHT, BAND_LIGHT, BAND_LIGHT);
        sheet.vertex(x1, high, z1, patch.u(1), patch.v(0), BAND_LIGHT, BAND_LIGHT, BAND_LIGHT);
        sheet.vertex(x0, high, z0, patch.u(0), patch.v(0), BAND_LIGHT, BAND_LIGHT, BAND_LIGHT);
    }

    /** A piece of a flange on one face, flat at a height, out from the band. */
    private static void flange(OrreryBrush sheet, int face, float radius, float y, OrreryBrush.Patch patch, float light) {
        float out = radius + FLANGE;
        sheet.vertex(OrreryDome.AXIS + COS[face] * radius, y, OrreryDome.AXIS + SIN[face] * radius, patch.u(0), patch.v(0), light, light, light);
        sheet.vertex(OrreryDome.AXIS + COS[face + 1] * radius, y, OrreryDome.AXIS + SIN[face + 1] * radius, patch.u(1), patch.v(0), light, light, light);
        sheet.vertex(OrreryDome.AXIS + COS[face + 1] * out, y, OrreryDome.AXIS + SIN[face + 1] * out, patch.u(1), patch.v(1), light, light, light);
        sheet.vertex(OrreryDome.AXIS + COS[face] * out, y, OrreryDome.AXIS + SIN[face] * out, patch.u(0), patch.v(1), light, light, light);
    }

    /**
     * A lamp's window, or the light in it, in the middle of one face of the band at a height, so far in front of it, in
     * one colour.
     */
    private static void window(OrreryBrush brush, int face, float radius, float middle, float off, OrreryBrush.Patch patch, float r, float g,
                               float b) {
        float along = Mth.TWO_PI * (face + 0.5F) / FACES;
        float inX = -Mth.cos(along) * off;
        float inZ = -Mth.sin(along) * off;
        float from = (1 - WINDOW_SHARE) / 2;
        float to = 1 - from;
        float x0 = OrreryDome.AXIS + Mth.lerp(from, COS[face], COS[face + 1]) * radius + inX;
        float z0 = OrreryDome.AXIS + Mth.lerp(from, SIN[face], SIN[face + 1]) * radius + inZ;
        float x1 = OrreryDome.AXIS + Mth.lerp(to, COS[face], COS[face + 1]) * radius + inX;
        float z1 = OrreryDome.AXIS + Mth.lerp(to, SIN[face], SIN[face + 1]) * radius + inZ;
        float low = middle - WINDOW_HEIGHT / 2;
        float high = middle + WINDOW_HEIGHT / 2;
        brush.vertex(x0, low, z0, patch.u(0), patch.v(1), r, g, b);
        brush.vertex(x1, low, z1, patch.u(1), patch.v(1), r, g, b);
        brush.vertex(x1, high, z1, patch.u(1), patch.v(0), r, g, b);
        brush.vertex(x0, high, z0, patch.u(0), patch.v(0), r, g, b);
    }

    /** The lamps' light, as her autopilot's flight has them at this moment of the game's time, in ticks. */
    static void lamps(OrreryDome dome, OrreryBrush glow, @Nullable AutopilotBlockEntity autopilot, float ticks) {
        float radius = dome.oculus() - INSET;
        float middle = dome.floor() + (1 + RISE - UNDER) / 2;
        float[] colour = new float[3];
        for (int lamp = 0; lamp < LAMPS; lamp++) {
            lit(autopilot, lamp, ticks, colour);
            window(glow, 2 * lamp, radius, middle, LIGHT_OFF, OrreryGlow.LAMP, colour[0], colour[1], colour[2]);
        }
    }

    /** How a lamp is lit, into the colour given, by her autopilot's phase; dim cyan for none. */
    private static void lit(@Nullable AutopilotBlockEntity autopilot, int lamp, float ticks, float[] colour) {
        AutopilotBlockEntity.Phase phase = autopilot == null ? AutopilotBlockEntity.Phase.OFF : autopilot.phase();
        switch (phase) {
            case OFF -> shade(colour, CYAN, IDLE);
            case TAKEOFF -> {
                if (autopilot.rocketThrust() > 0) {
                    shade(colour, WHITE, chased(lamp, ticks));
                } else {
                    counting(lamp, ticks, colour);
                }
            }
            case CLIMB -> shade(colour, WHITE, chased(lamp, ticks));
            case CRUISE -> shade(colour, BLUE, breathing(ticks, SPACE_BREATH));
            case DESCENT, LAND, ROLLOUT -> shade(colour, AMBER, breathing(ticks, PULSE));
        }
    }

    /**
     * A lamp through her countdown: green for each second launch control has counted, its tenth of the ring turning from
     * red to green through its second, red for those to come; all red, breathing slowly, while the count waits to begin.
     */
    private static void counting(int lamp, float ticks, float[] colour) {
        float since = ticks - heardAt;
        if (heardLeft < 0 || since < 0 || since > COUNT_HEARD_FOR) {
            shade(colour, RED, breathing(ticks, WAITING_BREATH));
            return;
        }
        float counted = COUNT_FROM - heardLeft + since / SharedConstants.TICKS_PER_SECOND;
        float tenth = (float) lamp * COUNT_FROM / LAMPS;
        float green = Mth.clamp(counted - tenth, 0, 1);
        for (int i = 0; i < 3; i++) {
            colour[i] = Mth.lerp(green, RED[i], GREEN[i]);
        }
    }

    /** How bright a lamp is as the white light chases round the ring, its tail fading behind it. */
    private static float chased(int lamp, float ticks) {
        float head = (ticks / CHASE % 1) * LAMPS;
        float behind = Mth.positiveModulo(head - lamp, LAMPS) / LAMPS;
        float tail = Math.max(0, 1 - behind / TAIL);
        return CHASE_FLOOR + (1 - CHASE_FLOOR) * tail * tail;
    }

    /** How bright a lamp breathing or pulsing once in so many ticks is now. */
    private static float breathing(float ticks, float period) {
        return Mth.lerp(0.5F + 0.5F * Mth.sin(Mth.TWO_PI * ticks / period), DIMMEST, 1);
    }

    private static void shade(float[] colour, float[] of, float brightness) {
        for (int i = 0; i < 3; i++) {
            colour[i] = of[i] * brightness;
        }
    }
}
