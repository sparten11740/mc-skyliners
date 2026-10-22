package dev.aerolarge.airport;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.NavLightBlock;
import dev.aerolarge.block.PilotSeatBlock;
import dev.aerolarge.block.TransponderBlockEntity;
import dev.aerolarge.radar.MarkerColour;
import dev.aerolarge.radar.MarkerIcon;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * An airfield's control tower, as its blueprint lays it down: a white shaft seven blocks square and twenty tall, banded
 * in grey, with slit windows up each side, a door at its foot facing whoever built it and a ladder up the inside to a
 * hatch; on top of it the glass cab, wider than the shaft, windows all round over a knee wall, its roof overhanging
 * them. In the cab a desk runs along the front windows with the tower's radio, its radar screen and its transponder on
 * it, showing the tower on every map by a tower in orange, two pilot seats to sit at it, and lights in the ceiling; on
 * the roof a mast with a flashing red beacon on top.
 *
 * <p>It is laid out facing south, its front (with the door and the desk) to the north, and turned to face whoever
 * built it as it is set in the world: its front wall two blocks past the block they clicked, its floor in place of the
 * ground there. Blocks are set without updating their neighbours, so a door is not torn down before its other half
 * is in; under the shaft, the ground is filled in down to whatever holds it up.
 */
public final class ControlTower {
    /** The shaft: its half width, its depth from the front, its top row, and the bands round it every so many rows. */
    private static final int SHAFT_HALF = 3;
    private static final int SHAFT_BACK = 6;
    private static final int SHAFT_TOP = 20;
    private static final int BAND = 5;
    /** The rows of each band of slit windows up the shaft. */
    private static final int[] WINDOW_ROWS = {7, 8, 12, 13, 17, 18};
    /** The rows of the shaft's lights, set into its side walls. */
    private static final int[] LIGHT_ROWS = {4, 9, 14, 19};
    /** The cab: its half width, its front and back, its floor, the knee wall's row, and its roof. */
    private static final int CAB_HALF = 5;
    private static final int CAB_FRONT = -2;
    private static final int CAB_BACK = 8;
    private static final int CAB_FLOOR = SHAFT_TOP + 1;
    private static final int KNEE_WALL = CAB_FLOOR + 1;
    private static final int CAB_ROOF = CAB_FLOOR + 5;
    /** The ladder's column inside the shaft, against its back wall, and the hatch over it in the cab's floor. */
    private static final int LADDER_Z = SHAFT_BACK - 1;
    /** The desk along the front windows: its row, its half length, and the row the seats stand in behind it. */
    private static final int DESK_Z = CAB_FRONT + 1;
    private static final int DESK_HALF = 3;
    private static final int SEAT_Z = DESK_Z + 1;
    private static final int SEAT_X = 2;
    /** The mast on the roof, toward its back corner, and how tall it is under the beacon. */
    private static final int MAST_X = 3;
    private static final int MAST_Z = 6;
    private static final int MAST_HEIGHT = 4;
    /** How far down under the shaft the ground is filled in, at most. */
    private static final int FOUNDATION_DEPTH = 16;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final BlockState WALL = Blocks.WHITE_CONCRETE.defaultBlockState();
    private static final BlockState TRIM = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState FLOOR = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState GLASS = Blocks.GLASS.defaultBlockState();
    private static final BlockState LIGHT = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState DESK = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState FOUNDATION = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /** Every block of the tower, by where it goes from the front wall's middle at floor level, air included. */
    private final Map<BlockPos, BlockState> cells = new LinkedHashMap<>();
    /** Where its transponder goes. */
    private BlockPos transponder = BlockPos.ZERO;

    private ControlTower() {
    }

    /** The tower, laid out facing south. */
    public static ControlTower layout() {
        ControlTower tower = new ControlTower();
        tower.shaft();
        tower.cab();
        tower.roof();
        tower.desk();
        return tower;
    }

    private void set(int x, int y, int z, BlockState state) {
        cells.put(new BlockPos(x, y, z), state);
    }

    /** Whether a column is on the edge of a rectangle from (x0, z0) to (x1, z1). */
    private static boolean onEdge(int x, int z, int x0, int z0, int x1, int z1) {
        return x == x0 || x == x1 || z == z0 || z == z1;
    }

