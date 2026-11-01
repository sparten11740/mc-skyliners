package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skyliners.block.LookoutGlassBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;

/**
 * Draws the Lookout's Glass, its block's own model, but not for whoever looks through it: out of their view, the glass
 * never hides the land under the ship it brings near, however steeply they look down past it (see
 * {@link LookoutView}).
 */
public class LookoutGlassRenderer implements BlockEntityRenderer<LookoutGlassBlockEntity> {
    @Override
    public void render(LookoutGlassBlockEntity glass, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (Sight.ahead(pose.last()) && !LookoutView.lookingThrough(glass)) {
            RubberDuckRenderer.draw(Minecraft.getInstance().getBlockRenderer(), glass.getBlockState(), pose, buffers, light, overlay);
        }
    }
}
