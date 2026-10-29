package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A spot on one of her rooms' walls to fix something to her skin, and the way what is fixed there faces, away from the
 * skin: the block out along a way round from her front, at a row, that stands open at the room's edge, with her cover
 * beside it on a side facing out from her axis and room over it for whatever is fixed there. Where her wall steps, a
 * way round can pass from open air into her cover corner to corner, so the outermost of the blocks it passes through,
 * and of those beside them further out, is taken. Her wall washers are fixed so (see {@link RocketWallLights}), and the
 * club's neon signs.
 */
record SkinMount(BlockPos at, Direction facing) {
    /** How far in from her body's radius the search for the wall begins, and how far past it it ends, in blocks. */
    private static final double SEARCH_IN = 3;
    private static final double SEARCH_OUT = 1;
    /** How finely the search steps out toward the wall, in blocks. */
    private static final double STEP = 0.5;

    /**
     * The spot on the wall out along a way round from her front, in degrees, at a row, with so many rows open from it
     * up; null where there is none.
     */
    @Nullable
    static SkinMount find(RocketLayout layout, double round, int y, int rows) {
        SkinMount found = null;
        for (double out = layout.radius() - SEARCH_IN; out <= layout.radius() + SEARCH_OUT; out += STEP) {
            BlockPos on = new BlockPos(RocketRoom.across(out, round), y, RocketRoom.along(out, round));
            for (BlockPos at : List.of(on, on.relative(outward(on, true)), on.relative(outward(on, false)))) {
                SkinMount mount = at(layout, at, rows);
                if (mount != null && (found == null || farther(at, found.at()))) {
                    found = mount;
                }
            }
        }
        return found;
    }

    /** The way out from her axis along one of the two axes, across (x) or along (z), at a block. */
    private static Direction outward(BlockPos at, boolean across) {
        if (across) {
            return at.getX() < 0 ? Direction.WEST : Direction.EAST;
        }
        return at.getZ() < 0 ? Direction.NORTH : Direction.SOUTH;
    }

    /** The spot at a block, open with so many rows from it up and her cover beside it; null where it cannot be. */
    @Nullable
    private static SkinMount at(RocketLayout layout, BlockPos at, int rows) {
        Direction back = skinBeside(layout, at.getX(), at.getY(), at.getZ());
        if (back == null) {
            return null;
        }
        for (int up = 0; up < rows; up++) {
            if (!layout.isOpen(at.getX(), at.getY() + up, at.getZ())) {
                return null;
            }
        }
        return new SkinMount(at, back.getOpposite());
    }

    /** Whether a block lies further out from her axis than another. */
    private static boolean farther(BlockPos at, BlockPos than) {
        return at.getX() * at.getX() + at.getZ() * at.getZ() > than.getX() * than.getX() + than.getZ() * than.getZ();
    }

    /** The side of a block, facing out from her axis, that her cover stands on; null for none. */
    @Nullable
    private static Direction skinBeside(RocketLayout layout, int x, int y, int z) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            boolean outward = side.getStepX() * x + side.getStepZ() * z > 0;
            if (outward && layout.stateAt(x + side.getStepX(), y, z + side.getStepZ()).is(AerolargeRegistry.ZEPPELIN_COVER.get())) {
                return side;
            }
        }
        return null;
    }
}
