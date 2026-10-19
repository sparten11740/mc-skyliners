package dev.aerolarge.item;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.airframe.AirframeBuild;
import dev.aerolarge.airframe.AirframeLayout;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Lays down a whole airliner. Used on the ground, it builds the airframe with its nose just past the block clicked
 * and its tail stretching away in the direction the player looks, wheels on the ground. It refuses when the space
 * is not clear, unless the player sneaks, which builds straight through whatever is there.
 */
public class AirlinerBlueprintItem extends Item {
    private final boolean furnished;

    public AirlinerBlueprintItem(boolean furnished, Properties properties) {
        super(properties);
        this.furnished = furnished;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        if (AirframeBuild.isRunning(level)) {
            player.displayClientMessage(Component.translatable("message.aerolarge.busy"), true);
            return InteractionResult.FAIL;
        }

        Direction along = player.getDirection();
        BlockPos nose = context.getClickedPos().above().relative(along, 2);
        AirframeLayout layout = AirframeLayout.create(AerolargeConfig.SCALE.get(), furnished);
        AirframeBuild.Placement placement = AirframeBuild.Placement.of(level, layout, nose, along);

        if (placement.top() >= level.getMaxBuildHeight()) {
            player.displayClientMessage(Component.translatable("message.aerolarge.too_high"), false);
            return InteractionResult.FAIL;
        }
        if (placement.blocked() > 0 && !player.isShiftKeyDown()) {
            BlockPos at = placement.firstBlocked();
            player.displayClientMessage(Component.translatable(
                "message.aerolarge.blocked", placement.blocked(), at.getX(), at.getY(), at.getZ()
            ).withStyle(ChatFormatting.RED), false);
            return InteractionResult.FAIL;
        }
        AirframeBuild.start(level, placement, player);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(furnished ? "item.aerolarge.furnished_airliner_blueprint.tooltip" : "item.aerolarge.airliner_blueprint.tooltip")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.aerolarge.airliner_blueprint.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
