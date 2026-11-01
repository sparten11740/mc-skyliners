package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.InstrumentPanelBlock;
import dev.skyliners.block.TailCameraDisplayBlock;
import dev.skyliners.block.TailCameraDisplayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The camera display in the rocket's cockpit, the airliner's own in its large size (see
 * {@link TailCameraDisplayBlock}), showing her outside cameras live (see {@link RocketCamera}): upright on the right
 * wing of her flight deck (see {@link RocketFlightDeck}), facing in at the pilot's right hand, at eye height over the
 * livery selector (see {@link RocketLiverySelector}), so whoever repaints her watches the new paint run down her. It
 * shows the orbit camera at first, which sees the whole of her as it circles her. It stands on blank panel casing,
 * face to face, so it is assembled with the rocket; the livery selector, laid after it, takes the casing's place.
 */
final class RocketCameraDisplay implements RocketPiece {
    /** How high over the cockpit's floor its bottom row is, in blocks: over the livery selector, at eye height. */
    private static final int UP = 2;

    private final RocketLayout layout;

    RocketCameraDisplay(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted() {
        return SkylinersConfig.CAMERA_DISPLAY.get();
    }

    @Override
    public void build() {
        BlockPos wing = layout.flightDeck().cameraWing();
        if (wing == null) {
            return;
        }
        int columns = TailCameraDisplayBlock.LARGE_COLUMNS;
        int y = layout.cockpitFloor() + UP;
        BlockState casing = SkylinersRegistry.INSTRUMENT_PANEL.get().defaultBlockState().setValue(InstrumentPanelBlock.FACING, Direction.WEST)
            .setValue(InstrumentPanelBlock.BLANK, true);
        BlockState display = SkylinersRegistry.TAIL_CAMERA_DISPLAY.get().defaultBlockState()
            .setValue(TailCameraDisplayBlock.FACING, Direction.WEST)
            .setValue(TailCameraDisplayBlock.LARGE, true);
        // Facing west, the viewer's right is south: the display reaches south and up from its first part, at the
        // wing's front end.
        for (int part = 0; part < columns * TailCameraDisplayBlock.LARGE_ROWS; part++) {
            layout.set(wing.getX(), y + part / columns, wing.getZ() + part % columns, display.setValue(TailCameraDisplayBlock.PART, part));
        }
        for (int column = 0; column < columns; column++) {
            layout.set(wing.getX(), wing.getY(), wing.getZ() + column, casing);
        }
        layout.fit(new BlockPos(wing.getX(), y, wing.getZ()), (blockEntity, rotation) -> {
            if (blockEntity instanceof TailCameraDisplayBlockEntity shown) {
                shown.look(RocketCamera.ORBIT);
            }
        });
    }
}
