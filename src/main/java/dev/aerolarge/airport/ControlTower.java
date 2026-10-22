package dev.aerolarge.airport;

import com.simibubi.create.content.contraptions.elevator.ElevatorPulleyBlockEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.airframe.AirframePalette;
import dev.aerolarge.block.MotionSensorBlock;
import dev.aerolarge.block.NavLightBlock;
import dev.aerolarge.block.PilotSeatBlock;
import dev.aerolarge.block.TransponderBlockEntity;
import dev.aerolarge.radar.MarkerColour;
import dev.aerolarge.radar.MarkerIcon;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;

/**
 * An airfield's control tower, as its blueprint lays it down, after the towers at real airports: a slender octagonal
 * shaft of white concrete with light grey pilasters at its corners and a glazed strip up two of its faces, through
 * which its stairs and its lift show; a wider equipment floor with a ribbon of windows round it; and on top the
 * octagonal glass cab, its windows leaning out as they rise (each band a block further out than the one under it, as
 * real cabs lean theirs out against reflections), dark mullions at its corners, a walkway with a railing round its
 * foot, and an overhanging roof carrying a white radar dome, antennas and a flashing red beacon.
 *
 * <p>Inside, through glass sliding doors at its foot that a motion sensor opens, switchback stairs climb round one side of a glass lift core to the
 * equipment floor and on into the cab. The lift is Create's elevator: a cabin under an elevator pulley driven by a
 * creative motor in the top of the core, with a pair of contacts at the lobby and at the equipment floor, an iron door
 * at each that the contact opens while the cabin is there, a button by each to call it, and the cabin's own controls to
 * pick a floor. In the cab a ring of consoles runs round under the windows, with the tower's radio, its radar screen
 * and its transponder (showing the tower on every map as an orange tower) at the front, two seats at them, and a second
 * radar screen at the side.
 *
 * <p>It is laid out facing south, its front (with the door and the radio) to the north, and turned to face whoever
 * built it as it is set in the world: its front two blocks past the block they clicked, its floor in place of the
 * ground there. Blocks are set without updating their neighbours, so a door is not torn down before its other half is
 * in; railings are joined up as they are laid out. Under the shaft, the ground is filled in down to whatever holds it
 * up.
 */
public final class ControlTower {
    /** The shaft's outer octagon, how many blocks across inside it, the lobby floor, and the equipment floor. */
    private static final int SHAFT = 6;
    private static final int INSIDE = 4;
    private static final int OPS_FLOOR = 40;
    /** The equipment floor's octagon, and its rows: a grey sill, two of windows and a white band. */
    private static final int OPS = 7;
    /** The cab: its floor (the walkway's, out to the railing), the knee wall's octagon, and how far its glass leans out. */
    private static final int CAB_FLOOR = OPS_FLOOR + 5;
    private static final int CAB = 7;
    private static final int WALKWAY = 9;
    /** The cab's rows: knee wall, two of upright glass, two leaning a block further out, then the roof. */
    private static final int KNEE = CAB_FLOOR + 1;
    private static final int LEAN = CAB_FLOOR + 4;
    private static final int ROOF = CAB_FLOOR + 6;
    /** The radar dome's radius and the height of its middle, on a short pedestal on the roof. */
    private static final int DOME = 4;
    private static final int DOME_MIDDLE = ROOF + 6;
    /** The mast with the beacon, toward the roof's back corner, and how tall it is under the beacon. */
    private static final int MAST_X = 5;
    private static final int MAST_Z = 4;
    private static final int MAST_HEIGHT = 5;
    /**
     * The lift: its cabin's middle, the cabin three blocks square inside a glass core a block thicker all round; the
     * row the pulley and its motor sit in, at the top of the core under the cab's floor.
     */
    private static final int LIFT_X = -2;
    private static final int PULLEY_Y = OPS_FLOOR + 4;
    /**
     * The stairs: two lanes side by side east of the core, each a flight of five steps the length of the core,
     * turning on a landing across both lanes at either end, five blocks up from the one before.
     */
    private static final int LANE_A = 1;
    private static final int LANE_B = 3;
    private static final int FLIGHT = 5;
    /** How many rows of the glazed strips up the shaft's faces leave solid at its foot and its top. */
    private static final int STRIP_FROM = 4;
    /** How far apart the lights set into the shaft's inner walls are, up it, and the row of the first. */
    private static final int LIGHT_EVERY = 8;
    private static final int FIRST_LIGHT = 3;
    /** The entrance's westmost column: it is four wide, its two sliding doors in the middle. */
    private static final int ENTRANCE_LEFT = -2;
    /** How far down under the shaft the ground is filled in, at most. */
    private static final int FOUNDATION_DEPTH = 16;
    /**
     * How fast the lift's motor turns, in revolutions a minute: about four blocks a second up and down the shaft, as
     * fast as a real tower's lift, where Create's own default would take the cabin half a minute to the top.
     */
    private static final int LIFT_RPM = 64;
    /** How long after it is built the lift is assembled, in ticks: once its motor has the pulley turning. */
    private static final int ASSEMBLE_AFTER = 40;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final BlockState WALL = Blocks.WHITE_CONCRETE.defaultBlockState();
    private static final BlockState TRIM = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState MULLION = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState FLOOR = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState WINDOW = Blocks.GRAY_STAINED_GLASS.defaultBlockState();
    /** The cab's glass: clear, so the controllers see out all round, and anyone below sees them at their consoles. */
    private static final BlockState CAB_GLASS = Blocks.GLASS.defaultBlockState();
    private static final BlockState CORE_GLASS = Blocks.LIGHT_GRAY_STAINED_GLASS.defaultBlockState();
    private static final BlockState LIGHT = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState CONSOLE = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState STEP = Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState();
    private static final BlockState DOME_SHELL = Blocks.SMOOTH_QUARTZ.defaultBlockState();
    private static final BlockState RAILING = Blocks.IRON_BARS.defaultBlockState();
    private static final BlockState FOUNDATION = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState ENTRANCE_GLASS = Blocks.GLASS.defaultBlockState();
    private static final BlockState CANOPY = Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    /** The lifts waiting to be assembled, by level, and when. */
    private static final Map<ServerLevel, List<PendingLift>> PENDING = new IdentityHashMap<>();

