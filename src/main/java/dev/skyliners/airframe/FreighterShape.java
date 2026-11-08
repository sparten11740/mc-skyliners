package dev.skyliners.airframe;

/**
 * The freighter's outline in metres: a battered saucer 25 m across, a lens over and under a short upright rim, two flat
 * prongs reaching forward either side of a loading gap, her cockpit's tube and pod out on her starboard front edge, a
 * dome for a ball turret on her top and another under her belly, and a docking collar on either beam. Measured from the
 * published figures of the ship she is modelled on, never traced from plans. Drawn as the rocket is (see
 * {@link RocketShape}): y up from the ground under her legs' feet, the disc's axis straight up through x 0, z 0, her
 * prongs ahead to the north (z falling) and starboard to the east (x rising). It is cut into blocks by
 * {@link FreighterLayout}.
 *
 * <p>Each part is a solid of its own with a depth, how far a point lies outside it, negative inside; the hull is their
 * union, the least depth of them, with the loading gap cut out of it. A depth is a true distance close to a part's
 * surface, which is all the snapped skin's cut asks of it (see {@link SurfaceCut}).
 */
final class FreighterShape {
    /** The disc's radius, and the height of the middle of its upright rim over the ground. */
    static final double RADIUS = 12.5;
    static final double MIDDLE = 3.4;
    /** Half the rim's height: it runs straight up this far over and under the middle. */
    static final double RIM_HALF = 0.9;
    /** How far the lens over the rim rises to her top at the axis, and how far the one under it falls to her belly. */
    static final double CROWN = 1.8;
    static final double SAG = 1.0;
    /** The loading gap between her prongs: how far it reaches out from the middle line, and its back wall's place. */
    static final double GAP_HALF = 1.8;
    static final double GAP_BACK = -8.5;
    /**
     * Her prongs: their tips this far ahead of the axis; each from the gap out to its outer edge, so far out at the
     * tip and at its root, where it meets the disc, and how far back the root is; and how far back into the disc the
     * prong runs, so the two are one.
     */
    static final double PRONG_TIP = -22.25;
    static final double PRONG_OUT_TIP = 5.0;
    static final double PRONG_OUT_ROOT = 6.6;
    static final double PRONG_ROOT = -11;
    static final double PRONG_BACK = -6;
    /**
     * A prong's top and underside: flat, so high over the ground at its root and at its tip, so it thins toward its
     * tip, as thick as her rim where it meets the disc.
     */
    static final double PRONG_TOP_ROOT = 4.4;
    static final double PRONG_UNDER_ROOT = 2.4;
    static final double PRONG_TOP_TIP = 4.1;
    static final double PRONG_UNDER_TIP = 2.7;
    /**
     * The cockpit: the tube's axis so far out to starboard (the pod's is the same) and so high, its radius, and where
     * it runs from, inside the disc, where it crosses her outer bulkhead and stands a little proud of her top as it
     * runs out over her rim, to the pod; the pod's axis so high and its radius, round as far as its nose, whose
     * shoulder rounds in to the flat glazed front at its tip, so wide.
     */
    static final double TUBE_OUT = 9.5;
    static final double TUBE_UP = 4.0;
    static final double TUBE_RADIUS = 1.5;
    static final double TUBE_FROM = -3;
    static final double POD_FROM = -12;
    static final double POD_UP = 4.4;
    static final double POD_RADIUS = 2.4;
    static final double NOSE_FROM = -14;
    static final double POD_TIP = -16.5;
    static final double TIP_RADIUS = 1.0;
    /** The ball turrets' domes on the axis: the top one's radius and crown's height, the belly one's and foot's. */
    static final double TOP_DOME = 1.3;
    static final double TOP_DOME_CROWN = 6.8;
    static final double BELLY_DOME = 1.0;
    static final double BELLY_DOME_FOOT = 0.8;
    /** The docking collars: so far back, standing so far proud of the rim, round so far, their middles at the rim's. */
    static final double COLLAR_BACK = 2.5;
    static final double COLLAR_PROUD = 0.3;
    static final double COLLAR_RADIUS = 1.3;
    /** How far out to her side her furthest block reaches: a docking collar's face. */
    static final double SIDE_REACH = Math.sqrt(RADIUS * RADIUS - COLLAR_BACK * COLLAR_BACK) + COLLAR_PROUD;
    /**
     * Her landing legs, where each stands as x and z in metres: one forward under the loading gap's back wall, one
     * either side abeam, and one either side aft.
     */
    static final double[][] LEGS = {{0, -7.8}, {8.5, 3}, {-8.5, 3}, {5.5, 8.5}, {-5.5, 8.5}};
    /** The step a depth is differenced over to find the way out of the hull, in metres. */
    private static final double STEP = 0.01;

