package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.block.BubbleLevelBlock;
import dev.aerolarge.block.BubbleLevelBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a bubble level's glass tube of green spirit and its bubble (see {@link BubbleLevelBlock}): the bubble slides
 * toward whichever end of the tube is higher, as far as its end at a tilt of some ten degrees, so a zeppelin's pitch
 * is read at a glance, nose up or nose down; a dark mark in the middle shows where she is level.
 */
public class BubbleLevelRenderer implements BlockEntityRenderer<BubbleLevelBlockEntity> {
    /** The tilt that puts the bubble at the end of its tube, in degrees. */
    private static final double FULL_TILT = 10;
    /**
     * The tube: how far either side of the middle it reaches, how high its middle is, and how thick it is, in blocks.
     */
    private static final float TUBE_HALF = 6 / 16F;
    private static final float TUBE_HEIGHT = 6 / 16F - 0.5F;
    private static final float TUBE_THICK = 1 / 16F;
    /** How far the bubble slides either side of the middle, and how long it is, in blocks. */
    private static final float BUBBLE_TRAVEL = 4.5F / 16;
    private static final float BUBBLE_HALF = 1 / 16F;
    private static final int SPIRIT = 0xFF7ACC3A;
    private static final int BUBBLE = 0xFFF4FFE8;
    private static final int MARK = 0xFF202020;

    @Override
    public void render(BubbleLevelBlockEntity level, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level world = level.getLevel();
        if (world == null) {
            return;
        }
        if (!Sight.ahead(pose.last())) {
            return;
        }
        Direction facing = level.getBlockState().getValue(BubbleLevelBlock.FACING);
        float shift = (float) Mth.clamp(tilt(world, level.getBlockPos().getCenter(), facing.getClockWise()) / FULL_TILT, -1, 1) * BUBBLE_TRAVEL;
        pose.pushPose();
        // Onto the face whoever reads it looks at: x to their right, y up, z toward them.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.getOpposite().toYRot()));
        PoseStack.Pose last = pose.last();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
        FlatQuads.box(consumer, last, light, SPIRIT, -TUBE_HALF, TUBE_HEIGHT - TUBE_THICK, -TUBE_THICK, TUBE_HALF, TUBE_HEIGHT + TUBE_THICK, TUBE_THICK);
        FlatQuads.box(consumer, last, light, MARK, -0.4F / 16, TUBE_HEIGHT - TUBE_THICK * 1.4F, -TUBE_THICK * 1.4F, 0.4F / 16, TUBE_HEIGHT + TUBE_THICK * 1.4F,
            TUBE_THICK * 1.4F);
        FlatQuads.box(consumer, last, light, BUBBLE, shift - BUBBLE_HALF, TUBE_HEIGHT - TUBE_THICK * 0.7F, -TUBE_THICK * 1.2F, shift + BUBBLE_HALF,
            TUBE_HEIGHT + TUBE_THICK * 0.9F, TUBE_THICK * 1.2F);
        pose.popPose();
    }

    /** How far the way given rises from a point, as a tilt in degrees, where the point is in the world. */
    private static double tilt(Level level, Vec3 from, Direction way) {
        Vec3 there = SableClient.toWorld(level, from.add(Vec3.atLowerCornerOf(way.getNormal())));
        Vec3 here = SableClient.toWorld(level, from);
        return Math.toDegrees(Math.asin(Mth.clamp(there.y - here.y, -1, 1)));
    }
}
