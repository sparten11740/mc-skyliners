package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The coping round a swimming pool's edge: a white stone kerb, its front edge rounded over the water, and on its front
 * under the round a band of dark blue tiles along the waterline over the pool's mosaic. FACING is the way its rounded
 * edge looks, out over the water. A CORNER piece, where two edges of the pool meet and the water is only off its
 * corner, is the kerb's plain top all over. Put down, its rounded edge faces away from you, out over the water you
 * look at; sneaking, it is a corner piece. It is a whole block to stand on, as the floor round the pool is.
 */
public class PoolCopingBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<PoolCopingBlock> CODEC = simpleCodec(PoolCopingBlock::new);
    public static final BooleanProperty CORNER = BooleanProperty.create("corner");

    public PoolCopingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CORNER, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CORNER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection()).setValue(CORNER, context.isSecondaryUseActive());
    }
}
