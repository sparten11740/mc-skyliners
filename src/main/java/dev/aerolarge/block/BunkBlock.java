package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A bunk for the kids' den: a padded mattress on a white frame along a wall, two blocks long, a blanket over it in
 * COLOR (tinted on the client, and dyed by using a dye on it, as a seat is), a pillow at its head under a little reading
 * light on the headboard, and a padded rail along its open side at the head end, so nobody rolls out, the foot end left
 * open to climb in. Use it with an empty hand to switch the reading light on or off; it lights the pillow, and only
 * while it is on (LIT). It is no bed to sleep in.
 *
 * <p>Put down, it faces whoever puts it there, its back to the wall behind it, its head where it was put and its foot
 * to their right; it needs the room there. FACING is the way its open side looks, and PART which half it is: the foot
 * is to the head's left as the bunk looks out. Either half gone, the other goes too, and only the head drops the bunk.
 */
public class BunkBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<BunkBlock> CODEC = simpleCodec(BunkBlock::new);
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** The blanket's colour; the frame, the mattress, the pillow and the light keep their own. */
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);
    /** The blanket's colour until dyed: the den's old wool mattresses' light blue. */
    private static final DyeColor BLANKET = DyeColor.LIGHT_BLUE;
    /** The light switch's tick: a small button's click, quiet and high. */
    private static final float SWITCH_VOLUME = 0.4F;
    private static final float SWITCH_PITCH = 1.6F;
    /**
     * The head half facing north, its back to the south and its head end to the east: the frame and the mattress under
     * the blanket, the headboard at the end and the rail along the open side.
     */
    private static final VoxelShape HEAD = Shapes.or(
        Block.box(0, 0, 1, 15, 8, 16),
        Block.box(15, 0, 1, 16, 14, 16),
        Block.box(1, 3, 0.5, 15, 12, 2)
    );
    /** The foot half facing north, its foot end to the west: the frame and the mattress, and the footboard. */
    private static final VoxelShape FOOT = Shapes.or(
        Block.box(1, 0, 1, 16, 8, 16),
        Block.box(0, 0, 1, 1, 10, 16)
    );
    private static final Map<Direction, VoxelShape> HEAD_SHAPES = TurnedShapes.of(HEAD);
    private static final Map<Direction, VoxelShape> FOOT_SHAPES = TurnedShapes.of(FOOT);

    public BunkBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BedPart.HEAD).setValue(LIT, false)
            .setValue(COLOR, BLANKET));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, LIT, COLOR);
    }

    /** Which way the foot lies from the head of a bunk whose open side looks this way: to its left. */
    public static Direction footward(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** Which way the other half of this one lies. */
    private static Direction towardOtherHalf(BlockState state) {
        Direction foot = footward(state.getValue(FACING));
        return state.getValue(PART) == BedPart.HEAD ? foot : foot.getOpposite();
    }

    /** The colour the client tints the blanket with: its dye's. */
    public static int tint(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
        return state.getValue(COLOR).getTextureDiffuseColor();
    }

    /** The colour the client tints a bunk in hand or in an inventory with: the one it was taken up in, or its own. */
    public static int itemTint(ItemStack stack, int tintIndex) {
        DyeColor color = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(COLOR);
        return (color != null ? color : BLANKET).getTextureDiffuseColor();
    }

    /** It faces whoever puts it down, the head where it is put and the foot to their right, where there is room for it. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos foot = context.getClickedPos().relative(footward(facing));
        Level level = context.getLevel();
        if (!level.getBlockState(foot).canBeReplaced(context) || !level.getWorldBorder().isWithinBounds(foot)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    /** The foot goes down beside the head put down, in its colour. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.relative(footward(state.getValue(FACING))), state.setValue(PART, BedPart.FOOT), Block.UPDATE_ALL);
    }

    /** A half whose other half is gone goes too, so the bunk is only ever whole. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (direction == towardOtherHalf(state) && !(neighbour.is(this) && neighbour.getValue(PART) != state.getValue(PART))) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    /** Broken by its foot in creative mode, the head it takes with it drops nothing either. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(PART) == BedPart.FOOT) {
            BlockPos head = pos.relative(towardOtherHalf(state));
            if (level.getBlockState(head).is(this)) {
                level.setBlock(head, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(PART) == BedPart.HEAD ? HEAD_SHAPES : FOOT_SHAPES).get(state.getValue(FACING));
    }

    /**
     * A dye recolours the blanket, both halves, using one up (unless in creative mode); whatever else is in hand is used
     * as it would be anywhere. Only an empty hand works the light.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(stack.getItem() instanceof DyeItem dye) || state.getValue(COLOR) == dye.getDyeColor()) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            setBoth(level, pos, state, state.setValue(COLOR, dye.getDyeColor()));
            stack.consume(1, player);
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The reading light switches on, or off, with a tick. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            setBoth(level, pos, state, state.cycle(LIT));
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, SWITCH_VOLUME, SWITCH_PITCH);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Both halves as the one used now is, each its own half. */
    private void setBoth(Level level, BlockPos pos, BlockState state, BlockState changed) {
        level.setBlock(pos, changed, Block.UPDATE_ALL);
        BlockPos other = pos.relative(towardOtherHalf(state));
        BlockState half = level.getBlockState(other);
        if (half.is(this)) {
            level.setBlock(other, changed.setValue(PART, half.getValue(PART)), Block.UPDATE_ALL);
        }
    }

    /** Mirrored, the foot lies to the other side of the head, so the halves swap to keep it to the head's left. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState turned = super.mirror(state, mirror);
        return mirror == Mirror.NONE ? turned : turned.setValue(PART, state.getValue(PART) == BedPart.HEAD ? BedPart.FOOT : BedPart.HEAD);
    }

    /**
     * A bunk with its blanket in its colour, the way one broken comes back (the loot table copies the colour the same
     * way): the colour rides along as the block state to put it down in, and its own is left off, so new bunks stack
     * with it.
     */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (state.getValue(COLOR) != BLANKET) {
            stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(COLOR, state.getValue(COLOR)));
        }
        return stack;
    }
}
