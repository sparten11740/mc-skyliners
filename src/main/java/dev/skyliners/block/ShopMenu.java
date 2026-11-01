package dev.skyliners.block;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A shop's trading screen, as the server holds it (see {@link Shop}): vanilla's, but closing once the shop's block is
 * gone or out of reach, and buying with a shift-click without taking the shop for a villager. The client opens
 * vanilla's own.
 */
final class ShopMenu extends MerchantMenu {
    /** The menu's slots: what is bought, then the shopper's inventory, from the first slot to past the last. */
    private static final int BOUGHT = 2;
    private static final int INVENTORY_START = 3;
    private static final int INVENTORY_END = 39;

    private final Shop shop;

    ShopMenu(int containerId, Inventory inventory, Shop shop) {
        super(containerId, inventory, shop);
        this.shop = shop;
    }

    @Override
    public boolean stillValid(Player player) {
        return super.stillValid(player) && shop.openTo(player);
    }

    /**
     * Shift-clicking what is on offer buys it straight into the inventory, as vanilla's does, but for its trade sound:
     * vanilla plays that from the merchant, taking it for the villager it would be, and the shop is a block. The
     * shop's own sale sound plays instead.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index != BOUGHT) {
            return super.quickMoveStack(player, index);
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack bought = slot.getItem();
        ItemStack before = bought.copy();
        if (!moveItemStackTo(bought, INVENTORY_START, INVENTORY_END, true)) {
            return ItemStack.EMPTY;
        }
        slot.onQuickCraft(bought, before);
        if (bought.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (bought.getCount() == before.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, bought);
        return before;
    }
}
