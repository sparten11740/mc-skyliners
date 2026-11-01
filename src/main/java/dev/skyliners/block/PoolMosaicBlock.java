package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A swimming pool's glass mosaic: small square tiles in the pool's blues, each a shade of its own, set in pale grout.
 * With LINE a lane line is laid into it, a dark blue stripe down its middle, as a pool's end walls are marked where
 * each lane's middle meets them; use blue dye on a plain tile to lay one.
 */
public class PoolMosaicBlock extends Block {
    private static final MapCodec<PoolMosaicBlock> CODEC = simpleCodec(PoolMosaicBlock::new);
    public static final BooleanProperty LINE = BooleanProperty.create("line");

    public PoolMosaicBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LINE, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LINE);
    }

    /** Blue dye lays a lane line into a plain tile, using the dye up (unless in creative mode). */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof DyeItem dye) || dye.getDyeColor() != DyeColor.BLUE || state.getValue(LINE)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(LINE, true), Block.UPDATE_CLIENTS);
            stack.consume(1, player);
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
