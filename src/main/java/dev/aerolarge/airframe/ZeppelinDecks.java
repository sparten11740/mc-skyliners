package dev.aerolarge.airframe;

import static dev.aerolarge.airframe.ZeppelinShape.*;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.BarCounterBlock;
import dev.aerolarge.block.BeanBagBlock;
import dev.aerolarge.block.BunkBlock;
import dev.aerolarge.block.CabinSeatBlock;
import dev.aerolarge.block.CoffeeMachineBlock;
import dev.aerolarge.block.DisplayShelfBlock;
import dev.aerolarge.block.GalleyUnitBlock;
import dev.aerolarge.block.PlanterBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The furnished zeppelin's passenger decks, fitted out from the airliner's furniture as the Hindenburg's were laid
 * out. On the lower deck: the boarding hall at the front, bamboo along its walls; the galley beside the stairs, a
 * coffee machine on its worktop; and aft of the stairs the sleeping cabins, bunks along both walls, their reading
 * lights on. On the upper deck: armchairs along both promenades, facing out of the slanting windows; the dining room
 * at the front, tables of two set for four; and the lounge aft on the port side, a bar with stools before its back
 * bar, the coffee on the bar, and bean bags beyond it. Everything stands only where the deck is open, never in the
 * stairs, their headroom or the hull, so the decks of a plain zeppelin and a furnished one are otherwise the same.
 */
final class ZeppelinDecks {
    /** How far aft of the decks' front the boarding hall reaches, and how far apart its planters stand, in blocks. */
    private static final int HALL_LENGTH = 21;
    private static final int PLANTER_SPACING = 4;
    /**
     * Where the galley runs, from how far aft of the decks' front to how far, in blocks: between the ring that passes
     * through the walls there and the stairs.
     */
    private static final int GALLEY_FROM = 25;
    private static final int GALLEY_TO = 30;
    /** How far aft of the decks' middle the cabins start, and how far apart the bunks lie along a wall, in blocks. */
    private static final int CABINS_FROM = 2;
    private static final int BUNK_SPACING = 3;
    /** How far in from the promenade windows its armchairs stand, and how far apart, in blocks. */
    private static final int PROMENADE_IN = 2;
    private static final int PROMENADE_SPACING = 3;
    /** The dining room's tables: from how far aft of the decks' front to how far, how far apart, in blocks. */
    private static final int DINING_FROM = 4;
    private static final int DINING_TO = 26;
    private static final int TABLE_SPACING = 6;
    /** How far out from the middle the dining room's two columns of tables stand, either side, in blocks. */
    private static final int[] TABLES_OUT = {5, 11};
    /** The lounge's bar: how far aft of the decks' middle it starts, its length and how far out to port, in blocks. */
    private static final int BAR_FROM = 8;
    private static final int BAR_LENGTH = 8;
    private static final int BAR_OUT = 10;
    /** The lounge's bean bags: how far aft of the bar's end they lie, how many, and how far out to port, in blocks. */
    private static final int BEAN_BAGS_FROM = 4;
    private static final int BEAN_BAGS = 5;
    private static final int BEAN_BAGS_OUT = 4;
    /** The colours the bunks' blankets and the bean bags come in, one after another. */
    private static final DyeColor[] SOFT = {DyeColor.LIGHT_BLUE, DyeColor.PINK, DyeColor.LIME, DyeColor.YELLOW, DyeColor.ORANGE, DyeColor.PURPLE};

    private final ZeppelinLayout layout;
    private int coloursUsed;

    ZeppelinDecks(ZeppelinLayout layout) {
        this.layout = layout;
    }

    /** Furnishes both decks, once their stairs are in. */
    void furnish() {
        int front = (int) Math.ceil(DECKS_FRONT * SCALE);
        int back = (int) Math.floor(DECKS_BACK * SCALE);
        int middle = (front + back) / 2;
        int lower = layout.lowerDeckRow();
        int upper = layout.upperDeckRow();
        boardingHall(front, lower);
        galley(front, lower);
        cabins(middle + CABINS_FROM, back - 1, lower);
        promenades(front + 1, back - 1, upper);
        dining(front, upper);
        lounge(middle + BAR_FROM, upper);
    }

    /** Bamboo in planters along both walls of the boarding hall, facing in. */
    private void boardingHall(int front, int lower) {
        for (int z = front + PLANTER_SPACING; z <= front + HALL_LENGTH; z += PLANTER_SPACING) {
            wall(-1, lower + 1, z, PlanterBlock.planted(AerolargeRegistry.PLANTER.get(), PlanterBlock.Plant.BAMBOO, Direction.EAST));
            wall(1, lower + 1, z, PlanterBlock.planted(AerolargeRegistry.PLANTER.get(), PlanterBlock.Plant.BAMBOO, Direction.WEST));
        }
    }

    /** The galley along the starboard wall: worktops, a coffee machine on the middle one, and stowage either end. */
    private void galley(int front, int lower) {
        int coffee = front + (GALLEY_FROM + GALLEY_TO) / 2;
        for (int z = front + GALLEY_FROM; z <= front + GALLEY_TO; z++) {
            boolean end = z == front + GALLEY_FROM || z == front + GALLEY_TO;
            BlockState unit = (end ? AerolargeRegistry.GALLEY_STOWAGE : AerolargeRegistry.GALLEY_WORKTOP).get().defaultBlockState()
                .setValue(GalleyUnitBlock.FACING, Direction.WEST);
            int x = wall(1, lower + 1, z, unit);
            if (z == coffee && x > 0) {
                place(x, lower + 2, z, AerolargeRegistry.COFFEE_MACHINE.get().defaultBlockState().setValue(CoffeeMachineBlock.FACING, Direction.WEST));
            }
        }
    }

