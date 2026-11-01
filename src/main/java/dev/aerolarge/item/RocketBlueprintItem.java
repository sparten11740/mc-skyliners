package dev.aerolarge.item;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.airframe.Layout;
import dev.aerolarge.airframe.RocketLayout;
import dev.aerolarge.block.LaunchPadBlockEntity;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lays down the rocket as any airframe's blueprint does, but used on a launch pad (see {@link LaunchPadBlockEntity}),
 * anywhere on it and from any side, it sets her down in the pad's very middle, her axis over its hub, her fins' feet on
 * their plates and her door to the pad's front, so her airstairs come down on its walkway. Held and aimed at a pad, its
 * preview shows her there at her full size.
 */
public class RocketBlueprintItem extends AirframeBlueprintItem {
    public RocketBlueprintItem(Supplier<Layout> layout, Supplier<BlockState> model, float modelMiddle, String tooltip, Properties properties) {
        super(layout, model, modelMiddle, tooltip, properties);
    }

    @Nullable
    @Override
    public LaunchPadBlockEntity padUnder(Level level, BlockPos aimed) {
        return LaunchPadBlockEntity.under(level, aimed);
    }

    @Override
    protected Spot spot(Level level, BlockPos clicked, Direction looking) {
        LaunchPadBlockEntity pad = padUnder(level, clicked);
        if (pad == null) {
            return super.spot(level, clicked, looking);
        }
        Direction along = pad.along();
        int reach = RocketLayout.reachAt(AerolargeConfig.SCALE.get());
        return new Spot(pad.getBlockPos().above().relative(along.getOpposite(), reach), along);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.aerolarge.rocket_blueprint.on_pad").withStyle(ChatFormatting.DARK_GRAY));
    }
}
