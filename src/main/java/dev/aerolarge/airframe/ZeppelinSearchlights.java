package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.SearchlightHandleBlock;
import dev.aerolarge.block.SearchlightHandleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Two searchlights hung under the control car's floor at its forward corners, one to port and one to starboard (see
 * {@link dev.aerolarge.block.SearchlightBlock}), and their handles in the car's observation lounge, each on its own
 * side, over the glass floor the light it swings shows through (see {@link SearchlightHandleBlock}). Each light hangs
 * from the floor and each handle stands on it, face to face, so they are assembled with the zeppelin.
 */
final class ZeppelinSearchlights implements ZeppelinPiece {
    /** How far in from the car's sides the lights hang and how far aft of its front, in blocks. */
    private static final int LIGHT_IN = 1;
    private static final int LIGHT_AFT = 1;
    /** How far either side of the middle the handles stand, and how far aft of the lounge's start, in blocks. */
    private static final int HANDLE_OUT = 2;
    private static final int HANDLE_AFT = 3;
    private static final int[] SIDES = {-1, 1};

    private final ZeppelinLayout layout;

    ZeppelinSearchlights(ZeppelinLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return AerolargeConfig.SEARCHLIGHTS.get();
    }

    /** In the control car, the same at every size. */
    @Override
    public boolean fits(ZeppelinSize size) {
        return true;
    }

    @Override
    public void build() {
        ZeppelinLayout.Car car = layout.car();
        for (int side : SIDES) {
            BlockPos light = new BlockPos(side * (car.half() - LIGHT_IN), car.floor() - 1, car.front() + LIGHT_AFT);
            BlockPos handle = new BlockPos(side * HANDLE_OUT, car.floor() + 1, car.lounge() + HANDLE_AFT);
            // The car's inside is laid as air, so it is clear of whatever is laid there that is not.
            if (layout.isOpen(light.getX(), light.getY(), light.getZ()) && layout.stateAt(handle.getX(), handle.getY(), handle.getZ()).isAir()) {
                rig(light, handle);
            }
        }
    }

    /** A light and its handle, the handle told where its light hangs. */
    private void rig(BlockPos light, BlockPos handle) {
        layout.set(light.getX(), light.getY(), light.getZ(), AerolargeRegistry.SEARCHLIGHT.get().defaultBlockState());
        layout.set(handle.getX(), handle.getY(), handle.getZ(),
            AerolargeRegistry.SEARCHLIGHT_HANDLE.get().defaultBlockState().setValue(SearchlightHandleBlock.FACING, Direction.NORTH));
        layout.fit(handle, (blockEntity, rotation) -> {
            if (blockEntity instanceof SearchlightHandleBlockEntity rigged) {
                rigged.rig(light.subtract(handle).rotate(rotation));
            }
        });
    }
}
