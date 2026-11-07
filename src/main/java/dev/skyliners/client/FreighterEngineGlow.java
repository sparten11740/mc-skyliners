package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skyliners.Skyliners;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.airframe.FreighterLayout;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Craft;
import dev.skyliners.autopilot.FreighterFlight;
import dev.skyliners.block.AutopilotBlock;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;

/**
 * The freighter's engines glowing at her stern while she flies, as this client sees it: over each of her mains'
 * nozzles a white-hot core in a wide blue halo, added onto whatever is behind it, soft while her lift thrusters hold
 * her up and brighter and wider the harder her mains push her ahead, shimmering as they burn, and fading out once
 * she rests. How hard her engines fire is what her autopilot already shows every client for her roar (see
 * {@link FreighterFlight#liftOf}), so it costs the server and the network nothing more; the glow eases toward it by
 * this client's own clock (see {@link DrawingClock}). A freighter is passed over unless her stern is within
 * {@link #SEEN} of the camera and in its view. None with the server config's {@code freighter.engineGlow} off.
 */
public final class FreighterEngineGlow {
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/misc/exhaust_glow.png");
    /** How far from the camera her glow is drawn, in blocks: as far as she is seen against the sky. */
    private static final double SEEN = 320;
    /** How far round her stern her glow reaches at most, in blocks, for leaving it out unseen. */
    private static final double REACH = 16;
    /**
     * How bright her glow is while only her lift holds her up, as a share of full, and her mains' share of full at
     * which it is at full.
     */
    private static final double IDLE = 0.45;
    private static final double MAINS_FULL = 0.5;
    /** About how long her glow takes to come to how hard her engines fire, in ticks. */
    private static final double EASE_TICKS = 12;
    /** The longest a frame is counted as, in ticks, so her glow does not leap after the game stalls. */
    private static final double LONGEST_STEP = 5;
    /** How big a nozzle's white core and its blue halo are drawn at idle and at full, in blocks across. */
    private static final float CORE_IDLE = 1.8F;
    private static final float CORE_FULL = 3.4F;
    private static final float HALO_IDLE = 4.5F;
    private static final float HALO_FULL = 10F;
    /** How near the camera a flare is ever brought, in blocks (see {@link #toward}). */
    private static final double NEAREST = 1;
    /**
     * How far her glow shimmers either way, as a share of it, how fast, in radians a tick, and how far out of step each
     * nozzle shimmers from the one beside it, in radians.
     */
    private static final float SHIMMER = 0.08F;
    private static final double SHIMMER_SPEED = 1.3;
    private static final double SHIMMER_APART = 2.1;
    /** How faint her fading glow may grow before it is out, as a share of full. */
    private static final double OUT = 0.02;
    /** The core's and the halo's colours, red, green and blue, as shares of full. */
    private static final float[] CORE = {0.85F, 0.95F, 1.0F};
    private static final float[] HALO = {0.3F, 0.6F, 1.0F};
    /** Where her mains' nozzles are from her disc's middle (see {@link FreighterLayout#mainNozzles}). */
    private static final List<Vec3> NOZZLES = FreighterLayout.mainNozzles();
    /** How bright each freighter's glow is shown, and when it was last drawn, by her autopilot. */
    private static final Map<AutopilotBlockEntity, Shown> SHOWN = new WeakHashMap<>();

    private FreighterEngineGlow() {
    }

    /** How bright a freighter's glow is as shown, a share of full, and the client tick it was last brought on to. */
    private static final class Shown {
        double brightness;
        double drawnAt = Double.NaN;
    }

