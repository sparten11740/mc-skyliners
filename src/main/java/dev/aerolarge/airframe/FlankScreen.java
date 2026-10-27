package dev.aerolarge.airframe;

/**
 * Where a zeppelin's flank screens are (see the client's {@code FlankScreenRenderer}): one on each flank, following the
 * hull's curve just off the cover, so it is seen from the ground under her. On a full-size zeppelin, under the hull's
 * equator, aft of the promenade windows and forward of the engine cars, clear of her crest and name above it; on a
 * half-size one, whose windows run as far aft as her engine cars, as long but lower on her flank, under the promenade
 * windows along her decks, ending ahead of her forward engine car and clear of the boarding stairs under her. Places
 * on it are measured along the hull from its front edge and round the hull from its top edge.
 *
 * @param front how far aft of the nose's tip the screen starts, in blocks
 * @param length how long it is, in blocks
 * @param top how far below the hull's equator its top is, measured round the hull, in blocks
 * @param bottom how far below the hull's equator its bottom is, measured round the hull, in blocks
 * @param radius how far out from the axis it is, in blocks: the hull's radius under it and a little more
 */
public record FlankScreen(int front, int length, double top, double bottom, double radius) {
    /** How far off the cover it stands, in blocks, so it never flickers into it. */
    private static final double OFF_THE_COVER = 0.6;
    private static final FlankScreen FULL = along(ZeppelinSize.FULL, 190, 64, 3, 23);
    private static final FlankScreen HALF = along(ZeppelinSize.HALF, 60, 64, 15, 26);

    /** The screens on a zeppelin of a size. */
    public static FlankScreen of(ZeppelinSize size) {
        return switch (size) {
            case HALF -> HALF;
            case FULL -> FULL;
        };
    }

    private static FlankScreen along(ZeppelinSize size, int front, int length, double top, double bottom) {
        return new FlankScreen(front, length, top, bottom, size.radiusAt(front + length / 2.0) + OFF_THE_COVER);
    }

    /** How tall it is, round the hull, in blocks. */
    public double height() {
        return bottom - top;
    }
}
