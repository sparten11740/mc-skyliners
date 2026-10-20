package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The drop bay in the airliner's belly, under the hold: its doors, and what waits behind them to be dropped (see
 * {@link DropBayBlockEntity}). Use anything on it from the hold to load one of it; use it with an empty hand to count
 * what is aboard, sneaking to take the last load back out. The drop release on the flight deck opens the doors and
 * drops the loads.
 */
public class DropBayBlock extends BaseEntityBlock {
    private static final MapCodec<DropBayBlock> CODEC = simpleCodec(DropBayBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public DropBayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof DropBayBlockEntity bay) {
            if (bay.load(stack)) {
                stack.consume(1, player);
                level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 1.0F, 0.8F);
                player.displayClientMessage(Component.translatable("message.aerolarge.drop_bay.loaded", bay.count(), DropBayBlockEntity.CAPACITY), true);
            } else {
                player.displayClientMessage(Component.translatable("message.aerolarge.drop_bay.full"), true);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof DropBayBlockEntity bay) {
            if (player.isShiftKeyDown()) {
                ItemStack load = bay.unload();
                if (!load.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(load);
                }
            }
            player.displayClientMessage(Component.translatable("message.aerolarge.drop_bay.loaded", bay.count(), DropBayBlockEntity.CAPACITY), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Taken apart, it spills whatever was still aboard; not when it is only moved, as Sable moves it into an assembled
     * airliner's plot and back, its load going with it.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving && !state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof DropBayBlockEntity bay) {
            for (ItemStack load = bay.unload(); !load.isEmpty(); load = bay.unload()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, load);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DropBayBlockEntity(pos, state);
    }
}
