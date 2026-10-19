package dev.aerolarge.block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocks joined into one thing by their states, each saying which of its neighbours it carries on into: the panes of
 * a tall window, the pieces of a curtain. Using one works the whole thing, so it is found from the block used.
 */
final class JoinedBlocks {
    /** Whether the block in one state carries on into its neighbour one way, in the other state. */
    @FunctionalInterface
    interface Join {
        boolean joins(BlockState from, Direction way, BlockState to);
    }

    private JoinedBlocks() {
    }

    /**
     * The block at the position and every block joined to it, nearest first: at most {@code limit} of them, so a
     * player's sprawling build is worked a piece at a time rather than all at once.
     */
    static List<BlockPos> from(BlockGetter level, BlockPos pos, int limit, Join join) {
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> next = new ArrayDeque<>();
        seen.add(pos);
        next.add(pos);
        while (!next.isEmpty() && found.size() < limit) {
            BlockPos at = next.poll();
            found.add(at);
            BlockState state = level.getBlockState(at);
            for (Direction way : Direction.values()) {
                BlockPos beside = at.relative(way);
                if (!seen.contains(beside) && join.joins(state, way, level.getBlockState(beside))) {
                    seen.add(beside);
                    next.add(beside);
                }
            }
        }
        return found;
    }
}
