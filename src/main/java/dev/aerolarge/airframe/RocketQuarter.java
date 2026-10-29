package dev.aerolarge.airframe;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

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
    /**
     * How far past its inner edge a quarter reaches, in blocks, from which it is roomy: a wall across it has a gap to
     * walk through, a bench along it is a seat longer, a kitchen in it has an island.
     */
    private static final int ROOMY = 4;

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

    /** Whether a quarter reaching out so far, in blocks from her axis, is roomy (see {@link #ROOMY}). */
    static boolean roomy(int reach) {
        return reach - INNER >= ROOMY;
    }

    /** The middle of a quarter's spots out to so far, in blocks from her axis, a half rounded out. */
    static int middle(int reach) {
        return (INNER + reach + 1) / 2;
    }

    /**
     * Where a wall across a quarter out to so far, in blocks from her axis, has its gap to walk through, picked at
     * random past its first block; -1, none, unless the quarter is roomy.
     */
    static int gap(int reach, RandomSource random) {
        return roomy(reach) ? INNER + 1 + random.nextInt(reach - INNER - 1) : -1;
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
