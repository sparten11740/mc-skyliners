package dev.aerolarge.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The steel hub at the very middle of a launch pad's grate, a yellow cross on it, under a rocket's axis as she stands
 * on the pad. Only the blueprint places it, so it has no item, and it gives nothing back (see
 * {@link LaunchPadBlockEntity}).
 */
public class LaunchPadHubBlock extends Block implements EntityBlock {
    public LaunchPadHubBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaunchPadBlockEntity(pos, state);
    }
}
