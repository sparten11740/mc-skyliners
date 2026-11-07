package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.skyliners.club.BandstandBlock;
import dev.skyliners.club.BandstandBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Draws the club's band on its bandstand (see {@link BandstandBlock}) out of plain coloured boxes: a jazz trio of
 * robots of the deck robots' own family (see {@code DeckRobotRenderer}), boxy and white on dark chassis, each with a
 * band of its own colour round its body and head and a cyan visor across its face. On the left a teal one plucks an
 * upright bass, in the middle an orange one plays a keyboard on its stand, its hands falling in turn, and on the right
 * a violet one beats a snare drum under a cymbal, its sticks falling in turn. While its song plays for this player (see
 * {@link ClubBand}) they sway and nod to its beat; silent, they stand still. The stage is drawn three blocks wide, its
 * block's own model in the middle, with footlights along its front edge. Drawn facing north, turned the way the stage
 * faces.
 */
public class BandstandRenderer implements BlockEntityRenderer<BandstandBlockEntity> {
    /** How far apart the three players stand across the stage, in blocks, and how high its top is. */
    private static final float APART = 0.95F;
    private static final float STAGE = 3 / 16F;
    /**
     * A player, in blocks: its chassis's half width, half depth and height; its body's half width, half depth and
     * height; its band's height round its body's middle; its head's half width and height, and its neck's height and
     * half width; its visor's height on the head's front, from and to, and how far in from its sides; its arms' and
     * hands' thickness; and how far a band or visor stands proud.
     */
    private static final float CHASSIS_HALF = 0.15F;
    private static final float CHASSIS_DEPTH = 0.12F;
    private static final float CHASSIS = 0.12F;
    private static final float BODY_HALF = 0.17F;
    private static final float BODY_DEPTH = 0.13F;
    private static final float BODY = 0.4F;
    private static final float BAND = 0.07F;
    private static final float HEAD_HALF = 0.14F;
    private static final float HEAD = 0.2F;
    private static final float NECK = 0.05F;
    private static final float NECK_HALF = 0.04F;
    private static final float VISOR_FROM = 0.08F;
    private static final float VISOR_TO = 0.13F;
    private static final float VISOR_IN = 0.02F;
    private static final float ARM = 0.04F;
    private static final float LAYER = 0.01F;
    /** How far the players sway, in degrees, and nod, in blocks, on each beat. */
    private static final float SWAY = 4;
    private static final float NOD = 0.025F;
    private static final int WHITE = 0xFFE8ECEF;
    private static final int CHASSIS_DARK = 0xFF2E3034;
    private static final int TEAL = 0xFF2FA89A;
    private static final int ORANGE = 0xFFE08A2A;
    private static final int VIOLET = 0xFF8A5AC8;
    private static final int VISOR = 0xFF40E0F0;
    /**
     * The upright bass, in blocks: how far to the left player's right and in front of it it stands, its body's half
     * width, half depth and height over the stage, its neck's half width and how far over the body it reaches; how far
     * the plucking hand swings across its strings on the beat, and how far up the body it plucks, as a share.
     */
    private static final float BASS_ACROSS = 0.2F;
    private static final float BASS_OUT = 0.18F;
    private static final float BASS_HALF = 0.13F;
    private static final float BASS_DEPTH = 0.06F;
    private static final float BASS_BODY = 0.42F;
    private static final float BASS_NECK_HALF = 0.025F;
    private static final float BASS_NECK = 0.42F;
    private static final float PLUCK = 0.05F;
    private static final float PLUCKED_AT = 0.75F;
    private static final int BASS_WOOD = 0xFF7A3E1C;
    private static final int BASS_NECK_DARK = 0xFF2A1A10;
    /**
     * The keyboard, in blocks: how far in front of the middle player its middle is, its half width, depth and case's
     * height and how high its top stands over the stage; how high its keys stand on it and how far each hand falls on
     * its beat.
     */
    private static final float KEYS_OUT = 0.24F;
    private static final float KEYS_HALF = 0.32F;
    private static final float KEYS_DEPTH = 0.18F;
    private static final float KEYS_CASE = 0.06F;
    private static final float KEYS_UP = 0.42F;
    private static final float KEY_RISE = 0.015F;
    private static final float KEY_PRESS = 0.03F;
    private static final int KEYS_BLACK = 0xFF1C1D20;
    private static final int KEYS_WHITE = 0xFFF4F4F0;
    /**
     * The snare and cymbal, in blocks: how high the snare's top stands over the stage and how far in front of the right
     * player, half how wide and how deep it is; the cymbal, how far to the player's left and how high, and half how
     * wide; the sticks, how far apart, how far over the snare at rest and how much higher on the beat.
     */
    private static final float SNARE_UP = 0.36F;
    private static final float SNARE_OUT = 0.24F;
    private static final float SNARE_HALF = 0.14F;
    private static final float SNARE_DEPTH = 0.08F;
    private static final float CYMBAL_ACROSS = 0.26F;
    private static final float CYMBAL_UP = 0.62F;
    private static final float CYMBAL_HALF = 0.12F;
    private static final float STICKS_APART = 0.08F;
    private static final float STICK_REST = 0.05F;
    private static final float STICK_BEAT = 0.06F;
    private static final int SNARE = 0xFFC8CDD4;
    private static final int SNARE_RIM = 0xFFB02A2A;
    private static final int CYMBAL = 0xFFD6A93A;
    private static final int STAND = 0xFF3A3D42;
    private static final int STICK = 0xFFD8C8A8;
    /**
     * The stage's wings: how far out from its middle they reach, in blocks, matching its block's dark oak, and its
     * brass edge along their front, how far under and over the stage's top and how deep, as its block's model has it;
     * its footlights along that edge, how many, half their size, how far in from its ends, and their colours, lit and
     * dark.
     */
    private static final float WING = 1.5F;
    private static final float HALF_BLOCK = 0.5F;
    private static final int STAGE_WOOD = 0xFF4A3220;
    private static final int BRASS = 0xFFD6A93A;
    private static final float EDGE_DROP = 0.5F / 16;
    private static final float EDGE_RISE = 0.2F / 16;
    private static final float EDGE_DEPTH = 0.5F / 16;
    private static final int FOOTLIGHTS = 7;
    private static final float FOOTLIGHT_HALF = 0.035F;
    private static final float FOOTLIGHT_IN = 0.12F;
    private static final int FOOTLIGHT_ON = 0xFFFFD27A;
    private static final int FOOTLIGHT_OFF = 0xFF6A5A3A;
    /**
     * How high its middle is over its block's foot, and how far round it the band reaches, for leaving it out unseen.
     */
    private static final float MIDDLE = 0.7F;
    private static final float REACH = 1.8F;

