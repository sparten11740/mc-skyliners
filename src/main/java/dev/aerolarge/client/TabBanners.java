package dev.aerolarge.client;

import dev.aerolarge.AerolargeTabs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Each Aerolarge creative tab laid out in its sections (see {@link AerolargeTabs}), each under a banner with its first
 * item's picture and its name: once a tab is open, its items are spread out so that every section starts on a fresh
 * row after a row left empty, and the banner is drawn over that row as it scrolls by.
 */
public final class TabBanners {
    /** The creative screen's item grid: its columns and rows, a slot's size, and where its first slot's frame starts. */
    private static final int COLUMNS = 9;
    private static final int ROWS = 5;
    private static final int SLOT = 18;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 17;
    /** A banner's colours: the livery's dark blue, a lighter line along its edges, white lettering. */
    private static final int BANNER = 0xFF1C2A44;
    private static final int BANNER_EDGE = 0xFF3A5A8C;
    private static final int TITLE = 0xFFFFFFFF;

    private TabBanners() {
    }

    /** Spreads the open tab's items out into their sections, once each time the tab is filled. */
    public static void layOut(ScreenEvent.Render.Pre event) {
        AerolargeTabs.Tab tab = AerolargeTabs.of(CreativeModeInventoryScreen.selectedTab);
        if (!(event.getScreen() instanceof CreativeModeInventoryScreen screen) || tab == null) {
            return;
        }
        NonNullList<ItemStack> items = screen.getMenu().items;
        // The tab fills its list with items only; laid out, it has empty slots.
        if (items.isEmpty() || items.contains(ItemStack.EMPTY)) {
            return;
        }
        Map<Item, Integer> sections = new HashMap<>();
        for (int i = 0; i < tab.sections().size(); i++) {
            for (Supplier<? extends Item> item : tab.sections().get(i).items()) {
                sections.put(item.get(), i);
            }
        }
        List<ItemStack> laidOut = new ArrayList<>();
        int section = -1;
        for (ItemStack stack : items) {
            int next = sections.getOrDefault(stack.getItem(), section);
            if (next != section) {
                pad(laidOut);
                for (int i = 0; i < COLUMNS; i++) {
                    laidOut.add(ItemStack.EMPTY);
                }
                section = next;
            }
            laidOut.add(stack);
        }
        items.clear();
        items.addAll(laidOut);
        screen.getMenu().scrollTo(screen.scrollOffs);
    }

    /** Fills the last row with empty slots, so what comes next starts a row of its own. */
    private static void pad(List<ItemStack> items) {
        while (items.size() % COLUMNS != 0) {
            items.add(ItemStack.EMPTY);
        }
    }

    /** Draws the banners over the empty rows in view, each naming the section that follows it. */
    public static void draw(ContainerScreenEvent.Render.Foreground event) {
        AerolargeTabs.Tab tab = AerolargeTabs.of(CreativeModeInventoryScreen.selectedTab);
        if (!(event.getContainerScreen() instanceof CreativeModeInventoryScreen screen) || tab == null) {
            return;
        }
        List<ItemStack> items = screen.getMenu().items;
        int top = screen.getMenu().getRowIndexForScroll(screen.scrollOffs);
        for (int row = top; row < top + ROWS; row++) {
            ItemStack first = bannerAt(items, row);
            if (first != null) {
                banner(event.getGuiGraphics(), GRID_Y + (row - top) * SLOT, tab, first);
            }
        }
    }

    /** The first item of the section whose banner row this is; null if it is not one. */
    private static ItemStack bannerAt(List<ItemStack> items, int row) {
        int start = row * COLUMNS;
        if (start + COLUMNS >= items.size()) {
            return null;
        }
        for (int i = start; i < start + COLUMNS; i++) {
            if (!items.get(i).isEmpty()) {
                return null;
            }
        }
        ItemStack first = items.get(start + COLUMNS);
        return first.isEmpty() ? null : first;
    }

    private static void banner(GuiGraphics graphics, int y, AerolargeTabs.Tab tab, ItemStack first) {
        int width = COLUMNS * SLOT;
        graphics.pose().pushPose();
        // Over the empty slots' frames and the highlight of a slot under the mouse.
        graphics.pose().translate(0, 0, 200);
        graphics.fill(GRID_X, y, GRID_X + width, y + SLOT, BANNER);
        graphics.fill(GRID_X, y, GRID_X + width, y + 1, BANNER_EDGE);
        graphics.fill(GRID_X, y + SLOT - 1, GRID_X + width, y + SLOT, BANNER_EDGE);
        graphics.renderItem(first, GRID_X + 1, y + 1);
        Component name = tab.sections().stream()
            .filter(section -> section.items().stream().anyMatch(item -> item.get() == first.getItem()))
            .findFirst().map(AerolargeTabs.Section::bannerName).orElse(Component.empty());
        graphics.drawString(Minecraft.getInstance().font, name, GRID_X + SLOT + 4, y + 5, TITLE);
        graphics.pose().popPose();
    }
}
