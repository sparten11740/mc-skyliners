package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A gym's climbing rope: a thick braided rope hanging from a ceiling, climbed as a ladder is (it is in the climbable
 * tag): jump up into it and keep climbing. Pieces hung one under another make one rope (UP and DOWN say which ways it
 * runs on); at the top it hangs from a steel eye plate, and at its foot it ends in a knot. It is climbed from inside
 * its block, so nothing bumps into it: up the zeppelin's shafts (see {@code ZeppelinRopes}) its cage of girders leaves
 * no room beside it, and solid it could not be climbed at all. Whatever it hangs from taken away, it comes down.
 */
public class ClimbingRopeBlock extends Block implements Joining {
    private static final MapCodec<ClimbingRopeBlock> CODEC = simpleCodec(ClimbingRopeBlock::new);
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    /** The rope, as thick as it is drawn, to point at. */
    private static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 16, 10);

    public ClimbingRopeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(UP, DOWN);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** It hangs from more of the rope, or from anything solid over it. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos above = pos.above();
        BlockState over = level.getBlockState(above);
        return over.is(this) || !over.getCollisionShape(level, above).isEmpty();
    }

    /** Hung from the underside of a block, or from the foot of a rope. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        BlockState state = joined(defaultBlockState(), way -> context.getLevel().getBlockState(pos.relative(way)));
        return state.canSurvive(context.getLevel(), pos) ? state : null;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (direction == Direction.UP && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return joined(state, way -> level.getBlockState(pos.relative(way)));
    }

    /** Running on into the rope above it and below it. */
    @Override
    public BlockState joined(BlockState state, Function<Direction, BlockState> beside) {
        return state.setValue(UP, beside.apply(Direction.UP).is(this)).setValue(DOWN, beside.apply(Direction.DOWN).is(this));
    }
}
