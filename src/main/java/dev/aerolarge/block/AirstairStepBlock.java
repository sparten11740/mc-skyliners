package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What the airstairs stand on once they are down: blocks the airstair controller lays under the flight its renderer
 * draws (see {@link AirstairBlockEntity}), so the stairs are walked like any others. A STEP is shaped like a stair,
 * its two half steps rising toward FACING, the way up to the door; a GUARD stands on a step at either side of the
 * flight and holds the handrail's thin wall along its EDGE (left or right, seen climbing), so nobody walks off the
 * side. Both are drawn by nothing and cannot be aimed at, mined or blown up: only the controller places them and takes
 * them away. A step no controller holds any more (left standing in the world when an airliner was assembled and flew
 * off with its stairs out) takes itself away, and the guard on it goes with it.
 */
public class AirstairStepBlock extends Block {
    private static final MapCodec<AirstairStepBlock> CODEC = simpleCodec(AirstairStepBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final EnumProperty<Edge> EDGE = EnumProperty.create("edge", Edge.class);
    /** How thick the handrail's wall is, in sixteenths. */
    private static final int RAIL = 2;
    private static final Map<Direction, VoxelShape> STEP = TurnedShapes.of(Shapes.or(box(0, 0, 0, 16, 8, 16), box(0, 8, 0, 16, 16, 8)));
    private static final Map<Direction, VoxelShape> LEFT_RAIL = TurnedShapes.of(box(0, 0, 0, RAIL, 16, 16));
    private static final Map<Direction, VoxelShape> RIGHT_RAIL = TurnedShapes.of(box(16 - RAIL, 0, 0, 16, 16, 16));
    /** How far a lone step looks up the flight for its top, in blocks: further than any airliner's stairs reach. */
    private static final int LONGEST_FLIGHT = 64;
    /** How far from the doorway at the top of the flight it looks for the controller, in blocks. */
    private static final int CONTROLLER_REACH = 3;

    /** A step of the flight, or the guard over one at its side. */
    public enum Part implements StringRepresentable {
        STEP, GUARD;

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }

    /** Which side of the flight a guard's wall is on, seen climbing; a step has none. */
    public enum Edge implements StringRepresentable {
        NONE, LEFT, RIGHT;

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }

        Edge mirrored() {
            return this == LEFT ? RIGHT : this == RIGHT ? LEFT : NONE;
        }
    }

    public AirstairStepBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.STEP).setValue(EDGE, Edge.NONE));
    }

    /** A step of a flight climbing toward {@code up}. */
    public static BlockState step(BlockState base, Direction up) {
        return base.setValue(FACING, up).setValue(PART, Part.STEP).setValue(EDGE, Edge.NONE);
    }

    /** The guard over a step of a flight climbing toward {@code up}, its wall on the given side. */
    public static BlockState guard(BlockState base, Direction up, Edge edge) {
        return base.setValue(FACING, up).setValue(PART, Part.GUARD).setValue(EDGE, edge);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, EDGE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Nothing to aim at: clicks go through to whatever is behind, as they would through the drawn stairs' gaps. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        if (state.getValue(PART) == Part.STEP) {
            return STEP.get(facing);
        }
        return switch (state.getValue(EDGE)) {
            case LEFT -> LEFT_RAIL.get(facing);
            case RIGHT -> RIGHT_RAIL.get(facing);
            case NONE -> Shapes.empty();
        };
    }

    /**
     * A step is solid ground to anything finding its way, as a stair is, so villagers walk up the flight to the door;
     * a guard is open air but for its wall at the edge.
     */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return state.getValue(PART) == Part.GUARD && type != PathComputationType.WATER;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    /** A guard goes with the step it stands on. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(PART) == Part.GUARD && direction == Direction.DOWN && !isStep(neighbor)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(PART) == Part.STEP;
    }

    /**
     * Now and then a step makes sure it is still some controller's: it follows the flight up to its top and looks
     * round the doorway there for an airstair controller that has its stairs down; with none, it is a leftover, and
     * goes. Where that part of the world is not loaded it waits: the box round the doorway spans at most two chunks
     * each way, so its four corners say whether all of it is.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos top = pos;
        for (int i = 0; i < LONGEST_FLIGHT; i++) {
            BlockPos next = top.relative(state.getValue(FACING)).above();
            if (!isStep(level.getBlockState(next))) {
                break;
            }
            top = next;
        }
        BlockPos doorway = top.relative(state.getValue(FACING)).above();
        for (int corner = 0; corner < 4; corner++) {
            if (!level.isLoaded(doorway.offset((corner & 1) == 0 ? -CONTROLLER_REACH : CONTROLLER_REACH, 0,
                (corner & 2) == 0 ? -CONTROLLER_REACH : CONTROLLER_REACH))) {
                return;
            }
        }
        BlockPos from = doorway.offset(-CONTROLLER_REACH, -CONTROLLER_REACH, -CONTROLLER_REACH);
        BlockPos to = doorway.offset(CONTROLLER_REACH, CONTROLLER_REACH, CONTROLLER_REACH);
        for (BlockPos at : BlockPos.betweenClosed(from, to)) {
            if (level.getBlockEntity(at) instanceof AirstairBlockEntity airstairs && airstairs.holds(level, pos)) {
                return;
            }
        }
        level.removeBlock(pos, false);
    }

    public static boolean isStep(BlockState state) {
        return state.getBlock() instanceof AirstairStepBlock && state.getValue(PART) == Part.STEP;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** Mirrored, the flight still climbs the same way along it, but its left and right swap. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING))).setValue(EDGE, state.getValue(EDGE).mirrored());
    }
}
