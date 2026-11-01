package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.skyliners.block.CraneWinchBlock;
import dev.skyliners.block.CraneWinchBlockEntity;
import dev.skyliners.entity.CraneHook;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

/**
 * Draws the sky crane's winch's moving parts (see {@link CraneWinchBlock}): its drum of rope turning as the rope runs
 * out or in, and on its load dial a black needle over a green, amber and red arc, from the left at no strain to the
 * right at what the crane is rated for, with a green lamp lit while the hook holds a load. They read from its hook,
 * when it is near.
 */
public class CraneWinchRenderer implements BlockEntityRenderer<CraneWinchBlockEntity> {
    /** The drum: how far either side of the middle it reaches, how high its axle is, and its radius, in blocks. */
    private static final float DRUM_HALF = 4.5F / 16;
    private static final float DRUM_Y = -1.5F / 16;
    private static final float DRUM_RADIUS = 3F / 16;
    /** The drum's wooden ends and the rope wound on it. */
    private static final int DRUM_WOOD = 0xFF5C3B20;
    private static final int ROPE = 0xFFC9B27A;
    /** How far in from each end the rope's winding starts, in blocks. */
    private static final float DRUM_END = 1F / 16;
    /**
     * The dial: its middle, at the bottom of its half circle, and its arc's inner and outer radius, in blocks, on the
     * face of its plate toward whoever works the winch, a hair in front of it.
     */
    private static final float DIAL_Y = 11.5F / 16 - 0.5F;
    private static final float DIAL_Z = 12F / 16 - 0.5F + 0.002F;
    private static final float ARC_INNER = 3F / 16;
    private static final float ARC_OUTER = 3.8F / 16;
    private static final float NEEDLE = 3.4F / 16;
    private static final float NEEDLE_HALF = 0.25F / 16;
    /** The arc's colours, and where amber and red begin, as shares of the crane's rating. */
    private static final int GREEN = 0xFF3FA34D;
    private static final int AMBER = 0xFFE8A317;
    private static final int RED = 0xFFC8282C;
    private static final int NEEDLE_COLOUR = 0xFF161616;
    private static final float AMBER_FROM = 0.6F;
    private static final float RED_FROM = 0.85F;
    /** How far past the rating the needle may go, as a share of it, and how many pieces each colour's arc has. */
    private static final float NEEDLE_STOP = 1.05F;
    private static final int ARC_PIECES = 6;
    /** The lamp at the plate's corner, lit while the hook holds a load, and dark. */
    private static final float LAMP_X = -3.5F / 16;
    private static final float LAMP_Y = 15F / 16 - 0.5F;
    private static final float LAMP_HALF = 0.6F / 16;
    private static final int LAMP_LIT = 0xFF5CFF6A;
    private static final int LAMP_DARK = 0xFF203824;

    @Override
    public void render(CraneWinchBlockEntity winch, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (winch.getLevel() == null) {
            return;
        }
        CraneHook hook = CraneHook.hanging(winch.getLevel(), winch.getBlockPos());
        float length = hook == null ? (float) CraneHook.SHORTEST : hook.length();
        float strain = hook == null ? 0 : Mth.clamp(hook.strain(), 0, NEEDLE_STOP);
        Direction facing = winch.getBlockState().getValue(CraneWinchBlock.FACING);
        pose.pushPose();
        // Onto the face whoever works it looks at: x to their right, y up, z toward them.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.getOpposite().toYRot()));
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
        drum(consumer, pose, light, length / DRUM_RADIUS);
        dial(consumer, pose.last(), light, strain, hook != null && hook.loaded());
        pose.popPose();
    }

    /** The drum turned this far round its axle, in radians: wooden ends and the rope wound between them. */
    private static void drum(VertexConsumer consumer, PoseStack pose, int light, float turned) {
        float side = DRUM_RADIUS * Mth.SQRT_OF_TWO / 2;
        for (int eighth = 0; eighth < 2; eighth++) {
            pose.pushPose();
            pose.translate(0, DRUM_Y, 0);
            pose.mulPose(Axis.XP.rotation(turned + eighth * Mth.HALF_PI / 2));
            PoseStack.Pose at = pose.last();
            FlatQuads.box(consumer, at, light, DRUM_WOOD, -DRUM_HALF, -side, -side, -DRUM_HALF + DRUM_END, side, side);
            FlatQuads.box(consumer, at, light, DRUM_WOOD, DRUM_HALF - DRUM_END, -side, -side, DRUM_HALF, side, side);
            FlatQuads.box(consumer, at, light, ROPE, -DRUM_HALF + DRUM_END, -side * 0.9F, -side * 0.9F, DRUM_HALF - DRUM_END, side * 0.9F, side * 0.9F);
            pose.popPose();
        }
    }

    /** The load dial: its arc, its needle at the strain given, and its lamp. */
    private static void dial(VertexConsumer consumer, PoseStack.Pose at, int light, float strain, boolean loaded) {
        arc(consumer, at, light, GREEN, 0, AMBER_FROM);
        arc(consumer, at, light, AMBER, AMBER_FROM, RED_FROM);
        arc(consumer, at, light, RED, RED_FROM, 1);
        float angle = Mth.PI * (1 - strain);
        float tipX = Mth.cos(angle) * NEEDLE;
        float tipY = DIAL_Y + Mth.sin(angle) * NEEDLE;
        float acrossX = -Mth.sin(angle) * NEEDLE_HALF;
        float acrossY = Mth.cos(angle) * NEEDLE_HALF;
        float z = DIAL_Z + 0.001F;
        FlatQuads.quad(consumer, at, light, NEEDLE_COLOUR, 0, 0, 1, -acrossX, DIAL_Y - acrossY, z, tipX - acrossX * 0.3F, tipY - acrossY * 0.3F, z,
            tipX + acrossX * 0.3F, tipY + acrossY * 0.3F, z, acrossX, DIAL_Y + acrossY, z);
        int lamp = loaded ? LAMP_LIT : LAMP_DARK;
        FlatQuads.quad(consumer, at, light, lamp, 0, 0, 1, LAMP_X - LAMP_HALF, LAMP_Y - LAMP_HALF, DIAL_Z, LAMP_X + LAMP_HALF, LAMP_Y - LAMP_HALF, DIAL_Z,
            LAMP_X + LAMP_HALF, LAMP_Y + LAMP_HALF, DIAL_Z, LAMP_X - LAMP_HALF, LAMP_Y + LAMP_HALF, DIAL_Z);
    }

    /** A piece of the dial's arc, from one share of the rating to another, left to right. */
    private static void arc(VertexConsumer consumer, PoseStack.Pose at, int light, int colour, float from, float to) {
        for (int piece = 0; piece < ARC_PIECES; piece++) {
            float a0 = Mth.PI * (1 - Mth.lerp(piece / (float) ARC_PIECES, from, to));
            float a1 = Mth.PI * (1 - Mth.lerp((piece + 1) / (float) ARC_PIECES, from, to));
            FlatQuads.quad(consumer, at, light, colour, 0, 0, 1,
                Mth.cos(a0) * ARC_INNER, DIAL_Y + Mth.sin(a0) * ARC_INNER, DIAL_Z,
                Mth.cos(a0) * ARC_OUTER, DIAL_Y + Mth.sin(a0) * ARC_OUTER, DIAL_Z,
                Mth.cos(a1) * ARC_OUTER, DIAL_Y + Mth.sin(a1) * ARC_OUTER, DIAL_Z,
                Mth.cos(a1) * ARC_INNER, DIAL_Y + Mth.sin(a1) * ARC_INNER, DIAL_Z);
        }
    }
}
