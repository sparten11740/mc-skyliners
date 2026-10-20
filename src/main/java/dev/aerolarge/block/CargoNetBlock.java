package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A cargo net, as an airliner's hold is netted off with: webbing straps in a diamond lattice, sewn together where they
 * cross, on steel fittings: a track along its top where it hangs from the ceiling, double-stud fittings along its foot
 * locked into the floor, and an edge strap down a free end. Nets put up beside one another join into one net, as panes
 * do (NORTH, EAST, SOUTH, WEST), and a net's end against a wall hooks onto it; nets put up over one another join too
 * (UP, DOWN), so a whole net shows its track only along its top and its fittings only along its foot.
 * <p>
 * It is a mesh to reach through: pointing at a net, you point through it at whatever is behind it, unless at its steel
 * fittings, or holding a net, or sneaking, when you take hold of the webbing itself. It keeps everyone and everything
 * on its own side all the same.
 */
public class CargoNetBlock extends CrossCollisionBlock {
    private static final MapCodec<CargoNetBlock> CODEC = simpleCodec(CargoNetBlock::new);
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    /** The webbing's half-width and height, in pixels, as a pane's: a node in the middle and an arm to each side joined. */
    private static final float WEBBING = 1.0F;
    private static final float HEIGHT = 16.0F;
    /** The steel fittings along an arm, each way: the track along its top and the fittings along its foot. */
    private static final Map<Direction, VoxelShape> TRACK = TurnedShapes.of(Block.box(7.25, 15, 0, 8.75, 16, 8));
    private static final Map<Direction, VoxelShape> FOOT = TurnedShapes.of(Block.box(6.5, 0, 0, 9.5, 1.5, 8));
    /** The edge strap down a free end, or at a corner, in the middle of the block, its fittings at its top and foot. */
    private static final VoxelShape EDGE_STRAP = Block.box(6.75, 0, 6.75, 9.25, 16, 9.25);
    /** Each state's fittings, worked out once, as the shapes of the webbing are. */
    private final Map<BlockState, VoxelShape> fittings;

    public CargoNetBlock(Properties properties) {
        super(WEBBING, WEBBING, HEIGHT, HEIGHT, HEIGHT, properties);
        registerDefaultState(stateDefinition.any()
            .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false)
            .setValue(UP, false).setValue(DOWN, false).setValue(WATERLOGGED, false));
        Map<BlockState, VoxelShape> shapes = new HashMap<>();
        for (BlockState state : stateDefinition.getPossibleStates()) {
            shapes.put(state, fittingsOf(state));
        }
        fittings = Map.copyOf(shapes);
    }

    @Override
    protected MapCodec<? extends CrossCollisionBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, WATERLOGGED);
    }

    /** Whether the state is a cargo net. */
    public static boolean isNet(BlockState state) {
        return state.getBlock() instanceof CargoNetBlock;
    }

    /**
     * Whether a net at {@code pos} joins the block beside it on that side: another net always; a wall, a whole face,
     * only at the end of a net, where the net runs on from the other side, so a net never hooks onto what stands
     * beside its webbing.
     */
    public static boolean joins(BlockGetter level, BlockPos pos, Direction side) {
        BlockPos next = pos.relative(side);
        BlockState beside = level.getBlockState(next);
        if (isNet(beside)) {
            return true;
        }
        return !isExceptionForConnection(beside) && beside.isFaceSturdy(level, next, side.getOpposite())
            && isNet(level.getBlockState(pos.relative(side.getOpposite())));
    }

    /** The net's state at {@code pos}, joined to whatever is round it. */
    public BlockState joined(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(side), joins(level, pos, side));
        }
        return state.setValue(UP, isNet(level.getBlockState(pos.above()))).setValue(DOWN, isNet(level.getBlockState(pos.below())));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        return joined(defaultBlockState(), context.getLevel(), pos)
            .setValue(WATERLOGGED, context.getLevel().getFluidState(pos).getType() == Fluids.WATER);
    }

    /**
     * Rejoined to the block that changed; on the level, also to the wall across from it, which the net hooks onto
     * only while it runs on from this side.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        if (side.getAxis().isVertical()) {
            return state.setValue(side == Direction.UP ? UP : DOWN, isNet(neighbour));
        }
        return state.setValue(PROPERTY_BY_DIRECTION.get(side), joins(level, pos, side))
            .setValue(PROPERTY_BY_DIRECTION.get(side.getOpposite()), joins(level, pos, side.getOpposite()));
    }

    /**
     * What a player points at: only the steel fittings, so the rest of the mesh is reached through; holding a net, to
     * put up another against it, or sneaking, the whole of it.
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context.isHoldingItem(asItem()) || context.isDescending()) {
            return super.getShape(state, level, pos, context);
        }
        return fittings.get(state);
    }

    /** The steel fittings of a net in this state: its track and its foot along each arm, and the edge strap if it has one. */
    private static VoxelShape fittingsOf(BlockState state) {
        VoxelShape shape = edgeStrap(state) ? EDGE_STRAP : Shapes.empty();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (state.getValue(PROPERTY_BY_DIRECTION.get(side))) {
                if (!state.getValue(UP)) {
                    shape = Shapes.or(shape, TRACK.get(side));
                }
                if (!state.getValue(DOWN)) {
                    shape = Shapes.or(shape, FOOT.get(side));
                }
            }
        }
        return shape;
    }

    /**
     * Whether the net has an edge strap down the middle of the block: wherever it does not run on through the block,
     * north to south or east to west, so at a free end, a corner, or a net on its own.
     */
    private static boolean edgeStrap(BlockState state) {
        return !(state.getValue(NORTH) && state.getValue(SOUTH)) && !(state.getValue(EAST) && state.getValue(WEST));
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** Between two nets joined to each other, neither draws the faces they share. */
    @Override
    protected boolean skipRendering(BlockState state, BlockState neighbour, Direction side) {
        if (neighbour.is(this) && (side.getAxis().isVertical()
            || state.getValue(PROPERTY_BY_DIRECTION.get(side)) && neighbour.getValue(PROPERTY_BY_DIRECTION.get(side.getOpposite())))) {
            return true;
        }
        return super.skipRendering(state, neighbour, side);
    }
}
