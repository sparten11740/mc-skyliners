package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A basketball backboard of toughened glass, a pane along the front of its block in a brushed aluminium frame. Pieces
 * side by side and one over another, facing the same way, make one board, framed only round its outside (LEFT, RIGHT,
 * UP and DOWN say which ways it runs on, LEFT and RIGHT as seen from in front of it); a piece with the board running on
 * either side of it and either over it or under it shows its half of the red shooter's square, so a board three wide
 * and two high has the square over the hoop, as a real one has. A hoop put up against its front is mounted on it.
 * FACING is the way its front faces, out over the court.
 */
public class GlassBackboardBlock extends HorizontalDirectionalBlock implements Joining {
    private static final MapCodec<GlassBackboardBlock> CODEC = simpleCodec(GlassBackboardBlock::new);
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    /** The pane facing north, along the north side of its block, two pixels thick with its frame. */
    private static final Map<Direction, VoxelShape> SHAPES = TurnedShapes.of(Block.box(0, 0, 0, 16, 16, 2));

    public GlassBackboardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
            .setValue(LEFT, false).setValue(RIGHT, false).setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEFT, RIGHT, UP, DOWN);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Its front toward whoever puts it up. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return joined(state, way -> context.getLevel().getBlockState(pos.relative(way)));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return joined(state, way -> level.getBlockState(pos.relative(way)));
    }

    /** Running on into the board beside it, over it and under it: pieces facing the same way. */
    @Override
    public BlockState joined(BlockState state, Function<Direction, BlockState> beside) {
        Direction front = state.getValue(FACING);
        return state.setValue(LEFT, sameBoard(state, beside.apply(front.getClockWise())))
            .setValue(RIGHT, sameBoard(state, beside.apply(front.getCounterClockWise())))
            .setValue(UP, sameBoard(state, beside.apply(Direction.UP)))
            .setValue(DOWN, sameBoard(state, beside.apply(Direction.DOWN)));
    }

    private boolean sameBoard(BlockState piece, BlockState other) {
        return other.is(this) && other.getValue(FACING) == piece.getValue(FACING);
    }

    /** Mirrored, its left is its right. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = super.mirror(state, mirror);
        return mirror == Mirror.NONE ? mirrored : mirrored.setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT));
    }
}
