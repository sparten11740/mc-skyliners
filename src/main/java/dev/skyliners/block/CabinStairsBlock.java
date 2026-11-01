package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A carpeted stair, as an airliner's staircase between its decks has: two half-height steps to a block, as vanilla
 * stairs have, so each step is half a block up and the stair is walked up without a jump. Each tread is carpeted in
 * the cabin's blue between white stringers, with a slim brushed-steel nosing along its front edge; the risers and
 * the sides are white. FACING is the way it climbs, its upper step on that side.
 *
 * <p>Turned over (HALF top) it is the underside of the step above it, all white, so a flight's soffit slopes down with
 * it; put down, it turns over the way vanilla stairs do. It outlines and bumps into exactly its steps.
 */
public class CabinStairsBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<CabinStairsBlock> CODEC = simpleCodec(CabinStairsBlock::new);
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    /** The steps facing north: the lower one across the whole block, the upper one over its north half. */
    private static final Map<Direction, VoxelShape> STEPS = TurnedShapes.of(Shapes.or(Block.box(0, 0, 0, 16, 8, 16), Block.box(0, 8, 0, 16, 16, 8)));
    /** Turned over, facing north: the top half across the whole block, the north half under it. */
    private static final Map<Direction, VoxelShape> SOFFIT = TurnedShapes.of(Shapes.or(Block.box(0, 8, 0, 16, 16, 16), Block.box(0, 0, 0, 16, 8, 8)));
    private static final Map<Half, Map<Direction, VoxelShape>> SHAPES = new EnumMap<>(Map.of(Half.BOTTOM, STEPS, Half.TOP, SOFFIT));

    public CabinStairsBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, Half.BOTTOM));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    /** Climbing away from whoever puts it down; turned over against the underside of a block or high on a side. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        boolean over = face == Direction.DOWN || face != Direction.UP && context.getClickLocation().y - context.getClickedPos().getY() > 0.5;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection()).setValue(HALF, over ? Half.TOP : Half.BOTTOM);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(HALF)).get(state.getValue(FACING));
    }

    /** Light passes the open half of the block, as it does a vanilla stair's. */
    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    /** Whether a block is a stair right way up whose steps a rail over it follows: these, or vanilla's. */
    public static boolean isStep(BlockState state) {
        return (state.getBlock() instanceof CabinStairsBlock || state.getBlock() instanceof StairBlock)
            && state.getValue(BlockStateProperties.HALF) == Half.BOTTOM;
    }
}
