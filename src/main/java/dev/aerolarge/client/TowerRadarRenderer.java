package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.block.TowerRadarBlock;
import dev.aerolarge.block.TowerRadarBlockEntity;
import dev.aerolarge.radar.Contact;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the tower's radar scope on its screen: a dark green disc, north up, with range rings and a cross through the
 * tower in the middle, the sweep going round, and every transponder within range as a blip in its own colour, bright
 * as the sweep passes it and fading until it comes round again; an airliner's blip has a short line the way it goes.
 * Its range, in blocks, is written in the screen's bottom right corner.
 * Drawn only with the camera in front of the screen.
 */
public class TowerRadarRenderer implements BlockEntityRenderer<TowerRadarBlockEntity> {
    /** How far in front of the block's middle the screen's face is, in blocks, and the drawing just off it. */
    private static final float SURFACE = 2 / 16F + 0.003F;
    /** The scope's middle, how far up from the block's middle, and its radius, in blocks. */
    private static final float MIDDLE_Y = 1 / 16F;
    private static final float RADIUS = 5.5F / 16;
    /** The screen's glass round the scope: half its width and height, and how far up its middle is. */
    private static final float GLASS_HALF_WIDTH = 7 / 16F;
    private static final float GLASS_HALF_HEIGHT = 5.75F / 16;
    /** How far the scope reaches, in blocks: the rim. */
    private static final double RANGE = 2000;
    private static final int RINGS = 4;
    /** How long the sweep takes to go round, in milliseconds, and how many pieces of the disc it is drawn in. */
    private static final long SWEEP_PERIOD = 4000;
    private static final int PIECES = 48;
    /** How far behind the sweep its fading trail reaches, in pieces. */
    private static final int TRAIL = 10;
    /** A blip's half size, and the length of an airliner's heading line, in blocks; how dim it fades to. */
    private static final float BLIP = 0.018F;
    private static final float HEADING = 0.05F;
    private static final float FADED = 0.35F;
    /** The text's size, in blocks per font pixel. */
    private static final float TEXT = 1 / 300F;

    private static final int GLASS = 0xFF050A06;
    private static final int SCOPE = 0xFF072810;
    private static final int RING = 0xFF1D6A30;
    private static final int SWEEP = 0xFF46E070;
    private static final int TRAIL_COLOUR = 0x2E46E070;

    @Override
    public boolean shouldRender(TowerRadarBlockEntity radar, Vec3 camera) {
        Direction screen = radar.getBlockState().getValue(TowerRadarBlock.FACING);
        return Sight.facing(radar.getBlockPos(), screen, SURFACE, camera, getViewDistance());
    }

    @Override
    public void render(TowerRadarBlockEntity radar, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = radar.getLevel();
        if (level == null) {
            return;
        }
        Direction screen = radar.getBlockState().getValue(TowerRadarBlock.FACING);
        pose.pushPose();
        // Onto the screen's face, x to the viewer's right and y up, from the scope's middle.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-screen.toYRot()));
        pose.translate(0, MIDDLE_Y, SURFACE);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer shapes = ScreenDrawing.shapes(buffers);
        ScreenDrawing.rect(shapes, matrix, -GLASS_HALF_WIDTH, -GLASS_HALF_HEIGHT, GLASS_HALF_WIDTH, GLASS_HALF_HEIGHT, 0, GLASS);
        disc(shapes, matrix, RADIUS, 0.0005F, SCOPE);
        for (int ring = RINGS; ring >= 1; ring--) {
            float radius = RADIUS * ring / RINGS;
            disc(shapes, matrix, radius, 0.001F, RING);
            disc(shapes, matrix, radius - 0.004F, 0.0015F, SCOPE);
        }
        ScreenDrawing.rect(shapes, matrix, -RADIUS, -0.0015, RADIUS, 0.0015, 0.002F, RING);
        ScreenDrawing.rect(shapes, matrix, -0.0015, -RADIUS, 0.0015, RADIUS, 0.002F, RING);
        double sweep = 2 * Math.PI * (Util.getMillis() % SWEEP_PERIOD) / SWEEP_PERIOD;
        trail(shapes, matrix, sweep);
        Vec3 tower = SableClient.toWorld(level, Vec3.atCenterOf(radar.getBlockPos()));
        for (Contact contact : RadarPicture.in(level)) {
            blip(shapes, matrix, contact, tower, sweep);
        }
        double tipX = Math.sin(sweep) * RADIUS;
        double tipY = Math.cos(sweep) * RADIUS;
        line(shapes, matrix, 0, 0, tipX, tipY, 0.0025F, 0.004F, SWEEP);
        ScreenDrawing.text(Minecraft.getInstance().font, pose, buffers, Component.literal(String.valueOf(Math.round(RANGE))),
            GLASS_HALF_WIDTH - 0.02F, -GLASS_HALF_HEIGHT + 0.03F, 0.005F, TEXT, RING, ScreenDrawing.Align.RIGHT);
        pose.popPose();
    }

