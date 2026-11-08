package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skyliners.block.CanopyLiningBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Draws the freighter's whole canopy (see {@link CanopyLiningBlockEntity}), inside and out, where her canopy's blocks,
 * which draw nothing themselves, would step round her curve: smoked glass as one smooth dome at her surface, its outside
 * and its inside each drawn only from its own side, so a look through it is tinted once, from either side, at every
 * darkness her window dimmer sets, the outside giving back the sky's shine where it faces up; and over it her frame of
 * slim dark gunmetal ribs on the glass, standing a little out of it and in from it, one along the dome to each of its
 * ribs and a rim round its flat front, which the ribs butt into, and round its base a deeper rim set into her pod, with
 * rivets and a row of amber lamps along the frame inside. A canopy built with a straight cone and a ring half way along
 * it, before its nose was rounded, is drawn so. Left out while out of sight.
 *
 * <p>A place on the canopy is given by how far along its profile it is: from 0 at the base to 1 at the edge of the
 * flat front over the shoulder, on to 2 at the front's middle; below 0, so many blocks back past the base into her pod;
 * by how far round from her right toward up, in radians; and by how far out from the lining, in blocks: 0 the inside of
 * her blocks, the skin's thickness her surface.
 */
public class CanopyLiningRenderer implements BlockEntityRenderer<CanopyLiningBlockEntity> {
    /** How many flat faces go round the dome, along its shoulder and across its front, so its curve shows no facets. */
    private static final int ROUND = 72;
    private static final int ALONG = 12;
    private static final int ACROSS = 3;
    /** How many flat faces a rib along the dome is drawn in along it. */
    private static final int RIB_STEPS = 12;
    /** How far either side of its middle a rib along the dome reaches, in blocks. */
    private static final double RIB_HALF = 0.16;
    /**
     * How far a rib stands proud of the glass and in from it, in blocks: on the glass, so seen through it from either
     * side it stands as a slim rib, never as a fin as deep as her skin.
     */
    private static final double RIB_PROUD = 0.06;
    private static final double RIB_DEPTH = 0.1;
    /** How much further than a rib each rim stands out and in, in blocks, so the ribs butt into the rims' sides. */
    private static final double RIM_RAISE = 0.03;
    /**
     * The rim round the dome's base: how far along its surface it reaches from the base, how far back past it into her
     * pod, over the seam where her hull's blocks meet the dome, and how far in past the inside of her blocks it reaches,
     * to her cockpit's walls, in blocks.
     */
    private static final double RIM_AHEAD = 0.4;
    private static final double RIM_BEHIND = 0.35;
    private static final double RIM_INSIDE = 0.4;
    /** How far either side of the edge of the flat front its rim reaches along the surface, in blocks. */
    private static final double FRONT_RIM_HALF = 0.22;
    /** How far either side of its middle the ring half way along an older canopy's cone reaches, in blocks. */
    private static final double RING_HALF = 0.2;
    /** The rivets: how far apart along a rib, in blocks, how far out each reaches from its middle, and how proud. */
    private static final double RIVET_STEP = 0.6;
    private static final double RIVET_HALF = 0.035;
    private static final double RIVET_PROUD = 0.012;
    /** The amber lamps along the base rim inside, how many round it, how far out each reaches, and how proud of it. */
    private static final int RIM_LAMPS = 24;
    private static final double LAMP_HALF = 0.04;
    private static final double LAMP_PROUD = 0.01;
    /** How far past the dome the renderer still draws, for whether it is in view, in blocks. */
    private static final double REACH_MARGIN = 1;
    /** How far off the canopy is still drawn, in blocks: it is her hull's glass, seen as far as her blocks are. */
    private static final int VIEW_DISTANCE = 256;
    /** The step its profile is differenced over to find how fast a place moves along it, as a share of the profile. */
    private static final double STEP = 1e-4;
    /** How far ahead of the dome's front, in blocks, the light her canopy's outside is drawn with is taken. */
    private static final double OUTSIDE_AHEAD = 1.5;
    /**
     * The glass at each darkness from clear to all but black: an even neutral smoke, denser each step, the same tint
     * from inside and out.
     */
    private static final int[] GLASS = {0x60181A1C, 0x9C111214, 0xC80A0A0C, 0xEA040405};
    /**
     * The sky the outside of the glass gives back, its shine, rounding the dome and keeping it glass even at its
     * darkest: how much of it at most where it faces straight up, and where it is seen right edge on.
     */
    private static final int SHEEN = 0xFF8C9AA8;
    private static final double SHEEN_UP = 0.35;
    private static final double SHEEN_EDGE = 0.6;
    /**
     * Her frame's gunmetal: where it looks out, the plate of her turret domes' ribs; where it looks in, a shade darker;
     * its sides between; its rivets' heads; and its amber lamps.
     */
    private static final int RIB_OUTSIDE = 0xFF5A616A;
    private static final int RIB_INSIDE = 0xFF3D4249;
    private static final int RIB_SIDE = 0xFF464C54;
    private static final int RIVET = 0xFF7E858D;
    private static final int LAMP = 0xFFFFB040;

