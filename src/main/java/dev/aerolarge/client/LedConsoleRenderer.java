package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.aerolarge.block.LedConsoleBlock;
import dev.aerolarge.block.LedConsoleBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Draws the rocket's cockpit LED console (see {@link LedConsoleBlock}) over the whole of its face (see
 * {@link WideScreenFace}), as {@link LedControlsArt} lays it out: her silhouette with each story's band glowing as its
 * strips do, read from each story's panel, the whole-rocket button, and the controls for what is picked, the tile the
 * crosshair rests on lit up as on every touch panel (see {@link TouchPanelArt}).
 */
public class LedConsoleRenderer implements BlockEntityRenderer<LedConsoleBlockEntity> {
    @Override
    public void render(LedConsoleBlockEntity console, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = console.getLevel();
        if (level == null) {
            return;
        }
        pose.pushPose();
        WideScreenFace.onto(pose, console.getBlockState());
        LedControlsArt.console(pose.last().pose(), ScreenDrawing.shapes(buffers), console, TouchPanelArt.lookedAt(console),
            level.getGameTime() + partialTick);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(LedConsoleBlockEntity console) {
        return true;
    }

    /** All of it, from the part holding it. */
    @Override
    public AABB getRenderBoundingBox(LedConsoleBlockEntity console) {
        return new AABB(console.getBlockPos()).inflate(LedConsoleBlock.CONSOLE_COLUMNS);
    }
}
