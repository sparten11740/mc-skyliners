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
 * The DJ deck in a zeppelin's secret techno room: a black desk with two turntables and a mixer, glowing at its edges,
 * that plays the room (see {@link TechnoDeckBlockEntity}): the music starts for whoever walks in, builds and drops,
 * and the room's lights go with it, from the dance floor's tiles to the beams sweeping over the dancers (see the
 * client's {@code TechnoRoomRenderer}). FACING is the way the DJ looks, over the deck at the dance floor.
 */
public class TechnoDeckBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<TechnoDeckBlock> CODEC = simpleCodec(TechnoDeckBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 13, 16);

    public TechnoDeckBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Put down by hand, the DJ looks the way whoever puts it down looks. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TechnoDeckBlockEntity(pos, state);
    }
}
