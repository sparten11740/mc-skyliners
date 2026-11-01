package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A lift's call button's block entity: the lift it calls and the landing it calls it to (see
 * {@link LiftStopBlockEntity}).
 */
public class LiftCallButtonBlockEntity extends LiftStopBlockEntity {
    public LiftCallButtonBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.LIFT_CALL_BUTTON_ENTITY.get(), pos, state);
    }
}
