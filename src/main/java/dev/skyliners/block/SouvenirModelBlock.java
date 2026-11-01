package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A souvenir model on a stand, for a desk or a shelf: of the airliner, or of the zeppelin. FACING is the way its
 * nose points: toward whoever put it down. A blueprint's preview shows its airframe's model (see
 * {@link dev.skyliners.item.AirframeBlueprintItem}).
 */
public class SouvenirModelBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<SouvenirModelBlock> CODEC = simpleCodec(SouvenirModelBlock::new);
    /** The model on its stand, the airliner's wings or the zeppelin's fins and all, whichever way it points. */
    private static final VoxelShape SHAPE = Block.box(1, 0, 0, 15, 13, 16);

    public SouvenirModelBlock(Properties properties) {
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

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
