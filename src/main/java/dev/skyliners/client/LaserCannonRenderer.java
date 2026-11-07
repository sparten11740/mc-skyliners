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
 * Draws the freighter's laser cannon's swivel on its pedestal (see {@link LaserCannonBlockEntity}), out of plain
 * coloured boxes: a yoke turning round to its aim, and between its cheeks a housing tilting up and down with it, its
 * twin barrels reaching out ahead with their muzzles glowing red-hot. Outside its cheeks, on stubs through them and
 * tilting with the housing, its two rocket pods, dark boxes banded in hazard yellow, each with two tubes: the red nose
 * of a rocket shows in each loaded tube, and a spent one is a dark hole, so the pods show at a glance how many rockets
 * are left. Its pedestal is its block's own model.
 */
public class LaserCannonRenderer implements BlockEntityRenderer<LaserCannonBlockEntity> {
    /**
     * The yoke: how far out its cheeks stand either side and how thick they are, and how high its base is, in blocks:
     * on the top of the pedestal. The base between the cheeks is narrower than they are deep, so no side of it lies in
     * the plane of theirs.
     */
    private static final float CHEEK_OUT = 0.42F;
    private static final float CHEEK = 0.07F;
    private static final float YOKE_FOOT = 1.0F;
    /**
     * The housing between the cheeks: its half width, half height and half length, in blocks; a little wider than its
     * barrels reach out, so their outer sides never lie in the plane of its own and flicker against them.
     */
    private static final float HOUSING_HALF_WIDTH = 0.39F;
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
    /**
     * Its rocket pods: their half width and half height, and how far back of the pivot they reach, in blocks; the
     * hazard band round each, where along it and how far proud of it; the stub each hangs on, how thick, from inside
     * its cheek to inside its pod.
     */
    private static final float POD_HALF_WIDTH = 0.12F;
    private static final float POD_HALF_HEIGHT = 0.2F;
    private static final float POD_BACK = -0.35F;
    private static final float BAND_FROM = 0.36F;
    private static final float BAND_TO = 0.44F;
    private static final float BAND_PROUD = 0.006F;
    private static final float STUB_HALF = 0.05F;
    private static final float STUB_IN = CHEEK_OUT;
    /**
     * Its tubes: the half width of each dark mouth, and how far proud of the pod's face it is drawn, so the two never
     * flicker; a loaded tube's rocket nose, its half width at the mouth and how far it stands out, in blocks.
     */
    private static final float TUBE_HALF = 0.075F;
    private static final float TUBE_PROUD = 0.002F;
    private static final float NOSE_HALF = 0.06F;
    private static final float NOSE_OUT = 0.11F;
    private static final int POD = 0xFF363A40;
    private static final int HAZARD = 0xFFE8B425;
    private static final int TUBE = 0xFF0E0F11;
    private static final int NOSE = 0xFFD8362A;

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
        FlatQuads.box(consumer, pose.last(), light, GUNMETAL, -CHEEK_OUT, foot, -CHEEK, CHEEK_OUT, foot + CHEEK, CHEEK);
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
        if (cannon.pods()) {
            pods(consumer, pose.last(), light);
            tubes(consumer, pose.last(), light, cannon);
        }
        pose.popPose();
    }

    /** Its two rocket pods, either side of its housing on their stubs, banded in hazard yellow near their mouths. */
    private static void pods(VertexConsumer consumer, PoseStack.Pose pose, int light) {
        float mouth = (float) LaserCannonBlockEntity.POD_MOUTH;
        for (int side = -1; side <= 1; side += 2) {
            float middle = side * (float) LaserCannonBlockEntity.POD_OUT;
            float inner = middle - side * POD_HALF_WIDTH;
            FlatQuads.box(consumer, pose, light, POD, middle - POD_HALF_WIDTH, -POD_HALF_HEIGHT, POD_BACK, middle + POD_HALF_WIDTH, POD_HALF_HEIGHT,
                mouth);
            FlatQuads.box(consumer, pose, light, HAZARD, middle - POD_HALF_WIDTH - BAND_PROUD, -POD_HALF_HEIGHT - BAND_PROUD, BAND_FROM,
                middle + POD_HALF_WIDTH + BAND_PROUD, POD_HALF_HEIGHT + BAND_PROUD, BAND_TO);
            FlatQuads.box(consumer, pose, light, GUNMETAL, Math.min(side * STUB_IN, inner), -STUB_HALF, -STUB_HALF, Math.max(side * STUB_IN, inner),
                STUB_HALF, STUB_HALF);
        }
    }

    /** The mouths of its pods' tubes, dark, and a rocket's red nose standing out of each loaded one. */
    private static void tubes(VertexConsumer consumer, PoseStack.Pose pose, int light, LaserCannonBlockEntity cannon) {
        float mouth = (float) LaserCannonBlockEntity.POD_MOUTH;
        float face = mouth + TUBE_PROUD;
        for (int tube = 0; tube < LaserCannonBlockEntity.ROCKETS; tube++) {
            float x = (float) LaserCannonBlockEntity.tubeAcross(tube);
            float y = (float) LaserCannonBlockEntity.tubeUp(tube);
            FlatQuads.quad(consumer, pose, light, TUBE, 0, 0, 1, x - TUBE_HALF, y - TUBE_HALF, face, x + TUBE_HALF, y - TUBE_HALF, face,
                x + TUBE_HALF, y + TUBE_HALF, face, x - TUBE_HALF, y + TUBE_HALF, face);
            if (cannon.tubeLoaded(tube)) {
                nose(consumer, pose, light, x, y, face);
            }
        }
    }

    /** A rocket's red nose standing out of a tube's mouth, a little pyramid. */
    private static void nose(VertexConsumer consumer, PoseStack.Pose pose, int light, float x, float y, float mouth) {
        float tip = mouth + NOSE_OUT;
        float left = x - NOSE_HALF;
        float right = x + NOSE_HALF;
        float low = y - NOSE_HALF;
        float high = y + NOSE_HALF;
        FlatQuads.quad(consumer, pose, light, NOSE, 0, -1, 0, left, low, mouth, right, low, mouth, x, y, tip, x, y, tip);
        FlatQuads.quad(consumer, pose, light, NOSE, 1, 0, 0, right, low, mouth, right, high, mouth, x, y, tip, x, y, tip);
        FlatQuads.quad(consumer, pose, light, NOSE, 0, 1, 0, right, high, mouth, left, high, mouth, x, y, tip, x, y, tip);
        FlatQuads.quad(consumer, pose, light, NOSE, -1, 0, 0, left, high, mouth, left, low, mouth, x, y, tip, x, y, tip);
    }

    @Override
    public AABB getRenderBoundingBox(LaserCannonBlockEntity cannon) {
        return new AABB(cannon.getBlockPos()).inflate(LaserCannonBlockEntity.BARREL + 1);
    }
}
