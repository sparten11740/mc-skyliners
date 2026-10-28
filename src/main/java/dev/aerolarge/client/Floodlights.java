package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import dev.aerolarge.AerolargeClientConfig;
import dev.aerolarge.block.FloodlightBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.slf4j.Logger;

/**
 * The light the zeppelins' lit floodlights throw on the land under them, as this client sees it (see
 * {@link FloodlightBlockEntity}): each one of Veil's area lights (see {@link VeilLights}), a lens's worth of glowing
 * face shining down and a little ahead within a wide angle, so the land under a ship and ahead of her is lit as a
 * landing light lights a runway. Veil's light fades over its reach by the cube of what is left of it, so each reaches
 * {@link #PAST} times as far as the ground ahead of it and is brightened to make up for it: the ground is as bright
 * under a ship flying high as under one coming in to land, and what stands nearer, treetops and roofs, brighter still,
 * as under a real light. The ground is looked for along each light once a tick; the frames in between reach part of
 * the way from where it was at the one to where it is at the other.
 *
 * <p>Only the {@link #MOST_LIGHTS} nearest the camera within {@link #SEEN} are lit, each with ground within
 * {@link #REACH} of it. A light among an assembled airliner's blocks is placed anew every frame, as Sable draws the
 * airliner at that partial tick, so it flies with her. Where Veil's lights cannot be drawn ({@link VeilLights#USABLE}),
 * with the {@code floodlightLight} setting off, or once drawing them has failed, a pool of light is drawn on the ground
 * under each instead, as a searchlight's is (see {@link SearchlightBeams}).
 *
 * <p>By day the lights shine into the shade of the hull, which this client draws over the land (see
 * {@link GreatShadow}): where each light's pool falls ({@link #pools}), the shadow is drawn the lighter the nearer the
 * light is to the ground.
 */
public final class Floodlights {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** How far from the camera a floodlight's light is drawn, in blocks. */
    private static final double SEEN = 384;
    /** The most floodlights whose light is drawn at once, the nearest. */
    private static final int MOST_LIGHTS = 8;
    /** Half the size of the lens a light shines from, in blocks. */
    private static final double LENS = 0.3;
    /** How far under the middle of its block a light's lens is, in blocks. */
    private static final double LENS_DROP = 0.32;
    /** How wide a light is: the angle from straight out at which it has faded away, in radians. */
    private static final float SPREAD = (float) Math.toRadians(36);
    /** How far off the ground may be, at most, along a light, for it to light it, in blocks. */
    private static final double REACH = 192;
    /** How far a light reaches past the ground, as a share of the way to it. */
    private static final double PAST = 1.5;
    /** How bright a light is on the ground straight under it, and its warm white. */
    private static final float BRIGHTNESS = 1.3F;
    private static final float RED = 1.0F;
    private static final float GREEN = 0.94F;
    private static final float BLUE = 0.8F;
    /** How much brighter a light is made, for what of it is left on the ground: the cube of the share it fades over. */
    private static final float GAIN = (float) Math.pow(PAST / (PAST - 1), 3);
    /**
     * Where no light is drawn, how wide its drawn pool on the ground is, for each block to the ground, a share; and how
     * much higher over the ground it is drawn for each block it is from the camera, in blocks, so that far off it does
     * not flicker into the ground.
     */
    private static final float POOL = 0.35F;
    private static final double POOL_LIFT = 0.003;
    /**
     * How far from the ground a light lifts her shadow wholly at the middle of its pool, in blocks; twice as far off,
     * half of it, and so on, so flying low, landing or parked the ground under her is bright, and from a cruise high
     * up her shadow is lifted half or less.
     */
    private static final double LIFTS_SHADOW = 64;
    /** The lights drawn, each by its floodlight. */
    private static final Map<FloodlightBlockEntity, Lit> LIT = new HashMap<>();
    /** Veil's buffers, kept on while the lights are near. */
    private static final VeilLights.Buffers BUFFERS = new VeilLights.Buffers("floodlights");
    /** Where a light goes in the world, a way square to it, and the way it shines: reused, as lights fly every frame. */
    private static final Vector3d LENS_AT = new Vector3d();
    private static final Vector3d ACROSS = new Vector3d();
    private static final Vector3d OUT = new Vector3d();
    /** Whether drawing the lights has failed; then none are drawn until the game is restarted. */
    private static boolean failed;

