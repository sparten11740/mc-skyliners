package dev.skyliners.client;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.airframe.Livery;
import dev.skyliners.airframe.RocketSilhouette;
import dev.skyliners.airframe.ZeppelinSilhouette;
import dev.skyliners.airframe.ZeppelinSize;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * A zeppelin or a rocket seen side on from her right, painted in a livery just as her own cover and fabric are (see
 * {@link Livery#colour} and {@link Livery#rocketColour}), for the livery selector's pictures. The zeppelin lies with
 * her nose to the right, her hull shaded to look round, her upper and lower fins over and under it; the rocket is laid
 * on her side the same way, her nose to the right and her front up, so she fits the same wide picture, her fins
 * standing out ahead of her and behind her at her foot. A picture is cut into a grid of cells along her and across
 * her, each coloured as the spot of her right side it shows, cheatline, checks and stars and all, and kept as runs of
 * cells of one colour along each row, so it is a few dozen boxes. Each livery's picture is worked out once for each
 * size.
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
    /** How far round a rocket from her front her right side's fins stand, ahead of her axis and behind it. */
    private static final double FRONT_FIN = 45;
    private static final double BACK_FIN = 135;
    private static final Map<Key, List<Run>> PICTURES = new HashMap<>();

    private LiveryPreview() {
    }

    private static final Map<Livery, List<Run>> ROCKETS = new HashMap<>();

    private record Key(Livery livery, ZeppelinSize size) {
    }

    /** A cell's colour, by how far along and up the picture it is, in cells; {@link #CLEAR} where she is not. */
    @FunctionalInterface
    private interface Painter {
        int colour(int cell, int row);
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

    /** The picture of a rocket painted in a livery. */
    static List<Run> ofRocket(Livery livery) {
        return ROCKETS.computeIfAbsent(livery, LiveryPreview::paintRocket);
    }

    /** How much wider than tall a picture of a zeppelin of a size is: her length over her fins' height, tip to tip. */
    static double aspect(ZeppelinSize size) {
        ZeppelinSilhouette outline = ZeppelinSilhouette.of(size);
        return outline.length() / (2 * outline.reach());
    }

    /** How much wider than tall a picture of a rocket is: her height over her fins' reach seen from her side. */
    static double rocketAspect() {
        return RocketSilhouette.height() / (2 * RocketSilhouette.reach());
    }

    private static List<Run> paint(Livery livery, ZeppelinSize size) {
        ZeppelinSilhouette outline = ZeppelinSilhouette.of(size);
        return picture((cell, row) -> colour(livery, size, outline, cell, row, ((row + 0.5) / UP * 2 - 1) * outline.reach()));
    }

    private static List<Run> paintRocket(Livery livery) {
        return picture((cell, row) -> rocketColour(livery, cell, row));
    }

    /** A picture as its painter colours its cells, kept as runs of one colour along each row. */
    private static List<Run> picture(Painter painter) {
        List<Run> runs = new ArrayList<>();
        for (int row = 0; row < UP; row++) {
            row(painter, row, runs);
        }
        return List.copyOf(runs);
    }

    /** A row of the picture, so many cells up from its bottom, as its runs of one colour. */
    private static void row(Painter painter, int row, List<Run> runs) {
        int[] colours = new int[ALONG];
        for (int cell = 0; cell < ALONG; cell++) {
            colours[cell] = painter.colour(cell, row);
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

    /**
     * The colour of a cell of a rocket's picture, or {@link #CLEAR} where she is not: her cover there on her right
     * side, shaded as it curves away toward her front and her back, or a fin standing out ahead of her or behind her.
     */
    private static int rocketColour(Livery livery, int cell, int row) {
        double height = (cell + 0.5) / ALONG * RocketSilhouette.height();
        double ahead = ((row + 0.5) / UP * 2 - 1) * RocketSilhouette.reach();
        double radius = RocketSilhouette.body(height);
        double share = height / RocketSilhouette.height();
        if (Math.abs(ahead) <= radius) {
            double across = Math.sqrt(radius * radius - ahead * ahead);
            double round = Math.toDegrees(Math.atan2(across, ahead));
            return shaded(livery.rocketColour(rocketSpot(share, round, cell, row), false), 1 - SHADE * (1 - across / radius));
        }
        if (Math.abs(ahead) <= RocketSilhouette.finOut(height) && Math.abs(ahead) >= RocketSilhouette.finIn(height)) {
            double round = ahead > 0 ? FRONT_FIN : BACK_FIN;
            return shaded(livery.rocketColour(rocketSpot(share, round, cell, row), true), 1 - FIN_SHADE);
        }
        return CLEAR;
    }

    /** The spot a cell of a rocket's picture shows, so far up her and round her, in her stories as one is built. */
    private static Livery.RocketSpot rocketSpot(double share, double round, int cell, int row) {
        double scale = SkylinersConfig.SCALE.get();
        int[] stories = RocketSilhouette.stories(scale);
        int band = Livery.RocketSpot.band((int) (share * RocketSilhouette.rows(scale)), stories);
        int cockpit = Livery.RocketSpot.band(stories[stories.length - 1], stories);
        return new Livery.RocketSpot(share, round, band, cockpit, false, BlockPos.asLong(cell, row, 0));
    }

    /** A colour as RGB, so much as bright, opaque. */
    private static int shaded(int rgb, double light) {
        int red = (int) ((rgb >> 16 & 0xFF) * light);
        int green = (int) ((rgb >> 8 & 0xFF) * light);
        int blue = (int) ((rgb & 0xFF) * light);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
