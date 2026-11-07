package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.skyliners.turret.LaserCannonBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.phys.AABB;

/**
 * Draws the freighter's laser cannon's swivel over its mount (see {@link LaserCannonBlockEntity}), out of plain
 * coloured boxes: a yoke turning round to its aim, and between its cheeks a housing tilting up with it, its twin
 * barrels reaching out ahead with their muzzles glowing red-hot. Its mount is its block's own model.
 */
public class LaserCannonRenderer implements BlockEntityRenderer<LaserCannonBlockEntity> {
    /**
     * The yoke: how far out its cheeks stand either side and how thick they are, and how high its base is, in blocks.
     */
    private static final float CHEEK_OUT = 0.42F;
    private static final float CHEEK = 0.07F;
    private static final float YOKE_FOOT = 0.36F;
    /** The housing between the cheeks: its half width, half height and half length, in blocks. */
    private static final float HOUSING_HALF_WIDTH = 0.36F;
    private static final float HOUSING_HALF_HEIGHT = 0.17F;
    private static final float HOUSING_HALF_LENGTH = 0.42F;
    /**
     * The barrels: their half thickness and how far back of the pivot they start, and the muzzles' length, in blocks.
     */
    private static final float BARREL_HALF = 0.08F;
    private static final float BARREL_BACK = 0.4F;
    private static final float MUZZLE = 0.15F;
    private static final int GUNMETAL = 0xFF4E535B;
    private static final int GUNMETAL_LIGHT = 0xFF7A808A;
    private static final int MUZZLE_GLOW = 0xFFFF4A30;
    /** Where its yoke's cheeks and its barrels stand, across its middle line, in blocks. */
    private static final float[] CHEEKS = {-CHEEK_OUT, CHEEK_OUT};
    private static final float[] BARRELS = {(float) -LaserCannonBlockEntity.BARRELS_APART, (float) LaserCannonBlockEntity.BARRELS_APART};
    /** How far round its middle the swivel reaches, for leaving it out unseen, in blocks. */
    private static final float REACH = 2.3F;

    @Override
    public void render(LaserCannonBlockEntity cannon, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!Sight.ahead(pose.last(), 0.5F, (float) LaserCannonBlockEntity.PIVOT_UP, 0.5F, REACH)) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
        pose.pushPose();
        pose.translate(0.5, LaserCannonBlockEntity.PIVOT_UP, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-cannon.shownYaw(partialTick)));
        float foot = YOKE_FOOT - (float) LaserCannonBlockEntity.PIVOT_UP;
        for (float side : CHEEKS) {
            FlatQuads.box(consumer, pose.last(), light, GUNMETAL, side - CHEEK, foot, -CHEEK * 2, side + CHEEK, HOUSING_HALF_HEIGHT, CHEEK * 2);
        }
        FlatQuads.box(consumer, pose.last(), light, GUNMETAL, -CHEEK_OUT, foot, -CHEEK * 2, CHEEK_OUT, foot + CHEEK, CHEEK * 2);
        pose.mulPose(Axis.XP.rotationDegrees(-cannon.shownPitch(partialTick)));
        FlatQuads.box(consumer, pose.last(), light, GUNMETAL_LIGHT, -HOUSING_HALF_WIDTH, -HOUSING_HALF_HEIGHT, -HOUSING_HALF_LENGTH,
            HOUSING_HALF_WIDTH, HOUSING_HALF_HEIGHT, HOUSING_HALF_LENGTH);
        float length = (float) LaserCannonBlockEntity.BARREL;
        for (float side : BARRELS) {
            FlatQuads.box(consumer, pose.last(), light, GUNMETAL, side - BARREL_HALF, -BARREL_HALF, -BARREL_BACK, side + BARREL_HALF, BARREL_HALF,
                length - MUZZLE);
            FlatQuads.box(consumer, pose.last(), LightTexture.FULL_BRIGHT, MUZZLE_GLOW, side - BARREL_HALF, -BARREL_HALF, length - MUZZLE,
                side + BARREL_HALF, BARREL_HALF, length);
        }
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(LaserCannonBlockEntity cannon) {
        return new AABB(cannon.getBlockPos()).inflate(LaserCannonBlockEntity.BARREL + 1);
    }
}
