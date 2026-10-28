package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.LiverySelectorBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The livery selector in the control car (see {@link LiverySelectorBlock}): a wooden board four blocks wide against
 * the starboard wall, just over the floor and under the camera display, its pictures of her in each livery facing in,
 * so whoever picks one watches her new paint sweep along on the display over it. It stands against the car's wall
 * face to face, so it is assembled with the zeppelin, and holds nothing up. Zeppelins built before it keep the livery
 * board they were built with.
 */
final class ZeppelinLiverySelector implements ZeppelinPiece {
    /**
     * How far aft of the car's front its forward edge is, under the camera display's, and how high over the floor it
     * stands, in blocks.
     */
    private static final int BACK = 8;
    private static final int UP = 1;

    private final ZeppelinLayout layout;

    ZeppelinLiverySelector(ZeppelinLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return AerolargeConfig.LIVERY_BOARD.get();
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
        // Facing west, the viewer's right is south, aft: the selector reaches aft from its first part.
        int first = car.front() + BACK;
        for (int part = 0; part < LiverySelectorBlock.SELECTOR_COLUMNS; part++) {
            if (!roomAt(x, y, first + part)) {
                return;
            }
        }
        BlockState selector = AerolargeRegistry.LIVERY_SELECTOR.get().defaultBlockState().setValue(LiverySelectorBlock.FACING, Direction.WEST);
        for (int part = 0; part < LiverySelectorBlock.SELECTOR_COLUMNS; part++) {
            layout.set(x, y, first + part, selector.setValue(LiverySelectorBlock.PART, part));
        }
    }

    /** Whether a part of the selector has room at a block: inside the car, open, with the car's wall behind it. */
    private boolean roomAt(int x, int y, int z) {
        // The car's inside is laid as air, so it is clear of whatever is laid there that is not.
        return layout.stateAt(x, y, z).isAir() && !layout.stateAt(x + 1, y, z).isAir();
    }
}
