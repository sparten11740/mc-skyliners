package dev.aerolarge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.airframe.OutsideCamera;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.block.AutopilotScreenBlockEntity.Box;
import dev.aerolarge.block.CameraButtons;
import dev.aerolarge.block.CockpitDisplayBlock;
import dev.aerolarge.block.CockpitDisplayBlockEntity;
import dev.aerolarge.block.CockpitKeys;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws a panel of the flight deck's curved row from the block holding it (see {@link CockpitDisplayBlockEntity}): a
 * slim dark screen standing a block over the floor, turned toward the seats, on a desk whose top slopes down toward
 * them at 45 degrees (see {@link CockpitCasing}), with the touch keys on the slope (see {@link CockpitKeysArt} and
 * {@link AutopilotDeskArt}). On the screen, inside the bezel, what it shows: a pilot's flight display,
 * the same as the one set into the panel in older airliners (see {@link FlightDisplayRenderer}), as large as it fits;
 * the moving map, as the autopilot screen of older airliners draws it, with what the autopilot is doing over it and how
 * the airliner flies under it; the systems page's tiles; or an outside camera's live picture with its buttons, as on
 * the camera display (see {@link TailCameraDisplayRenderer}). The screen and the slope are each only drawn with the
 * camera in front of them; the casing shows from every side. Where the player looks at the screen or the slope from
 * within reach, a little pointer follows the look, and the key under it is outlined in white, so whoever is about to
 * touch it sees what the touch will work.
 */
public class CockpitDisplayRenderer implements BlockEntityRenderer<CockpitDisplayBlockEntity> {
    /** The casing round the glass, in blocks. */
    private static final float BEZEL = 0.06F;
    /** Each layer of the face lifted off the one below. */
    private static final float LAYER = 0.002F;
    private static final float TEXT_LIFT = 0.01F;
    /** Text size on the map panel: blocks per font pixel. */
    private static final float TEXT = 1 / 150F;
    /** The map panel's header and readout lines, how tall each is, in blocks. */
    private static final float LINE = 0.16F;
    /** How far round the holding block the panel can reach, for whether it is in view, in blocks. */
    private static final double REACH = 4;
    /** The pointer: its dot's radius and its dark ring's, and the outline round the key under it, in blocks. */
    private static final double POINTER = 0.022;
    private static final double POINTER_RING = 0.034;
    private static final double OUTLINE = 0.012;
    private static final int POINTER_DARK = 0xFF05080D;
    private static final int POINTER_LIGHT = 0xFFFFFFFF;

    private static final int GLASS = AutopilotScreenRenderer.GLASS;

    /** Where the player's look meets the panel, within reach: on its screen or its slope, in blocks from its corner. */
    record Pointer(boolean onSlope, double across, double up) {
    }

    /** Records the airliner's track for the map as long as the panel is near, seen or not. */
    @Override
    public boolean shouldRender(CockpitDisplayBlockEntity display, Vec3 camera) {
        Level level = display.getLevel();
        if (level != null && display.shows() == CockpitDisplayBlock.Shows.MAP) {
            display.record(SableClient.toWorld(level, Vec3.atCenterOf(display.getBlockPos())), level.getGameTime());
        }
        return BlockEntityRenderer.super.shouldRender(display, camera);
    }

