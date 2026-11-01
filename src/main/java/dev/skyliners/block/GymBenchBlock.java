package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A gym bench (Turnbank): a long low bench of lacquered ash, its board at a seat's height over the narrow balance beam
 * along its underside, on a steel frame of legs at either end. It is one block tall, so it stands under a window
 * without covering it. Benches side by side, facing the same way, make one long bench (LEFT and RIGHT, as whoever sits
 * on it sees them, say which ways it runs on), with legs only at its ends. Using it sits you down on it facing FACING:
 * it is one of Create's seats underneath, as the cabin seats are, so the seatbelts and villager passengers treat it
 * as a seat. Nothing sits down on it by landing on it, and a dye does not recolour it.
 */
public class GymBenchBlock extends SeatBlock implements Joining {
    private static final MapCodec<GymBenchBlock> CODEC = simpleCodec(GymBenchBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    /** Facing north: the board and the balance beam under it, the whole block along. */
    private static final VoxelShape BOARD = Shapes.or(Block.box(0, 6, 4, 16, 8, 12), Block.box(0, 4, 6.75, 16, 6, 9.25));
    /** Facing north: the legs at its left end (west), and at its right end (east). */
    private static final VoxelShape LEFT_LEGS = Block.box(1, 0, 4, 2.5, 6, 12);
    private static final VoxelShape RIGHT_LEGS = Block.box(13.5, 0, 4, 15, 6, 12);
    /** The bench's shape for each way it faces, with legs at neither end, its left, its right or both: by legs(). */
    private static final List<Map<Direction, VoxelShape>> SHAPES = IntStream.range(0, 4).mapToObj(GymBenchBlock::standingOn).toList();

    public GymBenchBlock(Properties properties) {
        super(properties, DyeColor.BROWN);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(LEFT, false).setValue(RIGHT, false));
    }

    /** The bench standing on the legs given as by legs(), turned each way. */
    private static Map<Direction, VoxelShape> standingOn(int legs) {
        VoxelShape shape = BOARD;
        if ((legs & 1) != 0) {
            shape = Shapes.or(shape, LEFT_LEGS);
        }
        if ((legs & 2) != 0) {
            shape = Shapes.or(shape, RIGHT_LEGS);
        }
        return TurnedShapes.of(shape);
    }

    /** Which legs the bench stands on, as an index into SHAPES: its left end's and its right end's, where it ends. */
    private static int legs(BlockState state) {
        return (state.getValue(LEFT) ? 0 : 1) | (state.getValue(RIGHT) ? 0 : 2);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FACING, LEFT, RIGHT));
    }

    /** Facing the way whoever puts it down faces, so they sit down on it facing that way. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        BlockState state = withWater(defaultBlockState().setValue(FACING, context.getHorizontalDirection()), context);
        return joined(state, way -> context.getLevel().getBlockState(pos.relative(way)));
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return joined(super.updateShape(state, direction, neighbour, level, pos, neighbourPos), way -> level.getBlockState(pos.relative(way)));
    }

    /** Running on into a bench beside it, either side, facing the same way. */
    @Override
    public BlockState joined(BlockState state, Function<Direction, BlockState> beside) {
        Direction facing = state.getValue(FACING);
        return state.setValue(LEFT, sameBench(state, beside.apply(facing.getCounterClockWise())))
            .setValue(RIGHT, sameBench(state, beside.apply(facing.getClockWise())));
    }

    private boolean sameBench(BlockState bench, BlockState other) {
        return other.is(this) && other.getValue(FACING) == bench.getValue(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(legs(state)).get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    /** Using it sits whoever uses it down, with nothing in hand as far as Create is concerned, so no dye recolours it. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return super.useItemOn(ItemStack.EMPTY, state, level, pos, player, hand, hit);
    }

    /** Nothing that lands on it sits down, and nothing bounces off it as off Create's cushions. */
    @Override
    public void updateEntityAfterFallOn(BlockGetter level, Entity entity) {
        entity.setDeltaMovement(entity.getDeltaMovement().multiply(1.0, 0.0, 1.0));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** Mirrored, it faces the mirrored way, and its left end is its right. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
        return mirror == Mirror.NONE ? mirrored : mirrored.setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT));
    }
}
