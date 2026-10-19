package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A pilot's seat on the flight deck: a high padded back with a headrest, an armrest either side and a harness over
 * the back, on a column between two floor rails. It is two blocks tall: the lower half is the seat, the upper half
 * the top of the back and the headrest, and either half sits whoever uses it down in the seat. Underneath it is one of
 * Create's seats, sat in by Create's own seat entity, so the seatbelts, the pilots' head-up display and the outside
 * cameras treat whoever sits in it as seated, aboard an assembled airliner too. It never takes a sitter on its own,
 * though: nothing sits down in it by landing on it, and villager passengers leave it to the pilots (see
 * {@link dev.aerolarge.cabin.Passengers}); nor does dye turn it into a Create seat. FACING is the way the sitter faces.
 */
public class PilotSeatBlock extends SeatBlock {
    private static final MapCodec<PilotSeatBlock> CODEC = simpleCodec(PilotSeatBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    /** The lower half facing north, the sitter's back to the south: the seat on its column, the back, the armrests. */
    private static final VoxelShape LOWER = Shapes.or(
        Block.box(2, 0, 1, 14, 8, 12),
        Block.box(2, 0, 11, 14, 16, 15),
        Block.box(0.5, 5, 3.5, 2, 12, 12),
        Block.box(14, 5, 3.5, 15.5, 12, 12)
    );
    /** The upper half facing north: the top of the back, up to the shoulders, and the headrest over it. */
    private static final VoxelShape UPPER = Shapes.or(
        Block.box(2, 0, 11, 14, 4.5, 15),
        Block.box(4, 4.5, 12, 12, 13, 15)
    );
    private static final Map<Direction, VoxelShape> LOWER_SHAPES = turned(LOWER);
    private static final Map<Direction, VoxelShape> UPPER_SHAPES = turned(UPPER);

    public PilotSeatBlock(Properties properties) {
        super(properties, DyeColor.BLACK);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FACING, HALF));
    }

    /** The shape drawn facing north, turned a quarter at a time to face each way, as the block's model is. */
    private static Map<Direction, VoxelShape> turned(VoxelShape north) {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            // Quarter turns clockwise, seen from above, from north.
            int turns = (facing.get2DDataValue() + 2) % 4;
            VoxelShape[] shape = {Shapes.empty()};
            north.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
                double[] from = {x0, z0};
                double[] to = {x1, z1};
                for (int k = 0; k < turns; k++) {
                    from = new double[]{1 - from[1], from[0]};
                    to = new double[]{1 - to[1], to[0]};
                }
                shape[0] = Shapes.or(shape[0], Shapes.box(
                    Math.min(from[0], to[0]), y0, Math.min(from[1], to[1]), Math.max(from[0], to[0]), y1, Math.max(from[1], to[1])));
            });
            shapes.put(facing, shape[0]);
        }
        return shapes;
    }

    /** Facing the way whoever puts it there faces, so they sit down in it facing that way; it needs the room above. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return withWater(defaultBlockState().setValue(FACING, context.getHorizontalDirection()), context);
    }

    /** The upper half goes up over the lower one put down. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        BlockPos above = pos.above();
        level.setBlock(above, ProperWaterloggedBlock.withWater(level, state.setValue(HALF, DoubleBlockHalf.UPPER), above), Block.UPDATE_ALL);
    }

    /** A half whose other half is gone goes too, so the seat is only ever whole. */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean towardOtherHalf = direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);
        if (towardOtherHalf && !(neighbour.is(this) && neighbour.getValue(HALF) != half)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    /** Broken by its upper half in creative mode, the lower half it takes with it drops nothing either. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER && level.getBlockState(pos.below()).is(this)) {
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPES : UPPER_SHAPES).get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    /**
     * Sits whoever uses either half down in the seat, as Create's seats do, with nothing in hand as far as Create is
     * concerned, so that no dye turns it into one of Create's.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos seat = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        BlockState lower = level.getBlockState(seat);
        if (!lower.is(this)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(ItemStack.EMPTY, lower, level, seat, player, hand, hit);
    }

    /** Nothing that lands in it sits down, and nothing bounces off it as off Create's cushions. */
    @Override
    public void updateEntityAfterFallOn(BlockGetter level, Entity entity) {
        entity.setDeltaMovement(entity.getDeltaMovement().multiply(1.0, 0.0, 1.0));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }
}
