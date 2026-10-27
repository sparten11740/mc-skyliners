package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.LiveryBoardBlock;
import net.minecraft.core.Direction;

/**
 * The livery board in the control car (see {@link LiveryBoardBlock}): standing on the floor against the starboard
 * wall, across from the elevator wheel and the burner lever, its swatches facing in. It stands on the floor face to
 * face, so it is assembled with the zeppelin, and holds nothing up.
 */
final class ZeppelinLiveryBoard implements ZeppelinPiece {
    /** How far aft of the car's front the board stands, in blocks. */
    private static final int BACK = 7;

    private final ZeppelinLayout layout;

    ZeppelinLiveryBoard(ZeppelinLayout layout) {
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
        int y = car.floor() + 1;
        int z = car.front() + BACK;
        // The car's inside is laid as air, so it is clear of whatever is laid there that is not.
        if (layout.stateAt(x, y, z).isAir() && !layout.stateAt(x, car.floor(), z).isAir()) {
            layout.set(x, y, z, AerolargeRegistry.LIVERY_BOARD.get().defaultBlockState().setValue(LiveryBoardBlock.FACING, Direction.WEST));
        }
    }
}
