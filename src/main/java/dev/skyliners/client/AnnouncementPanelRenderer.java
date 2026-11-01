package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.skyliners.Skyliners;
import dev.skyliners.block.AnnouncementPanelBlock;
import dev.skyliners.block.AnnouncementPanelBlockEntity;
import dev.skyliners.block.Announcement;
import dev.skyliners.block.TouchPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the announcement panel over the whole of its face, the front of its blocks on a wall or their underside under
 * a ceiling, from the block that holds it: a tile in each block, glass tinted in the tile's own colour, its picture big
 * in the middle and its name small under it, the pictures saying it all for those who cannot read yet. Its tiles answer
 * a player as every touch panel's do (see {@link TouchPanelArt}): the one under the crosshair brightens and stands out
 * a hair. A tile shows where its announcement stands (see {@link Announcement.Standing}): ready, its glass glows softly
 * in its colour; being made, it is bordered in white, as what is on now is on every panel, its glass pulsing in
 * its colour; waiting its turn, its glass blinks; hushed while another is made, or resting after it was, it is dimmed,
 * its picture and name greyed. The face reads from the top: on a wall its top row, under a ceiling the row nearest
 * whoever looks up at it, as they face. It is only drawn with the camera in front of it.
 */
public class AnnouncementPanelRenderer implements BlockEntityRenderer<AnnouncementPanelBlockEntity> {
    /** Where the face is, from the middle of the block the way it looks: the panel's front, the drawing just off it. */
    private static final float FACE = AnnouncementPanelBlock.DEPTH / 16F - 0.5F + 0.002F;
    private static final float LAYER = 0.002F;
    private static final float TEXT_LIFT = 0.01F;
    /** Text size: blocks per font pixel. */
    private static final float TEXT = 1 / 110F;
    /** A tile's margin inside its block, its picture's size and how far the picture is lifted off its middle. */
    private static final double MARGIN = 0.05;
    private static final double PICTURE = 0.64;
    private static final double PICTURE_UP = 0.07;
    /** How far up from the tile's bottom the name sits. */
    private static final double LABEL_UP = 0.1;
    /**
     * How much of its colour a tile's glass takes on: ready; being made, at the height of its pulse (from the ready
     * share at its depth); and waiting, at the dark of its blink (to the height of a pulse at its bright).
     */
    private static final float READY_TINT = 0.35F;
    private static final float ON_AIR_TINT = 0.65F;
    private static final float WAITING_TINT = 0.12F;
    /** How long a tile being made takes to pulse, and a waiting one to blink, in ms. */
    private static final float PULSE_PERIOD = 1200;
    private static final float BLINK_PERIOD = 600;
    /** The panel's blocks on its face, by their parts, from its top left corner: each block's tile is its square. */
    private static final TouchPanel.Layout PARTS = TouchPanel.Layout.grid(AnnouncementPanelBlock.COLUMNS * AnnouncementPanelBlock.ROWS,
        AnnouncementPanelBlock.COLUMNS, new TouchPanel.Tile(0, -AnnouncementPanelBlock.ROWS, AnnouncementPanelBlock.COLUMNS, 0), true);

    private static final int CASING = AutopilotScreenRenderer.CASING;
    private static final int GLASS = 0xFF0B1017;
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
        int looked = TouchPanelArt.lookedAt(panel);
        for (Announcement announcement : announcements) {
            Announcement.Standing standing = panel.standing(announcement);
            TouchPanelArt.Look look = new TouchPanelArt.Look(announcement.ordinal() == looked, standing == Announcement.Standing.ON_AIR,
                usable(standing));
            TouchPanelArt.tile(fill, matrix, tile(announcement, state), glass(COLOURS[announcement.ordinal()], standing), look, LAYER);
        }
        // The pictures each take their own buffer, ending the shapes', and the names theirs after them.
        for (Announcement announcement : announcements) {
            picture(buffers, matrix, announcement, tile(announcement, state), panel.standing(announcement));
        }
        for (Announcement announcement : announcements) {
            label(pose, buffers, announcement, tile(announcement, state), panel.standing(announcement));
        }
        pose.popPose();
    }

    /** Whether a tile shows its announcement can be asked for, or soon can, rather than dimmed. */
    private static boolean usable(Announcement.Standing standing) {
        return standing != Announcement.Standing.HUSHED && standing != Announcement.Standing.RESTING;
    }

    /** Where an announcement's tile is on the face: its block's square, inside the margin. */
    private static TouchPanel.Tile tile(Announcement announcement, BlockState state) {
        return PARTS.get(AnnouncementPanelBlock.part(announcement, state)).inset(MARGIN);
    }

    /**
     * A tile's glass, tinted in its colour for where it stands: pulsing while it is made, blinking while it waits,
     * steady otherwise (its look then dims it while it is hushed or resting; see {@link TouchPanelArt#colour}).
     */
    private static int glass(int colour, Announcement.Standing standing) {
        float tint = switch (standing) {
            case ON_AIR -> Mth.lerp(PanelArt.pulse(PULSE_PERIOD), READY_TINT, ON_AIR_TINT);
            case WAITING -> Mth.lerp(PanelArt.pulse(BLINK_PERIOD), WAITING_TINT, ON_AIR_TINT);
            case READY, HUSHED, RESTING -> READY_TINT;
        };
        return PanelArt.blend(GLASS, colour, tint);
    }

    /** A tile's picture, in its own colours, or greyed out. */
    private static void picture(MultiBufferSource buffers, Matrix4f matrix, Announcement announcement, TouchPanel.Tile tile,
                                Announcement.Standing standing) {
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/gui/announcement/" + announcement.key() + ".png");
        ScreenDrawing.picture(buffers, matrix, texture, tile.middleX(), tile.middleY() + PICTURE_UP, PICTURE / 2, 3 * LAYER,
            usable(standing) ? 0xFFFFFFFF : PICTURE_OFF);
    }

    /** A tile's name, small under its picture: for those who can read it. */
    private static void label(PoseStack pose, MultiBufferSource buffers, Announcement announcement, TouchPanel.Tile tile, Announcement.Standing standing) {
        Component name = Component.translatable("screen.skyliners.announcement_panel." + announcement.key());
        ScreenDrawing.text(Minecraft.getInstance().font, pose, buffers, name, tile.middleX(), tile.bottom() + LABEL_UP, TEXT_LIFT, TEXT,
            usable(standing) ? LABEL : LABEL_OFF, ScreenDrawing.Align.CENTRE);
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
