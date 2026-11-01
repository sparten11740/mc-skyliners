package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A lift's picture board's block entity, in the part holding the board: its lift and landing (see
 * {@link LiftStopBlockEntity}), which its renderer reads the floors to draw from.
 */
public class LiftBoardBlockEntity extends LiftStopBlockEntity {
    public LiftBoardBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.LIFT_BOARD_ENTITY.get(), pos, state);
    }
}
