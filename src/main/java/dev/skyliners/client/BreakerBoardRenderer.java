package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.skyliners.block.LedPanelBlockEntity;
import dev.skyliners.block.LedSetting;
import dev.skyliners.engineering.BreakerBoardBlock;
import dev.skyliners.engineering.BreakerBoardBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;

/**
 * Draws the breaker board's six switches on its face (see {@link BreakerBoardBlock}): for each of her rooms a dark
 * plate under a band of the room's LED colour, as its panel has it, a chunky lever thrown up while the room's strips
 * are lit and down while they are out, and a round lamp glowing the room's colour while lit, dark while out. A switch
 * whose room has no panel stays grey. Left out while out of sight.
 */
public class BreakerBoardRenderer implements BlockEntityRenderer<BreakerBoardBlockEntity> {
    /** How far out from the wall the board's face is, in blocks. */
    private static final float FACE = (float) BreakerBoardBlock.DEPTH / 16;
    /** A switch's plate, its colour band across its top and its lamp, as shares of the switch's cell, and how proud. */
    private static final float PLATE_IN = 0.08F;
    private static final float BAND = 0.18F;
    private static final float LAMP = 0.16F;
    private static final float PROUD = 0.01F;
    /** A switch's lever: how wide and long it is, as shares of the cell, how far it stands out, in blocks. */
    private static final float LEVER_WIDE = 0.3F;
    private static final float LEVER_LONG = 0.32F;
    private static final float LEVER_OUT = 0.06F;
    /** The plate's, the unlit lamp's and the lever's colours, and a switch's colour with no room to set. */
    private static final int PLATE = 0xFF23262B;
    private static final int LAMP_OFF = 0xFF3A3D42;
    private static final int LEVER = 0xFFC9CED6;
    private static final int NO_ROOM = 0xFF6E737A;
    /** How far round its middle it reaches, for leaving it out unseen, in blocks. */
    private static final float REACH = 0.8F;

    @Override
    public void render(BreakerBoardBlockEntity board, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(board.getBlockState().getBlock() instanceof BreakerBoardBlock) || !Sight.ahead(pose.last(), 0.5F, 0.5F, 0.5F, REACH)) {
            return;
        }
        Direction facing = board.getBlockState().getValue(BreakerBoardBlock.FACING);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
        pose.pushPose();
        pose.rotateAround(Axis.YP.rotationDegrees(180 - facing.toYRot()), 0.5F, 0, 0.5F);
        float width = (float) (1 - 2 * BreakerBoardBlock.SIDE_IN) / BreakerBoardBlock.ACROSS;
        float height = (float) (BreakerBoardBlock.TOP - BreakerBoardBlock.FOOT) / BreakerBoardBlock.DOWN;
        for (int number = 0; number < BreakerBoardBlock.ACROSS * BreakerBoardBlock.DOWN; number++) {
            float left = (float) BreakerBoardBlock.SIDE_IN + number % BreakerBoardBlock.ACROSS * width;
            float top = (float) BreakerBoardBlock.TOP - number / BreakerBoardBlock.ACROSS * height;
            switchAt(pose, consumer, light, board.panel(number), left, top, width, height);
        }
        pose.popPose();
    }

    /**
     * A switch in its cell, from its left and its top as shares of the face (drawn as for a board facing north, so the
     * viewer's left is its east side), its room's panel saying its colour and whether it is on.
     */
    private static void switchAt(PoseStack pose, VertexConsumer consumer, int light, LedPanelBlockEntity panel, float left, float top, float width,
        float height) {
        LedSetting setting = panel == null ? null : panel.setting();
        boolean on = setting != null && setting.brightness() > 0;
        int colour = setting == null ? NO_ROOM : 0xFF000000 | setting.colour().rgb();
        float x0 = 1 - (left + width) + PLATE_IN * width;
        float x1 = 1 - left - PLATE_IN * width;
        float y0 = top - height + PLATE_IN * height;
        float y1 = top - PLATE_IN * height;
        float z = 1 - FACE;
        PoseStack.Pose at = pose.last();
        FlatQuads.box(consumer, at, light, PLATE, x0, y0, z - PROUD, x1, y1, z);
        FlatQuads.box(consumer, at, light, colour, x0, y1 - BAND * height, z - 2 * PROUD, x1, y1, z - PROUD);
        float middle = (x0 + x1) / 2;
        float lampHalf = LAMP * width / 2;
        float lampY = y0 + lampHalf + PLATE_IN * height;
        FlatQuads.box(consumer, at, on ? LightTexture.FULL_BRIGHT : light, on ? colour : LAMP_OFF, middle - lampHalf, lampY - lampHalf, z - 2 * PROUD,
            middle + lampHalf, lampY + lampHalf, z - PROUD);
        float leverMiddle = (y0 + y1 - BAND * height) / 2 + LAMP * height / 2;
        float leverHalf = LEVER_WIDE * width / 2;
        float reach = LEVER_LONG * height;
        float from = on ? leverMiddle : leverMiddle - reach;
        float to = on ? leverMiddle + reach : leverMiddle;
        FlatQuads.box(consumer, at, light, LEVER, middle - leverHalf, from, z - LEVER_OUT, middle + leverHalf, to, z - PROUD);
    }
}
