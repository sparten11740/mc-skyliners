package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.block.OrreryBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * Draws the rocket's orrery (see {@link OrreryBlockEntity}) and the planetarium it hangs in: the night sky lining her
 * dome (see {@link PlanetariumShell}), the machined ring round the hole under it with the lamps that show her flight
 * (see {@link OculusRing}), and the orrery itself hanging from its mount, its sun and planets (see {@link
 * OrreryPlanets}) and the brass works that carry them (see {@link OrreryWorks}), turning with the game's time, the same
 * for everyone. The orrery is as big as the hole under it and the dome round it leave room for, as the dome is measured
 * on the client (see {@link OrreryDome}), and no bigger than it was built to be.
 *
 * <p>None of it is drawn unless the camera is in her cockpit or her dome, where it can be seen: Sable draws an
 * assembled rocket's block entities every frame wherever the camera looks. Everything is drawn in three batches, the
 * solid parts from one sheet, the sky, then all the light added on, as taking another buffer finishes the one before.
 */
public class OrreryRenderer implements BlockEntityRenderer<OrreryBlockEntity> {
    /** How far off the orrery and its planetarium are drawn, in blocks: from the cockpit's floor up to the tip. */
    private static final int VIEW_DISTANCE = 96;
    /**
     * Before its dome is first found: how far out from her axis the widest dome reaches, and how far under the sun the
     * deepest cockpit's floor is, in blocks, at the biggest scale.
     */
    private static final double WIDEST_DOME = 24;
    private static final double DEEPEST_UNDER_SUN = 16;
    /** How far over the mount's middle the planetarium's top is, where the rod meets it, in blocks. */
    private static final float ROD_TOP = 0.4F;

    @Override
    public void render(OrreryBlockEntity orrery, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = orrery.getLevel();
        if (level == null) {
            return;
        }
        // The dome as last found tells whether the camera is out of it, without looking it over again for that.
        Vector3f eye = Sight.eye(pose.last());
        OrreryDome known = OrreryDome.known(orrery);
        if (known != null && !known.holds(eye)) {
            return;
        }
        OrreryDome dome = OrreryDome.of(orrery, level);
        if (!dome.holds(eye)) {
            return;
        }
        OrreryClock clock = new OrreryClock(level.getGameTime(), partialTick);
        float reach = reach(orrery, dome);
        OrreryNow now = reach >= OrreryBlockEntity.REACH_LEAST ? new OrreryNow(reach, clock) : null;
        float drop = (float) orrery.drop();
        PoseStack.Pose block = pose.last();
        pose.pushPose();
        pose.translate(OrreryDome.AXIS, OrreryDome.AXIS - drop, OrreryDome.AXIS);
        PoseStack.Pose sun = pose.last();
        VertexConsumer sheet = buffers.getBuffer(RenderType.entityCutoutNoCull(OrrerySheet.TEXTURE));
        if (now != null) {
            Vector3f fromSun = new Vector3f(eye).sub(OrreryDome.AXIS, OrreryDome.AXIS - drop, OrreryDome.AXIS);
            OrreryPlanets.solid(now, new OrreryBrush(sheet, sun), fromSun, clock);
            OrreryWorks.solid(now, new OrreryBrush(sheet, sun), clock, drop + ROD_TOP, cableTop(dome, now, drop));
        }
        if (dome.shaped()) {
            planetarium(dome, level, buffers, sheet, block, clock);
        }
        if (now != null) {
            VertexConsumer glow = buffers.getBuffer(RenderType.eyes(OrreryGlow.TEXTURE));
            OrreryPlanets.glow(now, new OrreryBrush(glow, sun), OrreryBrush.Facing.of(sun), clock);
            OrreryWorks.tracks(now, new OrreryBrush(glow, sun));
        }
        pose.popPose();
    }

    /**
     * The planetarium round the orrery, in the mount's frame: the oculus ring onto the sheet the orrery went onto, the
     * sky, then the light added on, the sky's twinkling stars and the ring's lamps, leaving that buffer for the
     * orrery's own light after.
     */
    private static void planetarium(OrreryDome dome, Level level, MultiBufferSource buffers, VertexConsumer sheet, PoseStack.Pose block,
                                    OrreryClock clock) {
        boolean ring = OculusRing.fits(dome);
        if (ring) {
            OculusRing.draw(dome, new OrreryBrush(sheet, block));
        }
        PlanetariumShell.draw(dome, new OrreryBrush(buffers.getBuffer(RenderType.entityCutoutNoCull(PlanetariumShell.SKY)), block));
        OrreryBrush glow = new OrreryBrush(buffers.getBuffer(RenderType.eyes(OrreryGlow.TEXTURE)), block);
        PlanetariumShell.sparkles(dome, glow, OrreryBrush.Facing.of(block), clock);
        if (ring) {
            OculusRing.lamps(dome, glow, dome.autopilot(level), clock);
        }
    }

    /**
     * How far out the orrery's outermost planet circles, in blocks: as far as it was built to, but no further than the
     * dome as it is now leaves room for, so a rocket built before the orrery was measured this way has it fit too.
     */
    private static float reach(OrreryBlockEntity orrery, OrreryDome dome) {
        if (!dome.shaped()) {
            return (float) orrery.reach();
        }
        int overSun = 1 - Mth.floor(orrery.drop());
        return (float) Math.min(orrery.reach(), OrreryBlockEntity.reachIn(dome.oculus(), dome.openAt(overSun)));
    }

    /**
     * How high over the sun's middle the cables the ecliptic hoop hangs on meet the planetarium's sky, in blocks; the
     * rod's top where the dome has no shape.
     */
    private static float cableTop(OrreryDome dome, OrreryNow now, float drop) {
        if (!dome.shaped()) {
            return drop + ROD_TOP;
        }
        float hoop = now.reach * (float) OrreryBlockEntity.EXTENT;
        return Math.max(now.sun, dome.heightWithin(hoop) - OrreryDome.AXIS + drop);
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
