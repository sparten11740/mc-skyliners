package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.block.FlightDisplayBlock;
import dev.aerolarge.block.FlightDisplayBlockEntity;
import dev.aerolarge.client.PanelArt.Pictures;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the primary flight display on the screen face of its block: an attitude indicator (blue sky over brown
 * ground, the horizon tilted against the bank and moved with the pitch, a pitch ladder, the yellow aircraft symbol)
 * with airspeed on the left, altitude on the right, vertical speed under it and the heading at the bottom. The values
 * come from the airliner's pose as the client draws it, so the display moves as smoothly as the airliner does. On an
 * airliner or zeppelin that is not assembled it shows a standby screen. It is only drawn with the camera in front of
 * it.
 */
public class FlightDisplayRenderer implements BlockEntityRenderer<FlightDisplayBlockEntity> {
    /** The screen's half width and half height on the block face, in blocks. */
    static final float HALF_WIDTH = 0.44F;
    static final float HALF_HEIGHT = 0.36F;
    /** The attitude indicator's half size, how far it sits up from the screen's middle, and blocks per degree of pitch. */
    private static final float ADI = 0.2F;
    private static final float ADI_RAISE = 0.04F;
    private static final float PER_DEGREE = 0.009F;
    /** Text size: blocks per font pixel. */
    private static final float TEXT = 1 / 180F;
    /**
     * The standby screen's picture of what to press, for whoever cannot read its words: how far up its middle is from
     * the screen's, and how far out it reaches each way, in blocks.
     */
    private static final float STANDBY_PICTURE_Y = 0.17F;
    private static final float STANDBY_PICTURE = 0.13F;
    /**
     * What to press to fly while the airliner stands as blocks: the picture on the autopilot's on/off key, whose press
     * has the Physics Assembler assemble the airliner and takes off. A zeppelin's display shows the zeppelin's own.
     */
    static final ResourceLocation ASSEMBLE = Pictures.AIRLINER.cruise().texture();
    /** Lifts the drawing just off the block face so it does not flicker against it, and the text off the drawing. */
    private static final float SURFACE = 0.502F;
    private static final float TEXT_LIFT = 0.005F;
    /** The static after a lightning strike, in specks across and up the screen. */
    private static final int NOISE_COLUMNS = 16;
    private static final int NOISE_ROWS = 12;

    private static final int BLACK = 0xFF0A0C10;
    private static final int SKY = 0xFF2D6FD1;
    private static final int GROUND = 0xFF7A4E26;
    private static final int WHITE = 0xFFF2F2F2;
    private static final int YELLOW = 0xFFF2CF30;
    private static final int GREEN = 0xFF46E070;

    /** Only with the camera in front of the screen, which is drawn from the front only, and as far off as ever. */
    @Override
    public boolean shouldRender(FlightDisplayBlockEntity display, Vec3 camera) {
        Direction screen = display.getBlockState().getValue(FlightDisplayBlock.FACING);
        return Sight.facing(display.getBlockPos(), screen, SURFACE, camera, getViewDistance());
    }

    @Override
    public void render(FlightDisplayBlockEntity display, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction screen = display.getBlockState().getValue(FlightDisplayBlock.FACING);
        SableClient.Attitude attitude = SableClient.attitude(display, screen.getOpposite(), partialTick);

        pose.pushPose();
        // Onto the screen face, x to the viewer's right and y up.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-screen.toYRot()));
        pose.translate(0, 0, SURFACE);
        long staticSeed = display.getLevel() == null ? 0 : display.getBlockPos().asLong() + display.getLevel().getGameTime();
        draw(pose, buffers, attitude, staticSeed, standby(display));
        pose.popPose();
    }

    /** The standby screen's picture of what to press: the one on the nearby autopilot's on/off key, if there is one. */
    private static ResourceLocation standby(FlightDisplayBlockEntity display) {
        Level level = display.getLevel();
        AutopilotBlockEntity autopilot = level != null ? AutopilotBlockEntity.nearest(level, display.getBlockPos()) : null;
        return autopilot != null ? Pictures.of(autopilot.craft()).cruise().texture() : ASSEMBLE;
    }

