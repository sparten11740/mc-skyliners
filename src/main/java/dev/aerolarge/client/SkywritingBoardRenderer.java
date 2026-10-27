package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.block.SkywritingBoardBlock;
import dev.aerolarge.block.SkywritingBoardBlockEntity;
import dev.aerolarge.skywriting.SkyShape;
import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;

/**
 * Lights the skywriting board's button for the shape its zeppelin is writing (see {@link SkywritingBoardBlock}): a
 * glowing gold frame round it, brightening and dimming slowly, so whoever looks knows from the picture alone which
 * shape she is flying.
 */
public class SkywritingBoardRenderer implements BlockEntityRenderer<SkywritingBoardBlockEntity> {
    /** How wide the frame is, and how far off the board's face it is drawn, in blocks. */
    private static final float FRAME = 1.5F / 16;
    private static final float LIFT = 0.003F;
    /** How long the frame takes to brighten and dim again, in ticks. */
    private static final float PULSE_TICKS = 30;
    private static final int GOLD = 0xFFFFC83C;

    @Override
    public void render(SkywritingBoardBlockEntity board, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        SkyShape shape = board.shape();
        int[] cell = shape == null ? null : cell(shape);
        if (cell == null || board.getLevel() == null) {
            return;
        }
        Direction facing = board.getBlockState().getValue(SkywritingBoardBlock.FACING);
        pose.pushPose();
        // Onto the board's face as whoever reads it sees it: x to their right, y up, z toward them.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        float face = -0.5F + SkywritingBoardBlock.THICKNESS / 16F + LIFT;
        float margin = (float) SkywritingBoardBlock.MARGIN;
        float width = (1 - 2 * margin) / SkywritingBoardBlock.BUTTONS[0].length;
        float height = (1 - 2 * margin) / SkywritingBoardBlock.BUTTONS.length;
        float left = -0.5F + margin + cell[1] * width;
        float top = 0.5F - margin - cell[0] * height;
        float pulse = 0.6F + 0.4F * (float) Math.sin((board.getLevel().getGameTime() + partialTick) / PULSE_TICKS * 2 * Math.PI);
        int colour = ((int) (0xFF * pulse) << 24) | (GOLD & 0xFFFFFF);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(FlatQuads.WHITE));
        PoseStack.Pose last = pose.last();
        frame(consumer, last, colour, left, top - height, left + width, top, face);
        pose.popPose();
    }

    /** Four strips round a rectangle on the face, the frame's width inside it. */
    private static void frame(VertexConsumer consumer, PoseStack.Pose pose, int colour, float x0, float y0, float x1, float y1, float z) {
        strip(consumer, pose, colour, x0, y1 - FRAME, x1, y1, z);
        strip(consumer, pose, colour, x0, y0, x1, y0 + FRAME, z);
        strip(consumer, pose, colour, x0, y0 + FRAME, x0 + FRAME, y1 - FRAME, z);
        strip(consumer, pose, colour, x1 - FRAME, y0 + FRAME, x1, y1 - FRAME, z);
    }

    private static void strip(VertexConsumer consumer, PoseStack.Pose pose, int colour, float x0, float y0, float x1, float y1, float z) {
        FlatQuads.quad(consumer, pose, LightTexture.FULL_BRIGHT, colour, 0, 0, 1, x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z);
    }

    /** Which row and column a shape's button is in, or null for one not on the board. */
    @Nullable
    private static int[] cell(SkyShape shape) {
        for (int row = 0; row < SkywritingBoardBlock.BUTTONS.length; row++) {
            int column = Arrays.asList(SkywritingBoardBlock.BUTTONS[row]).indexOf(shape);
            if (column >= 0) {
                return new int[]{row, column};
            }
        }
        return null;
    }
}
