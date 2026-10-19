package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.parachute.Parachute;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws a player's parachute: packed, a pack on their back; open, a striped canopy high over their head on cords to
 * their shoulders, swaying a little. Drawn in the player model's own space (y down, the origin about a block and a
 * half above the feet), so it turns with the player.
 */
public class ParachuteLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/entity/parachute.png");
    /** The canopy: its top above the model's origin, its radius, how deep it is, and how far round it reaches. */
    private static final float TOP = -2.9F;
    private static final float RADIUS = 2.3F;
    private static final float DEPTH = 1.1F;
    private static final float REACH = 1.25F;
    /** How far down the texture the canopy takes; the pack and the cords take the corner below. */
    private static final float CANOPY_V = 0.7F;
    /** Panels round the canopy and rings down it. */
    private static final int PANELS = 16;
    private static final int RINGS = 4;
    /** The cords' thickness, and where they meet the shoulders. */
    private static final float CORD = 0.02F;
    private static final float SHOULDER_X = 0.3F;
    private static final float SHOULDER_Y = 0.05F;

    public ParachuteLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (Parachute.worn(player).isEmpty()) {
            return;
        }
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        if (Parachute.isOpen(player)) {
            pose.pushPose();
            // A slow sway, as if in the wind.
            pose.mulPose(Axis.ZP.rotationDegrees(3 * Mth.sin(ageInTicks * 0.05F)));
            pose.mulPose(Axis.XP.rotationDegrees(2 * Mth.sin(ageInTicks * 0.037F + 1)));
            canopy(buffer, pose.last(), light);
            pose.popPose();
        } else {
            pack(buffer, pose.last(), light);
        }
    }

    /** The dome: panels round it, alternately coloured by which half of the texture they take. */
    private static void canopy(VertexConsumer buffer, PoseStack.Pose pose, int light) {
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
        // A cord from every other panel's edge down to the nearer shoulder.
        for (int panel = 0; panel < PANELS; panel += 2) {
            float[] rim = dome(REACH, Mth.TWO_PI * panel / PANELS);
            float[] shoulder = {rim[0] > 0 ? SHOULDER_X : -SHOULDER_X, SHOULDER_Y, 0};
            cord(buffer, pose, rim, shoulder, light);
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

    /** The packed parachute: a box on the back, from the shoulders down. */
    private static void pack(VertexConsumer buffer, PoseStack.Pose pose, int light) {
        float x0 = -0.25F;
        float x1 = 0.25F;
        float y0 = 0.05F;
        float y1 = 0.62F;
        float z0 = 0.14F;
        float z1 = 0.36F;
        float[][] corners = {
            {x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0},
            {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}
        };
        int[][] faces = {{0, 1, 2, 3}, {5, 4, 7, 6}, {4, 0, 3, 7}, {1, 5, 6, 2}, {4, 5, 1, 0}, {3, 2, 6, 7}};
        for (int[] face : faces) {
            vertex(buffer, pose, corners[face[0]], 0.5F, 0.75F, light);
            vertex(buffer, pose, corners[face[1]], 1, 0.75F, light);
            vertex(buffer, pose, corners[face[2]], 1, 0.95F, light);
            vertex(buffer, pose, corners[face[3]], 0.5F, 0.95F, light);
        }
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float[] p, float u, float v, int light) {
        buffer.addVertex(pose, p[0], p[1], p[2]).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, -1, 0);
    }
}