    private Floodlights() {
    }

    /**
     * A floodlight's light, as Veil's, and how far the ground was along it at the tick before and is at this one, in
     * blocks; none as {@link Double#NaN}.
     */
    private static final class Lit {
        final Object light = VeilLights.add(LENS, LENS, SPREAD);
        double before = Double.NaN;
        double now = Double.NaN;
        /** The game tick the ground was last looked for at. */
        long lookedAt = Long.MIN_VALUE;
    }

    /**
     * Where a lit floodlight's light falls on the land: the middle of its pool, how wide it is, and how much of her
     * shadow it lifts there, a share.
     */
    public record Pool(double x, double z, double radius, double lift) {
        /** How much of her shadow the light lifts at a point, a share: most at the middle, none past the edge. */
        public double liftAt(double atX, double atZ) {
            double off = Math.hypot(atX - x, atZ - z) / radius;
            return off >= 1 ? 0 : lift * (1 - off * off);
        }
    }

    /** The pools of light the lit floodlights near the camera throw on the land, as their ships lie this tick. */
    public static List<Pool> pools(ClientLevel level, Vec3 camera) {
        List<Pool> pools = new ArrayList<>();
        for (FloodlightBlockEntity light : near(level, camera)) {
            SableClient.View seen = SableClient.view(lens(light), light.aim(), new Vec3(0, 1, 0), 1);
            Vec3 from = seen != null ? seen.eye() : lens(light);
            Vec3 ground = SearchlightBeams.ground(level, from, seen != null ? seen.forward() : light.aim(), REACH);
            if (ground != null) {
                double reach = ground.distanceTo(from);
                pools.add(new Pool(ground.x, ground.z, reach * POOL, Math.min(1, LIFTS_SHADOW / reach)));
            }
        }
        return pools;
    }

