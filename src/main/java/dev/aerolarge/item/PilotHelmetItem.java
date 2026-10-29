package dev.aerolarge.item;

import dev.aerolarge.AerolargeRegistry;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/**
 * A jet pilot's flight helmet, to wear: use it to put it on, or put it in the helmet slot. On the flight deck its visor
 * shows the pilots' head-up display ({@link dev.aerolarge.client.PilotHud}), which shows to nobody without one. It is
 * not an armour piece drawn from an armour texture, which could only paint a visor flat onto a box round the head;
 * worn, it is drawn as its own model, the way a carved pumpkin is, so its visor and the cover it slides up into stand
 * out from the shell. It still protects as much as a leather cap, through the armour it adds while on the head.
 */
public class PilotHelmetItem extends Item implements Equipable {
    /** As much armour as a leather cap gives, under the id vanilla's helmets give theirs. */
    private static final double ARMOR = 1;
    private static final ResourceLocation ARMOR_ID = ResourceLocation.withDefaultNamespace("armor.helmet");

    public PilotHelmetItem(Properties properties) {
        super(properties.attributes(protection()));
    }

    /** The armour the helmet adds while on the head, as much as a leather cap's; the space helmet adds the same. */
    static ItemAttributeModifiers protection() {
        return ItemAttributeModifiers.builder()
            .add(Attributes.ARMOR, new AttributeModifier(ARMOR_ID, ARMOR, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
            .build();
    }

    /** Whether the entity has a pilot helmet on. */
    public static boolean worn(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(AerolargeRegistry.PILOT_HELMET);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    /** The knock of a hard shell going on, as a turtle shell's does. */
    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_TURTLE;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.aerolarge.pilot_helmet.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
