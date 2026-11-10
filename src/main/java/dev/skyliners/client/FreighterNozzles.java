package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.skyliners.Skyliners;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Craft;
import dev.skyliners.autopilot.FreighterFlight;
import dev.skyliners.block.AutopilotBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The freighter's main engines as engines, seen from behind her: over the socket each stands in, a nozzle of her own
 * standing out of her stern, a gunmetal collar narrowing through steel petals to a bright lip, sooty inside down to a
 * throat of turbine vanes round a tail cone. Drawn by each client and never built, so it stands on no block and in no
 * thruster's way (Create Propulsion counts only what lies straight out from a nozzle), weighs nothing, and shows on a
 * freighter built before it was thought of; her autopilot tells clients where her mains are with what it shows them
 * of her flight (see {@link FreighterFlight#mainRelaysOf}). It is drawn before the particles, so her plumes and her
 * glow lie over it, and passes over a nozzle out of view. None with the server config's {@code freighter.nozzleRims}
 * off.
 */
public final class FreighterNozzles {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/misc/engine_nozzle.png");
    /** How far from the camera her nozzles are drawn, in blocks: past that one is a few pixels of her stern. */
    private static final double SEEN = 256;
    /** How many flat pieces a nozzle is cut into round its axis. */
    private static final int SEGMENTS = 24;
    /** How many bands, one above the other, the texture holds, one for each part of a nozzle. */
    private static final float BANDS = 4;
    private static final int COLLAR = 0;
    private static final int PETALS = 1;
    private static final int LIP = 2;
    private static final int THROAT = 3;
    /** How far a nozzle reaches from its axis and out of her stern, in blocks, for leaving it out unseen. */
    private static final double REACH = 1.6;
    /**
     * At most how many blocks a nozzle stands out past its thruster's face, where her rim steps out beside it (see
     * {@link #standOff}).
     */
    private static final int STAND_OFF_MOST = 2;
    /** The blocks round a nozzle's mouth its bell would cut into, so many across and up from its axis. */
    private static final int[][] ROUND_MOUTH = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};
    /** Which way a part of a nozzle faces, to be seen from the right side: off its axis, toward it, or astern. */
    private enum Facing { OUT, IN, ASTERN }

    /**
     * One part of a nozzle, a ring round its axis between two circles, each so far astern of the thruster's face and so
     * far from the axis, in blocks: on the texture's band of that number. The collar starts inside her hull, so it
     * meets her skin wherever her rim steps forward beside a thruster.
     */
    private record Part(float astern, float out, float asternTo, float outTo, int band, Facing facing) {
    }

    private static final Part[] PARTS = {
        new Part(-1.5F, 1.3F, 0.25F, 1.3F, COLLAR, Facing.OUT),
        new Part(0.25F, 1.3F, 0.35F, 1.2F, COLLAR, Facing.OUT),
        new Part(0.35F, 1.2F, 1.15F, 1F, PETALS, Facing.OUT),
        new Part(1.15F, 1F, 1.22F, 0.97F, LIP, Facing.OUT),
        new Part(1.22F, 0.97F, 1.22F, 0.8F, LIP, Facing.ASTERN),
        new Part(1.22F, 0.8F, 0.3F, 0.62F, LIP, Facing.IN),
        new Part(0.3F, 0.62F, 0.3F, 0F, THROAT, Facing.ASTERN),
        new Part(0.3F, 0.26F, 0.7F, 0.02F, THROAT, Facing.OUT),
    };

    private FreighterNozzles() {
    }

    /** After the entities, before the particles: every freighter's nozzles near the camera and in its view. */
    public static void stage(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || level == null || !SkylinersConfig.FREIGHTER_NOZZLE_RIMS.get()) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        RenderType type = RenderType.entitySolid(TEXTURE);
        VertexConsumer consumer = buffers.getBuffer(type);
        for (AutopilotBlockEntity autopilot : AutopilotBlockEntity.loaded(level)) {
            if (autopilot.craft() == Craft.FREIGHTER && autopilot.built()) {
                nozzles(level, consumer, event.getPoseStack(), event.getFrustum(), autopilot, camera, partialTick);
            }
        }
        buffers.endBatch(type);
    }

    /**
     * One freighter's nozzles, each where its thruster is in the world this frame, a block astern of its relay, and
     * standing out past whatever of her rim stands out round it.
     */
    private static void nozzles(ClientLevel level, VertexConsumer consumer, PoseStack pose, Frustum frustum, AutopilotBlockEntity autopilot, Vec3 camera,
                                float partialTick) {
        Direction astern = autopilot.getBlockState().getValue(AutopilotBlock.FACING).getOpposite();
        Vec3 out = Vec3.atLowerCornerOf(astern.getNormal());
        SableClient.Placing placing = SableClient.placing(Vec3.atCenterOf(autopilot.getBlockPos()), partialTick);
        for (BlockPos relay : FreighterFlight.mainRelaysOf(autopilot)) {
            BlockPos thruster = autopilot.getBlockPos().offset(relay).relative(astern);
            Vec3 face = Vec3.atCenterOf(thruster).add(out.scale(0.5));
            Vec3 near = LaserBolts.inWorld(placing, face);
            boolean inView = near.distanceTo(camera) <= SEEN && (frustum == null || frustum.isVisible(new AABB(near, near).inflate(REACH + STAND_OFF_MOST)));
            if (inView && !level.getBlockState(thruster).isAir()) {
                int standing = standOff(level, thruster, astern);
                Vec3 at = LaserBolts.inWorld(placing, face.add(out.scale(standing)));
                nozzle(consumer, pose, at.subtract(camera), placing, astern, SableClient.light(level, thruster.relative(astern, standing + 1)));
            }
        }
    }

    /**
     * How many blocks a nozzle stands out past its thruster's face: past every layer astern of it with a block round
     * its mouth, where her rim, cut into blocks along its curve, steps out a block beside a thruster, so that none of
     * her blocks cuts into its bell. Its collar, starting inside her hull, meets her skin wherever it stands out.
     */
    private static int standOff(ClientLevel level, BlockPos thruster, Direction astern) {
        Direction across = astern.getClockWise();
        int standing = 0;
        while (standing < STAND_OFF_MOST && roundMouth(level, thruster.relative(astern, standing + 1), across)) {
            standing++;
        }
        return standing;
    }

    /** Whether any of the blocks round a nozzle's mouth stands in a layer square to her stern, its axis at a block. */
    private static boolean roundMouth(ClientLevel level, BlockPos axis, Direction across) {
        for (int[] off : ROUND_MOUTH) {
            if (!level.getBlockState(axis.relative(across, off[0]).above(off[1])).isAir()) {
                return true;
            }
        }
        return false;
    }

    /** One nozzle, its axis astern of her, its face at a point from the camera, turned with her. */
    private static void nozzle(VertexConsumer consumer, PoseStack pose, Vec3 at, SableClient.Placing placing, Direction astern, int light) {
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        if (placing != null) {
            pose.mulPose(new Quaternionf(placing.turn()));
        }
        pose.mulPose(Axis.YP.rotationDegrees(-astern.toYRot()));
        for (Part part : PARTS) {
            part(consumer, pose.last(), part, light);
        }
        pose.popPose();
    }

    /** One part of a nozzle, its pieces round the axis each on half a petal of its band, so the petals line up. */
    private static void part(VertexConsumer consumer, PoseStack.Pose pose, Part part, int light) {
        for (int piece = 0; piece < SEGMENTS; piece++) {
            double angle = piece * 2 * Math.PI / SEGMENTS;
            double angleTo = (piece + 1) * 2 * Math.PI / SEGMENTS;
            float u = piece % 2 / 2F;
            quad(consumer, pose, part, angle, angleTo, u, u + 0.5F, light);
        }
    }

    /** One flat piece of a part, wound to be seen from the side it faces, its normal square to it. */
    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, Part part, double angle, double angleTo, float u, float uTo, int light) {
        float v = part.band() / BANDS;
        float vTo = (part.band() + 1) / BANDS;
        Vector3f from = point(angle, part.out(), part.astern());
        Vector3f across = point(angleTo, part.out(), part.astern());
        Vector3f along = point(angle, part.outTo(), part.asternTo());
        Vector3f far = point(angleTo, part.outTo(), part.asternTo());
        Vector3f normal = normal(from, across, far, along);
        if (normal.dot(facing(part.facing(), (angle + angleTo) / 2)) >= 0) {
            vertex(consumer, pose, from, u, v, normal, light);
            vertex(consumer, pose, across, uTo, v, normal, light);
            vertex(consumer, pose, far, uTo, vTo, normal, light);
            vertex(consumer, pose, along, u, vTo, normal, light);
        } else {
            normal.negate();
            vertex(consumer, pose, along, u, vTo, normal, light);
            vertex(consumer, pose, far, uTo, vTo, normal, light);
            vertex(consumer, pose, across, uTo, v, normal, light);
            vertex(consumer, pose, from, u, v, normal, light);
        }
    }

    /** A point of a nozzle at an angle round its axis, so far from it and so far astern of the thruster's face. */
    private static Vector3f point(double angle, float out, float astern) {
        return new Vector3f((float) Math.cos(angle) * out, (float) Math.sin(angle) * out, astern);
    }

    /**
     * The way a quad given corner by corner faces, by its two diagonals, which a corner on the axis (the middle of the
     * throat, the tip of the cone) leaves well defined where its sides would not be.
     */
    private static Vector3f normal(Vector3f a, Vector3f b, Vector3f c, Vector3f d) {
        return new Vector3f(c).sub(a).cross(new Vector3f(d).sub(b)).normalize();
    }

    /** The way a part should face at an angle round its axis. */
    private static Vector3f facing(Facing facing, double angle) {
        return switch (facing) {
            case OUT -> point(angle, 1, 0);
            case IN -> point(angle, -1, 0);
            case ASTERN -> new Vector3f(0, 0, 1);
        };
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, Vector3f point, float u, float v, Vector3f normal, int light) {
        consumer.addVertex(pose, point.x, point.y, point.z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
            .setNormal(pose, normal.x, normal.y, normal.z);
    }
}
