package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

/**
 * What the coffee machine brews: an espresso, or one of the milk drinks, a cappuccino or a mocha, for which it froths
 * the milk with steam after pouring the coffee. Its name is the drink's in the block state, the advancement's
 * criterion and the screen's lang keys.
 */
public enum CoffeeDrink implements StringRepresentable {
    ESPRESSO(false),
    CAPPUCCINO(true),
    MOCHA(true);

    private final boolean milk;

    CoffeeDrink(boolean milk) {
        this.milk = milk;
    }

    /** Whether the milk is frothed for it once the coffee is in. */
    public boolean milk() {
        return milk;
    }

    /** The drink in its cup, to hand out. */
    public ItemStack cup() {
        return switch (this) {
            case ESPRESSO -> new ItemStack(AerolargeRegistry.ESPRESSO.get());
            case CAPPUCCINO -> new ItemStack(AerolargeRegistry.CAPPUCCINO.get());
            case MOCHA -> new ItemStack(AerolargeRegistry.MOCHA.get());
        };
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
