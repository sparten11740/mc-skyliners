package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.skyliners.block.Mount;
import dev.skyliners.exterior.HullFittingBlock;
import dev.skyliners.exterior.SatelliteDishBlock;
import dev.skyliners.exterior.SatelliteDishBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws a satellite dish's dish over its mount (see {@link SatelliteDishBlock}), its own model turned about where its
 * mount holds it and drawn as big as the block says: on a floor turning round and round, tipped up at the sky; under a
 * ceiling turning round, tipped down at the ground; on a wall looking out from it, tipped up a little, sweeping from
 * side to side. Each dish keeps time a little out of step with any other, by where it is, so a ship's dishes never turn
 * as one. Left out while out of sight.
 */
public class SatelliteDishRenderer implements BlockEntityRenderer<SatelliteDishBlockEntity> {
    /** How long a dish on a floor or under a ceiling takes to turn round once, in ticks: slow, tracking. */
    private static final double TURN_TICKS = 600;
    /** How long a dish on a wall takes to sweep to one side and back, in ticks. */
    private static final double SWEEP_TICKS = 360;
    /** How far a dish on a wall is tipped up from looking straight out from it, in degrees. */
    private static final float WALL_TILT = 20;
    /** Where in the dish's own model it is held: the back of its hub, in pixels (see tools/generate_resources.mjs). */
    private static final float[] HELD_AT = {8, 8, 12};
    /** How far round where it is held the small dish reaches, turned any way, in blocks, for leaving it out unseen. */
    private static final float REACH = 1.2F;
    /** How far off a dish is drawn, in blocks: from across a parked freighter and beyond. */
    private static final int VIEW_DISTANCE = 160;
    /** A large odd number spreading the dishes' places in their turn, so neighbours are out of step. */
    private static final long SPREAD = 0x9E3779B97F4A7C15L;

    private final BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();

    @Override
    public void render(SatelliteDishBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = entity.getBlockState();
        if (!(state.getBlock() instanceof SatelliteDishBlock block)) {
            return;
        }
        SatelliteDishBlock.Dish dish = block.dish();
        Mount mount = state.getValue(HullFittingBlock.MOUNT);
        Direction facing = state.getValue(HullFittingBlock.FACING);
        float[] pivot = block.pivot(mount);
        if (!Sight.ahead(pose.last(), 0.5F, pivot[1] / 16, 0.5F, REACH * dish.scale())) {
            return;
        }
        double ticks = DrawingClock.ticks(partialTick) + Math.floorMod(entity.getBlockPos().asLong() * SPREAD, (long) TURN_TICKS);
        pose.pushPose();
        // Turned to its facing about the block's middle, so the pivot drawn facing north lands where its mount has it.
        pose.rotateAround(Axis.YP.rotationDegrees(180 - facing.toYRot()), 0.5F, 0, 0.5F);
        pose.translate(pivot[0] / 16, pivot[1] / 16, pivot[2] / 16);
        pose.mulPose(Axis.YP.rotationDegrees((float) turn(mount, dish, ticks)));
        pose.mulPose(Axis.XP.rotationDegrees(mount == Mount.FLOOR ? dish.tilt() : mount == Mount.WALL ? WALL_TILT : -dish.tilt()));
        pose.scale(dish.scale(), dish.scale(), dish.scale());
        pose.translate(-HELD_AT[0] / 16, -HELD_AT[1] / 16, -HELD_AT[2] / 16);
        blocks.renderSingleBlock(state.setValue(SatelliteDishBlock.PART, SatelliteDishBlock.Part.DISH), pose, buffers, light, overlay,
            ModelData.EMPTY, null);
        pose.popPose();
    }

    /** How far round the dish is turned now, in degrees: round and round, or on a wall sweeping either way. */
    private static double turn(Mount mount, SatelliteDishBlock.Dish dish, double ticks) {
        if (mount == Mount.WALL) {
            return dish.sweep() * Math.sin(2 * Math.PI * (ticks % SWEEP_TICKS) / SWEEP_TICKS);
        }
        return 360 * (ticks % TURN_TICKS) / TURN_TICKS;
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
