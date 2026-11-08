package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Quaternionf;

/**
 * A ball, the basketball or a bowling ball, drawn as a thrown item is: its item's picture, turned to face the camera.
 * A ball among an assembled ship's blocks is drawn by Sable turned with the ship, so the ship's turn is taken off it
 * again first, or the picture would be seen edge on once she has turned.
 */
public class BallRenderer<T extends Entity & ItemSupplier> extends EntityRenderer<T> {
    /** How big the basketball is drawn, and a bowling ball, as a share of an item's picture, a block across. */
    public static final float BASKETBALL = 1.4F;
    public static final float BOWLING_BALL = 1.2F;
    /** Closer to the camera than this, in blocks, a ball is not drawn for its first two ticks: still in the hand. */
    private static final double IN_HAND = 3.5;

    private final ItemRenderer items;
    /** How big this ball is drawn, as a share of an item's picture. */
    private final float size;

    public BallRenderer(EntityRendererProvider.Context context, float size) {
        super(context);
        this.items = context.getItemRenderer();
        this.size = size;
    }

    @Override
    public void render(T ball, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Entity camera = entityRenderDispatcher.camera.getEntity();
        if (ball.tickCount < 2 && camera.position().distanceToSqr(SableClient.toWorld(ball.level(), ball.position())) < IN_HAND * IN_HAND) {
            return;
        }
        pose.pushPose();
        pose.scale(size, size, size);
        SableClient.Placing placing = SableClient.placing(ball.position(), partialTick);
        if (placing != null) {
            pose.mulPose(new Quaternionf(placing.turn()).conjugate());
        }
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        items.renderStatic(ball.getItem(), ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, pose, buffers, ball.level(), ball.getId());
        pose.popPose();
        super.render(ball, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(T ball) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