    /** After the particles are drawn: every freighter's engines glowing near the camera, as her engines fire. */
    public static void stage(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || level == null || !SkylinersConfig.FREIGHTER_ENGINE_GLOW.get()) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = DrawingClock.ticks(partialTick);
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.eyes(GLOW));
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        for (AutopilotBlockEntity autopilot : AutopilotBlockEntity.loaded(level)) {
            if (autopilot.craft() == Craft.FREIGHTER && autopilot.built()) {
                glow(consumer, pose, event.getFrustum(), autopilot, camera, now, partialTick);
            }
        }
        pose.popPose();
        buffers.endBatch(RenderType.eyes(GLOW));
    }

    /** One freighter's engines glowing, where they are in the world this frame, if she is near and in view. */
    private static void glow(VertexConsumer consumer, PoseStack pose, Frustum frustum, AutopilotBlockEntity autopilot, Vec3 camera, double now,
                             float partialTick) {
        float brightness = (float) brightness(SHOWN.computeIfAbsent(autopilot, ignored -> new Shown()), autopilot, now);
        Vec3 middle = FreighterFlight.middleOf(autopilot);
        if (brightness <= 0 || middle == null) {
            return;
        }
        Direction nose = autopilot.getBlockState().getValue(AutopilotBlock.FACING);
        SableClient.Placing placing = SableClient.placing(middle, partialTick);
        Vec3 stern = LaserBolts.inWorld(placing, aboard(middle, nose, NOZZLES.get(NOZZLES.size() / 2)));
        if (stern.distanceTo(camera) > SEEN || frustum != null && !frustum.isVisible(new AABB(stern, stern).inflate(REACH))) {
            return;
        }
        Quaternionf facingCamera = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
        for (int i = 0; i < NOZZLES.size(); i++) {
            float shimmer = 1 + SHIMMER * (float) Math.sin((now * SHIMMER_SPEED + i * SHIMMER_APART) % (2 * Math.PI));
            float shown = brightness * shimmer;
            float throttle = (float) Mth.clamp((brightness - IDLE) / (1 - IDLE), 0, 1);
            float halo = Mth.lerp(throttle, HALO_IDLE, HALO_FULL);
            Vec3 at = toward(LaserBolts.inWorld(placing, aboard(middle, nose, NOZZLES.get(i))), camera, halo / 2);
            pose.pushPose();
            pose.translate(at.x, at.y, at.z);
            pose.mulPose(facingCamera);
            flare(consumer, pose.last(), halo, HALO, shown);
            flare(consumer, pose.last(), Mth.lerp(throttle, CORE_IDLE, CORE_FULL), CORE, shown);
            pose.popPose();
        }
    }

    /**
     * How bright a freighter's glow is shown now, a share of full: brought on toward how hard her engines fire, by the
     * ticks since it was last drawn.
     */
    private static double brightness(Shown shown, AutopilotBlockEntity autopilot, double now) {
        float lift = FreighterFlight.liftOf(autopilot);
        float mains = FreighterFlight.mainsOf(autopilot);
        double wanted = lift <= 0 && mains <= 0 ? 0 : IDLE + (1 - IDLE) * Mth.clamp(mains / MAINS_FULL, 0, 1);
        double step = Double.isNaN(shown.drawnAt) ? LONGEST_STEP : Mth.clamp(now - shown.drawnAt, 0, LONGEST_STEP);
        shown.drawnAt = now;
        shown.brightness += (wanted - shown.brightness) * Math.min(1, step / EASE_TICKS);
        if (wanted == 0 && shown.brightness < OUT) {
            shown.brightness = 0;
        }
        return shown.brightness;
    }

    /**
     * A point brought so far toward the camera, but never nearer it than {@link #NEAREST}: a flare drawn there, facing
     * the camera, lies wholly in front of the hull behind its nozzle seen from any side, never half buried in it, while
     * the hull still hides it from the far side of her.
     */
    private static Vec3 toward(Vec3 point, Vec3 camera, double by) {
        Vec3 way = camera.subtract(point);
        double distance = way.length();
        return distance <= NEAREST ? point : point.add(way.scale(Math.min(by, distance - NEAREST) / distance));
    }

    /** A point given from her disc's middle, so far to her right, up and astern, among her blocks. */
    private static Vec3 aboard(Vec3 middle, Direction nose, Vec3 offset) {
        Vec3 right = Vec3.atLowerCornerOf(nose.getClockWise().getNormal()).scale(offset.x);
        Vec3 astern = Vec3.atLowerCornerOf(nose.getOpposite().getNormal()).scale(offset.z);
        return middle.add(right).add(astern).add(0, offset.y, 0);
    }

    /** A square flare facing the camera, so big across, in a colour at a brightness, added over what is behind it. */
    private static void flare(VertexConsumer consumer, PoseStack.Pose pose, float size, float[] colour, float brightness) {
        float half = size / 2;
        float red = colour[0] * brightness;
        float green = colour[1] * brightness;
        float blue = colour[2] * brightness;
        vertex(consumer, pose, -half, -half, 0, 1, red, green, blue);
        vertex(consumer, pose, half, -half, 1, 1, red, green, blue);
        vertex(consumer, pose, half, half, 1, 0, red, green, blue);
        vertex(consumer, pose, -half, half, 0, 0, red, green, blue);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v, float red, float green, float blue) {
        consumer.addVertex(pose, x, y, 0).setColor(red, green, blue, 1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 0, 1);
    }
}
