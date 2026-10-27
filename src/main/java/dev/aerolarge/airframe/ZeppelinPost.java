package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.FrankingMachineBlock;
import net.minecraft.core.Direction;

/**
 * Zeppelin Post: the franking machine (see {@link FrankingMachineBlock}) on the control car's floor to starboard, aft of
 * the control room's hand controls, facing the middle, where it prints postcards and drops mail sacks through the car's
 * floor. It stands on the floor face to face, so it is assembled with the zeppelin, and holds nothing up. The
 * {@code zeppelinPost} switch leaves it off a zeppelin built from then on.
 */
final class ZeppelinPost implements ZeppelinPiece {
    /** How far to starboard of the car's middle the machine stands, and how far aft of its front, in blocks. */
    private static final int STARBOARD = 3;
    private static final int AFT = 11;

    private final ZeppelinLayout layout;

    ZeppelinPost(ZeppelinLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return AerolargeConfig.ZEPPELIN_POST.get();
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
        int z = car.front() + AFT;
        // The car's inside is laid as air, so it is clear of whatever is laid there that is not.
        if (layout.stateAt(STARBOARD, y, z).isAir() && !layout.stateAt(STARBOARD, car.floor(), z).isAir()) {
            layout.set(STARBOARD, y, z, AerolargeRegistry.FRANKING_MACHINE.get().defaultBlockState().setValue(FrankingMachineBlock.FACING, Direction.WEST));
        }
    }
}
