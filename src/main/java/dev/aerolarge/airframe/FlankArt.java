package dev.aerolarge.airframe;

/**
 * Where a christened zeppelin's crest and name are painted on her flanks (see the client's {@code ChristeningPaint}),
 * the same on both: forward, ahead of the passenger decks, her crest, a banner upright in a gold frame, one block to
 * each of its pixels, its middle on the hull's equator; and aft of it her name, in tall serif capitals standing on the
 * equator, as the Graf Zeppelin's name ran along her forward flanks. Both read the right way round from either
 * side, so on the starboard side the name runs from aft forward. Places on the flank are measured round the hull's
 * curve, so the crest is not squashed where the hull turns away above and below.
 */
public final class FlankArt {
    /**
     * How far aft of the nose's tip the crest's frame starts, and how wide and tall it is with its frame, in blocks.
     */
    public static final int CREST_FRONT = 56;
    public static final int CREST_WIDTH = FinArt.BANNER_WIDTH + 2;
    public static final int CREST_HEIGHT = FinArt.BANNER_HEIGHT + 2;
    /** How far aft of the nose's tip the name starts, a little aft of the crest, and the longest it runs, in blocks. */
    public static final int NAME_FRONT = CREST_FRONT + CREST_WIDTH + 6;
    public static final int NAME_LONGEST = 240;
    /** How far round from the crown a block must be to be on a flank, either way, in degrees. */
    private static final double FLANK_FROM = 40;
    private static final double FLANK_TO = 140;

    private FlankArt() {
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
        return Math.toRadians(90 - spot.round()) * Livery.radiusAt(spot.along());
    }
}
