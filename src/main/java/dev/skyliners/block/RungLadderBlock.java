package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
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
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A ladder of rungs fixed to a wall and climbed as any ladder is (it is in the climbable tag): the diving tower's
 * chrome pool ladder, the gym hall's wall bars (Sprossenwand), or the ladder up the foot end of an upper bunk (see
 * {@link BunkBlock.Tier}). Pieces stacked one on another make one ladder, and the TOP piece, with no more of it above,
 * finishes it off: the pool ladder's rails curl over into grab handles down onto the floor behind them, the wall bars'
 * top rung stands out from the wall on brackets, to hang from, and the bunk ladder's uprights hook over the upper
 * bunk's footboard. FACING is the way it faces, out from the wall it is fixed to. A bunk ladder has no wall: it stands
 * on the floor, or on its piece below, against the end of the bunk bed it climbs, and goes with it.
 */
public class RungLadderBlock extends LadderBlock implements Joining {
    private static final MapCodec<LadderBlock> POOL_LADDER_CODEC = simpleCodec(properties -> new RungLadderBlock(properties, Kind.POOL_LADDER));
    private static final MapCodec<LadderBlock> WALL_BARS_CODEC = simpleCodec(properties -> new RungLadderBlock(properties, Kind.WALL_BARS));
    private static final MapCodec<LadderBlock> BUNK_LADDER_CODEC = simpleCodec(properties -> new RungLadderBlock(properties, Kind.BUNK_LADDER));
    public static final BooleanProperty TOP = BooleanProperty.create("top");

    /**
     * The ladders there are, and how far each stands out from its wall, as drawn facing north (its wall south): any
     * piece of it, and its top piece.
     */
    public enum Kind {
        /** Two chrome rails with a non-slip tread between them every half block, standing off the wall on brackets. */
        POOL_LADDER(Block.box(0, 0, 8, 16, 16, 16), Block.box(0, 0, 8, 16, 16, 16)),
        /** Wooden uprights at its sides with four round rungs between them, the top one hanging out on brackets. */
        WALL_BARS(Block.box(0, 0, 11, 16, 16, 16), Shapes.or(Block.box(0, 0, 11, 16, 16, 16), Block.box(0, 13.5, 7, 16, 15, 11))),
        /**
         * White steel uprights with a round rung between them every quarter block, up against the bunk's end; its top
         * piece stands only half as high, under the upper bunk's footboard, so stepping off the bunk onto the ladder
         * the feet go down into it and hold on, instead of up onto it and over.
         */
        BUNK_LADDER(Block.box(1, 0, 13, 15, 16, 16), Block.box(1, 0, 13, 15, 8, 16));

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
        return switch (kind) {
            case POOL_LADDER -> POOL_LADDER_CODEC;
            case WALL_BARS -> WALL_BARS_CODEC;
            case BUNK_LADDER -> BUNK_LADDER_CODEC;
        };
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

    /** A bunk ladder stands on the floor or on its piece below; the others hang on their wall, as any ladder does. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (kind != Kind.BUNK_LADDER) {
            return super.canSurvive(state, level, pos);
        }
        BlockPos below = pos.below();
        BlockState under = level.getBlockState(below);
        return under.is(this) || under.isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (kind == Kind.BUNK_LADDER && direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        BlockState updated = super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
        return updated.is(this) ? joined(updated, way -> level.getBlockState(pos.relative(way))) : updated;
    }

    /** The top of the ladder: no more of it right above. */
    @Override
    public BlockState joined(BlockState state, Function<Direction, BlockState> beside) {
        return state.setValue(TOP, !beside.apply(Direction.UP).is(this));
    }
}
