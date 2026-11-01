package dev.skyliners.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Lightning striking the airliner the player rides, as they see it from inside: a blinding white flash over
 * everything, fading fast, then the cabin flickering dark twice as the electrics catch their breath, and the flight
 * deck's screens full of static for a moment (see {@link FlightDisplayRenderer}). The server says when (see
 * {@link dev.skyliners.cabin.LightningStrikes}); the bolt itself, the sky's flash and the thunder are vanilla's.
 */
public final class LightningFlash {
    /** How long the white flash takes to fade, in ticks, and how bright it starts, as the overlay's opacity (0 to 255). */
    private static final int FLASH_TICKS = 6;
    private static final int FLASH_ALPHA = 235;
    /** The two flickers of the lights going out, each from one tick to another after the strike, and how dark. */
    private static final int[][] FLICKERS = {{8, 10}, {13, 15}};
    private static final int FLICKER_ALPHA = 170;
    /** How long the screens show static after a strike, in ticks. */
    private static final int STATIC_TICKS = 30;
    /** Ticks since the last strike: long ago at first. */
    private static int since = Integer.MAX_VALUE;

    private LightningFlash() {
    }

    /** Lightning has struck the airliner the player rides. */
    public static void struck() {
        since = 0;
    }

    public static void tick(ClientTickEvent.Post event) {
        if (since < Integer.MAX_VALUE) {
            since++;
        }
    }

    /** Whether the flight deck's screens show static, just after a strike. */
    static boolean screensStatic() {
        return since < STATIC_TICKS;
    }

    /** The flash, then the flickers, over the whole screen. */
    public static void overlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (since > FLICKERS[FLICKERS.length - 1][1]) {
            return;
        }
        float t = since + deltaTracker.getGameTimeDeltaPartialTick(false);
        int colour = flash(t);
        if (colour != 0) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), colour);
        }
    }

    /** The overlay's colour this far into the strike, in ticks: white fading, a dark flicker, or none (0). */
    private static int flash(float t) {
        if (t < FLASH_TICKS) {
            int alpha = Math.round(FLASH_ALPHA * (1 - t / FLASH_TICKS));
            return alpha << 24 | 0xFFFFFF;
        }
        for (int[] flicker : FLICKERS) {
            if (t >= flicker[0] && t < flicker[1]) {
                return FLICKER_ALPHA << 24;
            }
        }
        return 0;
    }
}
