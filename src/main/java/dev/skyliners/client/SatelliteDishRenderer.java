package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skyliners.block.Mount;
import dev.skyliners.exterior.HullFittingBlock;
import dev.skyliners.exterior.SatelliteDishBlock;
import dev.skyliners.exterior.SatelliteDishBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws a satellite dish's dish over its mount (see {@link SatelliteDishBlock}), its own model turned about where its
 * mount holds it and drawn as big as the block says: on a floor turning round and round, tipped up at the sky; under a
 * ceiling turning round, tipped down at the ground; on a wall looking out from it, tipped up a little, sweeping from
 * side to side, by the game's time, as it is solid (see {@link SatelliteDishBlock#pose}). Left out while out of sight.
 */
public class SatelliteDishRenderer implements BlockEntityRenderer<SatelliteDishBlockEntity> {
    /** How far round where it is held the small dish reaches, turned any way, in blocks, for leaving it out unseen. */
    private static final float REACH = 1.2F;
    /** How far off a dish is drawn, in blocks: from across a parked freighter and beyond. */
    private static final int VIEW_DISTANCE = 160;

    private final BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();

    @Override
    public void render(SatelliteDishBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = entity.getBlockState();
        if (!(state.getBlock() instanceof SatelliteDishBlock block)) {
            return;
        }
        Mount mount = state.getValue(HullFittingBlock.MOUNT);
        float[] pivot = block.pivot(mount);
        if (!Sight.ahead(pose.last(), 0.5F, pivot[1] / 16, 0.5F, REACH * block.dish().scale())) {
            return;
        }
        double time = SatelliteDishBlock.time(entity.getBlockPos(), DrawingClock.gameTime(partialTick));
        pose.pushPose();
        pose.mulPose(block.pose(mount, state.getValue(HullFittingBlock.FACING), block.turn(mount, time)));
        blocks.renderSingleBlock(state.setValue(SatelliteDishBlock.PART, SatelliteDishBlock.Part.DISH), pose, buffers, light, overlay,
            ModelData.EMPTY, null);
        pose.popPose();
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(SatelliteDishBlockEntity entity) {
        return new AABB(entity.getBlockPos()).inflate(2);
    }
}
