package dev.aerolarge.airframe;

import java.util.Set;

/**
 * How big a zeppelin's hull is built: the Hindenburg's 245 m at so many blocks a metre, half or the whole of the
 * airliner's default scale. Only the hull grows and shrinks, its cover, rings, gas cells and fins; the control car,
 * the decks and everything aboard stay the size of a player at every size, the hull raised over the car where a
 * smaller one would sink onto it, and what is in between shrinks by its own share, enough to look right on a smaller
 * hull and no more than leaves it usable (see {@link ZeppelinLayout}). Each ship keeps its own, from its blueprint.
 */
public enum ZeppelinSize {
    HALF(1, new ZeppelinShape.CellFill(1, 13.5, 7.5, 0.55)),
    FULL(2, ZeppelinShape.CELLS_FILL);

    /** The names of the sizes the first test builds were made at, smaller than full. */
    private static final Set<String> LEGACY_SMALL = Set.of("THIRD", "TWO_THIRDS");
    /** The hull's blocks a metre. */
    private final double scale;
    /**
     * How its gas cells fill the hull: half's fill more of it than the full ship's, or the hot air in them, inside a
     * block-thick envelope, would be too little to float the full-size control car and decks it carries.
     */
    private final ZeppelinShape.CellFill cells;

    ZeppelinSize(double scale, ZeppelinShape.CellFill cells) {
        this.scale = scale;
        this.cells = cells;
    }

    ZeppelinShape.CellFill cells() {
        return cells;
    }

    public double scale() {
        return scale;
    }

    /** The hull's length, nose to stern, in blocks. */
    public double length() {
        return ZeppelinShape.LENGTH * scale;
    }

    /**
     * The row the hull's axis runs along, over the zeppelin's lowest blocks: as high as the shape has it, or higher,
     * where the hull would sink onto the full-size control car under it, so its belly behind the car stands clear of
     * the car's roof.
     */
    public int axis() {
        int carBack = (int) Math.round(ZeppelinShape.CAR_FRONT * scale) + ZeppelinLayout.CAR_ROWS;
        double clear = ZeppelinLayout.CAR_ROOF + 1 + ZeppelinShape.radius(carBack / scale) * scale;
        return Math.max((int) Math.round(ZeppelinShape.AXIS_HEIGHT * scale), (int) Math.ceil(clear));
    }

    /** The hull's radius so many blocks aft of the nose's tip, in blocks; 0 past either end. */
    public double radiusAt(double along) {
        return ZeppelinShape.radius(along / scale) * scale;
    }

    /** How far aft of the nose's tip one of the main rings stands, the bow's first, in blocks. */
    public double ringAt(int ring) {
        return ZeppelinShape.ringAt(ring) * scale;
    }

    /** How many main rings the hull has, the bow's to the stern's. */
    public static int rings() {
        return ZeppelinShape.CELLS + 1;
    }

    /**
     * The size of this name: full size for none, as a ship saved before sizes was; half for the third and two thirds
     * the first test builds were made at, the nearest left; full for any other.
     */
    public static ZeppelinSize named(String name) {
        for (ZeppelinSize size : values()) {
            if (size.name().equals(name)) {
                return size;
            }
        }
        return LEGACY_SMALL.contains(name) ? HALF : FULL;
    }
}
