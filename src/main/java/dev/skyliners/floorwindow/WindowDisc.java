package dev.skyliners.floorwindow;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The disc of a round floor window (see {@link FloorWindowBlock}): how many blocks across it is, from its full nine
 * down to two for a narrow ledge, how far its glass reaches at that size, and which blocks it covers round its middle
 * in the face it was set into, a floor's, a wall's or a ceiling's. An odd size has its middle in the middle of a block,
 * an even one on the corner where four blocks meet, so each block of a window keeps the way from its own middle to the
 * window's in half blocks (see {@link FloorWindowBlockEntity}), and a window of any size finds its blocks from any one
 * of them.
 */
public final class WindowDisc {
    /** The largest a window comes, in blocks across: as the freighter's beach has it, and every window laid before sizes. */
    public static final int FULL = 9;
    /** The smallest a window comes, in blocks across: a two by two, for a ledge two blocks wide. */
    public static final int SMALLEST = 2;
    /** How far in from the middle of each side of a window's square its glass stops, in blocks: room for its ring. */
    private static final double MARGIN = 0.3;

    private WindowDisc() {
    }

    /** How far out from a window's middle its glass reaches, to the inside of its ring, in blocks. */
    public static double radius(int size) {
        return size / 2.0 - MARGIN;
    }

    /** A size as saved, kept within the sizes a window comes in. */
    public static int sized(int size) {
        return Math.clamp(size, SMALLEST, FULL);
    }

    /**
     * The two ways across the face of a window looking out the way given: east and south in a floor or a ceiling, and
     * along a wall and up it in a wall.
     */
    public static Direction[] across(Direction out) {
        return out.getAxis().isVertical() ? new Direction[] {Direction.EAST, Direction.SOUTH} : new Direction[] {out.getClockWise(), Direction.UP};
    }

    /** How many half blocks one way an offset in half blocks runs. */
    public static int halfBlocks(BlockPos offset, Direction way) {
        return offset.getX() * way.getStepX() + offset.getY() * way.getStepY() + offset.getZ() * way.getStepZ();
    }

    /**
     * Every block of a window so many blocks across, looking out the way given, whose middle lies so many half blocks
     * from the middle of the block given: each whose square reaches inside its glass.
     */
    public static List<BlockPos> blocks(BlockPos from, BlockPos toMiddle, int size, Direction out) {
        Direction[] across = across(out);
        double middleU = halfBlocks(toMiddle, across[0]) / 2.0;
        double middleV = halfBlocks(toMiddle, across[1]) / 2.0;
        double radius = radius(size);
        int reach = (int) Math.ceil(radius + 0.5);
        List<BlockPos> blocks = new ArrayList<>();
        for (int u = (int) Math.floor(middleU) - reach; u <= (int) Math.ceil(middleU) + reach; u++) {
            addColumn(blocks, from.relative(across[0], u), across[1], u - middleU, middleV, radius);
        }
        return List.copyOf(blocks);
    }

    /**
     * The blocks of a window in one column of its square across it, the column so far along from its middle: each whose
     * square reaches inside its glass.
     */
    private static void addColumn(List<BlockPos> blocks, BlockPos column, Direction up, double along, double middleV, double radius) {
        int reach = (int) Math.ceil(radius + 0.5);
        double nearAlong = Math.max(0, Math.abs(along) - 0.5);
        for (int v = (int) Math.floor(middleV) - reach; v <= (int) Math.ceil(middleV) + reach; v++) {
            double nearUp = Math.max(0, Math.abs(v - middleV) - 0.5);
            if (Math.hypot(nearAlong, nearUp) < radius) {
                blocks.add(column.relative(up, v));
            }
        }
    }
}
