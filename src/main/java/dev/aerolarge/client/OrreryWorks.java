package dev.aerolarge.client;

import dev.aerolarge.block.OrreryBlockEntity;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * The orrery's works (see {@link OrreryNow}), drawn in its sun's frame: a slim steel rod down from its mount to the
 * sun, a brass collar round it every two blocks over its gears; two toothed brass gears turning each at its own speed;
 * under them a brass tube round the rod for each planet, each a little wider than the one under it, from which the
 * planet's flat arm reaches out over the planets' plane with a slim rod down from its end to the planet, so that seen
 * from under them the arms are spokes over the planets and never hide one; the Moon carried round the Earth on a little
 * arm of its own; faint gold tracks round the sun where each planet goes; and round it all a brass ecliptic hoop,
 * ticked every thirty degrees and hung on four hairline cables from the dome. Every part is lit by the sun under it,
 * each face as it faces the sun.
 */
final class OrreryWorks {
    /** How thick the steel rod down to the sun is, in blocks. */
    private static final float ROD = 0.08F;
    /** The collars round the rod: how far apart, how far out round it to their corners, and how tall, in blocks. */
    private static final float COLLAR_EVERY = 2;
    private static final float COLLAR = 0.07F;
    private static final float COLLAR_TALL = 0.08F;
    /** How many sides the collars, the tubes and the rods down to the planets have. */
    private static final int COLLAR_SIDES = 6;
    private static final int TUBE_SIDES = 6;
    private static final int SLIM_SIDES = 4;
    /** How much wider each planet's tube is round the rod than the one under it, in blocks at the design reach. */
    private static final float TUBE_STEP = 0.014F;
    /** The smallest share of the design reach the tubes, arms and tracks are drawn at, so they never vanish. */
    private static final float SLIMMEST = 0.6F;
    /** The arms: how wide and how thick, in blocks at the design reach; and how thick the rods down from them are. */
    private static final float ARM_WIDE = 0.05F;
    private static final float ARM_THICK = 0.022F;
    private static final float DROP = 0.022F;
    /**
     * The gears: how wide the big one and the small one are, how thick each is and how far over the big one the small
     * one turns, in blocks at the design reach; and how long each takes to turn once, in ticks, the small one backward.
     */
    private static final float BIG_GEAR = 1.4F;
    private static final float SMALL_GEAR = 1.0F;
    private static final float GEAR_THICK = 0.05F;
    private static final float GEAR_GAP = 0.1F;
    private static final float BIG_GEAR_TURN = 900;
    private static final float SMALL_GEAR_TURN = -400;
    /** How far out the roots of a gear's teeth are, as a share of its width's half, and how many sides its rim has. */
    private static final float GEAR_ROOT = 0.86F;
    private static final int GEAR_SIDES = 16;
    /** The ecliptic hoop: how tall and how thick, in blocks at the design reach, and how many faces go round it. */
    private static final float HOOP_TALL = 0.08F;
    private static final float HOOP_THICK = 0.03F;
    private static final int HOOP_FACES = 48;
    /**
     * Its ticks: how many, how much taller than the hoop, a share, and how wide and how deep, in blocks at the design
     * reach.
     */
    private static final int TICKS = 12;
    private static final float TICK_TALL = 2.5F;
    private static final float TICK_WIDE = 0.04F;
    private static final float TICK_DEEP = 0.07F;
    /** The cables it hangs on: how many, evenly round it, and how thick, in blocks. */
    private static final int CABLES = 4;
    private static final float CABLE = 0.012F;
    /** How brightly the brass and steel are lit where the sun does not reach them, a share. */
    private static final float METAL_NIGHT = 0.35F;
    /** The orbits' tracks: how wide, in blocks at the design reach, how many faces round, and their faint gold. */
    private static final float TRACK = 0.025F;
    private static final int TRACK_FACES = 48;
    private static final float[] TRACK_GOLD = {0.18F, 0.14F, 0.07F};
    /** A box's eight corners, each across, along and up, from its least to its most each way. */
    private static final int[][] BOX_CORNERS = {
        {0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}};
    /**
     * A box's faces: its corners going round as seen from outside, and the way it faces, across, along and up: its top,
     * its underside, its two sides, and its two ends.
     */
    private static final int[][] BOX_FACES = {
        {4, 7, 6, 5, 0, 0, 1}, {0, 1, 2, 3, 0, 0, -1}, {0, 3, 7, 4, -1, 0, 0}, {1, 5, 6, 2, 1, 0, 0}, {0, 4, 5, 1, 0, -1, 0},
        {3, 2, 6, 7, 0, 1, 0}};
    /** How many of those faces a bar without its ends has: its top, its underside and its two sides. */
    private static final int BAR_FACES = 4;

