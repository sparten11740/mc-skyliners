package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.CabinWindowBlock;
import dev.aerolarge.block.LightStripBlock;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The furnished cabin's finishing touches, laid once the fit-out's rooms stand, from what they left: a diving tower
 * at the moon pool and a starlit ceiling over the lounge. Like the fit-out it only goes into open cabin space (and
 * the pool's water, and the light cables and chains hung from the lounge's crown, which it shortens), and anything
 * that does not fit at the airliner's scale is left out.
 */
final class CabinDecor {
    /** How high the diving tower's platform stands over the cabin floor, in metres: a three-metre springboard's. */
    private static final double PLATFORM_METRES = 3.0;
    /**
     * Where across the tower stands, in blocks from the middle: just to port of the low board on the centreline,
     * under the highest part of the crown.
     */
    private static final int TOWER_X = -1;
    /** Open space over the springboard, in blocks: someone standing on it and the height it throws them. */
    private static final int BOUNCE_ROOM = 5;
    /** Water at least this deep under the springboard. */
    private static final int MIN_DEPTH = 3;
    /** How high the lounge's starlit ceiling hangs over the floor, in metres, unless an upper window wants it lower. */
    private static final double STARLIGHT_METRES = 3.5;
    /** Rows between the floor and the starlit ceiling it needs at least: headroom and a pendant light under it. */
    private static final int STARLIGHT_HEADROOM = 6;
    /** Over the floor up to this row the windows are the main deck's; any higher, the upper deck's. */
    private static final int MAIN_WINDOWS_TOP = 3;
    /**
     * Where the zones are divided from one another, in metres from the nose: the business class from the forward doors
     * and from the pool, the pool from the mid doors and those from the gym hall, the aft doors from the gym hall and
     * from the lounge. These stations and the one either side are left free for the dividers.
     */
    private static final double[] DIVIDERS = {13.5, 17.5, 23.5, 26.5, 48.5, 51.5};
    /** The potted plants along the lounge bar's back shelf, in turn. */
    private static final List<Block> SHELF_PLANTS = List.of(Blocks.POTTED_FLOWERING_AZALEA, Blocks.POTTED_BLUE_ORCHID, Blocks.POTTED_ORANGE_TULIP);
    /** Every this many stations along the bar's back shelf, a potted plant. */
    private static final int SHELF_SPACING = 3;
    /** Every this many stations along the bar, a spore blossom hanging over it. */
    private static final int BAR_BLOSSOM_SPACING = 4;
    /** Every this many stations along each side of the pool, a potted azalea between patches of moss. */
    private static final int PLANTER_SPACING = 4;
    /** A spore blossom hangs no lower than this many rows over the floor, clear of heads. */
    private static final int BLOSSOM_CLEARANCE = 4;
    /** How many stations either side of a passenger door the potted bamboo stands: just outside the door's aisle. */
    private static final int BAMBOO_FROM_DOOR = 3;

    private final AirframeLayout layout;
    private final int floor;

    CabinDecor(AirframeLayout layout) {
        this.layout = layout;
        this.floor = layout.floorY();
    }

    void apply() {
        highDive();
        starlightCeiling();
        // After the ceiling: spore blossoms hang from it.
        greenery();
    }

    /** The first station of a zone, in metres from the nose, as the fit-out rounds it. */
    private int from(double[] zone) {
        return (int) Math.ceil(zone[0] * layout.scale());
    }

    /** The last station of a zone, as the fit-out rounds it. */
    private int to(double[] zone) {
        return (int) Math.floor(zone[1] * layout.scale());
    }

    private boolean isWater(int x, int y, int z) {
        return layout.stateAt(x, y, z).is(Blocks.WATER);
    }

    /**
     * The moon pool's basin as the fit-out left it: the first and last stations where its water lies flush with the
     * floor down the centreline, and how far across from the middle the water reaches halfway along.
     */
    private record Basin(int first, int last, int halfWidth) {
        int middle() {
            return (first + last) / 2;
        }
    }

