package dev.aerolarge.client;

import dev.aerolarge.block.OpenTransponder;
import dev.aerolarge.block.SetTransponder;
import dev.aerolarge.block.TransponderBlockEntity;
import dev.aerolarge.client.PanelArt.CloseButton;
import dev.aerolarge.client.PanelArt.Look;
import dev.aerolarge.client.PanelArt.PanelButton;
import dev.aerolarge.radar.MarkerColour;
import dev.aerolarge.radar.MarkerIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A transponder's panel, opened by using it: a panel in the flight deck's colours (see {@link PanelArt}) with the
 * marker it shows on the maps, large, on glass, and beside it a button for each picture and one for each colour, the
 * one chosen lit; so that a player who cannot read yet can pick them too, every button shows what it picks and only
 * names it when pointed at. A pick is sent at once (see {@link SetTransponder}), which the server checks again. The
 * game goes on behind it, and it closes with its close button, Escape, or once the player has stepped out of reach.
 */
public class TransponderScreen extends Screen {
    /** The panel's size and the space inside its bezel. */
    private static final int WIDTH = 260;
    private static final int HEIGHT = 156;
    private static final int PADDING = 8;
    private static final int CONTENT_Y = PanelArt.BORDER + PanelArt.TITLE_HEIGHT + 1 + PADDING;
    /** The marker on its glass: the glass's size and the marker's scale on it. */
    private static final int PREVIEW = 64;
    private static final int PREVIEW_SCALE = 3;
    private static final int PREVIEW_GAP = 6;
    /** The pictures' buttons, five to a row, and the colours', six to a row, with the gaps between them and the rows. */
    private static final int ICONS_PER_ROW = 5;
    private static final int ICON_WIDTH = 30;
    private static final int ICON_HEIGHT = 24;
    private static final int COLOURS_PER_ROW = 6;
    private static final int COLOUR_WIDTH = 24;
    private static final int COLOUR_HEIGHT = 18;
    private static final int GAP = 3;
    private static final int GROUP_GAP = 8;
    /** How far in from a colour's edge its swatch starts. */
    private static final int SWATCH_INSET = 3;
    /** The close button's distance in from the corner of the title bar. */
    private static final int CLOSE_INSET = 3;
    /** How far a player can step from the transponder before the panel closes, as from a container. */
    private static final double REACH = 8;

    private final BlockPos pos;
    private MarkerIcon icon;
    private MarkerColour colour;
    private int left;
    private int top;

    private TransponderScreen(OpenTransponder opened) {
        super(Component.translatable("block.aerolarge.transponder"));
        pos = opened.pos();
        icon = opened.icon();
        colour = opened.colour();
    }

    /** Opens the panel as the server sent it. */
    public static void open(OpenTransponder opened) {
        Minecraft.getInstance().setScreen(new TransponderScreen(opened));
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        int x = left + PanelArt.BORDER + PADDING + PREVIEW + PREVIEW_GAP;
        int y = top + CONTENT_Y;
        MarkerIcon[] icons = MarkerIcon.values();
        for (int i = 0; i < icons.length; i++) {
            int column = i % ICONS_PER_ROW;
            int row = i / ICONS_PER_ROW;
            addRenderableWidget(new IconChoice(x + column * (ICON_WIDTH + GAP), y + row * (ICON_HEIGHT + GAP), icons[i]));
        }
        int coloursY = y + rows(icons.length, ICONS_PER_ROW) * (ICON_HEIGHT + GAP) - GAP + GROUP_GAP;
        MarkerColour[] colours = MarkerColour.values();
        for (int i = 0; i < colours.length; i++) {
            int column = i % COLOURS_PER_ROW;
            int row = i / COLOURS_PER_ROW;
            addRenderableWidget(new ColourChoice(x + column * (COLOUR_WIDTH + GAP), coloursY + row * (COLOUR_HEIGHT + GAP), colours[i]));
        }
        int closeX = left + WIDTH - PanelArt.BORDER - CLOSE_INSET - CloseButton.SIZE;
        addRenderableWidget(new CloseButton(closeX, top + PanelArt.BORDER + CLOSE_INSET, this::onClose));
    }

    private static int rows(int count, int perRow) {
        return (count + perRow - 1) / perRow;
    }

    /** Sends the picture and colour as they stand now. */
    private void send() {
        PacketDistributor.sendToServer(new SetTransponder(pos, icon, colour));
    }

    /**
     * Closes once the transponder is gone or out of reach: measured in the world, where an airliner carries it aboard
     * among its blocks.
     */
    @Override
    public void tick() {
        LocalPlayer player = minecraft != null ? minecraft.player : null;
        Level level = player != null ? player.level() : null;
        if (level == null || !(level.getBlockEntity(pos) instanceof TransponderBlockEntity)
            || player.position().distanceToSqr(SableClient.toWorld(level, Vec3.atCenterOf(pos))) > REACH * REACH) {
            onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        PanelArt.drawFrame(graphics, left, top, left + WIDTH, top + HEIGHT);
        PanelArt.drawTitleBar(graphics, font, MarkerArt.icon(MarkerIcon.TOWER), title, left, top, left + WIDTH);
        int x = left + PanelArt.BORDER + PADDING;
        int y = top + CONTENT_Y;
        PanelArt.drawGlass(graphics, x, y, x + PREVIEW, y + PREVIEW);
        MarkerArt.draw(graphics, icon, colour, x + PREVIEW / 2, y + PREVIEW / 2, PREVIEW_SCALE);
        Component name = icon.title();
        int nameY = y + PREVIEW + GAP + 2;
        graphics.drawCenteredString(font, name, x + PREVIEW / 2, nameY, colour.argb());
    }

    /** A picture to show the transponder by, drawn in the colour chosen, lit while chosen. */
    private final class IconChoice extends PanelButton {
        private final MarkerIcon choice;

        IconChoice(int x, int y, MarkerIcon choice) {
            super(x, y, ICON_WIDTH, ICON_HEIGHT, choice.title());
            this.choice = choice;
            showLabel();
        }

        @Override
        public void onPress() {
            icon = choice;
            send();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Look look = look();
            PanelArt.drawTile(graphics, getX(), getY(), getRight(), getBottom(), colour.argb(), look, icon == choice);
            int sink = look == Look.PRESSED ? 1 : 0;
            int size = PanelArt.Icon.SIZE;
            MarkerArt.icon(choice).draw(graphics, getX() + (width - size) / 2, getY() + (height - size) / 2 + sink, 1, colour.argb());
        }
    }

    /** A colour to show the transponder in: a swatch of it, lit while chosen. */
    private final class ColourChoice extends PanelButton {
        private final MarkerColour choice;

        ColourChoice(int x, int y, MarkerColour choice) {
            super(x, y, COLOUR_WIDTH, COLOUR_HEIGHT, choice.title());
            this.choice = choice;
            showLabel();
        }

        @Override
        public void onPress() {
            colour = choice;
            send();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Look look = look();
            PanelArt.drawTile(graphics, getX(), getY(), getRight(), getBottom(), choice.argb(), look, colour == choice);
            int sink = look == Look.PRESSED ? 1 : 0;
            graphics.fill(getX() + SWATCH_INSET, getY() + SWATCH_INSET + sink, getRight() - SWATCH_INSET, getBottom() - SWATCH_INSET + sink, choice.argb());
        }
    }
}
