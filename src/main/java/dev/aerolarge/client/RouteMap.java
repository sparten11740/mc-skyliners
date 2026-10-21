package dev.aerolarge.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.Aerolarge;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector2d;

/**
 * An airliner's route map, as its passenger screens show it: the land below as {@link TerrainMap} knows it, coloured
 * as vanilla's maps colour it and shaded by its slopes (the slopes facing north darker, as if lit from the north),
 * water darker the deeper it is, and the land not seen yet left dark; over it the route flown since take-off, the
 * villages as little houses with red roofs, a green dot where it took off and the airliner as a gold arrow pointing
 * the way it goes. North is up. The map takes in the whole route and the airliner, zooming out as the route grows, and
 * no closer than a couple of blocks a pixel. It is drawn into a texture of its own a few times a second, one for all
 * the airliner's screens.
 */
final class RouteMap implements AutoCloseable {
    /** The texture's size, in pixels: about the screen's own shape under its header. */
    static final int WIDTH = 160;
    static final int HEIGHT = 96;
    /** How often it is drawn again, in ticks. */
    private static final int REDRAW_TICKS = 10;
    /** How many blocks a pixel takes in, at the closest and the furthest. */
    private static final double CLOSEST = 2;
    private static final double FURTHEST = 256;
    /** How much of the map the route takes up at most, across and down. */
    private static final double FILL = 0.8;
    /** How much darker or lighter a slope is drawn, facing away from the north or toward it, and the flat. */
    private static final double SLOPE_DOWN = 0.72;
    private static final double FLAT = 0.86;
    private static final double SLOPE_UP = 1.0;
    /** How much darker water is for each block of its depth, and at most. */
    private static final double DEPTH_SHADE = 0.035;
    private static final double DEEPEST_SHADE = 0.6;

    private static final int UNSEEN = 0x0B1A33;
    private static final int UNSEEN_GRID = 0x10254A;
    private static final int OUTLINE = 0x081226;
    private static final int ROUTE = 0x46C8F0;
    private static final int START = 0x46E070;
    private static final int AIRLINER = 0xF2C230;
    private static final int ROOF = 0xD04030;
    private static final int WALL = 0xF2E6CC;
    private static final int DOOR = 0x5A3A1A;
    /** A village's little house, five pixels across, its middle on the village: roof, wall and door. */
    private static final String[] HOUSE = {"..R..", ".RRR.", "RRRRR", ".WDW.", ".WDW."};

    private final DynamicTexture texture = new DynamicTexture(WIDTH, HEIGHT, true);
    private final ResourceLocation location;
    private long drawnAt = Long.MIN_VALUE;

