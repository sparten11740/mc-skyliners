package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.block.AutopilotScreenBlockEntity.Box;
import dev.aerolarge.block.LedColour;
import dev.aerolarge.block.LedConsoleBlockEntity;
import dev.aerolarge.block.LedControls;
import dev.aerolarge.block.LedMode;
import dev.aerolarge.block.LedPanelBlockEntity;
import dev.aerolarge.block.LedSetting;
import dev.aerolarge.block.LedStripBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.joml.Matrix4f;

/**
 * The faces of the rocket's LED panels and of her cockpit's console, drawn where {@link LedControls} lays out their
 * controls, all pictures: dark glass in a black casing; the ten colours' swatches; a sun for each brightness, a dark
 * ring struck through for off and brighter with more rays up to full; a little strip for each mode doing what it does,
 * lit steadily, breathing, chasing along or pulsing in bars; each in the colour it sets, and what is set framed in
 * white. On the console, along the top, her silhouette laid on her side, her foot at the left, each story a band
 * glowing as its strips do, the story picked framed in white, and beside it the whole-rocket button, a little rocket
 * standing up with every story's band lit. Everything is in blocks on the face, at full brightness.
 */
final class LedControlsArt {
    private static final int CASING = 0xFF15171C;
    private static final int GLASS = 0xFF090A0D;
    private static final int TILE = 0xFF1F232A;
    private static final int TILE_PICKED = 0xFF343A45;
    private static final int RIM = 0xFFF4F8FF;
    private static final int DARK_ICON = 0xFF5B616C;
    private static final int HULL = 0xFF8F96A1;
    private static final int HULL_SHADE = 0xFF5E646E;
    private static final int SMOKED = 0xFF101216;
    /** The casing round the glass, and the white frame round what is set, in blocks on the face. */
    private static final double CASING_WIDTH = 0.025;
    private static final double RIM_WIDTH = 0.018;
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

    private LedControlsArt() {
    }

    /** A story's panel's face, a block square, its controls set as the story is. */
    static void panel(Matrix4f matrix, VertexConsumer shapes, LedSetting setting, double ticks) {
        background(matrix, shapes, 1);
        controls(matrix, shapes, LedControls.Face.PANEL, List.of(setting), ticks);
    }

    /**
     * The console's face, two rows wide to one tall: her silhouette with each story's band, the whole-rocket button,
     * and the controls, set as the story picked is, or framing only what every story agrees on while all are picked.
     */
    static void console(Matrix4f matrix, VertexConsumer shapes, LedConsoleBlockEntity console, double ticks) {
        background(matrix, shapes, LedControls.Face.CONSOLE.width());
        List<LedSetting> settings = new ArrayList<>();
        for (int story = 0; story < console.stories(); story++) {
            LedPanelBlockEntity panel = console.panel(story);
            settings.add(panel == null ? LedSetting.PLAIN : panel.setting());
        }
        int picked = console.selected();
        boolean whole = picked < 0 || picked >= settings.size();
        silhouette(matrix, shapes, console.spans(), settings, whole ? LedConsoleBlockEntity.WHOLE_ROCKET : picked, ticks);
        wholeRocket(matrix, shapes, settings, whole, ticks);
        controls(matrix, shapes, LedControls.Face.CONSOLE, whole ? settings : List.of(settings.get(picked)), ticks);
    }

    /** The casing and the dark glass inside it, over a face so wide and a row tall. */
    private static void background(Matrix4f matrix, VertexConsumer shapes, double width) {
        ScreenDrawing.rect(shapes, matrix, 0, 0, width, 1, 0, CASING);
        ScreenDrawing.rect(shapes, matrix, CASING_WIDTH, CASING_WIDTH, width - CASING_WIDTH, 1 - CASING_WIDTH, LAYER, GLASS);
    }

    /**
     * The swatches, the suns and the modes' strips, in the colour of the first of the stories they set, framing what
     * all of those stories are set to.
     */
    private static void controls(Matrix4f matrix, VertexConsumer shapes, LedControls.Face face, List<LedSetting> set, double ticks) {
        LedSetting shown = set.isEmpty() ? LedSetting.PLAIN : set.getFirst();
        LedColour colour = agreed(set, LedSetting::colour);
        Integer brightness = agreed(set, LedSetting::brightness);
        LedMode mode = agreed(set, LedSetting::mode);
        for (LedColour swatch : LedColour.values()) {
            swatch(matrix, shapes, face.swatch(swatch), swatch, swatch == colour);
        }
        for (int glow = 0; glow <= LedStripBlock.BRIGHTEST; glow++) {
            sun(matrix, shapes, face.glow(glow), shown.colour(), glow, brightness != null && brightness == glow);
        }
        for (LedMode way : LedMode.values()) {
            mode(matrix, shapes, face.mode(way), new LedSetting(shown.colour(), LedStripBlock.BRIGHTEST, way), way == mode, ticks);
        }
    }

    /** What every one of the settings has alike; null where they differ, or there are none. */
    @Nullable
    private static <T> T agreed(List<LedSetting> settings, Function<LedSetting, T> value) {
        T first = settings.isEmpty() ? null : value.apply(settings.getFirst());
        for (LedSetting setting : settings) {
            if (!value.apply(setting).equals(first)) {
                return null;
            }
        }
        return first;
    }

