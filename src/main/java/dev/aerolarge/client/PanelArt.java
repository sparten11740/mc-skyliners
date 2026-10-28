package dev.aerolarge.client;

import dev.aerolarge.Aerolarge;
import dev.aerolarge.autopilot.AutopilotBlockEntity.CircleSize;
import dev.aerolarge.autopilot.AutopilotBlockEntity.Lateral;
import dev.aerolarge.autopilot.AutopilotBlockEntity.Phase;
import dev.aerolarge.autopilot.Craft;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Drawing for the mod's control panels, in the flight deck's glass-cockpit colours (see
 * {@link AutopilotScreenRenderer}): a dark face in a bezel of brushed metal with a screw in each corner, a title bar,
 * black glass for readouts, and the panels' own controls (picture buttons, number boxes and the close button), so
 * that no vanilla button shows. Every control has a picture and a colour of its own, and its label only
 * as a caption or a tooltip, so the panels work for players who cannot read yet.
 */
final class PanelArt {
    /** The bezel's width, and the height of the title bar inside it. */
    static final int BORDER = 6;
    static final int TITLE_HEIGHT = 22;
    /** The height of the font's letters without the space under them, which vanilla centres text by. */
    static final int GLYPH_HEIGHT = 8;

    static final int WHITE = AutopilotScreenRenderer.WHITE;
    static final int GREY = AutopilotScreenRenderer.GREY;
    static final int GREEN = AutopilotScreenRenderer.GREEN;
    static final int AMBER = AutopilotScreenRenderer.AMBER;
    static final int MAGENTA = AutopilotScreenRenderer.TARGET;
    static final int CYAN = AutopilotScreenRenderer.TRACK;
    static final int RED = 0xFFE8382C;
    /** Edges and text of what cannot be used now. */
    static final int OFF = 0xFF59616C;
    private static final int FACE = AutopilotScreenRenderer.CASING;
    private static final int FACE_TOP = 0xFF1D2026;
    private static final int GLASS = AutopilotScreenRenderer.GLASS;
    private static final int EDGE = AutopilotScreenRenderer.BUTTON_EDGE;
    private static final int TILE = AutopilotScreenRenderer.BUTTON;
    private static final int TILE_LIT = AutopilotScreenRenderer.BUTTON_LIT;
    private static final int TILE_OFF = 0xFF1A1E24;
    private static final int TITLE_BAR = 0xFF20252D;
    private static final int TITLE_RULE = 0xFF2A8CB0;
    private static final int BEZEL_LIGHT = 0xFF5D6673;
    private static final int BEZEL_SHADOW = 0xFF0A0C0F;
    private static final int SCREW = 0xFF8C939D;
    private static final int SCREW_SLOT = 0xFF2A2F37;
    private static final int SOCKET = 0xFF05080A;
    private static final int GLINT = 0xC0FFFFFF;
    /** A button's shine along its top and shade along its bottom, swapped while it is held down. */
    private static final int SHINE = 0x2CFFFFFF;
    private static final int SHADE = 0x60000000;
    /** Faint lines across the face, so it reads as a lit display rather than flat paint, and how far apart. */
    private static final int SCANLINE = 0x14000000;
    private static final int SCANLINE_PITCH = 3;

    /** How much of its colour a button's face takes on under the pointer, and while held down. */
    private static final float HOT_TINT = 0.2F;
    private static final float PRESSED_TINT = 0.32F;
    /** How bright the edge of a button at rest is, as a share of its colour. */
    private static final float EDGE_DIM = 0.55F;
    /** A picture's tint while what it stands for cannot be used: dark, but still to be made out. */
    private static final int PICTURE_OFF = 0xFF666666;
    /** A glowing button's glow: how many rings, how strong the innermost at its brightest, and its pulse in ms. */
    private static final int GLOW_RINGS = 3;
    private static final int GLOW_ALPHA = 200;
    private static final float GLOW_PERIOD = 1600;
    private static final int SCREW_RADIUS = 2;
    private static final int LAMP_RADIUS = 3;
    /** How bright a lamp must be to show a glint. */
    private static final float LAMP_GLINT = 0.6F;

    private static final ResourceLocation BEZEL = texture("panel/bezel");
    /** The autopilot's pictures that are the same whatever it flies: switched off, and flying to a pin. */
    private static final Icon OFF_PICTURE = Icon.of("autopilot/disengage");
    private static final Icon PIN = Icon.of("autopilot/destination");
    private static final int BEZEL_TILE = 16;

