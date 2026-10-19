package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.airframe.OutsideCamera;
import dev.aerolarge.block.TailCameraDisplayBlock;
import dev.aerolarge.block.TailCameraDisplayBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Draws the tail camera display over the whole of its face, from the block that holds it, inside a thin dark bezel:
 * the live picture from the camera on top of the fin (see {@link TailCameraFeed}), its name in the top corner behind a
 * blinking recording light and a little camera. With no picture it shows a test card, the way a screen with no signal
 * does: colour bars over a dark band, the airliner seen from behind in the middle, and a word under them on why: the
 * airliner is not assembled, or no picture is coming.
 */
public class TailCameraDisplayRenderer implements BlockEntityRenderer<TailCameraDisplayBlockEntity> {
    /** The face, from the middle of the block toward the way it faces, lifted just off the slim screen. */
    private static final float FACE = -0.5F + TailCameraDisplayBlock.DEPTH / 16F + 0.002F;
    /** The whole face and the bezel round the picture, in blocks. */
    static final double WIDTH = TailCameraDisplayBlock.COLUMNS;
    private static final double HEIGHT = TailCameraDisplayBlock.ROWS;
    private static final double BEZEL = 0.05;
    /** The picture's width over its height, the shape the fin camera's picture is drawn in. */
    static final double ASPECT = (WIDTH - 2 * BEZEL) / (HEIGHT - 2 * BEZEL);
    /**
     * The camera's label in the picture's top left corner, in blocks: its margin from the corner, its height, the
     * padding inside it and the gap between what is in it.
     */
    private static final double LABEL_MARGIN = 0.03;
    private static final double LABEL_HEIGHT = 0.075;
    private static final double LABEL_PAD = 0.015;
    private static final double LABEL_GAP = 0.015;
    /** The recording light's size, and ticks it is lit and dark. */
    private static final double RECORDING_LIGHT = 0.03;
    private static final int BLINK_TICKS = 10;
    /**
     * The camera drawn in the label, side on: its body, and its lens hood widening from half the narrow height to half
     * the wide one, in blocks.
     */
    private static final double CAMERA_BODY_WIDTH = 0.055;
    private static final double CAMERA_BODY_HEIGHT = 0.035;
    private static final double CAMERA_LENS_LENGTH = 0.02;
    private static final double CAMERA_LENS_NARROW = 0.008;
    private static final double CAMERA_LENS_WIDE = 0.016;
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
    private static final int LABEL_BACKING = 0x90000000;
    private static final int RECORDING = 0xFFE03030;
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
        ResourceLocation picture = TailCameraFeed.show(screen);
        pose.pushPose();
        // Onto the face, x to the viewer's right and y up, from the bottom left corner of the whole screen.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-0.5, -0.5, FACE);
        Matrix4f matrix = pose.last().pose();
        ScreenDrawing.rect(ScreenDrawing.shapes(buffers), matrix, 0, 0, WIDTH, HEIGHT, 0, CASING);
        Font font = Minecraft.getInstance().font;
        if (picture != null) {
            picture(buffers.getBuffer(RenderType.text(picture)), matrix);
            // The picture took its own buffer; the shapes over it take theirs back, and the text its own after them.
            Component label = Component.translatable(KEY + "camera", Component.translatable(OutsideCamera.TAIL.label()));
            double labelX = cameraLabel(ScreenDrawing.shapes(buffers), matrix, font.width(label) * TEXT, screen.getLevel());
            ScreenDrawing.text(font, pose, buffers, label, labelX, HEIGHT - BEZEL - LABEL_MARGIN - LABEL_HEIGHT / 2, TEXT_LIFT, TEXT, WHITE,
                ScreenDrawing.Align.LEFT);
        } else {
            testCard(ScreenDrawing.shapes(buffers), matrix);
            // Text last: it takes its own buffer, which ends the one the shapes went into.
            String status = SableClient.ride(screen) != null ? "no_signal" : "standby";
            double band = BEZEL + (HEIGHT - 2 * BEZEL) * (1 - BARS);
            ScreenDrawing.text(font, pose, buffers, Component.translatable(KEY + status), WIDTH / 2, (BEZEL + band) / 2, TEXT_LIFT, TEXT, WHITE,
                ScreenDrawing.Align.CENTRE);
        }
        pose.popPose();
    }

    /**
     * The picture from the fin over the glass inside the bezel, full bright as a lit screen is. The framebuffer it was
     * drawn into starts at its bottom row, so the picture's bottom edge takes the texture's first row.
     */
    private static void picture(VertexConsumer image, Matrix4f matrix) {
        float x0 = (float) BEZEL;
        float x1 = (float) (WIDTH - BEZEL);
        float y0 = (float) BEZEL;
        float y1 = (float) (HEIGHT - BEZEL);
        image.addVertex(matrix, x0, y0, LAYER).setColor(-1).setUv(0, 0).setLight(LightTexture.FULL_BRIGHT);
        image.addVertex(matrix, x1, y0, LAYER).setColor(-1).setUv(1, 0).setLight(LightTexture.FULL_BRIGHT);
        image.addVertex(matrix, x1, y1, LAYER).setColor(-1).setUv(1, 1).setLight(LightTexture.FULL_BRIGHT);
        image.addVertex(matrix, x0, y1, LAYER).setColor(-1).setUv(0, 1).setLight(LightTexture.FULL_BRIGHT);
    }

    /**
     * The camera's label in the picture's top corner, the way a security camera's picture has one: a blinking red
     * recording light and a camera on a dark backing, wide enough for the name after them. Where the name starts.
     */
    private static double cameraLabel(VertexConsumer shapes, Matrix4f matrix, double nameWidth, @Nullable Level level) {
        double top = HEIGHT - BEZEL - LABEL_MARGIN;
        double bottom = top - LABEL_HEIGHT;
        double middle = (top + bottom) / 2;
        double left = BEZEL + LABEL_MARGIN;
        double light = left + LABEL_PAD;
        double camera = light + RECORDING_LIGHT + LABEL_GAP;
        double lens = camera + CAMERA_BODY_WIDTH;
        double name = lens + CAMERA_LENS_LENGTH + LABEL_GAP;
        ScreenDrawing.rect(shapes, matrix, left, bottom, name + nameWidth + LABEL_PAD, top, 2 * LAYER, LABEL_BACKING);
        if (level == null || level.getGameTime() / BLINK_TICKS % 2 == 0) {
            ScreenDrawing.rect(shapes, matrix, light, middle - RECORDING_LIGHT / 2, light + RECORDING_LIGHT, middle + RECORDING_LIGHT / 2, 3 * LAYER, RECORDING);
        }
        ScreenDrawing.rect(shapes, matrix, camera, middle - CAMERA_BODY_HEIGHT / 2, lens, middle + CAMERA_BODY_HEIGHT / 2, 3 * LAYER, WHITE);
        // The lens hood, widening away from the body.
        ScreenDrawing.vertex(shapes, matrix, lens, middle - CAMERA_LENS_NARROW, 3 * LAYER, WHITE);
        ScreenDrawing.vertex(shapes, matrix, lens + CAMERA_LENS_LENGTH, middle - CAMERA_LENS_WIDE, 3 * LAYER, WHITE);
        ScreenDrawing.vertex(shapes, matrix, lens + CAMERA_LENS_LENGTH, middle + CAMERA_LENS_WIDE, 3 * LAYER, WHITE);
        ScreenDrawing.vertex(shapes, matrix, lens, middle + CAMERA_LENS_NARROW, 3 * LAYER, WHITE);
        return name;
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
