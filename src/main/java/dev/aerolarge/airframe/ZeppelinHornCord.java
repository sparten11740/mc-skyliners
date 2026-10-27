package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;

/**
 * The horn cord in the control car (see {@link dev.aerolarge.block.HornCordBlock}): hanging from the roof to starboard,
 * just aft of the control head, where whoever flies the zeppelin reaches it. It hangs from the roof face to face, so it
 * is assembled with the zeppelin, and holds nothing up.
 */
final class ZeppelinHornCord implements ZeppelinPiece {
    /** How far out to starboard of the middle the cord hangs, and how far aft of the car's front, in blocks. */
    private static final int OUT = 2;
    private static final int BACK = 5;

    private final ZeppelinLayout layout;

    ZeppelinHornCord(ZeppelinLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return AerolargeConfig.HORN.get();
    }

    /** In the control car, the same at every size. */
    @Override
    public boolean fits(ZeppelinSize size) {
        return true;
    }

    @Override
    public void build() {
        ZeppelinLayout.Car car = layout.car();
        int y = car.roof() - 1;
        int z = car.front() + BACK;
        // The car's inside is laid as air, so it is clear of whatever is laid there that is not.
        if (layout.stateAt(OUT, y, z).isAir() && !layout.stateAt(OUT, car.roof(), z).isAir()) {
            layout.set(OUT, y, z, AerolargeRegistry.HORN_CORD.get().defaultBlockState());
        }
    }
}
