package dev.aerolarge.airframe;

/**
 * The rocket's outline in metres: as tall as the airliner is long, a round body standing on four fins that are its
 * landing legs, its engines under the body's flat bottom, a pointed nose on top with the cockpit's glass in its lower
 * part. Drawn as the airliner and the zeppelin are, but standing: y up from the ground under the fins' feet, the body's
 * axis straight up through x 0, z 0, and the front (where its door is) to the north. It is cut into blocks by
 * {@link RocketLayout} at the airframe's scale (see {@code AerolargeConfig.SCALE}).
 */
final class RocketShape {
    /** How tall it stands, from the fins' feet to the nose's tip: as long as the airliner. */
    static final double HEIGHT = AirframeShape.LENGTH;
    /** The body's radius. */
    static final double RADIUS = 5;
    /** How long the nose cone is, from where it meets the body to its tip, and where it meets the body. */
    static final double NOSE = 14;
    static final double NOSE_BASE = HEIGHT - NOSE;
    /**
     * How high the body's flat bottom stands over the ground, the engines in it firing down past the fins' feet; higher
     * on a small rocket, where the engines' exhaust needs the room (see {@link RocketLayout}).
     */
    static final double BASE = 6;
    /**
     * The fins, one at each corner between the four ways (so none stands before the door): how far out from the axis
     * their tips reach, how far out their feet begin on the ground, and how high their tips' outer edges and their
     * roots along the body reach.
     */
    static final double FIN_TIP = 11;
    static final double FIN_FOOT = 9;
    static final double FIN_TIP_TOP = 8;
    static final double FIN_ROOT_TOP = 22;
    /** The nose cone's curve: a tangent ogive's, its radius of curvature from its length and the body's radius. */
    private static final double OGIVE = (RADIUS * RADIUS + NOSE * NOSE) / (2 * RADIUS);

    private RocketShape() {
    }

    /** The body's radius at a height, its bottom so high; 0 under the bottom and over the tip. */
    static double radius(double height, double base) {
        if (height < base || height >= HEIGHT) {
            return 0;
        }
        if (height <= NOSE_BASE) {
            return RADIUS;
        }
        double fromTip = HEIGHT - height;
        return Math.sqrt(OGIVE * OGIVE - (NOSE - fromTip) * (NOSE - fromTip)) + RADIUS - OGIVE;
    }

    /** How fast the radius changes going up the nose, in metres of radius a metre; nothing along the body. */
    private static double slope(double height) {
        if (height <= NOSE_BASE) {
            return 0;
        }
        double step = 0.01;
        return (radius(height + step, 0) - radius(height - step, 0)) / (2 * step);
    }

    /**
     * How far a point lies outside the body's surface, in metres, negative inside, its bottom so high: off its side and
     * nose, tilted back to the surface's normal where the nose narrows, or under its flat bottom, whichever is the
     * further out.
     */
    static double depth(double x, double y, double z, double base) {
        double across = Math.hypot(x, z);
        double under = base - y;
        if (y >= HEIGHT) {
            return Math.max(under, Math.hypot(across, y - HEIGHT));
        }
        double s = slope(y);
        return Math.max(under, (across - radius(Math.max(y, base), base)) / Math.sqrt(1 + s * s));
    }

    /** The way out of the body from a point near its surface, its bottom so high, a unit vector. */
    static double[] outward(double x, double y, double z, double base) {
        double across = Math.hypot(x, z);
        if (base - y > across - radius(Math.max(y, base), base)) {
            return new double[]{0, -1, 0};
        }
        double s = slope(y);
        double length = Math.sqrt(1 + s * s);
        double nx = across < 1e-9 ? 0 : x / across;
        double nz = across < 1e-9 ? 0 : z / across;
        return new double[]{nx / length, -s / length, nz / length};
    }

    /**
     * How far out from the axis a fin reaches at a height, measured along the fin; 0 where it does not reach past the
     * body. Its outer edge stands straight up from its foot to its tip's top, its leading edge slopes in from there to
     * the root on the body, and under the body it runs down from the bottom's edge to its foot.
     */
    static double finReach(double height) {
        if (height < 0 || height > FIN_ROOT_TOP) {
            return 0;
        }
        if (height <= FIN_TIP_TOP) {
            return FIN_TIP;
        }
        return FIN_TIP - (FIN_TIP - RADIUS) * (height - FIN_TIP_TOP) / (FIN_ROOT_TOP - FIN_TIP_TOP);
    }

    /**
     * How far in toward the axis a fin's lower edge comes at a height under the body, its bottom so high: from its foot
     * on the ground rising to the bottom's edge; nothing beside the body.
     */
    static double finInner(double height, double base) {
        return height >= base ? 0 : FIN_FOOT - (FIN_FOOT - RADIUS) * height / base;
    }
}