    private PanelArt() {
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/gui/" + name + ".png");
    }

    /** One of the panels' 16 by 16 pictures (from tools/generate_textures.mjs), drawn at a whole multiple of its size. */
    record Icon(ResourceLocation texture) {
        static final int SIZE = 16;

        static Icon of(String name) {
            return new Icon(PanelArt.texture(name));
        }

        /** Draws it, darkened while what it stands for cannot be used. */
        void draw(GuiGraphics graphics, int x, int y, int scale, boolean usable) {
            draw(graphics, x, y, scale, usable ? 0xFFFFFFFF : PICTURE_OFF);
        }

        /** Draws it with its colours multiplied by a tint's (opaque white leaves them as they are). */
        void draw(GuiGraphics graphics, int x, int y, int scale, int tint) {
            graphics.setColor((tint >> 16 & 0xFF) / 255F, (tint >> 8 & 0xFF) / 255F, (tint & 0xFF) / 255F, 1F);
            int size = SIZE * scale;
            graphics.blit(texture, x, y, size, size, 0, 0, SIZE, SIZE, SIZE, SIZE);
            graphics.setColor(1F, 1F, 1F, 1F);
        }
    }

    /**
     * The autopilot's pictures of what it flies, the airliner's or the zeppelin's, so that a zeppelin's screens show a
     * zeppelin where an airliner's show an airliner: itself as it is best known, for a title bar, the airliner from
     * above and the zeppelin from the side; itself from above, nose up, and from the side, nose right; what the
     * autopilot is doing, as its settings' buttons show it; the ways it flies, like road signs, the path from a small
     * one's nose, straight on or round a half circle to the left or right, tightly or gently; and its height over the
     * ground, on the pilots' HUD. The zeppelin rises from the ground and settles onto it anywhere, needing no runway,
     * and has no roll-out after landing; so does the rocket, standing upright, which is drawn from above as a ring with
     * its four fins.
     */
    record Pictures(Icon emblem, Icon fromAbove, Icon fromTheSide, Icon takeOff, Icon climb, Icon cruise, Icon descend, Icon land, Icon rollOut,
                    Icon landAtPin, Icon straightOn, Icon tightLeft, Icon gentleLeft, Icon gentleRight, Icon tightRight, Icon groundHeight) {
        static final Pictures AIRLINER = drawn("autopilot/", "airliner", "airliner", "roll_out");
        static final Pictures ZEPPELIN = drawn("autopilot/zeppelin_", "zeppelin", "zeppelin_side", "land");
        static final Pictures ROCKET = drawn("autopilot/rocket_", "rocket", "rocket_side", "land");

        /**
         * The pictures named with a prefix (from tools/generate_textures.mjs): but for itself and its emblem, which
         * are named apart, and the picture it rolls out with, which may be another of its own.
         */
        private static Pictures drawn(String prefix, String itself, String emblem, String rollOut) {
            return new Pictures(Icon.of("autopilot/" + emblem), Icon.of("autopilot/" + itself), Icon.of("autopilot/" + itself + "_side"),
                Icon.of(prefix + "take_off"), Icon.of(prefix + "climb"), Icon.of(prefix + "engage"), Icon.of(prefix + "descend"),
                Icon.of(prefix + "land"), Icon.of(prefix + rollOut), Icon.of(prefix + "land_at_pin"), Icon.of(prefix + "straight_on"),
                Icon.of(prefix + "turn_tight_left"), Icon.of(prefix + "turn_gentle_left"), Icon.of(prefix + "turn_gentle_right"),
                Icon.of(prefix + "turn_tight_right"), Icon.of(prefix + "ground_height"));
        }

        /** The pictures of what an autopilot flies. */
        static Pictures of(Craft craft) {
            return switch (craft) {
                case AIRLINER -> AIRLINER;
                case ZEPPELIN -> ZEPPELIN;
                case ROCKET -> ROCKET;
            };
        }

        /**
         * The picture of what the autopilot is doing: the settings' buttons' pictures where it has one, else its own.
         */
        Icon phase(Phase phase) {
            return switch (phase) {
                case OFF -> OFF_PICTURE;
                case TAKEOFF -> takeOff;
                case CLIMB -> climb;
                case CRUISE -> cruise;
                case DESCENT -> descend;
                case LAND -> land;
                case ROLLOUT -> rollOut;
            };
        }

        /** The picture of how the autopilot steers, and how tightly it circles, as on its controls. */
        Icon lateral(Lateral lateral, CircleSize circle) {
            boolean tight = circle == CircleSize.TIGHT;
            return switch (lateral) {
                case HEADING -> straightOn;
                case ORBIT_LEFT -> tight ? tightLeft : gentleLeft;
                case ORBIT_RIGHT -> tight ? tightRight : gentleRight;
                case DESTINATION -> PIN;
            };
        }
    }

