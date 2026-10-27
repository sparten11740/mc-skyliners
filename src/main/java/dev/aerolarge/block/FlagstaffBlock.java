package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A length of white-painted flagstaff, stacked up from a {@link FlagHalyardBlock} at its foot, the ship's colours
 * flown from its top. TOP is whether nothing more of the staff stands on it: the topmost length carries the staff's
 * gilded truck, the ball on its tip.
 */
public class FlagstaffBlock extends Block {
    private static final MapCodec<FlagstaffBlock> CODEC = simpleCodec(FlagstaffBlock::new);
    public static final BooleanProperty TOP = BlockStateProperties.UP;
    private static final VoxelShape POLE = Block.box(6.5, 0, 6.5, 9.5, 16, 9.5);
    private static final VoxelShape TOPPED = Shapes.or(Block.box(6.5, 0, 6.5, 9.5, 13, 9.5), Block.box(5.5, 13, 5.5, 10.5, 16, 10.5));

    public FlagstaffBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TOP, true));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOP);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(TOP, !context.getLevel().getBlockState(context.getClickedPos().above()).is(this));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return direction == Direction.UP ? state.setValue(TOP, !neighbour.is(this)) : state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(TOP) ? TOPPED : POLE;
    }
}
