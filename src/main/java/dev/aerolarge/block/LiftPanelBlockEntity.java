package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Holds nothing: the panel reads its lift live, on the client, as its renderer draws it, so it shows the same beside a
 * landing and carried in a cabin, where Create only draws it.
 */
public class LiftPanelBlockEntity extends BlockEntity {
    public LiftPanelBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.LIFT_PANEL_ENTITY.get(), pos, state);
    }
}
