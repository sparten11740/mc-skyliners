package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.block.EngineCoreBlock;
import dev.aerolarge.block.EngineCoreBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws an engine's intake round over the face of its nacelle: the cowl standing a little proud of the blocks, its
 * polished lip curling over into the duct, and at the bottom of the duct the fan round its spinner. At rest an iris
 * shuts the duct; it opens as the engine starts, and behind the turning fan the engine glows a dull red, brighter the
 * harder it runs. It is drawn from the engine's core, which knows where its intake is (see
 * {@link EngineCoreBlockEntity.Intake}) and how its iris and fan stand, in flat colours lit by the light just in front
 * of the intake, and in proportion to the nacelle, so it looks the same at any scale. An engine built before its core
 * knew where its intake is draws nothing and keeps its plain face.
 */
public class EngineIntakeRenderer implements BlockEntityRenderer<EngineCoreBlockEntity> {
    /** Vanilla's plain white texture, painted over in the vertices' colours. */
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    /** Steps round the intake, and round the lip's curl from the cowl into the duct. */
    private static final int SEGMENTS = 48;
    private static final int LIP_STEPS = 5;
    /** How far the cowl stands proud of the nacelle's face, as a share of the nacelle's radius. */
    private static final float PROUD = 0.4F;
    /**
     * How far out of the face the dark at the bottom of the duct, the glow, the fan and the spinner's base lie, as
     * shares of the nacelle's radius: just far enough apart not to flicker against each other or the face from afar.
     */
    private static final float DEPTH_OUT = 0.02F;
    private static final float GLOW_OUT = 0.03F;
    private static final float FAN_OUT = 0.04F;
    private static final float SPINNER_OUT = 0.045F;
    /** How far inside the duct's mouth the iris lies, as a share of the nacelle's radius: just ahead of the spinner. */
    private static final float IRIS_IN = 0.03F;
    /**
     * The iris: how many petals; how many rings in from the wall and steps across each petal it is drawn in, so its
     * seams curve; how far round, in radians, a seam winds from the wall to the middle; and how far the petals turn
     * as it opens.
     */
    private static final int PETALS = 10;
    private static final int IRIS_RINGS = 4;
    private static final int IRIS_STEPS = 4;
    private static final double IRIS_WIND = 1.2;
    private static final double IRIS_TURN = 0.6;
    /**
     * The glow's strength: it fades in over this first share of the spool as the engine lights, and burns from this
     * share of its full strength with the engine barely turning to full at full thrust.
     */
    private static final float GLOW_FADE_IN = 0.1F;
    private static final float GLOW_IDLE = 0.45F;
    /** The glow, deep red and dull, drawn with its own soft round texture. */
    private static final int GLOW_COLOUR = 0xB82A10;
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/misc/intake_glow.png");
    /** The spinner's radius as a share of the mouth's, and its length as a share of its own radius. */
    private static final float SPINNER = 0.3F;
    private static final float SPINNER_LENGTH = 0.8F;
    /**
     * The fan's blades: how many, and in shares of the angle from one to the next, how wide each is at its root and
     * at its tip and how far round its tip sweeps.
     */
    private static final int BLADES = 20;
    private static final float BLADE_ROOT = 0.2F;
    private static final float BLADE_TIP = 0.3F;
    private static final float BLADE_SWEEP = 0.5F;
    private static final int COWL = 0xFFE8ECF0;
    private static final int LIP = 0xFFD3D8DD;
    private static final int DUCT = 0xFF8A9097;
    private static final int DEPTH = 0xFF16181C;
    private static final int BLADE = 0xFF555B63;
    private static final int SPINNER_COLOUR = 0xFFC9CFD6;
    private static final int PETAL = 0xFF4A5058;
    private static final int PETAL_SHADE = 0xFF3C4148;
    /** How far off the intake is still drawn, in blocks: an airliner's engines are seen from a long way. */
    private static final int VIEW_DISTANCE = 256;
    /** The cosine and sine at every step round the circle, the first repeated at the end. */
    private static final float[] COS = new float[SEGMENTS + 1];
    private static final float[] SIN = new float[SEGMENTS + 1];

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            COS[i] = (float) Math.cos(2 * Math.PI * i / SEGMENTS);
            SIN[i] = (float) Math.sin(2 * Math.PI * i / SEGMENTS);
        }
    }

    @Override
    public void render(EngineCoreBlockEntity engine, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int overlay) {
        EngineCoreBlockEntity.Intake intake = engine.intake();
        Level level = engine.getLevel();
        if (intake == null || level == null) {
            return;
        }
        Direction nose = engine.getBlockState().getValue(EngineCoreBlock.FACING);
        // Only while the nacelle's face is there to draw it over.
        if (level.getBlockState(BlockPos.containing(onAxis(engine, intake, -0.5F))).isAir()) {
            return;
        }
        // The core itself is buried in the nacelle, in the dark: the intake is lit by the air in front of it.
        int light = SableClient.light(level, BlockPos.containing(onAxis(engine, intake, 0.5F)));

        pose.pushPose();
        // Onto the middle of the intake's face: x to port (to the right, looking into it), y up, z out along the nose.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-nose.toYRot()));
        pose.translate(-intake.starboard(), intake.up(), intake.ahead());
        Painter paint = new Painter(buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE)), pose.last(), light);
        float radius = intake.radius();
        float mouth = intake.mouth();
        // The lip curls over in a half circle as wide as it is thick; the cowl's straight sides end where it starts.
        float curl = (radius - mouth) / 2;
        float sides = PROUD * radius - curl;
        cowl(paint, radius, mouth, curl, sides);
        float open = ease(engine.iris(partialTick));
        if (open < 1) {
            iris(paint, mouth, sides - IRIS_IN * radius, open);
        }
        // Behind a shut iris there is nothing to see.
        if (open > 0) {
            disc(paint, mouth, DEPTH_OUT * radius, DEPTH);
            fan(paint, mouth, FAN_OUT * radius, engine.fan(partialTick));
            spinner(paint, SPINNER * mouth, SPINNER_OUT * radius);
            // Last: taking another buffer finishes the one the rest went into.
            glow(buffers.getBuffer(RenderType.eyes(GLOW)), pose.last(), mouth, GLOW_OUT * radius, glowStrength(engine.spool()));
        }
        pose.popPose();
    }

    /** Eases the iris in and out, so its petals start and stop gently. */
    private static float ease(float open) {
        return open * open * (3 - 2 * open);
    }

    /** How brightly the glow burns at a spool: fading in as the engine lights, then brighter the harder it runs. */
    private static float glowStrength(float spool) {
        return Math.min(1, spool / GLOW_FADE_IN) * (GLOW_IDLE + (1 - GLOW_IDLE) * spool);
    }

    /**
     * The cowl: its outside, from the nacelle's face out to where the lip starts to curl; the lip curling over in a
     * half circle from the outside to the mouth; and the duct, from the lip back to the face.
     */
    private static void cowl(Painter paint, float radius, float mouth, float curl, float sides) {
        band(paint, radius, 0, 1, 0, radius, sides, 1, 0, COWL);
        for (int k = 0; k < LIP_STEPS; k++) {
            double from = Math.PI * k / LIP_STEPS;
            double to = Math.PI * (k + 1) / LIP_STEPS;
            float outFrom = (float) Math.cos(from);
            float aheadFrom = (float) Math.sin(from);
            float outTo = (float) Math.cos(to);
            float aheadTo = (float) Math.sin(to);
            band(paint, mouth + curl + curl * outFrom, sides + curl * aheadFrom, outFrom, aheadFrom,
                mouth + curl + curl * outTo, sides + curl * aheadTo, outTo, aheadTo, LIP);
        }
        band(paint, mouth, sides, -1, 0, mouth, 0, -1, 0, DUCT);
    }

    /**
     * The iris that shuts the intake while the engine is at rest: petals round the duct, each between two seams that
     * wind in from the wall to the middle. As it opens (0 shut, 1 open) the petals draw back to the wall from the
     * middle out, turning a little as they go, the way a camera's iris opens.
     */
    private static void iris(Painter paint, float mouth, float out, float open) {
        double petal = 2 * Math.PI / PETALS;
        double turn = IRIS_TURN * open;
        float aperture = mouth * open;
        for (int k = 0; k < PETALS; k++) {
            int colour = k % 2 == 0 ? PETAL : PETAL_SHADE;
            for (int i = 0; i < IRIS_RINGS; i++) {
                float outer = mouth + (aperture - mouth) * i / IRIS_RINGS;
                float inner = mouth + (aperture - mouth) * (i + 1) / IRIS_RINGS;
                for (int j = 0; j < IRIS_STEPS; j++) {
                    double from = turn + petal * (k + (double) j / IRIS_STEPS);
                    double to = turn + petal * (k + (double) (j + 1) / IRIS_STEPS);
                    paint.flat(outer, from + seam(outer, mouth), out, colour);
                    paint.flat(outer, to + seam(outer, mouth), out, colour);
                    paint.flat(inner, to + seam(inner, mouth), out, colour);
                    paint.flat(inner, from + seam(inner, mouth), out, colour);
                }
            }
        }
    }

    /** How far round a seam between the iris's petals has wound, this far from the axis. */
    private static double seam(float radius, float mouth) {
        return IRIS_WIND * (1 - radius / mouth);
    }

    /**
     * The glow deep in the duct, behind the fan's blades: a dull red added over the dark, brightest round the spinner,
     * at full brightness whatever the light around, and as strong as given.
     */
    private static void glow(VertexConsumer consumer, PoseStack.Pose pose, float mouth, float out, float strength) {
        float red = (GLOW_COLOUR >> 16 & 0xFF) / 255F * strength;
        float green = (GLOW_COLOUR >> 8 & 0xFF) / 255F * strength;
        float blue = (GLOW_COLOUR & 0xFF) / 255F * strength;
        glowVertex(consumer, pose, -mouth, -mouth, out, 0, 1, red, green, blue);
        glowVertex(consumer, pose, mouth, -mouth, out, 1, 1, red, green, blue);
        glowVertex(consumer, pose, mouth, mouth, out, 1, 0, red, green, blue);
        glowVertex(consumer, pose, -mouth, mouth, out, 0, 0, red, green, blue);
    }

    private static void glowVertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                                   float red, float green, float blue) {
        consumer.addVertex(pose, x, y, z)
            .setColor(red, green, blue, 1)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(pose, 0, 0, 1);
    }

    /** The fan's blades, flat across the duct from the spinner to its wall, turned this far round. */
    private static void fan(Painter paint, float mouth, float out, float turn) {
        double pitch = 2 * Math.PI / BLADES;
        float root = SPINNER * mouth;
        float tip = mouth;
        for (int b = 0; b < BLADES; b++) {
            double middle = turn + b * pitch;
            double sweep = middle + BLADE_SWEEP * pitch;
            paint.flat(root, middle - BLADE_ROOT * pitch, out, BLADE);
            paint.flat(root, middle + BLADE_ROOT * pitch, out, BLADE);
            paint.flat(tip, sweep + BLADE_TIP * pitch, out, BLADE);
            paint.flat(tip, sweep - BLADE_TIP * pitch, out, BLADE);
        }
    }

    /** The spinner: a cone on the fan's middle, pointing out of the intake, shaded round its curve. */
    private static void spinner(Painter paint, float base, float out) {
        float length = SPINNER_LENGTH * base;
        // The cone's surface leans back from the axis by its base over its length.
        float across = length / (float) Math.hypot(length, base);
        float ahead = base / (float) Math.hypot(length, base);
        for (int i = 0; i < SEGMENTS; i++) {
            float middleCos = (COS[i] + COS[i + 1]) / 2;
            float middleSin = (SIN[i] + SIN[i + 1]) / 2;
            paint.vertex(base * COS[i], base * SIN[i], out, across * COS[i], across * SIN[i], ahead, SPINNER_COLOUR);
            paint.vertex(base * COS[i + 1], base * SIN[i + 1], out, across * COS[i + 1], across * SIN[i + 1], ahead, SPINNER_COLOUR);
            paint.vertex(0, 0, out + length, across * middleCos, across * middleSin, ahead, SPINNER_COLOUR);
            paint.vertex(0, 0, out + length, across * middleCos, across * middleSin, ahead, SPINNER_COLOUR);
        }
    }

    /** A flat disc across the duct, facing out of the intake. */
    private static void disc(Painter paint, float radius, float out, int colour) {
        for (int i = 0; i < SEGMENTS; i++) {
            paint.vertex(0, 0, out, 0, 0, 1, colour);
            paint.vertex(radius * COS[i], radius * SIN[i], out, 0, 0, 1, colour);
            paint.vertex(radius * COS[i + 1], radius * SIN[i + 1], out, 0, 0, 1, colour);
            paint.vertex(radius * COS[i + 1], radius * SIN[i + 1], out, 0, 0, 1, colour);
        }
    }

    /**
     * A band round the axis from one ring to another, each given by its radius, how far out of the face it is, and
     * its surface's way out as a share outward from the axis and a share ahead.
     */
    private static void band(Painter paint, float radiusFrom, float outFrom, float acrossFrom, float aheadFrom,
                             float radiusTo, float outTo, float acrossTo, float aheadTo, int colour) {
        for (int i = 0; i < SEGMENTS; i++) {
            paint.vertex(radiusFrom * COS[i], radiusFrom * SIN[i], outFrom, acrossFrom * COS[i], acrossFrom * SIN[i], aheadFrom, colour);
            paint.vertex(radiusFrom * COS[i + 1], radiusFrom * SIN[i + 1], outFrom, acrossFrom * COS[i + 1], acrossFrom * SIN[i + 1], aheadFrom, colour);
            paint.vertex(radiusTo * COS[i + 1], radiusTo * SIN[i + 1], outTo, acrossTo * COS[i + 1], acrossTo * SIN[i + 1], aheadTo, colour);
            paint.vertex(radiusTo * COS[i], radiusTo * SIN[i], outTo, acrossTo * COS[i], acrossTo * SIN[i], aheadTo, colour);
        }
    }

    /** A point on the intake's axis in the level, this far out ahead of its face. */
    private static Vec3 onAxis(EngineCoreBlockEntity engine, EngineCoreBlockEntity.Intake intake, float out) {
        Direction nose = engine.getBlockState().getValue(EngineCoreBlock.FACING);
        return Vec3.atCenterOf(engine.getBlockPos())
            .add(Vec3.atLowerCornerOf(nose.getNormal()).scale(intake.ahead() + out))
            .add(Vec3.atLowerCornerOf(nose.getClockWise().getNormal()).scale(intake.starboard()))
            .add(0, intake.up(), 0);
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(EngineCoreBlockEntity engine) {
        AABB core = new AABB(engine.getBlockPos());
        EngineCoreBlockEntity.Intake intake = engine.intake();
        if (intake == null) {
            return core;
        }
        Vec3 middle = onAxis(engine, intake, 0);
        return core.minmax(new AABB(middle, middle).inflate(intake.radius() * (1 + PROUD)));
    }

    /** Where the intake's quads go: the buffer, the pose they are drawn in and the light they are lit by. */
    private record Painter(VertexConsumer consumer, PoseStack.Pose pose, int light) {
        void vertex(float x, float y, float z, float nx, float ny, float nz, int colour) {
            consumer.addVertex(pose, x, y, z)
                .setColor(colour)
                .setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
        }

        /** A point of something flat across the duct, facing out of the intake, by its distance from the axis and angle. */
        void flat(float radius, double angle, float out, int colour) {
            vertex(radius * (float) Math.cos(angle), radius * (float) Math.sin(angle), out, 0, 0, 1, colour);
        }
    }
}
