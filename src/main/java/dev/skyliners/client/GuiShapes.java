package dev.skyliners.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * Flat shapes over the view that the GUI's own fills cannot draw, at any angle and in fractions of a pixel: quads,
 * triangles, lines and arcs, put into the GUI's shared buffer in the order they are drawn, as its fills are, and drawn
 * with the next of them. The GUI culls a shape whose corners go round the other way from its own, so each quad's are
 * taken round its way whichever way they were given. Shared by the pilots' head-up display ({@link PilotHud}) and the
 * gunner's sight ({@link GunnerSight}).
 */
final class GuiShapes {
    private GuiShapes() {
    }

    /** A convex quad, in the units the matrix takes to the screen. */
    static void quad(GuiGraphics graphics, Matrix4f matrix, float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy,
        int colour) {
        if (colour >>> 24 == 0) {
            return;
        }
        float turning = ax * by - bx * ay + bx * cy - cx * by + cx * dy - dx * cy + dx * ay - ax * dy;
        VertexConsumer shapes = graphics.bufferSource().getBuffer(RenderType.gui());
        if (turning > 0) {
            shapes.addVertex(matrix, dx, dy, 0).setColor(colour);
            shapes.addVertex(matrix, cx, cy, 0).setColor(colour);
            shapes.addVertex(matrix, bx, by, 0).setColor(colour);
            shapes.addVertex(matrix, ax, ay, 0).setColor(colour);
        } else {
            shapes.addVertex(matrix, ax, ay, 0).setColor(colour);
            shapes.addVertex(matrix, bx, by, 0).setColor(colour);
            shapes.addVertex(matrix, cx, cy, 0).setColor(colour);
            shapes.addVertex(matrix, dx, dy, 0).setColor(colour);
        }
    }

    static void triangle(GuiGraphics graphics, Matrix4f matrix, float ax, float ay, float bx, float by, float cx, float cy, int colour) {
        quad(graphics, matrix, ax, ay, bx, by, cx, cy, cx, cy, colour);
    }

    /** A straight line from one point to another, this wide, as a thin quad. */
    static void line(GuiGraphics graphics, Matrix4f matrix, float ax, float ay, float bx, float by, float width, int colour) {
        float length = (float) Math.hypot(bx - ax, by - ay);
        if (length < 1e-4F) {
            return;
        }
        float nx = -(by - ay) / length * width / 2;
        float ny = (bx - ax) / length * width / 2;
        quad(graphics, matrix, ax - nx, ay - ny, bx - nx, by - ny, bx + nx, by + ny, ax + nx, ay + ny, colour);
    }

    /**
     * An arc round a middle, this wide, from one angle to another in radians, clockwise from straight up on the
     * screen, as a run of so many short lines; a whole turn draws a ring.
     */
    static void arc(GuiGraphics graphics, Matrix4f matrix, float x, float y, float radius, double from, double to, int pieces, float width,
        int colour) {
        for (int piece = 0; piece < pieces; piece++) {
            double a = from + (to - from) * piece / pieces;
            double b = from + (to - from) * (piece + 1) / pieces;
            line(graphics, matrix, x + radius * (float) Math.sin(a), y - radius * (float) Math.cos(a), x + radius * (float) Math.sin(b),
                y - radius * (float) Math.cos(b), width, colour);
        }
    }

    /** A filled disc round a middle, as a fan of so many pieces. */
    static void disc(GuiGraphics graphics, Matrix4f matrix, float x, float y, float radius, int pieces, int colour) {
        for (int piece = 0; piece < pieces; piece++) {
            double a = 2 * Math.PI * piece / pieces;
            double b = 2 * Math.PI * (piece + 1) / pieces;
            triangle(graphics, matrix, x, y, x + radius * (float) Math.sin(a), y - radius * (float) Math.cos(a), x + radius * (float) Math.sin(b),
                y - radius * (float) Math.cos(b), colour);
        }
    }
}