    private OrreryWorks() {
    }

    /**
     * The works onto the orrery's sheet, the rod up to its mount so high over the sun's middle and the hoop's cables up
     * to the dome so high, in blocks.
     */
    static void solid(OrreryNow now, OrreryBrush sheet, OrreryClock clock, float rodTop, float cableTop) {
        float slim = Math.max(now.scale, SLIMMEST);
        prism(sheet, OrrerySheet.STEEL, 0, 0, SLIM_SIDES, ROD * Mth.SQRT_OF_TWO / 2, now.sun, rodTop, false);
        float bigTop = now.gears + GEAR_THICK * now.scale;
        float smallLow = bigTop + GEAR_GAP * now.scale;
        float smallTop = smallLow + GEAR_THICK * now.scale;
        for (float y = smallTop + COLLAR_EVERY; y + COLLAR_TALL < rodTop; y += COLLAR_EVERY) {
            prism(sheet, OrrerySheet.BRASS, 0, 0, COLLAR_SIDES, COLLAR, y, y + COLLAR_TALL, false);
        }
        gear(sheet, OrrerySheet.BIG_GEAR, BIG_GEAR * now.scale, now.gears, bigTop, clock.turned(BIG_GEAR_TURN));
        gear(sheet, OrrerySheet.SMALL_GEAR, SMALL_GEAR * now.scale, smallLow, smallTop, clock.turned(SMALL_GEAR_TURN));
        for (int i = 0; i < now.planets.size(); i++) {
            arm(sheet, now, now.planets.get(i), ROD / 2 + TUBE_STEP * (i + 1) * slim, slim);
        }
        if (now.moon != null) {
            moonArm(sheet, now, slim);
        }
        hoop(sheet, now, slim, cableTop);
    }

    /**
     * A planet's tube round the rod, so far out, up to the gears; its arm out over the plane, and its rod down to it.
     */
    private static void arm(OrreryBrush sheet, OrreryNow now, OrreryNow.Placed planet, float tube, float slim) {
        prism(sheet, OrrerySheet.BRASS, 0, 0, TUBE_SIDES, tube, planet.arm(), now.gears, true);
        float out = planet.planet().orbit() * now.scale;
        float thick = ARM_THICK * slim;
        bar(sheet, OrrerySheet.BRASS, 0, 0, planet.angle(), tube, out, planet.arm(), ARM_WIDE * slim, thick, BAR_FACES);
        Vector3f middle = planet.middle();
        prism(sheet, OrrerySheet.BRASS, middle.x, middle.z, SLIM_SIDES, DROP * slim, planet.radius(), planet.arm() - thick / 2, false);
    }

    /** The Moon's little arm out from the Earth's rod, just over her top, and its rod down to the Moon. */
    private static void moonArm(OrreryBrush sheet, OrreryNow now, float slim) {
        OrreryNow.Placed moon = now.moon;
        Vector3f earth = now.planets.get(OrreryNow.PLANETS.indexOf(OrreryNow.EARTH)).middle();
        float thick = ARM_THICK * slim;
        float out = earth.distance(moon.middle());
        bar(sheet, OrrerySheet.BRASS, earth.x, earth.z, moon.angle(), 0, out, moon.arm(), ARM_WIDE * slim / 2, thick, BAR_FACES);
        Vector3f middle = moon.middle();
        prism(sheet, OrrerySheet.BRASS, middle.x, middle.z, SLIM_SIDES, DROP * slim / 2, moon.radius(), moon.arm() - thick / 2, false);
    }

