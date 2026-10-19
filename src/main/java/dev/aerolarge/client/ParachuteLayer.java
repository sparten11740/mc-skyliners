package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.parachute.Parachute;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;

/**
 * Draws a player's parachute: packed, a pack on their back; open, the striped canopy high over their head on cords to
 * their shoulders (see {@link Canopy}), swaying a little. Drawn in the player model's own space (y down, the origin
 * about a block and a half above the feet), so it turns with the player.
 */
public class ParachuteLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    /** Where the cords meet the shoulders. */
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
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(Canopy.TEXTURE));
        if (Parachute.isOpen(player)) {
            pose.pushPose();
            // A slow sway, as if in the wind.
            pose.mulPose(Axis.ZP.rotationDegrees(3 * Mth.sin(ageInTicks * 0.05F)));
            pose.mulPose(Axis.XP.rotationDegrees(2 * Mth.sin(ageInTicks * 0.037F + 1)));
            Canopy.draw(buffer, pose.last(), light, SHOULDER_X, SHOULDER_Y);
            pose.popPose();
        } else {
            pack(buffer, pose.last(), light);
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
            Canopy.vertex(buffer, pose, corners[face[0]], 0.5F, 0.75F, light);
            Canopy.vertex(buffer, pose, corners[face[1]], 1, 0.75F, light);
            Canopy.vertex(buffer, pose, corners[face[2]], 1, 0.95F, light);
            Canopy.vertex(buffer, pose, corners[face[3]], 0.5F, 0.95F, light);
        }
    }
}
