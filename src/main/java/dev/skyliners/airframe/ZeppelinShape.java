package dev.skyliners.airframe;

/**
 * The zeppelin's outline in metres, after the Hindenburg: 245 m long and 41.2 m across, a body of revolution with a
 * blunt nose, a long full middle and a long taper to a point at the stern, its control car slung under the bow, four
 * engine cars out on the flanks and four fins in a cross at the tail. Drawn as the airliner is (see
 * {@link AirframeShape}): z back from the nose's tip, x across to starboard, y up, here from the ground under it as
 * it rests on its control car and its lower fin, the hull's axis so high over it as its layout raises it. It is cut
 * into blocks by {@link ZeppelinLayout} at its size's blocks a metre (see {@link ZeppelinSize}).
 */
final class ZeppelinShape {
    static final double LENGTH = 245;
    static final double RADIUS = 20.6;
    /**
     * The hull's outline along its length, as shares of it: the nose a quarter ellipse up to the first, the hull at its
     * full radius from there to the second, then tapering to the stern's point, gently at first.
     */
    private static final double NOSE = 0.2;
    static final double TAPER = 0.5;
    /** How the stern tapers: the radius falls as (1 - s^2)^TAPER_POWER, s running 0 to 1 down the taper. */
    private static final double TAPER_POWER = 0.9;
    /**
     * How high the hull's axis stands over the ground the zeppelin rests on, at least: its layout raises it further
     * where a small hull would otherwise sink onto the full-size control car under it.
     */
    static final double AXIS_HEIGHT = 25;

    /**
     * The main rings, the duralumin frames the hull is built on: the first ring this far back from the nose, the last
     * this far, the sixteen bays between them each holding a gas cell, as the Hindenburg's sixteen did.
     */
    static final double FIRST_RING = 12;
    static final double LAST_RING = 228;
    static final int CELLS = 16;
    /**
     * Each gas cell: a balloon hanging under the crown of its bay, as long as the bay less a gap at either end, and at
     * its fullest (in the full middle of the hull) so wide and tall, in metres, scaled down with the hull's radius
     * toward either end, its middle a share of the hull's radius over the axis. A small hull's cells fill more of it
     * (see {@link ZeppelinSize}): their envelope and the hot air's edge are a block thick at every size, a share of a
     * small cell that would leave it too little gas to float the ship.
     */
    record CellFill(double gap, double halfWidth, double halfHeight, double rise) {
    }

    /** The cells as they fill a full-size hull. */
    static final CellFill CELLS_FILL = new CellFill(1, 10, 6.5, 0.45);

    /**
     * The control car: its front this far back, at every size, the car itself a player's size (see
     * {@link ZeppelinLayout}); and at full size its back this far back and its floor this high over the ground, as
     * its cameras are placed (see {@link ZeppelinCamera}).
     */
    static final double CAR_FRONT = 30;
    static final double CAR_BACK = 44;
    static final double CAR_FLOOR = 0.5;

    /** The passenger decks, inside the hull's belly as the Hindenburg's were: from this far back. */
    static final double DECKS_FRONT = 54;

    /**
     * The four engine cars, each a pod on struts out from the hull: the forward pair this far back and the aft pair
     * that far, their axis this share of the hull's radius under its axis.
     */
    static final double[] ENGINE_STATIONS = {132, 168};
    static final double ENGINE_DROP = 0.35;

    /**
     * The fins: their roots along the hull from this far back to the stern, their tips from that far back to that, and
     * how far past the hull they reach at the tips, the lower fin down to the ground.
     */
    static final double FIN_ROOT_FRONT = 196;
    static final double FIN_ROOT_BACK = 240;
    static final double FIN_TIP_FRONT = 214;
    static final double FIN_TIP_BACK = 238;
    static final double FIN_SPAN = 15;

    private ZeppelinShape() {
    }

    /** The hull's radius at a distance back from the nose; 0 past either end. */
    static double radius(double z) {
        if (z <= 0 || z >= LENGTH) {
            return 0;
        }
        double t = z / LENGTH;
        if (t < NOSE) {
            double along = (NOSE - t) / NOSE;
            return RADIUS * Math.sqrt(1 - along * along);
        }
        if (t <= TAPER) {
            return RADIUS;
        }
        double s = (t - TAPER) / (1 - TAPER);
        return RADIUS * Math.pow(1 - s * s, TAPER_POWER);
    }

