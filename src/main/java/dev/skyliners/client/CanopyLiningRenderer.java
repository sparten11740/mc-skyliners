package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skyliners.block.CanopyLiningBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the freighter's canopy from inside (see {@link CanopyLiningBlockEntity}) as one smooth cone, its blunt glazed
 * front a flat disc, where her canopy's blocks, drawn from outside only, would step round her curve: tinted glass in
 * flat faces finer than a block, darkening with her window dimmer, the same smoke at every step, so the dimmed canopy
 * is as smooth as the clear one; and over it her frame as chunky dark gunmetal ribs standing in from the glass, one
 * along the cone at each of her struts, a ring half way along and a wide rim at the cone's base and round its front,
 * each studded with rivets, amber lamps round the rim. Both show from either side, so from outside her frame shows
 * through her glass and her glass is as smoky as when every face of it was drawn. Left out while out of sight.
 */
public class CanopyLiningRenderer implements BlockEntityRenderer<CanopyLiningBlockEntity> {
    /** How many flat faces go round the cone, and how many along it, so its curve shows no facets. */
    private static final int ROUND = 72;
    private static final int ALONG = 8;
    /** How many flat faces a rib along the cone is drawn in along it. */
    private static final int RIB_STEPS = 8;
    /** How far a rib stands in from the glass toward the cone's axis, in blocks. */
    private static final double RIB_DEPTH = 0.14;
    /** How far either side of its middle a rib along the cone and the ring reach, in blocks. */
    private static final double RIB_HALF = 0.22;
    private static final double RING_HALF = 0.2;
    /**
     * The rim round the cone's base: how far along the cone it reaches from the base, and how far back past it into
     * her pod, where it meets her cockpit's walls, in blocks.
     */
    private static final double RIM = 0.45;
    private static final double RIM_BEHIND = 0.35;
    /** The rim round the front disc: how far in from the disc's edge it reaches, and back along the cone, in blocks. */
    private static final double FRONT_RIM = 0.35;
    /** The rivets: how far apart along a rib, in blocks, how far out each reaches from its middle, and how proud. */
    private static final double RIVET_STEP = 0.6;
    private static final double RIVET_HALF = 0.035;
    private static final double RIVET_PROUD = 0.012;
    /** The amber lamps along the base rim, how many round it, how far out each reaches, and how proud of the rim. */
    private static final int RIM_LAMPS = 24;
    private static final double LAMP_HALF = 0.04;
    private static final double LAMP_PROUD = 0.01;
    /** How far past the cone the renderer still draws, for whether it is in view, in blocks. */
    private static final double REACH_MARGIN = 1;
    /**
     * The glass at each darkness from clear to all but black: the smoked glass's own colour and, a pane at a time, as
     * dense as her dimming glass's panes are drawn outside.
     */
    private static final int[] GLASS = {0x70101214, 0xA0181C24, 0xC80E1016, 0xEE06070A};
    private static final int RIB = 0xFF3D4249;
    private static final int RIB_SIDE = 0xFF2A2E33;
    private static final int RIVET = 0xFF7E858D;
    private static final int LAMP = 0xFFFFB040;

