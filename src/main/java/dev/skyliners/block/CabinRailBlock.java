package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A glass rail, as round an airliner's stairwell: frameless glass between slim brushed-steel posts under a steel
 * handrail, standing along the edges of its block rather than across its middle, so the block it stands in is still
 * walked through. NORTH, EAST, SOUTH and WEST are the edges it stands along, any of them, joining at the corners.
 *
 * <p>Over a stair (RAKE: the way the stair under it climbs) a rail along the stair's sides follows it, its glass down
 * to the treads and its handrail sloping with the flight, so it runs on from the rail on the landing at either end;
 * anywhere else it is level, its handrail a little over a block above the floor. It keeps anyone from stepping or
 * jumping over it, as a fence does.
 */
public class CabinRailBlock extends Block {
    private static final MapCodec<CabinRailBlock> CODEC = simpleCodec(CabinRailBlock::new);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final EnumProperty<Rake> RAKE = EnumProperty.create("rake", Rake.class);
    /** Each edge's property. */
    public static final Map<Direction, BooleanProperty> EDGES = new EnumMap<>(Map.of(
        Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH, Direction.WEST, WEST));
    /** How far in from the edge the rail reaches, how high its handrail stands and how high it stops a jump, in pixels. */
    private static final int THICKNESS = 2;
    private static final int HANDRAIL = 20;
    private static final int GUARD = 24;
    private static final Map<Direction, VoxelShape> OUTLINES = TurnedShapes.of(Block.box(0, 0, 0, 16, HANDRAIL, THICKNESS));
    private static final Map<Direction, VoxelShape> GUARDS = TurnedShapes.of(Block.box(0, 0, 0, 16, GUARD, THICKNESS));

    /** The way the stair under a rail climbs, which a rail along its sides slopes with; level over anything else. */
    public enum Rake implements StringRepresentable {
        LEVEL(null), NORTH(Direction.NORTH), EAST(Direction.EAST), SOUTH(Direction.SOUTH), WEST(Direction.WEST);

        @Nullable
        private final Direction climb;

        Rake(@Nullable Direction climb) {
            this.climb = climb;
        }

        static Rake of(@Nullable Direction climb) {
            for (Rake rake : values()) {
                if (rake.climb == climb) {
                    return rake;
                }
            }
            return LEVEL;
        }

        Rake rotate(Rotation rotation) {
            return climb == null ? this : of(rotation.rotate(climb));
        }

        Rake mirror(Mirror mirror) {
            return climb == null ? this : of(mirror.mirror(climb));
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public CabinRailBlock(BlockBehaviour.Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(RAKE, Rake.LEVEL);
        for (BooleanProperty edge : EDGES.values()) {
            state = state.setValue(edge, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, RAKE);
    }

    /**
     * A rail along an edge of a block, added to whatever rail already stands there (or a new one, over anything else),
     * sloping with the stair under it if there is one: for laying a rail out with a staircase.
     */
    public static BlockState along(BlockState there, Direction edge, BlockState below, BlockState rail) {
        BlockState state = there.is(rail.getBlock()) ? there : rail;
        return state.setValue(EDGES.get(edge), true).setValue(RAKE, rakeOver(below));
    }

    private static Rake rakeOver(BlockState below) {
        return CabinStairsBlock.isStep(below) ? Rake.of(below.getValue(BlockStateProperties.HORIZONTAL_FACING)) : Rake.LEVEL;
    }

    /** Along the edge the one putting it down faces, added to the rail already there when put into it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState there = context.getLevel().getBlockState(context.getClickedPos());
        return along(there, context.getHorizontalDirection(), context.getLevel().getBlockState(context.getClickedPos().below()), defaultBlockState());
    }

    /** Another rail goes into this one along an edge it has none on yet. */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return !context.isSecondaryUseActive() && context.getItemInHand().is(asItem()) && !state.getValue(EDGES.get(context.getHorizontalDirection()))
            || super.canBeReplaced(state, context);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN ? state.setValue(RAKE, rakeOver(neighbor)) : state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return edges(state, OUTLINES);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return edges(state, GUARDS);
    }

    private static VoxelShape edges(BlockState state, Map<Direction, VoxelShape> shapes) {
        VoxelShape shape = Shapes.empty();
        for (Map.Entry<Direction, BooleanProperty> edge : EDGES.entrySet()) {
            if (state.getValue(edge.getValue())) {
                shape = Shapes.or(shape, shapes.get(edge.getKey()));
            }
        }
        return shape;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState turned = state.setValue(RAKE, state.getValue(RAKE).rotate(rotation));
        for (Map.Entry<Direction, BooleanProperty> edge : EDGES.entrySet()) {
            turned = turned.setValue(EDGES.get(rotation.rotate(edge.getKey())), state.getValue(edge.getValue()));
        }
        return turned;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState turned = state.setValue(RAKE, state.getValue(RAKE).mirror(mirror));
        for (Map.Entry<Direction, BooleanProperty> edge : EDGES.entrySet()) {
            turned = turned.setValue(EDGES.get(mirror.mirror(edge.getKey())), state.getValue(edge.getValue()));
        }
        return turned;
    }
}
