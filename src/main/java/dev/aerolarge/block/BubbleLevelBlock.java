package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A bubble level in a zeppelin's control car: a brass-bound glass tube of green spirit on a wooden stand, its bubble
 * drawn sliding toward whichever end of the ship is higher as she pitches (see the client's
 * {@code BubbleLevelRenderer}), and a mark in its middle where she is level. FACING is the way whoever reads it looks;
 * the tube runs across their view, their right hand toward the end clockwise of it.
 */
public class BubbleLevelBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<BubbleLevelBlock> CODEC = simpleCodec(BubbleLevelBlock::new);
    private static final VoxelShape SHAPE = Block.box(1, 0, 4, 15, 9, 12);
    private static final VoxelShape ACROSS = Block.box(4, 0, 1, 12, 9, 15);

    public BubbleLevelBlock(Properties properties) {
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

    /** Put down by hand, it is read by whoever puts it down. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE : ACROSS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BubbleLevelBlockEntity(pos, state);
    }
}
