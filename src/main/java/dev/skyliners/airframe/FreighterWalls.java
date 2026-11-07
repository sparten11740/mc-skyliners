package dev.skyliners.airframe;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.LedPanelBlockEntity;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Finding places on the freighter's padded walls for what hangs on them, as her rooms' LED panels and her crossbow
 * rack: an open block in a room, a wedge round her axis, so far out from it, with her padded wall behind it on one
 * side and the room open before it on the other, nearest to a point in her layout's blocks.
 */
final class FreighterWalls {
    private FreighterWalls() {
    }

    /**
     * The open block in a row nearest to a point, in the room given and from so far out from her axis to so far, in
     * blocks, with her padded wall behind it and so many blocks open before it, to stand in and see it from; null for
     * none.
     */
    @Nullable
    static BlockPos nearest(HullLayout layout, int y, LedPanelBlockEntity.Wedge room, double from, double to, double towardX, double towardZ,
        int roomBefore) {
        return nearest(layout, y, room, from, to, towardX, towardZ, roomBefore, at -> true);
    }

    /** The same, among only the blocks that also pass the test given, as for room over them or out of a doorway. */
    @Nullable
    static BlockPos nearest(HullLayout layout, int y, LedPanelBlockEntity.Wedge room, double from, double to, double towardX, double towardZ,
        int roomBefore, Predicate<BlockPos> also) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int far = (int) Math.ceil(to);
        int across = 2 * far + 1;
        for (int block = 0; block < across * across; block++) {
            BlockPos at = new BlockPos(block / across - far, y, block % across - far);
            double distance = Math.hypot(at.getX() + 0.5 - towardX, at.getZ() + 0.5 - towardZ);
            if (distance < bestDistance && fits(layout, room, at, from, to) && openBefore(layout, at, roomBefore) && also.test(at)) {
                best = at;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Whether something fits at a block: in the room, so far out, open, against her wall and open before it. */
    private static boolean fits(HullLayout layout, LedPanelBlockEntity.Wedge room, BlockPos at, double from, double to) {
        double out = Math.hypot(at.getX(), at.getZ());
        boolean near = out >= from && out <= to && out >= FreighterLayout.CORRIDOR && room.contains(at);
        return near && layout.isOpen(at.getX(), at.getY(), at.getZ()) && wallBehind(layout, at) != null;
    }

    /** Whether so many blocks before a block are open, on the side away from her wall behind it. */
    private static boolean openBefore(HullLayout layout, BlockPos at, int blocks) {
        Direction behind = wallBehind(layout, at);
        if (behind == null) {
            return false;
        }
        for (int out = 1; out <= blocks; out++) {
            BlockPos before = at.relative(behind.getOpposite(), out);
            if (!layout.isOpen(before.getX(), before.getY(), before.getZ())) {
                return false;
            }
        }
        return true;
    }

    /** The side of a block her padded wall stands on, with the block on the side opposite open; null for none. */
    @Nullable
    static Direction wallBehind(HullLayout layout, BlockPos at) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos before = at.relative(side.getOpposite());
            BlockPos behind = at.relative(side);
            boolean wall = layout.stateAt(behind.getX(), behind.getY(), behind.getZ()).is(SkylinersRegistry.PADDED_BULKHEAD.get());
            if (wall && layout.isOpen(before.getX(), before.getY(), before.getZ())) {
                return side;
            }
        }
        return null;
    }
}
