package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.CrossbowRackBlock;
import dev.skyliners.block.CrossbowRackBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the laser crossbow lying across its rack's pegs while it is racked (see {@link CrossbowRackBlock}), as its own
 * item is drawn on a wall: its top to the viewer, its stock level across the pegs, its muzzle to the viewer's right and
 * its arms spread up and down the board. Left out while out of sight or taken down.
 */
public class CrossbowRackRenderer implements BlockEntityRenderer<CrossbowRackBlockEntity> {
    /**
     * Where the crossbow lies, as drawn for a rack facing north: the middle of its stock, from the block's corner, up
     * at the pegs and out from the wall so its underside rests on them; and its size.
     */
    private static final float MIDDLE_Y = 0.45F;
    private static final float FROM_WALL = 0.68F;
    private static final float SIZE = 0.75F;
    /** The crossbow as its item is drawn. */
    private final ItemStack crossbow = new ItemStack(SkylinersRegistry.LASER_CROSSBOW.get());

    @Override
    public void render(CrossbowRackBlockEntity rack, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
        int overlay) {
        BlockState state = rack.getBlockState();
        if (!(state.getBlock() instanceof CrossbowRackBlock) || !state.getValue(CrossbowRackBlock.LOADED) || !Sight.ahead(pose.last())) {
            return;
        }
        Direction facing = state.getValue(CrossbowRackBlock.FACING);
        pose.pushPose();
        pose.rotateAround(Axis.YP.rotationDegrees(180 - facing.toYRot()), 0.5F, 0, 0.5F);
        pose.translate(0.5, MIDDLE_Y, FROM_WALL);
        pose.scale(SIZE, SIZE, SIZE);
        Minecraft.getInstance().getItemRenderer().renderStatic(crossbow, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
            rack.getLevel(), 0);
        pose.popPose();
    }
}
