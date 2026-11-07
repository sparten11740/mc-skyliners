package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The crossbow rack's block entity (see {@link CrossbowRackBlock}): it keeps nothing, whether the crossbow is racked
 * being its block's state, and is only there for the client's {@code CrossbowRackRenderer} to draw the crossbow on it.
 */
public class CrossbowRackBlockEntity extends BlockEntity {
    public CrossbowRackBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.CROSSBOW_RACK_ENTITY.get(), pos, state);
    }
}
