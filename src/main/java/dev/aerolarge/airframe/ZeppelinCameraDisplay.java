package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.TailCameraDisplayBlock;
import dev.aerolarge.block.TailCameraDisplayBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The camera display in the control car, the airliner's own in its large size (see {@link TailCameraDisplayBlock}),
 * showing her outside cameras live (see {@link ZeppelinCamera}): upright on the starboard wall at eye height, its back
 * to the windows, over the livery selector and away from the elevator wheel, so whoever repaints her watches the new
 * paint sweep over her. It shows the flyby camera at first, which sees her broadside as it circles her. It
 * stands against the car's wall face to face, so it is assembled with the zeppelin, and holds nothing up.
 */
final class ZeppelinCameraDisplay implements ZeppelinPiece {
    /**
     * How far aft of the car's front its forward edge is, over the livery selector's, and how high over the floor its
     * bottom row is, in blocks.
     */
    private static final int BACK = 8;
    private static final int UP = 2;

    private final ZeppelinLayout layout;

    ZeppelinCameraDisplay(ZeppelinLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return AerolargeConfig.CAMERA_DISPLAY.get();
    }

    /** In the control car, the same at every size. */
    @Override
    public boolean fits(ZeppelinSize size) {
        return true;
    }

    @Override
    public void build() {
        ZeppelinLayout.Car car = layout.car();
        int x = car.half() - 1;
        int y = car.floor() + UP;
        // Facing west, the viewer's right is south, aft: the display reaches aft and up from its first part.
        int first = car.front() + BACK;
        int parts = TailCameraDisplayBlock.LARGE_COLUMNS * TailCameraDisplayBlock.LARGE_ROWS;
        for (int part = 0; part < parts; part++) {
            if (!roomAt(x, y + part / TailCameraDisplayBlock.LARGE_COLUMNS, first + part % TailCameraDisplayBlock.LARGE_COLUMNS)) {
                return;
            }
        }
        BlockState display = AerolargeRegistry.TAIL_CAMERA_DISPLAY.get().defaultBlockState()
            .setValue(TailCameraDisplayBlock.FACING, Direction.WEST)
            .setValue(TailCameraDisplayBlock.LARGE, true);
        for (int part = 0; part < parts; part++) {
            int column = part % TailCameraDisplayBlock.LARGE_COLUMNS;
            layout.set(x, y + part / TailCameraDisplayBlock.LARGE_COLUMNS, first + column, display.setValue(TailCameraDisplayBlock.PART, part));
        }
        layout.fit(new BlockPos(x, y, first), (blockEntity, rotation) -> {
            if (blockEntity instanceof TailCameraDisplayBlockEntity shown) {
                shown.look(ZeppelinCamera.FLYBY);
            }
        });
    }

    /** Whether a part of the display has room at a block: inside the car, open, with the car's wall behind it. */
    private boolean roomAt(int x, int y, int z) {
        // The car's inside is laid as air, so it is clear of whatever is laid there that is not.
        return layout.stateAt(x, y, z).isAir() && !layout.stateAt(x + 1, y, z).isAir();
    }
}
