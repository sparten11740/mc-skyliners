package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.CraneWinchBlock;
import net.minecraft.core.Direction;

/**
 * The sky crane's winch (see {@link CraneWinchBlock}) at the aft end of the control car's observation lounge, on its
 * glass floor to starboard, worked from behind it looking forward and down through the glass at the hook swinging
 * under her, its rope running down through the floor. It stands on the floor face to face, so it is assembled with the
 * zeppelin, and holds nothing up; the hook it lowers is never part of her and weighs nothing on her. The
 * {@code skyCrane} switch leaves it off a zeppelin built from then on.
 */
final class ZeppelinSkyCrane implements ZeppelinPiece {
    /** How far to starboard of the car's middle the winch stands, and how far forward of its back wall, in blocks. */
    private static final int STARBOARD = 2;
    private static final int FORWARD = 4;

    private final ZeppelinLayout layout;

    ZeppelinSkyCrane(ZeppelinLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return AerolargeConfig.SKY_CRANE.get();
    }

    /** In the control car, the same at every size. */
    @Override
    public boolean fits(ZeppelinSize size) {
        return true;
    }

    @Override
    public void build() {
        ZeppelinLayout.Car car = layout.car();
        int y = car.floor() + 1;
        int z = car.back() - FORWARD;
        // The car's inside is laid as air, so it is clear of whatever is laid there that is not.
        if (layout.stateAt(STARBOARD, y, z).isAir() && layout.stateAt(STARBOARD, y + 1, z).isAir() && !layout.stateAt(STARBOARD, car.floor(), z).isAir()) {
            layout.set(STARBOARD, y, z, AerolargeRegistry.CRANE_WINCH.get().defaultBlockState().setValue(CraneWinchBlock.FACING, Direction.NORTH));
        }
    }
}
