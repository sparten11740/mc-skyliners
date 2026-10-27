package dev.aerolarge.airframe;

import static dev.aerolarge.airframe.ZeppelinShape.*;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.ZeppelinPilot;
import dev.aerolarge.block.AutopilotBlock;
import dev.aerolarge.block.BurnerLeverBlock;
import dev.aerolarge.block.FlightDisplayBlock;
import dev.aerolarge.block.FlightMapBlock;
import dev.aerolarge.block.HelmBlock;
import dev.aerolarge.block.TelegraphBlock;
import dev.aerolarge.block.TiledPanelBlock;
import dev.aerolarge.block.TransponderBlock;
import dev.aerolarge.block.TransponderBlockEntity;
import dev.aerolarge.radar.MarkerColour;
import dev.aerolarge.radar.MarkerIcon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the zeppelin is flown with, in the control room at the front of its control car, as the Hindenburg's was: the
 * rudder wheel at the front windows, looking ahead; the autopilot's control head behind it, where its settings are
 * opened, between the port and the starboard engines' telegraphs; a flight display and the transponder on the low
 * wall under the windows; the elevator wheel and the burner lever along the port wall, looking out of its windows;
 * and the flight map on the back wall, by the ladder up into the hull. The control head is wired when the zeppelin is
 * built to every gas cell's burners, every engine's relays and the hand controls, and to the box round the zeppelin's
 * blocks (see {@link AutopilotBlockEntity#configureZeppelin}).
 */
final class ZeppelinControlCar {
    /** How far either side of the middle the flight display and the transponder stand, in blocks. */
    private static final int BESIDE = 2;
    /**
     * Where the controls stand, in blocks: how far back from the front wall the rudder wheel and the control head
     * are; how far either side of the middle the telegraphs stand, level with the control head; where across the car
     * the port wall's controls stand (to port of the middle, less than nothing), and how far back the elevator wheel
     * and the burner lever.
     */
    private static final int RUDDER_BACK = 1;
    private static final int HEAD_BACK = 3;
    private static final int TELEGRAPHS_OUT = 3;
    private static final int PORT_WALL = -4;
    private static final int ELEVATOR_BACK = 6;
    private static final int LEVER_BACK = 8;
    /** How far east of the car's middle the flight map's left edge is, seen facing it on the back wall, in blocks. */
    private static final int MAP_LEFT = 4;

    private final ZeppelinLayout layout;

    ZeppelinControlCar(ZeppelinLayout layout) {
        this.layout = layout;
    }

    /**
     * Fits the control room out, the car's front wall at row {@code front}, its back wall at {@code back}, its floor at
     * {@code floor}.
     */
    void fit(int front, int back, int floor) {
        BlockPos autopilot = new BlockPos(0, floor + 1, front + HEAD_BACK);
        set(autopilot, AerolargeRegistry.AUTOPILOT.get().defaultBlockState().setValue(AutopilotBlock.FACING, Direction.NORTH));
        Controls controls = controls(front, floor);
        layout.set(-BESIDE, floor + 1, front + 1,
            AerolargeRegistry.FLIGHT_DISPLAY.get().defaultBlockState().setValue(FlightDisplayBlock.FACING, Direction.SOUTH));
        layout.set(BESIDE, floor + 1, front + 1, AerolargeRegistry.TRANSPONDER.get().defaultBlockState().setValue(TransponderBlock.FACING, Direction.SOUTH));
        layout.fit(new BlockPos(BESIDE, floor + 1, front + 1), (blockEntity, rotation) -> {
            if (blockEntity instanceof TransponderBlockEntity transponder) {
                transponder.show(MarkerIcon.AIRSHIP, MarkerColour.WHITE);
            }
        });
        flightMap(back, floor);
        layout.fit(autopilot, (blockEntity, rotation) -> {
            if (blockEntity instanceof AutopilotBlockEntity wired) {
                wire(wired, autopilot, controls, rotation);
            }
        });
    }

    /** Where the hand controls stand in the layout. */
    private record Controls(BlockPos rudder, BlockPos elevator, BlockPos port, BlockPos starboard, BlockPos lever) {
    }

    /** The hand controls, each facing whoever works it. */
    private Controls controls(int front, int floor) {
        int y = floor + 1;
        Controls controls = new Controls(new BlockPos(0, y, front + RUDDER_BACK), new BlockPos(PORT_WALL, y, front + ELEVATOR_BACK),
            new BlockPos(-TELEGRAPHS_OUT, y, front + HEAD_BACK), new BlockPos(TELEGRAPHS_OUT, y, front + HEAD_BACK),
            new BlockPos(PORT_WALL, y, front + LEVER_BACK));
        set(controls.rudder(), AerolargeRegistry.RUDDER_WHEEL.get().defaultBlockState().setValue(HelmBlock.FACING, Direction.NORTH));
        set(controls.elevator(), AerolargeRegistry.ELEVATOR_WHEEL.get().defaultBlockState().setValue(HelmBlock.FACING, Direction.WEST));
        BlockState telegraph = AerolargeRegistry.ENGINE_TELEGRAPH.get().defaultBlockState().setValue(TelegraphBlock.FACING, Direction.NORTH);
        set(controls.port(), telegraph.setValue(TelegraphBlock.ENGINES, TelegraphBlock.Engines.PORT));
        set(controls.starboard(), telegraph.setValue(TelegraphBlock.ENGINES, TelegraphBlock.Engines.STARBOARD));
        set(controls.lever(), AerolargeRegistry.BURNER_LEVER.get().defaultBlockState().setValue(BurnerLeverBlock.FACING, Direction.WEST)
            .setValue(BurnerLeverBlock.HEAT, 0));
        return controls;
    }

    private void set(BlockPos at, BlockState state) {
        layout.set(at.getX(), at.getY(), at.getZ(), state);
    }

    /** The flight map, three blocks by three, on the back wall of the car, facing forward. */
    private void flightMap(int back, int floor) {
        BlockState map = AerolargeRegistry.FLIGHT_MAP.get().defaultBlockState().setValue(FlightMapBlock.FACING, Direction.SOUTH);
        int top = floor + 1 + TiledPanelBlock.ROWS;
        for (int part = 0; part < TiledPanelBlock.COLUMNS * TiledPanelBlock.ROWS; part++) {
            int column = part % TiledPanelBlock.COLUMNS;
            layout.set(MAP_LEFT - column, top - part / TiledPanelBlock.COLUMNS, back - 1, map.setValue(TiledPanelBlock.PART, part));
        }
    }

    /**
     * Wires the control head once it stands: each cell's burners' relays with how far ahead of the cells' middle the
     * cell hangs, each engine's relays, how far above the head the hot air stands and how far aft the cells' middle
     * is, its wheels' height under it, and the box round the zeppelin's blocks, all as offsets from it turned as the
     * zeppelin was placed; and the livery the zeppelin is first painted in.
     */
    private void wire(AutopilotBlockEntity autopilot, BlockPos at, Controls controls, Rotation rotation) {
        List<List<BlockPos>> relays = layout.burnerRelays();
        double middle = layout.liftMiddle();
        double height = 0;
        int burners = 0;
        for (int cell = 0; cell < relays.size(); cell++) {
            int count = relays.get(cell).size();
            height += count * layout.balloon(cell).centreY() * layout.scale();
            burners += count;
        }
        height = burners > 0 ? height / burners : at.getY();
        List<ZeppelinPilot.Cell> cells = new ArrayList<>();
        for (int cell = 0; cell < relays.size(); cell++) {
            cells.add(new ZeppelinPilot.Cell(offsets(at, relays.get(cell), rotation), middle - cellMiddle(cell) * layout.scale()));
        }
        autopilot.configureZeppelin(cells, engines(at, layout.aheadRelays(), rotation), engines(at, layout.asternRelays(), rotation),
            height - at.getY(), middle - at.getZ(), at.getY());
        List<BlockPos> hand = offsets(at, List.of(controls.rudder(), controls.elevator(), controls.port(), controls.starboard(), controls.lever()), rotation);
        autopilot.wireZeppelinControls(hand.get(0), hand.get(1), hand.get(2), hand.get(3), hand.get(4));
        List<BlockPos> frame = offsets(at, layout.frameCorners(), rotation);
        autopilot.frame(frame.get(0), frame.get(1));
        autopilot.paint(layout.livery());
    }

    private static Map<Integer, List<BlockPos>> engines(BlockPos at, Map<Integer, List<BlockPos>> relays, Rotation rotation) {
        Map<Integer, List<BlockPos>> turned = new HashMap<>();
        relays.forEach((engine, offsets) -> turned.put(engine, offsets(at, offsets, rotation)));
        return turned;
    }

    /** Positions in the layout as offsets from a block, turned with the zeppelin. */
    private static List<BlockPos> offsets(BlockPos from, List<BlockPos> positions, Rotation rotation) {
        return positions.stream().map(pos -> pos.subtract(from).rotate(rotation)).toList();
    }
}
