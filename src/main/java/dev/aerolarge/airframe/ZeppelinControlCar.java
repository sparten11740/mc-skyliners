package dev.aerolarge.airframe;

import static dev.aerolarge.airframe.ZeppelinShape.*;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.ZeppelinPilot;
import dev.aerolarge.block.AutopilotBlock;
import dev.aerolarge.block.FlightDisplayBlock;
import dev.aerolarge.block.FlightMapBlock;
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
 * What the zeppelin is flown with, in the control room at the front of its control car: the autopilot's control head
 * in the middle, a step back from the windows, where its settings are opened; a flight display and the transponder on
 * the low wall under the windows, either side of it; and the flight map on the back wall, by the ladder up into the
 * hull. The control head is wired when the zeppelin is built to every gas cell's burners and every engine's relays,
 * and to the box round the zeppelin's blocks (see {@link AutopilotBlockEntity#configureZeppelin}).
 */
final class ZeppelinControlCar {
    /** How far either side of the middle the flight display and the transponder stand, in blocks. */
    private static final int BESIDE = 2;
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
        BlockPos autopilot = new BlockPos(0, floor + 1, front + 2);
        layout.set(0, floor + 1, front + 2, AerolargeRegistry.AUTOPILOT.get().defaultBlockState().setValue(AutopilotBlock.FACING, Direction.NORTH));
        layout.set(-BESIDE, floor + 1, front + 1,
            AerolargeRegistry.FLIGHT_DISPLAY.get().defaultBlockState().setValue(FlightDisplayBlock.FACING, Direction.SOUTH));
        layout.set(BESIDE, floor + 1, front + 1, AerolargeRegistry.TRANSPONDER.get().defaultBlockState().setValue(TransponderBlock.FACING, Direction.SOUTH));
        layout.fit(new BlockPos(BESIDE, floor + 1, front + 1), (blockEntity, rotation) -> {
            if (blockEntity instanceof TransponderBlockEntity transponder) {
                transponder.show(MarkerIcon.AIRLINER, MarkerColour.WHITE);
            }
        });
        flightMap(back, floor);
        layout.fit(autopilot, (blockEntity, rotation) -> {
            if (blockEntity instanceof AutopilotBlockEntity wired) {
                wire(wired, autopilot, rotation);
            }
        });
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
     * zeppelin was placed.
     */
    private void wire(AutopilotBlockEntity autopilot, BlockPos at, Rotation rotation) {
        List<List<BlockPos>> relays = layout.burnerRelays();
        double middle = 0;
        double height = 0;
        int burners = 0;
        for (int cell = 0; cell < relays.size(); cell++) {
            int count = relays.get(cell).size();
            middle += count * cellMiddle(cell) * SCALE;
            height += count * ZeppelinShape.Cell.of(cell).centreY() * SCALE;
            burners += count;
        }
        middle = burners > 0 ? middle / burners : 0;
        height = burners > 0 ? height / burners : at.getY();
        List<ZeppelinPilot.Cell> cells = new ArrayList<>();
        for (int cell = 0; cell < relays.size(); cell++) {
            cells.add(new ZeppelinPilot.Cell(offsets(at, relays.get(cell), rotation), middle - cellMiddle(cell) * SCALE));
        }
        autopilot.configureZeppelin(cells, engines(at, layout.aheadRelays(), rotation), engines(at, layout.asternRelays(), rotation),
            height - at.getY(), middle - at.getZ(), at.getY());
        List<BlockPos> frame = offsets(at, layout.frameCorners(), rotation);
        autopilot.frame(frame.get(0), frame.get(1));
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