    /** How a control looks: dark while it cannot be used, at rest, lit up under the pointer, or held down. */
    enum Look {
        OFF, IDLE, HOT, PRESSED
    }

    /**
     * The panel: the bezel, lit along its outer top and left and shaded along its bottom and right, the face set into
     * it the other way round, a screw in each corner of the bezel, and the face, darkening downward and scanlined.
     */
    static void drawFrame(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        tile(graphics, x0, y0, x1, y0 + BORDER);
        tile(graphics, x0, y1 - BORDER, x1, y1);
        tile(graphics, x0, y0 + BORDER, x0 + BORDER, y1 - BORDER);
        tile(graphics, x1 - BORDER, y0 + BORDER, x1, y1 - BORDER);
        rim(graphics, x0, y0, x1, y1, BEZEL_LIGHT, BEZEL_SHADOW);
        rim(graphics, x0 + BORDER - 1, y0 + BORDER - 1, x1 - BORDER + 1, y1 - BORDER + 1, BEZEL_SHADOW, BEZEL_LIGHT);
        graphics.fillGradient(x0 + BORDER, y0 + BORDER, x1 - BORDER, y1 - BORDER, FACE_TOP, FACE);
        for (int y = y0 + BORDER + 2; y < y1 - BORDER; y += SCANLINE_PITCH) {
            graphics.fill(x0 + BORDER, y, x1 - BORDER, y + 1, SCANLINE);
        }
        int middle = BORDER / 2;
        for (int x : new int[]{x0 + middle, x1 - middle - 1}) {
            for (int y : new int[]{y0 + middle, y1 - middle - 1}) {
                drawDisc(graphics, x, y, SCREW_RADIUS, SCREW);
                graphics.fill(x - 1, y, x + 2, y + 1, SCREW_SLOT);
            }
        }
    }

    /** The bezel's brushed metal over a strip of it. */
    private static void tile(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        for (int x = x0; x < x1; x += BEZEL_TILE) {
            for (int y = y0; y < y1; y += BEZEL_TILE) {
                graphics.blit(BEZEL, x, y, 0, 0, Math.min(BEZEL_TILE, x1 - x), Math.min(BEZEL_TILE, y1 - y), BEZEL_TILE, BEZEL_TILE);
            }
        }
    }

