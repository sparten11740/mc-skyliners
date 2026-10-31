package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.block.OrreryBlockEntity;
import java.util.List;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Draws the rocket's orrery (see {@link OrreryBlockEntity}) under its mount: a brass rod down to the sun, glowing as
 * brightly in the dark as in the light and turning slowly, and the six planets in its plane, each in its own colours on
 * a brass arm from the column under the sun and a post up, circling it, the nearer the faster, and each turning as it
 * goes: grey Mercury, pale Venus, the blue and green Earth with its moon going round it, red Mars, banded Jupiter, the
 * biggest, and golden Saturn in its tilted ring. All of it is as big as the dome it hangs in leaves room for, from how
 * far out its outermost planet circles there, and turns with the game's time at this partial tick, so smoothly and the
 * same for everyone.
 */
public class OrreryRenderer implements BlockEntityRenderer<OrreryBlockEntity> {
    private static final ResourceLocation SHEET = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/block/orrery.png");
    /**
     * How many patches across and down its sheet holds, and which is which: the brass, the sun and each planet's
     * surface in the first row, the moon and Saturn's ring in the second.
     */
    private static final int PATCHES_ACROSS = 8;
    private static final int PATCHES_DOWN = 2;
    private static final int BRASS = 0;
    private static final int SUN = 1;
    private static final int MOON = 8;
    private static final int RING = 9;
    /** How far out the outermost planet circles for the sizes given here, in blocks: the rest grow with it. */
    private static final float BASE_REACH = 5;
    /** How wide the sun is at the base reach, and the rod down to it, the arms and the posts at any, in blocks. */
    private static final float SUN_SIZE = 0.7F;
    private static final float ROD = 0.1F;
    private static final float ARM = 0.06F;
    /** How far under the sun the first arm leaves the column, and each next under that, in blocks at the base reach. */
    private static final float FIRST_ARM = 0.15F;
    private static final float ARM_STEP = 0.12F;
    /** How far over the mount's middle the rod starts, at the foot of its brass rose, in blocks. */
    private static final float ROSE_FOOT = 0.375F;
    /** How long the sun takes to turn once, and each planet as it goes, in ticks. */
    private static final float SUN_TURN = 1200;
    private static final float PLANET_TURN = 300;
    /**
     * The moon: how far from the Earth it circles and how wide it is, in blocks at the base reach, and how long it
     * takes to go round, in ticks.
     */
    private static final float MOON_ORBIT = 0.32F;
    private static final float MOON_SIZE = 0.08F;
    private static final float MOON_PERIOD = 80;
    /** Saturn's ring: how wide it is, as a share of the planet's own width, and how far it tilts, in radians. */
    private static final float RING_WIDTH = 2.4F;
    private static final float RING_TILT = 0.45F;
    /** The planets from the sun out, and which of them have the moon and the ring. */
    private static final List<Planet> PLANETS = List.of(
        new Planet(2, 0.22F, 0.14F, 180, 0.3F),
        new Planet(3, 0.36F, 0.22F, 300, 2.1F),
        new Planet(4, 0.5F, 0.24F, 460, 4.0F),
        new Planet(5, 0.64F, 0.18F, 720, 5.2F),
        new Planet(6, 0.82F, 0.46F, 1300, 1.2F),
        new Planet(7, 1.0F, 0.38F, 2000, 3.3F));
    private static final int EARTH = 2;
    private static final int SATURN = 5;
    /** How far off the orrery and its planetarium are drawn, in blocks: from the cockpit's floor up to the tip. */
    private static final int VIEW_DISTANCE = 96;
    /**
     * Before its dome is first found: how far out from her axis the widest dome reaches, and how far under the sun the
     * deepest cockpit's floor is, in blocks, at the biggest scale.
     */
    private static final double WIDEST_DOME = 24;
    private static final double DEEPEST_UNDER_SUN = 16;
    /** A box's six sides: its corners going round (as numbered in {@link #box}), and the way it faces. */
    private static final int[][] FACES = {
        {0, 1, 2, 3, 0, 0, -1}, {5, 4, 7, 6, 0, 0, 1}, {4, 0, 3, 7, -1, 0, 0},
        {1, 5, 6, 2, 1, 0, 0}, {3, 2, 6, 7, 0, 1, 0}, {4, 5, 1, 0, 0, -1, 0}};

