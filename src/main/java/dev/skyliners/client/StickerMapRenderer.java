package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skyliners.Skyliners;
import dev.skyliners.block.WideScreenBlock;
import dev.skyliners.stickers.Sticker;
import dev.skyliners.stickers.StickerMapBlock;
import dev.skyliners.stickers.StickerMapBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws a sticker map (see {@link StickerMapBlock}) over the whole of its face (see {@link WideScreenFace}): the old
 * parchment map in its wooden frame, and on it the stickers in their three rows of six, each one that is up a bright picture
 * glowing as if lit from behind, each one still to get its picture's grey shadow on the map, so a child sees what is
 * missing. A sticker going up pops on, swelling past its size and settling, with a halo and a burst of stars flying out
 * of it; a row the moment it fills shines from left to right, every sticker in it bursting in turn, and keeps a gold
 * ribbon behind it from then on; and the whole board the moment it fills shines row after row, its frame glowing
 * through the rainbow and then edged in gold for good. The moments come from when each sticker went up, as the server
 * named it (see {@link StickerMapBlockEntity#upSince}).
 */
public class StickerMapRenderer implements BlockEntityRenderer<StickerMapBlockEntity> {
    private static final ResourceLocation BOARD = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/gui/sticker_map/board.png");
    private static final ResourceLocation SHEET = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/gui/sticker_map/stickers.png");
    /** How many cells the sticker sheet has each way: the pictures in its top three rows, their shadows under them. */
    private static final int SHEET_CELLS = 6;
    /**
     * Where the stickers sit on the face, in its units (two wide, one tall): the margin in from its left and right
     * edges and from its top and bottom, inside the frame, and how far out each way from its middle a sticker reaches.
     */
    private static final double SIDE_MARGIN = 0.1;
    private static final double END_MARGIN = 0.09;
    private static final double HALF = 0.12;
    /** How far in from the face's edge the gold edging of a full board runs, and how wide it is. */
    private static final double EDGING_IN = 0.06;
    private static final float EDGING = 0.018F;
    /** How long a sticker going up takes to pop on, in ticks, and how far past its size it swells as it does. */
    private static final double POP_TICKS = 10;
    private static final double OVERSHOOT = 1.7;
    /** How long a sticker's burst of stars flies, in ticks; how many stars; how far they fly out past its edge. */
    private static final double BURST_TICKS = 30;
    private static final int STARS = 8;
    private static final double STAR_FLIGHT = 0.14;
    /** How big a star is at its start, out each way from its middle, and how much it turns as it flies, in turns. */
    private static final double STAR_SIZE = 0.025;
    private static final double STAR_TURN = 0.5;
    /**
     * How long the halo round a sticker going up glows, in ticks, and how far past the sticker it reaches; its warm white
     * and how opaque it is at its brightest, out of 255.
     */
    private static final double HALO_TICKS = 40;
    private static final double HALO = 0.05;
    private static final int HALO_COLOUR = 0xFFF6B0;
    private static final int HALO_ALPHA = 0x90;
    /** How opaque a shine over a sticker is at its brightest, and anything fully opaque, out of 255. */
    private static final int SHINE_ALPHA = 0xA0;
    private static final int OPAQUE = 0xFF;
    /**
     * A star of a burst: how much of its size it has lost by the end of its flight, and how thick each of its arms is at
     * its waist, as a share of its length.
     */
    private static final double STAR_SHRINK = 0.6;
    private static final double STAR_WAIST = 0.28;
    /**
     * A row filling: how many ticks apart its stickers shine in turn, from the left, and how long each shines; the
     * whole board filling shines its rows this far apart, from the top, and glows its frame through the rainbow so
     * long, in ticks.
     */
    private static final double SHINE_APART = 5;
    private static final double SHINE_TICKS = 12;
    private static final double ROW_APART = 20;
    private static final double RAINBOW_TICKS = 140;
    /** How long the frame's glow takes to go once through the rainbow, in ticks. */
    private static final double RAINBOW_CYCLE = 40;
    /**
     * The least block light the map and the stickers' shadows are drawn in, as if a little lamp over it lit it, so it
     * is seen in a dim room.
     */
    private static final int LEAST_LIGHT = 9;
    /** Lifts each layer off the one below it. */
    private static final float LAYER = 0.001F;
    /**
     * How far off a board is drawn, in blocks, and how far from its block any of it can reach. Sable draws an assembled
     * ship's block entities every frame unless told otherwise, and past this its stickers cannot be made out.
     */
    private static final int VIEW_DISTANCE = 48;
    private static final float BOARD_REACH = 5;
    /** How far back of its block's middle the camera must be to see nothing of the board: at the back of the block. */
    private static final double BEHIND_FACE = -0.5;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GOLD = 0xFFF2C440;
    /**
     * The gold ribbon behind a full row, half see-through so the map shows through it: how far in from the face's
     * edges it runs, in its units, and how tall it is beside a sticker.
     */
    private static final int RIBBON = 0xB0F2C440;
    private static final double RIBBON_IN = 0.06;
    private static final double RIBBON_HEIGHT = 0.7;
    /** The stars of a burst, in turn. */
    private static final int[] STAR_COLOURS = {0xFFFFF6B0, 0xFFFFFFFF, 0xFF9CF0FF, 0xFFFFB0E0};

    /** Drawn from the front only: from behind, its frame is all there is to see. */
    @Override
    public boolean shouldRender(StickerMapBlockEntity board, Vec3 camera) {
        Direction facing = board.getBlockState().getValue(WideScreenBlock.FACING);
        return Sight.facing(board.getBlockPos(), facing, BEHIND_FACE, camera, getViewDistance());
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public boolean shouldRenderOffScreen(StickerMapBlockEntity board) {
        return true;
    }

    /** All of it, from the part holding it. */
    @Override
    public AABB getRenderBoundingBox(StickerMapBlockEntity board) {
        return new AABB(board.getBlockPos()).inflate(StickerMapBlock.BOARD_COLUMNS);
    }

    @Override
    public void render(StickerMapBlockEntity board, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!Sight.ahead(pose.last(), BOARD_REACH)) {
            return;
        }
        pose.pushPose();
        WideScreenFace.onto(pose, board.getBlockState());
        Matrix4f matrix = pose.last().pose();
        double width = WideScreenFace.width(board.getBlockState());
        double height = WideScreenFace.height(board.getBlockState());
        double now = DrawingClock.gameTime(partialTick);
        int lit = LightTexture.pack(Math.max(LightTexture.block(light), LEAST_LIGHT), LightTexture.sky(light));
        quad(buffers.getBuffer(RenderType.text(BOARD)), matrix, 0, 0, width, height, 0, 0, 1, 1, 0, WHITE, lit);
        VertexConsumer stickers = buffers.getBuffer(RenderType.text(SHEET));
        for (Sticker sticker : Sticker.values()) {
            sticker(stickers, matrix, board, sticker, now, lit);
        }
        VertexConsumer shapes = ScreenDrawing.shapes(buffers);
        Moments moments = Moments.of(board);
        for (int row = 0; row < Sticker.ROWS; row++) {
            ribbon(shapes, matrix, width, height, row, moments.rowFull()[row] <= now);
        }
        edging(shapes, matrix, width, height, moments.boardFull(), now);
        for (Sticker sticker : Sticker.values()) {
            celebrate(shapes, matrix, board, sticker, moments, now);
        }
        pose.popPose();
    }

    /** Where a sticker's middle is across the face, in its units, and up it. */
    private static double middleX(double width, Sticker sticker) {
        return SIDE_MARGIN + (sticker.column() + 0.5) * (width - 2 * SIDE_MARGIN) / Sticker.ROW;
    }

    private static double middleY(double height, int row) {
        return height - END_MARGIN - (row + 0.5) * (height - 2 * END_MARGIN) / Sticker.ROWS;
    }

    /**
     * A sticker: up, its picture lit, popping on while it goes up; not up yet, or popping on later, its shadow, lit as
     * the board is.
     */
    private static void sticker(VertexConsumer stickers, Matrix4f matrix, StickerMapBlockEntity board, Sticker sticker, double now, int light) {
        double age = now - board.upSince(sticker);
        double x = middleX(WideScreenFace.width(board.getBlockState()), sticker);
        double y = middleY(WideScreenFace.height(board.getBlockState()), sticker.row());
        boolean showing = age >= 0;
        double half = showing ? HALF * pop(age) : HALF;
        int sheetRow = sticker.row() + (showing ? 0 : Sticker.ROWS);
        double u0 = (double) sticker.column() / SHEET_CELLS;
        double v0 = (double) sheetRow / SHEET_CELLS;
        double cell = 1.0 / SHEET_CELLS;
        quad(stickers, matrix, x - half, y - half, x + half, y + half, u0, v0, u0 + cell, v0 + cell, 2 * LAYER, WHITE,
            showing ? LightTexture.FULL_BRIGHT : light);
    }

    /**
     * How big a sticker popping on is, so many ticks after it went up: swelling from nothing past its size, settling.
     */
    private static double pop(double age) {
        if (age >= POP_TICKS) {
            return 1;
        }
        double t = age / POP_TICKS - 1;
        return 1 + (OVERSHOOT + 1) * t * t * t + OVERSHOOT * t * t;
    }

    /**
     * When each row filled and when the whole board did, as the server named the moments; {@link Double#MAX_VALUE} for
     * not yet.
     */
    private record Moments(double[] rowFull, double boardFull) {
        static Moments of(StickerMapBlockEntity board) {
            double[] rows = new double[Sticker.ROWS];
            double whole = 0;
            for (Sticker sticker : Sticker.values()) {
                double since = board.upSince(sticker);
                rows[sticker.row()] = Math.max(rows[sticker.row()], since);
                whole = Math.max(whole, since);
            }
            for (int row = 0; row < rows.length; row++) {
                rows[row] = rows[row] == StickerMapBlockEntity.NOT_UP ? Double.MAX_VALUE : rows[row];
            }
            return new Moments(rows, whole == StickerMapBlockEntity.NOT_UP ? Double.MAX_VALUE : whole);
        }
    }

    /** A full row's gold ribbon behind its stickers, across the map from margin to margin. */
    private static void ribbon(VertexConsumer shapes, Matrix4f matrix, double width, double height, int row, boolean full) {
        if (!full) {
            return;
        }
        double y = middleY(height, row);
        double half = HALF * RIBBON_HEIGHT;
        ScreenDrawing.rect(shapes, matrix, RIBBON_IN, y - half, width - RIBBON_IN, y + half, LAYER, RIBBON);
    }

    /** A full board's edging just inside its frame: glowing through the rainbow as it fills, gold for good after. */
    private static void edging(VertexConsumer shapes, Matrix4f matrix, double width, double height, double full, double now) {
        double age = now - full;
        if (age < 0) {
            return;
        }
        int colour = age < RAINBOW_TICKS ? 0xFF000000 | Mth.hsvToRgb((float) (age / RAINBOW_CYCLE % 1), 0.7F, 1) : GOLD;
        double x0 = EDGING_IN;
        double y0 = EDGING_IN;
        double x1 = width - EDGING_IN;
        double y1 = height - EDGING_IN;
        float z = 3 * LAYER;
        ScreenDrawing.line(shapes, matrix, x0, y0, x1, y0, z, EDGING, colour);
        ScreenDrawing.line(shapes, matrix, x1, y0, x1, y1, z, EDGING, colour);
        ScreenDrawing.line(shapes, matrix, x1, y1, x0, y1, z, EDGING, colour);
        ScreenDrawing.line(shapes, matrix, x0, y1, x0, y0, z, EDGING, colour);
    }

    /**
     * What goes on over a sticker as it, its row or the board makes a moment: the halo and the burst of stars as it
     * goes up, and as its row fills its shine and a burst of its own in turn along the row, and again row by row as the
     * board fills.
     */
    private static void celebrate(VertexConsumer shapes, Matrix4f matrix, StickerMapBlockEntity board, Sticker sticker, Moments moments, double now) {
        double x = middleX(WideScreenFace.width(board.getBlockState()), sticker);
        double y = middleY(WideScreenFace.height(board.getBlockState()), sticker.row());
        double age = now - board.upSince(sticker);
        if (age >= 0 && age < HALO_TICKS) {
            int alpha = (int) (HALO_ALPHA * (1 - age / HALO_TICKS));
            double reach = HALF + HALO * Math.min(1, age / POP_TICKS);
            ScreenDrawing.rect(shapes, matrix, x - reach, y - reach, x + reach, y + reach, LAYER, alpha << 24 | HALO_COLOUR);
        }
        burst(shapes, matrix, x, y, age);
        double rowAge = now - moments.rowFull()[sticker.row()] - sticker.column() * SHINE_APART;
        double boardAge = now - moments.boardFull() - sticker.row() * ROW_APART - sticker.column() * SHINE_APART;
        shine(shapes, matrix, x, y, rowAge);
        burst(shapes, matrix, x, y, rowAge);
        shine(shapes, matrix, x, y, boardAge);
        burst(shapes, matrix, x, y, boardAge);
    }

    /**
     * A white shine over a sticker, rising and falling over {@link #SHINE_TICKS} from so many ticks after its moment.
     */
    private static void shine(VertexConsumer shapes, Matrix4f matrix, double x, double y, double age) {
        if (age < 0 || age >= SHINE_TICKS) {
            return;
        }
        int alpha = (int) (SHINE_ALPHA * Math.sin(Math.PI * age / SHINE_TICKS));
        ScreenDrawing.rect(shapes, matrix, x - HALF, y - HALF, x + HALF, y + HALF, 4 * LAYER, alpha << 24 | 0xFFFFFF);
    }

    /**
     * A burst of four-pointed stars flying out of a sticker so many ticks after its moment, turning, shrinking and
     * fading as they go.
     */
    private static void burst(VertexConsumer shapes, Matrix4f matrix, double x, double y, double age) {
        if (age < 0 || age >= BURST_TICKS) {
            return;
        }
        double along = age / BURST_TICKS;
        double out = HALF + STAR_FLIGHT * Math.sqrt(along);
        double size = STAR_SIZE * (1 - along * STAR_SHRINK);
        int alpha = (int) (OPAQUE * (1 - along * along));
        for (int star = 0; star < STARS; star++) {
            double angle = 2 * Math.PI * star / STARS + along * STAR_TURN;
            int colour = alpha << 24 | (STAR_COLOURS[star % STAR_COLOURS.length] & 0xFFFFFF);
            star(shapes, matrix, x + Math.cos(angle) * out, y + Math.sin(angle) * out, size, along * 2 * Math.PI * STAR_TURN, colour);
        }
    }

    /** A four-pointed star round a middle, so far out each way, turned so far, in radians. */
    private static void star(VertexConsumer shapes, Matrix4f matrix, double x, double y, double size, double turn, int colour) {
        float z = 5 * LAYER;
        double thin = size * STAR_WAIST;
        for (int arm = 0; arm < 2; arm++) {
            double a = turn + arm * Math.PI / 2;
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            ScreenDrawing.vertex(shapes, matrix, x + cos * size, y + sin * size, z, colour);
            ScreenDrawing.vertex(shapes, matrix, x - sin * thin, y + cos * thin, z, colour);
            ScreenDrawing.vertex(shapes, matrix, x - cos * size, y - sin * size, z, colour);
            ScreenDrawing.vertex(shapes, matrix, x + sin * thin, y - cos * thin, z, colour);
        }
    }

    /**
     * A textured quad from one corner of the face to the other, its picture from one corner of its texture to the
     * other, the top of the picture at the top, tinted and lit as given.
     */
    private static void quad(VertexConsumer consumer, Matrix4f matrix, double x0, double y0, double x1, double y1, double u0, double v0, double u1,
                             double v1, float z, int tint, int light) {
        consumer.addVertex(matrix, (float) x0, (float) y0, z).setColor(tint).setUv((float) u0, (float) v1).setLight(light);
        consumer.addVertex(matrix, (float) x1, (float) y0, z).setColor(tint).setUv((float) u1, (float) v1).setLight(light);
        consumer.addVertex(matrix, (float) x1, (float) y1, z).setColor(tint).setUv((float) u1, (float) v0).setLight(light);
        consumer.addVertex(matrix, (float) x0, (float) y1, z).setColor(tint).setUv((float) u0, (float) v0).setLight(light);
    }
}
