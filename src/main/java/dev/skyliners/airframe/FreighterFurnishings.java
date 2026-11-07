package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.AtisScreenBlock;
import dev.skyliners.block.BeanBagBlock;
import dev.skyliners.block.BunkBlock;
import dev.skyliners.block.CabinSeatBlock;
import dev.skyliners.block.CargoCrateBlock;
import dev.skyliners.block.CheckInDeskBlock;
import dev.skyliners.block.CrewLockerBlock;
import dev.skyliners.block.DiscoBallBlock;
import dev.skyliners.block.FogMachineBlock;
import dev.skyliners.block.FrankingMachineBlock;
import dev.skyliners.block.GalleyUnitBlock;
import dev.skyliners.block.LabInstrumentBlock;
import dev.skyliners.block.LedPanelBlockEntity;
import dev.skyliners.block.Mount;
import dev.skyliners.block.NeonSignBlock;
import dev.skyliners.block.ParachuteRackBlock;
import dev.skyliners.block.PassengerScreenBlock;
import dev.skyliners.block.PlanterBlock;
import dev.skyliners.block.ScreenStandBlock;
import dev.skyliners.block.ShelfUnitBlock;
import dev.skyliners.block.SnackMachineBlock;
import dev.skyliners.block.SouvenirModelBlock;
import dev.skyliners.block.SpeakerBlock;
import dev.skyliners.block.SuitcaseBlock;
import dev.skyliners.block.TelegraphBlock;
import dev.skyliners.block.TowerRadarBlock;
import dev.skyliners.block.UldContainerBlock;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The freighter's rooms filled out, past their first fittings, with the mod's own furniture, so none stands bare: her
 * cargo hold two baggage containers by her bow, mail sacks and suitcases by its crates, crates stacked two high, a
 * parachute rack, a shelf of boots and a worktop with the zeppelin's franking machine on it; her entry hall crew
 * lockers along its wall, a shelf for boots, a terminal's check-in desk facing her ramp, two benches facing each other
 * with luggage by them, crates come aboard, a snack machine, the weather outside on a tower's weather screen, planters
 * and a screen; her lounge round its beach (see {@link FreighterChillout}) a snack machine, shelves of books with the
 * fleet's models on them, a screen and planters; engineering a workbench corner with a crafting table and a grindstone,
 * a smithing table and an anvil, the zeppelin's engine telegraph by her jump drive, two screens on stands either side
 * of it, shelves of jars and tools, spare-parts crates and barrels, a cauldron of coolant; her crew quarters lockers, a
 * table with two seats a block back from it, bean bags, shelves of books and towels, a suitcase, planters and a med bay
 * corner, a white-blanketed bunk with a vitals screen over its head; and her club four cocktail tables with stools,
 * neon signs on its walls, speakers either side of its bandstand, the rocket club's fog machine, a bean bag and a disco
 * ball.
 *
 * <p>Each piece goes in where it has room, nudged to the nearest free spot near where it is meant to stand, or on the
 * nearest padded wall with room before it (see {@link FreighterWalls}); never across a room's doorway, which stays
 * clear to walk in through, nor on her ramp's hatch, whose row her ramp keeps clear. Laid after her rooms' first
 * fittings and before her Spookie's rounds and her LED strips, which keep round it. Laid out nose north, as the rest of
 * her; none with the server config's {@code freighter.rooms} off.
 */
