package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Quads of one flat colour on vanilla's white texture, for the renderers that build their parts out of plain coloured
 * faces: a zeppelin's propellers and its ship's wheels.
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

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int light, int colour, float nx, float ny, float nz, float x, float y, float z) {
        consumer.addVertex(pose, x, y, z)
            .setColor(colour)
            .setUv(0.5F, 0.5F)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, nx, ny, nz);
    }
}
