package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The drop bay's screen, laid out as a dispenser's: the bay's three by three slots in the middle, the player's
 * inventory under them. A shift-click moves a stack between the two. Its own menu type only so the client can draw it
 * with the bay's own picture (see {@code DropBayScreen}).
 */
public class DropBayMenu extends AbstractContainerMenu {
    /** The menu's slots: the bay's, then the player's inventory and hotbar, from the first to past the last. */
    private static final int BAY_END = DropBayBlockEntity.SLOTS;
    private static final int INVENTORY_END = BAY_END + 36;
    /** Where the slots are drawn, as the dispenser's are: the bay's grid, then the inventory and the hotbar under it. */
    private static final int GRID_X = 62;
    private static final int GRID_Y = 17;
    private static final int INVENTORY_X = 8;
    private static final int INVENTORY_Y = 84;
    private static final int HOTBAR_Y = 142;
    private static final int SLOT_SIZE = 18;
    private static final int COLUMNS = 3;

    private final Container bay;

    /** The client's: it shows the slots the server sends. */
    public DropBayMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(DropBayBlockEntity.SLOTS));
    }

    public DropBayMenu(int containerId, Inventory inventory, Container bay) {
        super(SkylinersRegistry.DROP_BAY_MENU.get(), containerId);
        checkContainerSize(bay, DropBayBlockEntity.SLOTS);
        this.bay = bay;
        bay.startOpen(inventory.player);
        for (int i = 0; i < DropBayBlockEntity.SLOTS; i++) {
            addSlot(new Slot(bay, i, GRID_X + i % COLUMNS * SLOT_SIZE, GRID_Y + i / COLUMNS * SLOT_SIZE));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * SLOT_SIZE, INVENTORY_Y + row * SLOT_SIZE));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, INVENTORY_X + column * SLOT_SIZE, HOTBAR_Y));
        }
    }

    /** Open while the bay is still there and in reach, as the player sees it: in an assembled airliner too. */
    @Override
    public boolean stillValid(Player player) {
        return bay.stillValid(player);
    }

    /** A shift-click moves the stack from the bay into the inventory, or from the inventory into the bay. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        boolean moved = index < BAY_END
            ? moveItemStackTo(stack, BAY_END, INVENTORY_END, true)
            : moveItemStackTo(stack, 0, BAY_END, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == before.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return before;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        bay.stopOpen(player);
    }
}
