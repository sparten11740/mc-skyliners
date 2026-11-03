package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skyliners.block.WideScreenBlock;
import dev.skyliners.block.WindowDimmerBlock;
import dev.skyliners.block.WindowDimmerBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a window dimmer (see {@link WindowDimmerBlock}) over its face (see {@link WideScreenFace}), its four oval
 * windows as the cockpit's LED console draws its own (see {@link LedControlsArt#windowDimmer}), the one the crosshair
 * rests on lit up as on every touch panel (see {@link TouchPanelArt}). Only drawn with the camera in front of it, as
 * its face is seen from the front alone.
 */
public class WindowDimmerRenderer implements BlockEntityRenderer<WindowDimmerBlockEntity> {
    /**
     * How far in front of the middle of its block the camera must be, toward the way it faces, for its face to show, in
     * blocks: upright, the front of the slim screen; tilted under a ceiling, its face looks down past the block's back.
     */
    private static final double UPRIGHT_FACE = -0.5 + WideScreenBlock.DEPTH / 16.0;
    private static final double TILTED_FACE = -0.5;

    @Override
    public boolean shouldRender(WindowDimmerBlockEntity dimmer, Vec3 camera) {
        BlockState state = dimmer.getBlockState();
        if (!BlockEntityRenderer.super.shouldRender(dimmer, camera) || !(state.getBlock() instanceof WindowDimmerBlock)) {
            return false;
        }
        double face = state.getValue(WideScreenBlock.TILTED) ? TILTED_FACE : UPRIGHT_FACE;
        return Sight.facing(dimmer.getBlockPos(), state.getValue(WideScreenBlock.FACING), face, camera, getViewDistance());
    }

    @Override
    public void render(WindowDimmerBlockEntity dimmer, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (dimmer.getLevel() == null || !(dimmer.getBlockState().getBlock() instanceof WindowDimmerBlock)) {
            return;
        }
        pose.pushPose();
        WideScreenFace.onto(pose, dimmer.getBlockState());
        LedControlsArt.windowDimmer(pose.last().pose(), ScreenDrawing.shapes(buffers), dimmer.windows(), TouchPanelArt.lookedAt(dimmer));
        pose.popPose();
    }
}