    /** A gear so wide, flat between two heights, turned so far: its faces, clear round its teeth, and its rim. */
    private static void gear(OrreryBrush sheet, OrreryBrush.Patch patch, float across, float low, float high, float turn) {
        float half = across / 2;
        float[][] corners = new float[4][];
        for (int corner = 0; corner < 4; corner++) {
            float angle = turn + Mth.HALF_PI * corner + Mth.PI / 4;
            corners[corner] = new float[]{Mth.cos(angle) * half * Mth.SQRT_OF_TWO, -Mth.sin(angle) * half * Mth.SQRT_OF_TWO};
        }
        float under = lit(0, -1, 0, 0, low, 0);
        float over = lit(0, 1, 0, 0, high, 0);
        float[][] uvs = {{1, 0}, {0, 0}, {0, 1}, {1, 1}};
        for (int corner = 0; corner < 4; corner++) {
            sheet.vertex(corners[corner][0], low, corners[corner][1], patch.u(uvs[corner][0]), patch.v(uvs[corner][1]), under, under, under);
        }
        for (int corner = 3; corner >= 0; corner--) {
            sheet.vertex(corners[corner][0], high, corners[corner][1], patch.u(uvs[corner][0]), patch.v(uvs[corner][1]), over, over, over);
        }
        prism(sheet, OrrerySheet.BRASS, 0, 0, GEAR_SIDES, half * GEAR_ROOT, low, high, false);
    }

    /** The ecliptic hoop round it all in the planets' plane, its ticks, and its cables up to the dome so high. */
    private static void hoop(OrreryBrush sheet, OrreryNow now, float slim, float cableTop) {
        float radius = now.reach * (float) OrreryBlockEntity.EXTENT;
        float tall = HOOP_TALL * slim;
        float inner = radius - HOOP_THICK * slim / 2;
        float outer = radius + HOOP_THICK * slim / 2;
        OrreryBrush.Patch brass = OrrerySheet.BRASS;
        for (int k = 0; k < HOOP_FACES; k++) {
            float a0 = Mth.TWO_PI * k / HOOP_FACES;
            float a1 = Mth.TWO_PI * (k + 1) / HOOP_FACES;
            float mid = (a0 + a1) / 2;
            float facingSun = lit(-Mth.cos(mid), 0, Mth.sin(mid), Mth.cos(mid) * inner, 0, -Mth.sin(mid) * inner);
            band(sheet, brass, inner, a0, a1, -tall / 2, tall / 2, facingSun);
            band(sheet, brass, outer, a0, a1, -tall / 2, tall / 2, METAL_NIGHT);
            float under = lit(0, -1, 0, Mth.cos(mid) * radius, -tall / 2, -Mth.sin(mid) * radius);
            sheet.vertex(Mth.cos(a0) * inner, -tall / 2, -Mth.sin(a0) * inner, brass.u(0), brass.v(0), under, under, under);
            sheet.vertex(Mth.cos(a1) * inner, -tall / 2, -Mth.sin(a1) * inner, brass.u(1), brass.v(0), under, under, under);
            sheet.vertex(Mth.cos(a1) * outer, -tall / 2, -Mth.sin(a1) * outer, brass.u(1), brass.v(1), under, under, under);
            sheet.vertex(Mth.cos(a0) * outer, -tall / 2, -Mth.sin(a0) * outer, brass.u(0), brass.v(1), under, under, under);
        }
        float tickDeep = TICK_DEEP * slim;
        for (int tick = 0; tick < TICKS; tick++) {
            float angle = Mth.TWO_PI * tick / TICKS;
            bar(sheet, brass, 0, 0, angle, radius - tickDeep / 2, radius + tickDeep / 2, 0, TICK_WIDE * slim, tall * TICK_TALL, BOX_FACES.length);
        }
        for (int cable = 0; cable < CABLES; cable++) {
            float angle = Mth.TWO_PI * (cable + 0.5F) / CABLES;
            prism(sheet, OrrerySheet.STEEL, Mth.cos(angle) * radius, -Mth.sin(angle) * radius, SLIM_SIDES, CABLE, tall / 2, cableTop, false);
        }
    }

