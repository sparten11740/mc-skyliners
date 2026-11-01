package dev.skyliners.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skyliners.block.LedColour;
import dev.skyliners.block.LedConsoleBlockEntity;
import dev.skyliners.block.LedControls;
import dev.skyliners.block.LedMode;
import dev.skyliners.block.LedPanelBlockEntity;
import dev.skyliners.block.LedSetting;
import dev.skyliners.block.LedStripBlock;
import dev.skyliners.block.OvalWindowBlock;
import dev.skyliners.block.TouchPanel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.joml.Matrix4f;

/**
 * The faces of the rocket's LED panels and of her cockpit's console, drawn on the tiles {@link LedControls} lays out,
 * all pictures: dark glass in a black casing; the ten colours' swatches; a sun for each brightness, a dark ring struck
 * through for off and brighter with more rays up to full; a little strip for each mode doing what it does, lit
 * steadily, breathing, chasing along or pulsing in bars; each in the colour it sets. On the console, along the top, her
 * silhouette laid on her side, her foot at the left, each story a band glowing as its strips do, and beside it the
 * whole-rocket button, a little rocket standing up with every story's band lit, and at the end of its bottom row an
 * oval window in a steel rim for each darkness of her windows' glass, the sky and the sun through it dimming from tile
 * to tile. Every tile lights up as every touch
 * panel's does (see {@link TouchPanelArt}): the one the crosshair rests on as looked at, and what is set now as picked:
 * the colour, the brightness and the mode (on the console those every story picked agrees on), and on the console the
 * stories picked, with the whole-rocket button once that is all of them, and the darkness it last set their windows to
 * if they agree. Everything is in blocks on the face, at full brightness.
 */
final class LedControlsArt {
    private static final int CASING = 0xFF15171C;
    private static final int GLASS = 0xFF090A0D;
    private static final int TILE = 0xFF1F232A;
    private static final int DARK_ICON = 0xFF5B616C;
    private static final int HULL = 0xFF8F96A1;
    private static final int HULL_SHADE = 0xFF5E646E;
    private static final int SMOKED = 0xFF101216;
    /** The casing round the glass, in blocks on the face. */
    private static final double CASING_WIDTH = 0.025;
    /** Lifts each layer off the one below it. */
    private static final float LAYER = 0.001F;
    /** The suns: how big the off ring is, the hole in it and its stroke, and how far out the rays reach, as shares. */
    private static final double RING = 0.3;
    private static final double RING_HOLE = 0.21;
    private static final double STROKE = 0.06;
    private static final double RAY_FROM = 0.24;
    private static final double RAY_STEP = 0.07;
    /** How many pieces a round shape is drawn of. */
    private static final int ROUND = 16;
    /** The club's bars' heights, as shares of the tallest, from the left. */
    private static final double[] CLUB_BARS = {0.55, 1, 0.7, 0.85};
    /**
     * The console's silhouette of her, as shares of the picture's length from her foot and of its height: where her
     * body begins and her nose, how far out her body and her fins reach from her middle, and her stories' bands.
     */
    private static final double BODY_FROM = 0.03;
    private static final double NOSE_FROM = 0.86;
    private static final double BODY_HALF = 0.22;
    private static final double FIN_HALF = 0.47;
    private static final double[] FIN_STEPS = {0, 0.05, 0.1, 0.15};
    private static final int NOSE_STEPS = 5;
    private static final double BAND_HALF = 0.15;
    private static final double BAND_GAP = 0.006;
    /**
     * A window's tile: how big the oval is, from its middle to its rim's edge across, as a share of the button's
     * shorter side; how much taller than wide, as her windows are; and how far in its glass starts and how big its sun
     * is, each a share of the oval, and where the sun sits from the middle of the glass.
     */
    private static final float WINDOW_SIZE = 0.4F;
    private static final float WINDOW_TALL = 4F / 3;
    private static final float WINDOW_GLASS = 0.78F;
    private static final float WINDOW_SUN = 0.24F;
    private static final float WINDOW_SUN_AT = 0.28F;
    /** The sky and the sun seen through a window, and how much of them each step of its glass lets through, a share. */
    private static final int WINDOW_SKY = 0x8CCBF0;
    private static final int WINDOW_SUNLIGHT = 0xFFF1B8;
    private static final float[] WINDOW_LIGHT = {1, 0.55F, 0.3F, 0.1F};