    /** Before each frame: lights the nearest lit floodlights, and places their light as their ships lie. */
    public static void frame(RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !available() || !VeilLights.settled(minecraft)) {
            forget();
            return;
        }
        try {
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            List<FloodlightBlockEntity> near = near(minecraft.level, minecraft.gameRenderer.getMainCamera().getPosition());
            Iterator<Map.Entry<FloodlightBlockEntity, Lit>> lit = LIT.entrySet().iterator();
            while (lit.hasNext()) {
                Map.Entry<FloodlightBlockEntity, Lit> light = lit.next();
                if (!near.contains(light.getKey())) {
                    VeilLights.remove(light.getValue().light);
                    lit.remove();
                }
            }
            for (FloodlightBlockEntity light : near) {
                shine(minecraft.level, light, LIT.computeIfAbsent(light, key -> new Lit()), partialTick);
            }
            BUFFERS.keep(!LIT.isEmpty(), Util.getMillis());
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            LOGGER.error("The zeppelins' floodlights could not be drawn; their pools are drawn instead until the game is restarted", e);
            forget();
        }
    }

    /** After the particles are drawn, where no light is: a pool of light on the ground under each near floodlight. */
    public static void stage(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || level == null || available()) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        Matrix4f pose = event.getPoseStack().last().pose();
        for (FloodlightBlockEntity light : near(level, camera)) {
            SableClient.View seen = SableClient.view(lens(light), light.aim(), new Vec3(0, 1, 0), partialTick);
            Vec3 from = seen != null ? seen.eye() : lens(light);
            Vec3 ground = SearchlightBeams.ground(level, from, seen != null ? seen.forward() : light.aim(), REACH);
            if (ground != null) {
                Vec3 lifted = ground.add(0, ground.distanceTo(camera) * POOL_LIFT, 0);
                SearchlightBeams.pool(consumer, pose, camera, lifted, (float) ground.distanceTo(from) * POOL);
            }
        }
        buffers.endBatch(RenderType.lightning());
    }

    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        forget();
    }

    /** Whether the lights may be drawn now at all. */
    private static boolean available() {
        return VeilLights.USABLE && !failed && AerolargeClientConfig.FLOODLIGHT_LIGHT.get();
    }

    /** The lit floodlights loaded in this level within sight of the camera, nearest first. */
    private static List<FloodlightBlockEntity> near(ClientLevel level, Vec3 camera) {
        record Near(FloodlightBlockEntity light, double distance) {
        }
        return FloodlightBlockEntity.loaded(level).stream()
            .filter(light -> !light.isRemoved() && light.lit())
            .map(light -> new Near(light, SableClient.toWorld(level, lens(light)).distanceTo(camera)))
            .filter(light -> light.distance() <= SEEN)
            .sorted(Comparator.comparingDouble(Near::distance))
            .limit(MOST_LIGHTS)
            .map(Near::light)
            .toList();
    }

    /** The middle of a floodlight's lens, among its ship's blocks. */
    private static Vec3 lens(FloodlightBlockEntity light) {
        return Vec3.atCenterOf(light.getBlockPos()).add(0, -LENS_DROP, 0);
    }

    /**
     * Shines and places a floodlight's light as its ship lies at this partial tick: out of its lens down along its aim,
     * reaching past the ground as far as it was then, and dark where there is no ground within reach.
     */
    private static void shine(ClientLevel level, FloodlightBlockEntity light, Lit lit, float partialTick) {
        look(level, light, lit);
        double ground = Double.isNaN(lit.before) ? lit.now : Mth.lerp(partialTick, lit.before, lit.now);
        boolean lighting = !Double.isNaN(ground);
        VeilLights.shine(lit.light, lighting ? (float) (ground * PAST) : 1, RED, GREEN, BLUE, lighting ? BRIGHTNESS * GAIN : 0);
        Vec3 lens = lens(light);
        Vec3 aim = light.aim();
        set(LENS_AT, lens);
        set(ACROSS, new Vec3(-aim.z, 0, aim.x).normalize());
        set(OUT, aim);
        SableClient.Placing placing = SableClient.placing(lens, partialTick);
        if (placing != null) {
            placing.transform().transformPosition(LENS_AT);
            placing.turn().transform(ACROSS);
            placing.turn().transform(OUT);
        }
        VeilLights.place(lit.light, LENS_AT, ACROSS, OUT);
    }

    /** Looks once a game tick for how far the ground is along a light, keeping how far it was at the tick before. */
    private static void look(ClientLevel level, FloodlightBlockEntity light, Lit lit) {
        long tick = level.getGameTime();
        if (lit.lookedAt == tick) {
            return;
        }
        SableClient.View seen = SableClient.view(lens(light), light.aim(), new Vec3(0, 1, 0), 1);
        Vec3 from = seen != null ? seen.eye() : lens(light);
        Vec3 ground = SearchlightBeams.ground(level, from, seen != null ? seen.forward() : light.aim(), REACH);
        lit.before = lit.lookedAt == tick - 1 ? lit.now : Double.NaN;
        lit.now = ground == null ? Double.NaN : ground.distanceTo(from);
        lit.lookedAt = tick;
    }

    /** Drops every light and lets Veil's buffers go. */
    private static void forget() {
        if (LIT.isEmpty() && !BUFFERS.held()) {
            return;
        }
        try {
            LIT.values().forEach(lit -> VeilLights.remove(lit.light));
            BUFFERS.release();
        } catch (RuntimeException | LinkageError e) {
            if (!failed) {
                failed = true;
                LOGGER.error("The zeppelins' floodlights could not be let go; their pools are drawn instead until the game is restarted", e);
            }
        } finally {
            LIT.clear();
        }
    }

    private static Vector3d set(Vector3d into, Vec3 vector) {
        return into.set(vector.x, vector.y, vector.z);
    }
}
