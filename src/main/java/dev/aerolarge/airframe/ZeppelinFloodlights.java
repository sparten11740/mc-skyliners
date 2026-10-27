package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.FloodlightBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The zeppelin's floodlights (see {@link dev.aerolarge.block.FloodlightBlock}), lighting the land under her and ahead
 * from dusk to dawn: two under the control car's floor near its front, either side of its middle, and a line along her
 * keel, each throwing its light down and a little ahead. Each hangs from the car's floor or her cover, face to face,
 * so it is assembled with her; one whose place is taken, by the beacon under the belly or the boarding stairs, is left
 * out. The {@code floodlights} switch leaves them off a zeppelin built from then on.
 */
final class ZeppelinFloodlights implements ZeppelinPiece {
    /** How far either side of the middle the car's lights hang, and how far aft of its front, in blocks. */
    private static final int CAR_OUT = 2;
    private static final int CAR_AFT = 3;
    /** Where along the hull the keel's lights hang, as shares of its length from the nose. */
    private static final double[] KEEL_SHARES = {0.3, 0.42, 0.58, 0.7};
    private static final int[] SIDES = {-1, 1};

    private final ZeppelinLayout layout;

    ZeppelinFloodlights(ZeppelinLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return AerolargeConfig.FLOODLIGHTS.get();
    }

    /** Under the control car and at shares of the keel's length, the same at every size. */
    @Override
    public boolean fits(ZeppelinSize size) {
        return true;
    }

    @Override
    public void build() {
        ZeppelinLayout.Car car = layout.car();
        for (int side : SIDES) {
            hang(side * CAR_OUT, car.floor() - 1, car.front() + CAR_AFT);
        }
        for (double share : KEEL_SHARES) {
            keel((int) Math.round(layout.length() * share));
        }
    }

    /** A light under the keel at a row: hung from the hull's lowest block there, where that is her cover. */
    private void keel(int z) {
        int y = 1;
        while (y < layout.axis() && layout.stateAt(0, y, z).isAir()) {
            y++;
        }
        BlockState above = layout.stateAt(0, y, z);
        if (above.is(AerolargeRegistry.ZEPPELIN_COVER.get()) || above.is(AerolargeRegistry.ZEPPELIN_FABRIC.get())) {
            hang(0, y - 1, z);
        }
    }

    /** A light at a block, facing ahead, if nothing stands there yet and something does over it to hang from. */
    private void hang(int x, int y, int z) {
        if (layout.isOpen(x, y, z) && !layout.isOpen(x, y + 1, z)) {
            layout.set(x, y, z, AerolargeRegistry.FLOODLIGHT.get().defaultBlockState().setValue(FloodlightBlock.FACING, Direction.NORTH));
        }
    }
}
