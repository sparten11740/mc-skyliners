package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.BeanBagBlock;
import dev.aerolarge.block.CoffeeMachineBlock;
import dev.aerolarge.block.GalleyOvenBlock;
import dev.aerolarge.block.GalleyUnitBlock;
import dev.aerolarge.block.ShelfUnitBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The galley and mess, its kitchen at her back and its dining room at her front. The galley's units curve round her
 * back wall either side of its window, worktops and stowage, an oven in the middle of each run and a coffee machine at
 * its end, facing in; out to either side a pass of worktops parts the kitchen from the dining room, bar stools before
 * it, a shelf of jars on it and a cake or a pot of tulips beside them; and where the kitchen is roomy an island stands
 * in it. At her front right a long communal table runs out from the way in, its chairs every which kind and colour,
 * armchairs, stools and a bean bag, a few left out; at her front left a smaller table with its own odd set of chairs.
 * A halo light hangs over each table.
 */
final class RocketMess extends RocketRoom {
    /** Her galley and mess's story, from the entry story up. */
    static final int STORY = 2;
    /** How far in from the skin's inside the galley's units stand, in blocks. */
    private static final double GALLEY_IN = 2.5;
    /** The galley's two runs, either side of the back window: from and to how far round from her front, in degrees. */
    private static final double[][] RUNS = {{118, 170}, {190, 242}};
    /** How far along toward her back the pass stands, in blocks, its stools in front of it toward her front. */
    private static final int PASS_ALONG = 1;
    /** How far out toward her side the long table begins, in blocks: beside the way in from the lift. */
    private static final int TABLE_FROM = 2;
    /** How long the room's corner is out from the walkway, in blocks, from which the kitchen has an island. */
    private static final int ROOMY = 4;
    /** How many worktops long the island is. */
    private static final int ISLAND = 3;
    /** One in how many places at the long table has no chair. */
    private static final int EMPTY_PLACES = 5;
    /** The armchairs' colours, a chair's picked at random. */
    private static final DyeColor[] CHAIRS = {DyeColor.BLUE, DyeColor.RED, DyeColor.ORANGE, DyeColor.GREEN, DyeColor.LIGHT_BLUE, DyeColor.YELLOW};
    /** How a chair is picked: one in so many a stool, one in so many a bean bag, the rest armchairs. */
    private static final int CHAIR_KINDS = 6;

    RocketMess(RocketLayout layout) {
        super(layout, STORY);
    }

