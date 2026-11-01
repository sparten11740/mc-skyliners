package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.block.TransponderBlock;
import dev.aerolarge.block.TransponderBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

/**
 * Draws a transponder's picture in its colour on the little screen on top of its module, lit as a screen is whatever
 * the light around it, upright to whoever its face looks at: what it shows on the maps, seen at a glance.
 */
public class TransponderRenderer implements BlockEntityRenderer<TransponderBlockEntity> {
    /** The top of the module, in blocks, and the picture just over it. */
    private static final float TOP = 6 / 16F + 0.002F;
    /** The screen's middle, how far back from the module's middle (away from its face), and the picture's half size. */
    private static final float SCREEN_BACK = 2.5F / 16;
    private static final float HALF = 3.5F / 16;

    @Override
    public void render(TransponderBlockEntity transponder, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!Sight.ahead(pose.last())) {
            return;
        }
        Direction face = transponder.getBlockState().getValue(TransponderBlock.FACING);
        pose.pushPose();
        // Onto the module's top, x to the right of whoever its face looks at and y away from them.
        pose.translate(0.5, TOP, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-face.toYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        Matrix4f matrix = pose.last().pose();
        VertexConsumer picture = buffers.getBuffer(RenderType.text(MarkerArt.texture(transponder.icon())));
        int colour = transponder.colour().argb();
        float y = SCREEN_BACK;
        vertex(picture, matrix, -HALF, y - HALF, colour, 0, 1);
        vertex(picture, matrix, HALF, y - HALF, colour, 1, 1);
        vertex(picture, matrix, HALF, y + HALF, colour, 1, 0);
        vertex(picture, matrix, -HALF, y + HALF, colour, 0, 0);
        pose.popPose();
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, int colour, float u, float v) {
        consumer.addVertex(matrix, x, y, 0).setColor(colour).setUv(u, v).setLight(LightTexture.FULL_BRIGHT);
    }
}
