package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.Aerolarge;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A parachute's open canopy, as worn by a player or carried by a care package: a striped dome high over its load on
 * cords down to it. Drawn with y down (a model's own space), the load's top at the origin.
 */
final class Canopy {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/entity/parachute.png");
    /** The canopy: its top above the origin, its radius, how deep it is, and how far round it reaches. */
    private static final float TOP = -2.9F;
    private static final float RADIUS = 2.3F;
    private static final float DEPTH = 1.1F;
    private static final float REACH = 1.25F;
    /** How far down the texture the canopy takes; the pack and the cords take the corner below. */
    private static final float CANOPY_V = 0.7F;
    /** Panels round the canopy and rings down it. */
    private static final int PANELS = 16;
    private static final int RINGS = 4;
    /** The cords' thickness. */
    private static final float CORD = 0.02F;

    private Canopy() {
    }

    /** The dome, its panels alternately coloured, with a cord from every other panel to the nearer of two anchors. */
    static void draw(VertexConsumer buffer, PoseStack.Pose pose, int light, float anchorX, float anchorY) {
        for (int ring = 0; ring < RINGS; ring++) {
            float a0 = REACH * ring / RINGS;
            float a1 = REACH * (ring + 1) / RINGS;
            for (int panel = 0; panel < PANELS; panel++) {
                float b0 = Mth.TWO_PI * panel / PANELS;
                float b1 = Mth.TWO_PI * (panel + 1) / PANELS;
                float u0 = panel % 2 == 0 ? 0 : 0.5F;
                float u1 = u0 + 0.5F;
                float v0 = CANOPY_V * ring / RINGS;
                float v1 = CANOPY_V * (ring + 1) / RINGS;
                vertex(buffer, pose, dome(a0, b0), u0, v0, light);
                vertex(buffer, pose, dome(a0, b1), u1, v0, light);
                vertex(buffer, pose, dome(a1, b1), u1, v1, light);
                vertex(buffer, pose, dome(a1, b0), u0, v1, light);
            }
        }
        for (int panel = 0; panel < PANELS; panel += 2) {
            float[] rim = dome(REACH, Mth.TWO_PI * panel / PANELS);
            float[] anchor = {rim[0] > 0 ? anchorX : -anchorX, anchorY, 0};
            cord(buffer, pose, rim, anchor, light);
        }
    }

    /** A point on the canopy, a angle down from its top and b round it. */
    private static float[] dome(float a, float b) {
        float out = RADIUS * Mth.sin(a);
        return new float[]{out * Mth.cos(b), TOP + DEPTH * (1 - Mth.cos(a)), out * Mth.sin(b)};
    }

    /** A cord as two thin strips crossed, so it shows from every side. */
    private static void cord(VertexConsumer buffer, PoseStack.Pose pose, float[] from, float[] to, int light) {
        for (float[] side : new float[][]{{CORD, 0}, {0, CORD}}) {
            vertex(buffer, pose, new float[]{from[0] - side[0], from[1], from[2] - side[1]}, 0.5F, 0.95F, light);
            vertex(buffer, pose, new float[]{from[0] + side[0], from[1], from[2] + side[1]}, 0.55F, 0.95F, light);
            vertex(buffer, pose, new float[]{to[0] + side[0], to[1], to[2] + side[1]}, 0.55F, 1, light);
            vertex(buffer, pose, new float[]{to[0] - side[0], to[1], to[2] - side[1]}, 0.5F, 1, light);
        }
    }

    static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float[] p, float u, float v, int light) {
        buffer.addVertex(pose, p[0], p[1], p[2]).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, -1, 0);
    }
}
