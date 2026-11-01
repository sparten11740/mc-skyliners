package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A backlit sign set into a cabin bulkhead over an archway, as an airliner's are: a block of the bulkhead's cream
 * panelling with a lightbox in its face, a printed picture behind a slim brushed steel bezel, glowing whatever the light
 * around (it gives no light of its own), so it reads with the cabin lights set for the night. Its PICTURE says what
 * lies through the archway without a word, for passengers who cannot read yet: a seat, a drop of water over the
 * waves, a basketball, a steaming cup, or a man and a woman for the lavatories, each in its room's colour. FACING is
 * the way its face looks; put down, it faces whoever puts it there. Use it with an empty hand to show the next picture,
 * sneaking the one before; taken down, or picked, it keeps its picture.
 */
public class BulkheadSignBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<BulkheadSignBlock> CODEC = simpleCodec(BulkheadSignBlock::new);
    public static final EnumProperty<Picture> PICTURE = EnumProperty.create("picture", Picture.class);
    /** The picture a sign is printed with when nothing says which: the first. */
    private static final Picture DEFAULT = Picture.SEAT;
    /** The click of a new picture going into the lightbox: an item frame's turn, a little higher. */
    private static final float CHANGE_PITCH = 1.3F;

    /** What a sign shows, in the order using it goes through them, which its item's looks follow. */
    public enum Picture implements StringRepresentable {
        SEAT, DROP, BALL, CUP, LAVATORY;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The picture so many steps on, round to the first after the last. */
        Picture step(int steps) {
            Picture[] all = values();
            return all[Math.floorMod(ordinal() + steps, all.length)];
        }
    }

    public BulkheadSignBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PICTURE, DEFAULT));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PICTURE);
    }

    /** Put down, it faces whoever puts it there, in the picture it was taken down or picked with. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Whatever is in hand is used as it would be anywhere, a block put against the sign; only an empty hand turns it. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /** The next picture goes in, or sneaking the one before. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            Picture picture = state.getValue(PICTURE).step(player.isSecondaryUseActive() ? -1 : 1);
            level.setBlock(pos, state.setValue(PICTURE, picture), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0F, CHANGE_PITCH);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Itself, with its picture: the picture rides along as the block state to put it down with (the loot table keeps it
     * the same way), left off for the first, so those stack with new ones.
     */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (state.getValue(PICTURE) != DEFAULT) {
            stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(PICTURE, state.getValue(PICTURE)));
        }
        return stack;
    }
}
