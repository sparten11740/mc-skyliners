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
 * Draws an engine from its core, which knows where its intake is (see {@link EngineCoreBlockEntity.Intake}): the
 * intake, and behind the engine its lightweight flame while it runs, as the server config asks (see
 * {@link ExhaustFlame}). An engine built before its core knew where its intake is draws neither and keeps its plain
 * face.
 *
 * <p>The intake is drawn over the face of the nacelle: the cowl standing a little proud of the blocks, in flat sides
 * as the nacelle's shell behind it is cut, its polished lip bevelled over into the duct, and at the bottom of the duct
 * the fan round its spinner. At rest an iris shuts the duct; it opens as the engine starts, and behind the turning fan
 * the engine glows a dull red, brighter the harder it runs. It is drawn as the core's iris and fan stand, in flat
 * colours lit by the light just in front of the intake, and in proportion to the nacelle, so it looks the same at any
 * scale.
 *
 * <p>The cowl stands proud of the nacelle, so it shows from beside and behind it too and is always drawn; what lies
 * inside the duct is left out with the camera behind the nacelle's face, where the face and the duct's wall hide it.
 */
public class EngineRenderer implements BlockEntityRenderer<EngineCoreBlockEntity> {
    /** Vanilla's plain white texture, painted over in the vertices' colours. */
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    /** Steps round the dark at the bottom of the duct and round the spinner. */
    private static final int SEGMENTS = 48;
    /**
     * The flat sides the cowl, its lip and the duct are drawn in, so they read as cut like the nacelle's shell rather
     * than turned round: a side each faces up, down and out to either side, as the nacelle's flats do.
     */
    private static final int SIDES = 16;
    /** The flat steps the lip turns over in from the cowl into the duct: a bevel, a flat front and a bevel. */
    private static final int LIP_STEPS = 3;
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
    /**
     * The cosine and sine at every corner of the cowl's sides, half a side round from straight out to port, the first
     * repeated at the end; and the same for the way each side faces, from the middle of it.
     */
    private static final float[] CORNER_COS = new float[SIDES + 1];
    private static final float[] CORNER_SIN = new float[SIDES + 1];
    private static final float[] FACING_COS = new float[SIDES];
    private static final float[] FACING_SIN = new float[SIDES];
    /**
     * The same at every step round the iris's petals, before they turn: worked out once, rather than for each of the
     * iris's 640 points in every frame.
     */
    private static final float[] IRIS_COS = new float[PETALS * IRIS_STEPS + 1];
    private static final float[] IRIS_SIN = new float[PETALS * IRIS_STEPS + 1];

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            COS[i] = (float) Math.cos(2 * Math.PI * i / SEGMENTS);
            SIN[i] = (float) Math.sin(2 * Math.PI * i / SEGMENTS);
        }
        for (int i = 0; i <= SIDES; i++) {
            CORNER_COS[i] = (float) Math.cos(2 * Math.PI * (i + 0.5) / SIDES);
            CORNER_SIN[i] = (float) Math.sin(2 * Math.PI * (i + 0.5) / SIDES);
        }
        for (int i = 0; i < SIDES; i++) {
            FACING_COS[i] = (float) Math.cos(2 * Math.PI * (i + 1) / SIDES);
            FACING_SIN[i] = (float) Math.sin(2 * Math.PI * (i + 1) / SIDES);
        }
        for (int i = 0; i <= PETALS * IRIS_STEPS; i++) {
            IRIS_COS[i] = (float) Math.cos(2 * Math.PI * i / (PETALS * IRIS_STEPS));
            IRIS_SIN[i] = (float) Math.sin(2 * Math.PI * i / (PETALS * IRIS_STEPS));
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
        pose.pushPose();
        // Onto the engine's axis level with the middle of the core: x to port (to the right, looking into the intake),
        // y up, z out along the nose.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-nose.toYRot()));
        pose.translate(-intake.starboard(), intake.up(), 0);
        // Only while the nacelle's face is there to draw it over.
        if (!level.getBlockState(BlockPos.containing(onAxis(engine, intake, -0.5F))).isAir()) {
            intake(engine, intake, level, partialTick, pose, buffers);
        }
        if (ExhaustFlame.burning(engine, level, nose)) {
            ExhaustFlame.draw(engine, level, intake.radius(), partialTick, pose, buffers);
        }
        pose.popPose();
    }

    /** The intake on the nacelle's face: the cowl, and what lies inside the duct with the camera in front of it. */
    private static void intake(EngineCoreBlockEntity engine, EngineCoreBlockEntity.Intake intake, Level level, float partialTick,
                               PoseStack pose, MultiBufferSource buffers) {
        // The core itself is buried in the nacelle, in the dark: the intake is lit by the air in front of it.
        int light = SableClient.light(level, BlockPos.containing(onAxis(engine, intake, 0.5F)));
        pose.pushPose();
        pose.translate(0, 0, intake.ahead());
        Painter paint = new Painter(buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE)), pose.last(), light);
        float radius = intake.radius();
        float mouth = intake.mouth();
        // The lip turns over round a half circle as wide as it is thick; the cowl's straight sides end where it starts.
        float curl = (radius - mouth) / 2;
        float sides = PROUD * radius - curl;
        cowl(paint, radius, mouth, curl, sides);
        if (Sight.eye(pose.last()).z > 0) {
            inDuct(paint, buffers, pose, engine, partialTick, radius, mouth, sides);
        }
        pose.popPose();
    }

    /** What lies inside the duct: the iris, as far as it is shut, and behind it the fan, its spinner and the glow. */
    private static void inDuct(Painter paint, MultiBufferSource buffers, PoseStack pose, EngineCoreBlockEntity engine, float partialTick,
                               float radius, float mouth, float sides) {
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
     * The cowl: its outside, from the nacelle's face out to where the lip starts to turn over; the lip turning over in
     * flat steps round a half circle from the outside to the mouth; and the duct, from the lip back to the face.
     */
    private static void cowl(Painter paint, float radius, float mouth, float curl, float sides) {
        band(paint, radius, 0, radius, sides, COWL);
        for (int k = 0; k < LIP_STEPS; k++) {
            double from = Math.PI * k / LIP_STEPS;
            double to = Math.PI * (k + 1) / LIP_STEPS;
            band(paint, mouth + curl + curl * (float) Math.cos(from), sides + curl * (float) Math.sin(from),
                mouth + curl + curl * (float) Math.cos(to), sides + curl * (float) Math.sin(to), LIP);
        }
        band(paint, mouth, sides, mouth, 0, DUCT);
    }

    /**
     * The iris that shuts the intake while the engine is at rest: petals round the duct, each between two seams that
     * wind in from the wall to the middle. As it opens (0 shut, 1 open) the petals draw back to the wall from the
     * middle out, turning a little as they go, the way a camera's iris opens.
     */
    private static void iris(Painter paint, float mouth, float out, float open) {
        float aperture = mouth * open;
        // Each ring the petals are drawn in, from the wall in: how far it is from the axis, and how far round it is
        // turned, as the petals turn and their seams wind, as a cosine and a sine.
        float[] ring = new float[IRIS_RINGS + 1];
        float[] turnCos = new float[IRIS_RINGS + 1];
        float[] turnSin = new float[IRIS_RINGS + 1];
        for (int i = 0; i <= IRIS_RINGS; i++) {
            ring[i] = mouth + (aperture - mouth) * i / IRIS_RINGS;
            double turn = IRIS_TURN * open + seam(ring[i], mouth);
            turnCos[i] = (float) Math.cos(turn);
            turnSin[i] = (float) Math.sin(turn);
        }
        for (int k = 0; k < PETALS; k++) {
            int colour = k % 2 == 0 ? PETAL : PETAL_SHADE;
            for (int i = 0; i < IRIS_RINGS; i++) {
                for (int j = 0; j < IRIS_STEPS; j++) {
                    int from = k * IRIS_STEPS + j;
                    irisPoint(paint, ring[i], from, turnCos[i], turnSin[i], out, colour);
                    irisPoint(paint, ring[i], from + 1, turnCos[i], turnSin[i], out, colour);
                    irisPoint(paint, ring[i + 1], from + 1, turnCos[i + 1], turnSin[i + 1], out, colour);
                    irisPoint(paint, ring[i + 1], from, turnCos[i + 1], turnSin[i + 1], out, colour);
                }
            }
        }
    }

    /** A point of the iris: this far from the axis, at a step round its petals, turned as far as its ring is. */
    private static void irisPoint(Painter paint, float radius, int step, float turnCos, float turnSin, float out, int colour) {
        float cos = IRIS_COS[step] * turnCos - IRIS_SIN[step] * turnSin;
        float sin = IRIS_SIN[step] * turnCos + IRIS_COS[step] * turnSin;
        paint.vertex(radius * cos, radius * sin, out, 0, 0, 1, colour);
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
     * A band of the cowl's flat sides round the axis from one ring to another, each given by the radius of its corners
     * and how far out of the face it is, every side lit as the flat face it is.
     */
    private static void band(Painter paint, float radiusFrom, float outFrom, float radiusTo, float outTo, int colour) {
        // The sides' way out, square to the band's slope from one ring to the other: a share outward and a share ahead.
        float slope = (float) Math.hypot(radiusTo - radiusFrom, outTo - outFrom);
        float across = (outTo - outFrom) / slope;
        float ahead = (radiusFrom - radiusTo) / slope;
        for (int i = 0; i < SIDES; i++) {
            float nx = across * FACING_COS[i];
            float ny = across * FACING_SIN[i];
            paint.vertex(radiusFrom * CORNER_COS[i], radiusFrom * CORNER_SIN[i], outFrom, nx, ny, ahead, colour);
            paint.vertex(radiusFrom * CORNER_COS[i + 1], radiusFrom * CORNER_SIN[i + 1], outFrom, nx, ny, ahead, colour);
            paint.vertex(radiusTo * CORNER_COS[i + 1], radiusTo * CORNER_SIN[i + 1], outTo, nx, ny, ahead, colour);
            paint.vertex(radiusTo * CORNER_COS[i], radiusTo * CORNER_SIN[i], outTo, nx, ny, ahead, colour);
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
        Vec3 nozzle = onAxis(engine, intake, -intake.ahead() - ExhaustFlame.NOZZLE_BEHIND);
        Direction back = engine.getBlockState().getValue(EngineCoreBlock.FACING).getOpposite();
        return core.minmax(new AABB(middle, middle).inflate(intake.radius() * (1 + PROUD)))
            .minmax(ExhaustFlame.bounds(nozzle, back, intake.radius()));
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
