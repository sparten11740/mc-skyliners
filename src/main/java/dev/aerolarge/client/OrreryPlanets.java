package dev.aerolarge.client;

import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * The orrery's sun and planets (see {@link OrreryNow}), drawn in the sun's frame. The sun glows of itself, its granules
 * turning slowly under a boiling skin turning the other way, its rim a little darker so it looks round, in a warm
 * corona, a wide orange glow and eight rays turning once in two minutes and pulsing gently. Every planet and the Moon
 * is lit by it alone, each corner as it faces the sun: a day side and a night side, and crescents as they come round
 * between it and the camera. The Earth's air glows blue on her day side, and Saturn's ring lies tilted round him, lit
 * as it faces the sun. A globe small on the screen is drawn with fewer faces, and one behind the camera not at all.
 */
final class OrreryPlanets {
    /** How much the sun's rim darkens seen from the camera, a share: so it looks round rather than flat. */
    private static final float SUN_LIMB = 0.35F;
    /** How long the sun takes to turn once, and its boiling skin the other way, in ticks. */
    private static final float SUN_DAY = 800;
    private static final float BOILING_DAY = -500;
    /** How much bigger than the sun its boiling skin is, a share, and the colour it adds on, each a share. */
    private static final float BOILING_SIZE = 1.05F;
    private static final float[] BOILING = {0.38F, 0.2F, 0.06F};
    /** How much bigger than the Earth her air is, a share, and the colour it adds on her day side, each a share. */
    private static final float AIR_SIZE = 1.08F;
    private static final float[] AIR = {0.15F, 0.28F, 0.5F};
    /** Saturn's ring: how far out from his middle it reaches from and to, in blocks at the design reach. */
    private static final float RING_INNER = 0.55F;
    private static final float RING_OUTER = 0.85F;
    /** How far out from his middle Saturn's ring begins at the least, as a share of his size, so it never cuts him. */
    private static final float RING_CLEAR = 1.15F;
    /** How many faces go round Saturn's ring. */
    private static final int RING_FACES = 48;
    /** How bright the ring is lit where the sun is edge on to it, a share. */
    private static final float RING_NIGHT = 0.45F;
    /** The corona: how much wider than the sun, and the colour it adds on, warm white at seven tenths. */
    private static final float CORONA_SIZE = 2.4F;
    private static final float[] CORONA = {0.7F, 0.65F, 0.56F};
    /** The glow round it: how much wider than the sun, and the colour it adds on, orange at a quarter. */
    private static final float HALO_SIZE = 5;
    private static final float[] HALO = {0.25F, 0.14F, 0.04F};
    /**
     * The rays: how much wider than the sun, the colour they add on, how long they take to turn once, in ticks (two
     * minutes), and how far they pulse either way, a share, once in so many ticks.
     */
    private static final float RAYS_SIZE = 4;
    private static final float[] RAYS = {0.45F, 0.38F, 0.25F};
    private static final float RAYS_TURN = 2400;
    private static final float RAYS_PULSE = 0.1F;
    private static final float RAYS_PULSE_TIME = 100;
    /** How many pixels tall on the screen a globe is drawn at least with its finer faces. */
    private static final float FINE_FROM = 24;
    private static final Vector3f MIDDLE = new Vector3f();

    private OrreryPlanets() {
    }

    /**
     * The sun, the planets, the Moon and Saturn's ring, onto the orrery's sheet, the camera where it is in the frame.
     */
    static void solid(OrreryNow now, OrreryBrush sheet, Vector3f eye, OrreryClock clock) {
        if (Sight.ahead(sheet.pose(), 0, 0, 0, now.sun)) {
            Globe.Light glowing = new Globe.Glowing(1, 1, 1, new Vector3f(eye).normalize(), SUN_LIMB);
            Globe.SUN.draw(sheet, MIDDLE, now.sun, new Matrix3f().rotateY(clock.turned(SUN_DAY)), OrrerySheet.SUN, glowing);
        }
        for (OrreryNow.Placed planet : now.planets) {
            globe(sheet, planet, eye);
        }
        if (now.moon != null) {
            globe(sheet, now.moon, eye);
        }
        ring(sheet, now, now.planets.get(OrreryNow.PLANETS.indexOf(OrreryNow.SATURN)));
    }

    /** A planet, lit by the sun, if any of it can be in front of the camera. */
    private static void globe(OrreryBrush sheet, OrreryNow.Placed placed, Vector3f eye) {
        Vector3f middle = placed.middle();
        if (!Sight.ahead(sheet.pose(), middle.x, middle.y, middle.z, placed.radius())) {
            return;
        }
        Globe.Light lit = new Globe.SunLit(new Vector3f(middle).negate().normalize());
        globeFor(sheet, placed, eye).draw(sheet, middle, placed.radius(), placed.turn(), placed.planet().patch(), lit);
    }

