package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.entity.FallingCrate;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/** A care package on its way down: the crate itself, and over it, once open, its parachute swaying a little. */
public class FallingCrateRenderer extends EntityRenderer<FallingCrate> {
    /** Where the cords meet the crate: its top edges. */
    private static final float ANCHOR_X = 0.45F;
    private static final float ANCHOR_Y = 0;

    private final BlockRenderDispatcher blocks;

    public FallingCrateRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.5F;
    }

    @Override
    public void render(FallingCrate crate, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(-0.5, 0, -0.5);
        blocks.renderSingleBlock(AerolargeRegistry.CARGO_CRATE.get().defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        if (crate.chuteOpen()) {
            float age = crate.tickCount + partialTick;
            pose.pushPose();
            // Onto the crate's top, y down as the canopy is drawn, swaying slowly.
            pose.translate(0, 1, 0);
            pose.scale(1, -1, 1);
            pose.mulPose(Axis.ZP.rotationDegrees(4 * Mth.sin(age * 0.06F)));
            pose.mulPose(Axis.XP.rotationDegrees(3 * Mth.sin(age * 0.045F + 1)));
            Canopy.draw(buffers.getBuffer(RenderType.entityCutoutNoCull(Canopy.TEXTURE)), pose.last(), light, ANCHOR_X, ANCHOR_Y);
            pose.popPose();
        }
        super.render(crate, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(FallingCrate crate) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