    /**
     * A planet: its patch on the sheet, how far out it circles as a share of the reach, how wide it is in blocks at the
     * base reach, how long it takes to go round once, in ticks, and where round it starts, in radians.
     */
    private record Planet(int patch, float orbit, float size, float period, float start) {
    }

    @Override
    public void render(OrreryBlockEntity orrery, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = orrery.getLevel();
        if (level == null) {
            return;
        }
        OrreryDome dome = OrreryDome.of(orrery, level);
        if (!dome.holds(Sight.eye(pose.last()))) {
            return;
        }
        orrery(orrery, partialTick, pose, buffers, overlay);
        if (!dome.shaped()) {
            return;
        }
        // Each kind of quad in turn: taking another buffer finishes the one before.
        float ticks = level.getGameTime() + partialTick;
        boolean ring = OculusRing.fits(dome);
        if (ring) {
            OculusRing.draw(dome, new OrreryBrush(buffers.getBuffer(RenderType.entityCutoutNoCull(OrrerySheet.TEXTURE)), pose.last()));
        }
        PlanetariumShell.draw(dome, new OrreryBrush(buffers.getBuffer(RenderType.entityCutoutNoCull(PlanetariumShell.SKY)), pose.last()));
        OrreryBrush glow = new OrreryBrush(buffers.getBuffer(RenderType.eyes(OrreryGlow.TEXTURE)), pose.last());
        PlanetariumShell.sparkles(dome, glow, OrreryBrush.Facing.of(pose.last()), ticks);
        if (ring) {
            OculusRing.lamps(dome, glow, dome.autopilot(level), ticks);
        }
    }

