package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A ladder of rungs fixed to a wall and climbed as any ladder is (it is in the climbable tag): the diving tower's
 * chrome pool ladder. Pieces stacked one on another make one ladder, and the TOP piece, with no more of it above,
 * finishes it off: the pool ladder's rails curl over into grab handles down onto the floor behind them. FACING is the
 * way it faces, out from the wall it is fixed to.
 */
public class RungLadderBlock extends LadderBlock implements Joining {
    private static final MapCodec<LadderBlock> POOL_LADDER_CODEC = simpleCodec(properties -> new RungLadderBlock(properties, Kind.POOL_LADDER));
    public static final BooleanProperty TOP = BooleanProperty.create("top");

    /** The ladders there are, and how far each stands out from its wall, as drawn facing north (its wall south). */
    public enum Kind {
        /** Two chrome rails with a non-slip tread between them every half block, standing off the wall on brackets. */
        POOL_LADDER(Block.box(0, 0, 8, 16, 16, 16));

        private final Map<Direction, VoxelShape> shapes;

        Kind(VoxelShape north) {
            this.shapes = TurnedShapes.of(north);
        }
    }

    private final Kind kind;

    public RungLadderBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(TOP, true));
    }

    @Override
    public MapCodec<LadderBlock> codec() {
        return POOL_LADDER_CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(TOP));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return kind.shapes.get(state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        BlockPos pos = context.getClickedPos();
        return state == null ? null : joined(state, way -> context.getLevel().getBlockState(pos.relative(way)));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        BlockState updated = super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
        return updated.is(this) ? joined(updated, way -> level.getBlockState(pos.relative(way))) : updated;
    }

    /** The top of the ladder: no more of it right above. */
    @Override
    public BlockState joined(BlockState state, Function<Direction, BlockState> beside) {
        return state.setValue(TOP, !beside.apply(Direction.UP).is(this));
    }
}
