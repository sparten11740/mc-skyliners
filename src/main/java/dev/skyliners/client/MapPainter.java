package dev.skyliners.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.joml.Vector2d;

/**
 * Painting a map of the land into an image, shared by the passenger screens' route map ({@link RouteMap}) and the
 * autopilot's map ({@link DestinationMap}): the land as {@link TerrainMap} knows it, coloured as vanilla's maps colour
 * it and shaded by its slopes (the slopes facing north darker, as if lit from the north), water darker the deeper it
 * is and the land not seen yet left dark under a faint grid; and over it the villages as little houses with red roofs,
 * routes, dots, circles and the airliner as a gold arrow. North is up.
 */
final class MapPainter {
    /** How much darker or lighter a slope is drawn, facing away from the north or toward it, and the flat. */
    private static final double SLOPE_DOWN = 0.72;
    private static final double FLAT = 0.86;
    private static final double SLOPE_UP = 1.0;
    /** How much darker water is for each block of its depth, and at most. */
    private static final double DEPTH_SHADE = 0.035;
    private static final double DEEPEST_SHADE = 0.6;
    /** How far apart the faint grid over the land not seen yet is, in blocks. */
    private static final int GRID = 256;

    static final int OUTLINE = 0x081226;
    static final int ROUTE = 0x46C8F0;
    static final int START = 0x46E070;
    static final int AIRLINER = 0xF2C230;
    static final int PIN = 0xE8382C;
    private static final int UNSEEN = 0x0B1A33;
    private static final int UNSEEN_GRID = 0x10254A;
    private static final int ROOF = 0xD04030;
    private static final int WALL = 0xF2E6CC;
    private static final int DOOR = 0x5A3A1A;
    /** A village's little house, five pixels across, its middle on the village: roof, wall and door. */
    private static final String[] HOUSE = {"..R..", ".RRR.", "RRRRR", ".WDW.", ".WDW."};

    private MapPainter() {
    }

    /** Where a map looks: the world x and z at its middle, how many blocks a pixel takes in, and its size in pixels. */
    record View(double x, double z, double scale, int width, int height) {
        int pixelX(double worldX) {
            return (int) Math.floor((worldX - x) / scale + width / 2.0);
        }

        int pixelY(double worldZ) {
            return (int) Math.floor((worldZ - z) / scale + height / 2.0);
        }

        double worldX(double pixel) {
            return x + (pixel - width / 2.0) * scale;
        }

        double worldZ(double pixel) {
            return z + (pixel - height / 2.0) * scale;
        }
    }

    /** The land, pixel by pixel, each the column at its middle. */
    static void land(NativeImage image, TerrainMap map, View view) {
        int step = Math.max(1, (int) Math.round(view.scale()));
        for (int py = 0; py < view.height(); py++) {
            for (int px = 0; px < view.width(); px++) {
                set(image, px, py, ground(map, Mth.floor(view.worldX(px + 0.5)), Mth.floor(view.worldZ(py + 0.5)), step));
            }
        }
    }

    /**
     * The colour of the ground at a column: shaded by the slope to the column a pixel north of it (this many blocks),
     * or by the water's depth; dark where it has not been seen, with a faint grid so even that shows the map moving.
     */
    private static int ground(TerrainMap map, int x, int z, int step) {
        int colour = map != null ? map.colour(x, z) : TerrainMap.UNKNOWN;
        if (colour == TerrainMap.UNKNOWN) {
            return Math.floorMod(x, GRID) < step || Math.floorMod(z, GRID) < step ? UNSEEN_GRID : UNSEEN;
        }
        int depth = colour >>> 24;
        if (depth > 0) {
            return shaded(colour & 0xFFFFFF, Math.max(DEEPEST_SHADE, 1 - depth * DEPTH_SHADE));
        }
        int height = map.height(x, z);
        int north = map.height(x, z - step);
        double shade = north == Integer.MIN_VALUE || height == north ? FLAT : height > north ? SLOPE_UP : SLOPE_DOWN;
        return shaded(colour & 0xFFFFFF, shade);
    }

    /** The villages known on the map, as little houses. */
    static void villages(NativeImage image, TerrainMap map, View view) {
        if (map == null) {
            return;
        }
        for (BlockPos village : map.villages()) {
            house(image, view.pixelX(village.getX()), view.pixelY(village.getZ()));
        }
    }

    /** A route through its points and on to a last point: a line edged dark, the edge drawn first under the whole of it. */
    static void route(NativeImage image, View view, List<Vector2d> route, double x, double z) {
        legs(image, view, route, x, z, 3, OUTLINE);
        legs(image, view, route, x, z, 1, ROUTE);
    }

    /** Each leg of the route, point to point and on to the last point, in lines this many pixels wide. */
    private static void legs(NativeImage image, View view, List<Vector2d> route, double x, double z, int width, int colour) {
        for (int i = 0; i < route.size(); i++) {
            Vector2d from = route.get(i);
            Vector2d to = i + 1 < route.size() ? route.get(i + 1) : new Vector2d(x, z);
            line(image, view.pixelX(from.x), view.pixelY(from.y), view.pixelX(to.x), view.pixelY(to.y), width, colour);
        }
    }