    /** A filled disc round the middle, as a fan of pieces. */
    private static void disc(VertexConsumer shapes, Matrix4f matrix, float radius, float z, int colour) {
        for (int i = 0; i < PIECES; i++) {
            double a = 2 * Math.PI * i / PIECES;
            double b = 2 * Math.PI * (i + 1) / PIECES;
            piece(shapes, matrix, a, b, radius, z, colour);
        }
    }

    /** One piece of a disc, from one angle round to another (clockwise from up, as a compass goes). */
    private static void piece(VertexConsumer shapes, Matrix4f matrix, double a, double b, float radius, float z, int colour) {
        ScreenDrawing.vertex(shapes, matrix, 0, 0, z, colour);
        ScreenDrawing.vertex(shapes, matrix, Math.sin(b) * radius, Math.cos(b) * radius, z, colour);
        ScreenDrawing.vertex(shapes, matrix, Math.sin(a) * radius, Math.cos(a) * radius, z, colour);
        ScreenDrawing.vertex(shapes, matrix, 0, 0, z, colour);
    }

    /** The sweep's fading trail, the pieces behind it. */
    private static void trail(VertexConsumer shapes, Matrix4f matrix, double sweep) {
        double step = 2 * Math.PI / PIECES;
        for (int i = 0; i < TRAIL; i++) {
            int alpha = (TRAIL_COLOUR >>> 24) * (TRAIL - i) / TRAIL;
            piece(shapes, matrix, sweep - (i + 1) * step, sweep - i * step, RADIUS, 0.0025F, alpha << 24 | TRAIL_COLOUR & 0xFFFFFF);
        }
    }

    /**
     * A transponder's blip, where it is from the tower, north up, if it is within range: brightest just after the sweep
     * has passed it, fading as the sweep goes round; with a line the way an airliner goes.
     */
    private static void blip(VertexConsumer shapes, Matrix4f matrix, Contact contact, Vec3 tower, double sweep) {
        double dx = contact.x() - tower.x;
        double dz = contact.z() - tower.z;
        double distance = Math.hypot(dx, dz);
        if (distance > RANGE) {
            return;
        }
        double x = dx / RANGE * RADIUS;
        double y = -dz / RANGE * RADIUS;
        double bearing = Math.atan2(dx, -dz);
        double since = Math.floorMod((long) Math.round(Math.toDegrees(sweep - bearing)), 360L) / 360.0;
        int colour = PanelArt.shade(contact.colour().argb(), (float) (1 - (1 - FADED) * since));
        ScreenDrawing.rect(shapes, matrix, x - BLIP, y - BLIP, x + BLIP, y + BLIP, 0.003F, colour);
        if (!contact.airborne() || Float.isNaN(contact.heading())) {
            return;
        }
        double heading = Math.toRadians(contact.heading());
        line(shapes, matrix, x, y, x + Math.sin(heading) * HEADING, y + Math.cos(heading) * HEADING, 0.003F, 0.003F, colour);
    }

    /** A straight line from one point to another, this wide. */
    private static void line(VertexConsumer shapes, Matrix4f matrix, double x0, double y0, double x1, double y1, float z, float width, int colour) {
        double length = Math.max(1e-6, Math.hypot(x1 - x0, y1 - y0));
        double nx = -(y1 - y0) / length * width / 2;
        double ny = (x1 - x0) / length * width / 2;
        ScreenDrawing.vertex(shapes, matrix, x0 + nx, y0 + ny, z, colour);
        ScreenDrawing.vertex(shapes, matrix, x0 - nx, y0 - ny, z, colour);
        ScreenDrawing.vertex(shapes, matrix, x1 - nx, y1 - ny, z, colour);
        ScreenDrawing.vertex(shapes, matrix, x1 + nx, y1 + ny, z, colour);
    }
}
