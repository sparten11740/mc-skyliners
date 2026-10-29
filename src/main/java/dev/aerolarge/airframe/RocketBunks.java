package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.BeanBagBlock;
import dev.aerolarge.block.BunkBlock;
import dev.aerolarge.block.ShelfUnitBlock;
import dev.aerolarge.block.SuitcaseBlock;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * The bunk room: bunk beds two high against her skin between the windows, a pair at each of the four diagonals, their
 * reading lights on, their blankets each in a colour of their own and a blue halo light hanging over each pair, as dim as
 * a night light; in front of each bunk a rug of its sleeper's own colour, and about each pair the sleepers' things, a
 * suitcase and now and then a bean bag; crew lockers either side of the way out and of the back window, a space helmet
 * in each. Out to either side a wall of bookshelves, one high here and two there, parts the front pairs from the back
 * ones; and behind the shaft a reading nook looks at the solar system poster on it: an armchair between two low
 * bookshelves on a rug, a blue halo light over it.
 */
final class RocketBunks extends RocketRoom {
    /** Her bunk room's story, from the entry story up. */
    static final int STORY = 3;
    /** How far in from the skin's inside the bunks and the lockers stand, in blocks. */
    private static final double ALONG_SKIN = 2.5;
    /** How far round either side of each diagonal the bunks of its pair are, in degrees. */
    private static final double PAIR_APART = 11;
    /** How far round from her front the lockers are, in degrees: either side of the way out and of the back window. */
    private static final double[] LOCKERS = {22, 338, 158, 202};
    /** The blankets' colours, taken in turn, and the rugs', a bunk's picked at random. */
    private static final DyeColor[] BLANKETS = {DyeColor.BLUE, DyeColor.PURPLE, DyeColor.CYAN, DyeColor.LIGHT_BLUE};
    private static final BlockState[] RUGS = {Blocks.PURPLE_CARPET.defaultBlockState(), Blocks.BLUE_CARPET.defaultBlockState(),
        Blocks.GRAY_CARPET.defaultBlockState(), Blocks.MAGENTA_CARPET.defaultBlockState(), Blocks.LIGHT_BLUE_CARPET.defaultBlockState()};
    /** The suitcases' and the bean bags' colours. */
    private static final DyeColor[] CASES = {DyeColor.RED, DyeColor.BLUE, DyeColor.GREEN, DyeColor.PURPLE};
    private static final DyeColor[] BEAN_BAGS = {DyeColor.PINK, DyeColor.LIME, DyeColor.ORANGE};
    /** One in how many bunk pairs has a bean bag by it, and one in how many bookshelves is one high instead of two. */
    private static final int BEAN_BAG_EVERY = 2;
    private static final int LOW_SHELF_EVERY = 3;
    /** How many bookshelves high the wall between the pairs stands at most. */
    private static final int SHELVES_HIGH = 2;

    private int blankets;

    RocketBunks(RocketLayout layout) {
        super(layout, STORY);
    }

    @Override
    public void build() {
        int y = floor() + 1;
        RandomSource random = scatter();
        double out = wall() - ALONG_SKIN;
        int arm = reach(0);
        int corner = corner();
        for (double diagonal = 45; diagonal < 360; diagonal += 90) {
            for (int side = -1; side <= 1; side += 2) {
                double round = diagonal + side * PAIR_APART;
                bunkBed(across(out, round), y, along(out, round), inward(round));
            }
            pendant(across(out, diagonal), along(out, diagonal), NIGHT_LIGHT);
        }
        for (double diagonal = 45; diagonal < 360; diagonal += 90) {
            for (int side = -1; side <= 1; side += 2) {
                double round = diagonal + side * PAIR_APART;
                bunkRug(across(out, round), y, along(out, round), inward(round), RUGS[random.nextInt(RUGS.length)]);
            }
        }
        for (double round : LOCKERS) {
            crewLocker(across(out, round), y, along(out, round), inward(round));
        }
        for (RocketQuarter quarter : RocketQuarter.values()) {
            belongings(quarter, y, corner, random);
        }
        for (RocketQuarter quarter : new RocketQuarter[]{RocketQuarter.FRONT_RIGHT, RocketQuarter.FRONT_LEFT}) {
            bookshelves(quarter, y, arm, random);
        }
        nook(y);
    }

