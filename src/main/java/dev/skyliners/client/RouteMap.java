package dev.skyliners.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skyliners.Skyliners;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector2d;

/**
 * An airliner's route map, as its passenger screens show it (painted by {@link MapPainter}): the land below, the
 * villages, the route flown since take-off from a green dot where it took off, and the airliner as a gold arrow
 * pointing the way it goes. The map takes in the whole route and the airliner, zooming out as the route grows, and no
 * closer than a couple of blocks a pixel. It is drawn into a texture of its own a few times a second, one for all the
 * airliner's screens.
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

    private final DynamicTexture texture = new DynamicTexture(WIDTH, HEIGHT, true);
    private final ResourceLocation location;
    private long drawnAt = Long.MIN_VALUE;

    RouteMap(UUID airliner) {
        location = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "route_map/" + airliner);
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
        NativeImage image = texture.getPixels();
        if (image == null) {
            return;
        }
        TerrainMap map = TerrainMap.of(level);
        MapPainter.View view = view(route, x, z);
        MapPainter.land(image, map, view);
        MapPainter.villages(image, map, view);
        MapPainter.route(image, view, route, x, z);
        if (!route.isEmpty()) {
            MapPainter.dot(image, view.pixelX(route.getFirst().x), view.pixelY(route.getFirst().y), MapPainter.START);
        }
        MapPainter.airliner(image, view.pixelX(x), view.pixelY(z), Double.isNaN(track) ? 0 : track);
        texture.upload();
    }

    /** The view that takes in the whole route and the airliner. */
    private static MapPainter.View view(List<Vector2d> route, double x, double z) {
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
        return new MapPainter.View((minX + maxX) / 2, (minZ + maxZ) / 2, scale, WIDTH, HEIGHT);
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
}