    /**
     * The shaft: its floor in place of the ground, its walls (banded, with slit windows in the middle of each side and
     * lights set into its side walls) round air, the door at the front, and the ladder up its back wall.
     */
    private void shaft() {
        for (int x = -SHAFT_HALF; x <= SHAFT_HALF; x++) {
            for (int z = 0; z <= SHAFT_BACK; z++) {
                shaftColumn(x, z);
            }
        }
        BlockState door = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
        set(0, 1, 0, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(0, 2, 0, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        for (int y = 1; y <= SHAFT_TOP; y++) {
            set(0, y, LADDER_Z, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        }
    }

    /** One column of the shaft, from its floor to its top: wall round the edge, air inside. */
    private void shaftColumn(int x, int z) {
        set(x, 0, z, FLOOR);
        boolean edge = onEdge(x, z, -SHAFT_HALF, 0, SHAFT_HALF, SHAFT_BACK);
        for (int y = 1; y <= SHAFT_TOP; y++) {
            set(x, y, z, edge ? shaftWall(x, y, z) : AIR);
        }
    }

    /** The shaft's wall at a spot: a band every few rows, a slit window or a light in the middle of a side, or wall. */
    private static BlockState shaftWall(int x, int y, int z) {
        boolean middleOfSide = x == 0 || z == SHAFT_BACK / 2;
        boolean corner = Math.abs(x) == SHAFT_HALF && (z == 0 || z == SHAFT_BACK);
        if (middleOfSide && !corner && contains(WINDOW_ROWS, y)) {
            return GLASS;
        }
        if (Math.abs(x) == SHAFT_HALF && z == SHAFT_BACK / 2 && contains(LIGHT_ROWS, y)) {
            return LIGHT;
        }
        return y % BAND == 0 ? TRIM : WALL;
    }

    private static boolean contains(int[] rows, int y) {
        for (int row : rows) {
            if (row == y) {
                return true;
            }
        }
        return false;
    }

    /**
     * The cab: its floor over the shaft with the hatch over the ladder, then round its edge the knee wall and the
     * windows over it, with a post at each corner, round air.
     */
    private void cab() {
        for (int x = -CAB_HALF; x <= CAB_HALF; x++) {
            for (int z = CAB_FRONT; z <= CAB_BACK; z++) {
                cabColumn(x, z);
            }
        }
        set(0, CAB_FLOOR, LADDER_Z, Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
            .setValue(TrapDoorBlock.FACING, Direction.NORTH).setValue(TrapDoorBlock.HALF, Half.TOP));
    }

    /** One column of the cab, from its floor up to under its roof. */
    private void cabColumn(int x, int z) {
        set(x, CAB_FLOOR, z, FLOOR);
        boolean edge = onEdge(x, z, -CAB_HALF, CAB_FRONT, CAB_HALF, CAB_BACK);
        boolean corner = Math.abs(x) == CAB_HALF && (z == CAB_FRONT || z == CAB_BACK);
        for (int y = KNEE_WALL; y < CAB_ROOF; y++) {
            BlockState state = !edge ? AIR : y == KNEE_WALL || corner ? WALL : GLASS;
            set(x, y, z, state);
        }
    }

    /**
     * The roof: over the cab and a block past it all round, its overhang grey, lights set into it over the cab; and on
     * it the mast of lightning rods with the red beacon on top.
     */
    private void roof() {
        for (int x = -CAB_HALF - 1; x <= CAB_HALF + 1; x++) {
            for (int z = CAB_FRONT - 1; z <= CAB_BACK + 1; z++) {
                boolean overhang = Math.abs(x) > CAB_HALF || z < CAB_FRONT || z > CAB_BACK;
                set(x, CAB_ROOF, z, overhang ? TRIM : WALL);
            }
        }
        int middle = (CAB_FRONT + CAB_BACK) / 2;
        for (int[] light : new int[][]{{0, middle}, {-3, DESK_Z + 1}, {3, DESK_Z + 1}, {-3, CAB_BACK - 2}, {3, CAB_BACK - 2}}) {
            set(light[0], CAB_ROOF, light[1], LIGHT);
        }
        for (int y = 1; y <= MAST_HEIGHT; y++) {
            set(MAST_X, CAB_ROOF + y, MAST_Z, Blocks.LIGHTNING_ROD.defaultBlockState());
        }
        set(MAST_X, CAB_ROOF + MAST_HEIGHT + 1, MAST_Z, AerolargeRegistry.NAV_LIGHT.get().defaultBlockState()
            .setValue(NavLightBlock.FACING, Direction.UP).setValue(NavLightBlock.KIND, NavLightBlock.Kind.BEACON));
    }

    /**
     * The desk along the front windows, and on it, facing into the cab, the tower's radio, its radar screen in the
     * middle and its transponder; behind it a pilot seat either side of the radar, facing the windows.
     */
    private void desk() {
        for (int x = -DESK_HALF; x <= DESK_HALF; x++) {
            set(x, KNEE_WALL, DESK_Z, DESK);
        }
        int top = KNEE_WALL + 1;
        set(-SEAT_X, top, DESK_Z, facingIn(AerolargeRegistry.TOWER_RADIO.get()));
        set(0, top, DESK_Z, facingIn(AerolargeRegistry.TOWER_RADAR.get()));
        set(SEAT_X, top, DESK_Z, facingIn(AerolargeRegistry.TRANSPONDER.get()));
        transponder = new BlockPos(SEAT_X, top, DESK_Z);
        BlockState seat = AerolargeRegistry.PILOT_SEAT.get().defaultBlockState().setValue(PilotSeatBlock.FACING, Direction.NORTH);
        for (int x : new int[]{-SEAT_X, SEAT_X}) {
            set(x, KNEE_WALL, SEAT_Z, seat.setValue(PilotSeatBlock.HALF, DoubleBlockHalf.LOWER));
            set(x, KNEE_WALL + 1, SEAT_Z, seat.setValue(PilotSeatBlock.HALF, DoubleBlockHalf.UPPER));
        }
    }

    /** A block of the desk's, its face looking back into the cab. */
    private static BlockState facingIn(Block block) {
        return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);
    }

    /**
     * Where the tower lands in the world: its front wall's middle at {@code front}, turned so that its back lies
     * {@code along} from there; where its floor takes the ground's place; where its transponder stands; how many of its
     * blocks something is in the way of (the floor aside), and the first; and its top.
     */
    public record Placement(
        List<BlockPos> positions, List<BlockState> states, List<BlockPos> floor, BlockPos transponder, int blocked, @Nullable BlockPos firstBlocked,
        int top
    ) {
    }

    public Placement place(ServerLevel level, BlockPos front, Direction along) {
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
            BlockPos pos = front.offset(cell.getKey().rotate(rotation));
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
        return new Placement(positions, states, floor, front.offset(transponder.rotate(rotation)), blocked, firstBlocked, top);
    }

    private static boolean inTheWay(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos)) {
            return false;
        }
        BlockState there = level.getBlockState(pos);
        return !there.isAir() && !there.canBeReplaced();
    }

    /**
     * Builds the tower as placed: the ground filled in under its shaft first, then every block, and its transponder set
     * to show the tower on the maps.
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
