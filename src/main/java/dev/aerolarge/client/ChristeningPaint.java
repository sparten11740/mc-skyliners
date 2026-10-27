package dev.aerolarge.client;

import dev.aerolarge.airframe.FinArt;
import dev.aerolarge.airframe.FlankArt;
import dev.aerolarge.airframe.Livery;
import dev.aerolarge.block.ShipsPlaqueBlockEntity;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A christened zeppelin's crest and name as this client paints them on her flanks (see {@link FlankArt}), from what
 * her plaque keeps (see {@link ShipsPlaqueBlockEntity}): as the doves fly off, the paint sweeps back from the crest
 * along her name on both flanks, a strip at a time, so the hull is meshed again a strip at a time. Her name is
 * lettered in tall bold serif capitals, as the Graf Zeppelin's was, drawn once from a typeface at one block to a pixel
 * and smaller only where it would run too long; dark on a light livery and white on a dark one, whatever the livery
 * under it is.
 */
public final class ChristeningPaint {
    /**
     * How long after the bottle breaks the paint starts, in ticks, and how fast it sweeps back along her, in blocks a
     * tick.
     */
    private static final int PAINT_DELAY = 10;
    private static final double SWEEP = 3;
    /** How long each strip of her name the paint sweeps on at a time is, in blocks: a section of the world. */
    private static final int STRIP = 16;
    private static final int GOLD = 0xD4AF37;
    private static final int DARK_LETTERS = 0x1C1C22;
    private static final int LIGHT_LETTERS = 0xF4F4F0;
    /** How light a livery must be, 0 to 1, for the letters on it to be dark. */
    private static final double LIGHT_LIVERY = 0.4;
    /**
     * How big her name is lettered, in blocks to the type's size, its capitals some 11 blocks tall, about as big
     * against her hull as the Graf Zeppelin's were; and the smallest a long name is lettered to fit, its capitals some
     * 9 blocks tall, as small as they keep their serifs at a block to a pixel.
     */
    private static final float TYPE_SIZE = 17;
    private static final float SMALLEST_TYPE = 14;
    /** How much of a block the letters must cover for it to be painted, out of 255. */
    private static final int COVERED = 128;
    /** How many names' lettering is kept. */
    private static final int KEPT = 16;
    private static final boolean[] SIDES = {false, true};

