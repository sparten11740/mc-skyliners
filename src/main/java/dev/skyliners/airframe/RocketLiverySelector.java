package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.LiverySelectorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The livery selector in the rocket's cockpit (see {@link LiverySelectorBlock}), its pictures of her in her six
 * liveries (see {@link Livery#swatches}) repainting her in the one used, as a zeppelin's selector does her: on the
 * cockpit's floor along the right wing of her flight deck (see {@link RocketFlightDeck}), facing in at the pilot's
 * right hand, under the camera display, so whoever repaints her watches the new paint run down her on it. It takes the
 * place of the blank casing the display stands on, and stands on the floor face to face, so it is assembled with the
 * rocket.
 */
final class RocketLiverySelector implements RocketPiece {
    private final RocketLayout layout;

    RocketLiverySelector(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted() {
        return SkylinersConfig.LIVERY_BOARD.get();
    }

    @Override
    public void build() {
        BlockPos wing = layout.flightDeck().cameraWing();
        if (wing == null) {
            return;
        }
        BlockState selector = SkylinersRegistry.LIVERY_SELECTOR.get().defaultBlockState().setValue(LiverySelectorBlock.FACING, Direction.WEST);
        // Facing west, the viewer's right is south: the selector reaches south from its first part, at the wing's front
        // end.
        for (int part = 0; part < LiverySelectorBlock.SELECTOR_COLUMNS; part++) {
            layout.set(wing.getX(), wing.getY(), wing.getZ() + part, selector.setValue(LiverySelectorBlock.PART, part));
        }
    }
}
