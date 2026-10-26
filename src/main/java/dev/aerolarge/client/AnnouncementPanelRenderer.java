package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.block.AnnouncementPanelBlock;
import dev.aerolarge.block.AnnouncementPanelBlockEntity;
import dev.aerolarge.block.Announcement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the announcement panel over the whole of its face, the front of its blocks on a wall or their underside under
 * a ceiling, from the block that holds it:
 * a tile in each block, dark glass in a rim of the tile's own colour, its picture big in the middle and its name small
 * under it, the pictures saying it all for those who cannot read yet. A tile shows where its announcement stands (see
 * {@link Announcement.Standing}): ready, it glows in its colour; being made, it lights up in its colour, pulsing; waiting
 * its turn, its rim blinks; hushed while another is made, or resting after it was, it greys out. The face reads from
 * the top: on a wall its top row, under a ceiling the row nearest whoever looks up at it, as they face. It is only
 * drawn with the camera in front of it.
 */
public class AnnouncementPanelRenderer implements BlockEntityRenderer<AnnouncementPanelBlockEntity> {
    /** Where the face is, from the middle of the block the way it looks: the panel's front, the drawing just off it. */
    private static final float FACE = AnnouncementPanelBlock.DEPTH / 16F - 0.5F + 0.002F;
    private static final float LAYER = 0.002F;
    private static final float TEXT_LIFT = 0.01F;
    /** Text size: blocks per font pixel. */
    private static final float TEXT = 1 / 110F;
    /** A tile's margin inside its block, its rim, its picture's size and how far the picture is lifted off its middle. */
    private static final double MARGIN = 0.05;
    private static final double RIM = 0.035;
    private static final double PICTURE = 0.64;
    private static final double PICTURE_UP = 0.07;
    /** How far up from the tile's bottom the name sits. */
    private static final double LABEL_UP = 0.1;
    /** A lit tile's glow: how many rings, how wide each, and how strong the innermost at its brightest. */
    private static final int GLOW_RINGS = 3;
    private static final double GLOW_RING = MARGIN / GLOW_RINGS;
    private static final int GLOW_ALPHA = 150;
    /** How long a lit tile takes to pulse, and a waiting one to blink, in ms. */
    private static final float PULSE_PERIOD = 1200;
    private static final float BLINK_PERIOD = 600;
    /** How much of its colour a lit tile's glass takes on. */
    private static final float LIT_TINT = 0.35F;

    private static final int CASING = AutopilotScreenRenderer.CASING;
    private static final int GLASS = 0xFF0B1017;
    private static final int OFF = 0xFF4A515B;
    private static final int PICTURE_OFF = 0xFF555555;
    private static final int LABEL = AutopilotScreenRenderer.WHITE;
    private static final int LABEL_OFF = 0xFF6E7681;
    /** Each tile's colour, in the order of the announcements. */
    private static final int[] COLOURS = {
        0xFFFFD84A, // welcome: sunny yellow
        0xFF46E070, // take-off: the autopilot's green
        0xFFFF8A3C, // bumpy air: orange
        0xFFFF6FAE, // snacks: pink
        0xFF46C8F0, // window: sky blue
        0xFF3C8CFF, // pool and gym: pool blue
        0xFFA58CFF, // sleep: night violet
        0xFFF2B830, // landing: the autopilot's amber
        0xFFE8382C, // arrival: a red heart
    };

    @Override
    public boolean shouldRender(AnnouncementPanelBlockEntity panel, Vec3 camera) {
        return BlockEntityRenderer.super.shouldRender(panel, camera)
            && Sight.facing(panel.getBlockPos(), AnnouncementPanelBlock.face(panel.getBlockState()), FACE, camera, getViewDistance());
    }

    @Override
    public void render(AnnouncementPanelBlockEntity panel, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = panel.getBlockState();
        Direction facing = state.getValue(AnnouncementPanelBlock.FACING);
        pose.pushPose();
        // Onto the face, x to the viewer's right and y to the top of the face as they see it, from the top left corner
        // of the whole panel: a screen facing them, upright on a wall, laid face down under a ceiling.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.getOpposite().toYRot()));
        if (!state.getValue(AnnouncementPanelBlock.WALL)) {
            pose.mulPose(Axis.XP.rotationDegrees(90));
        }
        pose.translate(-0.5, 0.5, FACE);
        Matrix4f matrix = pose.last().pose();

        VertexConsumer fill = ScreenDrawing.shapes(buffers);
        ScreenDrawing.rect(fill, matrix, 0, -AnnouncementPanelBlock.ROWS, AnnouncementPanelBlock.COLUMNS, 0, 0, CASING);
        Announcement[] announcements = Announcement.values();
        for (Announcement announcement : announcements) {
            tile(fill, matrix, announcement, AnnouncementPanelBlock.part(announcement, state), panel.standing(announcement));
        }
        // The pictures each take their own buffer, ending the shapes', and the names theirs after them.
        for (Announcement announcement : announcements) {
            picture(buffers, matrix, announcement, AnnouncementPanelBlock.part(announcement, state), panel.standing(announcement));
        }
        for (Announcement announcement : announcements) {
            label(pose, buffers, announcement, AnnouncementPanelBlock.part(announcement, state), panel.standing(announcement));
        }
        pose.popPose();
    }

