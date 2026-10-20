package dev.aerolarge.client;

import dev.aerolarge.Aerolarge;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.BrewCoffee;
import dev.aerolarge.block.CoffeeDrink;
import dev.aerolarge.block.CoffeeMachineBlock;
import dev.aerolarge.block.CoffeeMachineBlockEntity;
import dev.aerolarge.client.PanelArt.CloseButton;
import dev.aerolarge.client.PanelArt.Icon;
import dev.aerolarge.client.PanelArt.Look;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The coffee machine's panel, opened by using a machine that is ready for an order: a panel in the flight deck's
 * colours (see {@link PanelArt}) with a big button for each drink, each showing the drink in its cup, large, with its
 * name under it, so that a player who cannot read yet can order too. Pressing one sends the order to the server (see
 * {@link BrewCoffee}), which checks it again, and closes the panel; the machine's own display then shows the brew. The
 * game goes on behind it, and it closes once the machine is busy or the player has stepped out of its reach.
 */
public class CoffeeMachineScreen extends Screen {
    /** The panel's size, the space inside its bezel, and the width of what is on its face. */
    private static final int WIDTH = 276;
    private static final int HEIGHT = 138;
    private static final int PADDING = 8;
    private static final int CONTENT = WIDTH - 2 * (PanelArt.BORDER + PADDING);
    /** From the panel's top: the row of drinks, how tall each button is and the space between them. */
    private static final int DRINKS_Y = PanelArt.BORDER + PanelArt.TITLE_HEIGHT + 1 + PADDING;
    private static final int DRINK_HEIGHT = HEIGHT - DRINKS_Y - PanelArt.BORDER - PADDING;
    private static final int DRINK_GAP = 6;
    /** The close button's distance in from the corner of the title bar, the state's from it and the lamp's from that. */
    private static final int CLOSE_INSET = 3;
    private static final int STATE_GAP = 6;
    private static final int LAMP_GAP = 7;
    /** The lamp, pulsing between this brightness and full, this slowly (ms), while the machine waits for an order. */
    private static final float LAMP_LOW = 0.7F;
    private static final float LAMP_PERIOD = 2000;

    private static final Icon TITLE_ICON = Icon.of("coffee_machine/steaming_cup");
    private static final Component READY = Component.translatable("screen.aerolarge.coffee_machine.ready");

    private final BlockPos pos;
    /** The panel's top left corner on the screen. */
    private int left;
    private int top;

    private CoffeeMachineScreen(BlockPos pos) {
        super(Component.translatable("screen.aerolarge.coffee_machine.title"));
        this.pos = pos;
    }

    /** Opens the panel of the machine standing there, over whatever else is open. */
    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new CoffeeMachineScreen(pos));
    }

    /** A drink's picture: its cup as the item shows it, drawn large. */
    private static Icon picture(CoffeeDrink drink) {
        return new Icon(ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/item/" + drink.getSerializedName() + ".png"));
    }

    /** A drink's colour on the panel: the espresso's crema amber, the cappuccino's cyan, the mocha's chocolate red. */
    private static int colour(CoffeeDrink drink) {
        return switch (drink) {
            case ESPRESSO -> PanelArt.AMBER;
            case CAPPUCCINO -> PanelArt.CYAN;
            case MOCHA -> PanelArt.RED;
        };
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        CoffeeDrink[] drinks = CoffeeDrink.values();
        int buttonWidth = (CONTENT - (drinks.length - 1) * DRINK_GAP) / drinks.length;
        int x = left + PanelArt.BORDER + PADDING;
        for (CoffeeDrink drink : drinks) {
            addRenderableWidget(new DrinkButton(x, top + DRINKS_Y, buttonWidth, DRINK_HEIGHT, drink));
            x += buttonWidth + DRINK_GAP;
        }
        int closeX = left + WIDTH - PanelArt.BORDER - CLOSE_INSET - CloseButton.SIZE;
        addRenderableWidget(new CloseButton(closeX, top + PanelArt.BORDER + CLOSE_INSET, this::onClose));
    }

    @Override
    public void tick() {
        Level level = minecraft != null ? minecraft.level : null;
        LocalPlayer player = minecraft != null ? minecraft.player : null;
        BlockState state = level != null ? level.getBlockState(pos) : null;
        boolean ready = state != null && state.is(AerolargeRegistry.COFFEE_MACHINE.get()) && state.getValue(CoffeeMachineBlock.BREW) == CoffeeMachineBlock.Brew.READY;
        if (!ready || player == null || !(level.getBlockEntity(pos) instanceof CoffeeMachineBlockEntity machine) || !machine.canBeUsedBy(player)) {
            onClose();
        }
    }

    /** Orders the drink and closes, leaving the machine's display to show the brew. */
    private void order(CoffeeDrink drink) {
        PacketDistributor.sendToServer(new BrewCoffee(pos, drink));
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        PanelArt.drawFrame(graphics, left, top, left + WIDTH, top + HEIGHT);
        PanelArt.drawTitleBar(graphics, font, TITLE_ICON, title, left, top, left + WIDTH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // Ready for an order, by a green lamp at the end of the title bar, as the machine's display shows it.
        int stateX = left + WIDTH - PanelArt.BORDER - CLOSE_INSET - CloseButton.SIZE - STATE_GAP - font.width(READY);
        int titleMiddle = top + PanelArt.BORDER + PanelArt.TITLE_HEIGHT / 2;
        graphics.drawString(font, READY, stateX, titleMiddle - PanelArt.GLYPH_HEIGHT / 2, PanelArt.GREEN, false);
        PanelArt.drawLamp(graphics, stateX - LAMP_GAP, titleMiddle, PanelArt.GREEN, Mth.lerp(PanelArt.pulse(LAMP_PERIOD), LAMP_LOW, 1F));
    }

    /** A drink's button: the drink in its cup, as large as fits, its name under it, edged in its colour. */
    private final class DrinkButton extends PanelArt.PanelButton {
        /** The space over the picture, and under the name. */
        private static final int MARGIN = 6;
        private final CoffeeDrink drink;
        private final Icon picture;

        DrinkButton(int x, int y, int width, int height, CoffeeDrink drink) {
            super(x, y, width, height, Component.translatable("item.aerolarge." + drink.getSerializedName()));
            this.drink = drink;
            this.picture = picture(drink);
        }

        @Override
        public void onPress() {
            order(drink);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Look look = look();
            PanelArt.drawTile(graphics, getX(), getY(), getRight(), getBottom(), colour(drink), look, false);
            Font font = Minecraft.getInstance().font;
            int scale = Math.max(1, Math.min(width - 2 * MARGIN, height - 3 * MARGIN - PanelArt.GLYPH_HEIGHT) / Icon.SIZE);
            int size = Icon.SIZE * scale;
            int sink = look == Look.PRESSED ? 1 : 0;
            picture.draw(graphics, getX() + (width - size) / 2, getY() + MARGIN + sink, scale, active);
            int nameY = getBottom() - MARGIN - PanelArt.GLYPH_HEIGHT + sink;
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2, nameY, active ? PanelArt.WHITE : PanelArt.OFF);
        }
    }
}
