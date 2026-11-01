package dev.skyliners.item;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.airport.LaunchPad;
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
 * Lays down a launch pad for the rocket (see {@link LaunchPad}). Used on the ground, the deck takes the place of the
 * layer clicked, its front edge two blocks past the block clicked and centred on it, stretching away the way the player
 * faces. It refuses when anything stands over the deck, unless the player sneaks, which builds straight through it.
 * Held, it shows where the deck would go (see {@code BlueprintPreview}).
 */
public class LaunchPadBlueprintItem extends Item {
    /** How far past the block clicked the deck's front edge is, so the player is not standing on it. */
    private static final int AHEAD = 2;

    public LaunchPadBlueprintItem(Properties properties) {
        super(properties);
    }

    /** The middle of the deck a pad built from a block, the way a player looks, would have. */
    public static BlockPos middle(BlockPos clicked, Direction along) {
        return clicked.relative(along, AHEAD + LaunchPad.halfAt(SkylinersConfig.SCALE.get()));
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
        BlockPos clicked = context.getClickedPos();
        int roomUnder = clicked.getY() - level.getMinBuildHeight();
        return AirframeBlueprintItem.build(level, player, () -> LaunchPad.create(roomUnder), clicked.relative(along, AHEAD), along);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.skyliners.launch_pad_blueprint.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.skyliners.launch_pad_blueprint.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
