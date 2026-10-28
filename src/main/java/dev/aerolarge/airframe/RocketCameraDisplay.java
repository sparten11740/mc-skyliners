package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.TailCameraDisplayBlock;
import dev.aerolarge.block.TailCameraDisplayBlockEntity;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The camera display in the rocket's cockpit, the airliner's own in its large size (see
 * {@link TailCameraDisplayBlock}), showing her outside cameras live (see {@link RocketCamera}): upright against the
 * glass on her right, beside the autopilot, at eye height a row clear of the cockpit's floor. It shows the orbit camera
 * at first, which sees the whole of her as it circles her. Its back stands against the glass face to face where the
 * glass comes furthest in, so it is assembled with the rocket, and it holds nothing up.
 */
final class RocketCameraDisplay implements RocketPiece {
    /** How high over the cockpit's floor its bottom row is, in blocks: at eye height. */
    private static final int UP = 2;

    private final RocketLayout layout;

    RocketCameraDisplay(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted() {
        return AerolargeConfig.CAMERA_DISPLAY.get();
    }

    @Override
    public void build() {
        int columns = TailCameraDisplayBlock.LARGE_COLUMNS;
        int y = layout.cockpitFloor() + UP;
        // Facing west, the viewer's right is south: the display reaches south and up from its first part, its middle
        // level with the axis.
        int first = -columns / 2;
        OptionalInt x = layout.againstRightSkin(y, y + TailCameraDisplayBlock.LARGE_ROWS - 1, first, first + columns - 1);
        if (x.isEmpty()) {
            return;
        }
        BlockState display = AerolargeRegistry.TAIL_CAMERA_DISPLAY.get().defaultBlockState()
            .setValue(TailCameraDisplayBlock.FACING, Direction.WEST)
            .setValue(TailCameraDisplayBlock.LARGE, true);
        for (int part = 0; part < columns * TailCameraDisplayBlock.LARGE_ROWS; part++) {
            layout.set(x.getAsInt(), y + part / columns, first + part % columns, display.setValue(TailCameraDisplayBlock.PART, part));
        }
        layout.fit(new BlockPos(x.getAsInt(), y, first), (blockEntity, rotation) -> {
            if (blockEntity instanceof TailCameraDisplayBlockEntity shown) {
                shown.look(RocketCamera.ORBIT);
            }
        });
    }
}