    /** A line from one pixel to another, this many pixels wide (Bresenham's). */
    static void line(NativeImage image, int x0, int y0, int x1, int y1, int width, int colour) {
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        square(image, x0, y0, width, colour);
        while (x0 != x1 || y0 != y1) {
            int twice = 2 * error;
            if (twice >= dy) {
                error += dy;
                x0 += sx;
            }
            if (twice <= dx) {
                error += dx;
                y0 += sy;
            }
            square(image, x0, y0, width, colour);
        }
    }

    /** A circle round a point, this many pixels across, in a line edged dark. */
    static void circle(NativeImage image, int x, int y, double radius, int colour) {
        int pieces = Mth.clamp((int) (radius * 2), 12, 360);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < pieces; i++) {
                double a = 2 * Math.PI * i / pieces;
                double b = 2 * Math.PI * (i + 1) / pieces;
                line(image, x + (int) Math.round(radius * Math.cos(a)), y + (int) Math.round(radius * Math.sin(a)),
                    x + (int) Math.round(radius * Math.cos(b)), y + (int) Math.round(radius * Math.sin(b)), pass == 0 ? 3 : 1, pass == 0 ? OUTLINE : colour);
            }
        }
    }

    /** A square this many pixels across round a pixel. */
    static void square(NativeImage image, int x, int y, int width, int colour) {
        int from = -(width / 2);
        for (int ox = from; ox < from + width; ox++) {
            for (int oy = from; oy < from + width; oy++) {
                set(image, x + ox, y + oy, colour);
            }
        }
    }

    /** A village's little house, its middle on the village. */
    static void house(NativeImage image, int x, int y) {
        for (int row = 0; row < HOUSE.length; row++) {
            for (int column = 0; column < HOUSE[row].length(); column++) {
                housePixel(image, x - 2 + column, y - 2 + row, HOUSE[row].charAt(column));
            }
        }
    }

    /** One pixel of a house, as its picture has it: roof, wall, door, or nothing. */
    private static void housePixel(NativeImage image, int x, int y, char part) {
        switch (part) {
            case 'R' -> set(image, x, y, ROOF);
            case 'W' -> set(image, x, y, WALL);
            case 'D' -> set(image, x, y, DOOR);
            default -> {
            }
        }
    }

    /** A dot three pixels across, edged dark. */
    static void dot(NativeImage image, int x, int y, int colour) {
        square(image, x, y, 5, OUTLINE);
        square(image, x, y, 3, colour);
    }

    /** The airliner: an arrow pointing the way it goes, a compass heading (0 north, 90 east), edged dark. */
    static void airliner(NativeImage image, int x, int y, double track) {
        double angle = Math.toRadians(track);
        double ax = Math.sin(angle);
        double ay = -Math.cos(angle);
        // The tip ahead, the two back corners either side behind the middle.
        double[][] arrow = {
            {x + 0.5 + ax * 5, y + 0.5 + ay * 5},
            {x + 0.5 - ax * 3 + ay * 3.5, y + 0.5 - ay * 3 - ax * 3.5},
            {x + 0.5 - ax * 3 - ay * 3.5, y + 0.5 - ay * 3 + ax * 3.5},
        };
        for (int py = y - 7; py <= y + 7; py++) {
            for (int px = x - 7; px <= x + 7; px++) {
                arrowPixel(image, px, py, arrow);
            }
        }
    }

    /** One pixel of the arrow: gold inside it, dark within a pixel of it. */
    private static void arrowPixel(NativeImage image, int x, int y, double[][] arrow) {
        double cx = x + 0.5;
        double cy = y + 0.5;
        if (inside(cx, cy, arrow[0], arrow[1], arrow[2])) {
            set(image, x, y, AIRLINER);
        } else if (near(cx, cy, arrow[0], arrow[1], arrow[2])) {
            set(image, x, y, OUTLINE);
        }
    }

    /** Whether a point is in the triangle. */
    private static boolean inside(double x, double y, double[] a, double[] b, double[] c) {
        double ab = cross(a, b, x, y);
        double bc = cross(b, c, x, y);
        double ca = cross(c, a, x, y);
        return (ab >= 0 && bc >= 0 && ca >= 0) || (ab <= 0 && bc <= 0 && ca <= 0);
    }

    /** Whether a point is in the triangle or within a pixel of it, for its dark edge. */
    private static boolean near(double x, double y, double[] a, double[] b, double[] c) {
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                if (inside(x + ox, y + oy, a, b, c)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double cross(double[] from, double[] to, double x, double y) {
        return (to[0] - from[0]) * (y - from[1]) - (to[1] - from[1]) * (x - from[0]);
    }

    private static int shaded(int rgb, double shade) {
        int r = (int) ((rgb >> 16 & 0xFF) * shade);
        int g = (int) ((rgb >> 8 & 0xFF) * shade);
        int b = (int) ((rgb & 0xFF) * shade);
        return r << 16 | g << 8 | b;
    }

    /** One pixel, if it is on the image, from an RGB colour (the image keeps them as ABGR). */
    static void set(NativeImage image, int x, int y, int rgb) {
        if (x >= 0 && x < image.getWidth() && y >= 0 && y < image.getHeight()) {
            image.setPixelRGBA(x, y, 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF));
        }
    }
}
