package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.RoverBayBlockEntity;
import dev.aerolarge.block.RoverHatchBlock;
import dev.aerolarge.rover.RoverRoute;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * The rocket's rover bay (see {@link RoverBayBlockEntity}), drawn from the middle of its door's sill out of plain
 * coloured boxes: the door, in her livery outside, folding down outward on its hinge into a platform whose deck, its
 * inner face, is grey ribbed plate edged in yellow and black hazard stripes, held by two stay chains from the
 * doorway's top corners; the rover's cradle inside, its rails, stop bar and glowing charging pad; the light curtain
 * glowing red across the open doorway's sill; the yellow boom sliding out of its housing on the wall over the doorway
 * as the door opens; and, while the rover is not on its hook, the boom's
 * trolley with the hook wound up under it, waiting over the platform or at the boom's tip. Laid out as the bay's route
 * is (see {@link RoverRoute}): out of her along +z, up along +y, from the hinge.
 */
public class RoverBayRenderer implements BlockEntityRenderer<RoverBayBlockEntity> {
    /** The door's half width, in blocks, a little under the doorway's, and how far its hazard stripes reach in. */
    private static final float HALF_WIDTH = 1.45F;
    private static final float STRIPE = 0.2F;
    /** How long each hazard stripe is, and how far apart the deck's ribs are, along the door, in blocks. */
    private static final float STRIPE_LENGTH = 0.25F;
    private static final float RIB_SPACING = 0.4F;
    /** How far the deck plate, its ribs and its stripes stand proud of the door's inner face, in blocks. */
    private static final float PLATE = 0.03F;
    private static final float RIB = 0.05F;
    /** What makes a block's tint, which has no alpha, a colour drawn solid. */
    private static final int OPAQUE = 0xFF000000;
    private static final int DECK = 0xFF4B4F56;
    private static final int RIB_GREY = 0xFF373A40;
    private static final int HAZARD_YELLOW = 0xFFE8B828;
    private static final int HAZARD_BLACK = 0xFF1C1D20;
    /** The stay chains: their half thickness, in blocks, their steel, and how far open they show from, in degrees. */
    private static final float CHAIN_HALF = 0.035F;
    private static final int CHAIN = 0xFF3A3D42;
    private static final double CHAINS_FROM = 2;
    /**
     * The light curtain: its posts' half width and height, the heights of its beams over the sill and their half
     * thickness, how far out of the hinge they stand, in blocks, and their red glow.
     */
    private static final float POST_HALF = 0.05F;
    private static final float POST_HEIGHT = 1.5F;
    private static final float[] BEAMS = {0.45F, 0.9F, 1.35F};
    private static final float BEAM_HALF = 0.015F;
    private static final float CURTAIN_OUT = 0.12F;
    private static final int CURTAIN = 0xFFFF4A30;
    private static final int POST = 0xFF2A2C30;
    /**
     * The boom's half width, in blocks, and its yellow; its housing's half width and how far it reaches over and under
     * the boom, in blocks, and its grey.
     */
    private static final float BOOM_HALF = 0.15F;
    private static final int BOOM = 0xFFE0A820;
    private static final float HOUSING_HALF = 0.45F;
    private static final float HOUSING_MARGIN = 0.1F;
    private static final int HOUSING = 0xFF5A5E66;
    /** The hook wound up under the waiting trolley: how far under its pulley it hangs, and its size, in blocks. */
    private static final float HOOK_BELOW = 0.3F;
    private static final float HOOK_HALF = 0.08F;
    private static final float HOOK_HEIGHT = 0.22F;
    private static final int IRON = 0xFF8A8D91;
    /**
     * The rover's cradle on the bay's floor: its two rails, how far out from its middle they lie either side, their
     * half width and height, and how far they reach ahead of and behind its middle; the stop bar across its front, so
     * far ahead of its middle, and its half depth; and the charging pad between the rails, its half width and half
     * length, and its glowing strip's half width, in blocks.
     */
    private static final float RAIL_OUT = 1.3F;
    private static final float RAIL_HALF = 0.08F;
    private static final float RAIL_HEIGHT = 0.06F;
    private static final float RAIL_REACH = 1.6F;
    private static final float STOP_AHEAD = 1.65F;
    private static final float STOP_HALF = 0.06F;
    private static final float PAD_HALF_WIDTH = 0.45F;
    private static final float PAD_HALF_LENGTH = 0.8F;
    private static final float CHARGE_HALF = 0.08F;
    private static final float PAD_HEIGHT = 0.03F;
    private static final int PAD = 0xFF2A2D33;
    private static final int CHARGE = 0xFF40D0F0;
    /** How far round the door's middle its boom and platform reach, for when it is drawn, in blocks. */
    private static final double REACH = RoverRoute.BOOM_OUT + 1;
    /**
     * How far off the bay is still drawn, in blocks: as far as the rocket itself is, as the plug doors are (see
     * {@code PlugDoorRenderer}), or her shut door would leave a hole in her skin.
     */
    private static final int VIEW_DISTANCE = 1024;

