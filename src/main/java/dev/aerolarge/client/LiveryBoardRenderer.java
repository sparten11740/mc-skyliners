package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.airframe.Livery;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.block.LiveryBoardBlock;
import dev.aerolarge.block.LiveryBoardBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Draws over a livery board (see {@link LiveryBoardBlock}): a gold frame round the swatch its zeppelin wears; and,
 * while the zeppelin is being repainted, the painters' cradles riding along her (see {@link PaintersCradles}). Drawn as
 * far off as the whole zeppelin is seen.
 */
public class LiveryBoardRenderer implements BlockEntityRenderer<LiveryBoardBlockEntity> {
    /** The swatches' edges on the board's face, in sixteenths: their columns' left and right, their rows' top and bottom. */
    private static final int[][] COLUMNS = {{1, 5}, {6, 10}, {11, 15}};
    private static final int[][] ROWS = {{9, 14}, {2, 7}};
    /** How wide the gold frame is, in sixteenths, and how far it stands off the face, in blocks. */
    private static final float FRAME = 0.6F;
    private static final float LIFT = 0.004F;
    private static final int GOLD = 0xFFE8B83A;

    @Override
    public void render(LiveryBoardBlockEntity board, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = board.getLevel();
        AutopilotBlockEntity autopilot = level == null ? null : LiveryBoardBlock.zeppelin(level, board.getBlockPos());
        if (autopilot == null) {
            return;
        }
        frame(board, autopilot.livery(), pose, buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE)));
        PaintersCradles.draw(level, board.getBlockPos(), autopilot, partialTick, pose, buffers);
    }

    /** The gold frame round the worn swatch, just off the board's face, which faces the board's way. */
    private static void frame(LiveryBoardBlockEntity board, Livery worn, PoseStack pose, VertexConsumer consumer) {
        for (int swatch = 0; swatch < ROWS.length * COLUMNS.length; swatch++) {
            int row = swatch / COLUMNS.length;
            int column = swatch % COLUMNS.length;
            if (LiveryBoardBlock.SWATCHES[row][column] == worn) {
                drawFrame(board, COLUMNS[column], ROWS[row], pose, consumer);
            }
        }
    }

    private static void drawFrame(LiveryBoardBlockEntity board, int[] column, int[] row, PoseStack pose, VertexConsumer consumer) {
        Direction facing = board.getBlockState().getValue(LiveryBoardBlock.FACING);
        pose.pushPose();
        // Onto the painted face, x to the viewer's right and y up, z out of the face: the board stands at the back of
        // its block, so its face is short of the block's middle by half a block less the board's thickness.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(0, 0, LiveryBoardBlock.THICKNESS / 16F - 0.5F + LIFT);
        PoseStack.Pose last = pose.last();
        float x0 = column[0] / 16F - 0.5F;
        float x1 = column[1] / 16F - 0.5F;
        float y0 = row[0] / 16F - 0.5F;
        float y1 = row[1] / 16F - 0.5F;
        float w = FRAME / 16F;
        int lit = LightTexture.FULL_BRIGHT;
        FlatQuads.quad(consumer, last, lit, GOLD, 0, 0, 1, x0 - w, y0 - w, 0, x1 + w, y0 - w, 0, x1 + w, y0, 0, x0 - w, y0, 0);
        FlatQuads.quad(consumer, last, lit, GOLD, 0, 0, 1, x0 - w, y1, 0, x1 + w, y1, 0, x1 + w, y1 + w, 0, x0 - w, y1 + w, 0);
        FlatQuads.quad(consumer, last, lit, GOLD, 0, 0, 1, x0 - w, y0, 0, x0, y0, 0, x0, y1, 0, x0 - w, y1, 0);
        FlatQuads.quad(consumer, last, lit, GOLD, 0, 0, 1, x1, y0, 0, x1 + w, y0, 0, x1 + w, y1, 0, x1, y1, 0);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(LiveryBoardBlockEntity board) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 512;
    }

    /** The whole zeppelin's box, which the cradles ride along. */
    @Override
    public AABB getRenderBoundingBox(LiveryBoardBlockEntity board) {
        return PaintersCradles.box(board.getLevel(), board.getBlockPos());
    }
}
