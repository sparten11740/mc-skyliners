package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.entity.Trolley;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** The trolley, drawn as its item's model (a cart with drawers) standing full size, turned the way it rolls. */
public class TrolleyRenderer extends EntityRenderer<Trolley> {
    private final ItemRenderer items;
    private final ItemStack model = new ItemStack(SkylinersRegistry.TROLLEY_ITEM.get());

    public TrolleyRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
        this.shadowRadius = 0.4F;
    }

    @Override
    public void render(Trolley trolley, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        pose.translate(0, 0.5, 0);
        items.renderStatic(model, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose, buffers, trolley.level(), trolley.getId());
        pose.popPose();
        super.render(trolley, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(Trolley trolley) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
