package dev.aerolarge.airframe;

/**
 * Where a zeppelin's flank screens are (see the client's {@code FlankScreenRenderer}): one on each flank, under the
 * hull's equator, aft of the promenade windows and forward of the engine cars, clear of her crest and name above it,
 * following the hull's curve just off the cover, so it is seen from the ground under her. Places on it are measured
 * along the hull from its front edge and round the hull from its top edge.
 */
public final class FlankScreen {
    /** How far aft of the nose's tip the screen starts, and how long it is, in blocks. */
    public static final int FRONT = 190;
    public static final int LENGTH = 64;
    /** How far below the hull's equator its top and its bottom are, measured round the hull, in blocks. */
    public static final double TOP = 3;
    public static final double BOTTOM = 23;
    /** How far off the cover it stands, in blocks, so it never flickers into it. */
    public static final double OFF_THE_COVER = 0.6;

    private FlankScreen() {
    }

    /** How tall it is, round the hull, in blocks. */
    public static double height() {
        return BOTTOM - TOP;
    }

    /**
     * The hull's radius under the screen, and how far out from the axis the screen is, in blocks: on a full-size hull,
     * the only one it is laid on yet (see {@link ZeppelinFlankScreen}).
     */
    public static double radius() {
        return ZeppelinSize.FULL.radiusAt(FRONT + LENGTH / 2.0) + OFF_THE_COVER;
    }
}