    /** The sleeping cabins: bunks along both walls, every few blocks, each in the next soft colour, its light on. */
    private void cabins(int from, int to, int lower) {
        for (int z = from; z + 1 <= to; z += BUNK_SPACING) {
            bunk(-1, lower + 1, z);
            bunk(1, lower + 1, z);
        }
    }

    /** A bunk against a wall, facing in, its head and foot along the hull, where both are open. */
    private void bunk(int side, int y, int z) {
        Direction facing = side > 0 ? Direction.WEST : Direction.EAST;
        int x = side * layout.wallX(y, z);
        if (x == 0 || side * layout.wallX(y, z + 1) != x || !layout.isOpen(x, y, z) || !layout.isOpen(x, y, z + 1)) {
            return;
        }
        boolean footAft = BunkBlock.footward(facing) == Direction.SOUTH;
        BlockState head = AerolargeRegistry.BUNK.get().defaultBlockState()
            .setValue(BunkBlock.FACING, facing).setValue(BunkBlock.COLOR, nextColour()).setValue(BunkBlock.LIT, true);
        layout.set(x, y, footAft ? z : z + 1, head);
        layout.set(x, y, footAft ? z + 1 : z, head.setValue(BunkBlock.PART, BedPart.FOOT));
    }

    /** Armchairs a little in from both promenades' windows, facing out of them, every few blocks. */
    private void promenades(int from, int to, int upper) {
        for (int z = from; z <= to; z += PROMENADE_SPACING) {
            for (int side = -1; side <= 1; side += 2) {
                int x = side * (layout.wallX(upper + 1, z) - PROMENADE_IN);
                seat(x, upper + 1, z, side > 0 ? Direction.EAST : Direction.WEST, DyeColor.BLUE);
            }
        }
    }

    /** The dining room's tables, two columns either side, each two bar counters with two armchairs down each side. */
    private void dining(int front, int upper) {
        for (int z = front + DINING_FROM; z + 1 <= front + DINING_TO; z += TABLE_SPACING) {
            for (int out : TABLES_OUT) {
                table(-out, upper + 1, z);
                table(out, upper + 1, z);
            }
        }
    }

    /** A table two blocks long along the hull, an armchair facing it at either end of either side. */
    private void table(int x, int y, int z) {
        if (!layout.isOpen(x, y, z) || !layout.isOpen(x, y, z + 1)) {
            return;
        }
        BlockState top = AerolargeRegistry.BAR_COUNTER.get().defaultBlockState().setValue(BarCounterBlock.FACING, Direction.EAST);
        layout.set(x, y, z, top);
        layout.set(x, y, z + 1, top);
        for (int along = z; along <= z + 1; along++) {
            seat(x - 1, y, along, Direction.EAST, DyeColor.RED);
            seat(x + 1, y, along, Direction.WEST, DyeColor.RED);
        }
    }

    /**
     * The lounge on the port side, aft of the stairs: a bar along the hull facing the middle, stools before it, the
     * back bar behind it, the coffee on the bar, and bean bags aft of it.
     */
    private void lounge(int from, int upper) {
        int y = upper + 1;
        BlockState counter = AerolargeRegistry.BAR_COUNTER.get().defaultBlockState().setValue(BarCounterBlock.FACING, Direction.EAST);
        BlockState backBar = AerolargeRegistry.BACK_BAR.get().defaultBlockState().setValue(DisplayShelfBlock.FACING, Direction.EAST);
        for (int z = from; z < from + BAR_LENGTH; z++) {
            place(-BAR_OUT, y, z, counter);
            place(-BAR_OUT - 2, y, z, backBar);
            if ((z - from) % 2 == 0) {
                place(-BAR_OUT + 1, y, z, AerolargeRegistry.BAR_STOOL.get().defaultBlockState());
            }
        }
        place(-BAR_OUT, y + 1, from + BAR_LENGTH / 2,
            AerolargeRegistry.COFFEE_MACHINE.get().defaultBlockState().setValue(CoffeeMachineBlock.FACING, Direction.EAST));
        for (int k = 0; k < BEAN_BAGS; k++) {
            int z = from + BAR_LENGTH + BEAN_BAGS_FROM + 2 * k;
            place(-BEAN_BAGS_OUT - k % 2 * 2, y, z, AerolargeRegistry.BEAN_BAG.get().defaultBlockState()
                .setValue(BeanBagBlock.FACING, Direction.EAST).setValue(BeanBagBlock.COLOR, nextColour()));
        }
    }

    /** An armchair facing the way given, its back over it, where both are open. */
    private void seat(int x, int y, int z, Direction facing, DyeColor colour) {
        if (!layout.isOpen(x, y, z) || !layout.isOpen(x, y + 1, z)) {
            return;
        }
        BlockState seat = CabinSeatBlock.seat(AerolargeRegistry.BUSINESS_SEAT.get(), colour, facing);
        layout.set(x, y, z, seat);
        layout.set(x, y + 1, z, seat.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
    }

    /** A piece of furniture, where the deck is open. */
    private void place(int x, int y, int z, BlockState state) {
        if (layout.isOpen(x, y, z) && layout.inHull(x, y, z)) {
            layout.set(x, y, z, state);
        }
    }

    /** Something against the wall on one side, -1 port or 1 starboard; where across it went, or 0 where it did not. */
    private int wall(int side, int y, int z, BlockState state) {
        int x = side * layout.wallX(y, z);
        if (x == 0 || !layout.isOpen(x, y, z)) {
            return 0;
        }
        layout.set(x, y, z, state);
        return x;
    }

    private DyeColor nextColour() {
        return SOFT[coloursUsed++ % SOFT.length];
    }
}
