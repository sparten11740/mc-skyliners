package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skyliners.Skyliners;
import dev.skyliners.block.NavLightBlock;
import dev.skyliners.block.NavLightBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * Draws a light's glow: a soft flare facing the camera, just off the lens, added onto whatever is behind it at full
 * brightness, so it reads at night from far off: from as far as a zeppelin's whole length, so its lights are seen
 * end to end, and never smaller to the eye than a point of light, as a real light far off is. Flashing lights follow
 * the game clock, the same on every client.
 */
public class NavLightRenderer implements BlockEntityRenderer<NavLightBlockEntity> {
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/misc/nav_glow.png");
    /** The biggest a light's flare is drawn as near, in blocks: the festive star's. */
    private static final float BIGGEST = 6F;
    /** How far off a light is drawn, in blocks: past a zeppelin's length, about 490. */
    private static final int VIEW_DISTANCE = 512;
    /**
     * The least a flare spans as seen, as its size over its distance from the camera: about half a degree, a bright
     * point at any distance.
     */
    private static final float LEAST_SPAN = 0.009F;
    /** Where the light is from the camera, worked out afresh for each light on the render thread. */
    private final Vector3f fromCamera = new Vector3f();

    @Override
    public void render(NavLightBlockEntity light, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int overlay) {
        if (light.getLevel() == null) {
            return;
        }
        NavLightBlock.Kind kind = light.getBlockState().getValue(NavLightBlock.KIND);
        float brightness = kind.brightness((float) DrawingClock.ticks(partialTick));
        if (brightness <= 0) {
            return;
        }
        Direction facing = light.getBlockState().getValue(NavLightBlock.FACING);
        pose.pushPose();
        pose.translate(0.5 + facing.getStepX() * 0.3, 0.5 + facing.getStepY() * 0.3, 0.5 + facing.getStepZ() * 0.3);
        // The pose is the camera's, moved to the light, so how far it has moved is how far the light is.
        float distance = pose.last().pose().getTranslation(fromCamera).length();
        float size = Math.max(kind.size(), distance * LEAST_SPAN);
        int colour = kind.colour();
        float r = (colour >> 16 & 0xFF) / 255F * brightness;
        float g = (colour >> 8 & 0xFF) / 255F * brightness;
        float b = (colour & 0xFF) / 255F * brightness;
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
        return VIEW_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(NavLightBlockEntity light) {
        return new AABB(light.getBlockPos()).inflate(Math.max(BIGGEST, VIEW_DISTANCE * LEAST_SPAN));
    }
}