    RouteMap(UUID airliner) {
        location = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "route_map/" + airliner);
        Minecraft.getInstance().getTextureManager().register(location, texture);
    }

    /**
     * Draws the map again, unless it was drawn only a moment ago: the route flown (in world x and z), the airliner
     * where it is now and the way it goes (a compass heading, NaN before it has moved).
     */
    void update(Level level, List<Vector2d> route, double x, double z, double track, long now) {
        if (drawnAt != Long.MIN_VALUE && now >= drawnAt && now - drawnAt < REDRAW_TICKS) {
            return;
        }
        drawnAt = now;
        TerrainMap map = TerrainMap.of(level);
        double minX = x;
        double maxX = x;
        double minZ = z;
        double maxZ = z;
        for (Vector2d point : route) {
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minZ = Math.min(minZ, point.y);
            maxZ = Math.max(maxZ, point.y);
        }
        double scale = Mth.clamp(Math.max((maxX - minX) / (WIDTH * FILL), (maxZ - minZ) / (HEIGHT * FILL)), CLOSEST, FURTHEST);
        View view = new View((minX + maxX) / 2, (minZ + maxZ) / 2, scale);
        NativeImage image = texture.getPixels();
        if (image == null) {
            return;
        }
        land(image, map, view);
        if (map != null) {
            for (BlockPos village : map.villages()) {
                house(image, view.pixelX(village.getX()), view.pixelY(village.getZ()));
            }
        }
        route(image, view, route, x, z);
        if (!route.isEmpty()) {
            dot(image, view.pixelX(route.getFirst().x), view.pixelY(route.getFirst().y), START);
        }
        airliner(image, view.pixelX(x), view.pixelY(z), Double.isNaN(track) ? 0 : track);
        texture.upload();
    }

    /** Draws the map across a rectangle on a screen's face, lit at full brightness as the screen is. */
    void draw(MultiBufferSource buffers, Matrix4f matrix, double x0, double y0, double x1, double y1, float z) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.text(location));
        vertex(consumer, matrix, x0, y0, z, 0, 1);
        vertex(consumer, matrix, x1, y0, z, 1, 1);
        vertex(consumer, matrix, x1, y1, z, 1, 0);
        vertex(consumer, matrix, x0, y1, z, 0, 0);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, double x, double y, float z, float u, float v) {
        consumer.addVertex(matrix, (float) x, (float) y, z).setColor(0xFFFFFFFF).setUv(u, v).setLight(LightTexture.FULL_BRIGHT);
    }

    @Override
    public void close() {
        Minecraft.getInstance().getTextureManager().release(location);
    }

    /** Where the map looks: the world x and z at its middle, and how many blocks a pixel takes in. */
    private record View(double x, double z, double scale) {
        int pixelX(double worldX) {
            return (int) Math.floor((worldX - x) / scale + WIDTH / 2.0);
        }

        int pixelY(double worldZ) {
            return (int) Math.floor((worldZ - z) / scale + HEIGHT / 2.0);
        }

        int worldX(int pixel) {
            return Mth.floor(x + (pixel + 0.5 - WIDTH / 2.0) * scale);
        }

        int worldZ(int pixel) {
            return Mth.floor(z + (pixel + 0.5 - HEIGHT / 2.0) * scale);
        }
    }

    /** The land, pixel by pixel. */
    private static void land(NativeImage image, TerrainMap map, View view) {
        int step = Math.max(1, (int) Math.round(view.scale()));
        for (int py = 0; py < HEIGHT; py++) {
            for (int px = 0; px < WIDTH; px++) {
                set(image, px, py, ground(map, view.worldX(px), view.worldZ(py), step));
            }
        }
    }

    /**
     * The colour of the ground at a column: shaded by the slope to the column a pixel north of it (this many blocks),
     * or by the water's depth; dark where it has not been seen, with a faint grid every 256 blocks so even that shows
     * the map moving.
     */
    private static int ground(TerrainMap map, int x, int z, int step) {
        int colour = map != null ? map.colour(x, z) : TerrainMap.UNKNOWN;
        if (colour == TerrainMap.UNKNOWN) {
            return Math.floorMod(x, 256) < step || Math.floorMod(z, 256) < step ? UNSEEN_GRID : UNSEEN;
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

    /** The route from take-off to the airliner now: a line edged dark, the edge drawn first under the whole of it. */
    private static void route(NativeImage image, View view, List<Vector2d> route, double x, double z) {
        legs(image, view, route, x, z, 3, OUTLINE);
        legs(image, view, route, x, z, 1, ROUTE);
    }

    /** Each leg of the route, point to point and on to the airliner, in lines this many pixels wide. */
    private static void legs(NativeImage image, View view, List<Vector2d> route, double x, double z, int width, int colour) {
        for (int i = 0; i < route.size(); i++) {
            Vector2d from = route.get(i);
            Vector2d to = i + 1 < route.size() ? route.get(i + 1) : new Vector2d(x, z);
            line(image, view.pixelX(from.x), view.pixelY(from.y), view.pixelX(to.x), view.pixelY(to.y), width, colour);
        }
    }

    /** A line from one pixel to another, this many pixels wide (Bresenham's). */
    private static void line(NativeImage image, int x0, int y0, int x1, int y1, int width, int colour) {
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

    /** A square this many pixels across round a pixel. */
    private static void square(NativeImage image, int x, int y, int width, int colour) {
        int from = -(width / 2);
        for (int ox = from; ox < from + width; ox++) {
            for (int oy = from; oy < from + width; oy++) {
                set(image, x + ox, y + oy, colour);
            }
        }
    }

    /** A village's little house, its middle on the village. */
    private static void house(NativeImage image, int x, int y) {
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
    private static void dot(NativeImage image, int x, int y, int colour) {
        square(image, x, y, 5, OUTLINE);
        square(image, x, y, 3, colour);
    }

    /** The airliner: an arrow pointing the way it goes, a compass heading (0 north, 90 east), edged dark. */
    private static void airliner(NativeImage image, int x, int y, double track) {
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

    /** One pixel, if it is on the map, from an RGB colour (the image keeps them as ABGR). */
    private static void set(NativeImage image, int x, int y, int rgb) {
        if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT) {
            image.setPixelRGBA(x, y, 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF));
        }
    }
}