    private static void swatch(Matrix4f matrix, VertexConsumer shapes, Box box, LedColour colour, boolean picked) {
        if (picked) {
            rim(matrix, shapes, box);
        }
        rect(matrix, shapes, box, 3, 0xFF000000 | colour.rgb());
    }

    /** A button's tile, framed in white if what it sets is set. */
    private static void tile(Matrix4f matrix, VertexConsumer shapes, Box box, boolean picked) {
        if (picked) {
            rim(matrix, shapes, box);
        }
        rect(matrix, shapes, box, 3, picked ? TILE_PICKED : TILE);
    }

    /**
     * A brightness's sun: a dark ring struck through for off; lit, the brighter the bigger and the more rays it has.
     */
    private static void sun(Matrix4f matrix, VertexConsumer shapes, Box box, LedColour colour, int brightness, boolean picked) {
        tile(matrix, shapes, box, picked);
        double x = (box.x0() + box.x1()) / 2;
        double y = (box.y0() + box.y1()) / 2;
        double size = Math.min(box.x1() - box.x0(), box.y1() - box.y0());
        if (brightness <= 0) {
            ScreenDrawing.disc(shapes, matrix, x, y, RING * size, ROUND, 4 * LAYER, DARK_ICON);
            ScreenDrawing.disc(shapes, matrix, x, y, RING_HOLE * size, ROUND, 5 * LAYER, picked ? TILE_PICKED : TILE);
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

    /** A mode's little strip, doing now what the mode does, in the colour it sets. */
    private static void mode(Matrix4f matrix, VertexConsumer shapes, Box box, LedSetting setting, boolean picked, double ticks) {
        tile(matrix, shapes, box, picked);
        double x = (box.x0() + box.x1()) / 2;
        double y = (box.y0() + box.y1()) / 2;
        double width = box.x1() - box.x0();
        double tall = box.y1() - box.y0();
        double size = Math.min(width, tall);
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
     * are, her cockpit's smoked glass, and each story a band glowing as its strips do, the one picked, or all of them,
     * framed in white.
     */
    private static void silhouette(Matrix4f matrix, VertexConsumer shapes, int[] spans, List<LedSetting> settings, int picked, double ticks) {
        Box picture = LedControls.PICTURE;
        double middle = (picture.y0() + picture.y1()) / 2;
        double tall = picture.y1() - picture.y0();
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
            Box band = LedControls.band(spans, story);
            ScreenDrawing.rect(shapes, matrix, band.x0() + BAND_GAP, middle - BAND_HALF * tall, band.x1() - BAND_GAP, middle + BAND_HALF * tall, 4 * LAYER,
                LedLights.looks(settings.get(story), ticks, story, false));
        }
        if (settings.isEmpty()) {
            return;
        }
        Box first = LedControls.band(spans, picked < 0 ? 0 : picked);
        Box last = LedControls.band(spans, picked < 0 ? settings.size() - 1 : picked);
        outline(matrix, shapes, first.x0(), middle - body - RIM_WIDTH, last.x1(), middle + body + RIM_WIDTH, 5 * LAYER);
    }

    /** How far across the face a share of the silhouette's length from her foot is. */
    private static double along(double share) {
        Box picture = LedControls.PICTURE;
        return picture.x0() + share * (picture.x1() - picture.x0());
    }

    /** The whole-rocket button: a little rocket standing up, a band for each story up her, each glowing as it does. */
    private static void wholeRocket(Matrix4f matrix, VertexConsumer shapes, List<LedSetting> settings, boolean picked, double ticks) {
        Box box = LedControls.WHOLE_ROCKET;
        tile(matrix, shapes, box, picked);
        double x = (box.x0() + box.x1()) / 2;
        double width = box.x1() - box.x0();
        double tall = box.y1() - box.y0();
        double half = 0.15 * width;
        double foot = box.y0() + 0.18 * tall;
        double top = box.y0() + 0.7 * tall;
        ScreenDrawing.rect(shapes, matrix, x - 0.36 * width, box.y0() + 0.08 * tall, x + 0.36 * width, box.y0() + 0.3 * tall, 4 * LAYER, HULL_SHADE);
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

    /** A white frame round a button, just outside it, under it. */
    private static void rim(Matrix4f matrix, VertexConsumer shapes, Box box) {
        ScreenDrawing.rect(shapes, matrix, box.x0() - RIM_WIDTH, box.y0() - RIM_WIDTH, box.x1() + RIM_WIDTH, box.y1() + RIM_WIDTH, 2 * LAYER, RIM);
    }

    /** A white frame's four sides round a span of the face, just inside it. */
    private static void outline(Matrix4f matrix, VertexConsumer shapes, double x0, double y0, double x1, double y1, float z) {
        ScreenDrawing.rect(shapes, matrix, x0, y0, x1, y0 + RIM_WIDTH, z, RIM);
        ScreenDrawing.rect(shapes, matrix, x0, y1 - RIM_WIDTH, x1, y1, z, RIM);
        ScreenDrawing.rect(shapes, matrix, x0, y0, x0 + RIM_WIDTH, y1, z, RIM);
        ScreenDrawing.rect(shapes, matrix, x1 - RIM_WIDTH, y0, x1, y1, z, RIM);
    }

    private static void rect(Matrix4f matrix, VertexConsumer shapes, Box box, int layer, int colour) {
        ScreenDrawing.rect(shapes, matrix, box.x0(), box.y0(), box.x1(), box.y1(), layer * LAYER, colour);
    }
}