    @Override
    public void build() {
        int y = floor() + 1;
        RandomSource random = scatter();
        int pass = reach(PASS_ALONG);
        int corner = corner();
        for (double[] run : RUNS) {
            galley(y, run[0], run[1]);
        }
        pass(RocketQuarter.BACK_RIGHT, y, pass, Blocks.CAKE.defaultBlockState(), random);
        pass(RocketQuarter.BACK_LEFT, y, pass, Blocks.POTTED_RED_TULIP.defaultBlockState(), random);
        if (corner - RocketQuarter.INNER >= ROOMY) {
            island(RocketQuarter.BACK_RIGHT, y, corner);
        }
        longTable(RocketQuarter.FRONT_RIGHT, y, corner, random);
        table(RocketQuarter.FRONT_LEFT, y, corner, random);
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

    /**
     * The pass out toward her side from the walkway, worktops facing the dining room with a bar stool before every other
     * one; on it a shelf of jars at its outer end and the treat given on another.
     */
    private void pass(RocketQuarter quarter, int y, int reach, BlockState treat, RandomSource random) {
        BlockState worktop = AerolargeRegistry.GALLEY_WORKTOP.get().defaultBlockState().setValue(GalleyUnitBlock.FACING, Direction.NORTH);
        List<Integer> laid = new ArrayList<>();
        for (int across = RocketQuarter.INNER; across <= reach; across++) {
            if (!put(quarter.x(across), y, quarter.z(PASS_ALONG), worktop)) {
                continue;
            }
            laid.add(across);
            if ((across - RocketQuarter.INNER) % 2 == 0) {
                put(quarter.x(across), y, 0, AerolargeRegistry.BAR_STOOL.get().defaultBlockState());
            }
        }
        if (laid.isEmpty()) {
            return;
        }
        int jars = laid.getLast();
        standing(quarter.x(jars), y + 1, quarter.z(PASS_ALONG), ShelfUnitBlock.stocked(AerolargeRegistry.SHELF_UNIT.get(), ShelfUnitBlock.Kind.JARS, Direction.NORTH));
        if (laid.size() > 1) {
            standing(quarter.x(laid.get(random.nextInt(laid.size() - 1))), y + 1, quarter.z(PASS_ALONG), treat);
        }
    }

    /** A kitchen island of worktops in the middle of a roomy kitchen's corner, facing the pass. */
    private void island(RocketQuarter quarter, int y, int corner) {
        int middle = (RocketQuarter.INNER + corner + 1) / 2;
        BlockState worktop = AerolargeRegistry.GALLEY_WORKTOP.get().defaultBlockState().setValue(GalleyUnitBlock.FACING, Direction.NORTH);
        for (int across = middle - ISLAND / 2; across <= middle + ISLAND / 2; across++) {
            put(quarter.x(across), y, quarter.z(middle), worktop);
        }
    }

    /**
     * The long communal table, low tables end to end out from beside the way in along the quarter's middle, as far as
     * leaves a place at its far end; a chair at most places either side of it, and one at its end; a lantern over it.
     */
    private void longTable(RocketQuarter quarter, int y, int corner, RandomSource random) {
        int along = (RocketQuarter.INNER + corner + 1) / 2 - (corner - RocketQuarter.INNER >= ROOMY - 1 ? 0 : 1);
        int last = TABLE_FROM;
        while (free(quarter.x(last + 1), y, quarter.z(along)) && free(quarter.x(last + 2), y, quarter.z(along))) {
            last++;
        }
        if (!free(quarter.x(TABLE_FROM), y, quarter.z(along))) {
            return;
        }
        for (int across = TABLE_FROM; across <= last; across++) {
            put(quarter.x(across), y, quarter.z(along), AerolargeRegistry.LOUNGE_TABLE.get().defaultBlockState());
        }
        for (int across = TABLE_FROM; across <= last; across++) {
            places(quarter, across, along, random);
        }
        chair(quarter.x(last + 1), y, quarter.z(along), quarter.side().getOpposite(), random);
        pendant(quarter.x((TABLE_FROM + last) / 2), quarter.z(along), WARM_LIGHT);
    }

    /** The places either side of the long table at a block of it, each with a chair unless it is one left without. */
    private void places(RocketQuarter quarter, int across, int along, RandomSource random) {
        int y = floor() + 1;
        if (random.nextInt(EMPTY_PLACES) > 0) {
            chair(quarter.x(across), y, quarter.z(along + 1), quarter.end().getOpposite(), random);
        }
        if (random.nextInt(EMPTY_PLACES) > 0) {
            chair(quarter.x(across), y, quarter.z(along - 1), quarter.end(), random);
        }
    }

    /** A smaller table in the middle of a quarter's corner, an odd chair on each side of it that has room, a lantern over it. */
    private void table(RocketQuarter quarter, int y, int corner, RandomSource random) {
        int middle = (RocketQuarter.INNER + corner + 1) / 2;
        if (!put(quarter.x(middle), y, quarter.z(middle), AerolargeRegistry.LOUNGE_TABLE.get().defaultBlockState())) {
            return;
        }
        Direction side = quarter.side();
        Direction end = quarter.end();
        chair(quarter.x(middle - 1), y, quarter.z(middle), side, random);
        chair(quarter.x(middle), y, quarter.z(middle - 1), end, random);
        chair(quarter.x(middle + 1), y, quarter.z(middle), side.getOpposite(), random);
        chair(quarter.x(middle), y, quarter.z(middle + 1), end.getOpposite(), random);
        pendant(quarter.x(middle), quarter.z(middle), WARM_LIGHT);
    }

    /** A chair of a kind picked at random, facing the way given: now and then a stool or a bean bag, else an armchair. */
    private void chair(int x, int y, int z, Direction facing, RandomSource random) {
        int kind = random.nextInt(CHAIR_KINDS);
        if (kind == 0) {
            put(x, y, z, AerolargeRegistry.BAR_STOOL.get().defaultBlockState());
        } else if (kind == 1) {
            put(x, y, z, AerolargeRegistry.BEAN_BAG.get().defaultBlockState().setValue(BeanBagBlock.FACING, facing)
                .setValue(BeanBagBlock.COLOR, CHAIRS[random.nextInt(CHAIRS.length)]));
        } else {
            seat(x, y, z, facing, CHAIRS[random.nextInt(CHAIRS.length)]);
        }
    }
}
