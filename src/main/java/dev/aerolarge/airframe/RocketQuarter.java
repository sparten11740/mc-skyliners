package dev.aerolarge.airframe;

import net.minecraft.core.Direction;

/**
 * A quarter of one of the rocket's round rooms, between two of the four ways out from her shaft: her front right, back
 * right, back left or front left. A room is cut into sections by its quarters, each furnished as a corner of its own,
 * and a spot in a quarter is given by how far out it is toward her side (across) and toward her front or back (along),
 * from her axis, so one plan of a corner is laid in any of the four, mirrored.
 */
enum RocketQuarter {
    FRONT_RIGHT(1, -1),
    BACK_RIGHT(1, 1),
    BACK_LEFT(-1, 1),
    FRONT_LEFT(-1, -1);

    /**
     * How far out from her axis a quarter's spots begin, either way, in blocks: just clear of the walkway round her
     * shaft.
     */
    static final int INNER = RocketLayout.SHAFT + 2;

    private final int right;
    private final int back;

    RocketQuarter(int right, int back) {
        this.right = right;
        this.back = back;
    }

    /** The block across her of a spot so far out toward the quarter's side. */
    int x(int across) {
        return right * across;
    }

    /** The block along her of a spot so far out toward the quarter's front or back. */
    int z(int along) {
        return back * along;
    }

    /** The way out toward the quarter's side. */
    Direction side() {
        return right > 0 ? Direction.EAST : Direction.WEST;
    }

    /** The way out toward the quarter's end of her: her front or her back. */
    Direction end() {
        return back > 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
