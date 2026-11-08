package dev.skyliners.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.beam.TractorBeamBlockEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The tractor beams lit on this client (see {@link TractorBeamBlockEntity}), seen and heard. Each is drawn as a column
 * of green light from its lens, a little wider at its far end, glowing over whatever is behind it however dark the
 * night, with bright rings of light rising up it the way it carries things, faster than they could walk, and where it
 * meets the ground a pool of green light; seen from inside it too, so whoever rides it sees the light round them. Each
 * hums while it is lit, its hum played at its block so that aboard a ship in flight Sable carries it along, and ended
 * the moment it goes dark or is gone from this client.
 */
public final class TractorBeams {
    /** How near a lit beam is drawn, in blocks. */
    private static final double SEEN = 192;
    /**
     * How wide the beam is at its lens and at its far end, as a radius in blocks: as wide as it catches things there.
     */
    private static final float AT_LENS = 0.45F;
    private static final float AT_END = (float) TractorBeamBlockEntity.RADIUS;
    /** How many sides it is drawn with. */
    private static final int SIDES = 20;
    /** How many rings rise up it at once, how tall each is, in blocks, and how fast they rise, in blocks a tick. */
    private static final int RINGS = 7;
    private static final double RING_TALL = 0.18;
    private static final double RING_SPEED = 0.12;
    /** How much wider than the beam a ring is drawn, in blocks, so it shows over it. */
    private static final float RING_PROUD = 0.03F;
    /**
     * How wide the pool on the ground is for the beam's width there, and how far over the ground it lies, in blocks.
     */
    private static final float POOL_WIDER = 1.5F;
    private static final double POOL_LIFT = 0.05;
    /** The beam's green: faint along it, brighter in its rings and its pool, as alpha and red, green and blue. */
    private static final int BEAM = 0x3058FF8C;
    private static final int RING = 0x8098FFB8;
    private static final int POOL = 0x6058FF8C;
    /** How loud its hum is. */
    private static final float HUM_VOLUME = 0.7F;
    /** The hum each lit beam plays on this client, by beam, forgotten once it stops. */
    private static final Map<TractorBeamBlockEntity, Hum> HUMS = new HashMap<>();

    private TractorBeams() {
    }

    /**
     * Each tick of a lit beam on this client: its hum started, if it is not already playing; every hum no longer
     * playing, stopped or cut off as the world was left, forgotten.
     */
    public static void tick(TractorBeamBlockEntity beam) {
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        HUMS.values().removeIf(hum -> hum.isStopped() || !sounds.isActive(hum));
        if (!HUMS.containsKey(beam)) {
            Hum hum = new Hum(beam);
            HUMS.put(beam, hum);
            sounds.play(hum);
        }
    }

    /** After the particles are drawn: every lit beam near enough to see, its column, its rings and its pool. */
    public static void stage(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || level == null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        Matrix4f pose = event.getPoseStack().last().pose();
        double ticks = DrawingClock.ticks(partialTick);
        for (TractorBeamBlockEntity beam : TractorBeamBlockEntity.loaded(level)) {
            if (beam.lit()) {
                draw(level, consumer, pose, camera, beam, partialTick, ticks);
            }
        }
        buffers.endBatch(RenderType.lightning());
    }

    /** One lit beam, from its lens, where its ship holds it as she is drawn, to the ground or as far as it reaches. */
    private static void draw(ClientLevel level, VertexConsumer consumer, Matrix4f pose, Vec3 camera, TractorBeamBlockEntity beam,
        float partialTick, double ticks) {
        SableClient.View seen = SableClient.view(beam.lens(), beam.way(), new Vec3(0, 0, 1), partialTick);
        Vec3 from = seen != null ? seen.eye() : beam.lens();
        Vec3 way = seen != null ? seen.forward() : beam.way();
        if (from.distanceTo(camera) > SEEN) {
            return;
        }
        Vec3 ground = beam.shinesDown() ? SearchlightBeams.ground(level, from, way, TractorBeamBlockEntity.DOWN_REACH) : null;
        double length = beam.shinesDown() ? (ground != null ? ground.distanceTo(from) : TractorBeamBlockEntity.DOWN_REACH)
            : TractorBeamBlockEntity.UP_REACH;
        Vector3f side = new Vector3f((float) way.z, 0, (float) -way.x);
        if (side.lengthSquared() < 1.0E-4F) {
            side.set(1, 0, 0);
        }
        side.normalize();
        Vector3f across = new Vector3f((float) way.x, (float) way.y, (float) way.z).cross(side).normalize();
        Frame frame = new Frame(from, way, side, across, length);
        band(consumer, pose, camera, frame, 0, length, BEAM, BEAM & 0x00FFFFFF, 0);
        rings(consumer, pose, camera, frame, beam.shinesDown(), ticks);
        if (ground != null) {
            pool(consumer, pose, camera, ground.add(0, POOL_LIFT, 0), AT_END * POOL_WIDER);
        }
    }

