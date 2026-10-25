package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.block.AutopilotScreenBlockEntity;
import dev.aerolarge.block.AutopilotScreenBlockEntity.Box;
import dev.aerolarge.block.CockpitDisplayBlock;
import dev.aerolarge.block.CockpitDisplayBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import org.joml.Matrix4f;

/**
 * Draws a panel of the flight deck's curved row from the block holding it (see {@link CockpitDisplayBlockEntity}):
 * a slim dark slab standing on the floor, turned toward the seats, and on its face, inside the bezel, what it shows. A
 * pilot's flight display, the same as the one set into the panel in older airliners (see
 * {@link FlightDisplayRenderer}), as large as it fits. The moving map, as the autopilot screen beside the row draws
 * it but larger, with what the autopilot is doing over it and how the airliner flies under it. Or an outside
 * camera's live picture with its buttons, as on the camera display (see {@link TailCameraDisplayRenderer}). The face
 * is only drawn with the camera in front of it; the slab shows from every side.
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
    private static final float LINE = 0.18F;
    /** How far round the holding block the panel can reach, for whether it is in view, in blocks. */
    private static final double REACH = 4;

    private static final int CASING = AutopilotScreenRenderer.CASING;
    private static final int GLASS = AutopilotScreenRenderer.GLASS;
    private static final int EDGE = 0xFF23272E;

    @Override
    public void render(CockpitDisplayBlockEntity display, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        CockpitDisplayBlockEntity.Panel panel = display.panel();
        Level level = display.getLevel();
        if (panel == null || level == null) {
            return;
        }
        float width = panel.width();
        float height = panel.height();
        pose.pushPose();
        // Onto the face's bottom left corner: x along it to the right, y up, z out of it toward whoever looks at it.
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-display.facing().toYRot()));
        pose.translate(panel.leftX(), panel.bottom(), panel.leftZ());
        pose.mulPose(Axis.YP.rotation((float) -Math.atan2(panel.rightZ() - panel.leftZ(), panel.rightX() - panel.leftX())));
        slab(ScreenDrawing.shapes(buffers), pose.last().pose(), width, height);
        if (Sight.eye(pose.last()).z > 0) {
            face(display, level, pose, buffers, width, height, partialTick);
        }
        pose.popPose();
    }

    /** What the panel shows, on its face inside the bezel. */
    private static void face(CockpitDisplayBlockEntity display, Level level, PoseStack pose, MultiBufferSource buffers, float width, float height,
                             float partialTick) {
        switch (display.shows()) {
            case FLIGHT -> flight(display, level, pose, buffers, width, height, attitude(display, partialTick));
            case MAP -> map(display, level, pose, buffers, width, height, attitude(display, partialTick));
            case CAMERA -> camera(display, pose, buffers, width, height);
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
     * The moving map of the autopilot screen beside the row, filling the glass but for a line over it saying what the
     * autopilot is doing and one under it saying how the airliner flies.
     */
    private static void map(CockpitDisplayBlockEntity display, Level level, PoseStack pose, MultiBufferSource buffers, float width, float height,
                            @Nullable SableClient.Attitude attitude) {
        Matrix4f matrix = pose.last().pose();
        VertexConsumer fill = ScreenDrawing.shapes(buffers);
        ScreenDrawing.rect(fill, matrix, BEZEL, BEZEL, width - BEZEL, height - BEZEL, LAYER, GLASS);
        AutopilotScreenBlockEntity screen = display.screen(level);
        Vec3 here = SableClient.toWorld(level, Vec3.atCenterOf(display.getBlockPos()));
        double heading = attitude != null ? attitude.heading() : Math.floorMod((int) display.facing().toYRot() + 180, 360);
        Box map = new Box(BEZEL, BEZEL + LINE, width - BEZEL, height - BEZEL - LINE);
        AutopilotBlockEntity autopilot = screen != null ? screen.autopilot() : null;
        if (screen != null) {
            MovingMap.draw(fill, matrix, map, screen.track(), screen.range(), autopilot, here, heading, 2 * LAYER);
        }
        // Text last: it takes its own buffer, which ends the one the shapes went into.
        Font font = Minecraft.getInstance().font;
        double top = height - BEZEL - LINE / 2;
        double bottom = BEZEL + LINE / 2;
        text(font, pose, buffers, AutopilotScreenRenderer.state(autopilot, attitude), BEZEL + 0.05, top, AutopilotScreenRenderer.GREEN, ScreenDrawing.Align.LEFT);
        text(font, pose, buffers, AutopilotScreenRenderer.targets(autopilot), width - BEZEL - 0.05, top, AutopilotScreenRenderer.TARGET, ScreenDrawing.Align.RIGHT);
        if (attitude != null) {
            text(font, pose, buffers, AutopilotScreenRenderer.now(attitude, here), BEZEL + 0.05, bottom, AutopilotScreenRenderer.WHITE, ScreenDrawing.Align.LEFT);
        }
        if (screen != null) {
            text(font, pose, buffers, Component.translatable("screen.aerolarge.autopilot.range", screen.range()), width - BEZEL - 0.05, bottom,
                AutopilotScreenRenderer.GREY, ScreenDrawing.Align.RIGHT);
        }
    }

    /** An outside camera's live picture or test card, and its buttons, over the whole face. */
    private static void camera(CockpitDisplayBlockEntity display, PoseStack pose, MultiBufferSource buffers, float width, float height) {
        pose.pushPose();
        pose.translate(0, 0, LAYER);
        TailCameraDisplayRenderer.drawFace(display, pose, buffers, width, height);
        pose.popPose();
    }

    /** The slab: its face's casing, and its back and edges, each side wound to face out of the slab. */
    private static void slab(VertexConsumer shapes, Matrix4f matrix, float width, float height) {
        ScreenDrawing.rect(shapes, matrix, 0, 0, width, height, 0, CASING);
        box(shapes, matrix, 0, 0, -CockpitDisplayBlock.DEPTH, width, height, 0, EDGE);
    }

    /** The back and the four sides of a box, all but its front (which the face covers), each facing out of it. */
    private static void box(VertexConsumer shapes, Matrix4f matrix, float x0, float y0, float z0, float x1, float y1, float z1, int colour) {
        quad(shapes, matrix, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, colour);
        quad(shapes, matrix, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, colour);
        quad(shapes, matrix, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, colour);
        quad(shapes, matrix, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, colour);
        quad(shapes, matrix, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, colour);
    }

    private static void quad(VertexConsumer shapes, Matrix4f matrix, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy,
                             float cz, float dx, float dy, float dz, int colour) {
        shapes.addVertex(matrix, ax, ay, az).setColor(colour).setLight(LightTexture.FULL_BRIGHT);
        shapes.addVertex(matrix, bx, by, bz).setColor(colour).setLight(LightTexture.FULL_BRIGHT);
        shapes.addVertex(matrix, cx, cy, cz).setColor(colour).setLight(LightTexture.FULL_BRIGHT);
        shapes.addVertex(matrix, dx, dy, dz).setColor(colour).setLight(LightTexture.FULL_BRIGHT);
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
