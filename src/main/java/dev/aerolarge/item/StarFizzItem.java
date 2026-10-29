package dev.aerolarge.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

/**
 * Star Fizz, the club's sparkling soda, a glowing violet in a glass bottle, poured at the bar's drink tap (see
 * {@link dev.aerolarge.block.DrinkTapBlock}). Drunk, not eaten, for a little nutrition (see its food properties), and
 * the empty bottle is given back.
 */
public class StarFizzItem extends Item {
    public StarFizzItem(Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public SoundEvent getDrinkingSound() {
        return SoundEvents.GENERIC_DRINK;
    }

    @Override
    public SoundEvent getEatingSound() {
        return SoundEvents.GENERIC_DRINK;
    }
}
