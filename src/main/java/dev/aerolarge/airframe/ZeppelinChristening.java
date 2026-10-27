package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.ShipsPlaqueBlock;
import net.minecraft.core.Direction;

/**
 * The ship's plaque in the control car (see {@link ShipsPlaqueBlock}): on the back wall of the observation lounge, to
 * port of the ladder up the neck, at eye height, looking forward, blank until she is christened (see
 * {@code Christening}). It hangs on the wall face to face, so it is assembled with the zeppelin, and holds nothing up.
 */
final class ZeppelinChristening implements ZeppelinPiece {
    /** How far to port of the car's middle the plaque hangs, and how high over its floor, in blocks. */
    private static final int PORT = 3;
    private static final int UP = 2;

    private final ZeppelinLayout layout;

    ZeppelinChristening(ZeppelinLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return AerolargeConfig.CHRISTENING.get();
    }

    /** In the control car, the same at every size. */
    @Override
    public boolean fits(ZeppelinSize size) {
        return true;
    }

    @Override
    public void build() {
        ZeppelinLayout.Car car = layout.car();
        int y = car.floor() + UP;
        int z = car.back() - 1;
        // The car's inside is laid as air, so it is clear of whatever is laid there that is not.
        if (layout.stateAt(-PORT, y, z).isAir() && !layout.stateAt(-PORT, y, car.back()).isAir()) {
            layout.set(-PORT, y, z, AerolargeRegistry.SHIPS_PLAQUE.get().defaultBlockState().setValue(ShipsPlaqueBlock.FACING, Direction.NORTH));
        }
    }
}
