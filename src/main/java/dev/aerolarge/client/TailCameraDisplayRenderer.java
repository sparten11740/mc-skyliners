package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.airframe.CameraSpot;
import dev.aerolarge.autopilot.Craft;
import dev.aerolarge.block.CameraButtons;
import dev.aerolarge.block.CameraScreen;
import dev.aerolarge.block.TailCameraDisplayBlock;
import dev.aerolarge.block.TailCameraDisplayBlockEntity;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
 * Draws a camera display over the whole of its face, the flight deck's or a zeppelin's in her control car, from the
 * block that holds it, inside a thin dark bezel: upright, on the front of the slim screen; tilted, on the screen plate
 * across the blocks' diagonal, which makes the face taller. On it the live picture from the outside camera it is set to
 * (see {@link TailCameraFeed}), the camera's name in the top corner behind a blinking recording light and a little
 * camera in the camera's colour. With no picture it shows a test card, the way a screen with no signal does: colour
 * bars over a dark band, its airliner or zeppelin seen from behind in the middle, and a word under them on why: it is
 * not assembled, or no picture is coming. Either way, along the bottom left, the buttons that pick among its airframe's
 * cameras (see {@link CameraButtons}): each the airframe seen from its right side, nose to the right, with a dot where
 * its camera is, the one shown lit. The camera panel of the airliner's curved row of screens draws its face the same
 * way (see {@link #drawFace}).
 */
public class TailCameraDisplayRenderer implements BlockEntityRenderer<TailCameraDisplayBlockEntity> {
    /** The bezel round the picture, in blocks. */
    private static final double BEZEL = 0.05;
    /**
     * The picture's width over its height as the fin camera draws it: the tilted face's, as the airliner's own display
     * hangs; an upright face, wider, shows the middle of it, and a large display's, narrower, the middle across.
     */
    static final double ASPECT = pictureAspect(TailCameraDisplayBlock.COLUMNS, WideScreenFace.TILTED_HEIGHT);
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
    /** The camera buttons: the airframe's margin inside each, and the rim round the lit one, in blocks. */
    private static final double BUTTON_PAD = 0.008;
    private static final double BUTTON_RIM = 0.006;
    /**
     * A camera's mark on its button: the radius of the big dot where it is, as a share of the button's height, and of
     * the dark ring round it that sets it off the airframe, as a share of the dot's.
     */
    private static final double CAMERA_DOT = 0.2;
    private static final double DOT_RING = 1.3;
    private static final int DOT_RING_COLOUR = 0xFF0A0C0F;
    /** Pieces the dot and its ring are drawn round in. */
    private static final int DOT_PIECES = 20;
    /** Each airframe's side view, as its cameras' buttons draw it. */
    private static final Map<Craft, Outline> OUTLINES = outlines();
    /** How much of the test card's height the colour bars take, over the dark band. */
    private static final double BARS = 2 / 3.0;
    /**
     * How far out each way the autopilot's start picture in the band reaches while waiting to be assembled, as a share
     * of the band's height, for whoever cannot read the words it follows, and the gap between them, in blocks: after
     * the words, clear of the camera buttons along the bottom left.
     */
    private static final double STANDBY_PICTURE = 0.4;
    private static final double STANDBY_GAP = 0.03;
    /** The badge with the airframe on it: its height, as a share of the bars', and its width over its height. */
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
    private static final int AIRFRAME_DIM = 0xFF8A949E;
    /** The test card's bars, left to right: grey, yellow, cyan, green, magenta, red and blue. */
    private static final int[] COLOUR_BARS = {0xFFC0C0C0, 0xFFC0C000, 0xFF00C0C0, 0xFF00C000, 0xFFC000C0, 0xFFC00000, 0xFF0000C0};

    /**
     * Each airframe seen from behind on the test card's badge, as boxes from its middle in shares of the badge's height
     * (x0, y0, x1, y1). The airliner: the wings, the tailplane, the fuselage, the fin and the four engines under the
     * wings. The zeppelin: her round hull, in steps, and her four fins in a cross through it.
     */
    private static final Map<Craft, double[][]> BADGES = Map.of(
        Craft.AIRLINER, new double[][]{
            {-0.55, -0.20, 0.55, -0.14},
            {-0.26, -0.08, 0.26, -0.04},
            {-0.11, -0.30, 0.11, 0.02},
            {-0.025, 0.02, 0.025, 0.30},
            {-0.44, -0.29, -0.36, -0.20},
            {-0.26, -0.29, -0.18, -0.20},
            {0.18, -0.29, 0.26, -0.20},
            {0.36, -0.29, 0.44, -0.20},
        },
        Craft.ZEPPELIN, new double[][]{
            {-0.24, -0.08, 0.24, 0.08},
            {-0.20, -0.16, 0.20, 0.16},
            {-0.12, -0.22, 0.12, 0.22},
            {-0.42, -0.02, 0.42, 0.02},
            {-0.02, -0.42, 0.02, 0.42},
        });

    @Override
    public void render(TailCameraDisplayBlockEntity screen, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = screen.getBlockState();
        pose.pushPose();
        WideScreenFace.onto(pose, state);
        drawFace(screen, pose, buffers, WideScreenFace.width(state), WideScreenFace.height(state), true);
        pose.popPose();
    }

    /**
     * Draws a camera screen's face, this wide and tall, from the pose's origin at its bottom left corner, facing out
     * along its z: the picture or the test card inside the bezel, and, where they are on the screen, the camera
     * buttons along the bottom left.
     */
    static <T extends BlockEntity & CameraScreen> void drawFace(T screen, PoseStack pose, MultiBufferSource buffers, double width, double height,
                                                                boolean buttons) {
        ResourceLocation picture = TailCameraFeed.show(screen);
        Matrix4f matrix = pose.last().pose();
        ScreenDrawing.rect(ScreenDrawing.shapes(buffers), matrix, 0, 0, width, height, 0, CASING);
        Font font = Minecraft.getInstance().font;
        if (picture != null) {
            picture(buffers.getBuffer(RenderType.text(picture)), matrix, width, height);
            // The picture took its own buffer; the shapes over it take theirs back, and the text its own after them.
            Component label = Component.translatable(KEY + "camera", Component.translatable(screen.camera().label()));
            VertexConsumer shapes = ScreenDrawing.shapes(buffers);
            double labelX = cameraLabel(shapes, matrix, height, font.width(label) * TEXT, screen.camera().colour(), screen.getLevel());
            if (buttons) {
                cameraButtons(shapes, matrix, screen.camera());
            }
            ScreenDrawing.text(font, pose, buffers, label, labelX, height - BEZEL - LABEL_MARGIN - LABEL_HEIGHT / 2, TEXT_LIFT, TEXT, WHITE,
                ScreenDrawing.Align.LEFT);
        } else {
            VertexConsumer shapes = ScreenDrawing.shapes(buffers);
            testCard(shapes, matrix, width, height, screen.camera().craft());
            if (buttons) {
                cameraButtons(shapes, matrix, screen.camera());
            }
            // The picture and the text last: each takes its own buffer, which ends the one the shapes went into.
            boolean standby = SableClient.ride(screen) == null;
            double band = BEZEL + (height - 2 * BEZEL) * (1 - BARS);
            Component words = Component.translatable(KEY + (standby ? "standby" : "no_signal"));
            if (standby) {
                double half = (band - BEZEL) * STANDBY_PICTURE;
                double after = width / 2 + font.width(words) * TEXT / 2 + STANDBY_GAP + half;
                ResourceLocation craft = PanelArt.Pictures.of(screen.camera().craft()).cruise().texture();
                ScreenDrawing.picture(buffers, matrix, craft, after, (BEZEL + band) / 2, half, TEXT_LIFT, WHITE);
            }
            ScreenDrawing.text(font, pose, buffers, words, width / 2, (BEZEL + band) / 2, TEXT_LIFT, TEXT, WHITE, ScreenDrawing.Align.CENTRE);
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
    private static double cameraLabel(VertexConsumer shapes, Matrix4f matrix, double height, double nameWidth, int colour, @Nullable Level level) {
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
        ScreenDrawing.rect(shapes, matrix, camera, middle - CAMERA_BODY_HEIGHT / 2, lens, middle + CAMERA_BODY_HEIGHT / 2, 3 * LAYER, colour);
        // The lens hood, widening away from the body.
        ScreenDrawing.vertex(shapes, matrix, lens, middle - CAMERA_LENS_NARROW, 3 * LAYER, colour);
        ScreenDrawing.vertex(shapes, matrix, lens + CAMERA_LENS_LENGTH, middle - CAMERA_LENS_WIDE, 3 * LAYER, colour);
        ScreenDrawing.vertex(shapes, matrix, lens + CAMERA_LENS_LENGTH, middle + CAMERA_LENS_WIDE, 3 * LAYER, colour);
        ScreenDrawing.vertex(shapes, matrix, lens, middle + CAMERA_LENS_NARROW, 3 * LAYER, colour);
        return name;
    }

    /** The camera buttons along the bottom left, one for each of the shown camera's airframe's cameras, its own lit. */
    private static void cameraButtons(VertexConsumer shapes, Matrix4f matrix, CameraSpot shown) {
        for (CameraSpot camera : shown.alongside()) {
            double left = CameraButtons.left(camera);
            cameraButton(shapes, matrix, camera, camera == shown, left, CameraButtons.INSET, left + CameraButtons.WIDTH,
                CameraButtons.INSET + CameraButtons.HEIGHT, 0);
        }
    }

    /**
     * A camera's button in a box: its airliner or zeppelin seen from the right side on a dark backing, with a big dot
     * in the camera's colour where it is (see {@link #markUp}), kept inside the box; lit, the airframe white and a
     * white rim round it all. Drawn this far off the face, clear of whatever the box is already drawn on.
     */
    static void cameraButton(VertexConsumer shapes, Matrix4f matrix, CameraSpot camera, boolean lit, double x0, double y0, double x1, double y1,
                             float lift) {
        if (lit) {
            ScreenDrawing.rect(shapes, matrix, x0 - BUTTON_RIM, y0 - BUTTON_RIM, x1 + BUTTON_RIM, y1 + BUTTON_RIM, lift + 2 * LAYER, WHITE);
        }
        ScreenDrawing.rect(shapes, matrix, x0, y0, x1, y1, lift + 3 * LAYER, lit ? BUTTON_LIT : LABEL_BACKING);
        Outline outline = OUTLINES.get(camera.craft());
        SideView view = SideView.in(outline, x0, y0, x1, y1);
        int colour = lit ? WHITE : AIRFRAME_DIM;
        for (double[][] quad : outline.quads()) {
            // Back to front: the nose to the right turns the quads over, and they keep facing the viewer this way.
            for (int corner = quad.length - 1; corner >= 0; corner--) {
                ScreenDrawing.vertex(shapes, matrix, view.x(quad[corner][0]), view.y(quad[corner][1]), lift + 4 * LAYER, colour);
            }
        }
        double dot = (y1 - y0) * CAMERA_DOT;
        double ring = dot * DOT_RING;
        double x = Math.max(x0 + ring, Math.min(x1 - ring, view.x(camera.back())));
        double y = Math.max(y0 + ring, Math.min(y1 - ring, view.y(markUp(camera))));
        ScreenDrawing.disc(shapes, matrix, x, y, ring, DOT_PIECES, lift + 5 * LAYER, DOT_RING_COLOUR);
        ScreenDrawing.disc(shapes, matrix, x, y, dot, DOT_PIECES, lift + 6 * LAYER, camera.colour());
    }

    /**
     * How high above the ground a camera's dot stands on its button, in metres: where the camera is, or, for one that
     * circles the ship, as high over the place it circles as it circles, so it shows up over her, out of her hull.
     */
    private static double markUp(CameraSpot camera) {
        return camera.up() + camera.orbitRise();
    }

    /**
     * An airframe's side view (see {@link CameraSpot#sideView}), and how far it and the dots of its cameras reach, the
     * furthest any of them goes each way, in metres: ahead of the nose, back from it, and up.
     */
    private record Outline(List<double[][]> quads, double ahead, double back, double up) {
        static Outline of(Craft craft) {
            List<double[][]> quads = CameraSpot.sideView(craft);
            List<CameraSpot> cameras = CameraSpot.of(craft);
            return new Outline(quads, reach(quads, cameras, point -> -point[0], camera -> -camera.back()),
                reach(quads, cameras, point -> point[0], CameraSpot::back), reach(quads, cameras, point -> point[1], TailCameraDisplayRenderer::markUp));
        }
    }

    private static Map<Craft, Outline> outlines() {
        Map<Craft, Outline> outlines = new EnumMap<>(Craft.class);
        for (Craft craft : Craft.values()) {
            outlines.put(craft, Outline.of(craft));
        }
        return outlines;
    }

    /**
     * An airframe's side view fitted into a button, as large as it fits with every camera on it, in the middle: where
     * the nose's tip and the ground under the wheels come, and blocks of the button per metre of the airframe.
     */
    private record SideView(double noseX, double groundY, double scale) {
        static SideView in(Outline outline, double x0, double y0, double x1, double y1) {
            double length = outline.ahead() + outline.back();
            double scale = Math.min((x1 - x0 - 2 * BUTTON_PAD) / length, (y1 - y0 - 2 * BUTTON_PAD) / outline.up());
            return new SideView((x0 + x1 + length * scale) / 2 - outline.ahead() * scale, (y0 + y1 - outline.up() * scale) / 2, scale);
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

    /** How far a side view's corners and the dots of its cameras reach, the furthest any of them goes one way. */
    private static double reach(List<double[][]> quads, List<CameraSpot> cameras, ToDoubleFunction<double[]> corner,
                                ToDoubleFunction<CameraSpot> camera) {
        double outline = quads.stream().flatMap(Arrays::stream).mapToDouble(corner).max().orElse(0);
        return Math.max(outline, cameras.stream().mapToDouble(camera).max().orElse(0));
    }

    /**
     * The test card over the picture: the colour bars over the dark band, and the airliner or zeppelin on its badge
     * over the bars.
     */
    private static void testCard(VertexConsumer shapes, Matrix4f matrix, double width, double height, Craft craft) {
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
        for (double[] box : BADGES.get(craft)) {
            ScreenDrawing.rect(shapes, matrix, cx + box[0] * size, cy + box[1] * size, cx + box[2] * size, cy + box[3] * size, 3 * LAYER, WHITE);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(TailCameraDisplayBlockEntity screen) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(TailCameraDisplayBlockEntity screen) {
        BlockState state = screen.getBlockState();
        return new AABB(screen.getBlockPos()).inflate(Math.max(TailCameraDisplayBlock.columns(state), TailCameraDisplayBlock.rows(state)));
    }
}
