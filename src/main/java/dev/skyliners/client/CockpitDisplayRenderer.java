package dev.skyliners.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.block.AutopilotScreenBlockEntity.Box;
import dev.skyliners.block.CameraButtons;
import dev.skyliners.block.CockpitDisplayBlock;
import dev.skyliners.block.CockpitDisplayBlockEntity;
import dev.skyliners.block.CockpitKeys;
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
 * them at 45 degrees (see {@link CockpitCasing}), with the touch keys on the slope (see {@link CockpitKeysArt}, {@link
 * AutopilotDeskArt} and {@link TillerArt}). On the screen, inside the bezel, what it shows: a pilot's flight display,
 * the same as the one set into the panel in older airliners (see {@link FlightDisplayRenderer}), as large as it fits;
 * the moving map, as the autopilot screen of older airliners draws it, with what the autopilot is doing over it and how
 * the airliner flies under it; the systems page's tiles; or an outside camera's live picture with its buttons, as on
 * the camera display (see {@link TailCameraDisplayRenderer}). The screen and the slope are each only drawn with the
 * camera in front of them; the casing shows from every side. Where the player looks at the screen or the slope from
 * within reach, a little pointer follows the look, and the key under it lights up as every touch panel's tiles do (see
 * {@link TouchPanelArt}), so whoever is about to touch it sees what the touch will work. The panel runs through its
 * blocks at a slant, so the block the crosshair rests on cannot say where on it the look falls: the look is carried on
 * to the screen's face and the slope instead, as the server carries it when the panel is touched.
 */
public class CockpitDisplayRenderer implements BlockEntityRenderer<CockpitDisplayBlockEntity> {
    /** The casing round the glass, in blocks. */
    private static final float BEZEL = 0.06F;
    /** Each layer of the face lifted off the one below. */
    private static final float LAYER = 0.002F;
    private static final float TEXT_LIFT = 0.01F;
    /** Text size on the map panel: blocks per font pixel. */
    private static final float TEXT = 1 / 150F;
    /**
     * The map panel's lines of text: how far in from the bezel they start, in blocks, and the least gap between the
     * text against one side and the text against the other, in font pixels; where they would come closer, both are
     * drawn smaller, so a wide circle's radius or a long way out never writes one over the other.
     */
    private static final double TEXT_INSET = 0.05;
    private static final int TEXT_GAP = 8;
    /** The map panel's header and readout lines, how tall each is, in blocks. */
    private static final float LINE = 0.16F;
    /** How far round the holding block the panel can reach, for whether it is in view, in blocks. */
    private static final double REACH = 4;
    /** The pointer: its dot's radius and its dark ring's, in blocks. */
    private static final double POINTER = 0.022;
    private static final double POINTER_RING = 0.034;
    private static final int POINTER_DARK = 0xFF05080D;
    private static final int POINTER_LIGHT = 0xFFFFFFFF;

    private static final int GLASS = AutopilotScreenRenderer.GLASS;

    /** Where the player's look meets the panel, within reach: on its screen or its slope, in blocks from its corner. */
    record Pointer(boolean onSlope, double across, double up) {
        /**
         * The key a pointer rests on among the keys of the slope or the screen, as touching there would work it; null
         * for no pointer, or one on the other.
         */
        @Nullable
        static CockpitKeys.Key key(@Nullable Pointer pointer, boolean onSlope, CockpitKeys.Keys keys) {
            return pointer == null || pointer.onSlope() != onSlope ? null : keys.at(pointer.across(), pointer.up());
        }

