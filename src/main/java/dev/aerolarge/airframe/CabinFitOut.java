package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.AisleCarpetBlock;
import dev.aerolarge.block.BallRackBlock;
import dev.aerolarge.block.CabinCarpetBlock;
import dev.aerolarge.block.CabinSeatBlock;
import dev.aerolarge.block.CabinWindowBlock;
import dev.aerolarge.block.CoffeeMachineBlock;
import dev.aerolarge.block.GalleyBlock;
import dev.aerolarge.block.GymFloorBlock;
import dev.aerolarge.block.HoopBlock;
import dev.aerolarge.block.PassengerScreenBlock;
import dev.aerolarge.block.PoolCopingBlock;
import dev.aerolarge.block.PoolMosaicBlock;
import dev.aerolarge.block.ScoreboardBlock;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Fits out the main deck of the furnished airliner, front to back between the door aisles: a business class just
 * behind the forward doors, big seats left and right of the aisle; an indoor moon pool sunk into the hold so its water
 * lies flush with the floor; a full-height gym hall (Turnhalle) over the wing box; and a lounge with a bar in the
 * tail, all lit by pendant lights, with the kids' den behind it (see {@link TailDen}); and passenger screens on the
 * seat backs and the walls. Everything is vanilla blocks but the lights, the pool's glass, the screens, the den's
 * secret door and ball pit, and the seats, cabin seats that are Create's underneath so they can be sat in. It only
 * ever goes into open cabin space or replaces cabin floor, so the doors and the space in front of them stay as they
 * were, and so does the hull, but for the belly under the pool, which is glazed.
 */
final class CabinFitOut {
    /** Where each room starts and ends, in metres from the nose; {@link CabinDividers} walls them off. */
    static final double[] BUSINESS = {13.5, 17.5};
    static final double[] POOL = {18.0, 23.0};
    static final double[] GYM = {27.5, 47.5};
    static final double[] LOUNGE = {52.0, 58.5};
    /** How far apart the business class rows are, seat to seat, in metres: a seat, its back and legroom in front. */
    private static final double BUSINESS_PITCH = 1.5;
    /** How many business class seats stand side by side either side of the aisle. */
    private static final int BUSINESS_SEATS = 2;
    /** Seated eye height: passenger screens hang this many blocks over the floor, the row above the seats. */
    private static final int EYES = 2;
    /** A station with at least this many seats across it is a row of seats; seats along a wall have one to a station. */
    private static final int ROW_SEATS = 2;
    /**
     * Rows of seats at most this far apart, in metres, are one stretch of seating, each row's backs carrying the
     * screens for the row behind it; nearer than this to a row, the walls get no screens of their own.
     */
    private static final double ROW_REACH = 2.5;
    /**
     * Along a wall, a screen at most every this many stations: the windows are two stations apart, so one in every
     * fourth gap between them, enough to see one from any seat without lining the walls with them.
     */
    private static final int WALL_SPACING = 8;
    /**
     * How many stations into the lounge the coffee machine stands on the bar, near the galley at its head: on a station
     * with no stool in front of it, so a player can step right up to it.
     */
    private static final int BAR_COFFEE = 2;
    /** How wide each of the pool's lanes is, in blocks across: a lane line marks its middle on the end walls. */
    private static final int LANE_WIDTH = 3;

    private final AirframeLayout layout;
    private final int floor;
    /** Every seat the fit-out has put in on the main deck: what the passenger screens are fitted to. */
    private final Set<BlockPos> seats = new HashSet<>();

    CabinFitOut(AirframeLayout layout) {
        this.layout = layout;
        this.floor = layout.floorY();
    }

    void apply() {
        businessClass(from(BUSINESS), to(BUSINESS));
        pool(from(POOL), to(POOL));
        gym(from(GYM), to(GYM));
        // Before the lounge, which is furnished round it.
        new DutyFreeKiosk(layout).apply(from(LOUNGE), to(LOUNGE));
        lounge(from(LOUNGE), to(LOUNGE));
        new CabinDividers(layout).apply();
        // Before the decor, so the den's wall closes the lounge and the starlit ceiling stops at it.
        new TailDen(layout).apply(from(LOUNGE), to(LOUNGE));
        // After the dividers, which it keeps clear of.
        new CabinDecor(layout).apply();
        // Last: the screens go where the rest has left room, and on the backs of its seats.
        screens();
    }