    /** A beam laid out to draw: its lens, the way it shines, two ways square across it, and how long it is. */
    private record Frame(Vec3 from, Vec3 way, Vector3f side, Vector3f across, double length) {
        /** How wide it is so far along it, in blocks. */
        float width(double along) {
            return AT_LENS + (AT_END - AT_LENS) * (float) Math.min(1, along / Math.max(1, length));
        }
    }

    /**
     * The rings rising up the beam, the way it carries things: toward the lens of a beam shining down, away from it up
     * a beam shining up; each fading in at the beam's foot and out at its top.
     */
    private static void rings(VertexConsumer consumer, Matrix4f pose, Vec3 camera, Frame frame, boolean shinesDown, double ticks) {
        double spacing = frame.length() / RINGS;
        double risen = (ticks * RING_SPEED) % spacing;
        for (int k = 0; k < RINGS; k++) {
            double up = k * spacing + risen;
            double along = shinesDown ? frame.length() - up : up;
            float fade = (float) Math.min(1, Math.min(up, frame.length() - up) / spacing);
            int alpha = Math.round((RING >>> 24) * Math.max(0, fade));
            int colour = alpha << 24 | RING & 0x00FFFFFF;
            band(consumer, pose, camera, frame, along, along + RING_TALL, colour, colour, RING_PROUD);
        }
    }

    /**
     * A stretch of the beam's side between two distances along it, from one colour to another, drawn both ways round so
     * it is seen from inside the beam as well as from outside, so much wider than the beam as given.
     */
    private static void band(VertexConsumer consumer, Matrix4f pose, Vec3 camera, Frame frame, double near, double far, int nearColour,
        int farColour, float proud) {
        Vec3 nearMiddle = frame.from().add(frame.way().scale(near));
        Vec3 farMiddle = frame.from().add(frame.way().scale(far));
        float nearWidth = frame.width(near) + proud;
        float farWidth = frame.width(far) + proud;
        for (int k = 0; k < SIDES; k++) {
            double a = 2 * Math.PI * k / SIDES;
            double b = 2 * Math.PI * (k + 1) / SIDES;
            Vec3[] corners = {ring(nearMiddle, frame, a, nearWidth), ring(nearMiddle, frame, b, nearWidth),
                ring(farMiddle, frame, b, farWidth), ring(farMiddle, frame, a, farWidth)};
            int[] colours = {nearColour, nearColour, farColour, farColour};
            quad(consumer, pose, camera, corners, colours, false);
            quad(consumer, pose, camera, corners, colours, true);
        }
    }

    /** A disc of green light lying on the ground, brightest in its middle, seen from above and below. */
    private static void pool(VertexConsumer consumer, Matrix4f pose, Vec3 camera, Vec3 middle, float radius) {
        Frame flat = new Frame(middle, new Vec3(0, 1, 0), new Vector3f(1, 0, 0), new Vector3f(0, 0, 1), 0);
        for (int k = 0; k < SIDES; k++) {
            double a = 2 * Math.PI * k / SIDES;
            double b = 2 * Math.PI * (k + 1) / SIDES;
            Vec3[] corners = {middle, middle, ring(middle, flat, b, radius), ring(middle, flat, a, radius)};
            int[] colours = {POOL, POOL, POOL & 0x00FFFFFF, POOL & 0x00FFFFFF};
            quad(consumer, pose, camera, corners, colours, false);
            quad(consumer, pose, camera, corners, colours, true);
        }
    }

    /** A point on a ring round a middle, square across a beam, so far round it and so wide. */
    private static Vec3 ring(Vec3 middle, Frame frame, double angle, float radius) {
        double cos = Math.cos(angle) * radius;
        double sin = Math.sin(angle) * radius;
        Vector3f one = frame.side();
        Vector3f other = frame.across();
        return middle.add(one.x * cos + other.x * sin, one.y * cos + other.y * sin, one.z * cos + other.z * sin);
    }

    /** Four corners as one quad, in their order or the other way round. */
    private static void quad(VertexConsumer consumer, Matrix4f pose, Vec3 camera, Vec3[] corners, int[] colours, boolean backward) {
        for (int k = 0; k < corners.length; k++) {
            int at = backward ? corners.length - 1 - k : k;
            Vec3 corner = corners[at];
            Vec3 seen = corner.subtract(camera);
            consumer.addVertex(pose, (float) seen.x, (float) seen.y, (float) seen.z).setColor(colours[at]);
        }
    }

    /** A lit beam's hum, looping at its block until it goes dark or is gone. */
    private static final class Hum extends AbstractTickableSoundInstance {
        private final TractorBeamBlockEntity beam;

        Hum(TractorBeamBlockEntity beam) {
            super(SkylinersRegistry.TRACTOR_BEAM_HUM.get(), SoundSource.BLOCKS, RandomSource.create());
            this.beam = beam;
            Vec3 at = beam.lens();
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
            this.looping = true;
            this.delay = 0;
            this.volume = HUM_VOLUME;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
        }

        @Override
        public void tick() {
            if (beam.isRemoved() || !beam.lit()) {
                stop();
            }
        }
    }
}
