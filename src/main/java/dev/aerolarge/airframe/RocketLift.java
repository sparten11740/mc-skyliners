package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.LiftBoardBlock;
import dev.aerolarge.block.LiftBoardBlockEntity;
import dev.aerolarge.block.LiftDoorBlock;
import dev.aerolarge.block.LiftMachineBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The rocket's lift (see {@link LiftMachineBlockEntity}): its car rides her glass shaft from the entry story to the
 * cockpit, where the shaft runs on up into the cockpit as a glass booth roofed over, its machine in the middle of the
 * roof. Each landing has sliding doors in the shaft's doorway, a picture board beside them outside (right of the door)
 * and another on the shaft's back wall inside, both listing her floors by their rooms' icons and colours. The lift
 * knows her autopilot, so it stands open at a floor while she flies. Everything it lays touches the shaft or the
 * cockpit's floor face to face, so it is assembled with her.
 */
final class RocketLift implements RocketPiece {
    /** How many rows the car stands inside, from its floor to its roof, and over that the machine. */
    private static final int CAR_ROWS = 3;
    /** How far over a landing's floor the boards' lower block is, in rows: at eye height. */
    private static final int BOARD_UP = 2;
    /**
     * Each story's room, bottom first, by the icon and colour its floor is shown by on the boards, and the cockpit's
     * over them: entry, cargo bay, galley and mess, bunk room, greenhouse, swimming pool, techno club, lab, observation
     * deck. A smaller rocket has the lower of them.
     */
    private static final String[] ICONS = {"entry", "cargo", "galley", "bunks", "greenhouse", "pool", "club", "lab", "observation"};
    private static final int[] COLOURS = {0xFF8A949E, 0xFFC98A2E, 0xFFC0503A, 0xFF5562B0, 0xFF4C9A55, 0xFF2A9DB0, 0xFFA8458C, 0xFF6FB8C9,
        0xFF2D4F8C};
    private static final String COCKPIT_ICON = "cockpit";
    private static final int COCKPIT_COLOUR = 0xFFD8DEE6;

    private final RocketLayout layout;

    RocketLift(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted() {
        return AerolargeConfig.ROCKET_LIFT.get() && !layout.floors().isEmpty();
    }

    @Override
    public void build() {
        int cockpit = layout.cockpitFloor();
        int machineRow = cockpit + CAR_ROWS + 1;
        booth(cockpit, machineRow);
        BlockPos machine = new BlockPos(0, machineRow, 0);
        layout.set(0, machineRow, 0, AerolargeRegistry.LIFT_MACHINE.get().defaultBlockState());
        List<Integer> stops = new ArrayList<>(layout.floors());
        stops.add(cockpit);
        List<LiftMachineBlockEntity.Landing> landings = new ArrayList<>();
        for (int stop = 0; stop < stops.size(); stop++) {
            int floor = stops.get(stop);
            boolean top = stop == stops.size() - 1;
            landings.add(landing(machine, floor, top ? COCKPIT_ICON : ICONS[stop % ICONS.length], top ? COCKPIT_COLOUR : COLOURS[stop % COLOURS.length]));
            landing(machine, floor, stop);
        }
        BlockPos autopilot = layout.autopilot();
        layout.fit(machine, (blockEntity, rotation) -> {
            if (blockEntity instanceof LiftMachineBlockEntity lift) {
                lift.configure(landings, autopilot == null ? null : autopilot.subtract(machine), rotation);
            }
        });
    }

    /** A landing as the machine knows it: how far under it the car stands, its door and exit, its colour and icon. */
    private static LiftMachineBlockEntity.Landing landing(BlockPos machine, int floor, String icon, int colour) {
        BlockPos door = new BlockPos(0, floor + 1, -RocketLayout.SHAFT).subtract(machine);
        BlockPos exit = new BlockPos(0, floor + 1, -RocketLayout.SHAFT - 1).subtract(machine);
        return new LiftMachineBlockEntity.Landing(machine.getY() - (floor + 1), door, exit, colour, icon);
    }

    /** A landing's doors in the shaft's doorway and its two boards, outside beside the door and inside on the back. */
    private void landing(BlockPos machine, int floor, int stop) {
        LiftDoorBlock door = AerolargeRegistry.LIFT_DOOR.get();
        layout.set(0, floor + 1, -RocketLayout.SHAFT, door.half(Direction.NORTH, DoubleBlockHalf.LOWER));
        layout.set(0, floor + 2, -RocketLayout.SHAFT, door.half(Direction.NORTH, DoubleBlockHalf.UPPER));
        board(machine, new BlockPos(1, floor + BOARD_UP, -RocketLayout.SHAFT - 1), stop);
        board(machine, new BlockPos(0, floor + BOARD_UP, RocketLayout.SHAFT - 1), stop);
    }

    /** A picture board two blocks tall facing north, its lower block at a place, wired to the machine and a landing. */
    private void board(BlockPos machine, BlockPos at, int stop) {
        BlockState board = AerolargeRegistry.LIFT_BOARD.get().defaultBlockState().setValue(LiftBoardBlock.FACING, Direction.NORTH);
        for (int part = 0; part < LiftBoardBlock.BOARD_ROWS; part++) {
            layout.set(at.getX(), at.getY() + part, at.getZ(), board.setValue(LiftBoardBlock.PART, part));
        }
        layout.fit(at, (blockEntity, rotation) -> {
            if (blockEntity instanceof LiftBoardBlockEntity wired) {
                wired.configure(machine.subtract(at), stop, rotation);
            }
        });
    }

    /**
     * The shaft run on up into the cockpit as a booth: walls of the shaft's lift glass, its girders up their corners,
     * round the car's rows over the cockpit's floor but for the doorway at the front, and a roof of lift glass over
     * them, the machine in its middle.
     */
    private void booth(int cockpit, int machineRow) {
        BlockState roof = AerolargeRegistry.LIFT_GLASS.get().defaultBlockState();
        int side = 2 * RocketLayout.SHAFT + 1;
        for (int block = 0; block < side * side; block++) {
            int x = block % side - RocketLayout.SHAFT;
            int z = block / side - RocketLayout.SHAFT;
            boolean wall = Math.abs(x) == RocketLayout.SHAFT || Math.abs(z) == RocketLayout.SHAFT;
            layout.set(x, machineRow, z, roof);
            if (wall) {
                wallColumn(x, z, cockpit, RocketLayout.shaftWall(x, z));
            }
        }
    }

    /** A column of the booth's wall, from the cockpit's floor up to its roof, leaving the doorway at the front open. */
    private void wallColumn(int x, int z, int cockpit, BlockState wall) {
        for (int y = cockpit + 1; y <= cockpit + CAR_ROWS; y++) {
            boolean doorway = x == 0 && z == -RocketLayout.SHAFT && y <= cockpit + 2;
            if (!doorway) {
                layout.set(x, y, z, wall);
            }
        }
    }
}