    /** Whether overhead lockers go along the walls at this station, in metres from the nose: everywhere but the gym hall. */
    static boolean lockersFit(double metres) {
        return metres < GYM[0] || metres > GYM[1];
    }

    private int from(double[] zone) {
        return (int) Math.ceil(zone[0] * layout.scale());
    }

    private int to(double[] zone) {
        return (int) Math.floor(zone[1] * layout.scale());
    }

    /**
     * Puts a cabin seat on the main deck floor, where there is room for it and the top of its back, and remembers it
     * for the passenger screens.
     */
    private void seat(int x, int z, BlockState seat) {
        if (layout.seat(x, floor + 1, z, seat)) {
            seats.add(new BlockPos(x, floor + 1, z));
        }
    }

    /** The narrowest the cabin gets at standing height over a stretch, so a rectangular room fits everywhere in it. */
    private int narrowest(int fromZ, int toZ) {
        int width = Integer.MAX_VALUE;
        for (int z = fromZ; z <= toZ; z++) {
            width = Math.min(width, layout.openHalfWidth(floor + 1, z));
        }
        return width;
    }

    /**
     * The business class: rows of business seats facing forward, as many as fit side by side either side of the aisle
     * up to two, each with a wooden console at armrest height either side, shared between neighbours; the cabin's blue
     * carpet throughout, the aisle's runner either side of the aisle, its path lights toward the seats; and pendant
     * lights over the aisle hung high enough to clear the overhead lockers and the light strips along the walls.
     */
    private void businessClass(int fromZ, int toZ) {
        int width = narrowest(fromZ, toZ);
        int pitch = Math.max(3, (int) Math.round(BUSINESS_PITCH * layout.scale()));
        if (width < 3 || toZ - fromZ + 1 < pitch) {
            return;
        }
        BlockState seat = CabinSeatBlock.seat(AerolargeRegistry.BUSINESS_SEAT.get(), DyeColor.GRAY, Direction.NORTH);
        BlockState console = Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
        // How far out from the middle the aisle reaches, to just inside the innermost consoles; the whole cabin's width
        // while there are no rows.
        int aisle = width;
        // Each row is legroom, then the seats, their backs as high as a seated passenger's head, with the screen for
        // the row behind set onto them; the first row's legroom is toward the door aisle.
        for (int z = fromZ + pitch - 2; z + 1 <= toZ; z += pitch) {
            for (int side : new int[]{1, -1}) {
                // From the wall in: a console, a seat, a console and so on, leaving the aisle down the middle clear.
                for (int k = 0, x = width - 1; k < BUSINESS_SEATS && x - 1 >= 1; k++, x -= 2) {
                    seat(side * x, z, seat);
                    layout.furnish(side * (x + 1), floor + 1, z, console);
                    layout.furnish(side * (x - 1), floor + 1, z, console);
                    aisle = Math.min(aisle, x - 2);
                }
            }
        }
        carpet(fromZ, toZ, width, aisle, DyeColor.BLUE);
        for (int z = fromZ + 1; z <= toZ; z += 4) {
            hangLight(0, z, 7);
        }
    }

    /**
     * Carpets a room wall to wall, out to the width given either side of the middle, in the colour: the cabin carpet,
     * and where the room has an aisle narrower than itself, reaching {@code aisle} out from the middle, the aisle's
     * runner along both its edges, the path lights along the edge toward the seats. Only open floor is carpeted, round
     * whatever stands on it.
     */
    private void carpet(int fromZ, int toZ, int width, int aisle, DyeColor colour) {
        BlockState carpet = CabinCarpetBlock.carpet(AerolargeRegistry.CABIN_CARPET.get(), colour);
        BlockState runner = CabinCarpetBlock.carpet(AerolargeRegistry.AISLE_CARPET.get(), colour);
        for (int z = fromZ; z <= toZ; z++) {
            for (int x = -width; x <= width; x++) {
                boolean edge = aisle > 0 && aisle < width && Math.abs(x) == aisle;
                layout.furnish(x, floor + 1, z, edge ? runner.setValue(AisleCarpetBlock.FACING, x > 0 ? Direction.EAST : Direction.WEST) : carpet);
            }
        }
    }

