package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.airframe.OutsideCamera;
import dev.aerolarge.block.CameraButtons;
import dev.aerolarge.block.CameraScreen;
import dev.aerolarge.block.TailCameraDisplayBlock;
import dev.aerolarge.block.TailCameraDisplayBlockEntity;
import java.util.Arrays;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Draws the flight deck's camera display over the whole of its face, from the block that holds it, inside a thin dark
 * bezel: upright, on the front of the slim screen; tilted, on the screen plate across the blocks' diagonal, which makes
 * the face taller. On it the live picture from the outside camera it is set to (see {@link TailCameraFeed}), the
 * camera's name in the top corner behind a blinking recording light and a little camera. With no picture it shows a
 * test card, the way a screen with no signal does: colour bars over a dark band, the airliner seen from behind in the
 * middle, and a word under them on why: the airliner is not assembled, or no picture is coming. Either way, along the
 * bottom left, the buttons that pick the camera (see {@link CameraButtons}): each the airliner seen from its right side,
 * nose to the right, with a dot where its camera is, the one shown lit. The camera panel of the curved row of screens
 * draws its face the same way (see {@link #drawFace}).
 */
public class TailCameraDisplayRenderer implements BlockEntityRenderer<TailCameraDisplayBlockEntity> {
    /** The whole face's width, in blocks (see {@link WideScreenFace}). */
    private static final double WIDTH = WideScreenFace.WIDTH;
    /** The bezel round the picture, in blocks. */
    private static final double BEZEL = 0.05;
    /**
     * The picture's width over its height as the fin camera draws it: the tilted face's, as the airliner's own display
     * hangs; an upright face, wider, shows the middle of it.
     */
    static final double ASPECT = pictureAspect(WIDTH, WideScreenFace.TILTED_HEIGHT);
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
    /**
     * The camera buttons: the airliner's margin inside each, the rim round the lit one, and the size of the dot where
     * its camera is, in blocks.
     */
    private static final double BUTTON_PAD = 0.008;
    private static final double BUTTON_RIM = 0.006;
    private static final double CAMERA_DOT = 0.016;
    /** How far the side view and the cameras on it reach, in metres: ahead of the nose, back from it, and up. */
    private static final double VIEW_AHEAD = reach(point -> -point[0], camera -> -camera.back);
    private static final double VIEW_BACK = reach(point -> point[0], camera -> camera.back);
    private static final double VIEW_UP = reach(point -> point[1], camera -> camera.up);
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
    private static final int BUTTON_LIT = 0xE0000000;
    private static final int AIRLINER_DIM = 0xFF8A949E;
    private static final int CAMERA_DIM = 0xFFF2B233;
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
        BlockState state = screen.getBlockState();
        pose.pushPose();
        WideScreenFace.onto(pose, state);
        drawFace(screen, pose, buffers, WIDTH, WideScreenFace.height(state));
        pose.popPose();
    }

    /**
     * Draws a camera screen's face, this wide and tall, from the pose's origin at its bottom left corner, facing out
     * along its z: the picture or the test card inside the bezel, and the camera buttons along the bottom left.
     */
    static <T extends BlockEntity & CameraScreen> void drawFace(T screen, PoseStack pose, MultiBufferSource buffers, double width, double height) {
        ResourceLocation picture = TailCameraFeed.show(screen);
        Matrix4f matrix = pose.last().pose();
        ScreenDrawing.rect(ScreenDrawing.shapes(buffers), matrix, 0, 0, width, height, 0, CASING);
        Font font = Minecraft.getInstance().font;
        if (picture != null) {
            picture(buffers.getBuffer(RenderType.text(picture)), matrix, width, height);
            // The picture took its own buffer; the shapes over it take theirs back, and the text its own after them.
            Component label = Component.translatable(KEY + "camera", Component.translatable(screen.camera().label()));
            VertexConsumer shapes = ScreenDrawing.shapes(buffers);
            double labelX = cameraLabel(shapes, matrix, height, font.width(label) * TEXT, screen.getLevel());
            cameraButtons(shapes, matrix, screen.camera());
            ScreenDrawing.text(font, pose, buffers, label, labelX, height - BEZEL - LABEL_MARGIN - LABEL_HEIGHT / 2, TEXT_LIFT, TEXT, WHITE,
                ScreenDrawing.Align.LEFT);
        } else {
            VertexConsumer shapes = ScreenDrawing.shapes(buffers);
            testCard(shapes, matrix, width, height);
            cameraButtons(shapes, matrix, screen.camera());
            // Text last: it takes its own buffer, which ends the one the shapes went into.
            String status = SableClient.ride(screen) != null ? "no_signal" : "standby";
            double band = BEZEL + (height - 2 * BEZEL) * (1 - BARS);
            ScreenDrawing.text(font, pose, buffers, Component.translatable(KEY + status), width / 2, (BEZEL + band) / 2, TEXT_LIFT, TEXT, WHITE,
                ScreenDrawing.Align.CENTRE);
        }
    }

    /** The picture's width over its height on a face this wide and tall. */
    private static double pictureAspect(double width, double height) {
        return (width - 2 * BEZEL) / (height - 2 * BEZEL);
    }

    /**
     * The picture from the fin over the glass inside the bezel, full bright as a lit screen is: the middle of it, where
     * the face is not the shape the picture is drawn in. The framebuffer it was drawn into starts at its bottom row, so
     * the picture's bottom edge takes the texture's first row.
     */
    private static void picture(VertexConsumer image, Matrix4f matrix, double width, double height) {
        float x0 = (float) BEZEL;
        float x1 = (float) (width - BEZEL);
        float y0 = (float) BEZEL;
        float y1 = (float) (height - BEZEL);
        double aspect = pictureAspect(width, height);
        float u0 = (float) Math.max(0, (1 - aspect / ASPECT) / 2);
        float v0 = (float) Math.max(0, (1 - ASPECT / aspect) / 2);
        image.addVertex(matrix, x0, y0, LAYER).setColor(-1).setUv(u0, v0).setLight(LightTexture.FULL_BRIGHT);
        image.addVertex(matrix, x1, y0, LAYER).setColor(-1).setUv(1 - u0, v0).setLight(LightTexture.FULL_BRIGHT);
        image.addVertex(matrix, x1, y1, LAYER).setColor(-1).setUv(1 - u0, 1 - v0).setLight(LightTexture.FULL_BRIGHT);
        image.addVertex(matrix, x0, y1, LAYER).setColor(-1).setUv(u0, 1 - v0).setLight(LightTexture.FULL_BRIGHT);
    }

    /**
     * The camera's label in the picture's top corner, the way a security camera's picture has one: a blinking red
     * recording light and a camera on a dark backing, wide enough for the name after them. Where the name starts.
     */
    private static double cameraLabel(VertexConsumer shapes, Matrix4f matrix, double height, double nameWidth, @Nullable Level level) {
        double top = height - BEZEL - LABEL_MARGIN;
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

    /** The camera buttons along the bottom left, the shown camera's lit. */
    private static void cameraButtons(VertexConsumer shapes, Matrix4f matrix, OutsideCamera shown) {
        for (OutsideCamera camera : OutsideCamera.values()) {
            cameraButton(shapes, matrix, camera, camera == shown);
        }
    }

    /**
     * A camera's button: the airliner seen from its right side on a dark backing, with a dot where the camera is; lit,
     * the airliner white, the dot red as a recording light and a white rim round it all.
     */
    private static void cameraButton(VertexConsumer shapes, Matrix4f matrix, OutsideCamera camera, boolean lit) {
        double x0 = CameraButtons.left(camera);
        double y0 = CameraButtons.INSET;
        double x1 = x0 + CameraButtons.WIDTH;
        double y1 = y0 + CameraButtons.HEIGHT;
        if (lit) {
            ScreenDrawing.rect(shapes, matrix, x0 - BUTTON_RIM, y0 - BUTTON_RIM, x1 + BUTTON_RIM, y1 + BUTTON_RIM, 2 * LAYER, WHITE);
        }
        ScreenDrawing.rect(shapes, matrix, x0, y0, x1, y1, 3 * LAYER, lit ? BUTTON_LIT : LABEL_BACKING);
        SideView view = SideView.in(x0, y0, x1, y1);
        int colour = lit ? WHITE : AIRLINER_DIM;
        for (double[][] quad : OutsideCamera.SIDE_VIEW) {
            // Back to front: the nose to the right turns the quads over, and they keep facing the viewer this way.
            for (int corner = quad.length - 1; corner >= 0; corner--) {
                ScreenDrawing.vertex(shapes, matrix, view.x(quad[corner][0]), view.y(quad[corner][1]), 4 * LAYER, colour);
            }
        }
        double x = view.x(camera.back);
        double y = view.y(camera.up);
        double half = CAMERA_DOT / 2;
        ScreenDrawing.rect(shapes, matrix, x - half, y - half, x + half, y + half, 5 * LAYER, lit ? RECORDING : CAMERA_DIM);
    }

    /**
     * The side view fitted into a button, as large as it fits with every camera on it, in the middle: where the nose's
     * tip and the ground under the wheels come, and blocks of the button per metre of the airliner.
     */
    private record SideView(double noseX, double groundY, double scale) {
        static SideView in(double x0, double y0, double x1, double y1) {
            double length = VIEW_AHEAD + VIEW_BACK;
            double scale = Math.min((x1 - x0 - 2 * BUTTON_PAD) / length, (y1 - y0 - 2 * BUTTON_PAD) / VIEW_UP);
            return new SideView((x0 + x1 + length * scale) / 2 - VIEW_AHEAD * scale, (y0 + y1 - VIEW_UP * scale) / 2, scale);
        }

        /** Where on the button a place this far back from the nose comes, the nose to the right. */
        double x(double back) {
            return noseX - back * scale;
        }

        /** Where on the button a place this high above the ground comes. */
        double y(double up) {
            return groundY + up * scale;
        }
    }

    /** How far the side view's corners and the cameras on it reach, the furthest any of them goes one way. */
    private static double reach(ToDoubleFunction<double[]> corner, ToDoubleFunction<OutsideCamera> camera) {
        double outline = OutsideCamera.SIDE_VIEW.stream().flatMap(Arrays::stream).mapToDouble(corner).max().orElse(0);
        return Math.max(outline, Arrays.stream(OutsideCamera.values()).mapToDouble(camera).max().orElse(0));
    }

    /** The test card over the picture: the colour bars over the dark band, and the airliner on its badge over the bars. */
    private static void testCard(VertexConsumer shapes, Matrix4f matrix, double width, double height) {
        double x0 = BEZEL;
        double x1 = width - BEZEL;
        double y0 = BEZEL;
        double y1 = height - BEZEL;
        double band = y0 + (y1 - y0) * (1 - BARS);
        ScreenDrawing.rect(shapes, matrix, x0, y0, x1, band, LAYER, BAND);
        double bar = (x1 - x0) / COLOUR_BARS.length;
        for (int i = 0; i < COLOUR_BARS.length; i++) {
            ScreenDrawing.rect(shapes, matrix, x0 + i * bar, band, x0 + (i + 1) * bar, y1, LAYER, COLOUR_BARS[i]);
        }
        double size = (y1 - band) * BADGE;
        double cx = width / 2;
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
