package dev.aerolarge.airframe;

/**
 * How big a zeppelin's hull is built: the Hindenburg's 245 m at so many blocks a metre, a third, two thirds or the
 * whole of the airliner's default scale. Only the hull grows and shrinks, its cover, rings, gas cells and fins; the
 * control car, the decks, the engine cars and everything aboard stay the size of a player at every size, the hull
 * raised over the car where a smaller one would sink onto it (see {@link ZeppelinLayout}). Each ship keeps its own,
 * from its blueprint.
 */
public enum ZeppelinSize {
    THIRD(2.0 / 3),
    TWO_THIRDS(4.0 / 3),
    FULL(2);

    /** The hull's blocks a metre. */
    private final double scale;

    ZeppelinSize(double scale) {
        this.scale = scale;
    }

    public double scale() {
        return scale;
    }

    /** The hull's length, nose to stern, in blocks. */
    public double length() {
        return ZeppelinShape.LENGTH * scale;
    }

    /** The row the hull's axis runs along, over the zeppelin's lowest blocks. */
    public int axis() {
        return (int) Math.round(ZeppelinShape.AXIS_HEIGHT * scale);
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

    /** The size of this name; full size for none, or one no size has, as a ship saved before sizes was. */
    public static ZeppelinSize named(String name) {
        for (ZeppelinSize size : values()) {
            if (size.name().equals(name)) {
                return size;
            }
        }
        return FULL;
    }
}