    private LedControlsArt() {
    }

    /**
     * A story's panel's face, a block square, its controls set as the story is, the player looking at one of them (or
     * none, -1).
     */
    static void panel(Matrix4f matrix, VertexConsumer shapes, LedSetting setting, int looked, double ticks) {
        background(matrix, shapes, 1);
        controls(matrix, shapes, LedControls.Face.PANEL, List.of(setting), looked, ticks);
    }

    /**
     * The console's face, two rows wide to one tall, the player looking at one of its tiles (or none, -1): her
     * silhouette with each story's band, the whole-rocket button, and the controls, marking only what every story
     * picked agrees on.
     */
    static void console(Matrix4f matrix, VertexConsumer shapes, LedConsoleBlockEntity console, int looked, double ticks) {
        background(matrix, shapes, LedControls.Face.CONSOLE.width());
        List<LedSetting> settings = new ArrayList<>();
        List<LedSetting> picked = new ArrayList<>();
        List<Integer> windows = new ArrayList<>();
        for (int story = 0; story < console.stories(); story++) {
            LedPanelBlockEntity panel = console.panel(story);
            settings.add(panel == null ? LedSetting.PLAIN : panel.setting());
            if (console.picks(story)) {
                picked.add(settings.getLast());
                windows.add(panel == null ? LedPanelBlockEntity.WINDOWS_UNSET : panel.windows());
            }
        }
        silhouette(matrix, shapes, console, settings, looked, ticks);
        wholeRocket(matrix, shapes, settings, look(LedControls.WHOLE_ROCKET_TILE, looked, console.picksAll()), ticks);
        controls(matrix, shapes, LedControls.Face.CONSOLE, picked, looked, ticks);
        Integer dim = agreed(windows, Function.identity());
        for (int step = 0; step <= OvalWindowBlock.DARKEST; step++) {
            int tile = LedControls.window(step);
            window(matrix, shapes, button(console.layout(), tile), step, look(tile, looked, dim != null && dim == step));
        }
    }

    /** A darkness's window on its button: an oval in a steel rim, the sky and the sun through its glass. */
    private static void window(Matrix4f matrix, VertexConsumer shapes, TouchPanel.Tile button, int dim, TouchPanelArt.Look look) {
        TouchPanelArt.tile(shapes, matrix, button, TILE, look, 2 * LAYER);
        Matrix4f oval = new Matrix4f(matrix).translate((float) button.middleX(), (float) button.middleY(), 0).scale(1, WINDOW_TALL, 1);
        double size = WINDOW_SIZE * button.side();
        float light = WINDOW_LIGHT[dim];
        ScreenDrawing.disc(shapes, oval, 0, 0, size, ROUND, 4 * LAYER, HULL);
        ScreenDrawing.disc(shapes, oval, 0, 0, size * WINDOW_GLASS, ROUND, 5 * LAYER, 0xFF000000 | LedLights.scaled(WINDOW_SKY, light));
        ScreenDrawing.disc(shapes, oval, size * WINDOW_SUN_AT, size * WINDOW_SUN_AT, size * WINDOW_SUN, ROUND, 6 * LAYER,
            0xFF000000 | LedLights.scaled(WINDOW_SUNLIGHT, light));
    }

    /** The casing and the dark glass inside it, over a face so wide and a row tall. */
    private static void background(Matrix4f matrix, VertexConsumer shapes, double width) {
        ScreenDrawing.rect(shapes, matrix, 0, 0, width, 1, 0, CASING);
        ScreenDrawing.rect(shapes, matrix, CASING_WIDTH, CASING_WIDTH, width - CASING_WIDTH, 1 - CASING_WIDTH, LAYER, GLASS);
    }

