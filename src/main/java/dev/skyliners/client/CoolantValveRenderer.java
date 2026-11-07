package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.skyliners.engineering.CoolantValveBlock;
import dev.skyliners.engineering.CoolantValveBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the coolant valve's wheel and gauge (see {@link CoolantValveBlock}), drawn as for a valve facing north: a big
 * red wheel, its rim, four spokes and a hub, on the end of its pipe, turned a quarter for each step of pressure; and
 * over it a round-faced gauge, a band of green, yellow and red across its top and a black needle swinging from green to
 * red as the pressure rises, both eased toward the pressure (see {@link CoolantValveBlockEntity}). Left out while out
 * of sight.
 */
public class CoolantValveRenderer implements BlockEntityRenderer<CoolantValveBlockEntity> {
    /** Where the wheel turns, its middle, in blocks from the block's corner as drawn facing north; and its size. */
    private static final float WHEEL_X = 0.5F;
    private static final float WHEEL_Y = 6.5F / 16;
    private static final float WHEEL_Z = 8.6F / 16;
    private static final float WHEEL_RADIUS = 0.28F;
    private static final float RIM = 0.045F;
    private static final int RIM_PIECES = 12;
    private static final float SPOKE = 0.03F;
    private static final float HUB = 0.06F;
    /** Where the gauge's face is, its middle, as drawn facing north; and its size, and its needle's length. */
    private static final float GAUGE_X = 10F / 16;
    private static final float GAUGE_Y = 12.5F / 16;
    private static final float GAUGE_Z = 13.8F / 16;
    private static final float GAUGE_HALF = 2.6F / 16;
    private static final float NEEDLE = 2.1F / 16;
    private static final float NEEDLE_WIDE = 0.25F / 16;
    /** How far either side of upright the needle swings, from no pressure to full, in degrees. */
    private static final float NEEDLE_SWING = 70;
    /** How much of the way to the pressure the wheel and needle come each tick. */
    private static final double EASE = 0.15;
    private static final int RED = 0xFFC62A22;
    private static final int RED_DARK = 0xFF8A1A14;
    private static final int GAUGE_FACE = 0xFFEDEDE6;
    private static final int GAUGE_RIM = 0xFF5A5F66;
    private static final int[] BAND = {0xFF3CB44A, 0xFFE8C438, 0xFFD8342A};
    private static final int NEEDLE_COLOUR = 0xFF1A1A1A;
    /** How thin the drawn faces stand proud of what they lie on, in blocks. */
    private static final float PROUD = 0.004F;

    @Override
    public void render(CoolantValveBlockEntity valve, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = valve.getBlockState();
        if (!(state.getBlock() instanceof CoolantValveBlock) || !Sight.ahead(pose.last())) {
            return;
        }
        float eased = (float) valve.toward(DrawingClock.ticks(partialTick), state.getValue(CoolantValveBlock.PRESSURE), EASE);
        Direction facing = state.getValue(CoolantValveBlock.FACING);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
        pose.pushPose();
        pose.rotateAround(Axis.YP.rotationDegrees(180 - facing.toYRot()), 0.5F, 0, 0.5F);
        wheel(pose, consumer, light, eased);
        gauge(pose, consumer, light, eased / CoolantValveBlock.FULL);
        pose.popPose();
    }

    /** The wheel, turned a quarter for each step of pressure: its rim in pieces, four spokes and its hub. */
    private static void wheel(PoseStack pose, VertexConsumer consumer, int light, float turns) {
        pose.pushPose();
        pose.translate(WHEEL_X, WHEEL_Y, WHEEL_Z);
        pose.mulPose(Axis.ZP.rotationDegrees(turns * 90));
        float half = (float) (Math.PI * WHEEL_RADIUS / RIM_PIECES) + RIM / 2;
        for (int piece = 0; piece < RIM_PIECES; piece++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(piece * 360F / RIM_PIECES));
            FlatQuads.box(consumer, pose.last(), light, RED, -half, WHEEL_RADIUS - RIM / 2, -RIM / 2, half, WHEEL_RADIUS + RIM / 2, RIM / 2);
            pose.popPose();
        }
        FlatQuads.box(consumer, pose.last(), light, RED_DARK, -WHEEL_RADIUS, -SPOKE, -SPOKE, WHEEL_RADIUS, SPOKE, SPOKE);
        FlatQuads.box(consumer, pose.last(), light, RED_DARK, -SPOKE, -WHEEL_RADIUS, -SPOKE, SPOKE, WHEEL_RADIUS, SPOKE);
        FlatQuads.box(consumer, pose.last(), light, RED, -HUB, -HUB, -HUB, HUB, HUB, HUB);
        pose.popPose();
    }

    /** The gauge: its rim and face, its band of green, yellow and red, and its needle swung as far as the pressure. */
    private static void gauge(PoseStack pose, VertexConsumer consumer, int light, float share) {
        pose.pushPose();
        pose.translate(GAUGE_X, GAUGE_Y, GAUGE_Z);
        PoseStack.Pose at = pose.last();
        FlatQuads.box(consumer, at, light, GAUGE_RIM, -GAUGE_HALF, -GAUGE_HALF, -PROUD, GAUGE_HALF, GAUGE_HALF, 0);
        float face = GAUGE_HALF * 0.85F;
        FlatQuads.box(consumer, at, light, GAUGE_FACE, -face, -face, -2 * PROUD, face, face, -PROUD);
        float third = 2 * face / BAND.length;
        for (int part = 0; part < BAND.length; part++) {
            float left = face - (part + 1) * third;
            FlatQuads.box(consumer, at, light, BAND[part], left, face * 0.55F, -3 * PROUD, left + third, face * 0.85F, -2 * PROUD);
        }
        pose.mulPose(Axis.ZP.rotationDegrees(2 * NEEDLE_SWING * share - NEEDLE_SWING));
        FlatQuads.box(consumer, pose.last(), light, NEEDLE_COLOUR, -NEEDLE_WIDE, 0, -4 * PROUD, NEEDLE_WIDE, NEEDLE, -3 * PROUD);
        pose.popPose();
    }
}
