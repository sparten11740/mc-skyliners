package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skyliners.entity.Basketball;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Quaternionf;

/**
 * The basketball, drawn as a thrown item is: its item's picture, turned to face the camera. A ball among an assembled
 * airliner's blocks is drawn by Sable turned with the airliner, so the airliner's turn is taken off it again first, or
 * the picture would be seen edge on once the airliner has turned.
 */
public class BasketballRenderer extends EntityRenderer<Basketball> {
    /** How big the ball is drawn, as a share of an item's picture. */
    private static final float SIZE = 1.4F;
    /** Closer to the camera than this, in blocks, a ball is not drawn for its first two ticks: still in the hand. */
    private static final double IN_HAND = 3.5;

    private final ItemRenderer items;

    public BasketballRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(Basketball ball, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Entity camera = entityRenderDispatcher.camera.getEntity();
        if (ball.tickCount < 2 && camera.position().distanceToSqr(SableClient.toWorld(ball.level(), ball.position())) < IN_HAND * IN_HAND) {
            return;
        }
        pose.pushPose();
        pose.scale(SIZE, SIZE, SIZE);
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
    public ResourceLocation getTextureLocation(Basketball ball) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
