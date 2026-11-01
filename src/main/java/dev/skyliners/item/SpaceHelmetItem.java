package dev.skyliners.item;

import dev.skyliners.SkylinersRegistry;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * An astronaut's space helmet, for the rocket's crew, to wear: use it to put it on, or put it in the helmet slot. A
 * round white shell with a big gold visor, mirrored against the sun, a dark ring at its neck and a mission patch on its
 * left side. Like the pilot helmet it is drawn on the head as its own model, the way a carved pumpkin is, and protects
 * as much as a leather cap. Seen from inside, its visor tints the view a touch of gold ({@link
 * dev.skyliners.client.HelmetVisor}), with no head-up display on it; aboard a rocket at the edge of space or over it,
 * its air is heard hissing in and out as its wearer breathes ({@link dev.skyliners.client.HelmetBreathing}). Her crew
 * lockers are stocked with one each when she is built.
 */
public class SpaceHelmetItem extends Item implements Equipable {
    public SpaceHelmetItem(Properties properties) {
        super(properties.attributes(PilotHelmetItem.protection()));
    }

    /** Whether the entity has a space helmet on. */
    public static boolean worn(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(SkylinersRegistry.SPACE_HELMET);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    /** The knock of a hard shell going on, as the pilot helmet's does. */
    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_TURTLE;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.skyliners.space_helmet.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
