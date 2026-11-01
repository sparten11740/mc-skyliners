package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skyliners.block.LiftDoorBlock;
import dev.skyliners.block.LiftDoorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws a lift door's pane while it slides open or shut (see {@link LiftDoorBlock#SLIDING}), its block drawn empty
 * meanwhile: the shut door's own model, moved aside toward the shaft's wall as far as the door is open, easing off and
 * in (see {@link LiftDoorBlockEntity#openness}). Standing still, the door is drawn as its block and this draws nothing.
 */
public class LiftDoorRenderer implements BlockEntityRenderer<LiftDoorBlockEntity> {
    /** How far the pane slides aside into the shaft's wall, in blocks, as its open model has it. */
    private static final double SLIDE = 14 / 16.0;

    @Override
    public void render(LiftDoorBlockEntity door, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = door.getBlockState();
        if (!state.getValue(LiftDoorBlock.SLIDING) || !Sight.ahead(pose.last())) {
            return;
        }
        Direction toward = LiftDoorBlock.slidesToward(state.getValue(LiftDoorBlock.FACING));
        double along = SLIDE * door.openness(DrawingClock.gameTime(partialTick));
        BlockState shut = state.setValue(LiftDoorBlock.OPEN, false).setValue(LiftDoorBlock.SLIDING, false);
        pose.pushPose();
        pose.translate(toward.getStepX() * along, 0, toward.getStepZ() * along);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(shut, pose, buffers, light, overlay, ModelData.EMPTY, null);
        pose.popPose();
    }

    /** The pane reaches into the block beside the doorway as it slides. */
    @Override
    public AABB getRenderBoundingBox(LiftDoorBlockEntity door) {
        return new AABB(door.getBlockPos()).inflate(1, 0, 1);
    }
}
