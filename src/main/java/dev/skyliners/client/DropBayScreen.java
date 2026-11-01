package dev.skyliners.client;

import dev.skyliners.Skyliners;
import dev.skyliners.block.DropBayMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * The drop bay's screen: a dispenser's, its three by three slots in the middle under the title, with a picture either
 * side of them of what the bay does, for players who cannot read yet: the bay doors open under the belly and a care
 * package dropping out of them, then the package floating down under its parachute to the grass.
 */
public class DropBayScreen extends AbstractContainerScreen<DropBayMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/gui/container/drop_bay.png");

    public DropBayScreen(DropBayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
    }
}