final class FreighterFurnishings {
    /**
     * Her rooms, each by the bearing of the room wall on its anticlockwise side, in degrees clockwise from her bow: her
     * cargo hold, entry hall, lounge, engineering, crew quarters and club.
     */
    private static final int CARGO_HOLD = 330;
    private static final int ENTRY_HALL = 30;
    private static final int LOUNGE = 90;
    private static final int ENGINEERING = 150;
    private static final int QUARTERS = 210;
    private static final int CLUB = 270;
    /** How wide each room is, round her axis, in degrees. */
    private static final int ROOM_TURN = 60;
    /** How far a piece may be nudged from where it is meant to stand, in blocks, looking for room. */
    private static final int NUDGE = 2;
    /** How far either side of a room's doorway's middle line its way in is kept clear, in blocks. */
    private static final double DOORWAY_HALF = 1.6;
    /** How far out from her axis a room's way in is kept clear, in blocks: through its doorway and a few blocks on. */
    private static final double DOORWAY_TO = FreighterLayout.CORRIDOR + 4;
    /** The rows over her deck a disco ball may hang in, under her ceiling, lowest first. */
    private static final int[] BALL_ROWS = {3, 4};
    /** How far in and out of a wall piece's meant place along her radius it may hang, in blocks. */
    private static final double WALL_REACH = 3;
    /** How far from a table its seats stand, in blocks: a block of leg room between, as in her lounge's booth. */
    private static final int SEAT_OUT = 2;
    /**
     * Her entry hall's two benches: where the first seat of the one by her ramp's rail stands, at a bearing, in
     * degrees, and so far out, in blocks; how many seats each has; and how far apart they face each other, in blocks,
     * two blocks of leg room between.
     */
    private static final double BENCH_BEARING = 68;
    private static final double BENCH_OUT = 13;
    private static final int BENCH_SEATS = 3;
    private static final int BENCHES_APART = 3;

    private final HullLayout layout;
    private final int deck;

    private FreighterFurnishings(HullLayout layout, int deck) {
        this.layout = layout;
        this.deck = deck;
    }

    /** Furnishes the rooms of a freighter's layout whose deck's floor is the row given. */
    static void build(HullLayout layout, int deck) {
        if (SkylinersConfig.FREIGHTER_ROOMS.get()) {
            FreighterFurnishings furnishings = new FreighterFurnishings(layout, deck);
            furnishings.cargoHold();
            furnishings.entryHall();
            furnishings.lounge();
            furnishings.engineering();
            furnishings.quarters();
            furnishings.club();
        }
    }

    /** Her cargo hold: baggage containers by her bow, mail sacks and suitcases, a parachute rack and boots. */
    private void cargoHold() {
        container(350, 19, 1);
        container(10, 19, 2);
        floorPiece(CARGO_HOLD, 18, 12, 1, at -> SkylinersRegistry.MAIL_SACK.get().defaultBlockState());
        floorPiece(CARGO_HOLD, 22, 12, 1, at -> SkylinersRegistry.MAIL_SACK.get().defaultBlockState());
        suitcase(CARGO_HOLD, 345, 12, DyeColor.RED);
        suitcase(CARGO_HOLD, 342, 13, DyeColor.BLUE);
        wallPiece(CARGO_HOLD, 340, 9, 1, facing -> SkylinersRegistry.PARACHUTE_RACK.get().defaultBlockState()
            .setValue(ParachuteRackBlock.FACING, facing));
        wallPiece(CARGO_HOLD, 20, 10, 1, shelf(ShelfUnitBlock.Kind.BOOTS));
        BlockPos post = wallPiece(CARGO_HOLD, 345, 10, 1, facing -> SkylinersRegistry.GALLEY_WORKTOP.get().defaultBlockState()
            .setValue(GalleyUnitBlock.FACING, facing));
        onTop(post, facing -> SkylinersRegistry.FRANKING_MACHINE.get().defaultBlockState().setValue(FrankingMachineBlock.FACING, facing));
        crateStack(CARGO_HOLD, 25, 15);
    }

