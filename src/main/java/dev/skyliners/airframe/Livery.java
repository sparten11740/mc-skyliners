package dev.skyliners.airframe;

import dev.skyliners.autopilot.Craft;
import it.unimi.dsi.fastutil.HashCommon;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * How a zeppelin or a rocket is painted: its cover, and the fabric of its fins (a zeppelin's control car and engine
 * cars too). Its autopilot keeps its livery and sends it to the clients, which draw each block of its cover and fabric
 * tinted the colour its livery gives the spot the block is on (see the client's {@code ZeppelinPaint}): it is
 * repainted without a block of it changing, so a zeppelin's balloons, the mass and the assembly are as they were. The
 * cover's own texture is a pale weave for the tint to colour. Each kind of ship has six liveries of its own to pick
 * from (see {@link #swatches}), some of them shared; a zeppelin's spot is measured along her, a rocket's up her and
 * round her (see {@link #rocketColour}). Saved by name; one not known any more is silver.
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

        /**
         * On a rocket, her fins are blue and a cheatline runs on up from each of them to her nose, between her
         * windows, which stand out to the four ways.
         */
        @Override
        public int rocketColour(RocketSpot spot, boolean fin) {
            double fromDiagonal = Math.abs(Math.floorMod(Math.round(spot.round()), (long) QUARTER_TURN) - QUARTER_TURN / 2);
            boolean onLine = fromDiagonal <= ROCKET_CHEATLINE_HALF && spot.height() <= RocketShape.NOSE_BASE / RocketShape.HEIGHT;
            return fin || onLine ? fabric() : hull();
        }
    },
    /** Polar red, as an expedition's to the ice, with white fins and cars. */
    POLAR(0xCC3A2E, 0xF4F4F4),
    /** Midnight blue, scattered with gold stars. */
    MIDNIGHT(0x26335E, 0x1C2548) {
        @Override
        public int colour(Spot spot, boolean fabric) {
            return !fabric && spot.round() <= STARS_DOWN_TO && star(spot.seed()) ? STAR : super.colour(spot, fabric);
        }

        /** On a rocket, her stars are all round her, nose and all, but never on a window's frame. */
        @Override
        public int rocketColour(RocketSpot spot, boolean fin) {
            return !fin && !spot.window() && star(spot.seed()) ? STAR : super.rocketColour(spot, fin);
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
    },
    /**
     * The comic-book moon rocket's: red and white checks all over her, a band of them for each story (see
     * {@link RocketSpot#band}), each check out to one of the four ways or between them, so each window sits in one
     * check and each fin is checked up its length.
     */
    CHECKER(0xCC2A24, 0xF4F4F4) {
        @Override
        public int rocketColour(RocketSpot spot, boolean fin) {
            long round = Math.round(spot.round() / CHECK_ROUND);
            return Math.floorMod(round + spot.band(), 2) == 0 ? hull() : fabric();
        }
    },
    /**
     * White with black roll stripes, as the first big rockets were painted to show how they turned: her front and back
     * quarters black on her entry story and every third story over it, her sides white there, split between them on
     * the diagonals so each window is all one colour, and her fins black.
     */
    ROLL(0xF4F4F4, 0x1A1A1A) {
        @Override
        public int rocketColour(RocketSpot spot, boolean fin) {
            boolean frontOrBack = Math.floorMod(Math.round(spot.round() / QUARTER_TURN), 2) == 0;
            boolean striped = spot.band() >= 0 && spot.band() % ROLL_EVERY == 0 && spot.band() < spot.stories();
            return fin || frontOrBack && striped ? fabric() : hull();
        }
    },
    /** Silver, with her fins and her nose red. */
    RED_TRIM(0xD5D5D5, 0xCC2A24) {
        @Override
        public int rocketColour(RocketSpot spot, boolean fin) {
            return fin || spot.nose() ? fabric() : hull();
        }
    };

    /** Each kind of ship's liveries, as its livery board and livery selector show them: two rows of three. */
    public static final int SWATCH_ROWS = 2;
    public static final int SWATCH_COLUMNS = 3;
    private static final Livery[][] ZEPPELIN_SWATCHES = {{SILVER, CREAM, NAVY}, {AIRLINE, POLAR, MIDNIGHT}};
    private static final Livery[][] ROCKET_SWATCHES = {{CHECKER, ROLL, RED_TRIM}, {AIRLINE, POLAR, MIDNIGHT}};

    /** How long a repaint takes to sweep from the nose to the tail, in ticks: a minute. */
    public static final int REPAINT_TICKS = 60 * 20;
    /** How far past the hull's stern a repaint sweeps on, in blocks: over the fins, the last of it. */
    private static final double SWEEP_PAST = 30;
    /** The airliner's cheatline: how far round from the crown it runs, in degrees, and from and to where along. */
    private static final double CHEATLINE_TOP = 98;
    private static final double CHEATLINE_BOTTOM = 106;
    private static final double CHEATLINE_FROM = 0.06;
    private static final double CHEATLINE_TO = 0.8;
    /** The midnight livery's stars: about one block of its cover in so many, as far round as this, in degrees, gold. */
    private static final int STAR_ONE_IN = 89;
    private static final double STARS_DOWN_TO = 120;
    private static final int STAR = 0xFFD866;
    /** A quarter of a turn round a rocket, in degrees. */
    private static final double QUARTER_TURN = 90;
    /** How far round either side of its diagonal each of a rocket's cheatlines in the airliner's colours reaches. */
    private static final double ROCKET_CHEATLINE_HALF = 5;
    /** How far round her each of the moon rocket's checks reaches, in degrees. */
    private static final double CHECK_ROUND = 45;
    /** The roll stripes are on her entry story and every so many stories over it. */
    private static final int ROLL_EVERY = 3;
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

    /** The colour a spot on a zeppelin is tinted: of the cover there, or of the fabric of a fin or a car. */
    public int colour(Spot spot, boolean fabric) {
        return fabric ? this.fabric : hull;
    }

    /** The colour a spot on a rocket is tinted: of the cover there, or of a fin. */
    public int rocketColour(RocketSpot spot, boolean fin) {
        return fin ? fabric : hull;
    }

    /** The liveries a kind of ship is painted in from its livery board and livery selector, in their rows. */
    public static Livery[][] swatches(Craft craft) {
        return craft == Craft.ROCKET ? ROCKET_SWATCHES : ZEPPELIN_SWATCHES;
    }

    /**
     * The livery at a place among a kind of ship's swatches, counted along each row from the left, the top row first,
     * as a board's or a selector's tiles are; null for none (-1).
     */
    @Nullable
    public static Livery swatch(Craft craft, int place) {
        return place < 0 ? null : swatches(craft)[place / SWATCH_COLUMNS][place % SWATCH_COLUMNS];
    }

    /** Where a livery is among a kind of ship's swatches, counted as {@link #swatch} counts; -1 for one not there. */
    public static int swatchPlace(Craft craft, @Nullable Livery livery) {
        for (int place = 0; place < SWATCH_ROWS * SWATCH_COLUMNS; place++) {
            if (livery != null && swatch(craft, place) == livery) {
                return place;
            }
        }
        return -1;
    }

    /** Whether a block is one of the gold stars midnight blue is scattered with, from the number its position gives. */
    private static boolean star(long seed) {
        return Math.floorMod(HashCommon.mix(seed), (long) STAR_ONE_IN) == 0;
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

    /**
     * How far aft of the nose's tip a repaint sweeps on a zeppelin of a size, in blocks: past the hull's length over
     * the fins, the last of it.
     */
    public static double sweep(ZeppelinSize size) {
        return size.length() + SWEEP_PAST;
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
     * Where a block sits on a rocket: how far up her it is, as a share of her height from her fins' feet to her nose's
     * tip; how far round her it is from her front, where her door is, clockwise seen from above, in degrees; which band
     * of her it is in, and how many stories she has (see {@link #band}); whether it is the frame round a window, which
     * is painted all one colour, as the middle of the window is; and a number from its position that a pattern can
     * scatter by. Her stories are the bands under the first of her nose's.
     */
    public record RocketSpot(double height, double round, int band, int stories, boolean window, long seed) {
        /**
         * How many rows tall a band of her is outside her stories, under her entry story and over her cockpit's floor,
         * and about how tall a story is: one taller than half as much again is cut into bands about as tall.
         */
        private static final int BAND_ROWS = 9;

        /** Whether it is on her nose cone, over the body. */
        public boolean nose() {
            return height >= RocketShape.NOSE_BASE / RocketShape.HEIGHT;
        }

        /**
         * The band of a rocket a row up from her fins' feet is in, from her stories' floors (the cockpit's last): her
         * entry story is band 0 and each story over it the next, so a pattern that goes by bands keeps each story's
         * windows in one; under her entry story the bands run down from -1, and from her cockpit's floor up on from
         * her stories, each {@link #BAND_ROWS} tall.
         */
        public static int band(int row, int[] floors) {
            int first = floors[0];
            int cockpit = floors[floors.length - 1];
            if (row < first) {
                return -1 - (first - 1 - row) / BAND_ROWS;
            }
            int band = 0;
            for (int story = 0; story + 1 < floors.length; story++) {
                int bands = bands(floors[story + 1] - floors[story]);
                if (row < floors[story + 1]) {
                    return band + Math.min((row - floors[story]) / BAND_ROWS, bands - 1);
                }
                band += bands;
            }
            return band + (row - cockpit) / BAND_ROWS;
        }

        /** How many bands a story so many rows tall is cut into. */
        private static int bands(int rows) {
            return Math.max(1, Math.round(rows / (float) BAND_ROWS));
        }
    }

    /**
     * Where a block sits on a zeppelin: how far aft of its nose's tip, how far up from its axis and how far out from
     * it to starboard, in blocks, a number from its position that a pattern can scatter by, and how big the zeppelin's
     * hull is.
     */
    public record Spot(double along, double up, double across, long seed, ZeppelinSize size) {
        /**
         * Where a block sits on the zeppelin of a size whose blocks lie in a box, from one corner to the other, its
         * nose facing the way given.
         */
        public static Spot of(BlockPos pos, BlockPos min, BlockPos max, Direction nose, ZeppelinSize size) {
            Direction starboard = nose.getClockWise();
            double tip = Math.max(project(min, nose), project(max, nose));
            double middle = (project(min, starboard) + project(max, starboard)) / 2;
            return new Spot(tip - project(pos, nose), pos.getY() + 0.5 - min.getY() - size.axis(), project(pos, starboard) - middle, pos.asLong(), size);
        }

        /** The hull's radius where it lies along it, in blocks: where the flanks are there, to paint them from. */
        public double radius() {
            return size.radiusAt(along);
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
            for (int ring = 0; ring < ZeppelinSize.rings(); ring++) {
                nearest = Math.min(nearest, Math.abs(along - size.ringAt(ring)));
            }
            return nearest;
        }

        /** Whether it lies between two shares of the hull's length, from the nose. */
        public boolean alongFlank(double from, double to) {
            return along >= from * size.length() && along <= to * size.length();
        }
    }
}
