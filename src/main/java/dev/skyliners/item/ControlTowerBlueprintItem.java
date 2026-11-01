package dev.skyliners.item;

import dev.skyliners.airport.ControlTower;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Lays down an airfield's control tower (see {@link ControlTower}). Used on the ground, it builds the tower ahead of the
 * block clicked, the canopy over its door two blocks past it, facing back at the player, its door and its radio toward
 * them. It
 * refuses when the space is not clear, unless the player sneaks, which builds straight through whatever is there.
 */
public class ControlTowerBlueprintItem extends Item {
    public ControlTowerBlueprintItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        Direction along = player.getDirection();
        BlockPos middle = context.getClickedPos().relative(along, ControlTower.middleAhead());
        ControlTower.Placement placement = ControlTower.layout().place(level, middle, along);
        if (placement.top() >= level.getMaxBuildHeight()) {
            player.displayClientMessage(Component.translatable("message.skyliners.too_high"), false);
            return InteractionResult.FAIL;
        }
        if (placement.blocked() > 0 && !player.isShiftKeyDown()) {
            BlockPos at = placement.firstBlocked();
            player.displayClientMessage(Component.translatable(
                "message.skyliners.blocked", placement.blocked(), at.getX(), at.getY(), at.getZ()
            ).withStyle(ChatFormatting.RED), false);
            return InteractionResult.FAIL;
        }
        ControlTower.build(level, placement);
        level.playSound(null, middle, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6F, 1.2F);
        player.displayClientMessage(Component.translatable("message.skyliners.control_tower.built"), false);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.skyliners.control_tower_blueprint.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.skyliners.control_tower_blueprint.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