        /**
         * The camera button along the bottom of the screen's picture a pointer rests on: its place in the row (see
         * {@link CameraButtons}); -1 for none.
         */
        static int cameraButton(@Nullable Pointer pointer, int cameras) {
            return pointer == null || pointer.onSlope() ? -1 : CameraButtons.layout(cameras).at(pointer.across(), pointer.up());
        }
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
        Pointer pointer = pointer(pose.last(), panel);
        if (eye.z > 0) {
            pose.pushPose();
            pose.translate(0, panel.bottom(), 0);
            face(display, level, pose, buffers, width, panel.height(), partialTick, pointer);
            pose.popPose();
        }
        // The slope faces up and out at 45 degrees, through its front edge.
        if (panel.desk() > 0 && eye.y - (panel.bottom() - panel.desk()) + eye.z - panel.desk() > 0) {
            pose.pushPose();
            // Onto the slope's front left corner: x along it, y up the slope toward the screen, z out of it.
            pose.translate(0, panel.bottom() - panel.desk(), panel.desk());
            pose.mulPose(Axis.XP.rotationDegrees(-45));
            if (display.desk() == CockpitDisplayBlockEntity.Desk.AUTOPILOT) {
                AutopilotDeskArt.draw(display, level, buffers, pose, partialTick, pointer);
            } else if (display.desk() == CockpitDisplayBlockEntity.Desk.TILLER) {
                TillerArt.draw(display, level, buffers, pose.last().pose(), partialTick, pointer);
            } else {
                CockpitKeysArt.slope(display, level, buffers, pose.last().pose(), pointer);
            }
            pose.popPose();
        }
        if (pointer != null) {
            pointer(pose, buffers, panel, pointer);
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

    /** The pointer where the look meets the panel, a white dot ringed in black, over everything. */
    private static void pointer(PoseStack pose, MultiBufferSource buffers, CockpitDisplayBlockEntity.Panel panel, Pointer pointer) {
        pose.pushPose();
        if (pointer.onSlope()) {
            pose.translate(0, panel.bottom() - panel.desk(), panel.desk());
            pose.mulPose(Axis.XP.rotationDegrees(-45));
        } else {
            pose.translate(0, panel.bottom(), 0);
        }
        Matrix4f matrix = pose.last().pose();
        VertexConsumer shapes = ScreenDrawing.shapes(buffers);
        // Over everything a key shows, the autopilot's numbers and ghosts too (see AutopilotDeskArt).
        ScreenDrawing.disc(shapes, matrix, pointer.across(), pointer.up(), POINTER_RING, 12, 15 * LAYER, POINTER_DARK);
        ScreenDrawing.disc(shapes, matrix, pointer.across(), pointer.up(), POINTER, 12, 16 * LAYER, POINTER_LIGHT);
        pose.popPose();
    }

    /**
     * What the screen shows, on its face inside the bezel, from its bottom left corner, the pointer lighting up the key
     * it rests on there.
     */
    private static void face(CockpitDisplayBlockEntity display, Level level, PoseStack pose, MultiBufferSource buffers, float width, float height,
                             float partialTick, @Nullable Pointer pointer) {
        switch (display.shows()) {
            case FLIGHT -> flight(display, level, pose, buffers, width, height, attitude(display, partialTick));
            case MAP -> map(display, level, pose, buffers, width, height, attitude(display, partialTick));
            case CAMERA -> camera(display, pose, buffers, width, height, pointer);
            case SYSTEMS -> systems(display, level, pose, buffers, width, height, pointer);
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
        FlightDisplayRenderer.draw(pose, buffers, attitude, display.getBlockPos().asLong() + level.getGameTime(), FlightDisplayRenderer.ASSEMBLE);
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
        line(font, pose, buffers, width, top, AutopilotScreenRenderer.state(autopilot, attitude), AutopilotScreenRenderer.GREEN,
            AutopilotScreenRenderer.targets(autopilot), AutopilotScreenRenderer.TARGET);
        Component now = attitude != null ? AutopilotScreenRenderer.now(attitude, here) : Component.empty();
        line(font, pose, buffers, width, bottom, now, AutopilotScreenRenderer.WHITE, Component.translatable("screen.skyliners.autopilot.range", display.range()),
            AutopilotScreenRenderer.GREY);
    }

    /**
     * A line of text over or under the map, centred on y: one text against the left of the glass and one against the
     * right, both drawn smaller where at full size they would run into each other.
     */
    private static void line(Font font, PoseStack pose, MultiBufferSource buffers, float width, double y, Component left, int leftColour, Component right,
                             int rightColour) {
        double room = width - 2 * (BEZEL + TEXT_INSET);
        int wanted = font.width(left) + TEXT_GAP + font.width(right);
        float size = (float) Math.min(TEXT, room / wanted);
        ScreenDrawing.text(font, pose, buffers, left, BEZEL + TEXT_INSET, y, TEXT_LIFT, size, leftColour, ScreenDrawing.Align.LEFT);
        ScreenDrawing.text(font, pose, buffers, right, width - BEZEL - TEXT_INSET, y, TEXT_LIFT, size, rightColour, ScreenDrawing.Align.RIGHT);
    }

    /** The systems page: the glass, and its tiles over it, the one under the pointer lit up. */
    private static void systems(CockpitDisplayBlockEntity display, Level level, PoseStack pose, MultiBufferSource buffers, float width, float height,
                                @Nullable Pointer pointer) {
        ScreenDrawing.rect(ScreenDrawing.shapes(buffers), pose.last().pose(), BEZEL, BEZEL, width - BEZEL, height - BEZEL, LAYER, GLASS);
        pose.pushPose();
        pose.translate(0, 0, LAYER);
        CockpitKeysArt.tiles(display, level, buffers, pose.last().pose(), pointer);
        pose.popPose();
    }

    /**
     * An outside camera's live picture or test card over the whole face, and its buttons along the bottom unless they
     * are on the slope, the one under the pointer lit up.
     */
    private static void camera(CockpitDisplayBlockEntity display, PoseStack pose, MultiBufferSource buffers, float width, float height,
                               @Nullable Pointer pointer) {
        pose.pushPose();
        pose.translate(0, 0, LAYER);
        boolean buttons = !display.cameraKeysOnSlope();
        int looked = buttons ? Pointer.cameraButton(pointer, display.camera().alongside().size()) : -1;
        TailCameraDisplayRenderer.drawFace(display, pose, buffers, width, height, buttons, looked);
        pose.popPose();
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
