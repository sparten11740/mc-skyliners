package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Airframe skin with its outer edge cut away at 45 degrees, laid on the outer steps of the hull, nacelles and wings
 * so their stepped curves read as smooth facets. FACING is the side the cut faces and HALF whether it is the top or
 * the bottom edge; the cut runs along the block, across FACING. The two inner faces are whole, so from inside the
 * cabin it is an ordinary wall. Put down by hand it sits as stairs do (see {@link #getStateForPlacement}), and as
 * stairs do it turns a corner where it meets another fairing cut across it (see {@link #shapeAt}). SHAPE says which:
 * straight, or a corner with a second cut a quarter turn from FACING, anticlockwise seen from above for the left-hand
 * shapes and clockwise for the right-hand ones (see {@link #secondCut}). An inner corner keeps all that either cut
 * keeps, so the two slopes meet in a valley; an outer corner keeps only what both keep, so they meet in a hip.
 */
public class FairingBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<FairingBlock> CODEC = simpleCodec(FairingBlock::new);
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final EnumProperty<StairsShape> SHAPE = BlockStateProperties.STAIRS_SHAPE;
    /** The steps under each state's slope, for collision and for the light it shuts out (see {@link #stepsUnder}). */
    private final Map<BlockState, VoxelShape> steps;

    public FairingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, Half.TOP).setValue(SHAPE, StairsShape.STRAIGHT));
        steps = getShapeForEachState(FairingBlock::stepsUnder);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, SHAPE);
    }

    /**
     * Put down the way stairs are. Its cut faces whoever puts it down, as the airliner's face out of the skin toward
     * whoever stands beside it, so skinning a wing or a nacelle from outside cuts the edges facing out. It rests cut
     * along its top edge, put on top of a block or against the lower half of a block's side, and hangs cut along its
     * bottom edge, put against the underside of a block or the upper half of a block's side, for the underside of a
     * curve. It turns a corner at once if the fairings beside it make one.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        boolean upperHalf = context.getClickLocation().y - context.getClickedPos().getY() > 0.5;
        boolean hangs = clicked == Direction.DOWN || clicked != Direction.UP && upperHalf;
        BlockState placed = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(HALF, hangs ? Half.BOTTOM : Half.TOP);
        return placed.setValue(SHAPE, shapeAt(placed, context.getLevel(), context.getClickedPos()));
    }

    /**
     * Turns a corner, or straightens out again, as the fairings beside it come and go. None of the fairings an
     * airliner's layout lays meets another cut across it, so they stay straight when its assembly runs these updates.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return direction.getAxis().isHorizontal()
            ? state.setValue(SHAPE, shapeAt(state, level, pos))
            : super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    /**
     * The shape a fairing takes among the fairings round it, worked out as stairs work out theirs. One behind it, on
     * its solid side and cut across it, makes it the outer corner of the two runs; failing that, one ahead of it,
     * beyond its cut and cut across it, makes it the inner corner. Either way it takes that one's cut as its second,
     * and only a fairing of the same half counts. It stays straight where a fairing cut its own way stands on the far
     * side of the corner, carrying its run on straight through it.
     */
    private static StairsShape shapeAt(BlockState state, BlockGetter level, BlockPos pos) {
        Direction cut = state.getValue(FACING);
        BlockState behind = level.getBlockState(pos.relative(cut.getOpposite()));
        if (cutAcross(state, behind) && !sameRun(state, level.getBlockState(pos.relative(behind.getValue(FACING))))) {
            return behind.getValue(FACING) == cut.getCounterClockWise() ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT;
        }
        BlockState ahead = level.getBlockState(pos.relative(cut));
        if (cutAcross(state, ahead) && !sameRun(state, level.getBlockState(pos.relative(ahead.getValue(FACING).getOpposite())))) {
            return ahead.getValue(FACING) == cut.getCounterClockWise() ? StairsShape.INNER_LEFT : StairsShape.INNER_RIGHT;
        }
        return StairsShape.STRAIGHT;
    }

    /** Whether another block is a fairing of the same half cut a quarter turn from this one: a corner's other run. */
    private static boolean cutAcross(BlockState state, BlockState other) {
        return other.getBlock() instanceof FairingBlock && other.getValue(HALF) == state.getValue(HALF)
            && other.getValue(FACING).getAxis() != state.getValue(FACING).getAxis();
    }

    /** Whether another block is a fairing cut the same way along the same edge, in one straight run with this one. */
    private static boolean sameRun(BlockState state, BlockState other) {
        return other.getBlock() instanceof FairingBlock && other.getValue(HALF) == state.getValue(HALF) && other.getValue(FACING) == state.getValue(FACING);
    }

    /** The way a corner's second cut faces: a quarter turn from FACING, anticlockwise seen from above if left-hand. */
    private static Direction secondCut(BlockState state) {
        Direction cut = state.getValue(FACING);
        return switch (state.getValue(SHAPE)) {
            case INNER_LEFT, OUTER_LEFT -> cut.getCounterClockWise();
            default -> cut.getClockWise();
        };
    }

    /** Mirrored, its cut turns as any facing does, and a left-hand corner becomes a right-hand one and back. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = super.mirror(state, mirror);
        return mirror == Mirror.NONE ? mirrored : mirrored.setValue(SHAPE, otherHand(state.getValue(SHAPE)));
    }

    private static StairsShape otherHand(StairsShape shape) {
        return switch (shape) {
            case INNER_LEFT -> StairsShape.INNER_RIGHT;
            case INNER_RIGHT -> StairsShape.INNER_LEFT;
            case OUTER_LEFT -> StairsShape.OUTER_RIGHT;
            case OUTER_RIGHT -> StairsShape.OUTER_LEFT;
            case STRAIGHT -> StairsShape.STRAIGHT;
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return steps.get(state);
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    /**
     * The steps under a state's slope: four, each a quarter block high, stepping in toward the cut. A corner's are
     * those of both its cuts, all that either holds at an inner corner and only what both hold at an outer one.
     */
    private static VoxelShape stepsUnder(BlockState state) {
        boolean hangs = state.getValue(HALF) == Half.BOTTOM;
        VoxelShape shape = Shapes.empty();
        for (int k = 0; k < 4; k++) {
            // How far toward the cut this step reaches, and which quarter of the height it fills.
            double reach = 14 - 4 * k;
            double y0 = hangs ? 12 - 4 * k : 4 * k;
            VoxelShape step = step(state.getValue(FACING), reach, y0);
            shape = Shapes.or(shape, switch (state.getValue(SHAPE)) {
                case STRAIGHT -> step;
                case INNER_LEFT, INNER_RIGHT -> Shapes.or(step, step(secondCut(state), reach, y0));
                case OUTER_LEFT, OUTER_RIGHT -> Shapes.join(step, step(secondCut(state), reach, y0), BooleanOp.AND);
            });
        }
        return shape.optimize();
    }

    /** One step under a cut facing this way, from the side away from it this far toward it, a quarter block high. */
    private static VoxelShape step(Direction cut, double reach, double y0) {
        return switch (cut) {
            case EAST -> Block.box(0, y0, 0, reach, y0 + 4, 16);
            case WEST -> Block.box(16 - reach, y0, 0, 16, y0 + 4, 16);
            case SOUTH -> Block.box(0, y0, 0, 16, y0 + 4, reach);
            default -> Block.box(0, y0, 16 - reach, 16, y0 + 4, 16);
        };
    }
}
