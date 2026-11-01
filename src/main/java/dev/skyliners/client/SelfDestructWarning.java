package dev.skyliners.client;

import dev.skyliners.block.SelfDestructButtonBlockEntity;
import java.lang.ref.WeakReference;
import net.minecraft.SharedConstants;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;

/**
 * The self-destruct counting down, on the screen of everyone aboard while the button is armed (the server says when,
 * see {@link dev.skyliners.selfdestruct.SelfDestruct#alarm}), all in pictures, so that the youngest players, who cannot
 * read yet, see it: the screen's edges pulsing red, faster as the time runs out, and over the middle of the view a
 * picture of the button (red, on its yellow-and-black plate) above a row of red lamps, one for each second left, going
 * out one by one, the last lit one blinking.
 */
public final class SelfDestructWarning {
    /** How many lamps the row has: one for each second the button stays armed. */
    private static final int LAMPS = SelfDestructButtonBlockEntity.WINDOW / SharedConstants.TICKS_PER_SECOND;
    /** A lamp's size and the gap between two, and the picture of the button's size, in GUI pixels. */
    private static final int LAMP = 8;
    private static final int GAP = 4;
    private static final int BUTTON = 18;
    /** The picture's striped border's width, its red button's radius and the radius of the bright middle it flashes. */
    private static final int STRIPES = 3;
    private static final float DOME = 4.5F;
    private static final float SHINE = 2;
    /** How far down the screen the picture of the button stands, as a share of its height. */
    private static final float DOWN = 0.2F;
    /** The red edge's width, as a share of the screen's shorter side, and how opaque it is at most. */
    private static final float EDGE = 0.12F;
    private static final float EDGE_OPACITY = 0.45F;
    /** The share of that the edge keeps between its pulses, so it never quite goes. */
    private static final float EDGE_RESTING = 0.4F;
    /** How fast the edge pulses, in pulses a second, with the whole time left and with none. */
    private static final float SLOW_PULSE = 1;
    private static final float FAST_PULSE = 4;
    /** How many slices each side's edge is drawn in, fading to nothing toward the middle. */
    private static final int SLICES = 12;
    private static final int RED = 0xFF2010;
    private static final int LAMP_LIT = 0xFFFF3020;
    private static final int LAMP_GLOW = 0xFFFFB0A0;
    private static final int LAMP_OUT = 0xFF3A1414;
    private static final int RIM = 0xFF101010;
    private static final int HAZARD_YELLOW = 0xFFF0C020;
    private static final int HAZARD_BLACK = 0xFF1A1A1A;
    private static final int PLATE = 0xFF40444A;

    /** When the countdown runs out, in the level's game time; 0 while none runs. */
    private static long until;
    /** The level the countdown runs in: changing world or dimension forgets it, and it does not keep a world left. */
    private static WeakReference<ClientLevel> shownIn = new WeakReference<>(null);

    private SelfDestructWarning() {
    }

    /** The button is armed with this many ticks left, or (0) no longer. */
    public static void alarm(int ticks) {
        ClientLevel level = Minecraft.getInstance().level;
        shownIn = new WeakReference<>(level);
        until = ticks > 0 && level != null ? level.getGameTime() + ticks : 0;
    }

    /** The red edges and the countdown, while it runs. */
    public static void overlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        ClientLevel level = Minecraft.getInstance().level;
        if (until == 0 || level == null || level != shownIn.get()) {
            return;
        }
        float left = (float) (until - DrawingClock.gameTime(deltaTracker.getGameTimeDeltaPartialTick(false)));
        if (left <= 0) {
            until = 0;
            return;
        }
        float glow = glow(left);
        edges(graphics, EDGE_OPACITY * Mth.lerp(glow, EDGE_RESTING, 1));
        int seconds = Mth.ceil(left / SharedConstants.TICKS_PER_SECOND);
        int top = Math.round(graphics.guiHeight() * DOWN);
        button(graphics, (graphics.guiWidth() - BUTTON) / 2, top, glow);
        lamps(graphics, top + BUTTON + GAP, seconds, glow > 0.5F);
    }

    /**
     * How bright the flashing is with this many ticks left, from 0 to 1: pulsing faster and faster, from
     * {@link #SLOW_PULSE} as the button is armed to {@link #FAST_PULSE} as its time runs out, the button's own light in
     * step with the screens (see {@link SelfDestructButtonRenderer}).
     */
    static float glow(float left) {
        float window = SelfDestructButtonBlockEntity.WINDOW / (float) SharedConstants.TICKS_PER_SECOND;
        float armed = Mth.clamp(window - left / SharedConstants.TICKS_PER_SECOND, 0, window);
        // The pulses so far, the rate rising evenly from the slow to the fast.
        float pulses = SLOW_PULSE * armed + (FAST_PULSE - SLOW_PULSE) * armed * armed / (2 * window);
        return 0.5F + 0.5F * Mth.cos(pulses * Mth.TWO_PI);
    }

    /** The red glow round the screen's edges, this opaque at the very edge and fading to nothing inward. */
    private static void edges(GuiGraphics graphics, float opacity) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int band = Math.max(SLICES, Math.round(Math.min(width, height) * EDGE));
        for (int i = 0; i < SLICES; i++) {
            int alpha = Math.round(opacity * (1 - i / (float) SLICES) * 255);
            int colour = alpha << 24 | RED;
            int inner = band * i / SLICES;
            int outer = band * (i + 1) / SLICES;
            graphics.fill(inner, inner, width - inner, outer, colour);
            graphics.fill(inner, height - outer, width - inner, height - inner, colour);
            graphics.fill(inner, outer, outer, height - outer, colour);
            graphics.fill(width - outer, outer, width - inner, height - outer, colour);
        }
    }

    /** The picture of the button: its striped plate and the red button in it, brighter as it flashes. */
    private static void button(GuiGraphics graphics, int left, int top, float glow) {
        for (int y = 0; y < BUTTON; y++) {
            for (int x = 0; x < BUTTON; x++) {
                graphics.fill(left + x, top + y, left + x + 1, top + y + 1, buttonPixel(x, y, glow));
            }
        }
    }

    /** The colour of one pixel of the picture of the button. */
    private static int buttonPixel(int x, int y, float glow) {
        if (x < STRIPES || y < STRIPES || x >= BUTTON - STRIPES || y >= BUTTON - STRIPES) {
            return (x + y) % 4 < 2 ? HAZARD_YELLOW : HAZARD_BLACK;
        }
        float middle = (BUTTON - 1) / 2F;
        float out = Mth.sqrt((x - middle) * (x - middle) + (y - middle) * (y - middle));
        if (out > DOME) {
            return PLATE;
        }
        return out < SHINE && glow > 0.5F ? LAMP_GLOW : LAMP_LIT;
    }

    /** The row of lamps, one for each second, the ones still to come lit, the last of them blinking. */
    private static void lamps(GuiGraphics graphics, int top, int seconds, boolean blink) {
        int width = LAMPS * LAMP + (LAMPS - 1) * GAP;
        int left = (graphics.guiWidth() - width) / 2;
        for (int i = 0; i < LAMPS; i++) {
            int x = left + i * (LAMP + GAP);
            boolean lit = i < seconds && (i < seconds - 1 || blink);
            graphics.fill(x - 1, top - 1, x + LAMP + 1, top + LAMP + 1, RIM);
            graphics.fill(x, top, x + LAMP, top + LAMP, lit ? LAMP_LIT : LAMP_OUT);
            if (lit) {
                graphics.fill(x + 2, top + 2, x + LAMP - 3, top + LAMP - 3, LAMP_GLOW);
            }
        }
    }
}