    /** A face of the hoop round it at a radius, from one angle to another, between two heights, lit so brightly. */
    private static void band(OrreryBrush sheet, OrreryBrush.Patch patch, float radius, float from, float to, float low, float high, float lit) {
        sheet.vertex(Mth.cos(from) * radius, low, -Mth.sin(from) * radius, patch.u(0), patch.v(1), lit, lit, lit);
        sheet.vertex(Mth.cos(to) * radius, low, -Mth.sin(to) * radius, patch.u(1), patch.v(1), lit, lit, lit);
        sheet.vertex(Mth.cos(to) * radius, high, -Mth.sin(to) * radius, patch.u(1), patch.v(0), lit, lit, lit);
        sheet.vertex(Mth.cos(from) * radius, high, -Mth.sin(from) * radius, patch.u(0), patch.v(0), lit, lit, lit);
    }

    /**
     * An upright prism of so many sides round a point of the plane, so far out to its corners, between two heights, its
     * sides lit as they face the sun; and the floor under it, lit from under, where asked.
     */
    private static void prism(OrreryBrush sheet, OrreryBrush.Patch patch, float x, float z, int sides, float out, float low, float high,
                              boolean floor) {
        for (int side = 0; side < sides; side++) {
            float a0 = Mth.TWO_PI * side / sides;
            float a1 = Mth.TWO_PI * (side + 1) / sides;
            float mid = (a0 + a1) / 2;
            float shade = lit(Mth.cos(mid), 0, -Mth.sin(mid), x + Mth.cos(mid) * out, (low + high) / 2, z - Mth.sin(mid) * out);
            sheet.vertex(x + Mth.cos(a0) * out, low, z - Mth.sin(a0) * out, patch.u(0), patch.v(1), shade, shade, shade);
            sheet.vertex(x + Mth.cos(a1) * out, low, z - Mth.sin(a1) * out, patch.u(1), patch.v(1), shade, shade, shade);
            sheet.vertex(x + Mth.cos(a1) * out, high, z - Mth.sin(a1) * out, patch.u(1), patch.v(0), shade, shade, shade);
            sheet.vertex(x + Mth.cos(a0) * out, high, z - Mth.sin(a0) * out, patch.u(0), patch.v(0), shade, shade, shade);
        }
        if (!floor) {
            return;
        }
        float shade = lit(0, -1, 0, x, low, z);
        for (int side = 0; side < sides; side++) {
            float a0 = Mth.TWO_PI * side / sides;
            float a1 = Mth.TWO_PI * (side + 1) / sides;
            sheet.vertex(x, low, z, patch.u(0.5F), patch.v(0.5F), shade, shade, shade);
            sheet.vertex(x, low, z, patch.u(0.5F), patch.v(0.5F), shade, shade, shade);
            sheet.vertex(x + Mth.cos(a1) * out, low, z - Mth.sin(a1) * out, patch.u(1), patch.v(0), shade, shade, shade);
            sheet.vertex(x + Mth.cos(a0) * out, low, z - Mth.sin(a0) * out, patch.u(0), patch.v(0), shade, shade, shade);
        }
    }

