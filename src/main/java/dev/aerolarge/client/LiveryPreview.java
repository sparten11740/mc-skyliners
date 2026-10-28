package dev.aerolarge.client;

import dev.aerolarge.airframe.Livery;
import dev.aerolarge.airframe.ZeppelinSilhouette;
import dev.aerolarge.airframe.ZeppelinSize;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * A zeppelin seen side on from her right, her nose to the right, painted in a livery just as her own cover and fabric
 * are (see {@link Livery#colour}), for the livery selector's pictures: her hull, shaded to look round, and her upper
 * and lower fins. It is cut into a grid of cells along her and up her, each coloured as the spot of her starboard
 * flank it shows, cheatline and stars and all, and kept as runs of cells of one colour along each row, so a picture is
 * a few dozen boxes. Each livery's picture is worked out once for each size.
 */
final class LiveryPreview {
    /** How many cells the picture is cut into along her and up her. */
    private static final int ALONG = 64;
    private static final int UP = 28;
    /** How much darker her cover is at its top and bottom edges than across the middle of her flank, as a share. */
    private static final double SHADE = 0.35;
    /** How much darker her fins are than her lit flank, as a share: they are seen edge on, out of the sun. */
    private static final double FIN_SHADE = 0.15;
    /** A cell she does not reach into. */
    private static final int CLEAR = 0;
    private static final Map<Key, List<Run>> PICTURES = new HashMap<>();

    private LiveryPreview() {
    }

    private record Key(Livery livery, ZeppelinSize size) {
    }

    /**
     * A run of cells of one colour along a row of the picture: from and to how far across it from the left, and from
     * and to how far up it from the bottom, as shares of its width and height; and its colour, as ARGB.
     */
    record Run(double x0, double x1, double y0, double y1, int colour) {
    }

    /** The picture of a zeppelin of a size painted in a livery. */
    static List<Run> of(Livery livery, ZeppelinSize size) {
        return PICTURES.computeIfAbsent(new Key(livery, size), key -> paint(key.livery(), key.size()));
    }

    /** How much wider than tall a picture of a zeppelin of a size is: her length over her fins' height, tip to tip. */
    static double aspect(ZeppelinSize size) {
        ZeppelinSilhouette outline = ZeppelinSilhouette.of(size);
        return outline.length() / (2 * outline.reach());
    }

    private static List<Run> paint(Livery livery, ZeppelinSize size) {
        ZeppelinSilhouette outline = ZeppelinSilhouette.of(size);
        List<Run> runs = new ArrayList<>();
        for (int row = 0; row < UP; row++) {
            row(livery, size, outline, row, runs);
        }
        return List.copyOf(runs);
    }

    /** A row of the picture, so many cells up from its bottom, as its runs of one colour. */
    private static void row(Livery livery, ZeppelinSize size, ZeppelinSilhouette outline, int row, List<Run> runs) {
        double up = ((row + 0.5) / UP * 2 - 1) * outline.reach();
        int[] colours = new int[ALONG];
        for (int cell = 0; cell < ALONG; cell++) {
            colours[cell] = colour(livery, size, outline, cell, row, up);
        }
        int start = 0;
        for (int cell = 1; cell <= ALONG; cell++) {
            if (cell == ALONG || colours[cell] != colours[start]) {
                run(runs, colours[start], start, cell, row);
                start = cell;
            }
        }
    }

    private static void run(List<Run> runs, int colour, int from, int to, int row) {
        if (colour != CLEAR) {
            runs.add(new Run((double) from / ALONG, (double) to / ALONG, (double) row / UP, (double) (row + 1) / UP, colour));
        }
    }

    /**
     * The colour of a cell so far up from her axis, in blocks, or {@link #CLEAR} where she is not: her cover there on
     * her starboard flank, shaded as it curves away, or the fabric of a fin.
     */
    private static int colour(Livery livery, ZeppelinSize size, ZeppelinSilhouette outline, int cell, int row, double up) {
        double aft = (ALONG - cell - 0.5) / ALONG * outline.length();
        double radius = outline.hull(aft);
        long seed = BlockPos.asLong(cell, row, 0);
        if (Math.abs(up) <= radius) {
            double across = Math.sqrt(radius * radius - up * up);
            return shaded(livery.colour(new Livery.Spot(aft, up, across, seed, size), false), 1 - SHADE * (1 - across / radius));
        }
        if (Math.abs(up) <= outline.fin(aft)) {
            return shaded(livery.colour(new Livery.Spot(aft, up, 0, seed, size), true), 1 - FIN_SHADE);
        }
        return CLEAR;
    }

    /** A colour as RGB, so much as bright, opaque. */
    private static int shaded(int rgb, double light) {
        int red = (int) ((rgb >> 16 & 0xFF) * light);
        int green = (int) ((rgb >> 8 & 0xFF) * light);
        int blue = (int) ((rgb & 0xFF) * light);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
