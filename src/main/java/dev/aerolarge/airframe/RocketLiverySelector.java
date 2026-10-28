package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.LiverySelectorBlock;
import java.util.OptionalInt;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The livery selector in the rocket's cockpit (see {@link LiverySelectorBlock}), its pictures of her in her six
 * liveries (see {@link Livery#swatches}) repainting her in the one used, as a zeppelin's selector does her: on the
 * cockpit's floor against the glass on her right, under the camera display, so whoever repaints her watches the new
 * paint run down her on it. It stands on the floor face to face, so it is assembled with the rocket.
 */
final class RocketLiverySelector implements RocketPiece {
    /** How high over the cockpit's floor it stands, in blocks: on it. */
    private static final int UP = 1;

    private final RocketLayout layout;

    RocketLiverySelector(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted() {
        return AerolargeConfig.LIVERY_BOARD.get();
    }

    @Override
    public void build() {
        int columns = LiverySelectorBlock.SELECTOR_COLUMNS;
        OptionalInt x = layout.cockpitWall(columns);
        if (x.isEmpty()) {
            return;
        }
        int y = layout.cockpitFloor() + UP;
        // Facing west, the viewer's right is south: the selector reaches south from its first part.
        int first = -columns / 2;
        BlockState selector = AerolargeRegistry.LIVERY_SELECTOR.get().defaultBlockState().setValue(LiverySelectorBlock.FACING, Direction.WEST);
        for (int part = 0; part < columns; part++) {
            layout.set(x.getAsInt(), y, first + part, selector.setValue(LiverySelectorBlock.PART, part));
        }
    }
}
