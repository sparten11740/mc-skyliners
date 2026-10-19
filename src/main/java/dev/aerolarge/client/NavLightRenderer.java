package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.block.NavLightBlock;
import dev.aerolarge.block.NavLightBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * Draws a light's glow: a soft flare facing the camera, just off the lens, added onto whatever is behind it at full
 * brightness, so it reads at night from far off. Flashing lights follow the game clock, the same on every client.
 */
public class NavLightRenderer implements BlockEntityRenderer<NavLightBlockEntity> {
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/misc/nav_glow.png");
    /** The flare's size in blocks: the steady lights small, the flashing ones big. */
    private static final float STEADY_SIZE = 1.6F;
    private static final float FLASH_SIZE = 3.2F;

    @Override
    public void render(NavLightBlockEntity light, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int overlay) {
        if (light.getLevel() == null) {
            return;
        }
        NavLightBlock.Kind kind = light.getBlockState().getValue(NavLightBlock.KIND);
        float brightness = kind.brightness(light.getLevel().getGameTime() + partialTick);
        if (brightness <= 0) {
            return;
        }
        Direction facing = light.getBlockState().getValue(NavLightBlock.FACING);
        float size = kind == NavLightBlock.Kind.RED || kind == NavLightBlock.Kind.GREEN || kind == NavLightBlock.Kind.WHITE ? STEADY_SIZE : FLASH_SIZE;
        int colour = kind.colour();
        float r = (colour >> 16 & 0xFF) / 255F * brightness;
        float g = (colour >> 8 & 0xFF) / 255F * brightness;
        float b = (colour & 0xFF) / 255F * brightness;

        pose.pushPose();
        pose.translate(0.5 + facing.getStepX() * 0.3, 0.5 + facing.getStepY() * 0.3, 0.5 + facing.getStepZ() * 0.3);
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        PoseStack.Pose last = pose.last();
        VertexConsumer consumer = buffers.getBuffer(RenderType.eyes(GLOW));
        float h = size / 2;
        vertex(consumer, last, -h, -h, 0, 1, r, g, b);
        vertex(consumer, last, h, -h, 1, 1, r, g, b);
        vertex(consumer, last, h, h, 1, 0, r, g, b);
        vertex(consumer, last, -h, h, 0, 0, r, g, b);
        pose.popPose();
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v, float r, float g, float b) {
        consumer.addVertex(pose, x, y, 0)
            .setColor(r, g, b, 1)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(pose, 0, 0, 1);
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(NavLightBlockEntity light) {
        return new AABB(light.getBlockPos()).inflate(FLASH_SIZE);
    }
}
