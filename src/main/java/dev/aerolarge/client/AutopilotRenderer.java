package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.AutopilotBlockEntity.Lateral;
import dev.aerolarge.autopilot.AutopilotBlockEntity.Phase;
import dev.aerolarge.autopilot.Craft;
import dev.aerolarge.block.AutopilotBlock;
import dev.aerolarge.client.PanelArt.Icon;
import dev.aerolarge.client.PanelArt.Pictures;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws the autopilot's control head on the centre console: on top of it, a small display of what the autopilot is
 * set to, in the pictures and colours of its settings window (see {@link AutopilotSettingsScreen}), so a glance down
 * from either seat tells it. Along its top, a lamp, green while the autopilot flies the airliner, amber while it is
 * stuck and dark while it is off; what it is doing, as a picture (take-off, climb, cruise, descent, landing, rolling
 * out, or off); and how it steers (straight on, circling either way, or to a pin). Under them, the height, the speed
 * and the heading it is set to (or the circle's size), each a picture beside its number, greyed out while it is off
 * (and dashes while it has never been set). It reads the right way up looking toward the nose, as a pilot in either
 * seat beside it does, and stays that way round. And, as before the display, the airliner's control surfaces the
 * autopilot moves (see {@link ControlSurfaceRenderer}).
 */
public class AutopilotRenderer implements BlockEntityRenderer<AutopilotBlockEntity> {
    private static final float PIXEL = 1 / 16F;
    /** The display's side, the whole top of the control head, and the glass inside its rim, in blocks. */
    private static final float SIDE = (float) (2 * AutopilotBlock.HEAD_HALF_WIDTH) * PIXEL;
    private static final float RIM = 0.5F * PIXEL;
    /** How far off the display is still drawn, in blocks: from the seats and round the console. */
    private static final double REACH = 12;
    /** Each layer of the display lifted off the one below, and the text over them, in blocks. */
    private static final float LAYER = 0.002F;
    private static final float TEXT_LIFT = 0.006F;
    /** Text size: blocks per font pixel, the figures about two pixels of the head tall. */
    private static final float TEXT = 1 / 56F;
    /**
     * The display's rows, how high up the glass their middles stand: the lamp and pictures along the top, then the
     * height, the speed and the heading; and where the lamp, the pictures and the figures' right ends stand across it,
     * all in blocks.
     */
    private static final float TOP_ROW = 11.25F * PIXEL;
    private static final float[] ROWS = {8 * PIXEL, 5 * PIXEL, 2 * PIXEL};
    private static final float LAMP_X = 2.5F * PIXEL;
    private static final float PHASE_X = 7 * PIXEL;
    private static final float LATERAL_X = 11.25F * PIXEL;
    private static final float FIGURES_RIGHT = 13 * PIXEL;
    /** How big the lamp is, the pictures along the top, and those beside the figures, in blocks. */
    private static final float LAMP_RADIUS = 1.4F * PIXEL;
    private static final float BIG_PICTURE = 4 * PIXEL;
    private static final float SMALL_PICTURE = 2.75F * PIXEL;

    private static final int CASING = AutopilotScreenRenderer.CASING;
    private static final int GLASS = AutopilotScreenRenderer.GLASS;
    private static final int LAMP_OFF = 0xFF2A2F37;
    /** Pictures as they are, or darkened while the autopilot is off. */
    private static final int PICTURE_ON = 0xFFFFFFFF;
    private static final int PICTURE_OFF = 0xFF666666;

    /** A figure the autopilot has never been set to. */
    private static final String UNSET = "---";

    private static final Icon ALTITUDE = Icon.of("autopilot/altitude");
    private static final Icon SPEED = Icon.of("autopilot/speed");
    private static final Icon HEADING = Icon.of("autopilot/heading");

    private final ControlSurfaceRenderer surfaces;

    public AutopilotRenderer(BlockEntityRendererProvider.Context context) {
        this.surfaces = new ControlSurfaceRenderer(context);
    }

    @Override
    public void render(AutopilotBlockEntity autopilot, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        surfaces.render(autopilot, partialTick, pose, buffers, light, overlay);
        pose.pushPose();
        // Onto the middle of the display: x to the right and y toward the nose, z up out of it.
        Direction nose = autopilot.getBlockState().getValue(AutopilotBlock.FACING);
        pose.translate(0.5, AutopilotBlock.HEAD_TOP * PIXEL, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - nose.toYRot()));
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        Vector3f eye = Sight.eye(pose.last());
        if (eye.z > 0 && eye.lengthSquared() < REACH * REACH) {
            // Onto its bottom left corner.
            pose.translate(-SIDE / 2, -SIDE / 2, 0);
            display(autopilot, pose, buffers);
        }
        pose.popPose();
    }

    /** The display: the glass, the lamp, the pictures, and the figures over them. */
    private static void display(AutopilotBlockEntity autopilot, PoseStack pose, MultiBufferSource buffers) {
        Matrix4f matrix = pose.last().pose();
        boolean on = autopilot.phase() != Phase.OFF;
        VertexConsumer shapes = ScreenDrawing.shapes(buffers);
        ScreenDrawing.rect(shapes, matrix, 0, 0, SIDE, SIDE, LAYER, CASING);
        ScreenDrawing.rect(shapes, matrix, RIM, RIM, SIDE - RIM, SIDE - RIM, 2 * LAYER, GLASS);
        int lamp = !on ? LAMP_OFF : autopilot.stuck() ? AutopilotScreenRenderer.AMBER : AutopilotScreenRenderer.GREEN;
        ScreenDrawing.disc(shapes, matrix, LAMP_X, TOP_ROW, LAMP_RADIUS, 12, 3 * LAYER, lamp);
        int tint = on ? PICTURE_ON : PICTURE_OFF;
        // A rocket circles nothing, whatever her mode says.
        boolean circling = autopilot.craft() != Craft.ROCKET && (autopilot.lateral() == Lateral.ORBIT_LEFT || autopilot.lateral() == Lateral.ORBIT_RIGHT);
        Pictures pictures = Pictures.of(autopilot.craft());
        Icon steering = pictures.lateral(shownLateral(autopilot), autopilot.circle());
        // Pictures before text: text takes its own buffer, which ends the one the shapes went into.
        picture(buffers, matrix, pictures.phase(autopilot.phase()), PHASE_X, TOP_ROW, BIG_PICTURE, tint);
        picture(buffers, matrix, steering, LATERAL_X, TOP_ROW, BIG_PICTURE, tint);
        picture(buffers, matrix, ALTITUDE, LAMP_X, ROWS[0], SMALL_PICTURE, tint);
        picture(buffers, matrix, SPEED, LAMP_X, ROWS[1], SMALL_PICTURE, tint);
        picture(buffers, matrix, circling ? steering : HEADING, LAMP_X, ROWS[2], SMALL_PICTURE, tint);
        Font font = Minecraft.getInstance().font;
        boolean set = autopilot.targetSpeed() > 0;
        String heading = circling ? String.valueOf(Math.round(autopilot.orbitRadius()))
            : String.format(Locale.ROOT, "%03d", AutopilotBlockEntity.bearing(autopilot.targetHeading()));
        figure(font, pose, buffers, set ? String.valueOf(Math.round(autopilot.targetAltitude())) : UNSET, ROWS[0], on ? PanelArt.GREEN : PanelArt.GREY);
        figure(font, pose, buffers, set ? String.valueOf(Math.round(autopilot.targetSpeed())) : UNSET, ROWS[1], on ? PanelArt.AMBER : PanelArt.GREY);
        figure(font, pose, buffers, set ? heading : UNSET, ROWS[2], !on ? PanelArt.GREY : circling ? PanelArt.MAGENTA : PanelArt.CYAN);
    }

    /** One of the settings window's pictures, square round a middle, tinted. */
    private static void picture(MultiBufferSource buffers, Matrix4f matrix, Icon icon, float x, float y, float size, int tint) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.text(icon.texture()));
        float half = size / 2;
        float z = 3 * LAYER;
        vertex(consumer, matrix, x - half, y - half, z, 0, 1, tint);
        vertex(consumer, matrix, x + half, y - half, z, 1, 1, tint);
        vertex(consumer, matrix, x + half, y + half, z, 1, 0, tint);
        vertex(consumer, matrix, x - half, y + half, z, 0, 0, tint);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float u, float v, int colour) {
        consumer.addVertex(matrix, x, y, z).setColor(colour).setUv(u, v).setLight(LightTexture.FULL_BRIGHT);
    }

    /** A setting's figure, its right end lined up with the others', on its row. */
    private static void figure(Font font, PoseStack pose, MultiBufferSource buffers, String figure, float y, int colour) {
        ScreenDrawing.text(font, pose, buffers, Component.literal(figure), FIGURES_RIGHT, y, TEXT_LIFT, TEXT, colour, ScreenDrawing.Align.RIGHT);
    }

    @Override
    public boolean shouldRenderOffScreen(AutopilotBlockEntity autopilot) {
        return surfaces.shouldRenderOffScreen(autopilot);
    }

    @Override
    public int getViewDistance() {
        return surfaces.getViewDistance();
    }

    @Override
    public AABB getRenderBoundingBox(AutopilotBlockEntity autopilot) {
        return surfaces.getRenderBoundingBox(autopilot);
    }

    /** The way it flies as its pictures show it: a rocket set to anything but the pin goes straight up and back down. */
    static Lateral shownLateral(AutopilotBlockEntity autopilot) {
        boolean rocketReturning = autopilot.craft() == Craft.ROCKET && autopilot.lateral() != Lateral.DESTINATION;
        return rocketReturning ? Lateral.HEADING : autopilot.lateral();
    }
}
