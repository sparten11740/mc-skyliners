package dev.aerolarge.airframe;

/**
 * Where a christened zeppelin's crest and name are painted on her flanks (see the client's {@code ChristeningPaint}),
 * the same on both: forward, ahead of the passenger decks, her crest, a banner upright in a gold frame, one block to
 * each of its pixels (on a smaller zeppelin, to every second one, see {@link FinArt#pixelsPerBlock}), its middle on
 * the hull's equator; and aft of it her name, in tall serif capitals standing on the equator, as the Graf Zeppelin's
 * name ran along her forward flanks, as big at every size but running no further than a share of her length. Both read the right way round from either
 * side, so on the starboard side the name runs from aft forward. Places on the flank are measured round the hull's
 * curve, so the crest is not squashed where the hull turns away above and below.
 */
public final class FlankArt {
    /**
     * How far aft of the nose's tip the crest's frame starts, in metres along the hull; how far aft of the crest the
     * name starts, in blocks; and the longest it runs, in metres along the hull.
     */
    private static final double CREST_FRONT = 28;
    private static final int NAME_GAP = 6;
    private static final double NAME_LONGEST = 100;
    /** How far round from the crown a block must be to be on a flank, either way, in degrees. */
    private static final double FLANK_FROM = 40;
    private static final double FLANK_TO = 140;

    private FlankArt() {
    }

    /** How far aft of the nose's tip the crest's frame starts on a zeppelin of a size, in blocks. */
    public static int crestFront(ZeppelinSize size) {
        return (int) Math.round(CREST_FRONT * size.scale());
    }

    /** How wide the crest is with its frame, in blocks: its banner's picture at the size's blocks a pixel. */
    public static int crestWidth(ZeppelinSize size) {
        return FinArt.BANNER_WIDTH / FinArt.pixelsPerBlock(size) + 2;
    }

    /** How tall the crest is with its frame, in blocks. */
    public static int crestHeight(ZeppelinSize size) {
        return FinArt.BANNER_HEIGHT / FinArt.pixelsPerBlock(size) + 2;
    }

    /** How far aft of the nose's tip the name starts, a little aft of the crest, in blocks. */
    public static int nameFront(ZeppelinSize size) {
        return crestFront(size) + crestWidth(size) + NAME_GAP;
    }

    /** The longest the name runs, in blocks: as long a share of the hull at every size. */
    public static int nameLongest(ZeppelinSize size) {
        return (int) Math.round(NAME_LONGEST * size.scale());
    }

    /** Whether a spot is on one of the hull's flanks, well clear of its crown and its belly. */
    public static boolean onFlank(Livery.Spot spot) {
        double round = spot.round();
        return round >= FLANK_FROM && round <= FLANK_TO;
    }

    /**
     * How far above the hull's equator a spot on a flank is, measured round the hull, in blocks; less than nothing
     * below.
     */
    public static double rise(Livery.Spot spot) {
        return Math.toRadians(90 - spot.round()) * spot.radius();
    }

    /**
     * How far a block at a spot on a flank reaches either way of its middle, measured round the hull, in blocks: half a
     * block on the equator, where the blocks stand one over another, and more where the hull turns away, a block's
     * width showing round it slantwise.
     */
    public static double spread(Livery.Spot spot) {
        double slant = Math.toRadians(90 - spot.round());
        return (Math.abs(Math.sin(slant)) + Math.abs(Math.cos(slant))) / 2;
    }
}