    /** A bunk bed facing the way given, its head at the block and its foot beside it, and a second bunk over it. */
    private void bunkBed(int x, int y, int z, Direction facing) {
        Direction foot = BunkBlock.footward(facing);
        int footX = x + foot.getStepX();
        int footZ = z + foot.getStepZ();
        for (int bunk = y; bunk <= y + 1; bunk++) {
            if (!free(x, bunk, z) || !free(footX, bunk, footZ)) {
                return;
            }
            BlockState head = AerolargeRegistry.BUNK.get().defaultBlockState().setValue(BunkBlock.FACING, facing)
                .setValue(BunkBlock.COLOR, BLANKETS[blankets++ % BLANKETS.length]).setValue(BunkBlock.LIT, true);
            layout.set(x, bunk, z, head);
            layout.set(footX, bunk, footZ, head.setValue(BunkBlock.PART, BedPart.FOOT));
        }
    }

    /** A rug along the open side of a bunk bed standing at the block given, where the bed stands. */
    private void bunkRug(int x, int y, int z, Direction facing, BlockState rug) {
        if (!layout.stateAt(x, y, z).is(AerolargeRegistry.BUNK.get())) {
            return;
        }
        Direction foot = BunkBlock.footward(facing);
        standing(x + facing.getStepX(), y, z + facing.getStepZ(), rug);
        standing(x + foot.getStepX() + facing.getStepX(), y, z + foot.getStepZ() + facing.getStepZ(), rug);
    }

    /**
     * The things of a quarter's sleepers: a suitcase in its corner, at the first spot of a few that is free, the last
     * of them beside the way between the quarters, and in one quarter in so many a bean bag at the next.
     */
    private void belongings(RocketQuarter quarter, int y, int corner, RandomSource random) {
        int middle = (RocketQuarter.INNER + corner + 1) / 2;
        int inner = RocketQuarter.INNER;
        int[][] spots = {{middle, middle}, {inner, middle}, {middle, inner}, {inner, inner}, {inner - 2, inner}, {inner - 2, inner + 1}};
        BlockState suitcase = AerolargeRegistry.SUITCASE.get().defaultBlockState()
            .setValue(SuitcaseBlock.FACING, Direction.from2DDataValue(random.nextInt(4))).setValue(SuitcaseBlock.COLOR, CASES[random.nextInt(CASES.length)]);
        BlockState beanBag = random.nextInt(BEAN_BAG_EVERY) == 0 ? AerolargeRegistry.BEAN_BAG.get().defaultBlockState()
            .setValue(BeanBagBlock.FACING, quarter.end().getOpposite()).setValue(BeanBagBlock.COLOR, BEAN_BAGS[random.nextInt(BEAN_BAGS.length)]) : null;
        for (int[] spot : spots) {
            if (suitcase != null && standing(quarter.x(spot[0]), y, quarter.z(spot[1]), suitcase)) {
                suitcase = null;
            } else if (suitcase == null && beanBag != null && standing(quarter.x(spot[0]), y, quarter.z(spot[1]), beanBag)) {
                return;
            }
        }
    }

    /** The wall of bookshelves out toward her side from the walkway, between the front pairs and the back ones, uneven on top. */
    private void bookshelves(RocketQuarter quarter, int y, int arm, RandomSource random) {
        BlockState shelf = ShelfUnitBlock.stocked(AerolargeRegistry.SHELF_UNIT.get(), ShelfUnitBlock.Kind.BOOKS, quarter.end());
        for (int across = RocketQuarter.INNER; across <= arm; across++) {
            stack(quarter.x(across), y, 0, shelf, random.nextInt(LOW_SHELF_EVERY) == 0 ? 1 : SHELVES_HIGH);
        }
    }

    /**
     * The reading nook behind the shaft, looking at the poster on its back: an armchair facing it between two low
     * bookshelves, a rug before it and a soul lantern hanging over the rug.
     */
    private void nook(int y) {
        int seat = RocketQuarter.INNER + 1;
        seat(0, y, seat, Direction.NORTH, DyeColor.PURPLE);
        BlockState shelf = ShelfUnitBlock.stocked(AerolargeRegistry.SHELF_UNIT.get(), ShelfUnitBlock.Kind.BOOKS, Direction.NORTH);
        standing(-1, y, seat, shelf);
        standing(1, y, seat, shelf);
        rug(-1, RocketQuarter.INNER, 1, RocketQuarter.INNER, Blocks.BROWN_CARPET.defaultBlockState());
        pendant(0, RocketQuarter.INNER, NIGHT_LIGHT);
    }
}