    /**
     * The swatches, the suns and the modes' strips, in the colour of the first of the stories they set, what all of
     * those stories are set to picked.
     */
    private static void controls(Matrix4f matrix, VertexConsumer shapes, LedControls.Face face, List<LedSetting> set, int looked, double ticks) {
        TouchPanel.Layout tiles = face.controls();
        LedSetting shown = set.isEmpty() ? LedSetting.PLAIN : set.getFirst();
        LedColour colour = agreed(set, LedSetting::colour);
        Integer brightness = agreed(set, LedSetting::brightness);
        LedMode mode = agreed(set, LedSetting::mode);
        for (LedColour swatch : LedColour.values()) {
            int tile = LedControls.swatch(swatch);
            TouchPanelArt.tile(shapes, matrix, button(tiles, tile), 0xFF000000 | swatch.rgb(), look(tile, looked, swatch == colour), 2 * LAYER);
        }
        for (int glow = 0; glow <= LedStripBlock.BRIGHTEST; glow++) {
            int tile = LedControls.glow(glow);
            sun(matrix, shapes, button(tiles, tile), shown.colour(), glow, look(tile, looked, brightness != null && brightness == glow));
        }
        for (LedMode way : LedMode.values()) {
            int tile = LedControls.mode(way);
            mode(matrix, shapes, button(tiles, tile), new LedSetting(shown.colour(), LedStripBlock.BRIGHTEST, way), look(tile, looked, way == mode),
                ticks);
        }
    }

    /** Where a control's button is drawn: its tile, less the half gap round it. */
    private static TouchPanel.Tile button(TouchPanel.Layout tiles, int tile) {
        return tiles.get(tile).inset(LedControls.INSET);
    }

    /**
     * A tile's look, the player looking at one tile (or none, -1): always lit, as every control can always be touched,
     * and picked while what it sets is set.
     */
    private static TouchPanelArt.Look look(int tile, int looked, boolean picked) {
        return new TouchPanelArt.Look(tile == looked, picked, true);
    }

    /** What every one of the things has alike; null where they differ, or there are none. */
    @Nullable
    private static <S, T> T agreed(List<S> things, Function<S, T> value) {
        T first = things.isEmpty() ? null : value.apply(things.getFirst());
        for (S thing : things) {
            if (!value.apply(thing).equals(first)) {
                return null;
            }
        }
        return first;
    }

    /**
     * A brightness's sun on its button: a dark ring struck through for off; lit, the brighter the bigger and the more
     * rays it has.
     */
    private static void sun(Matrix4f matrix, VertexConsumer shapes, TouchPanel.Tile button, LedColour colour, int brightness, TouchPanelArt.Look look) {
        TouchPanelArt.tile(shapes, matrix, button, TILE, look, 2 * LAYER);
        double x = button.middleX();
        double y = button.middleY();
        double size = button.side();
        if (brightness <= 0) {
            ScreenDrawing.disc(shapes, matrix, x, y, RING * size, ROUND, 4 * LAYER, DARK_ICON);
            ScreenDrawing.disc(shapes, matrix, x, y, RING_HOLE * size, ROUND, 5 * LAYER, TouchPanelArt.colour(TILE, look));
            double reach = RING * size;
            ScreenDrawing.line(shapes, matrix, x - reach, y - reach, x + reach, y + reach, 6 * LAYER, (float) (STROKE * size), DARK_ICON);
            return;
        }
        int lit = 0xFF000000 | LedLights.scaled(colour.rgb(), LedLights.share(brightness));
        int rays = 4 * brightness;
        double out = RAY_FROM + RAY_STEP * (brightness + 1);
        for (int ray = 0; ray < rays; ray++) {
            double angle = 2 * Math.PI * ray / rays;
            ScreenDrawing.line(shapes, matrix, x + Math.sin(angle) * RAY_FROM * size, y + Math.cos(angle) * RAY_FROM * size,
                x + Math.sin(angle) * out * size, y + Math.cos(angle) * out * size, 4 * LAYER, (float) (STROKE * size), lit);
        }
        ScreenDrawing.disc(shapes, matrix, x, y, (RAY_FROM - RAY_STEP + 0.03 * brightness) * size, ROUND, 4 * LAYER, lit);
    }

