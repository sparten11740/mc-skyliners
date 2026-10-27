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
    HALF(1, new ZeppelinShape.CellFill(1, 13.5, 7.5, 0.55), new EngineCarShape(2.4, 7, 3, 6, 4.5F, 1 / 3.0)),
    FULL(2, ZeppelinShape.CELLS_FILL, new EngineCarShape(3.2, 9, 4, 8, 6, 1));

    /** The names of the sizes the first test builds were made at, smaller than full. */
    private static final Set<String> LEGACY_SMALL = Set.of("THIRD", "TWO_THIRDS");
    /** The hull's blocks a metre. */
    private final double scale;
    /**
     * How its gas cells fill the hull: half's fill more of it than the full ship's, or the hot air in them, inside a
     * block-thick envelope, would be too little to float the full-size control car and decks it carries.
     */
    private final ZeppelinShape.CellFill cells;
    /** Its engine cars: on a smaller hull, smaller and nearer it, or they would look oversized on her. */
    private final EngineCarShape engineCars;

    ZeppelinSize(double scale, ZeppelinShape.CellFill cells, EngineCarShape engineCars) {
        this.scale = scale;
        this.cells = cells;
        this.engineCars = engineCars;
    }

    /**
     * An engine car's pod: its radius, how many rows it reaches fore and aft of its station, how many its nose rounds
     * over, how far out from the hull's side it stands, in blocks, and how far its propeller's blades reach round the
     * hub, in blocks. Each is cut by its own share on a smaller hull, as far as leaves a room an engineer stands in. And
     * how hard its engine pushes, a share of the configured setting: on a lighter ship, as much less as she weighs,
     * so she speeds up and turns as the full-size ship does, which her pilot is tuned for, rather than hunting.
     */
    public record EngineCarShape(double radius, int halfLength, double nose, double standoff, float propellerReach, double thrust) {
    }

    ZeppelinShape.CellFill cells() {
        return cells;
    }

    public EngineCarShape engineCars() {
        return engineCars;
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