    @Override
    public void render(RoverBayBlockEntity bay, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float open = bay.progress(partialTick);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-bay.facing().toYRot()));
        door(pose, consumer, light, open, skin(bay));
        cradle(pose.last(), consumer, light);
        if (open > 0) {
            curtain(pose.last(), consumer);
        }
        double tip = RoverRoute.boomTip(open);
        boom(pose.last(), consumer, light, tip);
        if (bay.trolley() != RoverBayBlockEntity.Trolley.WITH_ROVER) {
            double waits = bay.trolley() == RoverBayBlockEntity.Trolley.AT_TIP ? RoverRoute.OVERHANG : RoverRoute.ON_PLATFORM;
            trolley(pose.last(), consumer, light, Math.min(waits, tip - RoverRenderer.TROLLEY_HALF));
        }
        pose.popPose();
    }

    /**
     * The colour of her skin over the door, in whatever livery she wears, for the door's outside to match it: the
     * cover's paint just over the doorway, as the client paints her.
     */
    private static int skin(RoverBayBlockEntity bay) {
        BlockPos over = bay.getBlockPos().above(RoverHatchBlock.ROWS);
        return OPAQUE | ZeppelinPaint.tint(AerolargeRegistry.ZEPPELIN_COVER.get().defaultBlockState(), bay.getLevel(), over, 0);
    }

    /** The door, folded so far open on its hinge, its outside in her skin's colour, with its stay chains. */
    private static void door(PoseStack pose, VertexConsumer consumer, int light, float open, int skin) {
        double angle = RoverRoute.doorAngle(open);
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees((float) angle));
        PoseStack.Pose leaf = pose.last();
        float tall = (float) RoverRoute.PLATFORM;
        float inner = (float) -RoverRoute.DECK;
        FlatQuads.box(consumer, leaf, light, skin, -HALF_WIDTH, 0, inner + PLATE, HALF_WIDTH, tall, 0);
        FlatQuads.box(consumer, leaf, light, DECK, -HALF_WIDTH + STRIPE, 0, inner, HALF_WIDTH - STRIPE, tall, inner + PLATE);
        for (float along = RIB_SPACING / 2; along < tall; along += RIB_SPACING) {
            FlatQuads.box(consumer, leaf, light, RIB_GREY, -HALF_WIDTH + STRIPE, along - RIB / 2, inner - RIB, HALF_WIDTH - STRIPE, along + RIB / 2, inner);
        }
        int stripes = Math.round(tall / STRIPE_LENGTH);
        for (int stripe = 0; stripe < stripes; stripe++) {
            int colour = stripe % 2 == 0 ? HAZARD_YELLOW : HAZARD_BLACK;
            float from = stripe * STRIPE_LENGTH;
            FlatQuads.box(consumer, leaf, light, colour, -HALF_WIDTH, from, inner, -HALF_WIDTH + STRIPE, from + STRIPE_LENGTH, inner + PLATE);
            FlatQuads.box(consumer, leaf, light, colour, HALF_WIDTH - STRIPE, from, inner, HALF_WIDTH, from + STRIPE_LENGTH, inner + PLATE);
        }
        pose.popPose();
        if (angle < CHAINS_FROM) {
            return;
        }
        double turn = Math.toRadians(angle);
        Vec3 far = new Vec3(0, tall * Math.cos(turn) - inner * Math.sin(turn), tall * Math.sin(turn) + inner * Math.cos(turn));
        for (float side : new float[]{-HALF_WIDTH + CHAIN_HALF, HALF_WIDTH - CHAIN_HALF}) {
            bar(pose, consumer, light, CHAIN, new Vec3(side, tall, 0), new Vec3(side, far.y, far.z), CHAIN_HALF);
        }
    }

    /**
     * The rover's cradle on the floor inside the door: two yellow rails either side of where its wheels stand, a stop
     * bar in yellow and black across its front, and between the rails a dark charging pad with a cyan strip glowing
     * down its middle.
     */
    private static void cradle(PoseStack.Pose at, VertexConsumer consumer, int light) {
        float middle = (float) -RoverRoute.CRADLE_IN;
        for (float side : new float[]{-RAIL_OUT, RAIL_OUT}) {
            FlatQuads.box(consumer, at, light, HAZARD_YELLOW, side - RAIL_HALF, 0, middle - RAIL_REACH, side + RAIL_HALF, RAIL_HEIGHT, middle + RAIL_REACH);
        }
        int stripes = Math.round(2 * RAIL_OUT / STRIPE_LENGTH);
        for (int stripe = 0; stripe < stripes; stripe++) {
            float from = -RAIL_OUT + stripe * STRIPE_LENGTH;
            FlatQuads.box(consumer, at, light, stripe % 2 == 0 ? HAZARD_YELLOW : HAZARD_BLACK, from, 0, middle + STOP_AHEAD - STOP_HALF,
                from + STRIPE_LENGTH, RAIL_HEIGHT, middle + STOP_AHEAD + STOP_HALF);
        }
        FlatQuads.box(consumer, at, light, PAD, -PAD_HALF_WIDTH, 0, middle - PAD_HALF_LENGTH, PAD_HALF_WIDTH, PAD_HEIGHT, middle + PAD_HALF_LENGTH);
        FlatQuads.box(consumer, at, LightTexture.FULL_BRIGHT, CHARGE, -CHARGE_HALF, PAD_HEIGHT, middle - PAD_HALF_LENGTH + CHARGE_HALF, CHARGE_HALF,
            PAD_HEIGHT + PLATE, middle + PAD_HALF_LENGTH - CHARGE_HALF);
    }

    /** The light curtain across the open doorway's sill: two posts at its jambs and three red beams between them. */
    private static void curtain(PoseStack.Pose at, VertexConsumer consumer) {
        int light = LightTexture.FULL_BRIGHT;
        for (float side : new float[]{-HALF_WIDTH, HALF_WIDTH}) {
            FlatQuads.box(consumer, at, light, POST, side - POST_HALF, 0, CURTAIN_OUT - POST_HALF, side + POST_HALF, POST_HEIGHT, CURTAIN_OUT + POST_HALF);
        }
        for (float height : BEAMS) {
            FlatQuads.box(consumer, at, light, CURTAIN, -HALF_WIDTH, height - BEAM_HALF, CURTAIN_OUT - BEAM_HALF, HALF_WIDTH, height + BEAM_HALF,
                CURTAIN_OUT + BEAM_HALF);
        }
    }

    /** The boom out of its housing over the doorway, so far out from the hinge. */
    private static void boom(PoseStack.Pose at, VertexConsumer consumer, int light, double tip) {
        float bottom = (float) RoverRoute.BOOM_BOTTOM;
        float top = (float) RoverRoute.BOOM_TOP;
        FlatQuads.box(consumer, at, light, HOUSING, -HOUSING_HALF, bottom - HOUSING_MARGIN, (float) RoverRoute.BOOM_ROOT, HOUSING_HALF,
            top + HOUSING_MARGIN, (float) RoverRoute.BOOM_IN);
        if (tip > RoverRoute.BOOM_IN) {
            FlatQuads.box(consumer, at, light, BOOM, -BOOM_HALF, bottom, (float) RoverRoute.BOOM_IN, BOOM_HALF, top, (float) tip);
        }
    }

    /** The trolley waiting so far out along the boom, the hook wound up under it; nothing while it is housed. */
    private static void trolley(PoseStack.Pose at, VertexConsumer consumer, int light, double along) {
        if (along < RoverRoute.BOOM_IN + RoverRenderer.TROLLEY_HALF) {
            return;
        }
        float z = (float) along;
        float pulley = (float) RoverRoute.PULLEY;
        float half = RoverRenderer.TROLLEY_HALF;
        FlatQuads.box(consumer, at, light, RoverRenderer.TROLLEY, -half, pulley, z - half, half, (float) RoverRoute.BOOM_BOTTOM, z + half);
        float hook = pulley - HOOK_BELOW;
        FlatQuads.box(consumer, at, light, IRON, -HOOK_HALF, hook - HOOK_HEIGHT, z - HOOK_HALF, HOOK_HALF, hook, z + HOOK_HALF);
    }

    /** A bar of one colour from one point to another, so many blocks either side of its line. */
    private static void bar(PoseStack pose, VertexConsumer consumer, int light, int colour, Vec3 from, Vec3 to, float half) {
        Vec3 way = to.subtract(from);
        float length = (float) way.length();
        if (length < 1e-3F) {
            return;
        }
        pose.pushPose();
        pose.translate(from.x, from.y, from.z);
        pose.mulPose(new Quaternionf().rotationTo(0, 1, 0, (float) way.x, (float) way.y, (float) way.z));
        FlatQuads.box(consumer, pose.last(), light, colour, -half, 0, -half, half, length, half);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(RoverBayBlockEntity bay) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(RoverBayBlockEntity bay) {
        return new AABB(bay.getBlockPos()).inflate(REACH);
    }
}