    /** How fast the radius changes going aft, in metres of radius a metre. */
    static double slope(double z) {
        double h = 0.01;
        return (radius(z + h) - radius(z - h)) / (2 * h);
    }

    /**
     * How far a point lies outside the hull's surface, in metres, negative inside: the radial distance off the surface,
     * tilted back to the surface's normal where the hull narrows, so it is close to the true distance everywhere but
     * at the very tips.
     */
    static double depth(double x, double y, double z, double axis) {
        double across = Math.hypot(x, y - axis);
        if (z <= 0 || z >= LENGTH) {
            // Past the tips: the distance to the nearer tip.
            double tip = z <= 0 ? -z : z - LENGTH;
            return Math.hypot(across, tip);
        }
        double s = slope(z);
        return (across - radius(z)) / Math.sqrt(1 + s * s);
    }

    /** Whether a point lies inside the hull, its axis so high over the ground. */
    static boolean inHull(double x, double y, double z, double axis) {
        return depth(x, y, z, axis) < 0;
    }

    /** The way out of the hull from a point near its surface, a unit vector, its axis so high over the ground. */
    static double[] outward(double x, double y, double z, double axis) {
        double dy = y - axis;
        double across = Math.hypot(x, dy);
        double s = slope(z);
        double nx = across < 1e-9 ? 0 : x / across;
        double ny = across < 1e-9 ? 1 : dy / across;
        double length = Math.sqrt(1 + s * s);
        return new double[]{nx / length, ny / length, -s / length};
    }

    /** The bay a gas cell hangs in, 0 at the bow: its front ring and back ring. */
    static double ringAt(int ring) {
        return FIRST_RING + (LAST_RING - FIRST_RING) * ring / CELLS;
    }

    /** The middle of a gas cell's bay. */
    static double cellMiddle(int cell) {
        return (ringAt(cell) + ringAt(cell + 1)) / 2;
    }

    /** A cell's size against the biggest, where the hull narrows toward either end. */
    static double cellScale(int cell) {
        double z = cellMiddle(cell);
        return Math.min(radius(ringAt(cell)), Math.min(radius(z), radius(ringAt(cell + 1)))) / RADIUS;
    }

    /**
     * A gas cell's balloon, in metres: an ellipsoid under the crown of its bay, its middle along the hull and over the
     * ground, and its half length, width and height, filling its bay less the gap at either end, as wide and tall as
     * the hull leaves room for there.
     */
    record Cell(double middle, double centreY, double halfLength, double halfWidth, double halfHeight) {
        /** A cell's balloon in a hull whose axis stands so high over the ground, its cells filling it so. */
        static Cell of(int cell, double axis, CellFill fill) {
            double k = cellScale(cell);
            double halfLength = (ringAt(cell + 1) - ringAt(cell)) / 2 - fill.gap();
            return new Cell(cellMiddle(cell), axis + fill.rise() * RADIUS * k, halfLength, fill.halfWidth() * k, fill.halfHeight() * k);
        }

        boolean contains(double x, double y, double z) {
            double ex = x / halfWidth;
            double ey = (y - centreY) / halfHeight;
            double ez = (z - middle) / halfLength;
            return ex * ex + ey * ey + ez * ez <= 1;
        }
    }

    /** The hull's lowest point at a station, over the ground, its axis so high over it. */
    static double belly(double z, double axis) {
        return axis - radius(z);
    }

    /**
     * The fins' outline: how far out from the hull's axis a fin reaches at a station, measured along the fin; 0 where
     * the fin does not reach. Its leading edge sweeps back from the root to the tip, its trailing edge runs nearly
     * straight down to the stern.
     */
    static double finReach(double z) {
        if (z < FIN_ROOT_FRONT || z > FIN_ROOT_BACK) {
            return 0;
        }
        double root = radius(z);
        if (z < FIN_TIP_FRONT) {
            return root + FIN_SPAN * (z - FIN_ROOT_FRONT) / (FIN_TIP_FRONT - FIN_ROOT_FRONT);
        }
        if (z <= FIN_TIP_BACK) {
            return root + FIN_SPAN;
        }
        return root + FIN_SPAN * (FIN_ROOT_BACK - z) / (FIN_ROOT_BACK - FIN_TIP_BACK);
    }
}