    @Override
    public void render(CanopyLiningBlockEntity lining, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        CanopyLiningBlockEntity.Cone cone = lining.cone();
        if (cone == null) {
            return;
        }
        Frame frame = new Frame(cone, lining.nose());
        Vec3 middle = frame.at(0.5, 0, 0);
        float reach = (float) (cone.length() / 2 + cone.baseRadius() + REACH_MARGIN);
        if (!Sight.ahead(pose.last(), (float) middle.x, (float) middle.y, (float) middle.z, reach)) {
            return;
        }
        PoseStack.Pose at = pose.last();
        VertexConsumer ribs = buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
        ribs(ribs, at, frame, light);
        rivets(ribs, at, frame, light);
        lamps(ribs, at, frame);
        VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucent(FlatQuads.WHITE));
        glass(glass, at, frame, GLASS[Mth.clamp(lining.darkness(), 0, GLASS.length - 1)], light);
    }

    /** The whole canopy, from its rim behind the base to its front. */
    @Override
    public AABB getRenderBoundingBox(CanopyLiningBlockEntity lining) {
        CanopyLiningBlockEntity.Cone cone = lining.cone();
        AABB own = new AABB(lining.getBlockPos());
        if (cone == null) {
            return own;
        }
        double reach = cone.length() + cone.baseRadius() + REACH_MARGIN + cone.base().length();
        return own.inflate(reach);
    }

    /**
     * The cone in the block's frame: from its base's middle toward her bow, her right and up, a point given by how far
     * along it (0 at the base, 1 at the front, past either end beyond it), how far round from her right toward up, in
     * radians, and how far in from the glass toward the axis, in blocks.
     */
    private record Frame(CanopyLiningBlockEntity.Cone cone, Vec3 base, Vec3 forward, Vec3 right) {
        Frame(CanopyLiningBlockEntity.Cone cone, Direction nose) {
            this(cone, new Vec3(0.5, 0.5, 0.5).add(cone.base()), Vec3.atLowerCornerOf(nose.getNormal()),
                Vec3.atLowerCornerOf(nose.getClockWise().getNormal()));
        }

        /** The glass's radius so far along: the pod's own behind the base, narrowing to the front's. */
        double radius(double along) {
            return Mth.lerp(Mth.clamp(along, 0, 1), cone.baseRadius(), cone.tipRadius());
        }

        Vec3 at(double along, double round, double in) {
            double radius = radius(along) - in;
            return base.add(forward.scale(cone.length() * along)).add(right.scale(Math.cos(round) * radius))
                .add(0, Math.sin(round) * radius, 0);
        }

        /** The way in toward the axis at a place round the cone, a unit long. */
        Vec3 inward(double round) {
            return right.scale(-Math.cos(round)).add(0, -Math.sin(round), 0);
        }

        /** A point on the front disc, so far out from its middle and round from her right toward up, so far in. */
        Vec3 front(double out, double round, double in) {
            return base.add(forward.scale(cone.length() - in)).add(right.scale(Math.cos(round) * out)).add(0, Math.sin(round) * out, 0);
        }

        /** How far along the cone a distance along its axis is, as a share of its length. */
        double share(double blocks) {
            return blocks / cone.length();
        }
    }

    /** The glass: the cone in flat faces and the front disc. */
    private static void glass(VertexConsumer consumer, PoseStack.Pose pose, Frame frame, int colour, int light) {
        for (int step = 0; step < ALONG; step++) {
            glassRing(consumer, pose, frame, (double) step / ALONG, (double) (step + 1) / ALONG, colour, light);
        }
        Vec3 middle = frame.front(0, 0, 0);
        for (int face = 0; face < ROUND; face++) {
            double from = Mth.TWO_PI * face / ROUND;
            double to = Mth.TWO_PI * (face + 1) / ROUND;
            Vec3 a = frame.front(frame.cone().tipRadius(), from, 0);
            Vec3 b = frame.front(frame.cone().tipRadius(), to, 0);
            quad(consumer, pose, light, colour, frame.forward().scale(-1), middle, b, a, a);
        }
    }

    /** A ring of the cone's glass between two places along it. */
    private static void glassRing(VertexConsumer consumer, PoseStack.Pose pose, Frame frame, double near, double far, int colour, int light) {
        for (int face = 0; face < ROUND; face++) {
            double from = Mth.TWO_PI * face / ROUND;
            double to = Mth.TWO_PI * (face + 1) / ROUND;
            quad(consumer, pose, light, colour, frame.inward((from + to) / 2), frame.at(near, from, 0), frame.at(far, from, 0), frame.at(far, to, 0),
                frame.at(near, to, 0));
        }
    }

    /** Her frame: a rib along the cone at each strut, the ring half way, the rim round the base and round the front. */
    private static void ribs(VertexConsumer consumer, PoseStack.Pose pose, Frame frame, int light) {
        CanopyLiningBlockEntity.Cone cone = frame.cone();
        double rim = frame.share(RIM);
        double frontRim = 1 - frame.share(FRONT_RIM);
        for (int rib = 0; rib < cone.ribs(); rib++) {
            spoke(consumer, pose, frame, Mth.TWO_PI * rib / cone.ribs(), rim, frontRim, light);
        }
        band(consumer, pose, frame, cone.ringAlong() - frame.share(RING_HALF), cone.ringAlong() + frame.share(RING_HALF), light);
        band(consumer, pose, frame, -frame.share(RIM_BEHIND), rim, light);
        band(consumer, pose, frame, frontRim, 1, light);
        frontRing(consumer, pose, frame, light);
    }

    /** A rib along the cone at a place round it, from one place along it to another, as wide all the way. */
    private static void spoke(VertexConsumer consumer, PoseStack.Pose pose, Frame frame, double round, double from, double to, int light) {
        Vec3 in = frame.inward(round);
        for (int step = 0; step < RIB_STEPS; step++) {
            double near = Mth.lerp((double) step / RIB_STEPS, from, to);
            double far = Mth.lerp((double) (step + 1) / RIB_STEPS, from, to);
            double nearHalf = RIB_HALF / frame.radius(near);
            double farHalf = RIB_HALF / frame.radius(far);
            Vec3 nearLeft = frame.at(near, round + nearHalf, RIB_DEPTH);
            Vec3 nearRight = frame.at(near, round - nearHalf, RIB_DEPTH);
            Vec3 farLeft = frame.at(far, round + farHalf, RIB_DEPTH);
            Vec3 farRight = frame.at(far, round - farHalf, RIB_DEPTH);
            quad(consumer, pose, light, RIB, in, nearRight, nearLeft, farLeft, farRight);
            quad(consumer, pose, light, RIB_SIDE, frame.inward(round + Mth.HALF_PI), frame.at(near, round + nearHalf, 0), nearLeft, farLeft,
                frame.at(far, round + farHalf, 0));
            quad(consumer, pose, light, RIB_SIDE, frame.inward(round - Mth.HALF_PI), nearRight, frame.at(near, round - nearHalf, 0),
                frame.at(far, round - farHalf, 0), farRight);
        }
    }

    /** A band all round the cone between two places along it, standing in from the glass, with its edges. */
    private static void band(VertexConsumer consumer, PoseStack.Pose pose, Frame frame, double near, double far, int light) {
        for (int face = 0; face < ROUND; face++) {
            double from = Mth.TWO_PI * face / ROUND;
            double to = Mth.TWO_PI * (face + 1) / ROUND;
            Vec3 in = frame.inward((from + to) / 2);
            quad(consumer, pose, light, RIB, in, frame.at(near, from, RIB_DEPTH), frame.at(near, to, RIB_DEPTH), frame.at(far, to, RIB_DEPTH),
                frame.at(far, from, RIB_DEPTH));
            quad(consumer, pose, light, RIB_SIDE, frame.forward(), frame.at(near, from, 0), frame.at(near, to, 0), frame.at(near, to, RIB_DEPTH),
                frame.at(near, from, RIB_DEPTH));
            quad(consumer, pose, light, RIB_SIDE, frame.forward().scale(-1), frame.at(far, from, RIB_DEPTH), frame.at(far, to, RIB_DEPTH),
                frame.at(far, to, 0), frame.at(far, from, 0));
        }
    }

    /** The rim on the front disc, round inside its edge, standing in from the glass toward her stern. */
    private static void frontRing(VertexConsumer consumer, PoseStack.Pose pose, Frame frame, int light) {
        double outer = frame.cone().tipRadius();
        double inner = outer - FRONT_RIM;
        Vec3 back = frame.forward().scale(-1);
        for (int face = 0; face < ROUND; face++) {
            double from = Mth.TWO_PI * face / ROUND;
            double to = Mth.TWO_PI * (face + 1) / ROUND;
            quad(consumer, pose, light, RIB, back, frame.front(inner, from, RIB_DEPTH), frame.front(outer, from, RIB_DEPTH),
                frame.front(outer, to, RIB_DEPTH), frame.front(inner, to, RIB_DEPTH));
            quad(consumer, pose, light, RIB_SIDE, frame.inward((from + to) / 2).scale(-1), frame.front(inner, from, 0),
                frame.front(inner, from, RIB_DEPTH), frame.front(inner, to, RIB_DEPTH), frame.front(inner, to, 0));
        }
    }

    /** Rivets down the middle of each rib along the cone and round the base's rim. */
    private static void rivets(VertexConsumer consumer, PoseStack.Pose pose, Frame frame, int light) {
        CanopyLiningBlockEntity.Cone cone = frame.cone();
        int count = (int) Math.floor(cone.length() / RIVET_STEP);
        for (int rib = 0; rib < cone.ribs(); rib++) {
            double round = Mth.TWO_PI * rib / cone.ribs();
            for (int rivet = 1; rivet < count; rivet++) {
                rivet(consumer, pose, frame, (double) rivet / count, round, light);
            }
        }
        double rimMiddle = frame.share((RIM - RIM_BEHIND) / 2);
        int round = (int) Math.floor(Mth.TWO_PI * cone.baseRadius() / RIVET_STEP);
        for (int rivet = 0; rivet < round; rivet++) {
            rivet(consumer, pose, frame, rimMiddle, Mth.TWO_PI * (rivet + 0.5) / round, light);
        }
    }

    /** A rivet's head on a rib, a little square standing proud of it. */
    private static void rivet(VertexConsumer consumer, PoseStack.Pose pose, Frame frame, double along, double round, int light) {
        stud(consumer, pose, frame, along, round, RIVET_HALF, RIB_DEPTH + RIVET_PROUD, RIVET, light);
    }

    /** The amber lamps round the base's rim, glowing whatever the light. */
    private static void lamps(VertexConsumer consumer, PoseStack.Pose pose, Frame frame) {
        double along = frame.share(RIM / 2);
        for (int lamp = 0; lamp < RIM_LAMPS; lamp++) {
            double round = Mth.TWO_PI * lamp / RIM_LAMPS;
            stud(consumer, pose, frame, along, round, LAMP_HALF, RIB_DEPTH + LAMP_PROUD, LAMP, LightTexture.FULL_BRIGHT);
        }
    }

    /** A little square on the cone facing in, round a place on it, so far out each way and so far in from the glass. */
    private static void stud(VertexConsumer consumer, PoseStack.Pose pose, Frame frame, double along, double round, double half, double in,
                             int colour, int light) {
        double alongHalf = frame.share(half);
        double roundHalf = half / frame.radius(along);
        quad(consumer, pose, light, colour, frame.inward(round), frame.at(along - alongHalf, round - roundHalf, in),
            frame.at(along - alongHalf, round + roundHalf, in), frame.at(along + alongHalf, round + roundHalf, in),
            frame.at(along + alongHalf, round - roundHalf, in));
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, int light, int colour, Vec3 normal, Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
        vertex(consumer, pose, light, colour, normal, a);
        vertex(consumer, pose, light, colour, normal, b);
        vertex(consumer, pose, light, colour, normal, c);
        vertex(consumer, pose, light, colour, normal, d);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int light, int colour, Vec3 normal, Vec3 at) {
        consumer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(colour).setUv(0.5F, 0.5F)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
