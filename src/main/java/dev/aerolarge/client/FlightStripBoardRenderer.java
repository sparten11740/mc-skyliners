package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.block.FlightStripBoardBlock;
import dev.aerolarge.block.FlightStripBoardBlockEntity;
import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the flight strip board over the whole of its face (see {@link WideScreenFace}), lit as a screen is whatever the
 * light around it: under a band with the columns' names, a strip for every transponder within the radar's range (see
 * {@link FlightStrip}), those flying first, then the nearest: a bar down its left edge and its picture in its colour,
 * its callsign, its height, how far it is from the tower, its heading, and what it is doing, in that phase's colour.
 * When there are more than the board holds it pages through them, a page every few seconds, the page it is on in the
 * band's corner; with none, it says there is no traffic.
 */
public class FlightStripBoardRenderer implements BlockEntityRenderer<FlightStripBoardBlockEntity> {
    /** The bezel round the board, the band of the columns' names along its top and each strip's height, in blocks. */
    private static final double BEZEL = 0.05;
    private static final double BAND = 0.1;
    private static final double ROW = 0.1;
    /** The gap between strips, in blocks. */
    private static final double GAP = 0.012;
    /** Each strip's colour bar, its left and right; the picture's middle and half its size. */
    private static final double BAR_LEFT = BEZEL + 0.03;
    private static final double BAR_RIGHT = BAR_LEFT + 0.025;
    private static final double ICON_X = 0.17;
    private static final double ICON_HALF = 0.035;
    /** Where the columns are: the callsign's left edge, the numbers' right edges and the phase's left edge. */
    private static final double CALLSIGN_X = 0.24;
    private static final double HEIGHT_X = 1.15;
    private static final double DISTANCE_X = 1.65;
    private static final double HEADING_X = 2.05;
    private static final double PHASE_X = 2.25;
    /** How long each page shows, in milliseconds. */
    private static final long PAGE_MILLIS = 5000;
    /** Lifts each layer off the one below it, and the text off them all. */
    private static final float LAYER = 0.001F;
    private static final float TEXT_LIFT = 0.004F;
    /** Text sizes, in blocks per font pixel: the strips' and the band's. */
    private static final float TEXT = 1 / 130F;
    private static final float BAND_TEXT = 1 / 180F;
    private static final String KEY = "screen.aerolarge.flight_strips.";

    private static final int CASING = 0xFF15171B;
    private static final int BAND_COLOUR = 0xFF10254A;
    private static final int STRIP = 0xFF1A2230;
    private static final int STRIP_ALTERNATE = 0xFF141B26;
    private static final int CYAN = 0xFF46C8F0;
    private static final int WHITE = 0xFFF2F2F2;
    private static final int GREY = 0xFF8A93A0;

    @Override
    public void render(FlightStripBoardBlockEntity board, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = board.getLevel();
        if (level == null) {
            return;
        }
        BlockState state = board.getBlockState();
        double height = WideScreenFace.height(state);
        Vec3 tower = SableClient.toWorld(level, Vec3.atCenterOf(board.getBlockPos()));
        List<FlightStrip> strips = FlightStrip.around(level, tower, TowerRadarRenderer.RANGE);
        int rows = Math.max(1, (int) ((height - 2 * BEZEL - BAND) / ROW));
        int pages = Math.max(1, (strips.size() + rows - 1) / rows);
        int page = (int) (Util.getMillis() / PAGE_MILLIS % pages);
        List<FlightStrip> shown = strips.subList(page * rows, Math.min(strips.size(), (page + 1) * rows));
        double top = height - BEZEL - BAND;
        pose.pushPose();
        WideScreenFace.onto(pose, state);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer shapes = ScreenDrawing.shapes(buffers);
        ScreenDrawing.rect(shapes, matrix, 0, 0, WideScreenFace.WIDTH, height, 0, CASING);
        ScreenDrawing.rect(shapes, matrix, BEZEL, top, WideScreenFace.WIDTH - BEZEL, height - BEZEL, LAYER, BAND_COLOUR);
        for (int i = 0; i < shown.size(); i++) {
            stripShape(shapes, matrix, shown.get(i), top - i * ROW, i);
        }
        for (int i = 0; i < shown.size(); i++) {
            picture(buffers, matrix, shown.get(i), top - (i + 0.5) * ROW);
        }
        // Text last: it takes its own buffer, which ends the ones the shapes and the pictures went into.
        Font font = Minecraft.getInstance().font;
        band(font, pose, buffers, height - BEZEL - BAND / 2, page, pages);
        for (int i = 0; i < shown.size(); i++) {
            stripText(font, pose, buffers, shown.get(i), top - (i + 0.5) * ROW);
        }
        if (strips.isEmpty()) {
            ScreenDrawing.text(font, pose, buffers, Component.translatable(KEY + "none"), WideScreenFace.WIDTH / 2, top / 2, TEXT_LIFT, TEXT, GREY,
                ScreenDrawing.Align.CENTRE);
        }
        pose.popPose();
    }