    @Override
    public void render(CanopyLiningBlockEntity lining, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        CanopyLiningBlockEntity.Cone cone = lining.cone();
        if (cone == null) {
            return;
        }
        Frame frame = new Frame(cone, lining.nose());
        Vec3 middle = frame.point(0.5, 0, 0);
        float reach = (float) (cone.length() / 2 + cone.baseRadius() + cone.skin() + REACH_MARGIN);
        if (!Sight.ahead(pose.last(), (float) middle.x, (float) middle.y, (float) middle.z, reach)) {
            return;
        }
        int outside = outsideLight(lining, frame, light);
        Lights lights = new Lights(light, outside, LightTexture.pack(Math.max(LightTexture.block(light), LightTexture.block(outside)),
            Math.max(LightTexture.sky(light), LightTexture.sky(outside))));
        PoseStack.Pose at = pose.last();
        VertexConsumer ribs = buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
        ribs(ribs, at, frame, lights);
        rivets(ribs, at, frame, light);
        lamps(ribs, at, frame);
        VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucentCull(FlatQuads.WHITE));
        glass(glass, at, frame, new Sheen(GLASS[Mth.clamp(lining.darkness(), 0, GLASS.length - 1)], at), lights);
    }

    /** The whole canopy, from its rim behind the base to its front. */
    @Override
    public AABB getRenderBoundingBox(CanopyLiningBlockEntity lining) {
        CanopyLiningBlockEntity.Cone cone = lining.cone();
        AABB own = new AABB(lining.getBlockPos());
        if (cone == null) {
            return own;
        }
        double reach = cone.length() + cone.baseRadius() + cone.skin() + REACH_MARGIN + cone.base().length();
        return own.inflate(reach);
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    /**
     * The light just ahead of the dome's front, out in the air, which its outside is drawn with: inside, where the
     * lining stands, may be darker or brighter than the day outside. The lining's own where there is no level.
     */
    private static int outsideLight(CanopyLiningBlockEntity lining, Frame frame, int inside) {
        if (lining.getLevel() == null) {
            return inside;
        }
        Vec3 ahead = frame.point(2, 0, frame.cone().skin() + OUTSIDE_AHEAD);
        BlockPos spot = BlockPos.containing(Vec3.atLowerCornerOf(lining.getBlockPos()).add(ahead));
        return LevelRenderer.getLightColor(lining.getLevel(), spot);
    }

    /** The light the canopy's inside, its outside and the sides of its ribs, seen from both, are drawn with. */
    private record Lights(int inside, int outside, int both) {
    }

    /**
     * A place on the canopy's profile at an offset out from the lining: how far along its axis from the base, how far
     * out from the axis, and the way out of its surface there, as how much of it points out from the axis and how
     * much toward her bow.
     */
    private record Spot(double along, double radius, double out, double ahead) {
    }

    /**
     * The canopy in the block's frame: from its base's middle toward her bow, her right and up. Its places are given as
     * the class's description has it.
     */
    private record Frame(CanopyLiningBlockEntity.Cone cone, Vec3 base, Vec3 forward, Vec3 right) {
        private static final Vec3 UP = new Vec3(0, 1, 0);

        Frame(CanopyLiningBlockEntity.Cone cone, Direction nose) {
            this(cone, new Vec3(0.5, 0.5, 0.5).add(cone.base()), Vec3.atLowerCornerOf(nose.getNormal()),
                Vec3.atLowerCornerOf(nose.getClockWise().getNormal()));
        }

        /** The profile at a place along it, so far out from the lining. */
        Spot spot(double profile, double off) {
            if (profile < 0) {
                return new Spot(profile, spot(0, off).radius(), 1, 0);
            }
            if (profile > 1) {
                Spot edge = spot(1, off);
                return new Spot(edge.along(), edge.radius() * (2 - profile), 0, 1);
            }
            return cone.rounded() ? shoulder(profile, off) : straight(profile, off);
        }

        /**
         * The dome's shoulder, a quarter ellipse from the base, where it meets her pod's round part level, round to
         * the edge of the flat front, where it meets it level; farther out, as long again as it is out and as wide.
         */
        private Spot shoulder(double profile, double off) {
            double angle = profile * Mth.HALF_PI;
            double deep = cone.length() + off;
            double wide = cone.baseRadius() - cone.tipRadius();
            double out = Math.cos(angle) / wide;
            double ahead = Math.sin(angle) / deep;
            double norm = Math.hypot(out, ahead);
            return new Spot(deep * Math.sin(angle), cone.tipRadius() + off + wide * Math.cos(angle), out / norm, ahead / norm);
        }

        /** A straight cone from the base to the flat front, as older canopies were built; farther out, as far again. */
        private Spot straight(double profile, double off) {
            double slope = (cone.baseRadius() - cone.tipRadius()) / cone.length();
            double tilt = Math.sqrt(1 + slope * slope);
            double along = profile * (cone.length() + off);
            return new Spot(along, cone.baseRadius() - slope * along + off * tilt, 1 / tilt, slope / tilt);
        }

        /** The way out from the axis at a place round it, a unit long. */
        Vec3 outward(double round) {
            return right.scale(Math.cos(round)).add(UP.scale(Math.sin(round)));
        }

        /** The point at a place on the canopy. */
        Vec3 point(double profile, double round, double off) {
            Spot spot = spot(profile, off);
            return base.add(forward.scale(spot.along())).add(outward(round).scale(spot.radius()));
        }

        /** The way out of the canopy's surface at a place, a unit long. */
        Vec3 normal(double profile, double round) {
            Spot spot = spot(profile, 0);
            return outward(round).scale(spot.out()).add(forward.scale(spot.ahead()));
        }

        /** The way round the dome at a place, from her right toward up, a unit long. */
        Vec3 roundward(double round) {
            return outward(round + Mth.HALF_PI);
        }

        /** How far a place on the canopy's outside moves along its surface as its place along the profile grows, in blocks. */
        double speed(double profile) {
            double from = profile < 1 && profile + STEP > 1 ? profile - STEP : profile;
            Spot a = spot(from, cone.skin());
            Spot b = spot(from + STEP, cone.skin());
            return Math.max(Math.hypot(b.along() - a.along(), b.radius() - a.radius()) / STEP, Mth.EPSILON);
        }

        /** The place along the profile so many blocks along the canopy's outside past another, back if negative. */
        double past(double profile, double blocks) {
            return profile + blocks / speed(blocks < 0 ? profile - STEP : profile);
        }

        /** The place along the profile a share of the canopy's length along it from the base. */
        double profileAt(double share) {
            return cone.rounded() ? Math.asin(Mth.clamp(share, 0, 1)) / Mth.HALF_PI : share;
        }
    }

    /**
     * The glass's colour at its darkness, and the pose it is drawn with, which takes the block's frame to the camera's,
     * where the eye stands at the origin, for its shine.
     */
    private record Sheen(int colour, PoseStack.Pose pose) {
        /**
         * The colour of the glass's outside at a point, facing a way: with the sky's shine the more it faces up, and
         * the more it is seen edge on, as glass gives back more the flatter it is looked across, so the dome's round
         * shows at its rim.
         */
        int at(Vec3 point, Vec3 normal) {
            double up = Math.max(normal.y, 0);
            Vector3f seen = pose.pose().transformPosition((float) point.x, (float) point.y, (float) point.z, new Vector3f());
            Vector3f facing = pose.transformNormal(normal.toVector3f(), new Vector3f());
            double across = seen.lengthSquared() < Mth.EPSILON ? 0 : 1 - Math.abs(facing.dot(seen) / seen.length());
            double share = SHEEN_UP * up * up + SHEEN_EDGE * across * across * across;
            return FastColor.ARGB32.lerp((float) Math.min(share, 1), colour, SHEEN);
        }
    }

    /** The glass, outside and inside, from its base over its shoulder and across its front. */
    private static void glass(VertexConsumer consumer, PoseStack.Pose pose, Frame frame, Sheen sheen, Lights lights) {
        for (int step = 0; step < ALONG + ACROSS; step++) {
            double near = step < ALONG ? (double) step / ALONG : 1 + (double) (step - ALONG) / ACROSS;
            double far = step < ALONG ? (double) (step + 1) / ALONG : 1 + (double) (step + 1 - ALONG) / ACROSS;
            glassRing(consumer, pose, frame, near, far, sheen, lights);
        }
    }

    /**
     * A ring of the glass between two places along the profile, at her surface: its outside facing out, with its shine,
     * and its inside facing in, each drawn only from its own side.
     */
    private static void glassRing(VertexConsumer consumer, PoseStack.Pose pose, Frame frame, double near, double far, Sheen sheen, Lights lights) {
        double skin = frame.cone().skin();
        for (int face = 0; face < ROUND; face++) {
            double from = Mth.TWO_PI * face / ROUND;
            double to = Mth.TWO_PI * (face + 1) / ROUND;
            Vec3[] ways = {frame.normal(near, from), frame.normal(far, from), frame.normal(far, to), frame.normal(near, to)};
            Vec3[] points = {frame.point(near, from, skin), frame.point(far, from, skin), frame.point(far, to, skin), frame.point(near, to, skin)};
            for (int corner = 0; corner < points.length; corner++) {
                vertex(consumer, pose, lights.outside(), sheen.at(points[corner], ways[corner]), ways[corner], points[corner]);
            }
            for (int corner = points.length - 1; corner >= 0; corner--) {
                vertex(consumer, pose, lights.inside(), sheen.colour(), ways[corner].scale(-1), points[corner]);
            }
        }
    }

    /**
     * Her frame: the rim round the base, from the inside of her cockpit's walls out past her surface, set into her pod;
     * and on the glass the rim round the front, the ring half way along an older canopy's cone, and a rib along the dome
     * between the rims at each place round it, evenly from straight up.
     */
    private static void ribs(VertexConsumer consumer, PoseStack.Pose pose, Frame frame, Lights lights) {
        double skin = frame.cone().skin();
        Layer base = new Layer(-RIM_INSIDE, skin + RIB_PROUD + RIM_RAISE, lights.outside(), lights.inside());
        band(consumer, pose, frame, -RIM_BEHIND, frame.past(0, RIM_AHEAD), base, lights.both());
        onGlass(consumer, pose, frame, new Layer(skin - RIB_DEPTH, skin + RIB_PROUD, lights.outside(), lights.inside()), lights.both());
    }

    /**
     * How deep a part of her frame runs, as offsets out from the lining, and the light its face looking out and its
     * face looking in take.
     */
    private record Layer(double low, double high, int outLight, int inLight) {
        /** The part standing so much further out and in, for her rims, which her ribs butt into. */
        Layer raised() {
            return new Layer(low - RIM_RAISE, high + RIM_RAISE, outLight, inLight);
        }
    }

    /** Her frame on the glass: the front's rim, an older canopy's ring, and the ribs. */
    private static void onGlass(VertexConsumer consumer, PoseStack.Pose pose, Frame frame, Layer layer, int sides) {
        CanopyLiningBlockEntity.Cone cone = frame.cone();
        double rimEnd = frame.past(0, RIM_AHEAD);
        double frontStart = frame.past(1, -FRONT_RIM_HALF);
        double frontEnd = 1 + FRONT_RIM_HALF / Math.max(frame.spot(1, cone.skin()).radius(), Mth.EPSILON);
        band(consumer, pose, frame, frontStart, frontEnd, layer.raised(), sides);
        if (cone.ringed()) {
            double ring = frame.profileAt(cone.ringAlong());
            band(consumer, pose, frame, frame.past(ring, -RING_HALF), frame.past(ring, RING_HALF), layer.raised(), sides);
        }
        for (int rib = 0; rib < cone.ribs(); rib++) {
            spoke(consumer, pose, frame, Mth.HALF_PI + Mth.TWO_PI * rib / cone.ribs(), rimEnd, frontStart, layer, sides);
        }
    }

    /**
     * A rib along the dome at a place round it, between two places along its profile, as wide all the way, as deep
     * as its part: its face looking out, its face looking in and its two sides.
     */
    private static void spoke(VertexConsumer consumer, PoseStack.Pose pose, Frame frame, double round, double from, double to, Layer layer,
                              int sides) {
        double high = layer.high();
        double low = layer.low();
        Vec3 left = frame.roundward(round);
        for (int step = 0; step < RIB_STEPS; step++) {
            double near = Mth.lerp((double) step / RIB_STEPS, from, to);
            double far = Mth.lerp((double) (step + 1) / RIB_STEPS, from, to);
            double nearHalf = RIB_HALF / frame.spot(near, high).radius();
            double farHalf = RIB_HALF / frame.spot(far, high).radius();
            Vec3 nearOut = frame.normal(near, round);
            Vec3 farOut = frame.normal(far, round);
            quad(consumer, pose, layer.outLight(), RIB_OUTSIDE, nearOut, farOut, frame.point(near, round - nearHalf, high),
                frame.point(far, round - farHalf, high), frame.point(far, round + farHalf, high), frame.point(near, round + nearHalf, high));
            quad(consumer, pose, layer.inLight(), RIB_INSIDE, nearOut.scale(-1), farOut.scale(-1), frame.point(near, round + nearHalf, low),
                frame.point(far, round + farHalf, low), frame.point(far, round - farHalf, low), frame.point(near, round - nearHalf, low));
            quad(consumer, pose, sides, RIB_SIDE, left, left, frame.point(near, round + nearHalf, high),
                frame.point(far, round + farHalf, high), frame.point(far, round + farHalf, low), frame.point(near, round + nearHalf, low));
            quad(consumer, pose, sides, RIB_SIDE, left.scale(-1), left.scale(-1), frame.point(near, round - nearHalf, low),
                frame.point(far, round - farHalf, low), frame.point(far, round - farHalf, high), frame.point(near, round - nearHalf, high));
        }
    }

    /**
     * A rim all round the canopy between two places along its profile, as deep as its part: its face looking out, its
     * face looking in and its two edges.
     */
    private static void band(VertexConsumer consumer, PoseStack.Pose pose, Frame frame, double near, double far, Layer layer, int sides) {
        double high = layer.high();
        double low = layer.low();
        for (int face = 0; face < ROUND; face++) {
            double from = Mth.TWO_PI * face / ROUND;
            double to = Mth.TWO_PI * (face + 1) / ROUND;
            bandFace(consumer, pose, frame, near, far, from, to, high, layer.outLight(), RIB_OUTSIDE);
            bandFace(consumer, pose, frame, far, near, from, to, low, layer.inLight(), RIB_INSIDE);
            Vec3 back = alongNormal(frame, near, (from + to) / 2).scale(-1);
            quad(consumer, pose, sides, RIB_SIDE, back, back, frame.point(near, from, high), frame.point(near, to, high),
                frame.point(near, to, low), frame.point(near, from, low));
            Vec3 ahead = alongNormal(frame, far, (from + to) / 2);
            quad(consumer, pose, sides, RIB_SIDE, ahead, ahead, frame.point(far, from, low), frame.point(far, to, low),
                frame.point(far, to, high), frame.point(far, from, high));
        }
    }

    /**
     * A face of a rim between two places along the profile and two round it, at an offset: facing out where the first
     * place along is the nearer, in where it is the farther.
     */
    private static void bandFace(VertexConsumer consumer, PoseStack.Pose pose, Frame frame, double first, double second, double from, double to,
                                 double off, int light, int colour) {
        double sign = first < second ? 1 : -1;
        vertex(consumer, pose, light, colour, frame.normal(first, from).scale(sign), frame.point(first, from, off));
        vertex(consumer, pose, light, colour, frame.normal(second, from).scale(sign), frame.point(second, from, off));
        vertex(consumer, pose, light, colour, frame.normal(second, to).scale(sign), frame.point(second, to, off));
        vertex(consumer, pose, light, colour, frame.normal(first, to).scale(sign), frame.point(first, to, off));
    }

    /** The way along the canopy's surface toward its front at a place, a unit long: square to its normal there. */
    private static Vec3 alongNormal(Frame frame, double profile, double round) {
        Spot spot = frame.spot(profile, 0);
        return frame.forward().scale(spot.out()).subtract(frame.outward(round).scale(spot.ahead()));
    }

    /** Rivets down the middle of each rib inside and round the base's rim. */
    private static void rivets(VertexConsumer consumer, PoseStack.Pose pose, Frame frame, int light) {
        CanopyLiningBlockEntity.Cone cone = frame.cone();
        double from = frame.past(0, RIM_AHEAD);
        double to = frame.past(1, -FRONT_RIM_HALF);
        int count = (int) Math.floor(cone.length() / RIVET_STEP);
        for (int rib = 0; rib < cone.ribs(); rib++) {
            double round = Mth.HALF_PI + Mth.TWO_PI * rib / cone.ribs();
            for (int rivet = 1; rivet < count; rivet++) {
                double along = Mth.lerp((double) rivet / count, from, to);
                stud(consumer, pose, frame, along, round, RIVET_HALF, cone.skin() - RIB_DEPTH - RIVET_PROUD, RIVET, light);
            }
        }
        double rimMiddle = frame.past(0, (RIM_AHEAD - RIM_BEHIND) / 2);
        int round = (int) Math.floor(Mth.TWO_PI * cone.baseRadius() / RIVET_STEP);
        for (int rivet = 0; rivet < round; rivet++) {
            stud(consumer, pose, frame, rimMiddle, Mth.TWO_PI * (rivet + 0.5) / round, RIVET_HALF, -RIM_INSIDE - RIVET_PROUD, RIVET, light);
        }
    }

    /** The amber lamps round the base's rim inside, glowing whatever the light. */
    private static void lamps(VertexConsumer consumer, PoseStack.Pose pose, Frame frame) {
        double along = frame.past(0, RIM_AHEAD / 2);
        for (int lamp = 0; lamp < RIM_LAMPS; lamp++) {
            double round = Mth.TWO_PI * lamp / RIM_LAMPS;
            stud(consumer, pose, frame, along, round, LAMP_HALF, -RIM_INSIDE - LAMP_PROUD, LAMP, LightTexture.FULL_BRIGHT);
        }
    }

    /** A little square facing in, round a place on the canopy, so far out each way, at an offset in from the lining. */
    private static void stud(VertexConsumer consumer, PoseStack.Pose pose, Frame frame, double profile, double round, double half, double off,
                             int colour, int light) {
        double alongHalf = half / frame.speed(profile);
        double roundHalf = half / frame.spot(profile, off).radius();
        Vec3 in = frame.normal(profile, round).scale(-1);
        quad(consumer, pose, light, colour, in, in, frame.point(profile - alongHalf, round - roundHalf, off),
            frame.point(profile - alongHalf, round + roundHalf, off), frame.point(profile + alongHalf, round + roundHalf, off),
            frame.point(profile + alongHalf, round - roundHalf, off));
    }

    /** A quad of one colour, its first two corners facing one way and its last two another, so it shades as it curves. */
    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, int light, int colour, Vec3 nearWay, Vec3 farWay, Vec3 a, Vec3 b, Vec3 c,
                             Vec3 d) {
        vertex(consumer, pose, light, colour, nearWay, a);
        vertex(consumer, pose, light, colour, farWay, b);
        vertex(consumer, pose, light, colour, farWay, c);
        vertex(consumer, pose, light, colour, nearWay, d);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int light, int colour, Vec3 normal, Vec3 at) {
        consumer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(colour).setUv(0.5F, 0.5F)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
