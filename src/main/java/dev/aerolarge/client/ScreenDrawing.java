package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/**
 * Drawing on the face of a screen, shared by the renderers of the airliner's screens and boards: flat shapes and
 * text, lit at full brightness as a lit screen is whatever the light around it. Everything is in the face's own
 * units, blocks with x to the viewer's right and y up, each layer lifted off the one below it by its own z. Shapes go
 * into one buffer and text takes its own, which ends the shapes' buffer, so a renderer draws its shapes first.
 */
final class ScreenDrawing {
    /** Where text sits against the x it is drawn at. */
    enum Align {
        LEFT, CENTRE, RIGHT
    }

    private ScreenDrawing() {
    }

    /** The buffer flat shapes are drawn into. */
    static VertexConsumer shapes(MultiBufferSource buffers) {
        return buffers.getBuffer(RenderType.textBackground());
    }

    static void vertex(VertexConsumer shapes, Matrix4f matrix, double x, double y, float z, int colour) {
        shapes.addVertex(matrix, (float) x, (float) y, z).setColor(colour).setLight(LightTexture.FULL_BRIGHT);
    }

    /** A rectangle from one corner to the other. */
    static void rect(VertexConsumer shapes, Matrix4f matrix, double x0, double y0, double x1, double y1, float z, int colour) {
        vertex(shapes, matrix, x0, y0, z, colour);
        vertex(shapes, matrix, x1, y0, z, colour);
        vertex(shapes, matrix, x1, y1, z, colour);
        vertex(shapes, matrix, x0, y1, z, colour);
    }

    /** Text placed against x as aligned and centred on y, at a size in blocks per font pixel. */
    static void text(Font font, PoseStack pose, MultiBufferSource buffers, Component text, double x, double y, float z, float size, int colour, Align align) {
        float left = switch (align) {
            case LEFT -> 0;
            case CENTRE -> -font.width(text) / 2F;
            case RIGHT -> -font.width(text);
        };
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(size, -size, size);
        font.drawInBatch(text, left, -font.lineHeight / 2F, colour, false, pose.last().pose(), buffers,
            Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
