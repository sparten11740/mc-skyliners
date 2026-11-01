package dev.skyliners.airframe;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.BeanBagBlock;
import dev.skyliners.block.CoffeeMachineBlock;
import dev.skyliners.block.GalleyFridgeBlock;
import dev.skyliners.block.GalleyFridgeBlockEntity;
import dev.skyliners.block.GalleyOvenBlock;
import dev.skyliners.block.GalleyUnitBlock;
import dev.skyliners.block.ShelfUnitBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The galley and mess, its kitchen at her back and its dining room at her front. The galley's units curve round her
 * back wall either side of its window, worktops and stowage, an oven in the middle of each run and a coffee machine at
 * its end, facing in, and toward the window's end of her right's run the fridge, stocked; out to either side a pass of
 * worktops parts the kitchen from the dining room, bar stools before it, a shelf of jars on it and a cake or a pot of
 * tulips beside them; and where the kitchen is roomy an island stands in it. At her front right a long communal table
 * runs out from the way in, its chairs every which kind and colour, armchairs, stools and a bean bag, a few left out;
 * at her front left a smaller table with its own odd set of chairs. A halo light hangs over each table.
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
    /**
     * How long the room's corner is out from the walkway, in blocks, from which the long table runs along the corner's
     * middle; in a smaller room it runs a block nearer the walkway, where its far side's chairs still have room.
     */
    private static final int TABLE_ROOMY = 3;
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
        galley(y, RUNS[0][0], RUNS[0][1], true);
        galley(y, RUNS[1][0], RUNS[1][1], false);
        pass(RocketQuarter.BACK_RIGHT, y, pass, Blocks.CAKE.defaultBlockState(), random);
        pass(RocketQuarter.BACK_LEFT, y, pass, Blocks.POTTED_RED_TULIP.defaultBlockState(), random);
        if (RocketQuarter.roomy(corner)) {
            island(RocketQuarter.BACK_RIGHT, y, corner);
        }
        longTable(RocketQuarter.FRONT_RIGHT, y, corner, random);
        table(RocketQuarter.FRONT_LEFT, y, corner, random);
    }

    /**
     * A run of galley units round the back wall between two angles, an oven in its middle, a coffee machine on it, and
     * where asked, the fridge, stocked, in the last unit toward the window that has room for it (see
     * {@link GalleyFridgeBlock}).
     */
    private void galley(int y, double from, double to, boolean fridge) {
        List<BlockPos> run = run(y, wall() - GALLEY_IN, from, to);
        int fridgeUnit = fridge ? fridgeUnit(run, y) : -1;
        for (int unit = 0; unit < run.size(); unit++) {
            BlockPos at = run.get(unit);
            Direction facing = inward(roundOf(at.getX(), at.getZ()));
            if (unit == run.size() / 2) {
                put(at.getX(), y, at.getZ(), SkylinersRegistry.GALLEY_OVEN.get().defaultBlockState().setValue(GalleyOvenBlock.FACING, facing));
            } else if (unit == fridgeUnit) {
                fridge(at, facing);
            } else if (unit % 2 == 1) {
                put(at.getX(), y, at.getZ(), SkylinersRegistry.GALLEY_STOWAGE.get().defaultBlockState().setValue(GalleyUnitBlock.FACING, facing));
            } else if (put(at.getX(), y, at.getZ(), SkylinersRegistry.GALLEY_WORKTOP.get().defaultBlockState().setValue(GalleyUnitBlock.FACING, facing))
                && unit == 0) {
                put(at.getX(), y + 1, at.getZ(), SkylinersRegistry.COFFEE_MACHINE.get().defaultBlockState().setValue(CoffeeMachineBlock.FACING, facing));
            }
        }
    }

    /**
     * Which unit of a run the fridge takes: the last free one counting back from the window's end, the oven's apart;
     * -1 for none.
     */
    private int fridgeUnit(List<BlockPos> run, int y) {
        for (int unit = run.size() - 1; unit >= 0; unit--) {
            BlockPos at = run.get(unit);
            if (unit != run.size() / 2 && free(at.getX(), y, at.getZ())) {
                return unit;
            }
        }
        return -1;
    }

    /** The fridge at a block of the galley, facing the way given, stocked once it stands. */
    private void fridge(BlockPos at, Direction facing) {
        if (put(at.getX(), at.getY(), at.getZ(), SkylinersRegistry.GALLEY_FRIDGE.get().defaultBlockState().setValue(GalleyFridgeBlock.FACING, facing))) {
            layout.fit(at, RocketMess::stockFridge);
        }
    }

    /** The fridge stocked as the blueprint stocks it. */
    private static void stockFridge(BlockEntity blockEntity, Rotation rotation) {
        if (blockEntity instanceof GalleyFridgeBlockEntity fridge) {
            fridge.stock();
        }
    }

    /**
     * The pass out toward her side from the walkway, worktops facing the dining room with a bar stool before every
     * other one; on it a shelf of jars at its outer end and the treat given on another.
     */
    private void pass(RocketQuarter quarter, int y, int reach, BlockState treat, RandomSource random) {
        BlockState worktop = SkylinersRegistry.GALLEY_WORKTOP.get().defaultBlockState().setValue(GalleyUnitBlock.FACING, Direction.NORTH);
        List<Integer> laid = new ArrayList<>();
        for (int across = RocketQuarter.INNER; across <= reach; across++) {
            if (!put(quarter.x(across), y, quarter.z(PASS_ALONG), worktop)) {
                continue;
            }
            laid.add(across);
            if ((across - RocketQuarter.INNER) % 2 == 0) {
                put(quarter.x(across), y, 0, SkylinersRegistry.BAR_STOOL.get().defaultBlockState());
            }
        }
        if (laid.isEmpty()) {
            return;
        }
        int jars = laid.getLast();
        BlockState shelf = ShelfUnitBlock.stocked(SkylinersRegistry.SHELF_UNIT.get(), ShelfUnitBlock.Kind.JARS, Direction.NORTH);
        standing(quarter.x(jars), y + 1, quarter.z(PASS_ALONG), shelf);
        if (laid.size() > 1) {
            standing(quarter.x(laid.get(random.nextInt(laid.size() - 1))), y + 1, quarter.z(PASS_ALONG), treat);
        }
    }

    /** A kitchen island of worktops in the middle of a roomy kitchen's corner, facing the pass. */
    private void island(RocketQuarter quarter, int y, int corner) {
        int middle = RocketQuarter.middle(corner);
        BlockState worktop = SkylinersRegistry.GALLEY_WORKTOP.get().defaultBlockState().setValue(GalleyUnitBlock.FACING, Direction.NORTH);
        for (int across = middle - ISLAND / 2; across <= middle + ISLAND / 2; across++) {
            put(quarter.x(across), y, quarter.z(middle), worktop);
        }
    }

    /**
     * The long communal table, low tables end to end out from beside the way in along the quarter's middle, as far as
     * leaves a place at its far end; a chair at most places either side of it, and one at its end; a halo light over
     * it.
     */
    private void longTable(RocketQuarter quarter, int y, int corner, RandomSource random) {
        int along = RocketQuarter.middle(corner) - (corner - RocketQuarter.INNER >= TABLE_ROOMY ? 0 : 1);
        int last = TABLE_FROM;
        while (free(quarter.x(last + 1), y, quarter.z(along)) && free(quarter.x(last + 2), y, quarter.z(along))) {
            last++;
        }
        if (!free(quarter.x(TABLE_FROM), y, quarter.z(along))) {
            return;
        }
        for (int across = TABLE_FROM; across <= last; across++) {
            put(quarter.x(across), y, quarter.z(along), SkylinersRegistry.LOUNGE_TABLE.get().defaultBlockState());
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

    /**
     * A smaller table in the middle of a quarter's corner, an odd chair on each side of it that has room, a halo light
     * over it.
     */
    private void table(RocketQuarter quarter, int y, int corner, RandomSource random) {
        int middle = RocketQuarter.middle(corner);
        if (!put(quarter.x(middle), y, quarter.z(middle), SkylinersRegistry.LOUNGE_TABLE.get().defaultBlockState())) {
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

    /**
     * A chair of a kind picked at random, facing the way given: now and then a stool or a bean bag, else an armchair.
     */
    private void chair(int x, int y, int z, Direction facing, RandomSource random) {
        int kind = random.nextInt(CHAIR_KINDS);
        if (kind == 0) {
            put(x, y, z, SkylinersRegistry.BAR_STOOL.get().defaultBlockState());
        } else if (kind == 1) {
            put(x, y, z, SkylinersRegistry.BEAN_BAG.get().defaultBlockState().setValue(BeanBagBlock.FACING, facing)
                .setValue(BeanBagBlock.COLOR, CHAIRS[random.nextInt(CHAIRS.length)]));
        } else {
            seat(x, y, z, facing, CHAIRS[random.nextInt(CHAIRS.length)]);
        }
    }
}
