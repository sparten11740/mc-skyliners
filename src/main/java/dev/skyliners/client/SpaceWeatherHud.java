package dev.skyliners.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.skyliners.Skyliners;
import dev.skyliners.spaceweather.SpaceWeather;
import dev.skyliners.spaceweather.SpaceWeatherShown;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * Space weather on the screen of everyone it is shown to (see {@link SpaceWeatherSky}), so nobody need read anything
 * to know what is coming: at the top of the screen, its picture in a badge ringed in its colour, flashing while it is
 * told and lit steady while it is on her (amber meteors streaking down, a red lumpy rock, a yellow sun throwing out a
 * flare), then a green tick as it is all clear; and while a solar flare is on her, a warm glare pulsing over the whole
 * view, now and then flickering dark, as her screens flicker with it (see {@link #screensFlicker}).
 */
public final class SpaceWeatherHud {
    /** The badge: how big it is and its picture, and how far down from the top of the screen, in the GUI's pixels. */
    private static final int BADGE = 40;
    private static final int PICTURE = 32;
    private static final int TOP = 8;
    /** Its ring, how thick, in pixels, and its backing, a dark glass. */
    private static final int RING = 2;
    private static final int BACKING = 0xC8101418;
    /** Each kind's colour, and the all-clear's. */
    private static final int METEOR_COLOUR = 0xFFFFB238;
    private static final int ASTEROID_COLOUR = 0xFFFF4A3A;
    private static final int FLARE_COLOUR = 0xFFFFE14A;
    private static final int CLEAR_COLOUR = 0xFF4CF08A;
    /** How long it flashes on and how long a whole flash takes while told, in ticks; how dim it shows off. */
    private static final int FLASH_ON = 8;
    private static final int FLASH_EVERY = 14;
    private static final float FLASH_DIM = 0.3F;
    /**
     * A flare's glare: its colour, how opaque it is at the least and the most, as the overlay's opacity (0 to 255), and
     * how long one swell of it takes, in ticks.
     */
    private static final int GLARE = 0xFFE8B0;
    private static final int GLARE_LEAST = 50;
    private static final int GLARE_MOST = 130;
    private static final float GLARE_SWELL = 50;
    /** How often a flare flickers its view and her screens dark, as the share of ticks, and how dark the view goes. */
    private static final double FLICKER_SHARE = 0.12;
    private static final int FLICKER_ALPHA = 150;
    /** SplitMix64's step and its mixing constants, by which each tick of a flare is hashed for its flickers. */
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;
    private static final long MIX_ONE = 0xBF58476D1CE4E5B9L;
    private static final long MIX_TWO = 0x94D049BB133111EBL;

    private SpaceWeatherHud() {
    }

    /** The badge, and a flare's glare under it. */
    public static void overlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        SpaceWeatherShown shown = SpaceWeatherSky.shown();
        Minecraft minecraft = Minecraft.getInstance();
        if (shown == null || minecraft.options.hideGui) {
            return;
        }
        if (SpaceWeatherSky.sinceOver() >= 0) {
            badge(graphics, "space_all_clear", CLEAR_COLOUR, 1);
            return;
        }
        double since = DrawingClock.gameTime(deltaTracker.getGameTimeDeltaPartialTick(false)) - shown.startedAt();
        boolean told = since < shown.warningTicks();
        if (shown.kind() == SpaceWeather.Kind.SOLAR_FLARE && !told && since < shown.warningTicks() + shown.stormTicks()) {
            glare(graphics, since);
        }
        boolean lit = !told || DrawingClock.wholeTicks() % FLASH_EVERY < FLASH_ON;
        badge(graphics, picture(shown.kind()), colour(shown.kind()), lit ? 1 : FLASH_DIM);
    }

    /**
     * Whether a solar flare on the ship the player rides flickers her screens this tick, a tick now and then, the same
     * ticks on every client.
     */
    static boolean screensFlicker() {
        SpaceWeatherShown shown = SpaceWeatherSky.shown();
        if (shown == null || shown.kind() != SpaceWeather.Kind.SOLAR_FLARE || SpaceWeatherSky.sinceOver() >= 0) {
            return false;
        }
        long since = DrawingClock.wholeTicks() - shown.startedAt();
        return since >= shown.warningTicks() && since < shown.warningTicks() + shown.stormTicks() && flickers(DrawingClock.wholeTicks());
    }

    /** The picture of what blows, as her scanner shows it too. */
    static String picture(SpaceWeather.Kind kind) {
        return "space_" + kind.getSerializedName();
    }

    /** The colour of what blows, as her scanner shows it too. */
    static int colour(SpaceWeather.Kind kind) {
        return switch (kind) {
            case METEOR_SHOWER -> METEOR_COLOUR;
            case ASTEROID_STORM -> ASTEROID_COLOUR;
            case SOLAR_FLARE -> FLARE_COLOUR;
        };
    }

    /**
     * The badge at the top of the screen: dark glass ringed in a colour, the picture on it in that colour, as bright as
     * given.
     */
    private static void badge(GuiGraphics graphics, String picture, int colour, float bright) {
        int left = (graphics.guiWidth() - BADGE) / 2;
        int alpha = Math.round(255 * bright);
        int ring = FastColor.ARGB32.color(alpha, colour);
        graphics.fill(left, TOP, left + BADGE, TOP + BADGE, BACKING);
        graphics.fill(left, TOP, left + BADGE, TOP + RING, ring);
        graphics.fill(left, TOP + BADGE - RING, left + BADGE, TOP + BADGE, ring);
        graphics.fill(left, TOP, left + RING, TOP + BADGE, ring);
        graphics.fill(left + BADGE - RING, TOP, left + BADGE, TOP + BADGE, ring);
        RenderSystem.enableBlend();
        graphics.setColor(FastColor.ARGB32.red(colour) / 255F, FastColor.ARGB32.green(colour) / 255F, FastColor.ARGB32.blue(colour) / 255F, bright);
        int inset = (BADGE - PICTURE) / 2;
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/gui/console/" + picture + ".png");
        graphics.blit(texture, left + inset, TOP + inset, PICTURE, PICTURE, 0, 0, 16, 16, 16, 16);
        graphics.setColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
    }

    /**
     * A flare's glare over the whole view, swelling and easing, so far into the weather, in ticks; dark as it flickers.
     */
    private static void glare(GuiGraphics graphics, double since) {
        if (flickers(DrawingClock.wholeTicks())) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), FLICKER_ALPHA << 24);
            return;
        }
        float swell = 0.5F + 0.5F * Mth.sin((float) (since / GLARE_SWELL * Mth.TWO_PI));
        int alpha = Math.round(Mth.lerp(swell, GLARE_LEAST, GLARE_MOST));
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), FastColor.ARGB32.color(alpha, GLARE));
    }

    /** Whether a flare flickers at a game tick, hashed from it alone. */
    private static boolean flickers(long tick) {
        long z = tick * GOLDEN;
        z = (z ^ (z >>> 30)) * MIX_ONE;
        z = (z ^ (z >>> 27)) * MIX_TWO;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53 < FLICKER_SHARE;
    }
}
