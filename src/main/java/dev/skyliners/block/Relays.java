package dev.skyliners.block;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A control's link to the relays it works, held as offsets from the control so the link holds as the airliner is
 * assembled and flown: saving and loading them, and setting every relay of a group to a signal. The offsets are in the
 * world's directions, so a control turns them with itself as the airliner is set down on another heading (see
 * {@link Turning}).
 */
public final class Relays {
    private Relays() {
    }

    /**
     * Sets every relay at the offsets from origin to the signal, leaving those already there untouched, and sends the
     * clients the change.
     */
    public static void drive(Level level, BlockPos origin, List<BlockPos> offsets, int power) {
        drive(level, origin, offsets, power, true);
    }

    /**
     * Sets every relay at the offsets from origin to the signal, leaving those already there untouched; the thrusters
     * beside them follow either way, and the clients are sent the change only if synced. Each relay sent has every
     * client redraw the part of the airliner it is in, so relays nothing on a client reads are best left unsent.
     */
    public static void drive(Level level, BlockPos origin, List<BlockPos> offsets, int power, boolean synced) {
        for (BlockPos offset : offsets) {
            drive(level, origin, offset, power, synced);
        }
    }

    /**
     * Sets the one relay at the offset from origin to the signal, as
     * {@link #drive(Level, BlockPos, List, int, boolean)} sets several: for a pilot setting each of its thrusters apart
     * every tick.
     */
    public static void drive(Level level, BlockPos origin, BlockPos offset, int power, boolean synced) {
        int signal = Mth.clamp(power, 0, 15);
        BlockPos pos = origin.offset(offset);
        BlockState state = level.getBlockState(pos);
        if (isRelay(state) && state.getValue(ThrustRelayBlock.POWER) != signal) {
            level.setBlock(pos, state.setValue(ThrustRelayBlock.POWER, signal), synced ? Block.UPDATE_ALL : Block.UPDATE_NEIGHBORS);
        }
    }

    /**
     * Sets a group of relays to signals adding up to a total, as nearly as they can: each gets an even share and what
     * is left over goes one more to the first ones, so the first is given a signal whenever any is. A group of
     * thrusters is so set far more finely than by one signal for all, a step of the total moving one relay alone.
     */
    public static void spread(Level level, BlockPos origin, List<BlockPos> offsets, int total, boolean synced) {
        int count = offsets.size();
        int each = total / Math.max(1, count);
        for (int i = 0; i < count; i++) {
            drive(level, origin, offsets.get(i), each + (i < total - each * count ? 1 : 0), synced);
        }
    }

    /**
     * How many steps the order of a group's leftover steps is looked through for a relay (see {@link #spreadRank}):
     * enough to reach every one of a group of up to 64, as many as a long's bits keep track of.
     */
    private static final int SPREAD_STEPS = 64;

    /**
     * Sets a group of relays to signals adding up to a total, as nearly as they can, what is left over of an even share
     * going out along them in a fixed order that keeps it spread (see {@link #spreadRank}) rather than to the first
     * ones: an airliner's engine at a low thrust, its few steps on its first relays alone, pushed from one side of its
     * nacelle and tipped her as she landed and rolled out. A step of the total still moves one relay alone, and the
     * first is still given a signal whenever any is.
     */
    public static void spreadEvenly(Level level, BlockPos origin, List<BlockPos> offsets, int total, boolean synced) {
        int count = offsets.size();
        int each = total / Math.max(1, count);
        int over = total - each * count;
        for (int i = 0; i < count; i++) {
            drive(level, origin, offsets.get(i), each + (spreadRank(i, count) < over ? 1 : 0), synced);
        }
    }

    /**
     * Where a relay comes in the order a group's leftover steps go out in: halving the gaps along the group, from its
     * first (0, then the middle, then the quarters...), so however many there are, they lie spread along it.
     */
    private static int spreadRank(int index, int count) {
        long taken = 0;
        int rank = 0;
        for (int step = 0; rank < count && step < SPREAD_STEPS; step++) {
            int at = (int) ((Integer.reverse(step) & 0xFFFFFFFFL) * count >>> 32);
            if ((taken & 1L << at) != 0) {
                continue;
            }
            if (at == index) {
                return rank;
            }
            taken |= 1L << at;
            rank++;
        }
        return count;
    }

    /** Whether the block is a relay, what a control's links lead to. */
    public static boolean isRelay(BlockState state) {
        return state.getBlock() instanceof ThrustRelayBlock;
    }

    public static ListTag write(List<BlockPos> offsets) {
        ListTag list = new ListTag();
        offsets.forEach(offset -> list.add(NbtUtils.writeBlockPos(offset)));
        return list;
    }

    public static List<BlockPos> read(ListTag list) {
        List<BlockPos> offsets = new ArrayList<>();
        for (Tag entry : list) {
            if (entry instanceof IntArrayTag array && array.getAsIntArray().length == 3) {
                int[] xyz = array.getAsIntArray();
                offsets.add(new BlockPos(xyz[0], xyz[1], xyz[2]));
            }
        }
        return List.copyOf(offsets);
    }
}