    /**
     * A flat bar out from a point of the plane along an angle, from so far out to so far out, its middle at a height,
     * so wide and so tall, with so many of its faces: its top, underside and sides, then its ends.
     */
    private static void bar(OrreryBrush sheet, OrreryBrush.Patch patch, float x, float z, float angle, float from, float to, float y, float wide,
                            float tall, int faces) {
        Vector3f along = new Vector3f(Mth.cos(angle), 0, -Mth.sin(angle));
        Vector3f across = new Vector3f(Mth.sin(angle), 0, Mth.cos(angle));
        Vector3f[] corners = new Vector3f[BOX_CORNERS.length];
        for (int corner = 0; corner < corners.length; corner++) {
            int[] at = BOX_CORNERS[corner];
            float out = at[1] == 0 ? from : to;
            float side = (at[0] - 0.5F) * wide;
            corners[corner] = new Vector3f(x, y + (at[2] - 0.5F) * tall, z).add(new Vector3f(along).mul(out)).add(new Vector3f(across).mul(side));
        }
        for (int face = 0; face < faces; face++) {
            barFace(sheet, patch, corners, BOX_FACES[face], along, across);
        }
    }

    /** One face of a bar, lit as it faces the sun from its middle. */
    private static void barFace(OrreryBrush sheet, OrreryBrush.Patch patch, Vector3f[] corners, int[] face, Vector3f along, Vector3f across) {
        Vector3f facing = new Vector3f(across).mul(face[4]).add(new Vector3f(along).mul(face[5])).add(0, face[6], 0);
        Vector3f middle = new Vector3f();
        for (int corner = 0; corner < 4; corner++) {
            middle.add(corners[face[corner]]);
        }
        middle.div(4);
        float shade = lit(facing.x, facing.y, facing.z, middle.x, middle.y, middle.z);
        float[][] uvs = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (int corner = 0; corner < 4; corner++) {
            Vector3f at = corners[face[corner]];
            sheet.vertex(at.x, at.y, at.z, patch.u(uvs[corner][0]), patch.v(uvs[corner][1]), shade, shade, shade);
        }
    }

    /** How brightly metal facing a way is lit by the sun at the origin, from a point of it, a share. */
    private static float lit(float nx, float ny, float nz, float x, float y, float z) {
        float distance = Math.max(Mth.sqrt(x * x + y * y + z * z), 1.0E-4F);
        float facing = -(nx * x + ny * y + nz * z) / distance;
        return METAL_NIGHT + (1 - METAL_NIGHT) * Math.max(0, facing);
    }

    /** The faint gold tracks where each planet goes round the sun, in its plane, added on, seen from under it. */
    static void tracks(OrreryNow now, OrreryBrush glow) {
        float half = TRACK * Math.max(now.scale, SLIMMEST) / 2;
        OrreryBrush.Patch white = OrreryGlow.WHITE;
        for (OrreryNow.Planet planet : OrreryNow.PLANETS) {
            float orbit = planet.orbit() * now.scale;
            for (int k = 0; k < TRACK_FACES; k++) {
                float a0 = Mth.TWO_PI * k / TRACK_FACES;
                float a1 = Mth.TWO_PI * (k + 1) / TRACK_FACES;
                // Round anticlockwise seen from under the plane, so it faces down, toward the cockpit.
                glow.vertex(Mth.cos(a0) * (orbit - half), 0, -Mth.sin(a0) * (orbit - half), white.u(0), white.v(0), TRACK_GOLD[0], TRACK_GOLD[1],
                    TRACK_GOLD[2]);
                glow.vertex(Mth.cos(a1) * (orbit - half), 0, -Mth.sin(a1) * (orbit - half), white.u(1), white.v(0), TRACK_GOLD[0], TRACK_GOLD[1],
                    TRACK_GOLD[2]);
                glow.vertex(Mth.cos(a1) * (orbit + half), 0, -Mth.sin(a1) * (orbit + half), white.u(1), white.v(1), TRACK_GOLD[0], TRACK_GOLD[1],
                    TRACK_GOLD[2]);
                glow.vertex(Mth.cos(a0) * (orbit + half), 0, -Mth.sin(a0) * (orbit + half), white.u(0), white.v(1), TRACK_GOLD[0], TRACK_GOLD[1],
                    TRACK_GOLD[2]);
            }
        }
    }
}
