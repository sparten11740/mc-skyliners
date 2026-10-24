package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.aerolarge.block.LaserTurretBlock;
import dev.aerolarge.block.LaserTurretBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws the laser path clearer's turret out of its hatch under the nose (see {@link LaserTurretBlockEntity}): lowered
 * out of the block as it is armed, pointing ahead, and raised back in as it is disarmed. Folded in, nothing is drawn:
 * the hatch is the block's own model.
 */
public class LaserTurretRenderer implements BlockEntityRenderer<LaserTurretBlockEntity> {
    /** How far from the camera the turret is drawn, in blocks: from out on the apron. */
    private static final int VIEW_DISTANCE = 128;

    @Override
    public void render(LaserTurretBlockEntity turret, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int overlay) {
        Level level = turret.getLevel();
        float out = turret.deployed(partialTick);
        if (level == null || out <= 0) {
            return;
        }
        pose.pushPose();
        pose.translate(0, -out, 0);
        int light = LevelRenderer.getLightColor(level, turret.getBlockPos().below());
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(turret.getBlockState().setValue(LaserTurretBlock.GUN, true), pose, buffers,
            light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();
    }

    /** Drawn whether or not the turret's section is in view: the block is in the hull's skin, the turret out under it. */
    @Override
    public boolean shouldRenderOffScreen(LaserTurretBlockEntity turret) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(LaserTurretBlockEntity turret) {
        return new AABB(turret.getBlockPos()).expandTowards(0, -1, 0).inflate(0.5);
    }
}