    /**
     * Her entry hall: crew lockers along its wall, a shelf for boots, a check-in desk facing her ramp, two benches
     * facing each other with luggage by them, crates come aboard, a snack machine, the weather outside on a screen,
     * planters and a passenger screen.
     */
    private void entryHall() {
        for (double out = 10; out <= 16; out += 2) {
            wallPiece(ENTRY_HALL, 34, out, 1, facing -> SkylinersRegistry.CREW_LOCKER.get().defaultBlockState()
                .setValue(CrewLockerBlock.FACING, facing));
        }
        wallPiece(ENTRY_HALL, 36, 19, 1, shelf(ShelfUnitBlock.Kind.BOOTS));
        BlockPos desk = floorPiece(ENTRY_HALL, 42, 18, 1, at -> SkylinersRegistry.CHECK_IN_DESK.get().defaultBlockState()
            .setValue(CheckInDeskBlock.FACING, toward(at)));
        if (desk != null) {
            BlockState placed = layout.stateAt(desk.getX(), desk.getY(), desk.getZ());
            BlockPos beside = desk.relative(placed.getValue(CheckInDeskBlock.FACING).getCounterClockWise());
            if (free(beside, 1) && !inDoorway(beside)) {
                layout.set(beside.getX(), beside.getY(), beside.getZ(), placed);
            }
        }
        BlockPos benches = at(BENCH_BEARING, BENCH_OUT, 1);
        bench(benches, Direction.WEST, Direction.NORTH);
        bench(benches.north(BENCHES_APART), Direction.WEST, Direction.SOUTH);
        suitcase(ENTRY_HALL, 63, 15.5, DyeColor.PURPLE);
        suitcase(ENTRY_HALL, 58, 15, DyeColor.BLUE);
        floorPiece(ENTRY_HALL, 66, 17, 1, at -> SkylinersRegistry.MAIL_SACK.get().defaultBlockState());
        crateStack(ENTRY_HALL, 54, 20);
        snackMachine(ENTRY_HALL, 48, 21);
        wallPiece(ENTRY_HALL, 72, 19, 2, facing -> SkylinersRegistry.ATIS_SCREEN.get().defaultBlockState()
            .setValue(AtisScreenBlock.FACING, facing).setValue(TowerRadarBlock.MOUNT, Mount.WALL));
        planter(ENTRY_HALL, 64, 19, PlanterBlock.Plant.GREENERY);
        planter(ENTRY_HALL, 38, 18, PlanterBlock.Plant.BAMBOO);
        planter(ENTRY_HALL, 35, 22, PlanterBlock.Plant.GREENERY);
        wallPiece(ENTRY_HALL, 60, 18, 2, facing -> SkylinersRegistry.PASSENGER_SCREEN.get().defaultBlockState()
            .setValue(PassengerScreenBlock.FACING, facing));
    }

    /** Her lounge round her beach: a snack machine, shelves with the fleet's models, a screen and planters. */
    private void lounge() {
        snackMachine(LOUNGE, 145, 12);
        Block[] models = {SkylinersRegistry.MODEL_ROCKET.get(), SkylinersRegistry.MODEL_ZEPPELIN.get(), SkylinersRegistry.MODEL_AIRLINER.get()};
        for (int shelf = 0; shelf < models.length; shelf++) {
            Block model = models[shelf];
            BlockPos at = wallPiece(LOUNGE, 128 + shelf * 6, 19, 1,
                shelf(ShelfUnitBlock.Kind.BOOKS));
            onTop(at, facing -> model.defaultBlockState().setValue(SouvenirModelBlock.FACING, facing));
        }
        wallPiece(LOUNGE, 140, 15, 2, facing -> SkylinersRegistry.PASSENGER_SCREEN.get().defaultBlockState()
            .setValue(PassengerScreenBlock.FACING, facing));
        planter(LOUNGE, 100, 17, PlanterBlock.Plant.FLOWERS);
        planter(LOUNGE, 146, 18, PlanterBlock.Plant.GREENERY);
    }

