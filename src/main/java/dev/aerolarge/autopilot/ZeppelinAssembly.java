package dev.aerolarge.autopilot;

import dev.aerolarge.Aboard;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.airframe.AirframePalette;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The blocks of a zeppelin standing in the world, for Sable to assemble into one flying body (see
 * {@link dev.aerolarge.compat.SableFlight#assemble}): everything touching its control head, face to face, through
 * blocks of the zeppelin's own, within the box round the blocks its blueprint laid. A block is the zeppelin's if it
 * is this mod's or one it is built with from other mods (its burners, envelopes and thrusters), or anything at all
 * inside its hull, what was built aboard. Not the ground it rests on, nor a runway's lights, a parked airliner's
 * wingtip or someone's balloon standing in the box without touching it: Sable would fly them off on their own.
 */
public final class ZeppelinAssembly {
    private ZeppelinAssembly() {
    }

    public static List<BlockPos> blocks(ServerLevel level, BlockPos head, BoundingBox frame) {
        Set<Block> theirs = AirframePalette.balloon().blocks();
        List<BlockPos> blocks = new ArrayList<>();
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue next = new LongArrayFIFOQueue();
        seen.add(head.asLong());
        next.enqueue(head.asLong());
        while (!next.isEmpty()) {
            BlockPos pos = BlockPos.of(next.dequeueLong());
            blocks.add(pos);
            for (Direction side : Direction.values()) {
                spread(level, pos.relative(side), frame, theirs, seen, next);
            }
        }
        return blocks;
    }

    /** Takes a block touching one already taken next, if it is the zeppelin's and was not looked at before. */
    private static void spread(ServerLevel level, BlockPos beside, BoundingBox frame, Set<Block> theirs, LongOpenHashSet seen, LongArrayFIFOQueue next) {
        if (frame.isInside(beside) && seen.add(beside.asLong()) && aboard(level, beside, theirs)) {
            next.enqueue(beside.asLong());
        }
    }

    /**
     * The zeppelin's own blocks in its frame that assembling it from its control head would leave behind, touching the
     * rest only along an edge or at a corner, or not at all: for play-testing a layout, which should leave none.
     */
    public static List<BlockPos> leftBehind(ServerLevel level, BlockPos head, BoundingBox frame) {
        Set<Block> theirs = AirframePalette.balloon().blocks();
        LongOpenHashSet assembled = new LongOpenHashSet();
        for (BlockPos pos : blocks(level, head, frame)) {
            assembled.add(pos.asLong());
        }
        List<BlockPos> left = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(frame.minX(), frame.minY(), frame.minZ(), frame.maxX(), frame.maxY(), frame.maxZ())) {
            if (!assembled.contains(pos.asLong()) && ours(level.getBlockState(pos), theirs)) {
                left.add(pos.immutable());
            }
        }
        return left;
    }

    /**
     * The blocks in a zeppelin's frame that its assembly would leave behind pressed against it, face to face: neither
     * its own nor inside its hull, such as vanilla blocks laid on its outside. Left in the world where they stand, they
     * hold the zeppelin as it flies off; for play-testing a layout, which should leave none.
     */
    public static List<BlockPos> stuckTo(ServerLevel level, BlockPos head, BoundingBox frame) {
        LongOpenHashSet assembled = new LongOpenHashSet();
        List<BlockPos> blocks = blocks(level, head, frame);
        blocks.forEach(pos -> assembled.add(pos.asLong()));
        LongOpenHashSet stuck = new LongOpenHashSet();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : blocks) {
            stuckBeside(level, pos, frame, assembled, stuck, found);
        }
        return found;
    }

    /** The blocks beside one of the zeppelin's, in its frame, that it would leave behind, each once. */
    private static void stuckBeside(ServerLevel level, BlockPos pos, BoundingBox frame, LongOpenHashSet assembled, LongOpenHashSet stuck, List<BlockPos> found) {
        for (Direction side : Direction.values()) {
            BlockPos beside = pos.relative(side);
            if (frame.isInside(beside) && !assembled.contains(beside.asLong()) && !level.getBlockState(beside).isAir() && stuck.add(beside.asLong())) {
                found.add(beside);
            }
        }
    }

    private static boolean aboard(ServerLevel level, BlockPos pos, Set<Block> theirs) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        return ours(state, theirs) || Aboard.insideHull(level, pos);
    }

    /** Whether a block is one a zeppelin is built of: this mod's, or one of the other mods' it is built with. */
    private static boolean ours(BlockState state, Set<Block> theirs) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(Aerolarge.MOD_ID) || theirs.contains(state.getBlock());
    }
}
