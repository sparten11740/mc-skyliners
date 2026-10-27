package dev.aerolarge.airframe;

/**
 * Where the ship's colours are painted on a zeppelin's fins (see the client's {@code ShipsColours}): on the top fin
 * and the bottom one, as a flag flying aft, one block to each of the banner's pixels (or to every second one, on a
 * smaller zeppelin's smaller fins, see {@link #pixelsPerBlock}), in the middle of the fin's outer
 * part, whose edges follow the hull's taper, with a margin of the livery's own colour round it. The banner's top is
 * at the fin's leading edge, and its left edge along the flag's top, skyward on both fins, as a banner flown as a
 * flag is; seen from the starboard side, a fin shows the flag the other way round, as flags on aircraft tails do. The
 * side fins keep the livery's colour, as the Hindenburg's did.
 */
public final class FinArt {
    /** A banner's picture, in pixels: as wide as it is, and as tall. */
    public static final int BANNER_WIDTH = 20;
    public static final int BANNER_HEIGHT = 40;

    private FinArt() {
    }

    /**
     * How many of a picture's pixels across and along each block of the hull stands for on a zeppelin of a size: one at
     * full size; two at half, whose fins and flanks are too small for the pictures at one block to a pixel.
     */
    public static int pixelsPerBlock(ZeppelinSize size) {
        return switch (size) {
            case HALF -> 2;
            case FULL -> 1;
        };
    }

    /** How far aft of the nose's tip the picture starts on a zeppelin of a size, in blocks. */
    public static int front(ZeppelinSize size) {
        return (int) Math.round((ZeppelinShape.FIN_TIP_FRONT + ZeppelinShape.FIN_TIP_BACK) / 2 * size.scale()) - rows(size) / 2;
    }

    /** How far aft of the nose's tip the picture ends on a zeppelin of a size, in blocks. */
    public static int back(ZeppelinSize size) {
        return front(size) + rows(size);
    }

    /**
     * The pixel of the banner's picture a block of fabric shows, as its place in the picture row by row from the top,
     * each row from the left; -1 for a block not in it.
     */
    public static int pixel(Livery.Spot spot) {
        ZeppelinSize size = spot.size();
        int front = front(size);
        if (Math.abs(spot.across()) >= 0.5 || spot.along() < front || spot.along() >= back(size)) {
            return -1;
        }
        // As much fin over the picture as under it.
        double out = Math.abs(spot.up()) - spot.radius() - (ZeppelinShape.FIN_SPAN * size.scale() - columns(size)) / 2;
        if (out < 0 || out >= columns(size)) {
            return -1;
        }
        int up = (int) Math.floor(out);
        int column = spot.up() > 0 ? columns(size) - 1 - up : up;
        int each = pixelsPerBlock(size);
        return (int) Math.floor(spot.along() - front) * each * BANNER_WIDTH + column * each;
    }

    /** How many blocks along the hull the picture covers, one row of them to so many of its rows. */
    private static int rows(ZeppelinSize size) {
        return BANNER_HEIGHT / pixelsPerBlock(size);
    }

    /** How many blocks out along the fin the picture covers. */
    private static int columns(ZeppelinSize size) {
        return BANNER_WIDTH / pixelsPerBlock(size);
    }
}
