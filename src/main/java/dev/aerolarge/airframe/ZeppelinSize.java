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