    /**
     * Engineering: a workbench corner, a smithing table and an anvil, the engine telegraph by her drive, shelves of
     * jars and tools, spare-parts crates and barrels, a cauldron of coolant and screens on stands.
     */
    private void engineering() {
        wallPiece(ENGINEERING, 160, 12, 1, facing -> SkylinersRegistry.GALLEY_WORKTOP.get().defaultBlockState()
            .setValue(GalleyUnitBlock.FACING, facing));
        wallPiece(ENGINEERING, 163, 13, 1, facing -> Blocks.CRAFTING_TABLE.defaultBlockState());
        wallPiece(ENGINEERING, 157, 11, 1, facing -> Blocks.GRINDSTONE.defaultBlockState()
            .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR).setValue(BlockStateProperties.HORIZONTAL_FACING, facing));
        floorPiece(ENGINEERING, 172, 11, 1, at -> SkylinersRegistry.ENGINE_TELEGRAPH.get().defaultBlockState()
            .setValue(TelegraphBlock.FACING, toward(at)));
        wallPiece(ENGINEERING, 200, 12, 1, shelf(ShelfUnitBlock.Kind.JARS));
        wallPiece(ENGINEERING, 203, 14, 1, shelf(ShelfUnitBlock.Kind.JARS));
        wallPiece(ENGINEERING, 155, 15, 1, shelf(ShelfUnitBlock.Kind.TOOLS));
        wallPiece(ENGINEERING, 158, 18, 1, facing -> Blocks.SMITHING_TABLE.defaultBlockState());
        floorPiece(ENGINEERING, 165, 16, 1, at -> Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, toward(at).getClockWise()));
        floorPiece(ENGINEERING, 186, 18, 1, at -> Blocks.WATER_CAULDRON.defaultBlockState()
            .setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
        floorPiece(ENGINEERING, 205, 18, 1, at -> Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        floorPiece(ENGINEERING, 208, 17, 1, at -> Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        floorPiece(ENGINEERING, 195, 16, 1, at -> SkylinersRegistry.CARGO_CRATE.get().defaultBlockState()
            .setValue(CargoCrateBlock.FACING, toward(at)));
        floorPiece(ENGINEERING, 198, 17, 1, at -> SkylinersRegistry.CARGO_CRATE.get().defaultBlockState()
            .setValue(CargoCrateBlock.FACING, toward(at).getClockWise()));
        monitor(ENGINEERING, 168, 12);
        monitor(ENGINEERING, 192, 12);
    }

    /** A screen on a stand near where it is meant, at the eyes of whoever stands before it, facing toward her axis. */
    private void monitor(int room, double bearing, double out) {
        BlockPos stand = floorPiece(room, bearing, out, 2, at -> SkylinersRegistry.SCREEN_STAND.get().defaultBlockState()
            .setValue(ScreenStandBlock.FACING, toward(at)));
        onTop(stand, facing -> SkylinersRegistry.PASSENGER_SCREEN.get().defaultBlockState().setValue(PassengerScreenBlock.FACING, facing));
    }

    /**
     * Her crew quarters: lockers, a table with two seats, bean bags, shelves of books and towels, planters and a med
     * bay corner.
     */
    private void quarters() {
        for (double out = 10; out <= 16; out += 2) {
            wallPiece(QUARTERS, 214, out, 1, facing -> SkylinersRegistry.CREW_LOCKER.get().defaultBlockState()
                .setValue(CrewLockerBlock.FACING, facing));
        }
        BlockPos table = floorPiece(QUARTERS, 252, 16, 1, at -> SkylinersRegistry.LOUNGE_TABLE.get().defaultBlockState());
        if (table != null) {
            businessSeat(table.north(SEAT_OUT), Direction.SOUTH);
            businessSeat(table.south(SEAT_OUT), Direction.NORTH);
        }
        floorPiece(QUARTERS, 232, 12, 1, at -> SkylinersRegistry.BEAN_BAG.get().defaultBlockState()
            .setValue(BeanBagBlock.FACING, toward(at)).setValue(BeanBagBlock.COLOR, DyeColor.PURPLE));
        wallPiece(QUARTERS, 263, 13, 1, shelf(ShelfUnitBlock.Kind.BOOKS));
        wallPiece(QUARTERS, 265, 16, 1, shelf(ShelfUnitBlock.Kind.BOOKS));
        suitcase(QUARTERS, 256, 11, DyeColor.GREEN);
        planter(QUARTERS, 226, 13, PlanterBlock.Plant.FLOWERS);
        wallPiece(QUARTERS, 232, 18, 1, shelf(ShelfUnitBlock.Kind.TOWELS));
        floorPiece(QUARTERS, 240, 15, 1, at -> SkylinersRegistry.BEAN_BAG.get().defaultBlockState()
            .setValue(BeanBagBlock.FACING, toward(at)).setValue(BeanBagBlock.COLOR, DyeColor.LIGHT_BLUE));
        planter(QUARTERS, 246, 19, PlanterBlock.Plant.GREENERY);
        medBay();
    }

    /**
     * Her med bay corner: a bunk with a white blanket, its light on, its head against the wall, where its foot has room
     * too, and a vitals screen over its head.
     */
    private void medBay() {
        BlockPos head = wallPiece(QUARTERS, 222, 17, 1, 1, 1, this::footFits, FreighterFurnishings::bunk);
        if (head == null) {
            return;
        }
        BlockState placed = layout.stateAt(head.getX(), head.getY(), head.getZ());
        BlockPos foot = footOf(head, placed.getValue(BunkBlock.FACING));
        layout.set(foot.getX(), foot.getY(), foot.getZ(), placed.setValue(BunkBlock.PART, BedPart.FOOT));
        BlockPos screen = head.above();
        if (layout.isOpen(screen.getX(), screen.getY(), screen.getZ())) {
            layout.set(screen.getX(), screen.getY(), screen.getZ(), SkylinersRegistry.LAB_INSTRUMENT.get()
                .of(LabInstrumentBlock.Kind.VITALS, placed.getValue(BunkBlock.FACING)));
        }
    }

    /** A shelf unit stocked with what is given, facing the way given, out from its wall. */
    private static Function<Direction, BlockState> shelf(ShelfUnitBlock.Kind kind) {
        return facing -> ShelfUnitBlock.stocked(SkylinersRegistry.SHELF_UNIT.get(), kind, facing);
    }

    /** The med bay's bunk's head, facing the way given, out from its wall. */
    private static BlockState bunk(Direction facing) {
        return SkylinersRegistry.BUNK.get().defaultBlockState().setValue(BunkBlock.FACING, facing).setValue(BunkBlock.COLOR, DyeColor.WHITE)
            .setValue(BunkBlock.LIT, true).setValue(BunkBlock.TIER, BunkBlock.Tier.SINGLE);
    }

    /** Where a bunk's foot is, its head at a block against its wall, facing out from the wall behind it. */
    private BlockPos footOf(BlockPos head, Direction facing) {
        return head.relative(BunkBlock.footward(facing));
    }

    /** Whether a bunk's head at a block against her wall would have room for its foot, in her crew quarters. */
    private boolean footFits(BlockPos head) {
        Direction behind = FreighterWalls.wallBehind(layout, head);
        if (behind == null) {
            return false;
        }
        BlockPos foot = footOf(head, behind.getOpposite());
        return free(foot, 1) && inRoom(QUARTERS, foot) && !inDoorway(foot);
    }

    /**
     * Her club: cocktail tables with stools, neon signs, speakers by the bandstand, the fog machine and a disco ball.
     */
    private void club() {
        cocktailTable(CLUB, 312, 12);
        cocktailTable(CLUB, 288, 12);
        cocktailTable(CLUB, 318, 17);
        cocktailTable(CLUB, 282, 17);
        NeonSignBlock.Design[] designs = {NeonSignBlock.Design.COCKTAIL, NeonSignBlock.Design.NOTE, NeonSignBlock.Design.PLANET,
            NeonSignBlock.Design.ROCKET};
        for (int sign = 0; sign < designs.length; sign++) {
            NeonSignBlock.Design design = designs[sign];
            wallPiece(CLUB, 276 + sign * 14, 13 + sign % 2 * 4, 2, facing -> SkylinersRegistry.NEON_SIGN.get().defaultBlockState()
                .setValue(NeonSignBlock.FACING, facing).setValue(NeonSignBlock.DESIGN, design).setValue(NeonSignBlock.LIT, true));
        }
        speakerStack(292, 16);
        speakerStack(308, 16);
        floorPiece(CLUB, 304, 14, 1, at -> SkylinersRegistry.FOG_MACHINE.get().defaultBlockState()
            .setValue(FogMachineBlock.FACING, toward(at)));
        discoBall(300, 11);
        floorPiece(CLUB, 322, 15, 1, at -> SkylinersRegistry.BEAN_BAG.get().defaultBlockState()
            .setValue(BeanBagBlock.FACING, toward(at)).setValue(BeanBagBlock.COLOR, DyeColor.PINK));
    }

    /** A baggage container with its door toward her axis, its origin corner as near as it fits to where it is meant. */
    private void container(double bearing, double out, int number) {
        BlockPos meant = at(bearing, out, 1);
        BlockState state = SkylinersRegistry.ULD_CONTAINER.get().defaultBlockState().setValue(UldContainerBlock.FACING, toward(meant))
            .setValue(UldContainerBlock.NUMBER, number % UldContainerBlock.NUMBER.getPossibleValues().size());
        for (BlockPos origin : BlockPos.betweenClosed(meant.offset(-NUDGE, 0, -NUDGE), meant.offset(NUDGE, 0, NUDGE))) {
            Map<BlockPos, BlockState> corners = UldContainerBlock.corners(origin.immutable(), state);
            if (corners.keySet().stream().allMatch(this::containerFits)) {
                corners.forEach((corner, part) -> layout.set(corner.getX(), corner.getY(), corner.getZ(), part));
                return;
            }
        }
    }

    /** Whether a corner of a container fits: open, out of a doorway, and on her floor if it is a lower one. */
    private boolean containerFits(BlockPos corner) {
        boolean lower = corner.getY() == deck + 1;
        return rowsOpen(corner, 1) && (!lower || standsOnFloor(corner)) && !inDoorway(corner);
    }

    /** A planter, its plant as given, near where it is meant to stand, facing toward her axis. */
    private void planter(int room, double bearing, double out, PlanterBlock.Plant plant) {
        floorPiece(room, bearing, out, 1, at -> PlanterBlock.planted(SkylinersRegistry.PLANTER.get(), plant, toward(at)));
    }

    /** A business seat, both halves, at a block, facing the way given, where it has room. */
    private void businessSeat(BlockPos at, Direction facing) {
        if (free(at, 2) && !inDoorway(at)) {
            BlockState seat = CabinSeatBlock.seat(SkylinersRegistry.BUSINESS_SEAT.get(), DyeColor.BLUE, facing);
            layout.set(at.getX(), at.getY(), at.getZ(), seat);
            layout.set(at.getX(), at.getY() + 1, at.getZ(), seat.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        }
    }

    /** A cocktail table near where it is meant, a bar stool either side of it where they have room. */
    private void cocktailTable(int room, double bearing, double out) {
        BlockPos table = floorPiece(room, bearing, out, 1, at -> SkylinersRegistry.COCKTAIL_TABLE.get().defaultBlockState());
        if (table == null) {
            return;
        }
        for (BlockPos stool : new BlockPos[]{table.east(), table.west()}) {
            if (free(stool, 1) && !inDoorway(stool)) {
                layout.set(stool.getX(), stool.getY(), stool.getZ(), SkylinersRegistry.BAR_STOOL.get().defaultBlockState());
            }
        }
    }

    /** The snack machine, two blocks tall, against the nearest wall with room to stand before it. */
    private void snackMachine(int room, double bearing, double out) {
        BlockPos lower = wallPiece(room, bearing, out, 1, 2, 2, at -> true,
            facing -> SnackMachineBlock.lower(SkylinersRegistry.SNACK_MACHINE.get(), facing));
        if (lower != null) {
            BlockState placed = layout.stateAt(lower.getX(), lower.getY(), lower.getZ());
            layout.set(lower.getX(), lower.getY() + 1, lower.getZ(), SnackMachineBlock.upper(placed));
        }
    }

    /** A stack of two speakers near where it is meant, facing toward her axis. */
    private void speakerStack(double bearing, double out) {
        BlockPos at = floorPiece(CLUB, bearing, out, 2, spot -> SkylinersRegistry.SPEAKER.get().defaultBlockState()
            .setValue(SpeakerBlock.FACING, toward(spot)));
        if (at != null) {
            layout.set(at.getX(), at.getY() + 1, at.getZ(), layout.stateAt(at.getX(), at.getY(), at.getZ()));
        }
    }

    /** A disco ball, lit, hanging under her ceiling over where it is meant, if a ceiling is there over the room. */
    private void discoBall(double bearing, double out) {
        BlockPos floor = at(bearing, out, 1);
        if (!inRoom(CLUB, floor) || inDoorway(floor)) {
            return;
        }
        for (int up : BALL_ROWS) {
            BlockPos ball = floor.above(up - 1);
            boolean hangs = layout.isOpen(ball.getX(), ball.getY(), ball.getZ()) && !layout.isOpen(ball.getX(), ball.getY() + 1, ball.getZ());
            if (hangs) {
                layout.set(ball.getX(), ball.getY(), ball.getZ(), SkylinersRegistry.DISCO_BALL.get().defaultBlockState()
                    .setValue(DiscoBallBlock.LIT, true));
                return;
            }
        }
    }

    /**
     * A bench of economy seats side by side, from a block on along one way, all facing another, each where it has room
     * for both its halves.
     */
    private void bench(BlockPos first, Direction along, Direction facing) {
        BlockState seat = CabinSeatBlock.seat(SkylinersRegistry.ECONOMY_SEAT.get(), DyeColor.LIGHT_GRAY, facing);
        for (int place = 0; place < BENCH_SEATS; place++) {
            BlockPos at = first.relative(along, place);
            if (free(at, 2) && !inDoorway(at)) {
                layout.set(at.getX(), at.getY(), at.getZ(), seat);
                layout.set(at.getX(), at.getY() + 1, at.getZ(), seat.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
            }
        }
    }

    /** Two cargo crates, one on the other turned a quarter, near where they are meant to stand. */
    private void crateStack(int room, double bearing, double out) {
        BlockPos stack = floorPiece(room, bearing, out, 2, at -> SkylinersRegistry.CARGO_CRATE.get().defaultBlockState()
            .setValue(CargoCrateBlock.FACING, toward(at)));
        onTop(stack, facing -> SkylinersRegistry.CARGO_CRATE.get().defaultBlockState().setValue(CargoCrateBlock.FACING, facing.getClockWise()));
    }

    /** A suitcase in a colour near where it is meant to stand, facing toward her axis. */
    private void suitcase(int room, double bearing, double out, DyeColor colour) {
        floorPiece(room, bearing, out, 1, at -> SkylinersRegistry.SUITCASE.get().defaultBlockState()
            .setValue(SuitcaseBlock.FACING, toward(at)).setValue(SuitcaseBlock.COLOR, colour));
    }

    /** On top of a piece just set, a piece facing as it does; nothing where it was not set or there is no room. */
    private void onTop(@Nullable BlockPos under, Function<Direction, BlockState> piece) {
        if (under == null || !layout.isOpen(under.getX(), under.getY() + 1, under.getZ())) {
            return;
        }
        BlockState below = layout.stateAt(under.getX(), under.getY(), under.getZ());
        Direction facing = below.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? below.getValue(BlockStateProperties.HORIZONTAL_FACING)
            : Direction.NORTH;
        layout.set(under.getX(), under.getY() + 1, under.getZ(), piece.apply(facing));
    }

    /**
     * A piece standing on her floor at the nearest free block to where it is meant, at a bearing and so far out, with
     * so many rows open over the floor there; gives where it went, or null where nothing near had room.
     */
    @Nullable
    private BlockPos floorPiece(int room, double bearing, double out, int rows, Function<BlockPos, BlockState> piece) {
        BlockPos meant = at(bearing, out, 1);
        BlockPos best = null;
        for (BlockPos near : BlockPos.betweenClosed(meant.offset(-NUDGE, 0, -NUDGE), meant.offset(NUDGE, 0, NUDGE))) {
            boolean nearer = best == null || near.distSqr(meant) < best.distSqr(meant);
            if (nearer && inRoom(room, near) && free(near, rows) && !inDoorway(near)) {
                best = near.immutable();
            }
        }
        if (best != null) {
            layout.set(best.getX(), best.getY(), best.getZ(), piece.apply(best));
        }
        return best;
    }

    /** A piece on the nearest padded wall to where it is meant, so many rows over her deck, with a block before it. */
    @Nullable
    private BlockPos wallPiece(int room, double bearing, double out, int up, Function<Direction, BlockState> piece) {
        return wallPiece(room, bearing, out, up, 1, 1, at -> true, piece);
    }

    /**
     * A piece on the nearest padded wall to where it is meant, so many rows over her deck, with so many blocks open
     * before it and so many rows open from it up, out of a doorway and on her floor if it stands on it, and passing
     * the test given, facing out from the wall; gives where it went, or null where no wall near had room.
     */
    @Nullable
    private BlockPos wallPiece(int room, double bearing, double out, int up, int before, int rows, Predicate<BlockPos> also,
        Function<Direction, BlockState> piece) {
        LedPanelBlockEntity.Wedge wedge = new LedPanelBlockEntity.Wedge(BlockPos.ZERO, room, room + ROOM_TURN);
        double turn = Math.toRadians(bearing);
        Predicate<BlockPos> fits = spot -> (up != 1 || standsOnFloor(spot)) && rowsOpen(spot, rows) && !inDoorway(spot) && also.test(spot);
        BlockPos at = FreighterWalls.nearest(layout, deck + up, wedge, out - WALL_REACH, out + WALL_REACH, Math.sin(turn) * out,
            -Math.cos(turn) * out, before, fits);
        Direction behind = at == null ? null : FreighterWalls.wallBehind(layout, at);
        if (behind == null) {
            return null;
        }
        layout.set(at.getX(), at.getY(), at.getZ(), piece.apply(behind.getOpposite()));
        return at;
    }

    /**
     * The block a bearing, in degrees, and a distance out from her axis, in blocks, falls in, so many rows over her
     * deck.
     */
    private BlockPos at(double bearing, double out, int up) {
        double turn = Math.toRadians(bearing);
        return new BlockPos((int) Math.round(Math.sin(turn) * out), deck + up, (int) Math.round(-Math.cos(turn) * out));
    }

    /** The way from a block toward her axis, the way a piece standing in a room faces into it. */
    private static Direction toward(BlockPos at) {
        return Direction.getNearest(-at.getX(), 0, -at.getZ());
    }

    /** Whether a block lies in a room: in its wedge round her axis, past the open floor about her core. */
    static boolean inRoom(int room, BlockPos at) {
        LedPanelBlockEntity.Wedge wedge = new LedPanelBlockEntity.Wedge(BlockPos.ZERO, room, room + ROOM_TURN);
        return Math.hypot(at.getX(), at.getZ()) >= FreighterLayout.CORRIDOR && wedge.contains(at);
    }

    /** Whether a block over her deck's floor has the floor under it and so many rows open from it up. */
    private boolean free(BlockPos at, int rows) {
        return standsOnFloor(at) && rowsOpen(at, rows);
    }

    /** Whether a block is the row over her deck's floor, with the floor under it. */
    private boolean standsOnFloor(BlockPos at) {
        return at.getY() == deck + 1 && !layout.isOpen(at.getX(), deck, at.getZ());
    }

    /** Whether so many rows from a block up are open. */
    private boolean rowsOpen(BlockPos at, int rows) {
        for (int row = 0; row < rows; row++) {
            if (!layout.isOpen(at.getX(), at.getY() + row, at.getZ())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a block lies in a room's way in: its middle near the middle line of the room's doorway, from the ring out
     * into the room, measured round the arc at its distance out, near enough the straight distance so close in.
     */
    static boolean inDoorway(BlockPos at) {
        double x = at.getX() + 0.5;
        double z = at.getZ() + 0.5;
        double out = Math.hypot(x, z);
        if (out > DOORWAY_TO) {
            return false;
        }
        double bearing = Math.toDegrees(Math.atan2(x, -z));
        double phase = ((bearing % ROOM_TURN) + ROOM_TURN) % ROOM_TURN;
        double aside = Math.min(phase, ROOM_TURN - phase);
        return Math.toRadians(aside) * out < DOORWAY_HALF;
    }
}
