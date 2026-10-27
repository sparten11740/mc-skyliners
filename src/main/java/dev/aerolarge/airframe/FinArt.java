package dev.aerolarge.airframe;

/**
 * Where the ship's colours are painted on a zeppelin's fins (see the client's {@code ShipsColours}): on the top fin
 * and the bottom one, as a flag flying aft, one block to each of the banner's pixels, in the middle of the fin's outer
 * part, whose edges follow the hull's taper, with a margin of the livery's own colour round it. The banner's top is
 * at the fin's leading edge, and its left edge along the flag's top, skyward on both fins, as a banner flown as a
 * flag is; seen from the starboard side, a fin shows the flag the other way round, as flags on aircraft tails do. The
 * side fins keep the livery's colour, as the Hindenburg's did.
 */
public final class FinArt {
    /** A banner's picture, in pixels: as wide as it is, and as tall. */
    public static final int BANNER_WIDTH = 20;
    public static final int BANNER_HEIGHT = 40;
    /** How far aft of the nose's tip the picture starts and ends, in blocks: one block to each of the banner's rows. */
    public static final int FRONT = (int) Math.round((ZeppelinShape.FIN_TIP_FRONT + ZeppelinShape.FIN_TIP_BACK) / 2 * ZeppelinSize.FULL.scale()) - BANNER_HEIGHT / 2;
    public static final int BACK = FRONT + BANNER_HEIGHT;
    /** How far out from the hull its lowest blocks are, in blocks: as much fin over it as under it. */
    private static final double OUT = (ZeppelinShape.FIN_SPAN * ZeppelinSize.FULL.scale() - BANNER_WIDTH) / 2;

    private FinArt() {
    }

    /**
     * The pixel of the banner's picture a block of fabric shows, as its place in the picture row by row from the top,
     * each row from the left; -1 for a block not in it.
     */
    public static int pixel(Livery.Spot spot) {
        if (Math.abs(spot.across()) >= 0.5 || spot.along() < FRONT || spot.along() >= BACK) {
            return -1;
        }
        double out = Math.abs(spot.up()) - Livery.radiusAt(spot.along()) - OUT;
        if (out < 0 || out >= BANNER_WIDTH) {
            return -1;
        }
        int up = (int) Math.floor(out);
        int column = spot.up() > 0 ? BANNER_WIDTH - 1 - up : up;
        return (int) Math.floor(spot.along() - FRONT) * BANNER_WIDTH + column;
    }
}
