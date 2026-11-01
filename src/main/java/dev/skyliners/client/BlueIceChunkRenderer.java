package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.entity.BlueIceChunk;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.model.data.ModelData;

/** A chunk of blue ice falling: the lump of lav ice it lands as, tumbling as it goes. */
public class BlueIceChunkRenderer extends EntityRenderer<BlueIceChunk> {
    /** How fast it tumbles, in degrees a tick, about the upright and about a level axis. */
    private static final float SPIN = 17;
    private static final float TUMBLE = 11;
    /** The lump's middle in its block model, in blocks above the block's floor: what it tumbles about. */
    private static final float LUMP_MIDDLE = 0.25F;

    private final BlockRenderDispatcher blocks;

    public BlueIceChunkRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.3F;
    }

    @Override
    public void render(BlueIceChunk chunk, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float age = chunk.tickCount + partialTick;
        pose.pushPose();
        pose.translate(0, chunk.getBbHeight() / 2, 0);
        pose.mulPose(Axis.YP.rotationDegrees(age * SPIN));
        pose.mulPose(Axis.XP.rotationDegrees(age * TUMBLE));
        pose.translate(-0.5, -LUMP_MIDDLE, -0.5);
        blocks.renderSingleBlock(SkylinersRegistry.LAV_ICE.get().defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();
        super.render(chunk, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(BlueIceChunk chunk) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
