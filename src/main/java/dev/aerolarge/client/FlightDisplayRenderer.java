package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.block.FlightDisplayBlock;
import dev.aerolarge.block.FlightDisplayBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

/**
 * Draws the primary flight display on the screen face of its block: an attitude indicator (blue sky over brown
 * ground, the horizon tilted against the bank and moved with the pitch, a pitch ladder, the yellow aircraft symbol)
 * with airspeed on the left, altitude on the right, vertical speed under it and the heading at the bottom. The values
 * come from the airliner's pose as the client draws it, so the display moves as smoothly as the airliner does. On an
 * airliner that is not assembled it shows a standby screen.
 */
public class FlightDisplayRenderer implements BlockEntityRenderer<FlightDisplayBlockEntity> {
    /** The screen's half width and half height on the block face, in blocks. */
    private static final float HALF_WIDTH = 0.44F;
    private static final float HALF_HEIGHT = 0.36F;
    /** The attitude indicator's half size, how far it sits up from the screen's middle, and blocks per degree of pitch. */
    private static final float ADI = 0.2F;
    private static final float ADI_RAISE = 0.04F;
    private static final float PER_DEGREE = 0.009F;
    /** Text size: blocks per font pixel. */
    private static final float TEXT = 1 / 180F;
    /** Lifts the drawing just off the block face so it does not flicker against it. */
    private static final float SURFACE = 0.502F;

    private static final int BLACK = 0xFF0A0C10;
    private static final int SKY = 0xFF2D6FD1;
    private static final int GROUND = 0xFF7A4E26;
    private static final int WHITE = 0xFFF2F2F2;
    private static final int YELLOW = 0xFFF2CF30;
    private static final int GREEN = 0xFF46E070;

    @Override
    public void render(FlightDisplayBlockEntity display, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction screen = display.getBlockState().getValue(FlightDisplayBlock.FACING);
        SableClient.Attitude attitude = SableClient.attitude(display, screen.getOpposite(), partialTick);

        pose.pushPose();
        // Onto the screen face, x to the viewer's right and y up.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-screen.toYRot()));
        pose.translate(0, 0, SURFACE);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer fill = buffers.getBuffer(RenderType.textBackground());
        quad(fill, matrix, -HALF_WIDTH, -HALF_HEIGHT, HALF_WIDTH, HALF_HEIGHT, 0, BLACK);

        Font font = Minecraft.getInstance().font;
        if (attitude == null) {
            text(font, pose, buffers, "STBY", 0, 0, GREEN, true);
            text(font, pose, buffers, "ASSEMBLE TO FLY", 0, -0.12F, WHITE, true);
            pose.popPose();
            return;
        }
        attitudeIndicator(fill, matrix, attitude);

        text(font, pose, buffers, String.valueOf(Math.round(attitude.airspeed())), -0.34F, ADI_RAISE, WHITE, true);
        text(font, pose, buffers, "SPD", -0.34F, ADI_RAISE + 0.08F, GREEN, true);
        text(font, pose, buffers, String.valueOf(Math.round(attitude.altitude())), 0.34F, ADI_RAISE, WHITE, true);
        text(font, pose, buffers, "ALT", 0.34F, ADI_RAISE + 0.08F, GREEN, true);
        text(font, pose, buffers, String.format(Locale.ROOT, "%+d", Math.round(attitude.verticalSpeed())), 0.34F, ADI_RAISE - 0.09F, GREEN, true);
        text(font, pose, buffers, String.format(Locale.ROOT, "HDG %03d", Math.round(attitude.heading()) % 360), 0, -0.29F, WHITE, true);
        pose.popPose();
    }