    /** A mode's little strip on its button, doing now what the mode does, in the colour it sets. */
    private static void mode(Matrix4f matrix, VertexConsumer shapes, TouchPanel.Tile button, LedSetting setting, TouchPanelArt.Look look, double ticks) {
        TouchPanelArt.tile(shapes, matrix, button, TILE, look, 2 * LAYER);
        double x = button.middleX();
        double y = button.middleY();
        double width = button.width();
        double tall = button.height();
        double size = button.side();
        float z = 4 * LAYER;
        switch (setting.mode()) {
            case STEADY -> ScreenDrawing.rect(shapes, matrix, x - 0.36 * width, y - 0.08 * tall, x + 0.36 * width, y + 0.08 * tall, z,
                LedLights.looks(setting, ticks, 0, false));
            case BREATHE -> {
                int colour = LedLights.looks(setting, ticks, 0, false);
                ScreenDrawing.disc(shapes, matrix, x - 0.28 * width, y, 0.09 * size, ROUND, z, colour);
                ScreenDrawing.disc(shapes, matrix, x, y, 0.15 * size, ROUND, z, colour);
                ScreenDrawing.disc(shapes, matrix, x + 0.28 * width, y, 0.09 * size, ROUND, z, colour);
            }
            case CHASE -> chase(matrix, shapes, x, y, width, tall, setting, ticks);
            case CLUB -> club(matrix, shapes, x, y, width, tall, setting, ticks);
        }
    }

    /** The chase's strip: six dashes, their bands running along them. */
    private static void chase(Matrix4f matrix, VertexConsumer shapes, double x, double y, double width, double tall, LedSetting setting, double ticks) {
        int dashes = 6;
        double step = 0.8 * width / dashes;
        for (int dash = 0; dash < dashes; dash++) {
            double left = x - 0.4 * width + dash * step;
            ScreenDrawing.rect(shapes, matrix, left, y - 0.08 * tall, left + 0.75 * step, y + 0.08 * tall, 4 * LAYER,
                LedLights.looks(setting, ticks, dash, false));
        }
    }

    /**
     * The club's strip: four bars standing up like a sound meter's, jumping on the beat, its two colours taking turns.
     */
    private static void club(Matrix4f matrix, VertexConsumer shapes, double x, double y, double width, double tall, LedSetting setting, double ticks) {
        double bottom = y - 0.35 * tall;
        float beat = LedLights.pulse(ticks);
        for (int bar = 0; bar < CLUB_BARS.length; bar++) {
            double left = x - 0.33 * width + bar * 0.18 * width;
            double height = CLUB_BARS[bar] * (0.3 + 0.7 * beat) * 0.7 * tall;
            ScreenDrawing.rect(shapes, matrix, left, bottom, left + 0.12 * width, bottom + height, 4 * LAYER,
                LedLights.looks(setting, ticks, 0, bar % 2 == 1));
        }
    }

