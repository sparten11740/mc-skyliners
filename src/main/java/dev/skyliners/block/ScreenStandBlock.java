package dev.skyliners.block;

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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The stand a passenger screen stands on before each armchair on a zeppelin's promenades: a brass foot and a teak post
 * along the back of its block, rising into the casing of the screen over it, so the screen is at the eyes of whoever
 * sits in the chair rather than on the floor at their feet. FACING is the way the screen on it faces, as the screen's
 * own does, the post at its back.
 */
public class ScreenStandBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<ScreenStandBlock> CODEC = simpleCodec(ScreenStandBlock::new);
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
        Direction.NORTH, Shapes.or(Block.box(4, 0, 10, 12, 1, 16), Block.box(7, 1, 14, 9, 16, 16)),
        Direction.SOUTH, Shapes.or(Block.box(4, 0, 0, 12, 1, 6), Block.box(7, 1, 0, 9, 16, 2)),
        Direction.WEST, Shapes.or(Block.box(10, 0, 4, 16, 1, 12), Block.box(14, 1, 7, 16, 16, 9)),
        Direction.EAST, Shapes.or(Block.box(0, 0, 4, 6, 1, 12), Block.box(0, 1, 7, 2, 16, 9))
    );

    public ScreenStandBlock(Properties properties) {
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

    /** Put down by hand, it faces whoever puts it down, as a screen put on it then does. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }
}