    private FreighterShape() {
    }

    /** The solids her hull is the union of. */
    enum Part {
        DISC, PRONG, TUBE, POD, DOME, COLLAR
    }

    /** How far a point lies outside her hull, in metres, negative inside. */
    static double depth(double x, double y, double z) {
        double hull = Double.MAX_VALUE;
        for (Part part : Part.values()) {
            hull = Math.min(hull, depth(part, x, y, z));
        }
        return Math.max(hull, -gap(x, z));
    }

    /** How far a point lies outside one of her solids, in metres, negative inside, the loading gap not cut out. */
    static double depth(Part part, double x, double y, double z) {
        return switch (part) {
            case DISC -> disc(x, y, z);
            case PRONG -> prong(Math.abs(x), y, z);
            case TUBE -> tube(x, y, z);
            case POD -> pod(x, y, z);
            case DOME -> domes(x, y, z);
            case COLLAR -> collar(Math.abs(x), y, z);
        };
    }

    /** Which of her solids a point lies nearest the surface of, or deepest inside: the one her hull is there. */
    static Part nearest(double x, double y, double z) {
        Part nearest = Part.DISC;
        double least = Double.MAX_VALUE;
        for (Part part : Part.values()) {
            double depth = depth(part, x, y, z);
            if (depth < least) {
                least = depth;
                nearest = part;
            }
        }
        return nearest;
    }

    /** The way out of her hull from a point near its surface, a unit vector. */
    static double[] outward(double x, double y, double z) {
        double dx = depth(x + STEP, y, z) - depth(x - STEP, y, z);
        double dy = depth(x, y + STEP, z) - depth(x, y - STEP, z);
        double dz = depth(x, y, z + STEP) - depth(x, y, z - STEP);
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return length < 1e-12 ? new double[]{0, 1, 0} : new double[]{dx / length, dy / length, dz / length};
    }

    /** Her top's height over the ground at a distance out from the axis: the lens over the rim. */
    static double top(double out) {
        return MIDDLE + RIM_HALF + CROWN * lens(out);
    }

    /** Her belly's height over the ground at a distance out from the axis: the lens under the rim. */
    static double belly(double out) {
        return MIDDLE - RIM_HALF - SAG * lens(out);
    }

    /** How full the lens is at a distance out: 1 at the axis, falling to 0 at the rim. */
    private static double lens(double out) {
        double share = Math.min(out / RADIUS, 1);
        return 1 - share * share;
    }

    /** The disc: its rim straight up, the lens over and under it, each tilted to its surface's normal. */
    private static double disc(double x, double y, double z) {
        double out = Math.hypot(x, z);
        double rise = 2 * CROWN * Math.min(out, RADIUS) / (RADIUS * RADIUS);
        double fall = 2 * SAG * Math.min(out, RADIUS) / (RADIUS * RADIUS);
        double over = (y - top(out)) / Math.sqrt(1 + rise * rise);
        double under = (belly(out) - y) / Math.sqrt(1 + fall * fall);
        return Math.max(out - RADIUS, Math.max(over, under));
    }