    /** Every block of the tower, by where it goes from the shaft's middle at floor level, air included. */
    private final Map<BlockPos, BlockState> cells = new LinkedHashMap<>();
    private BlockPos transponder = BlockPos.ZERO;
    /** Where the steps are, for the head room cut over them. */
    private final List<BlockPos> steps = new ArrayList<>();

    private ControlTower() {
    }

    /** The tower, laid out facing south round the middle of its shaft. */
    public static ControlTower layout() {
        ControlTower tower = new ControlTower();
        tower.shaft();
        tower.entrance();
        tower.lift();
        tower.stairs();
        tower.equipmentFloor();
        tower.cab();
        tower.roof();
        tower.consoles();
        tower.headRoom();
        tower.joinRailings();
        return tower;
    }

    private void set(int x, int y, int z, BlockState state) {
        cells.put(new BlockPos(x, y, z), state);
    }

    /** Whether a column is inside an octagon this many blocks out from the middle, its corners cut at 45 degrees. */
    private static boolean inOctagon(int x, int z, int radius) {
        return Math.abs(x) <= radius && Math.abs(z) <= radius && Math.abs(x) + Math.abs(z) <= radius + radius / 2;
    }

    /** Whether a column is on the edge of that octagon. */
    private static boolean onOctagon(int x, int z, int radius) {
        return inOctagon(x, z, radius) && !inOctagon(x, z, radius - 1);
    }

    /** Whether a column on an octagon's edge is one of its eight corners, where a mullion or a pilaster stands. */
    private static boolean octagonCorner(int x, int z, int radius) {
        int cut = radius / 2;
        return Math.abs(x) == radius && Math.abs(z) == cut || Math.abs(z) == radius && Math.abs(x) == cut;
    }

