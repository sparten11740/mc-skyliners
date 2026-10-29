package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.block.SelfDestructButtonBlock;
import dev.aerolarge.block.SelfDestructButtonBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The armed self-destruct button's flashing: a red flare over the lit button, on a wall or on its pedestal (see
 * {@link SelfDestructButtonBlock#flare}), facing the camera and added onto whatever is behind it at full brightness,
 * pulsing faster and faster as the time runs out, in step with the countdown on the screens of everyone aboard (see
 * {@link SelfDestructWarning#glow}). The button's cover and its steady glow are the block's own model.
 */
public class SelfDestructButtonRenderer implements BlockEntityRenderer<SelfDestructButtonBlockEntity> {
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/misc/nav_glow.png");
    /** The flare's size, in blocks, at its brightest. */
    private static final float SIZE = 1.4F;
    /** The flare's colour: the button's red. */
    private static final float RED = 1;
    private static final float GREEN = 0.15F;
    private static final float BLUE = 0.1F;

    @Override
    public void render(SelfDestructButtonBlockEntity button, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int overlay) {
        if (button.getLevel() == null || !button.getBlockState().getValue(SelfDestructButtonBlock.ARMED)) {
            return;
        }
        float left = button.armedUntil() - button.getLevel().getGameTime() - partialTick;
        float brightness = SelfDestructWarning.glow(left);
        if (brightness <= 0) {
            return;
        }
        Vec3 at = SelfDestructButtonBlock.flare(button.getBlockState());
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        PoseStack.Pose last = pose.last();
        VertexConsumer consumer = buffers.getBuffer(RenderType.eyes(GLOW));
        float h = SIZE * (0.5F + 0.5F * brightness) / 2;
        vertex(consumer, last, -h, -h, 0, 1, brightness);
        vertex(consumer, last, h, -h, 1, 1, brightness);
        vertex(consumer, last, h, h, 1, 0, brightness);
        vertex(consumer, last, -h, h, 0, 0, brightness);
        pose.popPose();
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v, float brightness) {
        consumer.addVertex(pose, x, y, 0)
            .setColor(RED * brightness, GREEN * brightness, BLUE * brightness, 1)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(pose, 0, 0, 1);
    }

    @Override
    public AABB getRenderBoundingBox(SelfDestructButtonBlockEntity button) {
        return new AABB(button.getBlockPos()).inflate(SIZE);
    }
}
