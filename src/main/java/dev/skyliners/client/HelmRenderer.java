package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.skyliners.block.HelmBlock;
import dev.skyliners.block.HelmBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * Draws a ship's wheel (see {@link HelmBlock}) on top of its pedestal, standing up across the block and facing its
 * helmsman: a varnished wooden rim, eight spokes from a brass hub running out past the rim into handles, the top one
 * painted red so the wheel's turn is seen at a glance, all turned as far round as its block entity has swung it.
 * Flat colours on vanilla's white texture.
 */
public class HelmRenderer implements BlockEntityRenderer<HelmBlockEntity> {
    private static final int SPOKES = 8;
    /** Steps round the rim. */
    private static final int RIM_STEPS = 24;
    /** How high the wheel's middle stands over the block's floor, and how far toward its helmsman, in blocks. */
    private static final float HUB_HEIGHT = 0.95F;
    private static final float HUB_TOWARD = 0.2F;
    /** The rim's middle radius and its half thickness, the spokes' half width, how far the handles reach, in blocks. */
    private static final float RIM_RADIUS = 0.62F;
    private static final float RIM_HALF = 0.05F;
    private static final float SPOKE_HALF = 0.035F;
    private static final float HANDLE_REACH = 0.85F;
    private static final float HUB_HALF = 0.1F;
    /** How thick the wheel is, front to back, in blocks. */
    private static final float DEPTH = 0.06F;
    private static final int WOOD = 0xFF8A5A33;
    private static final int HANDLE = 0xFF6B4426;
    private static final int MARK = 0xFFD23B2E;
    private static final int BRASS = 0xFFE8C24A;

    @Override
    public void render(HelmBlockEntity helm, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int overlay) {
        if (!Sight.ahead(pose.last())) {
            return;
        }
        Direction facing = helm.getBlockState().getValue(HelmBlock.FACING);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
        pose.pushPose();
        // Onto the wheel's middle: x to the helmsman's right, y up, z toward the helmsman.
        pose.translate(0.5, HUB_HEIGHT, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.getOpposite().toYRot()));
        pose.translate(0, 0, HUB_TOWARD);
        // Clockwise as the helmsman sees it, looking along -z.
        pose.mulPose(Axis.ZN.rotation(helm.angle(partialTick)));
        PoseStack.Pose at = pose.last();
        box(consumer, at, packedLight, -HUB_HALF, -HUB_HALF, HUB_HALF, HUB_HALF, BRASS);
        rim(consumer, at, packedLight);
        for (int spoke = 0; spoke < SPOKES; spoke++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(360F * spoke / SPOKES));
            box(consumer, pose.last(), packedLight, -SPOKE_HALF, HUB_HALF, SPOKE_HALF, HANDLE_REACH, spoke == 0 ? MARK : spokeColour(spoke));
            pose.popPose();
        }
        pose.popPose();
    }

    /** The wheel stands up over its block, its handles reaching out past it: drawn while any of that is in view. */
    @Override
    public AABB getRenderBoundingBox(HelmBlockEntity helm) {
        return new AABB(helm.getBlockPos()).inflate(HANDLE_REACH);
    }

    private static int spokeColour(int spoke) {
        return spoke % 2 == 0 ? WOOD : HANDLE;
    }

    /** The rim: a ring of short boxes round the hub. */
    private static void rim(VertexConsumer consumer, PoseStack.Pose pose, int light) {
        for (int step = 0; step < RIM_STEPS; step++) {
            double a = 2 * Math.PI * step / RIM_STEPS;
            double b = 2 * Math.PI * (step + 1) / RIM_STEPS;
            float inner = RIM_RADIUS - RIM_HALF;
            float outer = RIM_RADIUS + RIM_HALF;
            float ax = (float) Math.cos(a);
            float ay = (float) Math.sin(a);
            float bx = (float) Math.cos(b);
            float by = (float) Math.sin(b);
            FlatQuads.quad(consumer, pose, light, WOOD, 0, 0, 1,
                inner * ax, inner * ay, DEPTH, outer * ax, outer * ay, DEPTH, outer * bx, outer * by, DEPTH, inner * bx, inner * by, DEPTH);
            FlatQuads.quad(consumer, pose, light, WOOD, 0, 0, -1,
                inner * bx, inner * by, -DEPTH, outer * bx, outer * by, -DEPTH, outer * ax, outer * ay, -DEPTH, inner * ax, inner * ay, -DEPTH);
            FlatQuads.quad(consumer, pose, light, HANDLE, ax, ay, 0,
                outer * ax, outer * ay, DEPTH, outer * ax, outer * ay, -DEPTH, outer * bx, outer * by, -DEPTH, outer * bx, outer * by, DEPTH);
            FlatQuads.quad(consumer, pose, light, HANDLE, -ax, -ay, 0,
                inner * bx, inner * by, DEPTH, inner * bx, inner * by, -DEPTH, inner * ax, inner * ay, -DEPTH, inner * ax, inner * ay, DEPTH);
        }
    }

    /** A flat box in the wheel's plane, from one corner to the other, as thick as the wheel. */
    private static void box(VertexConsumer consumer, PoseStack.Pose pose, int light, float x1, float y1, float x2, float y2, int colour) {
        FlatQuads.quad(consumer, pose, light, colour, 0, 0, 1, x1, y1, DEPTH, x2, y1, DEPTH, x2, y2, DEPTH, x1, y2, DEPTH);
        FlatQuads.quad(consumer, pose, light, colour, 0, 0, -1, x1, y2, -DEPTH, x2, y2, -DEPTH, x2, y1, -DEPTH, x1, y1, -DEPTH);
        FlatQuads.quad(consumer, pose, light, colour, 1, 0, 0, x2, y1, DEPTH, x2, y1, -DEPTH, x2, y2, -DEPTH, x2, y2, DEPTH);
        FlatQuads.quad(consumer, pose, light, colour, -1, 0, 0, x1, y2, DEPTH, x1, y2, -DEPTH, x1, y1, -DEPTH, x1, y1, DEPTH);
        FlatQuads.quad(consumer, pose, light, colour, 0, 1, 0, x1, y2, DEPTH, x2, y2, DEPTH, x2, y2, -DEPTH, x1, y2, -DEPTH);
        FlatQuads.quad(consumer, pose, light, colour, 0, -1, 0, x1, y1, -DEPTH, x2, y1, -DEPTH, x2, y1, DEPTH, x1, y1, DEPTH);
    }
}
