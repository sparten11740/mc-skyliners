package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A passenger seat for the cabin, in two cuts. The economy seat is slim: a cushion on a pair of legs, an armrest
 * either side at the block's edges, so a row of them shares its armrests, and a high back with a headrest under a
 * white cover, the back's shell carrying a tray table folded up over a seat pocket for the seat behind. The business
 * seat is roomier: a cushion on a pedestal in a hard white shell that wraps its sides up to wide armrest consoles, a
 * leg rest folded down at its front and the shell rising behind the back up over a winged headrest. Both are
 * upholstered in COLOR, tinted on the client, and dyed by using a dye on them, as a curtain is.
 *
 * <p>Like the pilot seat each is two blocks tall, the lower half the seat, the upper half the top of the back and the
 * headrest, and either half sits whoever uses it down in the seat; and like the pilot seat each is one of Create's
 * seats underneath, sat in by Create's own seat entity, so the seatbelts, the outside cameras and villager passengers
 * (see {@link dev.aerolarge.cabin.Passengers}) treat them as the seats they are. Nothing sits down in one by landing on
 * it, and dye only recolours it. The back's shell is flush with the block's back, so a passenger screen put against
 * the back of the upper half sits on the seat's back, at the eye height of whoever sits behind. FACING is the way the
 * sitter faces.
 */
public class CabinSeatBlock extends SeatBlock {
    private static final MapCodec<CabinSeatBlock> ECONOMY_CODEC = simpleCodec(properties -> new CabinSeatBlock(properties, Cut.ECONOMY));
    private static final MapCodec<CabinSeatBlock> BUSINESS_CODEC = simpleCodec(properties -> new CabinSeatBlock(properties, Cut.BUSINESS));
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    /** The upholstery's colour; the shell, the frame and the headrest's cover keep their own. */
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);

    /** The seat's cut, and the colour it is upholstered in until dyed. */
    public enum Cut {
        ECONOMY(DyeColor.BLUE),
        BUSINESS(DyeColor.GRAY);

        private final DyeColor colour;

        Cut(DyeColor colour) {
            this.colour = colour;
        }
    }

    /**
     * The lower half facing north, the sitter's back to the south, as either cut has it: the cushion on its frame, the
     * back up from it with its shell flush with the block's back, and along either side an armrest, or the shell up to
     * the armrest console.
     */
    private static final VoxelShape LOWER = Shapes.or(
        Block.box(1.5, 0, 1, 14.5, 8, 12),
        Block.box(1, 0, 10.5, 15, 16, 16),
        Block.box(0, 1, 1.5, 2, 11.5, 16),
        Block.box(14, 1, 1.5, 16, 11.5, 16)
    );
    /** The upper half facing north: the top of the back and the headrest over it, with its wings. */
    private static final VoxelShape UPPER = Shapes.or(
        Block.box(1, 0, 10.5, 15, 10, 16),
        Block.box(2, 10, 10.5, 14, 15.5, 16)
    );
    /**
     * What is solid of each half: the same but for the padding on the front of the back and of the headrest, as
     * whoever gets up from the seat is set down over its middle, half a block wide, and would be wedged in it.
     */
    private static final VoxelShape LOWER_SOLID = Shapes.or(
        Block.box(1.5, 0, 1, 14.5, 8, 12),
        Block.box(1, 0, 13.5, 15, 16, 16),
        Block.box(0, 4, 2, 2, 11.5, 12),
        Block.box(14, 4, 2, 16, 11.5, 12)
    );
    private static final VoxelShape UPPER_SOLID = Block.box(1, 0, 13.5, 15, 15.5, 16);
    private static final Map<Direction, VoxelShape> LOWER_SHAPES = TurnedShapes.of(LOWER);
    private static final Map<Direction, VoxelShape> UPPER_SHAPES = TurnedShapes.of(UPPER);
    private static final Map<Direction, VoxelShape> LOWER_SOLID_SHAPES = TurnedShapes.of(LOWER_SOLID);
    private static final Map<Direction, VoxelShape> UPPER_SOLID_SHAPES = TurnedShapes.of(UPPER_SOLID);

    private final Cut cut;

    public CabinSeatBlock(Properties properties, Cut cut) {
        super(properties, cut.colour);
        this.cut = cut;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER).setValue(COLOR, cut.colour));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return cut == Cut.ECONOMY ? ECONOMY_CODEC : BUSINESS_CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FACING, HALF, COLOR));
    }

    /** The lower half of a seat upholstered in the colour, facing the way given, as the blueprints put one in. */
    public static BlockState seat(Block seat, DyeColor colour, Direction facing) {
        return seat.defaultBlockState().setValue(FACING, facing).setValue(COLOR, colour);
    }

    /** Whether this is the top of a seat's back: no seat to sit in, though one of Create's underneath. */
    public static boolean isBack(BlockState state) {
        return state.getBlock() instanceof CabinSeatBlock && state.getValue(HALF) == DoubleBlockHalf.UPPER;
    }

    /** The colour the client tints the upholstery with: its dye's. */
    public static int tint(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
        return state.getValue(COLOR).getTextureDiffuseColor();
    }

    /** The colour the client tints a seat in hand or in an inventory with: the one it was taken up in, or its own. */
    public static int itemTint(ItemStack stack, int tintIndex) {
        DyeColor color = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(COLOR);
        DyeColor own = stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CabinSeatBlock seat
            ? seat.cut.colour : DyeColor.GRAY;
        return (color != null ? color : own).getTextureDiffuseColor();
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

    /** The upper half goes up over the lower one put down, in its colour. */
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

    /**
     * A seat in its colour, the way one broken comes back (the loot table copies the colour the same way): the colour
     * rides along as the block state to put it down in, and the cut's own is left off, so new seats stack with it.
     */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        ItemStack stack = new ItemStack(this);
        if (state.getValue(COLOR) != cut.colour) {
            stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(COLOR, state.getValue(COLOR)));
        }
        return stack;
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
     * A dye recolours the whole seat, both halves, using one up (unless in creative mode); anything else sits whoever
     * uses either half down in the seat, as Create's seats do, with nothing in hand as far as Create is concerned, so
     * that no dye turns it into one of Create's.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos seat = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        BlockState lower = level.getBlockState(seat);
        if (!lower.is(this)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof DyeItem dye && !player.isShiftKeyDown()) {
            DyeColor color = dye.getDyeColor();
            if (lower.getValue(COLOR) == color) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide) {
                for (BlockPos at : new BlockPos[]{seat, seat.above()}) {
                    BlockState half = level.getBlockState(at);
                    if (half.is(this)) {
                        level.setBlock(at, half.setValue(COLOR, color), Block.UPDATE_CLIENTS);
                    }
                }
                stack.consume(1, player);
                level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
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
