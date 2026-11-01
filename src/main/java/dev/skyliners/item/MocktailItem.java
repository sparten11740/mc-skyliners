package dev.skyliners.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.EffectCures;

/**
 * A mocktail from the robot bartender, with no alcohol in it: Nebula Fizz, Rocket Fuel, Zero-G Float, Supernova Sunrise
 * or Moon Milk; or the drink tap's sparkling Star Fizz. Drunk, not eaten, a mocktail's glass gone with it and Star
 * Fizz's bottle given back, and each gives a short lift of its own (see its food properties); Moon Milk instead clears
 * away every effect milk does, as a bucket of milk would.
 */
public class MocktailItem extends Item {
    /** Whether drinking it clears away the effects milk does. */
    private final boolean clears;

    public MocktailItem(Properties properties, boolean clears) {
        super(properties);
        this.clears = clears;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity drinker) {
        ItemStack left = super.finishUsingItem(stack, level, drinker);
        if (clears && !level.isClientSide) {
            drinker.removeEffectsCuredBy(EffectCures.MILK);
        }
        return left;
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
