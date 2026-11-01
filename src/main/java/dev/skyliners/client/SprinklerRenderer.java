package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.skyliners.block.SprinklerBlock;
import dev.skyliners.block.SprinklerBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;

/**
 * Draws a spraying sprinkler's arm and nozzle on its popped-up head (see {@link SprinklerBlockEntity}): a brass arm
 * rising from the head's middle, so its root is always buried in the head however it turns, and the nozzle at its end,
 * turned round with the jet as it sweeps over the beds and tipped up as steeply as the water leaves it, so the jet
 * always comes out of the nozzle the way it points.
 */
public class SprinklerRenderer implements BlockEntityRenderer<SprinklerBlockEntity> {
    /** The arm's half thickness, and the nozzle's half length and half width, in blocks. */
    private static final float ARM_HALF = 0.025F;
    private static final float NOZZLE_HALF = 0.045F;
    private static final float NOZZLE_WIDTH = 0.05F;
    private static final int BRASS = 0xFFC9A44E;
    private static final int NOZZLE = 0xFF8C6E2E;

    @Override
    public void render(SprinklerBlockEntity sprinkler, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (sprinkler.getLevel() == null || !sprinkler.getBlockState().getValue(SprinklerBlock.SPRAYING)) {
            return;
        }
        if (!Sight.ahead(pose.last())) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
        double angle = sprinkler.jetAngle(partialTick);
        float reach = (float) SprinklerBlockEntity.NOZZLE_REACH;
        pose.pushPose();
        pose.translate(0.5, SprinklerBlockEntity.HEAD, 0.5);
        pose.mulPose(Axis.YP.rotation((float) -angle));
        pose.mulPose(Axis.ZP.rotation((float) SprinklerBlockEntity.LAUNCH));
        FlatQuads.box(consumer, pose.last(), light, BRASS, 0, -ARM_HALF, -ARM_HALF, reach - NOZZLE_HALF, ARM_HALF, ARM_HALF);
        FlatQuads.box(consumer, pose.last(), light, NOZZLE, reach - 2 * NOZZLE_HALF, -NOZZLE_WIDTH, -NOZZLE_WIDTH, reach, NOZZLE_WIDTH, NOZZLE_WIDTH);
        pose.popPose();
    }
}
