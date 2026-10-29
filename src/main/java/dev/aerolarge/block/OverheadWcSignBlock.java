package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The lit WC sign high up in the cabin, as an airliner has them on its bulkheads and over its aisles: a slim off-white
 * lightbox with a dark face, on which an arrow shows the way to the WC and a toilet beside it glows green while one of
 * the WCs it shows is free, or red, someone sitting on it, while every one of them is taken (see
 * {@link OverheadWcSignBlockEntity}). Pictures only, so it needs no reading. FACING is the way its face looks; HANGING,
 * whether it hangs on two short rods from the block over it rather than standing out from a wall. Use it with an empty
 * hand for the next arrow, sneaking for the one before; taken down, or picked, it keeps its arrow.
 */
public class OverheadWcSignBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<OverheadWcSignBlock> CODEC = simpleCodec(OverheadWcSignBlock::new);
    public static final BooleanProperty HANGING = BooleanProperty.create("hanging");
    public static final EnumProperty<Arrow> ARROW = EnumProperty.create("arrow", Arrow.class);
    public static final BooleanProperty OCCUPIED = WcSignBlock.OCCUPIED;
    /** The arrow a sign shows when nothing says which: straight on. */
    private static final Arrow DEFAULT = Arrow.AHEAD;
    /** The click of a new arrow going in: an item frame's turn, a little higher, as the bulkhead signs' pictures. */
    private static final float CHANGE_PITCH = 1.3F;
    /** Facing north: the lightbox against the south side of the block, or hanging in its middle from rods to its top. */
    private static final Map<Direction, VoxelShape> ON_WALL = TurnedShapes.of(Block.box(0, 2, 13, 16, 14, 16));
    private static final Map<Direction, VoxelShape> HUNG = TurnedShapes.of(Block.box(0, 2, 6.5, 16, 16, 9.5));

    /** Which way the arrow points, seen from in front of the sign; in the order using it goes through them. */
    public enum Arrow implements StringRepresentable {
        AHEAD, LEFT, RIGHT, DOWN, NONE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The arrow so many steps on, round to the first after the last. */
        Arrow step(int steps) {
            Arrow[] all = values();
            return all[Math.floorMod(ordinal() + steps, all.length)];
        }

        /** The arrow as a mirror shows it: left and right swapped. */
        Arrow mirrored() {
            return this == LEFT ? RIGHT : this == RIGHT ? LEFT : this;
        }
    }

    public OverheadWcSignBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HANGING, false)
            .setValue(ARROW, DEFAULT).setValue(OCCUPIED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HANGING, ARROW, OCCUPIED);
    }

    /**
     * Against the side of a block it stands out from it, facing away; against the underside of one it hangs from it,
     * facing whoever puts it up; not on top of one, where it would stand out from nothing. Its arrow is the one it was
     * taken down or picked with.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        if (clicked == Direction.UP) {
            return null;
        }
        return defaultBlockState()
            .setValue(FACING, clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection().getOpposite())
            .setValue(HANGING, clicked == Direction.DOWN);
    }

    /** Put up by hand, it shows the WCs whose signs are round it. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof OverheadWcSignBlockEntity sign) {
            sign.link();
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(HANGING) ? HUNG : ON_WALL).get(state.getValue(FACING));
    }

    /** Whatever is in hand is used as it would be anywhere, a block put against the sign; only an empty hand turns it. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * The next arrow goes in, or sneaking the one before. A sign that shows no WC yet (put up before any WC sign round
     * it) looks for them again.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            Arrow arrow = state.getValue(ARROW).step(player.isSecondaryUseActive() ? -1 : 1);
            level.setBlock(pos, state.setValue(ARROW, arrow), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0F, CHANGE_PITCH);
            if (level.getBlockEntity(pos) instanceof OverheadWcSignBlockEntity sign && !sign.linked()) {
                sign.link();
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Mirrored, it faces the mirrored way and its arrow points the other way across. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return super.mirror(state, mirror).setValue(ARROW, mirror == Mirror.NONE ? state.getValue(ARROW) : state.getValue(ARROW).mirrored());
    }

    /**
     * Itself, with its arrow: the arrow rides along as the block state to put it down with (the loot table keeps it the
     * same way), left off for the first, so those stack with new ones.
     */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        ItemStack stack = new ItemStack(this);
        if (state.getValue(ARROW) != DEFAULT) {
            stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(ARROW, state.getValue(ARROW)));
        }
        return stack;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OverheadWcSignBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof OverheadWcSignBlockEntity sign) {
                sign.tick();
            }
        };
    }
}
