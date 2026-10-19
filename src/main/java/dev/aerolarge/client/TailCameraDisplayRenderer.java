package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.block.TailCameraDisplayBlock;
import dev.aerolarge.block.TailCameraDisplayBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Draws the tail camera display over the whole of its face, from the block that holds it, inside a thin dark bezel.
 * With no picture from the fin it shows a test card, the way a screen with no signal does: colour bars over a dark
 * band, the airliner seen from behind in the middle, and a word under them on why: the airliner is not assembled, or
 * no picture is coming.
 */
public class TailCameraDisplayRenderer implements BlockEntityRenderer<TailCameraDisplayBlockEntity> {
    /** The face, from the middle of the block toward the way it faces, lifted just off the slim screen. */
    private static final float FACE = -0.5F + TailCameraDisplayBlock.DEPTH / 16F + 0.002F;
    /** The whole face and the bezel round the picture, in blocks. */
    private static final double WIDTH = TailCameraDisplayBlock.COLUMNS;
    private static final double HEIGHT = TailCameraDisplayBlock.ROWS;
    private static final double BEZEL = 0.05;
    /** How much of the test card's height the colour bars take, over the dark band. */
    private static final double BARS = 2 / 3.0;
    /** The badge with the airliner on it: its height, as a share of the bars', and its width over its height. */
    private static final double BADGE = 0.75;
    private static final double BADGE_SHAPE = 1.3;
    /** Lifts each layer off the one below it, and the text off them all. */
    private static final float LAYER = 0.001F;
    private static final float TEXT_LIFT = 0.004F;
    /** Text size: blocks per font pixel. */
    private static final float TEXT = 1 / 220F;
    private static final String KEY = "screen.aerolarge.tail_camera_display.";

    private static final int CASING = 0xFF15171B;
    private static final int BAND = 0xFF101418;
    private static final int BADGE_BACKING = 0xF0101820;
    private static final int WHITE = 0xFFF2F6FB;
    /** The test card's bars, left to right: grey, yellow, cyan, green, magenta, red and blue. */
    private static final int[] COLOUR_BARS = {0xFFC0C0C0, 0xFFC0C000, 0xFF00C0C0, 0xFF00C000, 0xFFC000C0, 0xFFC00000, 0xFF0000C0};

    /**
     * The airliner seen from behind, as boxes from its middle in shares of the badge's height (x0, y0, x1, y1): the
     * wings, the tailplane, the fuselage, the fin and the four engines under the wings.
     */
    private static final double[][] AIRLINER = {
        {-0.55, -0.20, 0.55, -0.14},
        {-0.26, -0.08, 0.26, -0.04},
        {-0.11, -0.30, 0.11, 0.02},
        {-0.025, 0.02, 0.025, 0.30},
        {-0.44, -0.29, -0.36, -0.20},
        {-0.26, -0.29, -0.18, -0.20},
        {0.18, -0.29, 0.26, -0.20},
        {0.36, -0.29, 0.44, -0.20},
    };

    @Override
    public void render(TailCameraDisplayBlockEntity screen, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = screen.getBlockState().getValue(TailCameraDisplayBlock.FACING);
        pose.pushPose();
        // Onto the face, x to the viewer's right and y up, from the bottom left corner of the whole screen.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-0.5, -0.5, FACE);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer shapes = ScreenDrawing.shapes(buffers);
        ScreenDrawing.rect(shapes, matrix, 0, 0, WIDTH, HEIGHT, 0, CASING);
        String status = SableClient.ride(screen) != null ? "no_signal" : "standby";
        testCard(shapes, matrix);
        // Text last: it takes its own buffer, which ends the one the shapes went into.
        double band = BEZEL + (HEIGHT - 2 * BEZEL) * (1 - BARS);
        ScreenDrawing.text(Minecraft.getInstance().font, pose, buffers, Component.translatable(KEY + status), WIDTH / 2, (BEZEL + band) / 2, TEXT_LIFT, TEXT,
            WHITE, ScreenDrawing.Align.CENTRE);
        pose.popPose();
    }

    /** The test card over the picture: the colour bars over the dark band, and the airliner on its badge over the bars. */
    private static void testCard(VertexConsumer shapes, Matrix4f matrix) {
        double x0 = BEZEL;
        double x1 = WIDTH - BEZEL;
        double y0 = BEZEL;
        double y1 = HEIGHT - BEZEL;
        double band = y0 + (y1 - y0) * (1 - BARS);
        ScreenDrawing.rect(shapes, matrix, x0, y0, x1, band, LAYER, BAND);
        double bar = (x1 - x0) / COLOUR_BARS.length;
        for (int i = 0; i < COLOUR_BARS.length; i++) {
            ScreenDrawing.rect(shapes, matrix, x0 + i * bar, band, x0 + (i + 1) * bar, y1, LAYER, COLOUR_BARS[i]);
        }
        double size = (y1 - band) * BADGE;
        double cx = WIDTH / 2;
        double cy = (band + y1) / 2;
        ScreenDrawing.rect(shapes, matrix, cx - size * BADGE_SHAPE / 2, cy - size / 2, cx + size * BADGE_SHAPE / 2, cy + size / 2, 2 * LAYER, BADGE_BACKING);
        for (double[] box : AIRLINER) {
            ScreenDrawing.rect(shapes, matrix, cx + box[0] * size, cy + box[1] * size, cx + box[2] * size, cy + box[3] * size, 3 * LAYER, WHITE);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(TailCameraDisplayBlockEntity screen) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(TailCameraDisplayBlockEntity screen) {
        return new AABB(screen.getBlockPos()).inflate(Math.max(TailCameraDisplayBlock.COLUMNS, TailCameraDisplayBlock.ROWS));
    }
}