    /** Every column of the octagon of this radius, edge or not, to lay something in. */
    private static List<int[]> octagon(int radius) {
        List<int[]> columns = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            addOctagonRow(columns, x, radius);
        }
        return columns;
    }

    private static void addOctagonRow(List<int[]> columns, int x, int radius) {
        for (int z = -radius; z <= radius; z++) {
            if (inOctagon(x, z, radius)) {
                columns.add(new int[]{x, z});
            }
        }
    }

    /**
     * The shaft: its floor in place of the ground, then up to the equipment floor walls round a square inside, white
     * with light grey pilasters at the octagon's corners, and a glazed strip up the middle of the faces the stairs and
     * the lift stand against.
     */
    private void shaft() {
        for (int[] column : octagon(SHAFT)) {
            set(column[0], 0, column[1], FLOOR);
            for (int y = 1; y < OPS_FLOOR; y++) {
                set(column[0], y, column[1], inside(column[0], column[1]) ? AIR : shaftWall(column[0], y, column[1]));
            }
        }
    }

    /**
     * Whether a block of the shaft's wall is one of its lights: on the inner face of its side walls, either side of the
     * glazed strip, every few rows up from the lobby, lighting the stairs and the lift.
     */
    private static boolean shaftLight(int x, int y, int z) {
        return Math.abs(x) == INSIDE + 1 && Math.abs(z) == 3 && y >= FIRST_LIGHT && (y - FIRST_LIGHT) % LIGHT_EVERY == 0;
    }

    private static boolean inside(int x, int z) {
        return Math.abs(x) <= INSIDE && Math.abs(z) <= INSIDE;
    }

    private static BlockState shaftWall(int x, int y, int z) {
        if (octagonCorner(x, z, SHAFT)) {
            return TRIM;
        }
        if (shaftLight(x, y, z)) {
            return LIGHT;
        }
        boolean strip = Math.abs(z) <= 1 && Math.abs(x) > INSIDE && y >= STRIP_FROM && y < OPS_FLOOR - STRIP_FROM;
        return strip ? WINDOW : WALL;
    }

    /**
     * The way in at the foot of the front face, set back in the thick wall: a glazed front four blocks wide, a pair of
     * Create's framed glass sliding doors in the middle with fixed glass either side and a glass transom over them, and
     * over the left door a motion sensor (see {@link MotionSensorBlock}) that slides them open for anyone coming or
     * going; a grey canopy over the recess outside.
     */
    private void entrance() {
        int front = -INSIDE - 1;
        for (int x = ENTRANCE_LEFT; x <= ENTRANCE_LEFT + 3; x++) {
            for (int y = 1; y <= 3; y++) {
                set(x, y, front - 1, AIR);
                set(x, y, front, ENTRANCE_GLASS);
            }
            set(x, 4, front - 2, CANOPY);
            set(x, 4, front - 3, CANOPY);
        }
        BlockState door = slidingDoor();
        for (int x : new int[]{ENTRANCE_LEFT + 1, ENTRANCE_LEFT + 2}) {
            // Seen from outside, facing south, the door to the east is the left one: hinged at its own outer side, as a pair.
            BlockState leaf = door.setValue(DoorBlock.HINGE, x == ENTRANCE_LEFT + 2 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
            set(x, 1, front, leaf.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            set(x, 2, front, leaf.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        }
        BlockState sensor = AerolargeRegistry.MOTION_SENSOR.get().defaultBlockState().setValue(MotionSensorBlock.FACING, Direction.NORTH);
        set(ENTRANCE_LEFT + 2, 3, front, sensor);
    }

    /** One of the entrance's sliding glass doors, as laid out facing south; an iron door without Create. */
    private static BlockState slidingDoor() {
        BlockState glass = create("framed_glass_door");
        BlockState door = glass != null ? glass : Blocks.IRON_DOOR.defaultBlockState();
        return door.setValue(DoorBlock.FACING, Direction.SOUTH);
    }

    /** An iron door, as laid out facing south, hinged on one side; its halves are set from it. */
    private static BlockState ironDoor(DoorHingeSide hinge) {
        return Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HINGE, hinge);
    }

    /** A stone button on the wall behind it, pointing the given way out from it. */
    private static BlockState button(Direction facing) {
        return Blocks.POLISHED_BLACKSTONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, facing);
    }

    /**
     * The lift: a glass core round the cabin's shaft from the lobby to the equipment floor, its top row solid with the
     * pulley over the cabin and the motor beside it; at the lobby and the equipment floor a doorway at the front with an
     * iron door, a contact beside it and a button to call the cabin; and the cabin itself, waiting at the lobby: a floor
     * and a lit roof on two posts, its contact facing the lobby's, and its controls.
     */
    private void lift() {
        for (int x = LIFT_X - 2; x <= LIFT_X + 2; x++) {
            liftCoreColumn(x);
        }
        for (int floor : new int[]{0, OPS_FLOOR}) {
            liftLanding(floor);
        }
        liftMachinery();
        liftCabin();
    }

    private void liftCoreColumn(int x) {
        for (int z = -2; z <= 2; z++) {
            boolean wall = Math.abs(x - LIFT_X) == 2 || Math.abs(z) == 2;
            for (int y = 1; y < PULLEY_Y; y++) {
                set(x, y, z, wall ? CORE_GLASS : AIR);
            }
            set(x, PULLEY_Y, z, WALL);
        }
    }

    /** One floor's landing: the doorway with its iron door, the contact beside it and the button on the contact. */
    private void liftLanding(int floor) {
        BlockState iron = ironDoor(DoorHingeSide.RIGHT);
        set(LIFT_X, floor + 1, -2, iron.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(LIFT_X, floor + 2, -2, iron.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // Open over the door, so the pulley above can be reached from the landing.
        set(LIFT_X, floor + 3, -2, AIR);
        BlockState contact = create("redstone_contact");
        if (contact != null) {
            set(LIFT_X - 1, floor + 2, -2, AirframePalette.with(contact, "facing", "south"));
            set(LIFT_X - 1, floor + 2, -3, button(Direction.NORTH));
        }
    }

    /** The pulley over the cabin's middle and the creative motor turning it, in the core's top row. */
    private void liftMachinery() {
        BlockState pulley = create("elevator_pulley");
        BlockState motor = create("creative_motor");
        if (pulley == null || motor == null) {
            return;
        }
        // Facing north, the pulley takes its turning from the east and the west.
        set(LIFT_X, PULLEY_Y, 0, AirframePalette.with(pulley, "facing", "north"));
        set(LIFT_X + 1, PULLEY_Y, 0, AirframePalette.with(motor, "facing", "west"));
    }

    private void liftCabin() {
        for (int x = LIFT_X - 1; x <= LIFT_X + 1; x++) {
            for (int z = -1; z <= 1; z++) {
                set(x, 0, z, Blocks.SMOOTH_STONE.defaultBlockState());
                set(x, 3, z, x == LIFT_X && z == 0 ? LIGHT : FLOOR);
            }
        }
        for (int x : new int[]{LIFT_X - 1, LIFT_X + 1}) {
            set(x, 1, 1, RAILING);
            set(x, 2, 1, RAILING);
        }
        BlockState contact = create("redstone_contact");
        if (contact != null) {
            set(LIFT_X - 1, 2, -1, AirframePalette.with(contact, "facing", "north"));
        }
        BlockState controls = create("contraption_controls");
        if (controls != null) {
            set(LIFT_X + 1, 1, 0, AirframePalette.with(controls, "facing", "west"));
        }
    }

    /** One of Create's blocks by name; null without Create. */
    @Nullable
    private static BlockState create(String name) {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", name));
        return block == Blocks.AIR ? null : block.defaultBlockState();
    }

    /**
     * The stairs: up from the lobby to the cab, flight after flight, lane A rising south and lane B north, each landing
     * across both lanes at the end of a flight, every step with an upturned stair under it so the flights read as solid
     * slabs; and holes in the floors they climb through.
     */
    private void stairs() {
        int y = 0;
        for (int flight = 0; y < CAB_FLOOR; flight++) {
            boolean south = flight % 2 == 0;
            flight(south ? LANE_A : LANE_B, y, south);
            y += FLIGHT;
            landing(y, south ? INSIDE - 1 : -INSIDE + 1);
        }
    }

    /** One flight of steps in a lane, from just above a height, rising toward the south or the north. */
    private void flight(int lane, int from, boolean south) {
        Direction up = south ? Direction.SOUTH : Direction.NORTH;
        for (int step = 0; step < FLIGHT; step++) {
            int z = south ? -2 + step : 2 - step;
            int y = from + 1 + step;
            for (int x = lane; x <= lane + 1; x++) {
                set(x, y, z, STEP.setValue(StairBlock.FACING, up));
                if (step > 0) {
                    set(x, y - 1, z, STEP.setValue(StairBlock.FACING, up.getOpposite()).setValue(StairBlock.HALF, Half.TOP));
                }
                steps.add(new BlockPos(x, y, z));
            }
        }
    }

    /**
     * Head room over the steps, once the floors are laid: the floors and the core's top they climb through are cut away
     * over them.
     */
    private void headRoom() {
        for (BlockPos step : steps) {
            for (int over = 1; over <= 3; over++) {
                BlockPos above = step.above(over);
                BlockState there = cells.get(above);
                if (there != null && (there.is(FLOOR.getBlock()) || there.is(WALL.getBlock()))) {
                    cells.put(above, AIR);
                }
            }
        }
    }

    /** A landing across both lanes at a flight's end. */
    private void landing(int y, int z) {
        for (int x = LANE_A; x <= LANE_B + 1; x++) {
            set(x, y, z, FLOOR);
        }
    }

    /**
     * The equipment floor: its floor out to its wider octagon, then a grey sill, two rows of windows between white
     * mullions at the corners, and a white band, round the lift core and the stairs, lit from its ceiling.
     */
    private void equipmentFloor() {
        for (int[] column : octagon(OPS)) {
            int x = column[0];
            int z = column[1];
            keepOrSet(x, OPS_FLOOR, z, FLOOR);
            boolean edge = onOctagon(x, z, OPS);
            for (int y = OPS_FLOOR + 1; y < CAB_FLOOR; y++) {
                if (edge) {
                    set(x, y, z, opsWall(x, y, z));
                } else {
                    keepOrSet(x, y, z, AIR);
                }
            }
        }
        for (int[] light : new int[][]{{-5, -3}, {-5, 3}, {4, -5}}) {
            set(light[0], CAB_FLOOR, light[1], LIGHT);
        }
    }

    /** Sets a block where the stairs and the lift have not put one already. */
    private void keepOrSet(int x, int y, int z, BlockState state) {
        cells.putIfAbsent(new BlockPos(x, y, z), state);
    }

    private static BlockState opsWall(int x, int y, int z) {
        if (y == OPS_FLOOR + 1) {
            return TRIM;
        }
        if (y == CAB_FLOOR - 1) {
            return WALL;
        }
        return octagonCorner(x, z, OPS) ? WALL : WINDOW;
    }

    /**
     * The cab: its floor out to the walkway's railing; round the octagon the grey knee wall, two rows of glass, then
     * two more a block further out, leaning the windows out as they rise, dark mullions at every corner of both; and
     * the railing of iron bars round the walkway at its foot.
     */
    private void cab() {
        for (int[] column : octagon(WALKWAY)) {
            int x = column[0];
            int z = column[1];
            keepOrSet(x, CAB_FLOOR, z, FLOOR);
            if (onOctagon(x, z, WALKWAY)) {
                set(x, KNEE, z, RAILING);
            }
            cabColumn(x, z);
        }
    }

    private void cabColumn(int x, int z) {
        for (int y = KNEE; y < ROOF; y++) {
            boolean upper = y >= LEAN;
            int radius = upper ? CAB + 1 : CAB;
            if (onOctagon(x, z, radius)) {
                set(x, y, z, y == KNEE ? TRIM : octagonCorner(x, z, radius) ? MULLION : CAB_GLASS);
            } else if (inOctagon(x, z, radius)) {
                keepOrSet(x, y, z, AIR);
            }
        }
    }

    /**
     * The roof: out over the leaning windows and the walkway, its rim grey, lights set in its ceiling over the cab; on it
     * a white radar dome on a pedestal, antennas round it, and the mast with the red beacon on top.
     */
    private void roof() {
        for (int[] column : octagon(WALKWAY)) {
            set(column[0], ROOF, column[1], onOctagon(column[0], column[1], WALKWAY) ? TRIM : WALL);
        }
        for (int[] light : new int[][]{{0, 0}, {-4, -4}, {4, -4}, {-4, 4}, {4, 4}, {0, -5}}) {
            set(light[0], ROOF, light[1], LIGHT);
        }
        for (int y = ROOF + 1; y < DOME_MIDDLE - DOME + 1; y++) {
            set(0, y, 0, TRIM);
        }
        dome();
        for (int[] antenna : new int[][]{{-5, -4}, {-5, 4}, {5, -4}}) {
            for (int y = ROOF + 1; y <= ROOF + 3; y++) {
                set(antenna[0], y, antenna[1], Blocks.LIGHTNING_ROD.defaultBlockState());
            }
        }
        for (int y = ROOF + 1; y <= ROOF + MAST_HEIGHT; y++) {
            set(MAST_X, y, MAST_Z, Blocks.LIGHTNING_ROD.defaultBlockState());
        }
        set(MAST_X, ROOF + MAST_HEIGHT + 1, MAST_Z, AerolargeRegistry.NAV_LIGHT.get().defaultBlockState()
            .setValue(NavLightBlock.FACING, Direction.UP).setValue(NavLightBlock.KIND, NavLightBlock.Kind.BEACON));
    }

    /** The radar dome: a hollow white ball on the pedestal. */
    private void dome() {
        for (int dx = -DOME; dx <= DOME; dx++) {
            for (int dz = -DOME; dz <= DOME; dz++) {
                domeColumn(dx, dz);
            }
        }
    }

    private void domeColumn(int dx, int dz) {
        for (int dy = -DOME; dy <= DOME; dy++) {
            double r = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (r <= DOME + 0.4 && r > DOME - 0.8) {
                set(dx, DOME_MIDDLE + dy, dz, DOME_SHELL);
            }
        }
    }

    /**
     * The consoles: a ring of dark desks under the windows, all round but where the stairs come up; on the front ones,
     * looking into the cab, the tower's radio, its radar screen in the middle and its transponder, with a pilot seat
     * either side facing out over them; and a second radar screen at the west side.
     */
    private void consoles() {
        for (int[] column : octagon(CAB - 1)) {
            if (onOctagon(column[0], column[1], CAB - 1) && !(column[0] >= LANE_A && column[1] > 0)) {
                set(column[0], KNEE, column[1], CONSOLE);
            }
        }
        int front = -(CAB - 1);
        int top = KNEE + 1;
        set(-2, top, front, facing(AerolargeRegistry.TOWER_RADIO.get(), Direction.SOUTH));
        set(0, top, front, facing(AerolargeRegistry.TOWER_RADAR.get(), Direction.SOUTH));
        set(2, top, front, facing(AerolargeRegistry.TRANSPONDER.get(), Direction.SOUTH));
        transponder = new BlockPos(2, top, front);
        set(-(CAB - 1), top, 0, facing(AerolargeRegistry.TOWER_RADAR.get(), Direction.EAST));
        BlockState seat = AerolargeRegistry.PILOT_SEAT.get().defaultBlockState().setValue(PilotSeatBlock.FACING, Direction.NORTH);
        for (int x : new int[]{-1, 1}) {
            set(x, KNEE, front + 1, seat.setValue(PilotSeatBlock.HALF, DoubleBlockHalf.LOWER));
            set(x, KNEE + 1, front + 1, seat.setValue(PilotSeatBlock.HALF, DoubleBlockHalf.UPPER));
        }
    }

    private static BlockState facing(Block block, Direction facing) {
        return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
    }

    /** Joins every railing to the railings and the solid blocks beside it, as it would join up placed by hand. */
    private void joinRailings() {
        for (Map.Entry<BlockPos, BlockState> cell : cells.entrySet()) {
            if (cell.getValue().is(Blocks.IRON_BARS)) {
                cell.setValue(joined(cell.getKey(), cell.getValue()));
            }
        }
    }

    private BlockState joined(BlockPos pos, BlockState bars) {
        BlockState state = bars;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockState next = cells.get(pos.relative(side));
            boolean joins = next != null && (next.is(Blocks.IRON_BARS) || next.isSolid());
            state = state.setValue(sideOf(side), joins);
        }
        return state;
    }

    private static BooleanProperty sideOf(Direction side) {
        return switch (side) {
            case NORTH -> CrossCollisionBlock.NORTH;
            case SOUTH -> CrossCollisionBlock.SOUTH;
            case EAST -> CrossCollisionBlock.EAST;
            default -> CrossCollisionBlock.WEST;
        };
    }

    /**
     * Where the tower lands in the world: its shaft's middle at {@code middle}, turned so that its back lies
     * {@code along} from there; where its floor takes the ground's place; where its transponder, its lift's pulley and
     * motor and its lift's cabin are; how many of its blocks something is in the way of (the floor aside), and the first; and its
     * top.
     */
    public record Placement(
        List<BlockPos> positions, List<BlockState> states, List<BlockPos> floor, BlockPos transponder, BlockPos pulley, BlockPos motor, AABB cabin,
        int blocked, @Nullable BlockPos firstBlocked, int top
    ) {
    }

    public Placement place(ServerLevel level, BlockPos middle, Direction along) {
        // Laid out facing south; turned onto `along`, as the airliner is.
        Rotation rotation = switch (along) {
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        List<BlockPos> positions = new ArrayList<>(cells.size());
        List<BlockState> states = new ArrayList<>(cells.size());
        List<BlockPos> floor = new ArrayList<>();
        int blocked = 0;
        BlockPos firstBlocked = null;
        int top = Integer.MIN_VALUE;
        for (Map.Entry<BlockPos, BlockState> cell : cells.entrySet()) {
            BlockPos pos = middle.offset(cell.getKey().rotate(rotation));
            positions.add(pos);
            states.add(cell.getValue().rotate(level, pos, rotation));
            top = Math.max(top, pos.getY());
            if (cell.getKey().getY() == 0) {
                floor.add(pos);
            } else if (inTheWay(level, pos)) {
                blocked++;
                firstBlocked = firstBlocked == null ? pos : firstBlocked;
            }
        }
        BlockPos pulley = middle.offset(new BlockPos(LIFT_X, PULLEY_Y, 0).rotate(rotation));
        BlockPos motor = middle.offset(new BlockPos(LIFT_X + 1, PULLEY_Y, 0).rotate(rotation));
        AABB cabin = AABB.encapsulatingFullBlocks(middle.offset(new BlockPos(LIFT_X - 1, 0, -1).rotate(rotation)),
            middle.offset(new BlockPos(LIFT_X + 1, 3, 1).rotate(rotation)));
        return new Placement(positions, states, floor, middle.offset(transponder.rotate(rotation)), pulley, motor, cabin, blocked, firstBlocked, top);
    }

    /**
     * How far ahead of the block the blueprint is used on the shaft's middle goes: the canopy over the door then stands
     * two blocks past it.
     */
    public static int middleAhead() {
        return SHAFT + 3;
    }

    private static boolean inTheWay(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos)) {
            return false;
        }
        BlockState there = level.getBlockState(pos);
        return !there.isAir() && !there.canBeReplaced();
    }

    /**
     * Builds the tower as placed: the ground filled in under its shaft first, then every block; its transponder set to
     * show the tower on the maps, the lift's motor set to its speed, its cabin glued together, and the lift assembled a
     * moment later.
     */
    public static void build(ServerLevel level, Placement placement) {
        placement.floor().forEach(pos -> underpin(level, pos.below()));
        for (int i = 0; i < placement.positions().size(); i++) {
            BlockPos pos = placement.positions().get(i);
            if (level.isInWorldBounds(pos)) {
                level.setBlock(pos, placement.states().get(i), FLAGS);
            }
        }
        if (level.getBlockEntity(placement.transponder()) instanceof TransponderBlockEntity transponder) {
            transponder.show(MarkerIcon.TOWER, MarkerColour.ORANGE);
        }
        if (level.getBlockEntity(placement.motor()) instanceof CreativeMotorBlockEntity motor) {
            motor.generatedSpeed.setValue(LIFT_RPM);
        }
        level.addFreshEntity(new SuperGlueEntity(level, placement.cabin()));
        PENDING.computeIfAbsent(level, ignored -> new ArrayList<>()).add(new PendingLift(placement.pulley(), level.getGameTime() + ASSEMBLE_AFTER));
    }

    /** A lift waiting for its motor to get its pulley turning before it is assembled. */
    private record PendingLift(BlockPos pulley, long at) {
    }

    /** Each tick: assembles the lifts whose moment has come, as a player's click on the pulley would. */
    public static void tick(ServerLevel level) {
        List<PendingLift> lifts = PENDING.get(level);
        if (lifts == null) {
            return;
        }
        lifts.removeIf(lift -> assembleIfDue(level, lift));
    }

    private static boolean assembleIfDue(ServerLevel level, PendingLift lift) {
        if (level.getGameTime() < lift.at()) {
            return false;
        }
        if (level.isLoaded(lift.pulley()) && level.getBlockEntity(lift.pulley()) instanceof ElevatorPulleyBlockEntity pulley) {
            pulley.clicked();
        }
        return true;
    }

    public static void drop(ServerLevel level) {
        PENDING.remove(level);
    }

    /** Fills the ground in under a block of the shaft's floor, down to whatever holds it up. */
    private static void underpin(ServerLevel level, BlockPos below) {
        BlockPos.MutableBlockPos pos = below.mutable();
        for (int depth = 0; depth < FOUNDATION_DEPTH && level.isInWorldBounds(pos); depth++, pos.move(Direction.DOWN)) {
            BlockState there = level.getBlockState(pos);
            if (!there.isAir() && !there.canBeReplaced()) {
                return;
            }
            level.setBlock(pos, FOUNDATION, FLAGS);
        }
    }
}
