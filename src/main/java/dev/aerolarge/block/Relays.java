package dev.aerolarge.block;

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

    /** Sets every relay at the offsets from origin to the signal, leaving those already there untouched. */
    public static void drive(Level level, BlockPos origin, List<BlockPos> offsets, int power) {
        int signal = Mth.clamp(power, 0, 15);
        for (BlockPos offset : offsets) {
            BlockPos pos = origin.offset(offset);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ThrustRelayBlock && state.getValue(ThrustRelayBlock.POWER) != signal) {
                level.setBlock(pos, state.setValue(ThrustRelayBlock.POWER, signal), Block.UPDATE_ALL);
            }
        }
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