    /** The orrery itself, hanging from its mount. */
    private static void orrery(OrreryBlockEntity orrery, float partialTick, PoseStack pose, MultiBufferSource buffers, int overlay) {
        float drop = (float) orrery.drop();
        float scale = (float) orrery.reach() / BASE_REACH;
        long time = orrery.getLevel().getGameTime();
        int lit = LevelRenderer.getLightColor(orrery.getLevel(), orrery.getBlockPos().below(Mth.floor(drop)));
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(SHEET));
        pose.pushPose();
        pose.translate(0.5, 0.5 - drop, 0.5);
        float sunHalf = SUN_SIZE * scale / 2;
        float top = drop + ROSE_FOOT;
        box(pose.last(), buffer, 0, (sunHalf + top) / 2, 0, ROD / 2, (top - sunHalf) / 2, ROD / 2, BRASS, lit, overlay);
        float lowest = sunHalf + (FIRST_ARM + ARM_STEP * PLANETS.size()) * scale;
        box(pose.last(), buffer, 0, -(sunHalf + lowest) / 2, 0, ROD / 2, (lowest - sunHalf) / 2, ROD / 2, BRASS, lit, overlay);
        sun(pose, buffer, sunHalf, turned(time, partialTick, SUN_TURN), overlay);
        for (int i = 0; i < PLANETS.size(); i++) {
            planet(pose, buffer, i, scale, sunHalf + (FIRST_ARM + ARM_STEP * i) * scale, time, partialTick, lit, overlay);
        }
        pose.popPose();
    }

    /** How far round something going round once in so many ticks has come, at this partial tick, in radians. */
    private static float turned(long time, float partialTick, float period) {
        return ((time % (long) period) + partialTick) / period * Mth.TWO_PI;
    }

    /** The sun, glowing, turned so far. */
    private static void sun(PoseStack pose, VertexConsumer buffer, float half, float turn, int overlay) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(turn));
        box(pose.last(), buffer, 0, 0, 0, half, half, half, SUN, LightTexture.FULL_BRIGHT, overlay);
        pose.popPose();
    }

    /** A planet on its arm, so far under the sun, where it has come round to, turning as it goes. */
    private static void planet(PoseStack pose, VertexConsumer buffer, int index, float scale, float armDown, long time, float partialTick, int light,
                               int overlay) {
        Planet planet = PLANETS.get(index);
        float out = planet.orbit() * BASE_REACH * scale;
        float half = planet.size() * scale / 2;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(planet.start() + turned(time, partialTick, planet.period())));
        box(pose.last(), buffer, out / 2, -armDown, 0, out / 2, ARM / 2, ARM / 2, BRASS, light, overlay);
        box(pose.last(), buffer, out, -armDown / 2, 0, ARM / 2, armDown / 2, ARM / 2, BRASS, light, overlay);
        pose.translate(out, 0, 0);
        if (index == EARTH) {
            moon(pose, buffer, scale, turned(time, partialTick, MOON_PERIOD), light, overlay);
        }
        pose.mulPose(Axis.YP.rotation(turned(time, partialTick, PLANET_TURN)));
        box(pose.last(), buffer, 0, 0, 0, half, half, half, planet.patch(), light, overlay);
        if (index == SATURN) {
            ring(pose, buffer, half * RING_WIDTH, light, overlay);
        }
        pose.popPose();
    }

    /** The Earth's moon, come so far round it. */
    private static void moon(PoseStack pose, VertexConsumer buffer, float scale, float turn, int light, int overlay) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(turn));
        float half = MOON_SIZE * scale / 2;
        box(pose.last(), buffer, MOON_ORBIT * scale, 0, 0, half, half, half, MOON, light, overlay);
        pose.popPose();
    }

    /** Saturn's ring, flat and tilted, round the planet's middle. */
    private static void ring(PoseStack pose, VertexConsumer buffer, float half, int light, int overlay) {
        pose.pushPose();
        pose.mulPose(Axis.XP.rotation(RING_TILT));
        box(pose.last(), buffer, 0, 0, 0, half, 0.001F, half, RING, light, overlay);
        pose.popPose();
    }

    /** A box round a middle, so far out each way, every side showing the patch given. */
    private static void box(PoseStack.Pose at, VertexConsumer buffer, float x, float y, float z, float hx, float hy, float hz, int patch, int light,
                            int overlay) {
        float u0 = (float) (patch % PATCHES_ACROSS) / PATCHES_ACROSS;
        float v0 = (float) (patch / PATCHES_ACROSS) / PATCHES_DOWN;
        float u1 = u0 + 1.0F / PATCHES_ACROSS;
        float v1 = v0 + 1.0F / PATCHES_DOWN;
        float[][] corners = {
            {x - hx, y - hy, z - hz}, {x + hx, y - hy, z - hz}, {x + hx, y + hy, z - hz}, {x - hx, y + hy, z - hz},
            {x - hx, y - hy, z + hz}, {x + hx, y - hy, z + hz}, {x + hx, y + hy, z + hz}, {x - hx, y + hy, z + hz}};
        for (int[] face : FACES) {
            for (int corner = 0; corner < 4; corner++) {
                float[] point = corners[face[corner]];
                float u = corner == 0 || corner == 3 ? u0 : u1;
                float v = corner < 2 ? v1 : v0;
                buffer.addVertex(at, point[0], point[1], point[2]).setColor(-1).setUv(u, v).setOverlay(overlay).setLight(light)
                    .setNormal(at, face[4], face[5], face[6]);
            }
        }
    }

    /** Drawn whenever any of the dome it lights is in view, even with its mount out of sight up at the tip. */
    @Override
    public boolean shouldRenderOffScreen(OrreryBlockEntity orrery) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    /**
     * The whole of the dome and the cockpit under it, as last found, or before it is first found as big as the biggest
     * rocket's: the planetarium is drawn over all of it.
     */
    @Override
    public AABB getRenderBoundingBox(OrreryBlockEntity orrery) {
        BlockPos at = orrery.getBlockPos();
        OrreryDome dome = OrreryDome.known(orrery);
        if (dome == null || !dome.shaped()) {
            return new AABB(at).inflate(WIDEST_DOME, 0, WIDEST_DOME).expandTowards(0, -orrery.drop() - DEEPEST_UNDER_SUN, 0);
        }
        return dome.box(at);
    }
}
