package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * An airliner's cabin carpet: a low woven pile a pixel thick, as vanilla's carpet is, in the small repeating diamonds
 * airline carpets are woven in, a lighter diamond round a dark knot on a fine twill. It is drawn in greys and tinted
 * on the client to COLOR, any dye's, the cabin's blue until dyed; use a dye on it to dye it, and taken up it keeps its
 * colour. See {@link AisleCarpetBlock} for the aisle's runner with the floor path lights along it.
 */
public class CabinCarpetBlock extends CarpetBlock {
    private static final MapCodec<CabinCarpetBlock> CODEC = simpleCodec(CabinCarpetBlock::new);
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);
    /** The colour a carpet is woven in until dyed: the cabin's blue, as the staircases' carpet is. */
    public static final DyeColor WOVEN = DyeColor.BLUE;

    public CabinCarpetBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(COLOR, WOVEN));
    }

    @Override
    public MapCodec<? extends CarpetBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR);
    }

    /** A carpet of this block in the colour, as the blueprints lay one. */
    public static BlockState carpet(Block carpet, DyeColor colour) {
        return carpet.defaultBlockState().setValue(COLOR, colour);
    }

    /** The colour the client tints the pile with: its dye's. The aisle runner's lights are not tinted. */
    public static int tint(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
        return state.getValue(COLOR).getTextureDiffuseColor();
    }

    /** The colour the client tints a carpet in hand or in an inventory with: the one it was taken up in. */
    public static int itemTint(ItemStack stack, int tintIndex) {
        DyeColor color = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(COLOR);
        return (color != null ? color : WOVEN).getTextureDiffuseColor();
    }

    /** A dye dyes the carpet, using one up (unless in creative mode). */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof DyeItem dye) || state.getValue(COLOR) == dye.getDyeColor()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(COLOR, dye.getDyeColor()), Block.UPDATE_CLIENTS);
            stack.consume(1, player);
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * A carpet in its colour, the way one broken comes back (the loot table copies the colour the same way): the colour
     * rides along as the block state to lay it in, and the woven blue is left off, so new carpets stack with it.
     */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        ItemStack stack = new ItemStack(this);
        if (state.getValue(COLOR) != WOVEN) {
            stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(COLOR, state.getValue(COLOR)));
        }
        return stack;
    }
}