    /**
     * A prong, either side alike (x taken as how far out from the middle line): from the gap out to its outer edge,
     * which runs straight back from its root and narrows toward its tip, from its flat front back into the disc, under
     * its flat top and over its flat underside.
     */
    private static double prong(double across, double y, double z) {
        double along = Math.clamp((z - PRONG_TIP) / (PRONG_ROOT - PRONG_TIP), 0, 1);
        double edge = PRONG_OUT_TIP + (PRONG_OUT_ROOT - PRONG_OUT_TIP) * along;
        double narrowing = z < PRONG_ROOT ? (PRONG_OUT_ROOT - PRONG_OUT_TIP) / (PRONG_ROOT - PRONG_TIP) : 0;
        double outer = (across - edge) / Math.sqrt(1 + narrowing * narrowing);
        double top = PRONG_TOP_TIP + (PRONG_TOP_ROOT - PRONG_TOP_TIP) * along;
        double under = PRONG_UNDER_TIP + (PRONG_UNDER_ROOT - PRONG_UNDER_TIP) * along;
        double sides = Math.max(GAP_HALF - across, outer);
        double ends = Math.max(PRONG_TIP - z, z - PRONG_BACK);
        return Math.max(Math.max(sides, ends), Math.max(y - top, under - y));
    }

    /** The cockpit's tube: a cylinder along her length from the disc's edge to the pod. */
    private static double tube(double x, double y, double z) {
        double round = Math.hypot(x - TUBE_OUT, y - TUBE_UP) - TUBE_RADIUS;
        return Math.max(round, Math.max(POD_FROM - z, z - TUBE_FROM));
    }

    /**
     * The cockpit's pod: round from the tube's end to its nose, whose shoulder rounds in to a flat glazed front at its
     * tip, meeting the round part and the front each without an edge, so her canopy is a round dome.
     */
    private static double pod(double x, double y, double z) {
        double across = Math.hypot(x - TUBE_OUT, y - POD_UP);
        double side = z >= NOSE_FROM ? across - POD_RADIUS : shoulder(Math.max(across - TIP_RADIUS, 0), NOSE_FROM - z);
        return Math.max(side, Math.max(POD_TIP - z, z - POD_FROM));
    }

    /**
     * How far a point lies outside her nose's shoulder, given how far out it is past the edge of the flat front and
     * how far ahead of where the nose begins, in metres: a quarter ellipse from the pod's round part to the front's
     * edge, as wide as the one is past the other and as deep as the nose is long. The distance is true on the surface
     * and close to it nearby, which is all the snapped skin's cut asks of it.
     */
    private static double shoulder(double out, double ahead) {
        double wide = POD_RADIUS - TIP_RADIUS;
        double deep = NOSE_FROM - POD_TIP;
        double share = Math.hypot(out / wide, ahead / deep);
        double gradient = Math.hypot(out / (wide * wide), ahead / (deep * deep));
        return gradient < Math.ulp(1.0) ? -Math.min(wide, deep) : share * (share - 1) / gradient;
    }

    /** The ball turrets' domes on the axis, balls standing out of her top and her belly. */
    private static double domes(double x, double y, double z) {
        double across = Math.hypot(x, z);
        double topBall = Math.hypot(across, y - (TOP_DOME_CROWN - TOP_DOME)) - TOP_DOME;
        double bellyBall = Math.hypot(across, y - (BELLY_DOME_FOOT + BELLY_DOME)) - BELLY_DOME;
        return Math.min(topBall, bellyBall);
    }

    /** A docking collar, either beam alike: a short drum standing out of the rim along the way across her. */
    private static double collar(double across, double y, double z) {
        double round = Math.hypot(y - MIDDLE, z - COLLAR_BACK) - COLLAR_RADIUS;
        double rim = Math.sqrt(RADIUS * RADIUS - COLLAR_BACK * COLLAR_BACK);
        return Math.max(round, Math.max(rim - 1 - across, across - (rim + COLLAR_PROUD)));
    }

    /** The loading gap between her prongs, open from its back wall forward: negative inside it. */
    private static double gap(double x, double z) {
        return Math.max(Math.abs(x) - GAP_HALF, z - GAP_BACK);
    }
}