    /**
     * Sky and ground split by the horizon: the horizon's up direction turns against the bank, and the horizon moves
     * down as the nose comes up. The ground is the square cut to the part below the horizon.
     */
    private static void attitudeIndicator(VertexConsumer fill, Matrix4f matrix, SableClient.Attitude attitude) {
        double roll = Math.toRadians(attitude.roll());
        float upX = (float) -Math.sin(roll);
        float upY = (float) Math.cos(roll);
        float shift = (float) (-attitude.pitch() * PER_DEGREE);
        float cx = shift * upX;
        float cy = ADI_RAISE + shift * upY;

        quad(fill, matrix, -ADI, ADI_RAISE - ADI, ADI, ADI_RAISE + ADI, 0.001F, SKY);
        List<float[]> ground = clipBelow(new float[][]{
            {-ADI, ADI_RAISE - ADI}, {ADI, ADI_RAISE - ADI}, {ADI, ADI_RAISE + ADI}, {-ADI, ADI_RAISE + ADI}
        }, cx, cy, upX, upY);
        polygon(fill, matrix, ground, 0.002F, GROUND);

        // The horizon line and the pitch ladder every ten degrees, each cut to the indicator.
        for (int degrees = -20; degrees <= 20; degrees += 10) {
            float along = degrees == 0 ? ADI : ADI * 0.35F;
            float offset = (float) (degrees * PER_DEGREE);
            float px = cx + offset * upX;
            float py = cy + offset * upY;
            float thickness = degrees == 0 ? 0.006F : 0.003F;
            List<float[]> rung = clipToBox(new float[][]{
                {px - upY * along - upX * thickness, py + upX * along - upY * thickness},
                {px + upY * along - upX * thickness, py - upX * along - upY * thickness},
                {px + upY * along + upX * thickness, py - upX * along + upY * thickness},
                {px - upY * along + upX * thickness, py + upX * along + upY * thickness}
            });
            polygon(fill, matrix, rung, 0.003F, WHITE);
        }
        // The aircraft symbol: wings either side of a dot in the middle, fixed to the screen.
        quad(fill, matrix, -0.13F, ADI_RAISE - 0.006F, -0.05F, ADI_RAISE + 0.006F, 0.004F, YELLOW);
        quad(fill, matrix, 0.05F, ADI_RAISE - 0.006F, 0.13F, ADI_RAISE + 0.006F, 0.004F, YELLOW);
        quad(fill, matrix, -0.01F, ADI_RAISE - 0.01F, 0.01F, ADI_RAISE + 0.01F, 0.004F, YELLOW);
    }

    /** The part of a convex polygon on the far side of a line from its up direction (Sutherland-Hodgman). */
    private static List<float[]> clipBelow(float[][] polygon, float px, float py, float upX, float upY) {
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < polygon.length; i++) {
            float[] a = polygon[i];
            float[] b = polygon[(i + 1) % polygon.length];
            float da = (a[0] - px) * upX + (a[1] - py) * upY;
            float db = (b[0] - px) * upX + (b[1] - py) * upY;
            if (da <= 0) {
                out.add(a);
            }
            if (da * db < 0) {
                float t = da / (da - db);
                out.add(new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t});
            }
        }
        return out;
    }

    /** A polygon cut to the attitude indicator's square. */
    private static List<float[]> clipToBox(float[][] polygon) {
        float[][] current = polygon;
        float[][] sides = {{1, 0, ADI}, {-1, 0, ADI}, {0, 1, ADI_RAISE + ADI}, {0, -1, ADI - ADI_RAISE}};
        for (float[] side : sides) {
            // Keep where side.x * x + side.y * y <= limit: below a line facing that way.
            float px = side[0] * side[2];
            float py = side[1] * side[2];
            List<float[]> clipped = clipBelow(current, px, py, side[0], side[1]);
            current = clipped.toArray(new float[0][]);
            if (current.length == 0) {
                break;
            }
        }
        return List.of(current);
    }

    /** A convex polygon as a fan of quads (the last corner doubled where a quad needs only three). */
    private static void polygon(VertexConsumer fill, Matrix4f matrix, List<float[]> points, float z, int colour) {
        for (int i = 1; i + 1 < points.size(); i += 2) {
            float[] a = points.getFirst();
            float[] b = points.get(i);
            float[] c = points.get(i + 1);
            float[] d = i + 2 < points.size() ? points.get(i + 2) : c;
            vertex(fill, matrix, a[0], a[1], z, colour);
            vertex(fill, matrix, b[0], b[1], z, colour);
            vertex(fill, matrix, c[0], c[1], z, colour);
            vertex(fill, matrix, d[0], d[1], z, colour);
        }
    }

    private static void quad(VertexConsumer fill, Matrix4f matrix, float x0, float y0, float x1, float y1, float z, int colour) {
        vertex(fill, matrix, x0, y0, z, colour);
        vertex(fill, matrix, x1, y0, z, colour);
        vertex(fill, matrix, x1, y1, z, colour);
        vertex(fill, matrix, x0, y1, z, colour);
    }

    private static void vertex(VertexConsumer fill, Matrix4f matrix, float x, float y, float z, int colour) {
        fill.addVertex(matrix, x, y, z).setColor(colour).setLight(LightTexture.FULL_BRIGHT);
    }

    /** Text centred on (x, y), in the screen's units, lit as the screen is. */
    private static void text(Font font, PoseStack pose, MultiBufferSource buffers, String text, float x, float y, int colour, boolean centred) {
        pose.pushPose();
        pose.translate(x, y, 0.005F);
        pose.scale(TEXT, -TEXT, TEXT);
        float left = centred ? -font.width(text) / 2F : 0;
        font.drawInBatch(text, left, -font.lineHeight / 2F, colour, false, pose.last().pose(), buffers,
            Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