    /** A rim one pixel wide: one colour along its top and left, another along its bottom and right. */
    private static void rim(GuiGraphics graphics, int x0, int y0, int x1, int y1, int topLeft, int bottomRight) {
        graphics.fill(x0, y0, x1, y0 + 1, topLeft);
        graphics.fill(x0, y0 + 1, x0 + 1, y1, topLeft);
        graphics.fill(x0 + 1, y1 - 1, x1, y1, bottomRight);
        graphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, bottomRight);
    }

    /** The title bar across the top of the face: a picture and the title on a lighter strip, ruled off in cyan. */
    static void drawTitleBar(GuiGraphics graphics, Font font, Icon icon, Component title, int x0, int y0, int x1) {
        int top = y0 + BORDER;
        graphics.fill(x0 + BORDER, top, x1 - BORDER, top + TITLE_HEIGHT, TITLE_BAR);
        graphics.fill(x0 + BORDER, top + TITLE_HEIGHT, x1 - BORDER, top + TITLE_HEIGHT + 1, TITLE_RULE);
        int inset = (TITLE_HEIGHT - Icon.SIZE) / 2;
        icon.draw(graphics, x0 + BORDER + inset, top + inset, 1, true);
        graphics.drawString(font, title, x0 + BORDER + 2 * inset + Icon.SIZE + 1, top + (TITLE_HEIGHT - GLYPH_HEIGHT) / 2, WHITE, true);
    }

    /** Black glass for a readout, in a thin frame just outside it. */
    static void drawGlass(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        graphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, EDGE);
        graphics.fill(x0, y0, x1, y1, GLASS);
    }

    /** An indicator lamp in a dark socket, its colour at a brightness from 0 (out) to 1, with a glint while lit. */
    static void drawLamp(GuiGraphics graphics, int cx, int cy, int colour, float brightness) {
        drawDisc(graphics, cx, cy, LAMP_RADIUS + 1, SOCKET);
        drawDisc(graphics, cx, cy, LAMP_RADIUS, shade(colour, brightness));
        if (brightness >= LAMP_GLINT) {
            graphics.fill(cx - 1, cy - 2, cx, cy - 1, GLINT);
        }
    }

    /**
     * A button's face, edged in its colour (dimly at rest): taking on its colour under the pointer and more while held
     * down, when its shine and shade swap so it looks pressed in; lit, green, for what is chosen; dark while it
     * cannot be used.
     */
    static void drawTile(GuiGraphics graphics, int x0, int y0, int x1, int y1, int colour, Look look, boolean lit) {
        if (look == Look.OFF) {
            graphics.fill(x0, y0, x1, y1, OFF);
            graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, TILE_OFF);
            return;
        }
        int edge = lit ? GREEN : colour;
        float tint = switch (look) {
            case HOT -> HOT_TINT;
            case PRESSED -> PRESSED_TINT;
            case OFF, IDLE -> 0F;
        };
        graphics.fill(x0, y0, x1, y1, look == Look.IDLE && !lit ? shade(edge, EDGE_DIM) : edge);
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, blend(lit ? TILE_LIT : TILE, edge, tint));
        boolean pressed = look == Look.PRESSED;
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, pressed ? SHADE : SHINE);
        graphics.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, pressed ? SHINE : SHADE);
    }

    /** A soft glow around a button, in its colour, pulsing to show the way. */
    static void drawGlow(GuiGraphics graphics, int x0, int y0, int x1, int y1, int colour) {
        float strength = pulse(GLOW_PERIOD);
        for (int ring = 1; ring <= GLOW_RINGS; ring++) {
            int alpha = Math.round(GLOW_ALPHA * strength / ring);
            graphics.renderOutline(x0 - ring, y0 - ring, x1 - x0 + 2 * ring, y1 - y0 + 2 * ring, alpha << 24 | colour & 0xFFFFFF);
        }
    }

    /** A filled circle, drawn a row at a time. */
    static void drawDisc(GuiGraphics graphics, int cx, int cy, int radius, int colour) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.floor(Math.sqrt(radius * radius - dy * dy));
            graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, colour);
        }
    }

    /** A smooth swing from 0 to 1 and back, once in a period of milliseconds, for glows and lamps. */
    static float pulse(float period) {
        return 0.5F + 0.5F * Mth.sin(Util.getMillis() % (long) period / period * Mth.TWO_PI);
    }

    /** A colour part of the way to another, alpha and all. */
    static int blend(int from, int to, float t) {
        int a = (int) Mth.lerp(t, from >>> 24, to >>> 24);
        int r = (int) Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF);
        int g = (int) Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** A colour darkened to a share of its brightness, its alpha kept. */
    static int shade(int colour, float share) {
        int r = Math.round((colour >> 16 & 0xFF) * share);
        int g = Math.round((colour >> 8 & 0xFF) * share);
        int b = Math.round((colour & 0xFF) * share);
        return colour & 0xFF000000 | r << 16 | g << 8 | b;
    }

    /**
     * A panel button. What it does is in {@link #onPress}, so that pressing it from the keyboard, or from code, does
     * what a click does; its label is what is narrated, and where it only shows a picture, its tooltip.
     */
    abstract static class PanelButton extends AbstractButton {
        PanelButton(int x, int y, int width, int height, Component message) {
            super(x, y, width, height, message);
        }

        /** Shows the label while the pointer is on the button: not narrated again, being the button's label already. */
        void showLabel() {
            setTooltip(Tooltip.create(getMessage(), null));
        }

        /** How it looks with the pointer on it or not. */
        Look look(boolean pointedAt) {
            if (!active) {
                return Look.OFF;
            }
            if (pointedAt) {
                return Minecraft.getInstance().mouseHandler.isLeftPressed() ? Look.PRESSED : Look.HOT;
            }
            return Look.IDLE;
        }

        /** How it looks now, lit up as under the pointer while it has the keyboard. */
        Look look() {
            Look look = look(isHovered());
            return look == Look.IDLE && isFocused() ? Look.HOT : look;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /**
     * A button with a picture on it, as large as fits, edged in its colour, and its label beside the picture where
     * there is room (else its tooltip). A glowing one pulses, to show the way.
     */
    static final class IconButton extends PanelButton {
        private static final int MARGIN = 4;
        private static final int LABEL_GAP = 6;
        private final Icon icon;
        private final int colour;
        private final boolean glowing;
        private final Runnable action;

        IconButton(int x, int y, int width, int height, Component message, Icon icon, int colour, boolean glowing, Runnable action) {
            super(x, y, width, height, message);
            this.icon = icon;
            this.colour = colour;
            this.glowing = glowing;
            this.action = action;
            if (!captioned()) {
                showLabel();
            }
        }

        private int scale() {
            return Math.max(1, (height - 2) / Icon.SIZE);
        }

        private boolean captioned() {
            return width >= 2 * MARGIN + Icon.SIZE * scale() + LABEL_GAP + Minecraft.getInstance().font.width(getMessage());
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (glowing && active) {
                drawGlow(graphics, getX(), getY(), getRight(), getBottom(), colour);
            }
            Look look = look();
            drawTile(graphics, getX(), getY(), getRight(), getBottom(), colour, look, false);
            Font font = Minecraft.getInstance().font;
            int size = Icon.SIZE * scale();
            boolean captioned = captioned();
            int content = captioned ? size + LABEL_GAP + font.width(getMessage()) : size;
            int x = getX() + (width - content) / 2;
            int sink = look == Look.PRESSED ? 1 : 0;
            icon.draw(graphics, x, getY() + (height - size) / 2 + sink, scale(), active);
            if (captioned) {
                graphics.drawString(font, getMessage(), x + size + LABEL_GAP, getY() + (height - GLYPH_HEIGHT) / 2 + sink, active ? WHITE : OFF, true);
            }
        }
    }

    /** The close button in a panel's title bar: an X, white on red under the pointer. Its label is Cancel. */
    static final class CloseButton extends PanelButton {
        static final int SIZE = Icon.SIZE;
        private static final Icon CROSS = Icon.of("panel/close");
        private final Runnable onClose;

        CloseButton(int x, int y, Runnable onClose) {
            super(x, y, SIZE, SIZE, CommonComponents.GUI_CANCEL);
            this.onClose = onClose;
            showLabel();
        }

        @Override
        public void onPress() {
            onClose.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Look look = look();
            boolean hot = look == Look.HOT || look == Look.PRESSED;
            if (hot) {
                graphics.fill(getX(), getY(), getRight(), getBottom(), look == Look.PRESSED ? shade(RED, EDGE_DIM) : RED);
            }
            CROSS.draw(graphics, getX(), getY(), 1, hot ? WHITE : GREY);
        }
    }

    /** A box a number is typed into: black glass in a thin frame, lit in its colour while it has the keyboard. */
    static final class ValueBox extends EditBox {
        /** How far in from the frame the text sits. */
        private static final int INSET = 5;
        private final int colour;

        ValueBox(Font font, int x, int y, int width, int height, Component name, int colour) {
            super(font, x, y, width, height, name);
            this.colour = colour;
            // Its own frame, drawn below, instead of vanilla's.
            setBordered(false);
            setTextColorUneditable(OFF);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int edge = !isActive() ? OFF : isFocused() ? colour : isHovered() ? shade(colour, EDGE_DIM) : EDGE;
            graphics.fill(getX(), getY(), getRight(), getBottom(), edge);
            graphics.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, GLASS);
            // Without its frame the box writes from its very corner: moved in, as far as with vanilla's frame.
            graphics.pose().pushPose();
            graphics.pose().translate(INSET, (height - GLYPH_HEIGHT) / 2F, 0);
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            graphics.pose().popPose();
        }

        @Override
        public void onClick(double mouseX, double mouseY, int button) {
            super.onClick(mouseX - INSET, mouseY, button);
        }
    }
}
