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

    /** A filled disc round a middle, as a fan of this many pieces. */
    static void disc(VertexConsumer shapes, Matrix4f matrix, double x, double y, double radius, int pieces, float z, int colour) {
        for (int i = 0; i < pieces; i++) {
            double a = 2 * Math.PI * i / pieces;
            double b = 2 * Math.PI * (i + 1) / pieces;
            vertex(shapes, matrix, x, y, z, colour);
            vertex(shapes, matrix, x + Math.sin(b) * radius, y + Math.cos(b) * radius, z, colour);
            vertex(shapes, matrix, x + Math.sin(a) * radius, y + Math.cos(a) * radius, z, colour);
            vertex(shapes, matrix, x, y, z, colour);
        }
    }

    /** A straight line from one point to another, this wide. */
    static void line(VertexConsumer shapes, Matrix4f matrix, double x0, double y0, double x1, double y1, float z, float width, int colour) {
        double length = Math.max(1e-6, Math.hypot(x1 - x0, y1 - y0));
        double nx = -(y1 - y0) / length * width / 2;
        double ny = (x1 - x0) / length * width / 2;
        vertex(shapes, matrix, x0 + nx, y0 + ny, z, colour);
        vertex(shapes, matrix, x0 - nx, y0 - ny, z, colour);
        vertex(shapes, matrix, x1 - nx, y1 - ny, z, colour);
        vertex(shapes, matrix, x1 + nx, y1 + ny, z, colour);
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
