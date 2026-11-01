package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skyliners.entity.LiftCar;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a ship's lift car (see {@link LiftCar}): a steel platform filling the glass shaft, its floor ribbed against
 * slipping, a slim post at each corner up to a frame round its roof, a handrail across its back and a lit panel in its
 * ceiling, so it reads as a car riding the shaft as it passes each story's glass. It is drawn in the ship's frame, as
 * Sable draws what lives among her blocks.
 */
public class LiftCarRenderer extends EntityRenderer<LiftCar> {
    /** Half the car's width, the floor's thickness and the car's height inside, in blocks. */
    private static final float HALF = 1.45F;
    private static final float FLOOR = 0.15F;
    private static final float HEIGHT = 2.95F;
    /** The posts' and frame's half thickness, the handrail's height and its half thickness, in blocks. */
    private static final float POST = 0.06F;
    private static final float RAIL_UP = 1.0F;
    private static final float RAIL = 0.04F;
    /** The ribs across its floor: how many and how wide each is, in blocks. */
    private static final int RIBS = 7;
    private static final float RIB = 0.08F;
    /** Half the ceiling light's width, in blocks. */
    private static final float LIGHT_HALF = 0.7F;
    private static final int FLOOR_STEEL = 0xFF3A3F46;
    private static final int RIB_STEEL = 0xFF5A6068;
    private static final int POST_STEEL = 0xFF9AA3AD;
    private static final int CEILING = 0xFF2A2E33;
    private static final int LAMP = 0xFFFFF4E0;
    /** How far off the camera the car is drawn, in blocks: as far as the shaft's glass is seen. */
    private static final double SEEN = 96;

    public LiftCarRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(LiftCar car, Frustum frustum, double cameraX, double cameraY, double cameraZ) {
        return car.distanceToSqr(cameraX, cameraY, cameraZ) < SEEN * SEEN;
    }

    @Override
    public void render(LiftCar car, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
        PoseStack.Pose last = pose.last();
        FlatQuads.box(consumer, last, light, FLOOR_STEEL, -HALF, -FLOOR, -HALF, HALF, 0, HALF);
        float step = 2 * HALF / RIBS;
        for (int rib = 0; rib < RIBS; rib++) {
            float at = -HALF + step * (rib + 0.5F);
            FlatQuads.box(consumer, last, light, RIB_STEEL, -HALF + POST, 0, at - RIB / 2, HALF - POST, 0.02F, at + RIB / 2);
        }
        for (int corner = 0; corner < 4; corner++) {
            float x = (corner & 1) == 0 ? -HALF + POST : HALF - POST;
            float z = (corner & 2) == 0 ? -HALF + POST : HALF - POST;
            FlatQuads.box(consumer, last, light, POST_STEEL, x - POST, 0, z - POST, x + POST, HEIGHT, z + POST);
        }
        roof(consumer, last, light);
        FlatQuads.box(consumer, last, light, POST_STEEL, -HALF + POST, RAIL_UP - RAIL, HALF - 3 * POST, HALF - POST, RAIL_UP + RAIL, HALF - POST);
        FlatQuads.box(consumer, last, LightTexture.FULL_BRIGHT, LAMP, -LIGHT_HALF, HEIGHT - 0.06F, -LIGHT_HALF, LIGHT_HALF, HEIGHT - 0.04F, LIGHT_HALF);
        super.render(car, yaw, partialTick, pose, buffers, light);
    }

    /** The frame round the car's roof and the ceiling inside it. */
    private static void roof(VertexConsumer consumer, PoseStack.Pose last, int light) {
        FlatQuads.box(consumer, last, light, POST_STEEL, -HALF, HEIGHT, -HALF, HALF, HEIGHT + 2 * POST, -HALF + 2 * POST);
        FlatQuads.box(consumer, last, light, POST_STEEL, -HALF, HEIGHT, HALF - 2 * POST, HALF, HEIGHT + 2 * POST, HALF);
        FlatQuads.box(consumer, last, light, POST_STEEL, -HALF, HEIGHT, -HALF, -HALF + 2 * POST, HEIGHT + 2 * POST, HALF);
        FlatQuads.box(consumer, last, light, POST_STEEL, HALF - 2 * POST, HEIGHT, -HALF, HALF, HEIGHT + 2 * POST, HALF);
        FlatQuads.box(consumer, last, light, CEILING, -HALF + 2 * POST, HEIGHT - 0.04F, -HALF + 2 * POST, HALF - 2 * POST, HEIGHT, HALF - 2 * POST);
    }

    @Override
    public ResourceLocation getTextureLocation(LiftCar car) {
        return FlatQuads.WHITE;
    }
}
