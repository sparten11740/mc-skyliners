package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A piece of an airliner's lounge bar: a white front that curves out from a dark recessed plinth up under a dark stone
 * top, with a strip of LED light glowing along its foot whatever the light around. Side by side the pieces run on into
 * one bar. It is a whole block to stand things on, a coffee machine or a glass. FACING is the way its front faces,
 * toward whoever stands at the bar.
 */
public class BarCounterBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<BarCounterBlock> CODEC = simpleCodec(BarCounterBlock::new);

    public BarCounterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Its front toward whoever puts it down. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}