    private static final Map<String, Lettering> NAMES = new LinkedHashMap<>(KEPT, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Lettering> eldest) {
            return size() > KEPT;
        }
    };

    private ChristeningPaint() {
    }

    /**
     * A name lettered at one block to a pixel: which blocks of it are painted, row by row from the top, how wide and
     * tall it is, and the row under the one its letters stand on.
     */
    private record Lettering(boolean[] painted, int width, int height, int baseline) {
        boolean on(int column, int row) {
            return column >= 0 && column < width && row >= 0 && row < height && painted[row * width + column];
        }
    }

    /** Her crest on both flanks: a banner's picture in a gold frame. */
    private record Crest(int[] picture) implements PaintOverlay {
        @Override
        public int colour(Livery.Spot spot, boolean fabric, Livery under) {
            int along = (int) Math.floor(spot.along()) - FlankArt.CREST_FRONT;
            if (fabric || along < 0 || along >= FlankArt.CREST_WIDTH || !FlankArt.onFlank(spot)) {
                return -1;
            }
            int row = (int) Math.floor(FlankArt.CREST_HEIGHT / 2.0 - FlankArt.rise(spot));
            if (row < 0 || row >= FlankArt.CREST_HEIGHT) {
                return -1;
            }
            int column = spot.across() > 0 ? FlankArt.CREST_WIDTH - 1 - along : along;
            if (column == 0 || column == FlankArt.CREST_WIDTH - 1 || row == 0 || row == FlankArt.CREST_HEIGHT - 1) {
                return GOLD;
            }
            return picture[(row - 1) * FinArt.BANNER_WIDTH + column - 1];
        }

        @Override
        public double from() {
            return FlankArt.CREST_FRONT;
        }

        @Override
        public double to() {
            return FlankArt.CREST_FRONT + FlankArt.CREST_WIDTH;
        }
    }

    /**
     * A strip of her name on one flank, so many blocks from its start, read the right way round from that side: on the
     * starboard side her name runs from aft forward.
     */
    private record Strip(Lettering name, int start, boolean starboard) implements PaintOverlay {
        @Override
        public int colour(Livery.Spot spot, boolean fabric, Livery under) {
            if (fabric || spot.along() < from() || spot.along() >= to() || spot.across() > 0 != starboard || !FlankArt.onFlank(spot)) {
                return -1;
            }
            int across = (int) Math.floor(spot.along()) - FlankArt.NAME_FRONT;
            int column = starboard ? name.width() - 1 - across : across;
            int row = name.baseline() - 1 - (int) Math.floor(FlankArt.rise(spot));
            if (!name.on(column, row)) {
                return -1;
            }
            return lightness(under.colour(spot, false)) >= LIGHT_LIVERY ? DARK_LETTERS : LIGHT_LETTERS;
        }

        @Override
        public double from() {
            return FlankArt.NAME_FRONT + (starboard ? name.width() - end() : start);
        }

        @Override
        public double to() {
            return FlankArt.NAME_FRONT + (starboard ? name.width() - start : end());
        }

        private int end() {
            return Math.min(name.width(), start + STRIP);
        }
    }

    /**
     * What a zeppelin's flanks are painted with, once she is christened: her crest and the strips of her name, as far
     * as the paint has swept along her since.
     */
    static List<PaintOverlay> flanks(ClientLevel level, BoundingBox box) {
        ShipsPlaqueBlockEntity plaque = plaque(level, box);
        if (plaque == null || !plaque.christened()) {
            return List.of();
        }
        double reached = FlankArt.CREST_FRONT + (level.getGameTime() - plaque.when() - PAINT_DELAY) * SWEEP;
        List<PaintOverlay> overlays = new ArrayList<>();
        if (plaque.hasCrest() && reached >= FlankArt.CREST_FRONT) {
            overlays.add(new Crest(ShipsColours.picture(plaque.crestBase(), plaque.crestPatterns())));
        }
        Lettering name = NAMES.computeIfAbsent(plaque.name(), ChristeningPaint::letter);
        for (int start = 0; start < name.width(); start += STRIP) {
            strip(overlays, name, start, reached);
        }
        return overlays;
    }

    /** A strip of her name on each flank, where the paint has reached it. */
    private static void strip(List<PaintOverlay> overlays, Lettering name, int start, double reached) {
        for (boolean starboard : SIDES) {
            Strip strip = new Strip(name, start, starboard);
            if (strip.from() <= reached) {
                overlays.add(strip);
            }
        }
    }

    @Nullable
    private static ShipsPlaqueBlockEntity plaque(ClientLevel level, BoundingBox box) {
        for (ShipsPlaqueBlockEntity plaque : ShipsPlaqueBlockEntity.loaded(level)) {
            if (box.isInside(plaque.getBlockPos())) {
                return plaque;
            }
        }
        return null;
    }

    /**
     * A name lettered in bold serif capitals, at one block to a pixel, smaller where it would run past the longest a
     * name runs; drawn off screen, as the game is told no window is to be opened for it.
     */
    private static Lettering letter(String name) {
        String text = name.toUpperCase(Locale.ROOT);
        if (text.isBlank()) {
            return new Lettering(new boolean[0], 0, 0, 0);
        }
        Font font = new Font(Font.SERIF, Font.BOLD, 1).deriveFont(TYPE_SIZE);
        FontMetrics measure = measure(font);
        if (measure.stringWidth(text) > FlankArt.NAME_LONGEST) {
            font = font.deriveFont(Math.max(SMALLEST_TYPE, TYPE_SIZE * FlankArt.NAME_LONGEST / measure.stringWidth(text)));
            measure = measure(font);
        }
        int width = Math.min(FlankArt.NAME_LONGEST, measure.stringWidth(text));
        int height = measure.getAscent() + measure.getDescent();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = graphics(image, font);
        graphics.drawString(text, 0, measure.getAscent());
        graphics.dispose();
        boolean[] painted = new boolean[width * height];
        for (int pixel = 0; pixel < painted.length; pixel++) {
            painted[pixel] = image.getRGB(pixel % width, pixel / width) >>> 24 >= COVERED;
        }
        return new Lettering(painted, width, height, measure.getAscent());
    }

    /** How a typeface measures. */
    private static FontMetrics measure(Font font) {
        Graphics2D graphics = graphics(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), font);
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.dispose();
        return metrics;
    }

    /**
     * Something to letter in a typeface with, its edges smoothed, so a block is painted where the letter covers most
     * of it.
     */
    private static Graphics2D graphics(BufferedImage image, Font font) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setFont(font);
        graphics.setColor(Color.BLACK);
        return graphics;
    }

    /** How light a colour is, 0 to 1, as the eye sees it. */
    private static double lightness(int colour) {
        return (0.2126 * (colour >> 16 & 0xFF) + 0.7152 * (colour >> 8 & 0xFF) + 0.0722 * (colour & 0xFF)) / 255;
    }
}
