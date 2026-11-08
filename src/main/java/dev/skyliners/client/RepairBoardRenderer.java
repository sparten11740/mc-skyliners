package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skyliners.block.WideScreenBlock;
import dev.skyliners.repairs.RepairBoardBlock;
import dev.skyliners.repairs.RepairBoardBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws a repair board (see {@link RepairBoardBlock}) over the whole of its face (see {@link WideScreenFace}): a dark
 * steel board edged in steel; a spanner clipped to its left end; two rows of five stars, each lit gold for a fault
 * fixed and grey until then, a row of five lit glowing gold behind; a little gold medal along its foot for each time
 * all ten have lit; and at its right end a warning sign over a lamp, both flashing red while a fault near it waits to
 * be fixed and dark when all is well. The newest star pops on, swelling past its size with a halo and settling, from
 * the moment the server says it lit (see {@link RepairBoardBlockEntity#litAt}).
 */
public class RepairBoardRenderer implements BlockEntityRenderer<RepairBoardBlockEntity> {
    /** The face's colours: its board, its steel edge, a star lit and unlit, the halo, the ribbon and the spanner. */
    private static final int BOARD = 0xFF262A30;
    private static final int EDGE = 0xFF6E757E;
    private static final int GOLD = 0xFFFFD03A;
    private static final int GOLD_HEART = 0xFFFFF2A8;
    private static final int UNLIT = 0xFF4A4F57;
    private static final int HALO = 0x90FFF0A0;
    private static final int RIBBON = 0x60F2C440;
    private static final int STEEL = 0xFFB8BFC6;
    private static final int STEEL_DARK = 0xFF7A828C;
    /** The warning sign and lamp: yellow and red lit, dark at rest, and the sign's mark. */
    private static final int WARNING_YELLOW = 0xFFF2C01C;
    private static final int RED = 0xFFFF3A2A;
    private static final int DARK_RED = 0xFF4A1612;
    private static final int DARK_SIGN = 0xFF4A4630;
    private static final int MARK = 0xFF1C1C20;
    /** How wide the steel edge round the board is, in the face's units (two wide, one tall). */
    private static final double EDGE_WIDTH = 0.04;
    /**
     * Where the stars sit: the first's middle so far from the face's left edge, each next so far along, the two rows so
     * high, and how far out each star's points reach and its waist between them.
     */
    private static final double STARS_FROM = 0.5;
    private static final double STAR_APART = 0.26;
    private static final double TOP_ROW = 0.66;
    private static final double BOTTOM_ROW = 0.3;
    private static final double STAR_SIZE = 0.1;
    private static final double STAR_WAIST = 0.42;
    /** A row of five lit: the gold ribbon behind it, so tall. */
    private static final double RIBBON_HEIGHT = 0.26;
    /** The medals for each full board: so far up from the foot, so far apart, so big, and how many shown at most. */
    private static final double MEDAL_UP = 0.07;
    private static final double MEDAL_APART = 0.06;
    private static final double MEDAL_SIZE = 0.022;
    private static final int MOST_MEDALS = 10;
    /** The warning sign and lamp at the right end: their middle across, the sign's middle and size, the lamp's. */
    private static final double WARNING_X = 1.82;
    private static final double SIGN_Y = 0.66;
    private static final double SIGN_SIZE = 0.12;
    private static final double LAMP_Y = 0.3;
    private static final double LAMP_SIZE = 0.09;
    /** How long the warning stays lit, and as long dark, flashing, in ticks. */
    private static final double FLASH_TICKS = 6;
    /**
     * A star lighting: how long it takes to pop on, in ticks, how far past its size it swells, how long its halo glows,
     * in ticks, and how far past the star the halo reaches, as a share of its size.
     */
    private static final double POP_TICKS = 10;
    private static final double OVERSHOOT = 1.8;
    private static final double HALO_TICKS = 40;
    private static final double HALO_REACH = 1.9;
    /** Lifts each layer off the one below it. */
    private static final float LAYER = 0.001F;
    /**
     * How far off a board is drawn, in blocks, and how far from its block any of it can reach. Sable draws an assembled
     * ship's block entities every frame unless told otherwise, and past this its stars cannot be made out.
     */
    private static final int VIEW_DISTANCE = 40;
    private static final float BOARD_REACH = 3;
    /** How far back of its block's middle the camera must be to see nothing of the board: at the back of the block. */
    private static final double BEHIND_FACE = -0.5;
    /** The pieces a disc is drawn in. */
    private static final int DISC_PIECES = 16;

    /** Drawn from the front only: from behind, its casing is all there is to see. */
    @Override
    public boolean shouldRender(RepairBoardBlockEntity board, Vec3 camera) {
        Direction facing = board.getBlockState().getValue(WideScreenBlock.FACING);
        return Sight.facing(board.getBlockPos(), facing, BEHIND_FACE, camera, getViewDistance());
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public boolean shouldRenderOffScreen(RepairBoardBlockEntity board) {
        return true;
    }

    /** All of it, from the part holding it. */
    @Override
    public AABB getRenderBoundingBox(RepairBoardBlockEntity board) {
        return new AABB(board.getBlockPos()).inflate(RepairBoardBlock.BOARD_COLUMNS);
    }

    @Override
    public void render(RepairBoardBlockEntity board, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!Sight.ahead(pose.last(), BOARD_REACH)) {
            return;
        }
        pose.pushPose();
        WideScreenFace.onto(pose, board.getBlockState());
        Matrix4f matrix = pose.last().pose();
        double width = WideScreenFace.width(board.getBlockState());
        double height = WideScreenFace.height(board.getBlockState());
        VertexConsumer shapes = ScreenDrawing.shapes(buffers);
        ScreenDrawing.rect(shapes, matrix, 0, 0, width, height, LAYER, EDGE);
        ScreenDrawing.rect(shapes, matrix, EDGE_WIDTH, EDGE_WIDTH, width - EDGE_WIDTH, height - EDGE_WIDTH, 2 * LAYER, BOARD);
        spanner(shapes, matrix);
        double now = DrawingClock.gameTime(partialTick);
        int repairs = board.repairs();
        int lit = repairs == 0 ? 0 : (repairs - 1) % RepairBoardBlockEntity.STARS + 1;
        for (int row = 0; row < 2; row++) {
            if (lit >= (row + 1) * RepairBoardBlockEntity.ROW) {
                double y = row == 0 ? TOP_ROW : BOTTOM_ROW;
                ScreenDrawing.rect(shapes, matrix, STARS_FROM - STAR_APART / 2, y - RIBBON_HEIGHT / 2,
                    STARS_FROM + STAR_APART * (RepairBoardBlockEntity.ROW - 0.5), y + RIBBON_HEIGHT / 2, 3 * LAYER, RIBBON);
            }
        }
        for (int star = 0; star < RepairBoardBlockEntity.STARS; star++) {
            star(shapes, matrix, star, lit, star == lit - 1 ? now - board.litAt() : Double.MAX_VALUE);
        }
        int medals = Math.min(MOST_MEDALS, repairs == 0 ? 0 : (repairs - 1) / RepairBoardBlockEntity.STARS);
        for (int medal = 0; medal < medals; medal++) {
            ScreenDrawing.disc(shapes, matrix, STARS_FROM + medal * MEDAL_APART, MEDAL_UP, MEDAL_SIZE, 8, 4 * LAYER, GOLD);
        }
        warning(shapes, matrix, board.waiting() > 0 && (int) (now / FLASH_TICKS) % 2 == 0);
        pose.popPose();
    }

    /** A star, its place on the board by its number, lit gold or grey; the newest popping on with its halo. */
    private static void star(VertexConsumer shapes, Matrix4f matrix, int star, int lit, double since) {
        double x = STARS_FROM + STAR_APART * (star % RepairBoardBlockEntity.ROW);
        double y = star < RepairBoardBlockEntity.ROW ? TOP_ROW : BOTTOM_ROW;
        if (star >= lit) {
            fivePoints(shapes, matrix, x, y, STAR_SIZE, 5 * LAYER, UNLIT);
            return;
        }
        double size = STAR_SIZE;
        if (since >= 0 && since < POP_TICKS) {
            size *= Math.min(1, since / (POP_TICKS / 2)) * (1 + (OVERSHOOT - 1) * Math.sin(Math.PI * since / POP_TICKS));
        }
        if (since >= 0 && since < HALO_TICKS) {
            int alpha = (int) ((HALO >>> 24) * (1 - since / HALO_TICKS));
            ScreenDrawing.disc(shapes, matrix, x, y, STAR_SIZE * HALO_REACH, DISC_PIECES, 4 * LAYER, alpha << 24 | (HALO & 0xFFFFFF));
        }
        fivePoints(shapes, matrix, x, y, size, 5 * LAYER, GOLD);
        fivePoints(shapes, matrix, x, y, size * 0.45, 6 * LAYER, GOLD_HEART);
    }

    /** A five-pointed star round a middle, a point up, each point a kite out from the middle. */
    private static void fivePoints(VertexConsumer shapes, Matrix4f matrix, double x, double y, double size, float z, int colour) {
        double waist = size * STAR_WAIST;
        for (int point = 0; point < 5; point++) {
            double out = 2 * Math.PI * point / 5;
            double before = out + Math.PI / 5;
            double after = out - Math.PI / 5;
            ScreenDrawing.vertex(shapes, matrix, x, y, z, colour);
            ScreenDrawing.vertex(shapes, matrix, x + Math.sin(before) * waist, y + Math.cos(before) * waist, z, colour);
            ScreenDrawing.vertex(shapes, matrix, x + Math.sin(out) * size, y + Math.cos(out) * size, z, colour);
            ScreenDrawing.vertex(shapes, matrix, x + Math.sin(after) * waist, y + Math.cos(after) * waist, z, colour);
        }
    }

    /** The spanner clipped to the board's left end: its jaw at the top, open upward, its shaft down to a ring. */
    private static void spanner(VertexConsumer shapes, Matrix4f matrix) {
        ScreenDrawing.line(shapes, matrix, 0.2, 0.72, 0.2, 0.22, 4 * LAYER, 0.06F, STEEL);
        ScreenDrawing.disc(shapes, matrix, 0.2, 0.76, 0.1, DISC_PIECES, 4 * LAYER, STEEL);
        ScreenDrawing.rect(shapes, matrix, 0.165, 0.76, 0.235, 0.9, 5 * LAYER, BOARD);
        ScreenDrawing.disc(shapes, matrix, 0.2, 0.18, 0.06, DISC_PIECES, 4 * LAYER, STEEL);
        ScreenDrawing.disc(shapes, matrix, 0.2, 0.18, 0.03, DISC_PIECES, 5 * LAYER, BOARD);
        ScreenDrawing.rect(shapes, matrix, 0.1, 0.45, 0.3, 0.5, 6 * LAYER, STEEL_DARK);
    }

    /** The warning sign, a triangle with a mark in it, over its lamp, both lit red and yellow or dark. */
    private static void warning(VertexConsumer shapes, Matrix4f matrix, boolean lit) {
        int sign = lit ? WARNING_YELLOW : DARK_SIGN;
        double bottom = SIGN_Y - SIGN_SIZE * 0.8;
        ScreenDrawing.vertex(shapes, matrix, WARNING_X - SIGN_SIZE, bottom, 4 * LAYER, sign);
        ScreenDrawing.vertex(shapes, matrix, WARNING_X + SIGN_SIZE, bottom, 4 * LAYER, sign);
        ScreenDrawing.vertex(shapes, matrix, WARNING_X, SIGN_Y + SIGN_SIZE, 4 * LAYER, sign);
        ScreenDrawing.vertex(shapes, matrix, WARNING_X, SIGN_Y + SIGN_SIZE, 4 * LAYER, sign);
        ScreenDrawing.rect(shapes, matrix, WARNING_X - 0.012, SIGN_Y - 0.03, WARNING_X + 0.012, SIGN_Y + 0.07, 5 * LAYER, MARK);
        ScreenDrawing.rect(shapes, matrix, WARNING_X - 0.012, SIGN_Y - 0.075, WARNING_X + 0.012, SIGN_Y - 0.05, 5 * LAYER, MARK);
        ScreenDrawing.disc(shapes, matrix, WARNING_X, LAMP_Y, LAMP_SIZE, DISC_PIECES, 4 * LAYER, lit ? RED : DARK_RED);
    }
}
