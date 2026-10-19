package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.CabinWindowBlock;
import dev.aerolarge.block.GymRingsBlock;
import dev.aerolarge.block.HideAndSeekButtonBlock;
import dev.aerolarge.block.LightStripBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The furnished cabin's finishing touches, laid once the fit-out's rooms stand, from what they left: a diving tower
 * at the moon pool, a starlit ceiling over the lounge, the gym hall's roof trusses, rings, team banners and hide and
 * seek buttons, and greenery. Like the fit-out it only goes into open cabin space (and the pool's water, and the
 * light cables and chains of what it hangs from a ceiling or a truss instead of the crown), it keeps clear of the
 * dividers between the zones, which stand already, and anything that does not fit at the airliner's scale is left
 * out.
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
    /**
     * A truss spans the gym hall's crown along the highest row still within this many blocks either side of as wide
     * as the hall is over the floor, and at least the least half width wide.
     */
    private static final int TRUSS_INSET = 2;
    private static final int TRUSS_LEAST_HALF_WIDTH = 3;
    /** Rows the truss leaves over the floor at least: over the hoops' scoreboards and the pendant lights. */
    private static final int TRUSS_CLEARANCE = 8;
    /** How far in from the wall the rings hang, in blocks: a block inside the court's side line. */
    private static final int RINGS_FROM_WALL = 2;
    /** How high the rings hang over the floor, in metres: reached with a jump. */
    private static final double RINGS_METRES = 1.5;
    /** The rings never hang lower than this many rows over the floor, clear of heads. */
    private static final int RINGS_LOWEST = 3;
    /** Every this many stations along the gym hall's walls at most, a team banner. */
    private static final int BANNER_SPACING = 4;
    /** Where across the hide and seek button stands by a hoop: to starboard of its pole, a step clear of it. */
    private static final int SEEK_BUTTON_X = 2;

    private final AirframeLayout layout;
    private final int floor;

    CabinDecor(AirframeLayout layout) {
        this.layout = layout;
        this.floor = layout.floorY();
    }

    void apply() {
        highDive();
        starlightCeiling();
        gymHall();
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
     * Whether a station is kept clear of the dividers between the zones: within a station of where one stands (see
     * {@link CabinDividers}, which are up already), or of the middle of the gym hall's court, where its curtain hangs.
     */
    private boolean keptForDivider(int z) {
        for (int at = z - 1; at <= z + 1; at++) {
            if (CabinDividers.standsAt(layout, at)) {
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

    /**
     * The gym hall's roof and walls: a light metal truss across the crown over each row of its pendant lights, which
     * now hang from it (but for the row over the middle of the court, kept for the curtain that divides it);
     * gymnastic rings on chains from the trusses either side of the middle, over the port side of the court by the
     * wall bars; the teams' banners between the upper windows, orange at the home end, blue at the guest end; and a
     * hide and seek button by each hoop.
     */
    private void gymHall() {
        int fromZ = from(CabinFitOut.GYM);
        int toZ = to(CabinFitOut.GYM);
        List<Integer> trusses = new ArrayList<>();
        for (int z = fromZ; z <= toZ; z++) {
            if (!keptForDivider(z) && hasPendant(z) && truss(z)) {
                trusses.add(z);
            }
        }
        int middle = courtMiddle();
        trusses.stream().filter(z -> z < middle).max(Integer::compare).ifPresent(this::rings);
        trusses.stream().filter(z -> z > middle).min(Integer::compare).ifPresent(this::rings);
        banners(fromZ, toZ);
        seekButtons(fromZ, toZ);
    }

    /**
     * The hide and seek button at each end of the hall, beside the hoop's pole behind the court's end line, facing the
     * court; only where a scoreboard hangs over that hoop, as the button starts its countdown.
     */
    private void seekButtons(int fromZ, int toZ) {
        for (int z : new int[]{fromZ, toZ}) {
            if (keptForDivider(z) || !hasScoreboard(z)) {
                continue;
            }
            layout.furnish(SEEK_BUTTON_X, floor + 1, z, AerolargeRegistry.HIDE_AND_SEEK_BUTTON.get().defaultBlockState()
                .setValue(HideAndSeekButtonBlock.FACING, z == fromZ ? Direction.SOUTH : Direction.NORTH));
        }
    }

    /** Whether a scoreboard hangs over the middle of this station. */
    private boolean hasScoreboard(int z) {
        for (int y = floor + 1; y <= cabinTop(); y++) {
            if (layout.stateAt(0, y, z).is(AerolargeRegistry.SCOREBOARD.get())) {
                return true;
            }
        }
        return false;
    }

    /** The top of the cabin over the floor, in rows: nothing in it stands higher. */
    private int cabinTop() {
        return floor + (int) Math.ceil(AirframeShape.HEIGHT * layout.scale());
    }

    /** Whether a pendant light hangs anywhere across this station. */
    private boolean hasPendant(int z) {
        for (int x = layout.besideWall(-1, floor + 1, z); x <= layout.besideWall(1, floor + 1, z); x++) {
            for (int y = floor + 1; y <= cabinTop(); y++) {
                if (layout.stateAt(x, y, z).is(AerolargeRegistry.PENDANT_LIGHT.get())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A truss across the crown at this station, wall to wall along the highest row still wide enough for it, with a
     * king post up from its middle to the crown; light cables crossing its row stop at it, and what was above is taken
     * down. Whether it went in: not where the crown is too low over the court.
     */
    private boolean truss(int z) {
        int y = trussRow(z);
        if (y - floor < TRUSS_CLEARANCE) {
            return false;
        }
        BlockState beam = AirframePalette.girder()
            .map(girder -> AirframePalette.with(AirframePalette.with(girder, "x", "true"), "axis", "x"))
            .orElse(Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true));
        for (int x = layout.besideWall(-1, y, z); x <= layout.besideWall(1, y, z); x++) {
            if (isCable(x, y, z)) {
                for (int above = y + 1; isCable(x, above, z); above++) {
                    layout.set(x, above, z, Blocks.AIR.defaultBlockState());
                }
                layout.set(x, y, z, AirframePalette.with(beam, "bottom", "true"));
            } else {
                layout.furnish(x, y, z, beam);
            }
        }
        BlockState post = AirframePalette.girder().map(girder -> AirframePalette.with(girder, "axis", "y")).orElse(Blocks.IRON_BARS.defaultBlockState());
        if (layout.isOpen(0, y + 1, z) && layout.stateAt(0, y, z).is(beam.getBlock())) {
            layout.set(0, y, z, AirframePalette.with(layout.stateAt(0, y, z), "top", "true"));
            for (int above = y + 1; layout.isOpen(0, above, z); above++) {
                layout.set(0, above, z, post);
            }
        }
        return true;
    }

    /**
     * The row at this station the truss spans: the highest where the cabin is still nearly as wide as over the floor,
     * just under where the walls curve in to the crown, so it reaches out over the sides of the court.
     */
    private int trussRow(int z) {
        int span = Math.max(TRUSS_LEAST_HALF_WIDTH, halfWidth(floor + 1, z) - TRUSS_INSET);
        int y = floor;
        while (halfWidth(y + 1, z) >= span) {
            y++;
        }
        return y;
    }

    /** How far the cabin reaches either side of the middle at this height and station, to the nearer wall. */
    private int halfWidth(int y, int z) {
        return Math.min(-layout.besideWall(-1, y, z), layout.besideWall(1, y, z));
    }

    /**
     * Gymnastic rings on chains from the truss at this station, over the port side of the court just inside its side
     * line; none over a court line, or where it is not the truss overhead.
     */
    private void rings(int z) {
        int x = layout.besideWall(-1, floor + 1, z) + RINGS_FROM_WALL;
        int y = floor + Math.max(RINGS_LOWEST, (int) Math.round(RINGS_METRES * layout.scale()));
        if (layout.stateAt(x, floor, z).is(Blocks.WHITE_CONCRETE) || !layout.isOpen(x, y, z)) {
            return;
        }
        int top = y;
        while (layout.isOpen(x, top + 1, z)) {
            top++;
        }
        BlockState beam = layout.stateAt(x, top + 1, z);
        if (!beam.is(Blocks.IRON_BARS) && AirframePalette.girder().filter(girder -> beam.is(girder.getBlock())).isEmpty()) {
            return;
        }
        layout.set(x, top + 1, z, AirframePalette.with(beam, "bottom", "true"));
        for (int link = y + 1; link <= top; link++) {
            layout.set(x, link, z, Blocks.CHAIN.defaultBlockState());
        }
        layout.set(x, y, z, AerolargeRegistry.GYM_RINGS.get().defaultBlockState().setValue(GymRingsBlock.FACING, Direction.NORTH));
    }

    /**
     * The teams' banners on both walls of the gym hall, every few stations between two upper windows: orange along
     * the third of the hall at the home end (the front), blue along the third at the guest end. Each hangs flat on the
     * wall from the bottom row of the windows beside it, its cloth reaching down to just above the light strip.
     */
    private void banners(int fromZ, int toZ) {
        int third = (toZ - fromZ) / 3;
        for (int side : new int[]{1, -1}) {
            int last = fromZ - BANNER_SPACING;
            for (int z = fromZ; z <= toZ; z++) {
                boolean home = z <= fromZ + third;
                if (!home && z < toZ - third || keptForDivider(z) || z - last < BANNER_SPACING) {
                    continue;
                }
                int y = upperWindowRow(side, z - 1);
                if (y < 0 || upperWindowRow(side, z + 1) != y || upperWindowRow(side, z) >= 0) {
                    continue;
                }
                int x = layout.besideWall(side, y, z);
                if (x == 0 || !layout.isOpen(x, y, z) || !layout.isPlainHull(x + side, y, z)) {
                    continue;
                }
                Block banner = home ? Blocks.ORANGE_WALL_BANNER : Blocks.BLUE_WALL_BANNER;
                layout.set(x, y, z, banner.defaultBlockState().setValue(WallBannerBlock.FACING, side > 0 ? Direction.WEST : Direction.EAST));
                last = z;
            }
        }
    }

    /** The bottom row of the upper deck's window in the wall on this side at this station; -1 where there is none. */
    private int upperWindowRow(int side, int z) {
        for (int y = floor + MAIN_WINDOWS_TOP + 1; y <= cabinTop(); y++) {
            int x = layout.besideWall(side, y, z);
            if (x != 0 && CabinWindowBlock.isPane(layout.stateAt(x + side, y, z))) {
                return y;
            }
        }
        return -1;
    }
}
