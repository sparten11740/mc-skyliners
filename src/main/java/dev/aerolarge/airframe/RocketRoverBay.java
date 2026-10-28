package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.RoverBayBlockEntity;
import dev.aerolarge.block.RoverHatchBlock;
import dev.aerolarge.block.RoverPanelBlock;
import dev.aerolarge.rover.RoverRoute;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The rocket's rover bay on her cargo story, the one over her entry story: a door in her skin where that story's back
 * window would be (see {@link RoverHatchBlock}), whose middle part on the sill keeps the bay's block entity, and the
 * bay's panel on the back of the lift shaft's glass, facing the door (see {@link RoverPanelBlock}), wired to the bay
 * once it stands. The rover itself is not laid: the bay makes it in its cradle, between the panel and the door, on its
 * first tick (see {@code entity.Rover}). The bay is only built where the rover has room: the story there, the skin flat
 * across the door's width where the window would be, and the cradle's length, a player's step and the panel between the
 * shaft and the door; a rocket too small has none and keeps her window. The {@code rover} switch leaves it off a rocket
 * built from then on.
 */
final class RocketRoverBay implements RocketPiece {
    /** Which story the bay is on, from the entry story up, and the way out of her its door looks: her back. */
    private static final int CARGO_STORY = 1;
    private static final Direction DOOR_WAY = Direction.SOUTH;
    /** Where the panel is: so many blocks to the side of her axis, over the floor, and out from the shaft's wall. */
    private static final int PANEL_ACROSS = 2;
    private static final int PANEL_UP = 2;
    private static final int PANEL_OUT = RocketLayout.SHAFT + 1;
    /** How much room is left between the panel and the rover's back in its cradle, in blocks: a step to stand in. */
    private static final double STANDING_ROOM = 1;

    private final RocketLayout layout;

    RocketRoverBay(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted() {
        return AerolargeConfig.ROCKET_ROVER.get();
    }

    /** Whether the bay's door takes the place of a story's window looking out one way, so the window is left out. */
    static boolean takesWindow(RocketLayout layout, int floor, Direction way) {
        List<Integer> floors = layout.floors();
        return way == DOOR_WAY && AerolargeConfig.ROCKET_ROVER.get() && floors.size() > CARGO_STORY && floor == floors.get(CARGO_STORY)
            && doorway(layout, floor) != null;
    }

    @Override
    public void build() {
        List<Integer> floors = layout.floors();
        if (floors.size() <= CARGO_STORY) {
            return;
        }
        int floor = floors.get(CARGO_STORY);
        BlockPos[] doorway = doorway(layout, floor);
        if (doorway == null) {
            return;
        }
        RoverHatchBlock hatch = AerolargeRegistry.ROVER_HATCH.get();
        for (int part = 0; part < doorway.length; part++) {
            BlockPos at = doorway[part];
            layout.set(at.getX(), at.getY(), at.getZ(), hatch.part(DOOR_WAY, part % RoverHatchBlock.COLUMNS, part / RoverHatchBlock.COLUMNS));
        }
        BlockPos sill = doorway[RoverHatchBlock.COLUMNS / 2];
        BlockPos panel = panel(floor);
        if (panel != null) {
            layout.fit(sill, (blockEntity, rotation) -> {
                if (blockEntity instanceof RoverBayBlockEntity bay) {
                    bay.wirePanel(panel.subtract(sill).rotate(rotation));
                }
            });
        }
    }

    /** The panel on the shaft's wall, where it is; null where something stands there already. */
    @Nullable
    private BlockPos panel(int floor) {
        int y = floor + PANEL_UP;
        if (!layout.isOpen(PANEL_ACROSS, y, PANEL_OUT) || layout.isOpen(PANEL_ACROSS, y, PANEL_OUT - 1)) {
            return null;
        }
        BlockState state = AerolargeRegistry.ROVER_PANEL.get().defaultBlockState().setValue(RoverPanelBlock.FACING, DOOR_WAY);
        layout.set(PANEL_ACROSS, y, PANEL_OUT, state);
        return new BlockPos(PANEL_ACROSS, y, PANEL_OUT);
    }

    /**
     * Where the door's parts go, by column from the left as seen from outside and row from the sill, the sill's first:
     * where the story's back window would be, the skin's outermost blocks there; null unless every one of them is the
     * cover, all in one plane, with the cradle's length inside them clear of the shaft and the panel.
     */
    @Nullable
    private static BlockPos[] doorway(RocketLayout layout, int floor) {
        int rows = RoverHatchBlock.ROWS;
        BlockPos[] parts = new BlockPos[RoverHatchBlock.COLUMNS * rows];
        for (int part = 0; part < parts.length; part++) {
            BlockPos at = layout.windowPiece(floor, DOOR_WAY, part % RoverHatchBlock.COLUMNS, rows - 1 - part / RoverHatchBlock.COLUMNS);
            if (at == null || at.getZ() != (parts[0] == null ? at.getZ() : parts[0].getZ())) {
                return null;
            }
            parts[part] = at;
        }
        double back = RoverRoute.hinge(parts[0]).z - RoverRoute.CRADLE_IN - RoverRoute.ROVER_HALF;
        return back >= PANEL_OUT + 1 + STANDING_ROOM ? parts : null;
    }
}
