package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.skyliners.turret.CannonRocket;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A laser cannon's rocket in flight (see {@link CannonRocket}), out of plain coloured faces: a white eight-sided body
 * with a red nose cone and four red fins swept back at its tail, spinning slowly about its length as it flies, nose
 * first the way it points, and out of its dark nozzle a flickering white-hot flame glowing into orange, whatever the
 * light round it. Its smoke and the flame's sparks behind it are puffs (see {@link CannonRockets}).
 */
public class CannonRocketRenderer extends EntityRenderer<CannonRocket> {
    /**
     * Its body: the radius of its eight sides, and how far behind and ahead of its middle it reaches, in blocks; its
     * nose cone's tip, ahead of its middle; its nozzle, how far behind its body, and its radius.
     */
    private static final float BODY = 0.065F;
    private static final float TAIL = -0.35F;
    private static final float SHOULDER = 0.25F;
    private static final float TIP = 0.43F;
    private static final float NOZZLE = 0.04F;
    private static final float NOZZLE_RADIUS = 0.05F;
    /** Its fins: how far out from its body they reach, and where along it they start and end, at root and tip. */
    private static final float FIN_OUT = 0.09F;
    private static final float FIN_ROOT_FRONT = -0.17F;
    private static final float FIN_TIP_FRONT = -0.3F;
    private static final float FIN_TIP_BACK = -0.4F;
    /**
     * Its flame: how long it is, in blocks, and how much longer at most as it flickers, how fast it flickers, in
     * radians a tick, how wide it is at the nozzle, in blocks, and its inner core's share of it.
     */
    private static final float FLAME = 0.55F;
    private static final float FLAME_FLICKER = 0.2F;
    private static final float FLICKER_SPEED = 2.7F;
    private static final float FLAME_WIDE = 0.07F;
    private static final float CORE_SHARE = 0.55F;
    /** How fast it spins about its length, in degrees a tick. */
    private static final float SPIN = 24;
    private static final int WHITE = 0xFFEDEDE8;
    private static final int RED = 0xFFD8362A;
    private static final int DARK = 0xFF2E3034;
    private static final int FLAME_GLOW = 0xC0FF8A2A;
    private static final int FLAME_CORE = 0xF0FFF2C8;
    /** How many sides its body and nose have. */
    private static final int SIDES = 8;
    /** The way its nose cone's faces face, out from its length and ahead along it, for their shading. */
    private static final float NOSE_SIDE = 0.86F;
    private static final float NOSE_AHEAD = 0.51F;

    public CannonRocketRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(CannonRocket rocket, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0, rocket.getBbHeight() / 2, 0);
        pose.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(partialTick, rocket.yRotO, rocket.getYRot())));
        pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partialTick, rocket.xRotO, rocket.getXRot())));
        float flown = rocket.flown(partialTick);
        pose.mulPose(Axis.ZP.rotationDegrees(flown * SPIN));
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
        PoseStack.Pose at = pose.last();
        body(solid, at, light);
        fins(solid, at, light);
        if (rocket.burning()) {
            float length = FLAME + FLAME_FLICKER * (0.5F + 0.5F * Mth.sin(flown * FLICKER_SPEED));
            VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
            flame(glow, at, length, FLAME_WIDE, FLAME_GLOW);
            flame(glow, at, length * CORE_SHARE, FLAME_WIDE * CORE_SHARE, FLAME_CORE);
        }
        pose.popPose();
        super.render(rocket, yaw, partialTick, pose, buffers, light);
    }

    /** Its eight-sided body, its nose cone, its tail's dark end and nozzle. */
    private static void body(VertexConsumer consumer, PoseStack.Pose pose, int light) {
        for (int side = 0; side < SIDES; side++) {
            double from = (side + 0.5) * 2 * Math.PI / SIDES;
            double to = (side + 1.5) * 2 * Math.PI / SIDES;
            double middle = (side + 1) * 2 * Math.PI / SIDES;
            float x0 = BODY * (float) Math.cos(from);
            float y0 = BODY * (float) Math.sin(from);
            float x1 = BODY * (float) Math.cos(to);
            float y1 = BODY * (float) Math.sin(to);
            float nx = (float) Math.cos(middle);
            float ny = (float) Math.sin(middle);
            FlatQuads.quad(consumer, pose, light, WHITE, nx, ny, 0, x0, y0, TAIL, x1, y1, TAIL, x1, y1, SHOULDER, x0, y0, SHOULDER);
            FlatQuads.quad(consumer, pose, light, RED, nx * NOSE_SIDE, ny * NOSE_SIDE, NOSE_AHEAD, x0, y0, SHOULDER, x1, y1, SHOULDER, 0, 0, TIP, 0, 0, TIP);
            FlatQuads.quad(consumer, pose, light, DARK, 0, 0, -1, x0, y0, TAIL, 0, 0, TAIL, x1, y1, TAIL, x1, y1, TAIL);
        }
        FlatQuads.box(consumer, pose, light, DARK, -NOZZLE_RADIUS, -NOZZLE_RADIUS, TAIL - NOZZLE, NOZZLE_RADIUS, NOZZLE_RADIUS, TAIL);
    }

    /** Its four fins, swept back at its tail, square to one another. */
    private static void fins(VertexConsumer consumer, PoseStack.Pose pose, int light) {
        for (int fin = 0; fin < 4; fin++) {
            double angle = fin * Math.PI / 2 + Math.PI / 4;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            float out = BODY + FIN_OUT;
            FlatQuads.quad(consumer, pose, light, RED, -sin, cos, 0, BODY * cos, BODY * sin, FIN_ROOT_FRONT, out * cos, out * sin, FIN_TIP_FRONT,
                out * cos, out * sin, FIN_TIP_BACK, BODY * cos, BODY * sin, TAIL);
        }
    }

    /**
     * Its flame out of its nozzle, so long and so wide there, two crossed tapering blades glowing in a colour that
     * fades to nothing at their tips; each blade drawn from both sides, as the glow is drawn one-sided.
     */
    private static void flame(VertexConsumer consumer, PoseStack.Pose pose, float length, float wide, int colour) {
        float from = TAIL - NOZZLE;
        float to = from - length;
        for (int side = -1; side <= 1; side += 2) {
            blade(consumer, pose, -wide * side, 0, wide * side, 0, from, to, colour);
            blade(consumer, pose, 0, -wide * side, 0, wide * side, from, to, colour);
        }
    }

    /** One side of a blade of flame, from one edge of the nozzle to the other and back to its tip. */
    private static void blade(VertexConsumer consumer, PoseStack.Pose pose, float x0, float y0, float x1, float y1, float from, float to,
        int colour) {
        int faded = colour & 0x00FFFFFF;
        consumer.addVertex(pose, x0, y0, from).setColor(colour);
        consumer.addVertex(pose, x1, y1, from).setColor(colour);
        consumer.addVertex(pose, 0, 0, to).setColor(faded);
        consumer.addVertex(pose, 0, 0, to).setColor(faded);
    }

    @Override
    public ResourceLocation getTextureLocation(CannonRocket rocket) {
        return FlatQuads.WHITE;
    }
}
