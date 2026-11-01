package dev.skyliners.airframe;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.BeanBagBlock;
import dev.skyliners.block.BunkBlock;
import dev.skyliners.block.RungLadderBlock;
import dev.skyliners.block.ShelfUnitBlock;
import dev.skyliners.block.SuitcaseBlock;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * The bunk room: bunk beds against her skin between the windows, a pair at each of the four diagonals, each its upper
 * bunk three rows over its lower on the lower's corner posts, room to stand up under it, with a ladder up its foot end
 * (see {@link BunkBlock.Tier}); their reading lights on, their blankets each in a colour of their own, and a blue halo
 * light hanging in front of each pair, as dim as a night light; in front of each bunk a rug of its sleeper's own
 * colour, and about each pair the sleepers' things, a suitcase and now and then a bean bag; crew lockers either side of
 * the way out and of the back window, a space helmet in each. Out to either side a wall of bookshelves, one high here
 * and two there, parts the front pairs from the back ones; and behind the shaft a reading nook looks at the solar
 * system poster on it: an armchair between two low bookshelves on a rug, a blue halo light over it.
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
    /** How many rows over an upper bunk are kept clear, to sit and stand up on it. */
    private static final int UPPER_HEADROOM = 2;
    /**
     * How much nearer her skin than other furniture a bunk bed's ladder may stand, in blocks: it stands up against the
     * bed's foot end, where the bed meets her curve, and keeps nothing from her LED strips along the skin.
     */
    private static final double LADDER_NEARER_SKIN = 1;
    /** How far in from the bunks each pair's night light hangs, in blocks: over the rugs, clear of the upper bunks. */
    private static final double LIGHT_IN = 2;

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
            pendant(across(out - LIGHT_IN, diagonal), along(out - LIGHT_IN, diagonal), NIGHT_LIGHT);
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

    /**
     * A bunk bed facing the way given, its head at the block and its foot beside it: the lower bunk, and where there is
     * room for it, its headroom and the ladder up the foot end, the upper bunk over it; else the lower on its own.
     */
    private void bunkBed(int x, int y, int z, Direction facing) {
        Direction foot = BunkBlock.footward(facing);
        int footX = x + foot.getStepX();
        int footZ = z + foot.getStepZ();
        if (!free(x, y, z) || !free(footX, y, footZ)) {
            return;
        }
        int ladderX = footX + foot.getStepX();
        int ladderZ = footZ + foot.getStepZ();
        boolean bed = clearAbove(x, y, z) && clearAbove(footX, y, footZ) && clearUp(ladderX, y, ladderZ, BunkBlock.LADDER_ROWS, -LADDER_NEARER_SKIN);
        bunk(x, y, z, footX, footZ, facing, bed ? BunkBlock.Tier.LOWER : BunkBlock.Tier.SINGLE);
        if (!bed) {
            return;
        }
        bunk(x, y + BunkBlock.UPPER_RISE, z, footX, footZ, facing, BunkBlock.Tier.UPPER);
        BlockState ladder = SkylinersRegistry.BUNK_LADDER.get().defaultBlockState().setValue(LadderBlock.FACING, foot);
        for (int row = 0; row < BunkBlock.LADDER_ROWS; row++) {
            layout.set(ladderX, y + row, ladderZ, ladder.setValue(RungLadderBlock.TOP, row == BunkBlock.LADDER_ROWS - 1));
        }
    }

    /** One bunk of a bunk bed, or a bunk on its own, its head and foot at the blocks given, in the next blanket. */
    private void bunk(int x, int y, int z, int footX, int footZ, Direction facing, BunkBlock.Tier tier) {
        BlockState head = SkylinersRegistry.BUNK.get().defaultBlockState().setValue(BunkBlock.FACING, facing)
            .setValue(BunkBlock.COLOR, BLANKETS[blankets++ % BLANKETS.length]).setValue(BunkBlock.LIT, true).setValue(BunkBlock.TIER, tier);
        layout.set(x, y, z, head);
        layout.set(footX, y, footZ, head.setValue(BunkBlock.PART, BedPart.FOOT));
    }

    /** Whether the rows over a lower bunk's block are free up to the upper bunk and its headroom. */
    private boolean clearAbove(int x, int y, int z) {
        return clearUp(x, y + 1, z, BunkBlock.UPPER_RISE + UPPER_HEADROOM, 0);
    }

    /** Whether so many rows from a block up are free, so far further in from the ring along the skin (see free). */
    private boolean clearUp(int x, int y, int z, int rows, double further) {
        for (int row = 0; row < rows; row++) {
            if (!free(x, y + row, z, further)) {
                return false;
            }
        }
        return true;
    }

    /** A rug along the open side of a bunk bed standing at the block given, where the bed stands. */
    private void bunkRug(int x, int y, int z, Direction facing, BlockState rug) {
        if (!layout.stateAt(x, y, z).is(SkylinersRegistry.BUNK.get())) {
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
        int middle = RocketQuarter.middle(corner);
        int inner = RocketQuarter.INNER;
        int[][] spots = {{middle, middle}, {inner, middle}, {middle, inner}, {inner, inner}, {inner - 2, inner}, {inner - 2, inner + 1}};
        BlockState suitcase = SkylinersRegistry.SUITCASE.get().defaultBlockState()
            .setValue(SuitcaseBlock.FACING, Direction.from2DDataValue(random.nextInt(4)))
            .setValue(SuitcaseBlock.COLOR, CASES[random.nextInt(CASES.length)]);
        BlockState beanBag = random.nextInt(BEAN_BAG_EVERY) == 0 ? SkylinersRegistry.BEAN_BAG.get().defaultBlockState()
            .setValue(BeanBagBlock.FACING, quarter.end().getOpposite()).setValue(BeanBagBlock.COLOR, BEAN_BAGS[random.nextInt(BEAN_BAGS.length)]) : null;
        for (int[] spot : spots) {
            if (suitcase != null && standing(quarter.x(spot[0]), y, quarter.z(spot[1]), suitcase)) {
                suitcase = null;
            } else if (suitcase == null && beanBag != null && standing(quarter.x(spot[0]), y, quarter.z(spot[1]), beanBag)) {
                return;
            }
        }
    }

    /**
     * The wall of bookshelves out toward her side from the walkway, between the front pairs and the back ones, uneven
     * on top.
     */
    private void bookshelves(RocketQuarter quarter, int y, int arm, RandomSource random) {
        BlockState shelf = ShelfUnitBlock.stocked(SkylinersRegistry.SHELF_UNIT.get(), ShelfUnitBlock.Kind.BOOKS, quarter.end());
        for (int across = RocketQuarter.INNER; across <= arm; across++) {
            stack(quarter.x(across), y, 0, shelf, random.nextInt(LOW_SHELF_EVERY) == 0 ? 1 : SHELVES_HIGH);
        }
    }

    /**
     * The reading nook behind the shaft, looking at the poster on its back: an armchair facing it between two low
     * bookshelves, a rug before it and a blue halo light hanging over the rug.
     */
    private void nook(int y) {
        int seat = RocketQuarter.INNER + 1;
        seat(0, y, seat, Direction.NORTH, DyeColor.PURPLE);
        BlockState shelf = ShelfUnitBlock.stocked(SkylinersRegistry.SHELF_UNIT.get(), ShelfUnitBlock.Kind.BOOKS, Direction.NORTH);
        standing(-1, y, seat, shelf);
        standing(1, y, seat, shelf);
        rug(-1, RocketQuarter.INNER, 1, RocketQuarter.INNER, Blocks.BROWN_CARPET.defaultBlockState());
        pendant(0, RocketQuarter.INNER, NIGHT_LIGHT);
    }
}
