package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skyliners.block.LaserBolt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * The laser bolts, as the server sends them: the path clearer's (see
 * {@link dev.skyliners.block.LaserTurretBlockEntity}), the freighter's laser cannon's and the laser crossbow's (see
 * {@link dev.skyliners.item.LaserCrossbowItem}), each glowing in a colour of its own, red or amber, round a white-hot
 * core. Drawn in the world after the particles, glowing like lightning.
 *
 * <p>A mounted gun's bolt is a short-lived beam from its muzzle to where it strikes, fading out over a few ticks. The
 * server says only where it leaves from and where it strikes, so the turret that fired it finds it by where it leaves
 * from (see {@link #from}), turns toward it (see {@link LaserTurretRenderer}), and has it drawn from the tip of its
 * barrel as it is turned and as its airliner is drawn. A bolt no turret has found, as from one too far off to be drawn,
 * starts where the server fired it from.
 *
 * <p>A bolt from a weapon in hand is drawn leaving that weapon's muzzle as this client draws it in its holder's hands
 * (see {@link LaserCrossbowHands}), in first person or third, aboard a ship standing or flying, rather than where the
 * server had it, a tick or more ago and, aboard a ship in flight, blocks off. It flies out as a bright streak with a
 * flash at the muzzle, leaving a trail that fades once it has struck. Over a bright sky an amber glow added to the sky
 * would be lost, so a halo of it is laid over the sky first. Far off it is never drawn thinner than a pixel or two, and
 * near the eye never a blot.
 *
 * <p>Where a bolt strikes a ship, it is drawn landing among her blocks as she is drawn.
 */
public final class LaserBolts {
    /** How long a mounted gun's bolt shows, in ticks. */
    private static final int LIFE_TICKS = 4;
    /** A mounted gun's beam's core and glow, half widths in blocks, and its core's colour (alpha first, fading). */
    private static final float CORE = 0.05F;
    private static final float GLOW = 0.16F;
    private static final int CORE_COLOUR = 0xFFFFE8E8;
    /** How fast a bolt from a weapon in hand flies out, in blocks a tick, and how long its streak is, in blocks. */
    private static final double FLIGHT_SPEED = 8;
    private static final double STREAK = 3;
    /** How long its trail lingers once it has struck, fading, in ticks. */
    private static final double TRAIL_TICKS = 8;
    /**
     * How long the four-pointed flash at its muzzle shows, in ticks, how far its points reach and how wide they are,
     * in blocks, and how big its bright heart is, half across, in blocks.
     */
    private static final double FLASH_TICKS = 2;
    private static final float FLASH_REACH = 0.14F;
    private static final float FLASH_WIDTH = 0.025F;
    private static final float FLASH_HEART = 0.04F;
    /** Its trail's halo, glow and core and its streak's glow and core, half widths in blocks. */
    private static final double HALO = 0.06;
    private static final double TRAIL_GLOW = 0.035;
    private static final double TRAIL_CORE = 0.014;
    private static final double STREAK_GLOW = 0.07;
    private static final double STREAK_CORE = 0.025;
    /**
     * How narrow and how wide a bolt from a weapon in hand is ever drawn, as seen, in blocks across per block away:
     * about two pixels far off, and near the eye no wider than its muzzle.
     */
    private static final double FAINTEST = 0.002;
    private static final double BOLDEST = 0.02;
    /** Its white-hot core's colour, and how strong its halo is over the sky (alpha first). */
    private static final int HOT_CORE = 0xFFFFF4D8;
    private static final int HALO_ALPHA = 0x60;
    /**
     * The most a client draws an airliner behind where the server has it, in ticks: Sable shows it a tick and a half
     * back by default, and up to three as the server's ticks come unevenly, and the drawing between ticks lags up to
     * one more. The server fires from where it has the airliner, so a bolt leaves from up to this far ahead of the
     * muzzle as drawn, along the airliner's way.
     */
    private static final double MOST_LAG_TICKS = 6;
    /** How far a bolt may leave from off that stretch ahead of a turret's muzzle and still be its own, in blocks. */
    private static final double AT_MUZZLE = 2;

    private static final List<Bolt> BOLTS = new ArrayList<>();
    private static int now;
    /** How many bolts this client has been sent, and what it last made of the newest, for a play-test to read. */
    private static int received;
    @Nullable
    private static LaserBolt.Seen seen;

    /**
     * A bolt, from the muzzle to where it struck, in the world or among a ship's blocks, the colour of its glow, the
     * entity id of whoever holds the weapon it left (or {@link LaserBolt#NO_HOLDER}), the tick it was fired and how
     * long it shows; and, once the turret that fired it has found it, the tip of that turret's barrel among its
     * airliner's blocks, where it is drawn from.
     */
    static final class Bolt {
        private final Vec3 from;
        private final Vec3 to;
        private final int glow;
        private final int holder;
        private final int fired;
        private final double life;
        @Nullable
        private Vec3 tip;
        private int frames;

        private Bolt(Vec3 from, Vec3 to, int glow, int holder, int fired, double life) {
            this.from = from;
            this.to = to;
            this.glow = glow;
            this.holder = holder;
            this.fired = fired;
            this.life = life;
        }

        /** Where the bolt strikes, in the world, though it struck among a ship's blocks. */
        Vec3 to() {
            ClientLevel level = Minecraft.getInstance().level;
            return level == null ? to : SableClient.toWorld(level, to);
        }

        private boolean held() {
            return holder != LaserBolt.NO_HOLDER;
        }
    }

    private LaserBolts() {
    }

    /** A bolt fired. */
    public static void add(LaserBolt bolt) {
        received++;
        double life = LIFE_TICKS;
        ClientLevel level = Minecraft.getInstance().level;
        if (bolt.holder() != LaserBolt.NO_HOLDER && level != null) {
            life = bolt.from().distanceTo(SableClient.toWorld(level, bolt.to())) / FLIGHT_SPEED + TRAIL_TICKS;
        }
        BOLTS.add(new Bolt(bolt.from(), bolt.to(), bolt.glow(), bolt.holder(), now, life));
        seen = new LaserBolt.Seen(received, bolt.holder(), 0, 0, false, null);
    }

    public static void tick(ClientTickEvent.Post event) {
        now++;
        BOLTS.removeIf(bolt -> now - bolt.fired > bolt.life);
    }

    /** Whether no bolt is shown at all. */
    static boolean none() {
        return BOLTS.isEmpty();
    }

    /** What this client last made of the newest bolt it was sent; null before it was sent one. */
    @Nullable
    public static LaserBolt.Seen seen() {
        return seen;
    }

    /**
     * The bolts shown that left a turret's muzzle, oldest first, given where the muzzle is in the world as drawn and
     * how fast its airliner moves, in blocks a second: those that left from near the stretch the muzzle covers in
     * {@link #MOST_LAG_TICKS} from there, as the airliner is drawn behind where the server fired from.
     */
    static List<Bolt> from(Vec3 muzzle, Vec3 velocity) {
        Vec3 lag = velocity.scale(MOST_LAG_TICKS / SharedConstants.TICKS_PER_SECOND);
        List<Bolt> found = new ArrayList<>();
        for (Bolt bolt : BOLTS) {
            if (!bolt.held() && offStretch(bolt.from, muzzle, lag) <= AT_MUZZLE) {
                found.add(bolt);
            }
        }
        return found;
    }

    /** How far a point is from the stretch from a start along a way. */
    private static double offStretch(Vec3 point, Vec3 start, Vec3 along) {
        double length = along.lengthSqr();
        double share = length == 0 ? 0 : Mth.clamp(point.subtract(start).dot(along) / length, 0, 1);
        return point.distanceTo(start.add(along.scale(share)));
    }

    /** Has a turret's bolts drawn from the tip of its barrel, a point among its airliner's blocks. */
    static void drawFrom(List<Bolt> bolts, Vec3 tip) {
        for (Bolt bolt : bolts) {
            bolt.tip = tip;
        }
    }

    /**
     * Where a point among an assembled airliner's blocks is in the world, the airliner placed as given; the point
     * itself when it is not among an airliner's blocks.
     */
    static Vec3 inWorld(@Nullable SableClient.Placing placing, Vec3 point) {
        if (placing == null) {
            return point;
        }
        Vector3d placed = placing.transform().transformPosition(new Vector3d(point.x, point.y, point.z));
        return new Vec3(placed.x, placed.y, placed.z);
    }

    public static void stage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BOLTS.isEmpty()) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        List<Flight> flights = new ArrayList<>();
        Map<Integer, Vec3> muzzles = new HashMap<>();
        for (Bolt bolt : BOLTS) {
            if (bolt.held()) {
                Vec3 muzzle = muzzles.computeIfAbsent(bolt.holder, holder -> muzzle(holder, camera, event.getProjectionMatrix(), partial));
                flights.add(flightOf(bolt, muzzle, camera, partial));
            }
        }
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-eye.x, -eye.y, -eye.z);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        Matrix4f matrix = pose.last().pose();
        VertexConsumer halos = buffers.getBuffer(RenderType.debugQuads());
        for (Flight flight : flights) {
            ribbon(halos, matrix, eye, flight.from(), flight.head(), HALO, fade(HALO_ALPHA << 24 | flight.glow() & 0xFFFFFF, flight.trail()));
        }
        buffers.endBatch(RenderType.debugQuads());
        VertexConsumer beams = buffers.getBuffer(RenderType.lightning());
        for (Bolt bolt : BOLTS) {
            if (!bolt.held()) {
                mounted(beams, matrix, bolt, camera, partial);
            }
        }
        for (Flight flight : flights) {
            flight(beams, matrix, camera, flight);
        }
        buffers.endBatch(RenderType.lightning());
        pose.popPose();
    }

    /** A mounted gun's bolt: a beam from its muzzle or its turret's barrel tip, fading out. */
    private static void mounted(VertexConsumer beams, Matrix4f matrix, Bolt bolt, Camera camera, float partial) {
        float fade = 1 - Math.min(1, (now - bolt.fired + partial) / LIFE_TICKS);
        Vec3 from = bolt.tip == null ? bolt.from : inWorld(SableClient.placing(bolt.tip, partial), bolt.tip);
        Vec3 to = inWorld(SableClient.placing(bolt.to, partial), bolt.to);
        drawn(bolt, camera, from, false);
        beam(beams, matrix, from, to, GLOW, fade(bolt.glow, fade));
        beam(beams, matrix, from, to, CORE, fade(CORE_COLOUR, fade));
    }

    /**
     * Where a bolt from a weapon in hand is drawn this frame, and how: from the weapon's muzzle as drawn, or from where
     * the server fired it while its holder is not to be seen, to where it struck; how far out its streak has flown, out
     * of how far it flies, and how long since it left, in ticks; how strong its trail still is; and its glow's colour.
     */
    private record Flight(Vec3 from, Vec3 to, double flown, double length, double age, float trail, int glow) {
        /** A point so far along it from the muzzle, in blocks. */
        Vec3 at(double along) {
            return length == 0 ? from : from.add(to.subtract(from).scale(Mth.clamp(along / length, 0, 1)));
        }

        /** The head of its streak. */
        Vec3 head() {
            return at(flown);
        }
    }

    /**
     * Where the muzzle of the weapon someone holds is drawn this frame, by their entity id; none while they are not to
     * be seen or hold none.
     */
    @Nullable
    private static Vec3 muzzle(int holder, Camera camera, Matrix4f projection, float partial) {
        ClientLevel level = Minecraft.getInstance().level;
        Entity entity = level == null ? null : level.getEntity(holder);
        return entity == null ? null : LaserCrossbowHands.muzzle(entity, camera, projection, partial);
    }

    /** How a bolt from a weapon in hand is drawn this frame, from its weapon's muzzle as drawn, if it is to be seen. */
    private static Flight flightOf(Bolt bolt, @Nullable Vec3 muzzle, Camera camera, float partial) {
        Vec3 from = muzzle == null ? bolt.from : muzzle;
        Vec3 to = inWorld(SableClient.placing(bolt.to, partial), bolt.to);
        double age = now - bolt.fired + partial;
        double length = from.distanceTo(to);
        float trail = (float) (1 - Mth.clamp((age - length / FLIGHT_SPEED) / TRAIL_TICKS, 0, 1));
        drawn(bolt, camera, from, muzzle != null);
        return new Flight(from, to, Math.min(length, age * FLIGHT_SPEED), length, age, trail, bolt.glow);
    }

    /**
     * Notes a bolt drawn this frame from a point, from its weapon's muzzle as drawn or not; the newest, for a play-test
     * to read.
     */
    private static void drawn(Bolt bolt, Camera camera, Vec3 from, boolean fromMuzzle) {
        bolt.frames++;
        if (bolt == BOLTS.get(BOLTS.size() - 1)) {
            seen = new LaserBolt.Seen(received, bolt.holder, bolt.frames, now - bolt.fired, fromMuzzle, fromCamera(camera, from));
        }
    }

    /** Where a point is from the camera, in blocks to the right, up and ahead as it looks. */
    private static Vec3 fromCamera(Camera camera, Vec3 point) {
        Vec3 offset = point.subtract(camera.getPosition());
        Vector3f view = new Quaternionf(camera.rotation()).conjugate().transform(new Vector3f((float) offset.x, (float) offset.y, (float) offset.z));
        return new Vec3(view.x, view.y, -view.z);
    }

    /**
     * A bolt from a weapon in hand: its trail from the muzzle to its streak, glowing round a white-hot core and fading
     * once it has struck, its streak flying out ahead of it until it strikes, and a flash at the muzzle as it leaves.
     */
    private static void flight(VertexConsumer beams, Matrix4f matrix, Camera camera, Flight flight) {
        Vec3 eye = camera.getPosition();
        Vec3 head = flight.head();
        ribbon(beams, matrix, eye, flight.from(), head, TRAIL_GLOW, fade(flight.glow(), flight.trail()));
        ribbon(beams, matrix, eye, flight.from(), head, TRAIL_CORE, fade(HOT_CORE, flight.trail()));
        if (flight.age() * FLIGHT_SPEED < flight.length() + STREAK) {
            Vec3 tail = flight.at(flight.age() * FLIGHT_SPEED - STREAK);
            ribbon(beams, matrix, eye, tail, head, STREAK_GLOW, flight.glow());
            ribbon(beams, matrix, eye, tail, head, STREAK_CORE, HOT_CORE);
        }
        if (flight.age() < FLASH_TICKS) {
            flash(beams, matrix, camera, flight.from(), flight.glow(), (float) (1 - flight.age() / FLASH_TICKS));
        }
    }

    /**
     * A flat strip this wide from one point to another, turned to face the eye all along it, never thinner than
     * {@link #FAINTEST} nor wider than {@link #BOLDEST} as seen from there.
     */
    private static void ribbon(VertexConsumer beams, Matrix4f matrix, Vec3 eye, Vec3 from, Vec3 to, double half, int colour) {
        Vec3 along = to.subtract(from);
        if (along.lengthSqr() < 1.0E-6) {
            return;
        }
        Vec3 nearSide = across(along, eye, from, half);
        Vec3 farSide = across(along, eye, to, half);
        vertex(beams, matrix, from.subtract(nearSide), colour);
        vertex(beams, matrix, from.add(nearSide), colour);
        vertex(beams, matrix, to.add(farSide), colour);
        vertex(beams, matrix, to.subtract(farSide), colour);
    }

    /** Half the width of a strip at a point along it, crosswise to it and to the eye's view of it. */
    private static Vec3 across(Vec3 along, Vec3 eye, Vec3 point, double half) {
        Vec3 view = point.subtract(eye);
        double away = view.length();
        Vec3 side = along.cross(view);
        if (side.lengthSqr() < 1.0E-9) {
            side = along.cross(Math.abs(along.normalize().y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0));
        }
        return side.normalize().scale(Mth.clamp(half, away * FAINTEST, away * BOLDEST));
    }

    /**
     * A four-pointed flash facing the camera at a point, its points in its glow's colour round a white-hot heart, this
     * strong.
     */
    private static void flash(VertexConsumer beams, Matrix4f matrix, Camera camera, Vec3 at, int glow, float strength) {
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        diamond(beams, matrix, at, left, up, FLASH_REACH, FLASH_WIDTH, fade(glow, strength));
        diamond(beams, matrix, at, up, left, FLASH_REACH, FLASH_WIDTH, fade(glow, strength));
        diamond(beams, matrix, at, left, up, FLASH_HEART, FLASH_HEART, fade(HOT_CORE, strength));
    }

    /** A diamond at a point, reaching so far each way along one direction and so far along another. */
    private static void diamond(VertexConsumer beams, Matrix4f matrix, Vec3 at, Vector3f along, Vector3f across, float reach, float width,
        int colour) {
        vertex(beams, matrix, at.add(along.x * reach, along.y * reach, along.z * reach), colour);
        vertex(beams, matrix, at.add(across.x * width, across.y * width, across.z * width), colour);
        vertex(beams, matrix, at.add(-along.x * reach, -along.y * reach, -along.z * reach), colour);
        vertex(beams, matrix, at.add(-across.x * width, -across.y * width, -across.z * width), colour);
    }

    /** A beam this wide from one end of a bolt to the other: two strips crossed along it, so it shows from any side. */
    private static void beam(VertexConsumer beams, Matrix4f matrix, Vec3 from, Vec3 to, float half, int colour) {
        Vec3 along = to.subtract(from).normalize();
        Vec3 side = along.cross(Math.abs(along.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize().scale(half);
        Vec3 other = along.cross(side).normalize().scale(half);
        strip(beams, matrix, from, to, side, colour);
        strip(beams, matrix, from, to, other, colour);
    }

    private static void strip(VertexConsumer beams, Matrix4f matrix, Vec3 from, Vec3 to, Vec3 half, int colour) {
        vertex(beams, matrix, from.subtract(half), colour);
        vertex(beams, matrix, from.add(half), colour);
        vertex(beams, matrix, to.add(half), colour);
        vertex(beams, matrix, to.subtract(half), colour);
    }

    private static void vertex(VertexConsumer beams, Matrix4f matrix, Vec3 at, int colour) {
        beams.addVertex(matrix, (float) at.x, (float) at.y, (float) at.z).setColor(colour);
    }

    /** A colour with its alpha scaled down this share of the way to nothing. */
    private static int fade(int colour, float share) {
        int alpha = Math.round((colour >>> 24) * share);
        return alpha << 24 | colour & 0xFFFFFF;
    }
}