    /** Whether a tile shows its announcement can be asked for, or soon can, rather than greyed out. */
    private static boolean usable(Announcement.Standing standing) {
        return standing != Announcement.Standing.HUSHED && standing != Announcement.Standing.RESTING;
    }

    /** Where the left and top edges of the tile on a part of the panel are on the face, inside its margin. */
    private static double left(int part) {
        return part % AnnouncementPanelBlock.COLUMNS + MARGIN;
    }

    private static double top(int part) {
        return -(part / AnnouncementPanelBlock.COLUMNS) - MARGIN;
    }

    /** A tile's glass in its rim, lit or greyed for where it stands, and the glow round it while it is being made. */
    private static void tile(VertexConsumer fill, Matrix4f matrix, Announcement announcement, int part, Announcement.Standing standing) {
        double x0 = left(part);
        double y1 = top(part);
        double x1 = x0 + 1 - 2 * MARGIN;
        double y0 = y1 - 1 + 2 * MARGIN;
        int colour = COLOURS[announcement.ordinal()];
        boolean onAir = standing == Announcement.Standing.ON_AIR;
        if (onAir) {
            float strength = PanelArt.pulse(PULSE_PERIOD);
            for (int ring = GLOW_RINGS; ring >= 1; ring--) {
                int alpha = Math.round(GLOW_ALPHA * strength / ring);
                double out = ring * GLOW_RING;
                ScreenDrawing.rect(fill, matrix, x0 - out, y0 - out, x1 + out, y1 + out, LAYER * (GLOW_RINGS - ring + 1) / (GLOW_RINGS + 1), alpha << 24 | colour & 0xFFFFFF);
            }
        }
        ScreenDrawing.rect(fill, matrix, x0, y0, x1, y1, LAYER, rim(colour, standing));
        int glass = onAir ? PanelArt.blend(GLASS, colour, LIT_TINT * (0.6F + 0.4F * PanelArt.pulse(PULSE_PERIOD))) : GLASS;
        ScreenDrawing.rect(fill, matrix, x0 + RIM, y0 + RIM, x1 - RIM, y1 - RIM, 2 * LAYER, glass);
    }

    /** A tile's rim: its colour, full while it is made, blinking while it waits, a little dimmer at rest; grey if not usable. */
    private static int rim(int colour, Announcement.Standing standing) {
        return switch (standing) {
            case ON_AIR -> colour;
            case WAITING -> PanelArt.blend(PanelArt.shade(colour, 0.35F), colour, PanelArt.pulse(BLINK_PERIOD));
            case READY -> PanelArt.shade(colour, 0.75F);
            case HUSHED, RESTING -> OFF;
        };
    }

    /** A tile's picture, in its own colours, or greyed out. */
    private static void picture(MultiBufferSource buffers, Matrix4f matrix, Announcement announcement, int part, Announcement.Standing standing) {
        double cx = left(part) + 0.5 - MARGIN;
        double cy = top(part) - 0.5 + MARGIN + PICTURE_UP;
        double half = PICTURE / 2;
        int tint = usable(standing) ? 0xFFFFFFFF : PICTURE_OFF;
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/gui/announcement/" + announcement.key() + ".png");
        VertexConsumer consumer = buffers.getBuffer(RenderType.text(texture));
        float z = 3 * LAYER;
        vertex(consumer, matrix, cx - half, cy - half, z, 0, 1, tint);
        vertex(consumer, matrix, cx + half, cy - half, z, 1, 1, tint);
        vertex(consumer, matrix, cx + half, cy + half, z, 1, 0, tint);
        vertex(consumer, matrix, cx - half, cy + half, z, 0, 0, tint);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, double x, double y, float z, float u, float v, int colour) {
        consumer.addVertex(matrix, (float) x, (float) y, z).setColor(colour).setUv(u, v).setLight(LightTexture.FULL_BRIGHT);
    }

    /** A tile's name, small under its picture: for those who can read it. */
    private static void label(PoseStack pose, MultiBufferSource buffers, Announcement announcement, int part, Announcement.Standing standing) {
        double x = left(part) + 0.5 - MARGIN;
        double y = top(part) - 1 + 2 * MARGIN + LABEL_UP;
        Component name = Component.translatable("screen.aerolarge.announcement_panel." + announcement.key());
        ScreenDrawing.text(Minecraft.getInstance().font, pose, buffers, name, x, y, TEXT_LIFT, TEXT, usable(standing) ? LABEL : LABEL_OFF, ScreenDrawing.Align.CENTRE);
    }

    @Override
    public boolean shouldRenderOffScreen(AnnouncementPanelBlockEntity panel) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(AnnouncementPanelBlockEntity panel) {
        return new AABB(panel.getBlockPos()).inflate(Math.max(AnnouncementPanelBlock.COLUMNS, AnnouncementPanelBlock.ROWS));
    }
}