    /** The moon pool's basin; null where the airliner has none. */
    @Nullable
    private Basin basin() {
        int first = -1;
        int last = -1;
        for (int z = from(CabinFitOut.POOL); z <= to(CabinFitOut.POOL); z++) {
            if (isWater(0, floor, z)) {
                first = first < 0 ? z : first;
                last = z;
            }
        }
        if (first < 0) {
            return null;
        }
        int halfWidth = 0;
        while (isWater(halfWidth + 1, floor, (first + last) / 2)) {
            halfWidth++;
        }
        return new Basin(first, last, halfWidth);
    }

    /**
     * Whether a station is kept free for a divider between two zones: within a station of where one stands, or of the
     * middle of the gym hall's court.
     */
    private boolean keptForDivider(int z) {
        for (double station : DIVIDERS) {
            if (Math.abs(z - (int) Math.round(station * layout.scale())) <= 1) {
                return true;
            }
        }
        return Math.abs(z - courtMiddle()) <= 1;
    }

    /** The station across the middle of the gym hall's court, its centre line. */
    private int courtMiddle() {
        return (from(CabinFitOut.GYM) + to(CabinFitOut.GYM)) / 2;
    }

    /**
     * The diving tower (Sprungturm) on the pool's front rim: a white column standing on the pool's bottom at the head
     * of the basin, a ladder up its front from the rim, and from its top a board out over the middle of the pool with
     * a springboard at its tip. Glass rails either side of the top keep anyone from stepping off it onto the rim; off
     * the board, the deck or the springboard there is only water below. Left out where the pool is too shallow or too
     * narrow, or the crown too low to bounce under.
     */
    private void highDive() {
        Basin basin = basin();
        if (basin == null) {
            return;
        }
        int x = TOWER_X;
        int first = basin.first();
        int middle = basin.middle();
        int rim = first - 1;
        int top = floor + (int) Math.round(PLATFORM_METRES * layout.scale());
        if (basin.halfWidth() < Math.abs(x) + 1 || middle - first < 2 || !towerFits(x, rim, first, middle, top)) {
            return;
        }
        BlockState column = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        int bottom = floor;
        while (isWater(x, bottom - 1, first)) {
            bottom--;
        }
        for (int y = bottom; y < top; y++) {
            layout.set(x, y, first, column);
        }
        for (int y = floor + 1; y < top; y++) {
            layout.furnish(x, y, rim, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        }
        for (int z = first + 1; z < middle; z++) {
            layout.furnish(x, top - 1, z, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        }
        layout.furnish(x, top - 1, middle, AerolargeRegistry.SPRINGBOARD.get().defaultBlockState());
        for (int side : new int[]{1, -1}) {
            BlockState rail = Blocks.GLASS_PANE.defaultBlockState();
            boolean both = layout.isOpen(x + side, top, rim) && layout.isOpen(x + side, top, first);
            layout.furnish(x + side, top, rim, rail.setValue(IronBarsBlock.SOUTH, both));
            layout.furnish(x + side, top, first, rail.setValue(IronBarsBlock.NORTH, both));
        }
    }

    /**
     * Whether the tower fits in this column: the rim open over the floor up to the platform for the ladder, the
     * column at the head of the basin open above the water and water below, the board open out to the middle, room to
     * bounce over the springboard and deep water under it.
     */
    private boolean towerFits(int x, int rim, int first, int middle, int top) {
        for (int y = floor + 1; y < top; y++) {
            if (!layout.isOpen(x, y, rim) || !layout.isOpen(x, y, first)) {
                return false;
            }
        }
        for (int z = first + 1; z <= middle; z++) {
            if (!layout.isOpen(x, top - 1, z)) {
                return false;
            }
        }
        for (int y = top; y < top + BOUNCE_ROOM; y++) {
            if (!layout.isOpen(x, y, middle)) {
                return false;
            }
        }
        for (int y = floor; y > floor - MIN_DEPTH; y--) {
            if (!isWater(x, y, first) || !isWater(x, y, middle)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The lounge's starlit ceiling: a dropped ceiling of starlight panels across the lounge, a block under the upper
     * deck's windows so it never cuts across one, with LED strips along its edges where it meets the walls. What hung
     * from the crown now hangs from it, the pendant lights and the duty-free kiosk's awning: their cables and chains
     * stop at the panels, and what was above is taken down. The first and last rows are left to the lounge's ends.
     * Left out where it would leave too little headroom.
     */
    private void starlightCeiling() {
        int fromZ = from(CabinFitOut.LOUNGE) + 1;
        int toZ = to(CabinFitOut.LOUNGE) - 1;
        int height = starlightHeight(fromZ, toZ);
        if (height - floor < STARLIGHT_HEADROOM) {
            return;
        }
        BlockState panel = AerolargeRegistry.STARLIGHT_PANEL.get().defaultBlockState();
        for (int z = fromZ; z <= toZ; z++) {
            int port = layout.besideWall(-1, height, z);
            int starboard = layout.besideWall(1, height, z);
            if (port == 0 || starboard == 0) {
                continue;
            }
            for (int x = port; x <= starboard; x++) {
                if (isHanger(x, height, z)) {
                    for (int y = height + 1; isHanger(x, y, z); y++) {
                        layout.set(x, y, z, Blocks.AIR.defaultBlockState());
                    }
                    layout.set(x, height, z, panel);
                } else {
                    layout.furnish(x, height, z, panel);
                }
            }
            for (int side : new int[]{1, -1}) {
                int x = layout.besideWall(side, height - 1, z);
                if (x != 0) {
                    layout.furnish(x, height - 1, z, AerolargeRegistry.LIGHT_STRIP.get().defaultBlockState()
                        .setValue(LightStripBlock.FACING, side > 0 ? Direction.EAST : Direction.WEST));
                }
            }
        }
    }

    /**
     * Where the starlit ceiling goes: its metres over the floor, or lower, a block under the lowest of the upper
     * deck's windows along the stretch.
     */
    private int starlightHeight(int fromZ, int toZ) {
        int height = floor + (int) Math.round(STARLIGHT_METRES * layout.scale());
        for (int z = fromZ; z <= toZ; z++) {
            for (int side : new int[]{1, -1}) {
                for (int y = floor + MAIN_WINDOWS_TOP + 1; y <= height; y++) {
                    int x = layout.besideWall(side, y, z);
                    if (x != 0 && CabinWindowBlock.isPane(layout.stateAt(x + side, y, z))) {
                        height = y - 1;
                    }
                }
            }
        }
        return height;
    }

    private boolean isCable(int x, int y, int z) {
        return layout.stateAt(x, y, z).is(AerolargeRegistry.LIGHT_CABLE.get());
    }

    /** Whether what is here hangs something from the crown: a pendant light's cable, or a chain. */
    private boolean isHanger(int x, int y, int z) {
        return isCable(x, y, z) || layout.stateAt(x, y, z).is(Blocks.CHAIN);
    }

    /**
     * Greenery along the walls and overhead, never underfoot in an aisle: potted flowers along the lounge bar's back
     * shelf and spore blossoms hanging from the starlit ceiling over the bar, drifting their spores down; potted
     * azaleas in patches of moss along the pool's sides, and over its rim planters of moss hung under the crown, a
     * spore blossom under each; and potted bamboo either side of each passenger door, just clear of its aisle.
     */
    private void greenery() {
        barGreenery();
        poolGreenery();
        doorBamboo();
    }

    /** The lounge bar's: its back shelf is the row of barrels along the starboard wall, its counter in front of them. */
    private void barGreenery() {
        int fromZ = from(CabinFitOut.LOUNGE);
        int plants = 0;
        for (int z = fromZ + 1; z < to(CabinFitOut.LOUNGE); z++) {
            int shelf = layout.besideWall(1, floor + 1, z);
            if (shelf == 0 || !layout.stateAt(shelf, floor + 1, z).is(Blocks.BARREL)) {
                continue;
            }
            if ((z - fromZ) % SHELF_SPACING == 0 && layout.isOpen(shelf, floor + 2, z)) {
                layout.furnish(shelf, floor + 2, z, SHELF_PLANTS.get(plants++ % SHELF_PLANTS.size()).defaultBlockState());
            }
            if ((z - fromZ) % BAR_BLOSSOM_SPACING == BAR_BLOSSOM_SPACING / 2) {
                hangBlossom(shelf - 1, z, state -> state.is(AerolargeRegistry.STARLIGHT_PANEL.get()));
            }
        }
    }

    /**
     * The pool's: a potted azalea every few stations along each side against the wall, with moss either side of it,
     * and two planters over each side of the rim, a quarter of the way in from either end of the basin.
     */
    private void poolGreenery() {
        Basin basin = basin();
        if (basin == null) {
            return;
        }
        int quarter = (basin.last() - basin.first()) / 4;
        for (int side : new int[]{1, -1}) {
            for (int z = basin.first() + 1; z <= basin.last(); z += PLANTER_SPACING) {
                int x = layout.besideWall(side, floor + 1, z);
                if (x == 0 || keptForDivider(z) || !layout.isOpen(x, floor + 1, z)) {
                    continue;
                }
                layout.furnish(x, floor + 1, z, Blocks.POTTED_FLOWERING_AZALEA.defaultBlockState());
                for (int dz : new int[]{-1, 1}) {
                    if (!keptForDivider(z + dz)) {
                        layout.furnish(x, floor + 1, z + dz, Blocks.MOSS_CARPET.defaultBlockState());
                    }
                }
            }
            int rim = side * (basin.halfWidth() + 1);
            hangingPlanter(rim, basin.first() + quarter);
            hangingPlanter(rim, basin.last() - quarter);
        }
    }

    /**
     * A planter of moss hung under the crown over this spot, a spore blossom under it: at this station, or failing that
     * the one behind or ahead, where it would hide no upper window and keeps clear of the dividers.
     */
    private void hangingPlanter(int x, int z) {
        int side = Integer.signum(x);
        for (int at : new int[]{z, z + 1, z - 1}) {
            int top = layout.ceiling(x, at);
            if (keptForDivider(at) || !layout.isOpen(x, top, at) || top - 1 < floor + BLOSSOM_CLEARANCE
                || CabinWindowBlock.isPane(layout.stateAt(x + side, top, at))) {
                continue;
            }
            layout.set(x, top, at, Blocks.MOSS_BLOCK.defaultBlockState());
            hangBlossom(x, at, state -> state.is(Blocks.MOSS_BLOCK));
            return;
        }
    }

    /**
     * A spore blossom hung from the underside of whatever the column's ceiling is, if the test accepts it and it is
     * high enough to clear heads.
     */
    private void hangBlossom(int x, int z, Predicate<BlockState> anchor) {
        int y = floor + BLOSSOM_CLEARANCE;
        if (!layout.isOpen(x, y, z)) {
            return;
        }
        while (layout.isOpen(x, y + 1, z)) {
            y++;
        }
        if (anchor.test(layout.stateAt(x, y + 1, z))) {
            layout.set(x, y, z, Blocks.SPORE_BLOSSOM.defaultBlockState());
        }
    }

    /**
     * Potted bamboo against both walls either side of each passenger door, where the station is plain cabin, its
     * floor not made over into one of the rooms.
     */
    private void doorBamboo() {
        for (double station : AirframeShape.PASSENGER_DOORS) {
            int door = (int) Math.round(station * layout.scale());
            for (int z : new int[]{door - BAMBOO_FROM_DOOR, door + BAMBOO_FROM_DOOR}) {
                for (int side : new int[]{1, -1}) {
                    int x = layout.besideWall(side, floor + 1, z);
                    if (x != 0 && !keptForDivider(z) && layout.isCabinFloor(0, floor, z)) {
                        layout.furnish(x, floor + 1, z, Blocks.POTTED_BAMBOO.defaultBlockState());
                    }
                }
            }
        }
    }
}
