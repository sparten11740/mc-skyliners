package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
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
 * An emergency exit sign over a door: a green running man heading out through a doorway, the sign every airliner has
 * over its exits and the one passengers who cannot read yet know. It glows whatever the light around, the way a
 * backlit sign does, so it shows with the cabin dark, and casts a little light of its own round the door. FACING is
 * the way the sign faces, its back to whatever it is fixed to.
 */
public class ExitSignBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<ExitSignBlock> CODEC = simpleCodec(ExitSignBlock::new);
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
        Direction.NORTH, Block.box(1, 4, 14.5, 15, 12, 16),
        Direction.SOUTH, Block.box(1, 4, 0, 15, 12, 1.5),
        Direction.WEST, Block.box(14.5, 4, 1, 16, 12, 15),
        Direction.EAST, Block.box(0, 4, 1, 1.5, 12, 15)
    );

    public ExitSignBlock(Properties properties) {
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

    /** Against the side of a block it faces out from it; nowhere else, as its back is fixed to a wall. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = Mounting.onWall(context);
        return facing == null ? null : defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }
}