    @Override
    public void render(BandstandBlockEntity stand, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(stand.getBlockState().getBlock() instanceof BandstandBlock) || !Sight.ahead(pose.last(), 0.5F, MIDDLE, 0.5F, REACH)) {
            return;
        }
        Direction facing = stand.getBlockState().getValue(BandstandBlock.FACING);
        float beat = ClubBand.beat(stand, partialTick);
        float swing = beat < 0 ? 0 : Mth.sin(beat * Mth.TWO_PI);
        float nod = beat < 0 ? 0 : NOD * Mth.abs(Mth.sin(beat * Mth.PI));
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
        pose.pushPose();
        pose.rotateAround(Axis.YP.rotationDegrees(180 - facing.toYRot()), 0.5F, 0, 0.5F);
        pose.translate(0.5, STAGE, 0.5);
        wings(pose.last(), consumer, light, beat >= 0);
        player(pose, consumer, light, -APART, TEAL, swing, nod);
        bass(pose.last(), consumer, light, -APART, swing);
        player(pose, consumer, light, 0, ORANGE, -swing, nod);
        keyboard(pose.last(), consumer, light, swing);
        player(pose, consumer, light, APART, VIOLET, swing, nod);
        drums(pose.last(), consumer, light, APART, swing);
        pose.popPose();
    }

    /**
     * The stage's wings either side of its block, as its block's own model is, so all three players stand on it, and
     * its footlights along its front edge, glowing warm while the band plays and dim while it is silent.
     */
    private static void wings(PoseStack.Pose at, VertexConsumer consumer, int light, boolean playing) {
        for (int side = -1; side <= 1; side += 2) {
            float near = Math.min(side * HALF_BLOCK, side * WING);
            float far = Math.max(side * HALF_BLOCK, side * WING);
            FlatQuads.box(consumer, at, light, STAGE_WOOD, near, -STAGE, -HALF_BLOCK, far, 0, HALF_BLOCK);
            FlatQuads.box(consumer, at, light, BRASS, near, -EDGE_DROP, -HALF_BLOCK - LAYER, far, EDGE_RISE, EDGE_DEPTH - HALF_BLOCK);
        }
        int glow = playing ? LightTexture.FULL_BRIGHT : light;
        int colour = playing ? FOOTLIGHT_ON : FOOTLIGHT_OFF;
        float front = -HALF_BLOCK + LAYER;
        for (int lamp = 0; lamp < FOOTLIGHTS; lamp++) {
            float x = -WING + FOOTLIGHT_IN + lamp * 2 * (WING - FOOTLIGHT_IN) / (FOOTLIGHTS - 1);
            FlatQuads.box(consumer, at, glow, colour, x - FOOTLIGHT_HALF, EDGE_RISE, front, x + FOOTLIGHT_HALF, EDGE_RISE + 2 * FOOTLIGHT_HALF,
                front + 2 * FOOTLIGHT_HALF);
        }
    }

    /**
     * A player standing at so far across the stage, its bands in its own colour, swaying and nodding so far: a dark
     * chassis, a white body and head, each banded, and a cyan visor across the head's front.
     */
    private static void player(PoseStack pose, VertexConsumer consumer, int light, float across, int band, float swing, float nod) {
        pose.pushPose();
        pose.translate(across, 0, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(swing * SWAY));
        PoseStack.Pose at = pose.last();
        FlatQuads.box(consumer, at, light, CHASSIS_DARK, -CHASSIS_HALF, 0, -CHASSIS_DEPTH, CHASSIS_HALF, CHASSIS, CHASSIS_DEPTH);
        float waist = CHASSIS + BODY / 2;
        FlatQuads.box(consumer, at, light, WHITE, -BODY_HALF, CHASSIS, -BODY_DEPTH, BODY_HALF, CHASSIS + BODY, BODY_DEPTH);
        FlatQuads.box(consumer, at, light, band, -BODY_HALF - LAYER, waist - BAND / 2, -BODY_DEPTH - LAYER, BODY_HALF + LAYER, waist + BAND / 2,
            BODY_DEPTH + LAYER);
        float neck = CHASSIS + BODY - nod;
        FlatQuads.box(consumer, at, light, CHASSIS_DARK, -NECK_HALF, neck, -NECK_HALF, NECK_HALF, neck + NECK, NECK_HALF);
        float head = neck + NECK;
        FlatQuads.box(consumer, at, light, WHITE, -HEAD_HALF, head, -HEAD_HALF, HEAD_HALF, head + HEAD, HEAD_HALF);
        FlatQuads.box(consumer, at, light, band, -HEAD_HALF - LAYER, head + HEAD - BAND / 2, -HEAD_HALF - LAYER, HEAD_HALF + LAYER,
            head + HEAD + LAYER, HEAD_HALF + LAYER);
        FlatQuads.box(consumer, at, LightTexture.FULL_BRIGHT, VISOR, -HEAD_HALF + VISOR_IN, head + VISOR_FROM, -HEAD_HALF - LAYER,
            HEAD_HALF - VISOR_IN, head + VISOR_TO, -HEAD_HALF);
        pose.popPose();
    }

    /** The upright bass standing at the left player's right, its neck rising over its head, plucked on each beat. */
    private static void bass(PoseStack.Pose at, VertexConsumer consumer, int light, float across, float swing) {
        float x = across + BASS_ACROSS;
        float z = -BASS_OUT;
        FlatQuads.box(consumer, at, light, BASS_WOOD, x - BASS_HALF, 0, z - BASS_DEPTH, x + BASS_HALF, BASS_BODY, z + BASS_DEPTH);
        FlatQuads.box(consumer, at, light, BASS_NECK_DARK, x - BASS_NECK_HALF, BASS_BODY, z - BASS_NECK_HALF, x + BASS_NECK_HALF,
            BASS_BODY + BASS_NECK, z + BASS_NECK_HALF);
        float hand = x + PLUCK * swing;
        float y = BASS_BODY * PLUCKED_AT;
        FlatQuads.box(consumer, at, light, WHITE, hand - ARM / 2, y - ARM / 2, z - BASS_DEPTH - ARM, hand + ARM / 2, y + ARM / 2, -BODY_DEPTH);
    }

    /** The keyboard on its stand before the middle player, its hands falling on its keys in turn with the beat. */
    private static void keyboard(PoseStack.Pose at, VertexConsumer consumer, int light, float swing) {
        float middle = -KEYS_OUT;
        FlatQuads.box(consumer, at, light, STAND, -ARM / 2, 0, middle - ARM / 2, ARM / 2, KEYS_UP - KEYS_CASE, middle + ARM / 2);
        FlatQuads.box(consumer, at, light, KEYS_BLACK, -KEYS_HALF, KEYS_UP - KEYS_CASE, middle - KEYS_DEPTH / 2, KEYS_HALF, KEYS_UP,
            middle + KEYS_DEPTH / 2);
        FlatQuads.box(consumer, at, light, KEYS_WHITE, -KEYS_HALF + 2 * LAYER, KEYS_UP, middle - KEYS_DEPTH / 2 + LAYER, KEYS_HALF - 2 * LAYER,
            KEYS_UP + KEY_RISE, middle);
        for (int side = -1; side <= 1; side += 2) {
            float press = KEY_PRESS * Math.max(0, side * swing);
            float x = side * KEYS_HALF / 2;
            float y = KEYS_UP + KEY_RISE + ARM - press;
            FlatQuads.box(consumer, at, light, WHITE, x - ARM / 2, y, middle - KEYS_DEPTH / 4, x + ARM / 2, y + ARM, -BODY_DEPTH);
        }
    }

    /** The snare drum and cymbal before the right player, and its two sticks falling in turn with the beat. */
    private static void drums(PoseStack.Pose at, VertexConsumer consumer, int light, float across, float swing) {
        float middle = -SNARE_OUT;
        FlatQuads.box(consumer, at, light, STAND, across - ARM / 2, 0, middle - ARM / 2, across + ARM / 2, SNARE_UP - SNARE_DEPTH, middle + ARM / 2);
        FlatQuads.box(consumer, at, light, SNARE_RIM, across - SNARE_HALF, SNARE_UP - SNARE_DEPTH, middle - SNARE_HALF, across + SNARE_HALF,
            SNARE_UP - LAYER, middle + SNARE_HALF);
        FlatQuads.box(consumer, at, light, SNARE, across - SNARE_HALF + LAYER, SNARE_UP - LAYER, middle - SNARE_HALF + LAYER,
            across + SNARE_HALF - LAYER, SNARE_UP, middle + SNARE_HALF - LAYER);
        float cymbal = across - CYMBAL_ACROSS;
        FlatQuads.box(consumer, at, light, STAND, cymbal - ARM / 4, 0, middle - ARM / 4, cymbal + ARM / 4, CYMBAL_UP, middle + ARM / 4);
        FlatQuads.box(consumer, at, light, CYMBAL, cymbal - CYMBAL_HALF, CYMBAL_UP, middle - CYMBAL_HALF, cymbal + CYMBAL_HALF, CYMBAL_UP + LAYER,
            middle + CYMBAL_HALF);
        for (int side = -1; side <= 1; side += 2) {
            float lift = STICK_REST + STICK_BEAT * Math.max(0, side * swing);
            float x = across + side * STICKS_APART;
            FlatQuads.box(consumer, at, light, STICK, x - ARM / 2, SNARE_UP + lift, middle, x + ARM / 2, SNARE_UP + lift + ARM / 2, -BODY_DEPTH);
        }
    }

    @Override
    public AABB getRenderBoundingBox(BandstandBlockEntity stand) {
        return new AABB(stand.getBlockPos()).inflate(REACH);
    }
}
