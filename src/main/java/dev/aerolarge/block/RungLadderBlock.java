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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A ladder of rungs fixed to a wall and climbed as any ladder is (it is in the climbable tag): the diving tower's
 * chrome pool ladder, or the gym hall's wall bars (Sprossenwand). Pieces stacked one on another make one ladder, and
 * the TOP piece, with no more of it above, finishes it off: the pool ladder's rails curl over into grab handles down
 * onto the floor behind them, and the wall bars' top rung stands out from the wall on brackets, to hang from. FACING
 * is the way it faces, out from the wall it is fixed to.
 */
public class RungLadderBlock extends LadderBlock implements Joining {
    private static final MapCodec<LadderBlock> POOL_LADDER_CODEC = simpleCodec(properties -> new RungLadderBlock(properties, Kind.POOL_LADDER));
    private static final MapCodec<LadderBlock> WALL_BARS_CODEC = simpleCodec(properties -> new RungLadderBlock(properties, Kind.WALL_BARS));
    public static final BooleanProperty TOP = BooleanProperty.create("top");

    /**
     * The ladders there are, and how far each stands out from its wall, as drawn facing north (its wall south): any
     * piece of it, and its top piece.
     */
    public enum Kind {
        /** Two chrome rails with a non-slip tread between them every half block, standing off the wall on brackets. */
        POOL_LADDER(Block.box(0, 0, 8, 16, 16, 16), Block.box(0, 0, 8, 16, 16, 16)),
        /** Wooden uprights at its sides with four round rungs between them, the top one hanging out on brackets. */
        WALL_BARS(Block.box(0, 0, 11, 16, 16, 16), Shapes.or(Block.box(0, 0, 11, 16, 16, 16), Block.box(0, 13.5, 7, 16, 15, 11)));

        private final Map<Direction, VoxelShape> pieces;
        private final Map<Direction, VoxelShape> tops;

        Kind(VoxelShape piece, VoxelShape top) {
            this.pieces = TurnedShapes.of(piece);
            this.tops = TurnedShapes.of(top);
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
        return kind == Kind.POOL_LADDER ? POOL_LADDER_CODEC : WALL_BARS_CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(TOP));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(TOP) ? kind.tops : kind.pieces).get(state.getValue(FACING));
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
