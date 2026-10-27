package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.LookoutGlassBlock;
import net.minecraft.core.Direction;

/**
 * The Lookout's Glass in the control car's observation lounge, by its port windows and over its glass floor, pointing
 * out of them, where a lookout sees the land round and under the ship and marks a spot on it to fly to (see
 * {@link LookoutGlassBlock}), steps from the autopilot. Not on the Sky Deck: from the top of the hull, its curve hides
 * all the land nearer than some three and a half times the deck's height over it, much further off than is loaded.
 * The glass stands on the lounge's floor, so it is assembled with the zeppelin; nothing stands on it.
 */
final class ZeppelinLookout implements ZeppelinPiece {
    /**
     * How far aft of the car's middle row, where the lounge begins, the glass stands, in rows, and how far in from the
     * car's port wall.
     */
    private static final int AFT = 1;
    private static final int IN = 1;

    private final ZeppelinLayout layout;

    ZeppelinLookout(ZeppelinLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return AerolargeConfig.LOOKOUT.get();
    }

    @Override
    public void build() {
        ZeppelinLayout.Car car = layout.car();
        int x = -car.half() + IN;
        int y = car.floor() + 1;
        int z = car.lounge() + AFT;
        // The car's inside is laid as air, so it is clear of whatever is laid there that is not.
        if (layout.stateAt(x, y, z).isAir()) {
            layout.set(x, y, z, AerolargeRegistry.LOOKOUT_GLASS.get().defaultBlockState().setValue(LookoutGlassBlock.FACING, Direction.WEST));
        }
    }
}