    /** A strip's backing under its top edge, alternately lighter and darker, and its colour bar. */
    private static void stripShape(VertexConsumer shapes, Matrix4f matrix, FlightStrip strip, double stripTop, int row) {
        double bottom = stripTop - ROW + GAP;
        ScreenDrawing.rect(shapes, matrix, BEZEL, bottom, WideScreenFace.WIDTH - BEZEL, stripTop, LAYER, row % 2 == 0 ? STRIP : STRIP_ALTERNATE);
        ScreenDrawing.rect(shapes, matrix, BAR_LEFT, bottom, BAR_RIGHT, stripTop, 2 * LAYER, strip.contact().colour().argb());
    }

    /** A strip's picture in its colour, its middle at this height. */
    private static void picture(MultiBufferSource buffers, Matrix4f matrix, FlightStrip strip, double y) {
        VertexConsumer picture = buffers.getBuffer(RenderType.text(MarkerArt.texture(strip.contact().icon())));
        int colour = strip.contact().colour().argb();
        float x0 = (float) (ICON_X - ICON_HALF);
        float x1 = (float) (ICON_X + ICON_HALF);
        float y0 = (float) (y - ICON_HALF);
        float y1 = (float) (y + ICON_HALF);
        picture.addVertex(matrix, x0, y0, 2 * LAYER).setColor(colour).setUv(0, 1).setLight(LightTexture.FULL_BRIGHT);
        picture.addVertex(matrix, x1, y0, 2 * LAYER).setColor(colour).setUv(1, 1).setLight(LightTexture.FULL_BRIGHT);
        picture.addVertex(matrix, x1, y1, 2 * LAYER).setColor(colour).setUv(1, 0).setLight(LightTexture.FULL_BRIGHT);
        picture.addVertex(matrix, x0, y1, 2 * LAYER).setColor(colour).setUv(0, 0).setLight(LightTexture.FULL_BRIGHT);
    }

    /** The band's words: the columns' names over them, and the page it is on when there is more than one. */
    private static void band(Font font, PoseStack pose, MultiBufferSource buffers, double y, int page, int pages) {
        label(font, pose, buffers, "callsign", CALLSIGN_X, y, ScreenDrawing.Align.LEFT);
        label(font, pose, buffers, "height", HEIGHT_X, y, ScreenDrawing.Align.RIGHT);
        label(font, pose, buffers, "distance", DISTANCE_X, y, ScreenDrawing.Align.RIGHT);
        label(font, pose, buffers, "heading", HEADING_X, y, ScreenDrawing.Align.RIGHT);
        label(font, pose, buffers, "phase", PHASE_X, y, ScreenDrawing.Align.LEFT);
        if (pages > 1) {
            Component count = Component.translatable(KEY + "page", page + 1, pages);
            ScreenDrawing.text(font, pose, buffers, count, WideScreenFace.WIDTH - 2 * BEZEL, y, TEXT_LIFT, BAND_TEXT, WHITE, ScreenDrawing.Align.RIGHT);
        }
    }

    private static void label(Font font, PoseStack pose, MultiBufferSource buffers, String name, double x, double y, ScreenDrawing.Align align) {
        ScreenDrawing.text(font, pose, buffers, Component.translatable(KEY + "column." + name), x, y, TEXT_LIFT, BAND_TEXT, CYAN, align);
    }

    /** A strip's words, their middle at this height: its callsign, height, distance, heading and phase. */
    private static void stripText(Font font, PoseStack pose, MultiBufferSource buffers, FlightStrip strip, double y) {
        float heading = strip.contact().heading();
        String headingText = strip.contact().airborne() && !Float.isNaN(heading) ? String.format(Locale.ROOT, "%03d", Math.round(heading) % 360) : "---";
        ScreenDrawing.text(font, pose, buffers, strip.callsign(), CALLSIGN_X, y, TEXT_LIFT, TEXT, WHITE, ScreenDrawing.Align.LEFT);
        number(font, pose, buffers, String.valueOf(Math.round(strip.contact().y())), HEIGHT_X, y);
        number(font, pose, buffers, String.valueOf(Math.round(strip.distance())), DISTANCE_X, y);
        number(font, pose, buffers, headingText, HEADING_X, y);
        FlightStrip.Phase phase = strip.phase();
        ScreenDrawing.text(font, pose, buffers, phase.title(), PHASE_X, y, TEXT_LIFT, TEXT, phase.colour(), ScreenDrawing.Align.LEFT);
    }

    private static void number(Font font, PoseStack pose, MultiBufferSource buffers, String text, double x, double y) {
        ScreenDrawing.text(font, pose, buffers, Component.literal(text), x, y, TEXT_LIFT, TEXT, WHITE, ScreenDrawing.Align.RIGHT);
    }

    /** The board is drawn over all of its blocks from the first. */
    @Override
    public AABB getRenderBoundingBox(FlightStripBoardBlockEntity board) {
        return new AABB(board.getBlockPos()).inflate(Math.max(FlightStripBoardBlock.COLUMNS, FlightStripBoardBlock.ROWS));
    }
}
