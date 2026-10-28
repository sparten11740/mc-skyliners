package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Quads of one flat colour on vanilla's white texture, for the renderers that build their parts out of plain coloured
 * faces: a zeppelin's propellers, its ship's wheels and its painters' cradle; and strands of rope or chain, as the
 * sky crane's and the rover's winches hang theirs.
 */
final class FlatQuads {
    /** Vanilla's plain white texture, which each quad's colour tints. */
    static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    private FlatQuads() {
    }

    /** A quad of one colour facing one way, its corners in order round it. */
    static void quad(VertexConsumer consumer, PoseStack.Pose pose, int light, int colour, float nx, float ny, float nz,
                     float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4) {
        vertex(consumer, pose, light, colour, nx, ny, nz, x1, y1, z1);
        vertex(consumer, pose, light, colour, nx, ny, nz, x2, y2, z2);
        vertex(consumer, pose, light, colour, nx, ny, nz, x3, y3, z3);
        vertex(consumer, pose, light, colour, nx, ny, nz, x4, y4, z4);
    }

    /** A box of one colour square to the pose, from one corner to the other: its six faces. */
    static void box(VertexConsumer consumer, PoseStack.Pose pose, int light, int colour, float x0, float y0, float z0, float x1, float y1, float z1) {
        quad(consumer, pose, light, colour, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(consumer, pose, light, colour, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(consumer, pose, light, colour, 0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(consumer, pose, light, colour, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(consumer, pose, light, colour, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(consumer, pose, light, colour, 1, 0, 0, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    /**
     * A strand of one colour between two points, so many blocks either side of its line, a thin strip turned to face
     * the camera, which is given in the same place as the points.
     */
    static void strand(VertexConsumer consumer, PoseStack.Pose pose, int light, int colour, Vec3 camera, Vec3 from, Vec3 to, float half) {
        Vec3 way = to.subtract(from);
        Vec3 across = way.cross(from.subtract(camera));
        if (across.lengthSqr() < Mth.EPSILON) {
            return;
        }
        across = across.normalize().scale(half);
        quad(consumer, pose, light, colour, 0, 1, 0,
            (float) (from.x + across.x), (float) (from.y + across.y), (float) (from.z + across.z),
            (float) (from.x - across.x), (float) (from.y - across.y), (float) (from.z - across.z),
            (float) (to.x - across.x), (float) (to.y - across.y), (float) (to.z - across.z),
            (float) (to.x + across.x), (float) (to.y + across.y), (float) (to.z + across.z));
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int light, int colour, float nx, float ny, float nz, float x, float y, float z) {
        consumer.addVertex(pose, x, y, z)
            .setColor(colour)
            .setUv(0.5F, 0.5F)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, nx, ny, nz);
    }
}
