package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
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
 * A strip of LED light along the top of a wall, where it meets the ceiling: the cabin's modern indirect lighting.
 * FACING is the wall it is fixed to; strips side by side run on as one. As bright as the flight deck's dimmer has it,
 * or red for the alarm.
 */
public class LightStripBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<LightStripBlock> CODEC = simpleCodec(LightStripBlock::new);
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
        Direction.NORTH, Block.box(0, 12, 0, 16, 15, 3),
        Direction.SOUTH, Block.box(0, 12, 13, 16, 15, 16),
        Direction.WEST, Block.box(0, 12, 0, 3, 15, 16),
        Direction.EAST, Block.box(13, 12, 0, 16, 15, 16)
    );

    public LightStripBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(Brightness.PROPERTY, Brightness.BRIGHT).setValue(Brightness.ALARM, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, Brightness.PROPERTY, Brightness.ALARM);
    }

    /** Put up by hand only on a wall, fixed to it: set down on a floor, it stood in the air at the top of its block. */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction wall = Mounting.support(context, List.of(Mount.WALL));
        return wall == null ? null : defaultBlockState().setValue(FACING, wall);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }
}
