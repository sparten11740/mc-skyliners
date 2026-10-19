package dev.aerolarge.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A boarding pass from the check-in desk; handing it over is up to {@link dev.aerolarge.cabin.Passengers}. */
public class BoardingPassItem extends Item {
    public BoardingPassItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.aerolarge.boarding_pass.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