    /**
     * The pool: a basin of water flush with the floor, reaching down into the hold, lined with glass mosaic, a lane
     * line down its end walls at the middle of each lane, and lit from its walls just under the water line; a coping
     * round its edge with non-slip deck tiles round that, a diving board at the front and tiled steps out at the
     * back, with a grab rail at either end. It is a moon pool: its bottom is glass, and so is the belly of the hull
     * under it, so swimmers look straight down through the airliner at the ground, or the clouds, below.
     */
    private void pool(int fromZ, int toZ) {
        int width = narrowest(fromZ, toZ);
        int basin = width - 2;
        if (basin < 1 || toZ - fromZ < 6) {
            return;
        }
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState tile = AerolargeRegistry.POOL_MOSAIC.get().defaultBlockState();
        BlockState lane = tile.setValue(PoolMosaicBlock.LINE, true);
        BlockState deck = AerolargeRegistry.POOL_DECK_TILE.get().defaultBlockState();
        // Clear glass, the same as the belly's under it, so the two join without a seam.
        BlockState glass = AerolargeRegistry.HULL_GLASS.get().defaultBlockState();
        for (int z = fromZ; z <= toZ; z++) {
            for (int x = -width; x <= width; x++) {
                boolean inBasin = Math.abs(x) <= basin && z >= fromZ + 2 && z <= toZ - 2;
                boolean rim = !inBasin && Math.abs(x) <= basin + 1 && z >= fromZ + 1 && z <= toZ - 1;
                if (inBasin) {
                    layout.refloor(x, floor, z, water);
                    for (int y = floor - 1; y >= floor - 3 && layout.isOpen(x, y, z); y--) {
                        layout.furnish(x, y, z, water);
                    }
                } else {
                    layout.refloor(x, floor, z, rim ? coping(x, z, basin, fromZ, toZ) : deck);
                }
            }
        }
        // Line the basin: every open block in the hold that touches the water becomes glass under it and mosaic beside
        // it, every fourth one of the walls just under the water line a light, so the water cannot run off into the
        // hold. The lights are cabin lights, so the flight deck dims them with the rest and the pool goes dark at
        // night too.
        BlockState light = AerolargeRegistry.CABIN_LIGHT.get().defaultBlockState();
        for (int z = fromZ + 1; z <= toZ - 1; z++) {
            boolean endWall = z == fromZ + 1 || z == toZ - 1;
            for (int x = -basin - 1; x <= basin + 1; x++) {
                boolean laneMiddle = endWall && Math.abs(x) <= basin && x % LANE_WIDTH == 0;
                for (int y = floor - 4; y < floor; y++) {
                    if (layout.isOpen(x, y, z) && touchesWater(x, y, z)) {
                        boolean lit = y == floor - 1 && Math.floorMod(Math.abs(x) + z, 4) == 0;
                        layout.furnish(x, y, z, isWater(x, y + 1, z) ? glass : lit ? light : laneMiddle ? lane : tile);
                    }
                }
            }
        }
        // The view down: under each column of water, whatever of the hold is still open below the glass bottom is
        // glazed too, and the belly skin it ends on turns to glass, which the shell cuts to the curve with the skin
        // around it. The pool lies between the nose gear and the main gear, so no gear leg meets the belly under it.
        for (int z = fromZ + 2; z <= toZ - 2; z++) {
            for (int x = -basin; x <= basin; x++) {
                int y = floor;
                while (isWater(x, y, z) || layout.stateAt(x, y, z).is(glass.getBlock())) {
                    y--;
                }
                for (; layout.isOpen(x, y, z); y--) {
                    layout.set(x, y, z, glass);
                }
                layout.glaze(x, y, z);
            }
        }
        BlockState board = Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        layout.furnish(0, floor + 1, fromZ + 1, deck);
        layout.furnish(0, floor + 1, fromZ + 2, board);
        layout.furnish(0, floor + 1, fromZ + 3, board);
        // Steps rising toward the back of the pool, out onto the rim, the water over their lower treads; one flight,
        // with a grab rail at either end.
        BlockState step = AerolargeRegistry.POOL_STEPS.get().defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH)
            .setValue(StairBlock.WATERLOGGED, true);
        for (int x = -1; x <= 1; x++) {
            if (isWater(x, floor, toZ - 2)) {
                layout.set(x, floor, toZ - 2, step);
            }
        }
        for (int x = -1; x <= 1; x++) {
            layout.join(x, floor, toZ - 2);
        }
        for (int x : new int[]{-width, width}) {
            layout.furnish(x, floor + 1, fromZ, Blocks.POTTED_FERN.defaultBlockState());
            layout.furnish(x, floor + 1, toZ, Blocks.POTTED_FERN.defaultBlockState());
        }
        for (int z = fromZ + 2; z <= toZ - 2; z += 5) {
            hangLight(-basin / 2, z, 6);
            hangLight(basin / 2, z, 6);
        }
    }

    /**
     * The coping at this spot of the rim round a basin reaching {@code basin} either side of the middle between these
     * stations: its rounded edge out over the water beside it, or a corner piece where the water is only off its corner.
     */
    private static BlockState coping(int x, int z, int basin, int fromZ, int toZ) {
        BlockState coping = AerolargeRegistry.POOL_COPING.get().defaultBlockState();
        boolean side = Math.abs(x) == basin + 1;
        boolean end = z == fromZ + 1 || z == toZ - 1;
        if (side && end) {
            return coping.setValue(PoolCopingBlock.CORNER, true);
        }
        Direction overWater = side ? (x > 0 ? Direction.WEST : Direction.EAST) : z == fromZ + 1 ? Direction.SOUTH : Direction.NORTH;
        return coping.setValue(PoolCopingBlock.FACING, overWater);
    }

    private boolean isWater(int x, int y, int z) {
        return layout.stateAt(x, y, z).is(Blocks.WATER);
    }

    private boolean touchesWater(int x, int y, int z) {
        return isWater(x + 1, y, z) || isWater(x - 1, y, z) || isWater(x, y + 1, z)
            || isWater(x, y, z + 1) || isWater(x, y, z - 1);
    }

    /**
     * The gym hall (Turnhalle): the cabin's whole height over a sprung maple court with white lines, a basketball
     * hoop at each end with its scoreboard above and a rack of balls in the corner by it, wall bars and mats along the
     * port wall, climbing ropes and seats along starboard facing the court, between its windows.
     */
    private void gym(int fromZ, int toZ) {
        int width = narrowest(fromZ, toZ);
        if (width < 3 || toZ - fromZ < 10) {
            return;
        }
        int middle = (fromZ + toZ) / 2;
        BlockState boards = AerolargeRegistry.GYM_FLOOR.get().defaultBlockState();
        BlockState line = boards.setValue(GymFloorBlock.LINE, true);
        double circle = Math.min(3, width - 1);
        for (int z = fromZ; z <= toZ; z++) {
            for (int x = -width; x <= width; x++) {
                boolean boundary = (Math.abs(x) == width - 1 && z >= fromZ + 1 && z <= toZ - 1)
                    || ((z == fromZ + 1 || z == toZ - 1) && Math.abs(x) <= width - 1);
                boolean centre = z == middle || Math.abs(Math.hypot(x, z - middle) - circle) < 0.5;
                layout.refloor(x, floor, z, boundary || centre ? line : boards);
            }
        }

        if (layout.ceiling(0, fromZ) >= floor + 7) {
            hoop(fromZ, Direction.SOUTH, HoopBlock.Team.HOME);
            hoop(toZ, Direction.NORTH, HoopBlock.Team.GUEST);
        }
        BlockState rack = AerolargeRegistry.BALL_RACK.get().defaultBlockState().setValue(BallRackBlock.FACING, Direction.EAST);
        layout.furnish(-width, floor + 1, fromZ + 1, rack);
        layout.furnish(-width, floor + 1, toZ - 1, rack);

        // Wall bars (Sprossenwand): ladders up the port wall, following its curve, with mats in front.
        for (int z = middle - 4; z <= middle + 4; z++) {
            for (int y = floor + 1; y <= floor + 5; y++) {
                int x = layout.openHalfWidth(y, z);
                if (x >= 0) {
                    layout.furnish(-x, y, z, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST));
                }
            }
            for (int x = -width + 1; x <= -width + 3; x++) {
                layout.furnish(x, floor + 1, z, Blocks.BLUE_CARPET.defaultBlockState());
            }
        }

        // Climbing ropes hanging from the crown to just above the floor.
        for (int z = middle - 2; z <= middle + 2; z += 2) {
            int x = width - 3;
            for (int y = layout.ceiling(x, z); y >= floor + 2; y--) {
                layout.furnish(x, y, z, Blocks.CHAIN.defaultBlockState());
            }
        }

        BlockState bench = CabinSeatBlock.seat(AerolargeRegistry.ECONOMY_SEAT.get(), DyeColor.BLUE, Direction.WEST);
        for (int z = fromZ + 3; z <= toZ - 3; z++) {
            if (Math.abs(z - middle) > 4) {
                seat(width, z, bench);
            }
        }

        for (int z = fromZ + 3; z <= toZ - 3; z += 6) {
            hangLight(-width / 2, z, 7);
            hangLight(width / 2, z, 7);
        }
    }

    /**
     * A basketball hoop at the end of the court, scoring for the given side: a pole, a white backboard with the ring
     * on it facing the court, and the scoreboard above.
     */
    private void hoop(int z, Direction toward, HoopBlock.Team team) {
        for (int y = floor + 1; y <= floor + 4; y++) {
            layout.furnish(0, y, z, Blocks.IRON_BARS.defaultBlockState());
        }
        for (int x = -1; x <= 1; x++) {
            layout.furnish(x, floor + 5, z, Blocks.WHITE_CONCRETE.defaultBlockState());
            layout.furnish(x, floor + 6, z, Blocks.WHITE_CONCRETE.defaultBlockState());
        }
        layout.furnish(0, floor + 5, z + toward.getStepZ(),
            AerolargeRegistry.HOOP.get().defaultBlockState().setValue(HoopBlock.FACING, toward.getOpposite()).setValue(HoopBlock.TEAM, team));
        layout.furnish(0, floor + 7, z, AerolargeRegistry.SCOREBOARD.get().defaultBlockState().setValue(ScoreboardBlock.FACING, toward));
    }

    /**
     * The lounge: red carpet throughout, a dark oak bar along the starboard wall with barrels behind and stools in
     * front, the galley that stocks the cabin crew's trolley at its head and a coffee machine on the bar just behind
     * it, a row of red armchairs (business seats) and low tables along the port wall, and a plant in the other corner.
     */
    private void lounge(int fromZ, int toZ) {
        if (toZ - fromZ < 4) {
            return;
        }
        int frontWidth = layout.openHalfWidth(floor + 1, fromZ);
        BlockState armchair = CabinSeatBlock.seat(AerolargeRegistry.BUSINESS_SEAT.get(), DyeColor.RED, Direction.EAST);
        BlockState coffee = AerolargeRegistry.COFFEE_MACHINE.get().defaultBlockState().setValue(CoffeeMachineBlock.FACING, Direction.WEST);
        // The tail cone narrows through the lounge, so it is fitted station by station, following the walls in.
        for (int z = fromZ; z <= toZ; z++) {
            int width = layout.openHalfWidth(floor + 1, z);
            if (width < 3) {
                continue;
            }
            boolean inner = z > fromZ && z < toZ;
            if (inner) {
                layout.furnish(width, floor + 1, z, Blocks.BARREL.defaultBlockState());
                layout.furnish(width - 1, floor + 1, z, Blocks.DARK_OAK_PLANKS.defaultBlockState());
                if ((z - fromZ) % 2 == 1) {
                    layout.furnish(width - 2, floor + 1, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
                }
                if (z - fromZ == BAR_COFFEE) {
                    layout.furnish(width - 1, floor + 2, z, coffee);
                }
                seat(-width, z, armchair);
                if (Math.floorMod(z - fromZ, 4) == 1 || Math.floorMod(z - fromZ, 4) == 2) {
                    layout.furnish(-width + 2, floor + 1, z, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
                }
            }
            carpet(z, z, width, 0, DyeColor.RED);
        }
        if (frontWidth >= 3) {
            layout.set(-frontWidth, floor + 1, fromZ, Blocks.POTTED_FERN.defaultBlockState());
            layout.set(frontWidth, floor + 1, fromZ, AerolargeRegistry.GALLEY.get().defaultBlockState().setValue(GalleyBlock.FACING, Direction.WEST));
        }
        for (int z = fromZ + 1; z <= toZ; z += 4) {
            hangLight(0, z, 5);
        }
    }

    /**
     * Passenger screens, found from the seats and walls already laid out, so they follow whatever the fit-out holds.
     * Where there are rows of seats, one on the back of each seat that has another right behind it in the next row,
     * facing that passenger. Along the walls away from the rows, one between a pair of main deck windows every
     * {@link #WALL_SPACING} stations at seated eye height, facing into the cabin: never over a window, never by a
     * door, which has no windows beside it, and never right over a seat along the wall (in the gym hall, the lounge),
     * where it would be in the head of whoever sits there.
     */
    private void screens() {
        NavigableMap<Integer, Set<Integer>> rows = seatRows();
        int reach = (int) Math.ceil(ROW_REACH * layout.scale());
        BlockState screen = AerolargeRegistry.PASSENGER_SCREEN.get().defaultBlockState();
        for (Map.Entry<Integer, Set<Integer>> row : rows.entrySet()) {
            // The row behind is further toward the tail, south.
            Map.Entry<Integer, Set<Integer>> behind = rows.higherEntry(row.getKey());
            if (behind == null || behind.getKey() - row.getKey() > reach) {
                continue;
            }
            for (int x : row.getValue()) {
                if (behind.getValue().contains(x)) {
                    seatBack(x, row.getKey(), behind.getKey(), screen.setValue(PassengerScreenBlock.FACING, Direction.SOUTH));
                }
            }
        }
        int length = (int) Math.ceil(AirframeShape.LENGTH * layout.scale());
        for (int side : new int[]{1, -1}) {
            BlockState facingIn = screen.setValue(PassengerScreenBlock.FACING, side > 0 ? Direction.WEST : Direction.EAST);
            // As if there were one just ahead of the nose, so the first station that fits gets one.
            int last = -WALL_SPACING;
            for (int z = 0; z < length; z++) {
                int x = layout.besideWall(side, floor + EYES, z);
                boolean fits = x != 0 && layout.isOpen(x, floor + EYES, z) && layout.isPlainHull(x + side, floor + EYES, z)
                    && windowAt(side, z - 1) && windowAt(side, z + 1) && !seats.contains(new BlockPos(x, floor + 1, z));
                if (fits && z - last >= WALL_SPACING && rows.subMap(z - reach, true, z + reach, true).isEmpty()) {
                    layout.furnish(x, floor + EYES, z, facingIn);
                    last = z;
                }
            }
        }
    }

    /**
     * The screen on the back of the seat at this station for the seat behind it at that one: at seated eye height
     * just behind whatever stands at the seat's back (a cabin seat's own back is flush with the back of its block), so
     * the screen is set onto the back's rear face; none if that leaves no legroom in front of the seat behind, or if
     * the back does not reach up to eye height to hold it.
     */
    private void seatBack(int x, int z, int behind, BlockState screen) {
        int at = z + 1;
        while (at < behind && solid(x, floor + 1, at)) {
            at++;
        }
        if (at < behind && holdsScreen(x, floor + EYES, at - 1)) {
            layout.furnish(x, floor + EYES, at, screen);
        }
    }

    /** Whether the block at this position fills it whole, unlike a carpet or a slab. */
    private boolean solid(int x, int y, int z) {
        return layout.stateAt(x, y, z).isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    /** Whether a screen facing aft can go onto the back of what is here: a whole block, or a seat facing forward. */
    private boolean holdsScreen(int x, int y, int z) {
        BlockState state = layout.stateAt(x, y, z);
        return solid(x, y, z) || CabinSeatBlock.isBack(state) && state.getValue(CabinSeatBlock.FACING) == Direction.NORTH;
    }

    /** Whether a main deck window is in the wall on this side at this station, at seated eye height. */
    private boolean windowAt(int side, int z) {
        int x = layout.besideWall(side, floor + EYES, z);
        return x != 0 && CabinWindowBlock.isPane(layout.stateAt(x + side, floor + EYES, z));
    }

    /**
     * The rows of seats across the main deck, from the seats the fit-out has put in: by station, where across each
     * seat is, for every station with seats across it, whether side by side or either side of the aisle. Seats one
     * behind the other along a wall, like the gym hall's benches, are no row.
     */
    private NavigableMap<Integer, Set<Integer>> seatRows() {
        NavigableMap<Integer, Set<Integer>> rows = new TreeMap<>();
        for (BlockPos seat : seats) {
            rows.computeIfAbsent(seat.getZ(), z -> new TreeSet<>()).add(seat.getX());
        }
        rows.values().removeIf(row -> row.size() < ROW_SEATS);
        return rows;
    }

    /** A pendant light hung on its cable from the crown, its bottom the given height above the floor (or as low as fits). */
    private void hangLight(int x, int z, int height) {
        int top = layout.ceiling(x, z);
        int bottom = Math.max(floor + 3, Math.min(floor + height, top));
        if (!layout.isOpen(x, top, z)) {
            return;
        }
        for (int y = top; y > bottom; y--) {
            layout.furnish(x, y, z, AerolargeRegistry.LIGHT_CABLE.get().defaultBlockState());
        }
        layout.furnish(x, bottom, z, AerolargeRegistry.PENDANT_LIGHT.get().defaultBlockState());
    }
}
