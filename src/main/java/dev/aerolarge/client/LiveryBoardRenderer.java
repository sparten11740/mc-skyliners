package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.airframe.Livery;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.block.AutopilotBlock;
import dev.aerolarge.block.LiveryBoardBlock;
import dev.aerolarge.block.LiveryBoardBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * Draws over a livery board (see {@link LiveryBoardBlock}): a gold frame round the swatch its zeppelin wears; and,
 * while the zeppelin is being repainted, the painters' cradle on each flank where the new paint has reached, a plank
 * platform with a rail and a pot of paint on it, slung on ropes from the crown, riding aft from the nose to the tail
 * as the paint sweeps along (see {@link Livery#REPAINT_TICKS}). Drawn as far off as the whole zeppelin is seen.
 */
public class LiveryBoardRenderer implements BlockEntityRenderer<LiveryBoardBlockEntity> {
    /** The swatches' edges on the board's face, in sixteenths: their columns' left and right, their rows' top and bottom. */
    private static final int[][] COLUMNS = {{1, 5}, {6, 10}, {11, 15}};
    private static final int[][] ROWS = {{9, 14}, {2, 7}};
    /** How wide the gold frame is, in sixteenths, and how far it stands off the face, in blocks. */
    private static final float FRAME = 0.6F;
    private static final float LIFT = 0.004F;
    private static final int GOLD = 0xFFE8B83A;
    /** The cradle: half its length along the hull, its depth out from it and its rail's height, in blocks. */
    private static final float CRADLE_HALF = 2.5F;
    private static final float CRADLE_DEPTH = 1.4F;
    private static final float RAIL = 1.0F;
    /** How far out from the hull's flank the cradle hangs, and how far under its widest, in blocks. */
    private static final double HANG_OUT = 0.6;
    private static final double HANG_BELOW = 3;
    /** Its ropes: how far off the cover they lie, how wide they are and how many straight lengths each is drawn in. */
    private static final float ROPE_OFF = 0.15F;
    private static final float ROPE_WIDTH = 0.12F;
    private static final int ROPE_LENGTHS = 12;
    private static final int PLANK = 0xFF8B5A2B;
    private static final int RAIL_WOOD = 0xFF5E3B1C;
    private static final int ROPE = 0xFFD9C79B;
    private static final int PAINT_POT = 0xFF9AA3AD;

    @Override
    public void render(LiveryBoardBlockEntity board, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = board.getLevel();
        AutopilotBlockEntity autopilot = level == null ? null : LiveryBoardBlock.zeppelin(level, board.getBlockPos());
        if (autopilot == null) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
        frame(board, autopilot.livery(), pose, consumer);
        long start = autopilot.repaintStart();
        double share = start < 0 ? 1 : (level.getGameTime() + partialTick - start) / Livery.REPAINT_TICKS;
        if (share >= 0 && share < 1) {
            cradles(board.getBlockPos(), autopilot, share * Livery.sweep(autopilot.zeppelinSize()), pose, consumer);
        }
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

    /** A cradle on each flank, so far aft of the nose as the new paint has reached, in blocks. */
    private static void cradles(BlockPos board, AutopilotBlockEntity autopilot, double reached, PoseStack pose, VertexConsumer consumer) {
        BoundingBox box = autopilot.frame();
        Direction nose = autopilot.getBlockState().getValue(AutopilotBlock.FACING);
        Direction starboard = nose.getClockWise();
        double tip = Math.max(project(box.minX(), box.minZ(), nose), project(box.maxX() + 1, box.maxZ() + 1, nose));
        double middle = (project(box.minX(), box.minZ(), starboard) + project(box.maxX() + 1, box.maxZ() + 1, starboard)) / 2;
        double along = tip - reached;
        double radius = autopilot.zeppelinSize().radiusAt(reached);
        double y = box.minY() + autopilot.zeppelinSize().axis() - HANG_BELOW;
        for (int side = -1; side <= 1; side += 2) {
            double across = middle + side * (radius + HANG_OUT + CRADLE_DEPTH / 2);
            double x = along * nose.getStepX() + across * starboard.getStepX();
            double z = along * nose.getStepZ() + across * starboard.getStepZ();
            pose.pushPose();
            pose.translate(x - board.getX(), y - board.getY(), z - board.getZ());
            Cradle cradle = new Cradle(pose.last(), consumer, nose.getAxis() == Direction.Axis.X, side * (starboard.getStepX() + starboard.getStepZ()));
            cradle.draw((float) radius);
            pose.popPose();
        }
    }

    /** How far along a way a place lies, in blocks, from a corner given in whole blocks. */
    private static double project(double x, double z, Direction way) {
        return x * way.getStepX() + z * way.getStepZ();
    }

    /**
     * One cradle, its middle at the pose's origin, its length along the hull (along x, or else z) and its depth out
     * from it the way given (+1 or -1 along the other): the planks, a rail along its outer edge, a pot of paint, and
     * from either end of it a rope up over the hull's curve to its crown, the hull's radius there given.
     */
    private record Cradle(PoseStack.Pose pose, VertexConsumer consumer, boolean alongX, int out) {
        void draw(float radius) {
            float half = CRADLE_DEPTH / 2;
            part(PLANK, -CRADLE_HALF, CRADLE_HALF, -half, half, -0.15F, 0);
            part(RAIL_WOOD, -CRADLE_HALF, CRADLE_HALF, half - 0.12F, half, 0, RAIL);
            part(PAINT_POT, -0.2F, 0.2F, -0.2F, 0.2F, 0, 0.35F);
            rope(-CRADLE_HALF + 0.1F, radius);
            rope(CRADLE_HALF - 0.1F, radius);
        }

        /**
         * A rope from the cradle's inner edge, so far along it, up over the hull's curve to its crown, lying just off the
         * cover: a string of short straight lengths round the hull's circle from beside the cradle to the top.
         */
        private void rope(float along, float radius) {
            float lying = radius + ROPE_OFF;
            float fromAxis = (float) (radius + HANG_OUT + CRADLE_DEPTH / 2);
            float axisUp = (float) HANG_BELOW;
            float previousOut = -CRADLE_DEPTH / 2;
            float previousUp = 0;
            for (int k = 1; k <= ROPE_LENGTHS; k++) {
                double round = Math.PI / 2 * (1 - k / (double) ROPE_LENGTHS);
                float nextOut = (float) (lying * Math.sin(round)) - fromAxis;
                float nextUp = (float) (lying * Math.cos(round)) + axisUp;
                ribbon(along, previousOut, previousUp, nextOut, nextUp);
                previousOut = nextOut;
                previousUp = nextUp;
            }
        }

        /** A length of rope, a thin strip across the hull's length, from one point out from it and up to another. */
        private void ribbon(float along, float out0, float up0, float out1, float up1) {
            float w = ROPE_WIDTH / 2;
            float a0 = out * out0;
            float a1 = out * out1;
            int lit = LightTexture.FULL_BRIGHT;
            if (alongX) {
                FlatQuads.quad(consumer, pose, lit, ROPE, 0, 1, 0, along - w, up0, a0, along + w, up0, a0, along + w, up1, a1, along - w, up1, a1);
            } else {
                FlatQuads.quad(consumer, pose, lit, ROPE, 0, 1, 0, a0, up0, along - w, a0, up0, along + w, a1, up1, along + w, a1, up1, along - w);
            }
        }

        /** A box of the cradle, from and to so far along the hull and out from it, and so high, in blocks. */
        private void part(int colour, float along0, float along1, float out0, float out1, float y0, float y1) {
            float a = out * out0;
            float b = out * out1;
            float across0 = Math.min(a, b);
            float across1 = Math.max(a, b);
            if (alongX) {
                FlatQuads.box(consumer, pose, LightTexture.FULL_BRIGHT, colour, along0, y0, across0, along1, y1, across1);
            } else {
                FlatQuads.box(consumer, pose, LightTexture.FULL_BRIGHT, colour, across0, y0, along0, across1, y1, along1);
            }
        }
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
        Level level = board.getLevel();
        AutopilotBlockEntity autopilot = level == null ? null : LiveryBoardBlock.zeppelin(level, board.getBlockPos());
        BoundingBox box = autopilot == null ? null : autopilot.frame();
        return box == null ? new AABB(board.getBlockPos()) : AABB.of(box).inflate(4);
    }
}
