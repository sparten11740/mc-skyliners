package dev.aerolarge.airframe;

import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * How a zeppelin is painted: its cover, and the fabric of its fins, its control car and its engine cars. A zeppelin's
 * autopilot keeps its livery and sends it to the clients, which draw each block of its cover and fabric tinted the
 * colour its livery gives the spot the block is on (see the client's {@code ZeppelinPaint}): a zeppelin is repainted
 * without a block of it changing, so its balloons, its mass and its assembly are as they were. Its cover's own
 * texture is a pale weave for the tint to colour. Saved by name; one not known any more is silver.
 */
public enum Livery {
    /** Silver dope, as the Hindenburg's: the zeppelin as it is built. */
    SILVER(0xD5D5D5, 0xD5D5D5),
    /** The Graf Zeppelin's warm cream. */
    CREAM(0xF0E2C0, 0xD9C9A0),
    /** Navy grey, as a warship's, a darker grey on its fins. */
    NAVY(0x8A939C, 0x6D7680),
    /** The airliner's own: white, with a blue cheatline down each flank, and its fins and cars blue. */
    AIRLINE(0xF7F8FA, 0x2F5BC0) {
        @Override
        public int colour(Spot spot, boolean fabric) {
            return fabric || spot.alongFlank(CHEATLINE_FROM, CHEATLINE_TO) && spot.round() >= CHEATLINE_TOP && spot.round() <= CHEATLINE_BOTTOM
                ? fabric() : hull();
        }
    },
    /** Polar red, as an expedition's to the ice, with white fins and cars. */
    POLAR(0xCC3A2E, 0xF4F4F4),
    /** Midnight blue, scattered with gold stars. */
    MIDNIGHT(0x26335E, 0x1C2548) {
        @Override
        public int colour(Spot spot, boolean fabric) {
            return !fabric && spot.round() <= STARS_DOWN_TO && Math.floorMod(HashCommon.mix(spot.seed()), (long) STAR_ONE_IN) == 0 ? STAR : super.colour(spot, fabric);
        }
    },
    /**
     * Dressed for Christmas, as the Festive Zeppelin Blueprint builds it (not on the livery board): frosted silver,
     * snow on its crown, a red band edged white round it at every ring, its fins and cars green, and its gas cells red
     * and green within.
     */
    FESTIVE(0xC9D3DC, 0x1F7A3A) {
        @Override
        public int colour(Spot spot, boolean fabric) {
            if (fabric) {
                return fabric();
            }
            if (spot.round() <= SNOW_DOWN_TO) {
                return SNOW;
            }
            double fromRing = spot.fromRing();
            if (fromRing <= BAND_HALF) {
                return fromRing > BAND_HALF - BAND_EDGE ? SNOW : BAND;
            }
            return hull();
        }

        @Override
        public String[] cellColours() {
            return FESTIVE_CELLS;
        }
    };

    /** How long a repaint takes to sweep from the nose to the tail, in ticks: a minute. */
    public static final int REPAINT_TICKS = 60 * 20;
    /** The airliner's cheatline: how far round from the crown it runs, in degrees, and from and to where along. */
    private static final double CHEATLINE_TOP = 98;
    private static final double CHEATLINE_BOTTOM = 106;
    private static final double CHEATLINE_FROM = 0.06;
    private static final double CHEATLINE_TO = 0.8;
    /** The midnight livery's stars: about one block of its cover in so many, as far round as this, in degrees, gold. */
    private static final int STAR_ONE_IN = 89;
    private static final double STARS_DOWN_TO = 120;
    private static final int STAR = 0xFFD866;
    /**
     * The festive livery: how far round from the crown its snow reaches, in degrees; how far either side of a ring its
     * band reaches and how much of that is its white edge, in blocks; its colours, and its cells'.
     */
    private static final double SNOW_DOWN_TO = 32;
    private static final double BAND_HALF = 3;
    private static final double BAND_EDGE = 1;
    private static final int SNOW = 0xFFFFFF;
    private static final int BAND = 0xC8202A;
    private static final String[] FESTIVE_CELLS = {"red", "green"};

    private final int hull;
    private final int fabric;

    Livery(int hull, int fabric) {
        this.hull = hull;
        this.fabric = fabric;
    }

    /** The colour a spot is tinted: of the cover there, or of the fabric of a fin or a car. */
    public int colour(Spot spot, boolean fabric) {
        return fabric ? this.fabric : hull;
    }

    int hull() {
        return hull;
    }

    /** The colours its gas cells are built in, bow to stern, one after another: the rainbow, unless it has its own. */
    public String[] cellColours() {
        return ZeppelinLayout.CELL_COLOURS;
    }

    int fabric() {
        return fabric;
    }

    /** The hull's radius at a distance aft of its nose's tip, in blocks: where its flanks are, to paint them from. */
    public static double radiusAt(double along) {
        return ZeppelinShape.radius(along / ZeppelinShape.SCALE) * ZeppelinShape.SCALE;
    }

    /** How high the hull's axis runs over the zeppelin's lowest blocks, in blocks. */
    public static double axisHeight() {
        return ZeppelinShape.AXIS_HEIGHT * ZeppelinShape.SCALE;
    }

    /** A livery by its saved name; silver for none or one not known. */
    public static Livery named(String name) {
        for (Livery livery : values()) {
            if (livery.name().equals(name)) {
                return livery;
            }
        }
        return SILVER;
    }

    /**
     * Where a block sits on a zeppelin: how far aft of its nose's tip, how far up from its axis and how far out from
     * it to starboard, in blocks, and a number from its position that a pattern can scatter by.
     */
    public record Spot(double along, double up, double across, long seed) {
        /** The hull's length, nose to stern, in blocks. */
        private static final double LENGTH = ZeppelinShape.LENGTH * ZeppelinShape.SCALE;
        /** How high the hull's axis runs over the zeppelin's lowest blocks, in blocks. */
        private static final double AXIS = ZeppelinShape.AXIS_HEIGHT * ZeppelinShape.SCALE;

        /**
         * Where a block sits on the zeppelin whose blocks lie in a box, from one corner to the other, its nose facing the
         * way given.
         */
        public static Spot of(BlockPos pos, BlockPos min, BlockPos max, Direction nose) {
            Direction starboard = nose.getClockWise();
            double tip = Math.max(project(min, nose), project(max, nose));
            double middle = (project(min, starboard) + project(max, starboard)) / 2;
            return new Spot(tip - project(pos, nose), pos.getY() + 0.5 - min.getY() - AXIS, project(pos, starboard) - middle, pos.asLong());
        }

        /** How far a block's middle lies the way given, in blocks. */
        private static double project(BlockPos pos, Direction way) {
            return (pos.getX() + 0.5) * way.getStepX() + (pos.getZ() + 0.5) * way.getStepZ();
        }

        /** How far round from the crown it is, in degrees: 0 on top, 90 at the side, 180 underneath. */
        public double round() {
            return Math.toDegrees(Math.atan2(Math.abs(across), up));
        }

        /** How far it lies along the hull from the nearest of its rings, in blocks. */
        public double fromRing() {
            double nearest = Double.MAX_VALUE;
            for (int ring = 0; ring <= ZeppelinShape.CELLS; ring++) {
                nearest = Math.min(nearest, Math.abs(along - ZeppelinShape.ringAt(ring) * ZeppelinShape.SCALE));
            }
            return nearest;
        }

        /** Whether it lies between two shares of the hull's length, from the nose. */
        public boolean alongFlank(double from, double to) {
            return along >= from * LENGTH && along <= to * LENGTH;
        }
    }
}
