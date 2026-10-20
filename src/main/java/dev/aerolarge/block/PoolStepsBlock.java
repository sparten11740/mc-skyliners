package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Steps down into a swimming pool: stairs tiled as the pool's deck is, each tread a non-slip tile with a dark blue
 * edge along its front, so it shows under the water, the risers tiled the same. At either end of a flight (RAIL_LEFT
 * and RAIL_RIGHT, as someone climbing out sees it) a chrome grab rail curves up from the lower tread over the upper
 * one; steps side by side, climbing the same way, make one flight with a rail at each end. Otherwise a stair like
 * vanilla's: it climbs away from whoever puts it down, turns corners and fills with water the same.
 */
public class PoolStepsBlock extends StairBlock implements Joining {
    private static final MapCodec<PoolStepsBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        BlockState.CODEC.fieldOf("base_state").forGetter(steps -> steps.baseState), propertiesCodec()
    ).apply(instance, PoolStepsBlock::new));
    public static final BooleanProperty RAIL_LEFT = BooleanProperty.create("rail_left");
    public static final BooleanProperty RAIL_RIGHT = BooleanProperty.create("rail_right");

    public PoolStepsBlock(BlockState base, Properties properties) {
        super(base, properties);
        registerDefaultState(defaultBlockState().setValue(RAIL_LEFT, true).setValue(RAIL_RIGHT, true));
    }

    @Override
    public MapCodec<? extends StairBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(RAIL_LEFT, RAIL_RIGHT));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        return joined(super.getStateForPlacement(context), way -> context.getLevel().getBlockState(pos.relative(way)));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return joined(super.updateShape(state, direction, neighbour, level, pos, neighbourPos), way -> level.getBlockState(pos.relative(way)));
    }

    /** A rail at each end of the flight: on either side with no step of the same flight beside it. */
    @Override
    public BlockState joined(BlockState state, Function<Direction, BlockState> beside) {
        Direction climb = state.getValue(FACING);
        return state.setValue(RAIL_LEFT, !sameFlight(state, beside.apply(climb.getCounterClockWise())))
            .setValue(RAIL_RIGHT, !sameFlight(state, beside.apply(climb.getClockWise())));
    }

    private boolean sameFlight(BlockState step, BlockState other) {
        return other.is(this) && other.getValue(FACING) == step.getValue(FACING) && other.getValue(HALF) == step.getValue(HALF);
    }

    /** Mirrored, the left end of the flight is its right. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = super.mirror(state, mirror);
        return mirror == Mirror.NONE ? mirrored : mirrored.setValue(RAIL_LEFT, state.getValue(RAIL_RIGHT)).setValue(RAIL_RIGHT, state.getValue(RAIL_LEFT));
    }
}
