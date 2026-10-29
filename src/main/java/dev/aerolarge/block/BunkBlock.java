package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
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
 *
 * <p>Two make a bunk bed, as the rocket's bunk room has them (TIER, see {@link Tier}): the lower bunk's corner posts
 * run up past it to carry the upper bunk {@link #UPPER_RISE} rows over it, room enough to stand up in the lower one,
 * and a ladder stands up the foot end to the upper one (see {@link RungLadderBlock.Kind#BUNK_LADDER}). Either bunk of a
 * bunk bed gone, the bed comes apart: the lower bunk gone, the upper and the ladder go too, the upper dropping its
 * bunk; the upper gone, the ladder goes and the lower stands on its own.
 */
public class BunkBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<BunkBlock> CODEC = simpleCodec(BunkBlock::new);
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** The blanket's colour; the frame, the mattress, the pillow and the light keep their own. */
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);
    public static final EnumProperty<Tier> TIER = EnumProperty.create("tier", Tier.class);
    /** How many rows over a bunk bed's lower bunk its upper bunk stands: room to stand up in the lower one. */
    public static final int UPPER_RISE = 3;
    /** How many rows tall the ladder up a bunk bed's foot end is: from the floor up to the upper bunk. */
    public static final int LADDER_ROWS = UPPER_RISE + 1;

    /**
     * Where a bunk stands: on its own; as a bunk bed's lower bunk, its corner posts running up past it to the upper; or
     * as its upper bunk, on those posts, guard rails along all its open side and its back and across its head end, so
     * it is climbed into, and out of, only up the ladder at its foot end.
     */
    public enum Tier implements StringRepresentable {
        SINGLE, LOWER, UPPER;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
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
    /** A bunk bed's corner posts at a head's end and at a foot's. */
    private static final VoxelShape HEAD_POSTS = Shapes.or(Block.box(14.5, 0, 0, 16, 16, 1.5), Block.box(14.5, 0, 14.5, 16, 16, 16));
    private static final VoxelShape FOOT_POSTS = Shapes.or(Block.box(0, 0, 0, 1.5, 16, 1.5), Block.box(0, 0, 14.5, 1.5, 16, 16));
    /**
     * An upper bunk's guards as they stop anyone, too high to step over, as a fence's: along its open side and its back
     * all its length, and across its head end; its foot end is left open, the way in from its ladder.
     */
    private static final VoxelShape HEAD_GUARDS = Shapes.or(Block.box(0, 0, 0, 16, 24, 1.75), Block.box(0, 0, 14.25, 16, 24, 16),
        Block.box(15, 0, 0, 16, 24, 16));
    private static final VoxelShape FOOT_GUARDS = Shapes.or(Block.box(0, 0, 0, 16, 24, 1.75), Block.box(0, 0, 14.25, 16, 24, 16));
    private static final Map<Direction, VoxelShape> HEAD_SHAPES = TurnedShapes.of(HEAD);
    private static final Map<Direction, VoxelShape> FOOT_SHAPES = TurnedShapes.of(FOOT);
    private static final Map<Direction, VoxelShape> POSTED_HEAD_SHAPES = TurnedShapes.of(Shapes.or(HEAD, HEAD_POSTS));
    private static final Map<Direction, VoxelShape> POSTED_FOOT_SHAPES = TurnedShapes.of(Shapes.or(FOOT, FOOT_POSTS));
    private static final Map<Direction, VoxelShape> GUARDED_HEAD_SHAPES = TurnedShapes.of(Shapes.or(HEAD, HEAD_POSTS, HEAD_GUARDS));
    private static final Map<Direction, VoxelShape> GUARDED_FOOT_SHAPES = TurnedShapes.of(Shapes.or(FOOT, FOOT_POSTS, FOOT_GUARDS));

    public BunkBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BedPart.HEAD).setValue(LIT, false)
            .setValue(COLOR, BLANKET).setValue(TIER, Tier.SINGLE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, LIT, COLOR, TIER);
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
        boolean head = state.getValue(PART) == BedPart.HEAD;
        Map<Direction, VoxelShape> shapes = state.getValue(TIER) == Tier.SINGLE ? (head ? HEAD_SHAPES : FOOT_SHAPES)
            : (head ? POSTED_HEAD_SHAPES : POSTED_FOOT_SHAPES);
        return shapes.get(state.getValue(FACING));
    }

    /** An upper bunk keeps anyone on it from falling off but at its ladder's end; the others are as they look. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(TIER) != Tier.UPPER) {
            return getShape(state, level, pos, context);
        }
        return (state.getValue(PART) == BedPart.HEAD ? GUARDED_HEAD_SHAPES : GUARDED_FOOT_SHAPES).get(state.getValue(FACING));
    }

    /** A bunk bed comes apart with either of its bunks (see the class's javadoc). */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !newState.is(this) && state.getValue(TIER) != Tier.SINGLE) {
            takeApart(level, pos, state);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * What goes with one half of a bunk bed's bunk: the upper half over a lower one, or a lower half under an upper one
     * left on its own; and the ladder, from the foot.
     */
    private void takeApart(Level level, BlockPos pos, BlockState state) {
        boolean lower = state.getValue(TIER) == Tier.LOWER;
        BlockPos floor = lower ? pos : pos.below(UPPER_RISE);
        BlockPos other = lower ? pos.above(UPPER_RISE) : floor;
        BlockState there = level.getBlockState(other);
        if (lower && there.is(this) && there.getValue(TIER) == Tier.UPPER) {
            level.destroyBlock(other, true);
        } else if (!lower && there.is(this) && there.getValue(TIER) == Tier.LOWER) {
            level.setBlock(other, there.setValue(TIER, Tier.SINGLE), Block.UPDATE_ALL);
        }
        if (state.getValue(PART) == BedPart.FOOT) {
            takeDownLadder(level, floor.relative(footward(state.getValue(FACING))));
        }
    }

    /** The ladder up a bunk bed's foot end, standing at its foot. */
    private static void takeDownLadder(Level level, BlockPos foot) {
        for (int row = 0; row < LADDER_ROWS; row++) {
            BlockPos at = foot.above(row);
            if (level.getBlockState(at).is(AerolargeRegistry.BUNK_LADDER.get())) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
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