    /** The finer globe for one big enough on the screen to show its faces, the coarser for one too small to. */
    private static Globe globeFor(OrreryBrush sheet, OrreryNow.Placed placed, Vector3f eye) {
        float near = eye.distance(placed.middle());
        float pixels = Sight.pixels(sheet.pose(), 2 * placed.radius()) * eye.length() / Math.max(near, 0.01F);
        return pixels >= FINE_FROM ? Globe.PLANET : Globe.SMALL;
    }

    /** Saturn's ring round his middle, tilted with him, lit as it faces the sun on either side. */
    private static void ring(OrreryBrush sheet, OrreryNow now, OrreryNow.Placed saturn) {
        float outer = RING_OUTER * now.scale;
        Vector3f middle = saturn.middle();
        if (!Sight.ahead(sheet.pose(), middle.x, middle.y, middle.z, outer)) {
            return;
        }
        float inner = Math.max(RING_INNER * now.scale, saturn.radius() * RING_CLEAR);
        Matrix3f tilt = new Matrix3f().rotateX(OrreryNow.SATURN.tilt());
        Vector3f up = tilt.transform(new Vector3f(0, 1, 0));
        float lit = Math.min(1, RING_NIGHT + Math.abs(up.dot(new Vector3f(middle).negate().normalize())));
        OrreryBrush.Patch patch = OrrerySheet.RING;
        Vector3f[] edge = new Vector3f[4];
        for (int k = 0; k < RING_FACES; k++) {
            edge[0] = ringPoint(tilt, middle, inner, k);
            edge[1] = ringPoint(tilt, middle, outer, k);
            edge[2] = ringPoint(tilt, middle, outer, k + 1);
            edge[3] = ringPoint(tilt, middle, inner, k + 1);
            for (int corner = 0; corner < 4; corner++) {
                Vector3f at = edge[corner];
                sheet.vertex(at.x, at.y, at.z, patch.u(corner == 1 || corner == 2 ? 1 : 0), patch.v(0.5F), lit, lit, lit);
            }
        }
    }

    private static Vector3f ringPoint(Matrix3f tilt, Vector3f middle, float out, int k) {
        float angle = (float) (2 * Math.PI * k / RING_FACES);
        return tilt.transform(new Vector3f((float) Math.cos(angle) * out, 0, (float) -Math.sin(angle) * out)).add(middle);
    }

    /**
     * The light added over them: the sun's boiling skin, the Earth's air, and round the sun its corona, its glow and
     * its rays, square to the camera.
     */
    /** How far out from the sun's middle the light round it reaches at most, in blocks: its halo's corners. */
    static float glowReach(OrreryNow now) {
        return now.sun * HALO_SIZE * Mth.SQRT_OF_TWO;
    }

    static void glow(OrreryNow now, OrreryBrush glow, OrreryBrush.Facing facing, OrreryClock clock) {
        Matrix3f boiling = new Matrix3f().rotateY(clock.turned(BOILING_DAY));
        Globe.Light skin = new Globe.Glowing(BOILING[0], BOILING[1], BOILING[2], new Vector3f(0, 1, 0), 0);
        Globe.PLANET.draw(glow, MIDDLE, now.sun * BOILING_SIZE, boiling, OrreryGlow.BOILING, skin);
        OrreryNow.Placed earth = now.planets.get(OrreryNow.PLANETS.indexOf(OrreryNow.EARTH));
        Globe.Light air = new Globe.SunLit(new Vector3f(earth.middle()).negate().normalize(), new float[3], AIR);
        Globe.PLANET.draw(glow, earth.middle(), earth.radius() * AIR_SIZE, earth.turn(), OrreryGlow.WHITE, air);
        glow.facingCamera(facing, 0, 0, 0, now.sun * HALO_SIZE, OrreryGlow.HALO, HALO[0], HALO[1], HALO[2]);
        glow.facingCamera(facing, 0, 0, 0, now.sun * CORONA_SIZE, OrreryGlow.CORONA, CORONA[0], CORONA[1], CORONA[2]);
        float pulse = 1 + RAYS_PULSE * (float) Math.sin(2 * Math.PI * clock.ticks() / RAYS_PULSE_TIME);
        OrreryBrush.Facing turned = facing.turned(clock.turned(RAYS_TURN));
        glow.facingCamera(turned, 0, 0, 0, now.sun * RAYS_SIZE * pulse, OrreryGlow.RAYS, RAYS[0] * pulse, RAYS[1] * pulse, RAYS[2] * pulse);
    }
}
