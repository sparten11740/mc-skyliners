package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
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
        Label.of(font, text, x, y, size, colour, align).draw(font, pose, buffers, z);
    }

    /**
     * Text laid out once, to be drawn again and again as it is: put in the order it reads in and measured, which is
     * most of the work of drawing it. Its left edge is in font pixels from the x it is placed at.
     */
    record Label(FormattedCharSequence text, float left, double x, double y, float size, int colour) {
        /** Text placed against x as aligned and centred on y, at a size in blocks per font pixel. */
        static Label of(Font font, Component text, double x, double y, float size, int colour, Align align) {
            FormattedCharSequence laid = text.getVisualOrderText();
            float left = switch (align) {
                case LEFT -> 0;
                case CENTRE -> -font.width(laid) / 2F;
                case RIGHT -> -font.width(laid);
            };
            return new Label(laid, left, x, y, size, colour);
        }

        void draw(Font font, PoseStack pose, MultiBufferSource buffers, float z) {
            draw(font, pose, buffers, z, colour >>> 24);
        }

        /** Drawn as opaque as the alpha says (255 whole), whatever its colour's own. */
        void draw(Font font, PoseStack pose, MultiBufferSource buffers, float z, int alpha) {
            pose.pushPose();
            pose.translate(x, y, z);
            pose.scale(size, -size, size);
            font.drawInBatch(text, left, -font.lineHeight / 2F, alpha << 24 | colour & 0xFFFFFF, false, pose.last().pose(), buffers,
                Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
    }
}