    /**
     * Her silhouette laid on her side, her foot at the left: fins and body in steel, her nose stepped as her blocks
     * are, her cockpit's smoked glass, and each story a band across her body glowing as its strips do, a touch anywhere
     * over her height at it picking it; those the console sets now picked.
     */
    private static void silhouette(Matrix4f matrix, VertexConsumer shapes, LedConsoleBlockEntity console, List<LedSetting> settings, int looked, double ticks) {
        TouchPanel.Layout tiles = console.layout();
        TouchPanel.Tile picture = LedControls.PICTURE;
        double middle = picture.middleY();
        double tall = picture.height();
        double body = BODY_HALF * tall;
        for (int step = 0; step < FIN_STEPS.length - 1; step++) {
            double reach = FIN_HALF * tall - (FIN_HALF * tall - body) * step / (FIN_STEPS.length - 1);
            ScreenDrawing.rect(shapes, matrix, along(FIN_STEPS[step]), middle - reach, along(FIN_STEPS[step + 1]), middle + reach, 2 * LAYER, HULL_SHADE);
        }
        ScreenDrawing.rect(shapes, matrix, along(BODY_FROM), middle - body, along(NOSE_FROM), middle + body, 3 * LAYER, HULL);
        for (int step = 0; step < NOSE_STEPS; step++) {
            double from = NOSE_FROM + (1 - NOSE_FROM) * step / NOSE_STEPS;
            double to = NOSE_FROM + (1 - NOSE_FROM) * (step + 1) / NOSE_STEPS;
            double share = (step + 1.0) / (NOSE_STEPS + 1);
            ScreenDrawing.rect(shapes, matrix, along(from), middle - body * (1 - share * share), along(to), middle + body * (1 - share * share), 3 * LAYER, HULL);
        }
        ScreenDrawing.rect(shapes, matrix, along(LedControls.STORIES_TO), middle - body * 0.8, along(NOSE_FROM), middle + body * 0.8, 4 * LAYER, SMOKED);
        for (int story = 0; story < settings.size(); story++) {
            int tile = LedControls.story(story);
            TouchPanel.Tile band = tiles.get(tile);
            TouchPanel.Tile lit = new TouchPanel.Tile(band.left() + BAND_GAP, middle - BAND_HALF * tall, band.right() - BAND_GAP, middle + BAND_HALF * tall);
            TouchPanelArt.tile(shapes, matrix, lit, LedLights.looks(settings.get(story), ticks, story, false),
                look(tile, looked, console.picks(story)), 5 * LAYER);
        }
    }

    /** How far across the face a share of the silhouette's length from her foot is. */
    private static double along(double share) {
        return LedControls.PICTURE.left() + share * LedControls.PICTURE.width();
    }

    /** The whole-rocket button: a little rocket standing up, a band for each story up her, each glowing as it does. */
    private static void wholeRocket(Matrix4f matrix, VertexConsumer shapes, List<LedSetting> settings, TouchPanelArt.Look look, double ticks) {
        TouchPanel.Tile box = LedControls.WHOLE_ROCKET;
        TouchPanelArt.tile(shapes, matrix, box, TILE, look, 2 * LAYER);
        double x = box.middleX();
        double width = box.width();
        double tall = box.height();
        double half = 0.15 * width;
        double foot = box.bottom() + 0.18 * tall;
        double top = box.bottom() + 0.7 * tall;
        ScreenDrawing.rect(shapes, matrix, x - 0.36 * width, box.bottom() + 0.08 * tall, x + 0.36 * width, box.bottom() + 0.3 * tall, 4 * LAYER, HULL_SHADE);
        ScreenDrawing.rect(shapes, matrix, x - half, foot, x + half, top, 5 * LAYER, HULL);
        for (int step = 0; step < NOSE_STEPS; step++) {
            double share = (step + 1.0) / (NOSE_STEPS + 1);
            double from = top + 0.2 * tall * step / NOSE_STEPS;
            ScreenDrawing.rect(shapes, matrix, x - half * (1 - share * share), from, x + half * (1 - share * share), from + 0.2 * tall / NOSE_STEPS, 5 * LAYER, HULL);
        }
        double band = (top - foot) / Math.max(1, settings.size());
        for (int story = 0; story < settings.size(); story++) {
            double low = foot + story * band;
            ScreenDrawing.rect(shapes, matrix, x - 0.7 * half, low + BAND_GAP, x + 0.7 * half, low + band - BAND_GAP, 6 * LAYER,
                LedLights.looks(settings.get(story), ticks, story, false));
        }
    }
}
