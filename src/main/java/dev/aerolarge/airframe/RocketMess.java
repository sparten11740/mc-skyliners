package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.CoffeeMachineBlock;
import dev.aerolarge.block.GalleyOvenBlock;
import dev.aerolarge.block.GalleyUnitBlock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The galley and mess: the galley's units in a curve round her back wall either side of its window, worktops and
 * stowage, an oven in the middle of each run and a coffee machine at its end, facing in; and round the room's front
 * and sides four tables, two by the side windows, an armchair either side of each.
 */
final class RocketMess extends RocketRoom {
    /** Her galley and mess's story, from the entry story up. */
    static final int STORY = 2;
    /** How far in from the skin's inside the galley's units stand, in blocks. */
    private static final double GALLEY_IN = 2.5;
    /** The galley's two runs, either side of the back window: from and to how far round from her front, in degrees. */
    private static final double[][] RUNS = {{118, 170}, {190, 242}};
    /** How far in from the skin's inside the tables stand, in blocks, and how far round from her front, in degrees. */
    private static final double TABLE_IN = 4;
    private static final double[] TABLES = {52, 92, 268, 308};

    RocketMess(RocketLayout layout) {
        super(layout, STORY);
    }

    @Override
    public void build() {
        int y = floor() + 1;
        for (double[] run : RUNS) {
            galley(y, run[0], run[1]);
        }
        double out = wall() - TABLE_IN;
        for (double round : TABLES) {
            table(across(out, round), y, along(out, round), round);
        }
    }

    /** A table, an armchair either side of it round the room, facing it. */
    private void table(int x, int y, int z, double round) {
        if (!put(x, y, z, AerolargeRegistry.LOUNGE_TABLE.get().defaultBlockState())) {
            return;
        }
        Direction on = clockwise(round);
        seat(x + on.getStepX(), y, z + on.getStepZ(), on.getOpposite(), DyeColor.BLUE);
        seat(x - on.getStepX(), y, z - on.getStepZ(), on, DyeColor.BLUE);
    }

    /** A run of galley units round the back wall between two angles, an oven in its middle, a coffee machine on it. */
    private void galley(int y, double from, double to) {
        List<BlockPos> run = run(y, wall() - GALLEY_IN, from, to);
        for (int unit = 0; unit < run.size(); unit++) {
            BlockPos at = run.get(unit);
            Direction facing = inward(roundOf(at.getX(), at.getZ()));
            if (unit == run.size() / 2) {
                put(at.getX(), y, at.getZ(), AerolargeRegistry.GALLEY_OVEN.get().defaultBlockState().setValue(GalleyOvenBlock.FACING, facing));
            } else if (unit % 2 == 1) {
                put(at.getX(), y, at.getZ(), AerolargeRegistry.GALLEY_STOWAGE.get().defaultBlockState().setValue(GalleyUnitBlock.FACING, facing));
            } else if (put(at.getX(), y, at.getZ(), AerolargeRegistry.GALLEY_WORKTOP.get().defaultBlockState().setValue(GalleyUnitBlock.FACING, facing))
                && unit == 0) {
                put(at.getX(), y + 1, at.getZ(), AerolargeRegistry.COFFEE_MACHINE.get().defaultBlockState().setValue(CoffeeMachineBlock.FACING, facing));
            }
        }
    }
}