    @Override
    public void render(CockpitDisplayBlockEntity display, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        CockpitDisplayBlockEntity.Panel panel = display.panel();
        Level level = display.getLevel();
        if (panel == null || level == null) {
            return;
        }
        float width = panel.width();
        pose.pushPose();
        // Onto the floor under the screen's bottom left corner: x along it to the right, y up, z out toward the seats.
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-display.facing().toYRot()));
        pose.translate(panel.leftX(), 0, panel.leftZ());
        pose.mulPose(Axis.YP.rotation((float) -Math.atan2(panel.rightZ() - panel.leftZ(), panel.rightX() - panel.leftX())));
        CockpitCasing.draw(buffers, pose.last(), width, panel, light);
        Vector3f eye = Sight.eye(pose.last());
        if (eye.z > 0) {
            pose.pushPose();
            pose.translate(0, panel.bottom(), 0);
            face(display, level, pose, buffers, width, panel.height(), partialTick);
            pose.popPose();
        }
        Pointer pointer = pointer(pose.last(), panel);
        // The slope faces up and out at 45 degrees, through its front edge.
        if (panel.desk() > 0 && eye.y - (panel.bottom() - panel.desk()) + eye.z - panel.desk() > 0) {
            pose.pushPose();
            // Onto the slope's front left corner: x along it, y up the slope toward the screen, z out of it.
            pose.translate(0, panel.bottom() - panel.desk(), panel.desk());
            pose.mulPose(Axis.XP.rotationDegrees(-45));
            if (display.desk() == CockpitDisplayBlockEntity.Desk.AUTOPILOT) {
                AutopilotDeskArt.draw(display, level, buffers, pose, partialTick, pointer);
            } else {
                CockpitKeysArt.slope(display, level, buffers, pose.last().pose(), panel.leftCut(), width - panel.rightCut(), panel.slope());
            }
            pose.popPose();
        }
        if (pointer != null) {
            pointer(display, pose, buffers, panel, pointer);
        }
        pose.popPose();
    }

    /**
     * Where the player's look meets the panel, on its screen or its slope, whichever it meets first, if it does within
     * the player's reach; null otherwise. Worked out in the panel's own frame, the pose's, from the view.
     */
    @Nullable
    private static Pointer pointer(PoseStack.Pose pose, CockpitDisplayBlockEntity.Panel panel) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        Matrix4f toPanel = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.pose()).invertAffine();
        Vector3f eye = toPanel.transformPosition(new Vector3f());
        Vector3f look = toPanel.transformDirection(new Vector3f(0, 0, -1)).normalize();
        double reach = player.blockInteractionRange();
        Pointer best = null;
        double nearest = reach;
        // The screen's face: the plane z = 0, from its bottom edge up.
        double t = look.z < 0 ? -eye.z / look.z : -1;
        if (t > 0 && t < nearest && within(eye.x + look.x * t, eye.y + look.y * t - panel.bottom(), panel.width(), panel.height())) {
            best = new Pointer(false, eye.x + look.x * t, eye.y + look.y * t - panel.bottom());
            nearest = t;
        }
        if (panel.desk() <= 0) {
            return best;
        }
        // The slope: through its front edge, facing up and out at 45 degrees; up it runs back and up.
        double frontY = panel.bottom() - panel.desk();
        double facing = (look.y + look.z) / Math.sqrt(2);
        t = facing < 0 ? -((eye.y - frontY) + (eye.z - panel.desk())) / Math.sqrt(2) / facing : -1;
        double up = ((eye.y + look.y * t - frontY) - (eye.z + look.z * t - panel.desk())) / Math.sqrt(2);
        if (t > 0 && t < nearest && panel.onSlope(eye.x + look.x * t, up)) {
            best = new Pointer(true, eye.x + look.x * t, up);
        }
        return best;
    }

    private static boolean within(double across, double up, double width, double height) {
        return across >= 0 && across <= width && up >= 0 && up <= height;
    }

    /** The pointer where the look meets the panel, over everything, and the outline round the key under it. */
    private static void pointer(CockpitDisplayBlockEntity display, PoseStack pose, MultiBufferSource buffers, CockpitDisplayBlockEntity.Panel panel,
                                Pointer pointer) {
        pose.pushPose();
        if (pointer.onSlope()) {
            pose.translate(0, panel.bottom() - panel.desk(), panel.desk());
            pose.mulPose(Axis.XP.rotationDegrees(-45));
        } else {
            pose.translate(0, panel.bottom(), 0);
        }
        Matrix4f matrix = pose.last().pose();
        VertexConsumer shapes = ScreenDrawing.shapes(buffers);
        Box key = keyAt(display, panel, pointer);
        if (key != null) {
            float z = 12 * LAYER;
            ScreenDrawing.rect(shapes, matrix, key.x0() - OUTLINE, key.y0() - OUTLINE, key.x1() + OUTLINE, key.y0(), z, POINTER_LIGHT);
            ScreenDrawing.rect(shapes, matrix, key.x0() - OUTLINE, key.y1(), key.x1() + OUTLINE, key.y1() + OUTLINE, z, POINTER_LIGHT);
            ScreenDrawing.rect(shapes, matrix, key.x0() - OUTLINE, key.y0(), key.x0(), key.y1(), z, POINTER_LIGHT);
            ScreenDrawing.rect(shapes, matrix, key.x1(), key.y0(), key.x1() + OUTLINE, key.y1(), z, POINTER_LIGHT);
        }
        // Over everything a key shows, the autopilot's numbers and ghosts too (see AutopilotDeskArt).
        ScreenDrawing.disc(shapes, matrix, pointer.across(), pointer.up(), POINTER_RING, 12, 15 * LAYER, POINTER_DARK);
        ScreenDrawing.disc(shapes, matrix, pointer.across(), pointer.up(), POINTER, 12, 16 * LAYER, POINTER_LIGHT);
        pose.popPose();
    }

    /** The key under the pointer: a key on the slope, a systems tile, or a camera's button; null for none. */
    @Nullable
    private static Box keyAt(CockpitDisplayBlockEntity display, CockpitDisplayBlockEntity.Panel panel, Pointer pointer) {
        if (!pointer.onSlope() && display.shows() == CockpitDisplayBlock.Shows.CAMERA && !display.cameraKeysOnSlope()) {
            OutsideCamera camera = CameraButtons.at(pointer.across(), pointer.up());
            double left = camera == null ? 0 : CameraButtons.left(camera);
            return camera == null ? null : new Box(left, CameraButtons.INSET, left + CameraButtons.WIDTH, CameraButtons.INSET + CameraButtons.HEIGHT);
        }
        for (CockpitKeys.Key key : pointer.onSlope() ? display.slopeKeys() : display.screenKeys()) {
            if (key.box().contains(pointer.across(), pointer.up())) {
                return key.box();
            }
        }
        return null;
    }

    /** What the screen shows, on its face inside the bezel, from its bottom left corner. */
    private static void face(CockpitDisplayBlockEntity display, Level level, PoseStack pose, MultiBufferSource buffers, float width, float height,
                             float partialTick) {
        switch (display.shows()) {
            case FLIGHT -> flight(display, level, pose, buffers, width, height, attitude(display, partialTick));
            case MAP -> map(display, level, pose, buffers, width, height, attitude(display, partialTick));
            case CAMERA -> camera(display, pose, buffers, width, height);
            case SYSTEMS -> systems(display, level, pose, buffers, width, height);
        }
    }

    /** How the airliner the panel is aboard flies; null while it stands as blocks. */
    @Nullable
    private static SableClient.Attitude attitude(CockpitDisplayBlockEntity display, float partialTick) {
        return SableClient.attitude(display, display.facing().getOpposite(), partialTick);
    }

    /** The pilot's flight display, as large as fits inside the bezel, in the middle of the glass. */
    private static void flight(CockpitDisplayBlockEntity display, Level level, PoseStack pose, MultiBufferSource buffers, float width, float height,
                               @Nullable SableClient.Attitude attitude) {
        ScreenDrawing.rect(ScreenDrawing.shapes(buffers), pose.last().pose(), BEZEL, BEZEL, width - BEZEL, height - BEZEL, LAYER, GLASS);
        float scale = Math.min((width - 2 * BEZEL) / (2 * FlightDisplayRenderer.HALF_WIDTH), (height - 2 * BEZEL) / (2 * FlightDisplayRenderer.HALF_HEIGHT));
        pose.pushPose();
        pose.translate(width / 2, height / 2, 2 * LAYER);
        pose.scale(scale, scale, scale);
        FlightDisplayRenderer.draw(pose, buffers, attitude, display.getBlockPos().asLong() + level.getGameTime());
        pose.popPose();
    }

    /**
     * The moving map, filling the glass but for a line over it saying what the autopilot is doing and one under it
     * saying how the airliner flies.
     */
    private static void map(CockpitDisplayBlockEntity display, Level level, PoseStack pose, MultiBufferSource buffers, float width, float height,
                            @Nullable SableClient.Attitude attitude) {
        Matrix4f matrix = pose.last().pose();
        VertexConsumer fill = ScreenDrawing.shapes(buffers);
        ScreenDrawing.rect(fill, matrix, BEZEL, BEZEL, width - BEZEL, height - BEZEL, LAYER, GLASS);
        Vec3 here = SableClient.toWorld(level, Vec3.atCenterOf(display.getBlockPos()));
        double heading = attitude != null ? attitude.heading() : Math.floorMod((int) display.facing().toYRot() + 180, 360);
        Box map = new Box(BEZEL, BEZEL + LINE, width - BEZEL, height - BEZEL - LINE);
        AutopilotBlockEntity autopilot = display.autopilot();
        MovingMap.draw(fill, matrix, map, display.track(), display.range(), autopilot, here, heading, 2 * LAYER);
        // Text last: it takes its own buffer, which ends the one the shapes went into.
        Font font = Minecraft.getInstance().font;
        double top = height - BEZEL - LINE / 2;
        double bottom = BEZEL + LINE / 2;
        text(font, pose, buffers, AutopilotScreenRenderer.state(autopilot, attitude), BEZEL + 0.05, top, AutopilotScreenRenderer.GREEN, ScreenDrawing.Align.LEFT);
        text(font, pose, buffers, AutopilotScreenRenderer.targets(autopilot), width - BEZEL - 0.05, top, AutopilotScreenRenderer.TARGET, ScreenDrawing.Align.RIGHT);
        if (attitude != null) {
            text(font, pose, buffers, AutopilotScreenRenderer.now(attitude, here), BEZEL + 0.05, bottom, AutopilotScreenRenderer.WHITE, ScreenDrawing.Align.LEFT);
        }
        text(font, pose, buffers, Component.translatable("screen.aerolarge.autopilot.range", display.range()), width - BEZEL - 0.05, bottom,
            AutopilotScreenRenderer.GREY, ScreenDrawing.Align.RIGHT);
    }

    /** The systems page: the glass, and its tiles over it. */
    private static void systems(CockpitDisplayBlockEntity display, Level level, PoseStack pose, MultiBufferSource buffers, float width, float height) {
        ScreenDrawing.rect(ScreenDrawing.shapes(buffers), pose.last().pose(), BEZEL, BEZEL, width - BEZEL, height - BEZEL, LAYER, GLASS);
        pose.pushPose();
        pose.translate(0, 0, LAYER);
        CockpitKeysArt.tiles(display, level, buffers, pose.last().pose());
        pose.popPose();
    }

    /** An outside camera's live picture or test card, and its buttons, over the whole face. */
    private static void camera(CockpitDisplayBlockEntity display, PoseStack pose, MultiBufferSource buffers, float width, float height) {
        pose.pushPose();
        pose.translate(0, 0, LAYER);
        TailCameraDisplayRenderer.drawFace(display, pose, buffers, width, height, !display.cameraKeysOnSlope());
        pose.popPose();
    }

    /** Text placed against x as aligned, centred on y, in the face's units. */
    private static void text(Font font, PoseStack pose, MultiBufferSource buffers, Component text, double x, double y, int colour, ScreenDrawing.Align align) {
        ScreenDrawing.text(font, pose, buffers, text, x, y, TEXT_LIFT, TEXT, colour, align);
    }

    /**
     * No outline round the block aimed at on a panel: the panel runs through its blocks at a slant, and a box round one
     * of them would stand out of it.
     */
    public static void hideOutline(RenderHighlightEvent.Block event) {
        Level level = Minecraft.getInstance().level;
        if (level != null && level.getBlockState(event.getTarget().getBlockPos()).getBlock() instanceof CockpitDisplayBlock) {
            event.setCanceled(true);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(CockpitDisplayBlockEntity display) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(CockpitDisplayBlockEntity display) {
        return new AABB(display.getBlockPos()).inflate(REACH);
    }
}
