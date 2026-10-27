package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.block.PropellerBlock;
import dev.aerolarge.block.PropellerBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Draws a zeppelin's propeller behind its hub (see {@link PropellerBlock}): four long blades of varnished wood with
 * brass-yellow tips, as the Hindenburg's were, round a polished spinner, turning as far as the hub's block entity has
 * spun them. Flat colours on vanilla's white texture, lit by the light just behind the hub.
 */
public class PropellerRenderer implements BlockEntityRenderer<PropellerBlockEntity> {
    private static final int BLADES = 4;
    /** How far behind the hub's middle the blades turn, in blocks: just clear of its back face. */
    private static final float BLADE_BEHIND = 0.7F;
    /**
     * A blade: where it starts out from the middle, how wide it is at its root and at its tip, and how thick, in
     * blocks.
     */
    private static final float BLADE_ROOT = 0.5F;
    private static final float ROOT_WIDTH = 0.9F;
    private static final float TIP_WIDTH = 0.55F;
    private static final float BLADE_THICKNESS = 0.16F;
    /** The brass tip's share of the blade's length. */
    private static final float TIP_SHARE = 0.12F;
    /** The spinner: its radius and how far it sticks out behind the blades, in blocks. */
    private static final float SPINNER_RADIUS = 0.45F;
    private static final float SPINNER_LENGTH = 0.9F;
    private static final int WOOD = 0xFF8A5A33;
    private static final int WOOD_EDGE = 0xFF6B4426;
    private static final int BRASS = 0xFFE8C24A;
    private static final int SPINNER = 0xFFD8DDE2;
    /** How far off it is still drawn, in blocks: from well along the hull, as an engine is. */
    private static final int VIEW_DISTANCE = 256;

    @Override
    public void render(PropellerBlockEntity propeller, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int overlay) {
        Level level = propeller.getLevel();
        if (level == null) {
            return;
        }
        Direction pulls = propeller.getBlockState().getValue(PropellerBlock.FACING);
        Direction behind = pulls.getOpposite();
        int light = SableClient.light(level, propeller.getBlockPos().relative(behind));
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
        pose.pushPose();
        // Onto the hub's axis: z out behind it, the blades turning in x and y.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-behind.toYRot()));
        pose.translate(0, 0, BLADE_BEHIND);
        spinner(consumer, pose.last(), light);
        pose.mulPose(Axis.ZP.rotation(propeller.angle(partialTick)));
        for (int blade = 0; blade < BLADES; blade++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(360F * blade / BLADES));
            blade(consumer, pose.last(), light);
            pose.popPose();
        }
        pose.popPose();
    }

    /** One blade, out along y: varnished wood, tapering to its brass tip. */
    private static void blade(VertexConsumer consumer, PoseStack.Pose pose, int light) {
        float tipStart = PropellerBlockEntity.REACH - TIP_SHARE * (PropellerBlockEntity.REACH - BLADE_ROOT);
        float tipWidth = ROOT_WIDTH + (TIP_WIDTH - ROOT_WIDTH) * (tipStart - BLADE_ROOT) / (PropellerBlockEntity.REACH - BLADE_ROOT);
        slab(consumer, pose, light, BLADE_ROOT, tipStart, ROOT_WIDTH, tipWidth, WOOD, WOOD_EDGE);
        slab(consumer, pose, light, tipStart, PropellerBlockEntity.REACH, tipWidth, TIP_WIDTH, BRASS, BRASS);
    }

    /**
     * A flat tapering slab out along y from one distance to another, its width tapering from one to the other, the
     * blade's thickness through z: its faces in the first colour, its edges in the second.
     */
    private static void slab(VertexConsumer consumer, PoseStack.Pose pose, int light, float from, float to, float fromWidth, float toWidth,
                             int face, int edge) {
        float a = fromWidth / 2;
        float b = toWidth / 2;
        float t = BLADE_THICKNESS / 2;
        FlatQuads.quad(consumer, pose, light, face, 0, 0, 1, -a, from, t, a, from, t, b, to, t, -b, to, t);
        FlatQuads.quad(consumer, pose, light, face, 0, 0, -1, -b, to, -t, b, to, -t, a, from, -t, -a, from, -t);
        FlatQuads.quad(consumer, pose, light, edge, 1, 0, 0, a, from, t, a, from, -t, b, to, -t, b, to, t);
        FlatQuads.quad(consumer, pose, light, edge, -1, 0, 0, -a, from, -t, -a, from, t, -b, to, t, -b, to, -t);
        FlatQuads.quad(consumer, pose, light, edge, 0, 1, 0, -b, to, t, b, to, t, b, to, -t, -b, to, -t);
    }

    /** The spinner over the blades' roots: a polished square cone pointing out behind. */
    private static void spinner(VertexConsumer consumer, PoseStack.Pose pose, int light) {
        float r = SPINNER_RADIUS;
        float tip = SPINNER_LENGTH;
        float[][] corners = {{-r, -r}, {r, -r}, {r, r}, {-r, r}};
        for (int i = 0; i < corners.length; i++) {
            float[] here = corners[i];
            float[] next = corners[(i + 1) % corners.length];
            float nx = (here[0] + next[0]) / 2;
            float ny = (here[1] + next[1]) / 2;
            FlatQuads.quad(consumer, pose, light, SPINNER, nx, ny, r, here[0], here[1], 0, next[0], next[1], 0, 0, 0, tip, 0, 0, tip);
        }
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(PropellerBlockEntity propeller) {
        return propeller.renderBox();
    }
}