    /**
     * Draws the display round the pose's origin, {@link #HALF_WIDTH} and {@link #HALF_HEIGHT} out each way and facing
     * out along its z: the standby screen without an attitude to show, the autopilot's start picture given over its
     * words, and static over it all just after lightning struck, its specks picked by the seed.
     */
    static void draw(PoseStack pose, MultiBufferSource buffers, @Nullable SableClient.Attitude attitude, long staticSeed, ResourceLocation standby) {
        Matrix4f matrix = pose.last().pose();
        VertexConsumer fill = ScreenDrawing.shapes(buffers);
        ScreenDrawing.rect(fill, matrix, -HALF_WIDTH, -HALF_HEIGHT, HALF_WIDTH, HALF_HEIGHT, 0, BLACK);

        Font font = Minecraft.getInstance().font;
        if (attitude == null) {
            ScreenDrawing.picture(buffers, matrix, standby, 0, STANDBY_PICTURE_Y, STANDBY_PICTURE, TEXT_LIFT, 0xFFFFFFFF);
            text(font, pose, buffers, "STBY", 0, 0, GREEN);
            text(font, pose, buffers, "ASSEMBLE TO FLY", 0, -0.12F, WHITE);
            return;
        }
        attitudeIndicator(fill, matrix, attitude);

        text(font, pose, buffers, String.valueOf(Math.round(attitude.airspeed())), -0.34F, ADI_RAISE, WHITE);
        text(font, pose, buffers, "SPD", -0.34F, ADI_RAISE + 0.08F, GREEN);
        text(font, pose, buffers, String.valueOf(Math.round(attitude.altitude())), 0.34F, ADI_RAISE, WHITE);
        text(font, pose, buffers, "ALT", 0.34F, ADI_RAISE + 0.08F, GREEN);
        text(font, pose, buffers, String.format(Locale.ROOT, "%+d", Math.round(attitude.verticalSpeed())), 0.34F, ADI_RAISE - 0.09F, GREEN);
        text(font, pose, buffers, String.format(Locale.ROOT, "HDG %03d", Math.round(attitude.heading()) % 360), 0, -0.29F, WHITE);
        if (LightningFlash.screensStatic()) {
            // A buffer of its own: drawing the text has moved the buffers on from the one the screen was drawn into.
            noise(ScreenDrawing.shapes(buffers), matrix, staticSeed);
        }
    }

    /**
     * Static over the whole screen, just after lightning struck: a grid of grey specks of every brightness, different
     * each tick.
     */
    private static void noise(VertexConsumer fill, Matrix4f matrix, long seed) {
        RandomSource random = RandomSource.create(seed);
        float width = 2 * HALF_WIDTH / NOISE_COLUMNS;
        float height = 2 * HALF_HEIGHT / NOISE_ROWS;
        for (int column = 0; column < NOISE_COLUMNS; column++) {
            for (int row = 0; row < NOISE_ROWS; row++) {
                int grey = random.nextInt(256);
                float x = -HALF_WIDTH + column * width;
                float y = -HALF_HEIGHT + row * height;
                ScreenDrawing.rect(fill, matrix, x, y, x + width, y + height, 2 * TEXT_LIFT, 0xFF000000 | grey << 16 | grey << 8 | grey);
            }
        }
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

        ScreenDrawing.rect(fill, matrix, -ADI, ADI_RAISE - ADI, ADI, ADI_RAISE + ADI, 0.001F, SKY);
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
        ScreenDrawing.rect(fill, matrix, -0.13F, ADI_RAISE - 0.006F, -0.05F, ADI_RAISE + 0.006F, 0.004F, YELLOW);
        ScreenDrawing.rect(fill, matrix, 0.05F, ADI_RAISE - 0.006F, 0.13F, ADI_RAISE + 0.006F, 0.004F, YELLOW);
        ScreenDrawing.rect(fill, matrix, -0.01F, ADI_RAISE - 0.01F, 0.01F, ADI_RAISE + 0.01F, 0.004F, YELLOW);
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
            ScreenDrawing.vertex(fill, matrix, a[0], a[1], z, colour);
            ScreenDrawing.vertex(fill, matrix, b[0], b[1], z, colour);
            ScreenDrawing.vertex(fill, matrix, c[0], c[1], z, colour);
            ScreenDrawing.vertex(fill, matrix, d[0], d[1], z, colour);
        }
    }

    /** Text centred on (x, y), in the screen's units. */
    private static void text(Font font, PoseStack pose, MultiBufferSource buffers, String text, float x, float y, int colour) {
        ScreenDrawing.text(font, pose, buffers, Component.literal(text), x, y, TEXT_LIFT, TEXT, colour, ScreenDrawing.Align.CENTRE);
    }
}
