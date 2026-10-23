package dev.aerolarge.item;

import dev.aerolarge.airport.RunwayBuild;
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
 * Lays down a runway for the airliner (see {@link RunwayBuild}). Used on the ground, the runway starts two blocks past
 * the block clicked, in its layer, centred on it, and runs away the way the player faces, replacing whatever is there.
 * One at a time: while a runway is being laid, another cannot be started.
 */
public class RunwayBlueprintItem extends Item {
    /** How far past the block clicked the runway starts, so the player is not standing on its threshold lights. */
    private static final int AHEAD = 2;

    public RunwayBlueprintItem(Properties properties) {
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
        if (RunwayBuild.isRunning(level)) {
            player.displayClientMessage(Component.translatable("message.aerolarge.runway.busy"), true);
            return InteractionResult.FAIL;
        }
        Direction along = player.getDirection();
        BlockPos start = context.getClickedPos().relative(along, AHEAD);
        RunwayBuild.start(level, start, along, player);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.aerolarge.runway_blueprint.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.aerolarge.runway_blueprint.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
