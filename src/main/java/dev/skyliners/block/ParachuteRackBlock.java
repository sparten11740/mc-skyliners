package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A rack of packed parachutes by the doors, never empty: using it puts one straight on, in the chest slot, or hands
 * one over if something else is worn there. FACING is the way its front looks.
 */
public class ParachuteRackBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<ParachuteRackBlock> CODEC = simpleCodec(ParachuteRackBlock::new);

    public ParachuteRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Put down by hand, its front looks at whoever puts it down. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            ItemStack parachute = new ItemStack(SkylinersRegistry.PARACHUTE.get());
            String message;
            if (chest.is(SkylinersRegistry.PARACHUTE.get())) {
                message = "message.skyliners.parachute_rack.wearing";
            } else if (chest.isEmpty()) {
                player.setItemSlot(EquipmentSlot.CHEST, parachute);
                message = "message.skyliners.parachute_rack.put_on";
            } else {
                player.getInventory().placeItemBackInInventory(parachute);
                message = "message.skyliners.parachute_rack.handed";
            }
            level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable(message), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
