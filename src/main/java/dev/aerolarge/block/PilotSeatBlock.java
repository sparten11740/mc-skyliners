package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import java.util.List;
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
import net.neoforged.neoforge.common.util.FakePlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A pilot's seat on the flight deck: a high padded back with a headrest, an armrest either side and a harness over
 * the back, on a column between two floor rails. It is two blocks tall: the lower half is the seat, the upper half
 * the top of the back and the headrest, and either half sits whoever uses it down in the seat. Its seat stands higher
 * than a Create seat's (see {@link #RAISE}), so a pilot looks out over the curved row of screens. Underneath it is one
 * of Create's seats, sat in by Create's own seat entity, so the seatbelts, the pilots' head-up display and the outside
 * cameras treat whoever sits in it as seated, aboard an assembled airliner too. It never takes a sitter on its own,
 * though: nothing sits down in it by landing on it, and villager passengers leave it to the pilots (see
 * {@link dev.aerolarge.cabin.Passengers}); nor does dye turn it into a Create seat. FACING is the way the sitter faces.
 */
public class PilotSeatBlock extends SeatBlock {
    private static final MapCodec<PilotSeatBlock> CODEC = simpleCodec(PilotSeatBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    /**
     * How much higher than on one of Create's seats a pilot sits, in blocks (six pixels, as the seat's model is raised
     * in tools/generate_resources.mjs): high enough to look out over the curved row of screens.
     */
    public static final double RAISE = 6.0 / 16;
    /** The lower half facing north, the sitter's back to the south: the seat on its column, the back, the armrests. */
    private static final VoxelShape LOWER = Shapes.or(
        Block.box(2, 0, 1, 14, 14, 12),
        Block.box(2, 0, 11, 14, 16, 15),
        Block.box(0.5, 11, 3.5, 2, 16, 12),
        Block.box(14, 11, 3.5, 15.5, 16, 12)
    );
    /** The upper half facing north: the armrests' tops, the top of the back, up to the shoulders, and the headrest. */
    private static final VoxelShape UPPER = Shapes.or(
        Block.box(0.5, 0, 3.5, 2, 2, 12),
        Block.box(14, 0, 3.5, 15.5, 2, 12),
        Block.box(2, 0, 11, 14, 10.5, 15),
        Block.box(4, 10.5, 12, 12, 16, 15)
    );
    /**
     * What is solid of each half: the same but for the padding on the front of the back and of the headrest, as
     * whoever gets up from the seat is set down over its middle, half a block wide, and would be wedged in it.
     */
    private static final VoxelShape LOWER_SOLID = Shapes.or(
        Block.box(2, 0, 1, 14, 14, 12),
        Block.box(2, 0, 13, 14, 16, 15),
        Block.box(0.5, 11, 3.5, 2, 16, 12),
        Block.box(14, 11, 3.5, 15.5, 16, 12)
    );
    private static final VoxelShape UPPER_SOLID = Block.box(2, 0, 13, 14, 16, 15);
    private static final Map<Direction, VoxelShape> LOWER_SHAPES = TurnedShapes.of(LOWER);
    private static final Map<Direction, VoxelShape> UPPER_SHAPES = TurnedShapes.of(UPPER);
    private static final Map<Direction, VoxelShape> LOWER_SOLID_SHAPES = TurnedShapes.of(LOWER_SOLID);
    private static final Map<Direction, VoxelShape> UPPER_SOLID_SHAPES = TurnedShapes.of(UPPER_SOLID);

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
        return (state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SOLID_SHAPES : UPPER_SOLID_SHAPES).get(state.getValue(FACING));
    }

    /**
     * Sits whoever uses either half down in the seat, as Create's seats do: not someone sneaking, and not where a
     * player already sits; anyone else sitting there gets up for them. Whatever is in hand, so no dye turns it into
     * one of Create's.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos seat = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        if (!level.getBlockState(seat).is(this) || player.isShiftKeyDown() || player instanceof FakePlayer) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        List<SeatEntity> taken = level.getEntitiesOfClass(SeatEntity.class, new AABB(seat));
        if (taken.isEmpty()) {
            sitRaised(level, seat, player);
            return ItemInteractionResult.SUCCESS;
        }
        return takeOver(level, taken.getFirst(), player);
    }

    /** Someone else's seat for a player: theirs unless a player sits in it, whoever else does getting up. */
    private static ItemInteractionResult takeOver(Level level, SeatEntity seat, Player player) {
        List<Entity> sitters = seat.getPassengers();
        if (!sitters.isEmpty() && sitters.getFirst() instanceof Player) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            seat.ejectPassengers();
            player.startRiding(seat);
        }
        return ItemInteractionResult.SUCCESS;
    }

    /**
     * Sits someone down in the pilot's seat at a position, in a new seat entity of Create's as Create's seats do, but
     * {@link #RAISE} higher in the block. Create's seat entity is never moved for the clients once it is there, so it
     * starts out where it stays.
     */
    private static void sitRaised(Level level, BlockPos at, Entity sitter) {
        if (level.isClientSide) {
            return;
        }
        SeatEntity seat = new SeatEntity(level);
        seat.setPos(at.getX() + 0.5, at.getY() + RAISE, at.getZ() + 0.5);
        level.addFreshEntity(seat);
        sitter.startRiding(seat, true);
    }

    /**
     * The seat someone sits in: the block of the seat they ride, if it is one of these or one of Create's seats; null
     * if they sit in none.
     */
    @Nullable
    public static BlockPos seatOf(Entity sitter) {
        if (!(sitter.getVehicle() instanceof SeatEntity seat)) {
            return null;
        }
        BlockPos at = seat.blockPosition();
        return sitter.level().getBlockState(at).getBlock() instanceof SeatBlock ? at : null;
    }

    /**
     * Sits someone down in the seat at a position, if one stands there and nobody sits in it, in a pilot's seat as high
     * as ever.
     */
    public static void sitIn(Level level, BlockPos at, Entity sitter) {
        BlockState seat = level.getBlockState(at);
        if (!(seat.getBlock() instanceof SeatBlock) || SeatBlock.isSeatOccupied(level, at)) {
            return;
        }
        if (seat.getBlock() instanceof PilotSeatBlock) {
            sitRaised(level, at, sitter);
        } else {
            SeatBlock.sitDown(level, at, sitter);
        }
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
