package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

/**
 * What the coffee machine brews: an espresso, or one of the milk drinks, a cappuccino or a mocha, for which it froths
 * the milk with steam after pouring the coffee; or for children a babycino, frothed milk and no coffee at all. Its name
 * is the drink's in the block state, the advancement's criterion and the screen's lang keys.
 */
public enum CoffeeDrink implements StringRepresentable {
    ESPRESSO(true, false),
    CAPPUCCINO(true, true),
    MOCHA(true, true),
    BABYCINO(false, true);

    private final boolean coffee;
    private final boolean milk;

    CoffeeDrink(boolean coffee, boolean milk) {
        this.coffee = coffee;
        this.milk = milk;
    }

    /** Whether the beans are ground and coffee poured for it: all but the babycino, and only those count for Barista. */
    public boolean coffee() {
        return coffee;
    }

    /** Whether the milk is frothed for it, once any coffee is in. */
    public boolean milk() {
        return milk;
    }

    /** The drink in its cup, to hand out. */
    public ItemStack cup() {
        return switch (this) {
            case ESPRESSO -> new ItemStack(AerolargeRegistry.ESPRESSO.get());
            case CAPPUCCINO -> new ItemStack(AerolargeRegistry.CAPPUCCINO.get());
            case MOCHA -> new ItemStack(AerolargeRegistry.MOCHA.get());
            case BABYCINO -> new ItemStack(AerolargeRegistry.BABYCINO.get());
        };
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
