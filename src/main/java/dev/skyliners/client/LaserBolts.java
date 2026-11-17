package dev.skyliners.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.skyliners.Skyliners;
import dev.skyliners.SkylinersRegistry;
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
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
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
 * <p>A bolt from a weapon in hand is a short glowing bolt, a blaster's, that flies from the weapon's muzzle as this
 * client draws it in its holder's hands (see {@link LaserCrossbowHands}), in first person or third, at
 * {@link LaserBolt#HELD_SPEED}, with a flash at the muzzle as it leaves. Where it left is kept from the first frame it
 * is drawn, among the blocks of the ship its holder rides when they ride one, so a bolt fired aboard a ship in flight
 * flies along with her, and one fired on the run is not dragged along by its holder. Its head burns white-hot, its tail
 * thinner and fainter. Where it strikes something it vanishes into it with a burst of sparks, a flash and a sizzle;
 * where it strikes nothing it fades out over the last blocks of its range. Over a bright sky an amber glow added to the
 * sky would be lost, so a halo of it is laid over the sky first. Far off it is never drawn thinner than a pixel or two,
 * and near the eye never a blot.
 *
 * <p>Where a bolt strikes a ship, or someone aboard her, it is drawn landing among her blocks as she is drawn.
 */
public final class LaserBolts {
    /** How long a mounted gun's bolt shows, in ticks. */
    private static final int LIFE_TICKS = 4;
    /** A mounted gun's beam's core and glow, half widths in blocks, and its core's colour (alpha first, fading). */
    private static final float CORE = 0.05F;
    private static final float GLOW = 0.16F;
    private static final int CORE_COLOUR = 0xFFFFE8E8;
    /** How long a bolt from a weapon in hand is, from its head to its tail, in blocks. */
    private static final double BOLT_LENGTH = 2;
    /** Over how many of the last blocks of its range a bolt that strikes nothing fades out. */
    private static final double FADE_BLOCKS = 8;
    /**
     * How long a bolt from a weapon in hand may show at most, in ticks, whatever happens to it: longer than any flies.
     * A bolt longer than any weapon reaches, in blocks, is one among the blocks of a ship this client does not have, and
     * is not drawn.
     */
    private static final int MOST_HELD_TICKS = 40;
    private static final double MOST_REACH = 96;
    /**
     * How long the four-pointed flash at its muzzle shows, in ticks, how far its points reach and how wide they are,
     * in blocks, and how big its bright heart is, half across, in blocks.
     */
    private static final double FLASH_TICKS = 2;
    private static final float FLASH_REACH = 0.1F;
    private static final float FLASH_WIDTH = 0.02F;
    private static final float FLASH_HEART = 0.035F;
    /** How long the flash where it strikes shows, in ticks, and how much bigger it is than the one at the muzzle. */
    private static final double BURST_TICKS = 3;
    private static final float BURST_SIZE = 3;
    /**
     * Where it strikes: how many sparks fly, and how many crackling bits fall, how fast they fly out, in blocks a tick,
     * and how much smoke curls up.
     */
    private static final int SPARKS = 10;
    private static final int CRACKLES = 6;
    private static final double SPARK_SPEED = 0.6;
    private static final double CRACKLE_SPEED = 0.25;
    private static final int SMOKE = 2;
    /**
     * Where a mounted gun's bolt strikes, as it comes: how many sparks fly, how far round the point they start, in
     * blocks, and how fast they fly, in blocks a tick.
     */
    private static final int MOUNTED_SPARKS = 12;
    private static final double MOUNTED_SPREAD = 0.2;
    private static final double MOUNTED_SPARK_SPEED = 0.15;
    /** How loud its sizzle is, as a share of full, and how high, at the lowest and how much higher it may be. */
    private static final float HIT_VOLUME = 0.7F;
    private static final float HIT_PITCH = 0.9F;
    private static final float HIT_PITCH_SPREAD = 0.25F;
    /**
     * Its halo, glow and white-hot core, half widths in blocks at its head and its tail, and how strong its tail is
     * against its head, as a share.
     */
    private static final double HALO_HEAD = 0.18;
    private static final double HALO_TAIL = 0.06;
    private static final double GLOW_HEAD = 0.11;
    private static final double GLOW_TAIL = 0.04;
    private static final double CORE_HEAD = 0.045;
    private static final double CORE_TAIL = 0.015;
    private static final float TAIL_STRENGTH = 0.35F;
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
    /**
     * A bolt from a weapon in hand is drawn in two layers, neither hiding what is drawn after it: its halo laid over
     * what is behind it, as the game lays any translucent colour, and its glow and core added to it, as lightning is.
     * Both are drawn in one plane, so neither keeps its depth, or each layer would hide the next one in places.
     */
    private static final RenderType HALO = RenderType.create(Skyliners.MOD_ID + "_laser_halo", DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS, RenderType.TRANSIENT_BUFFER_SIZE, false, true, RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setCullState(RenderStateShard.NO_CULL)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(false));
    private static final RenderType GLOWING = RenderType.create(Skyliners.MOD_ID + "_laser_glow", DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS, RenderType.TRANSIENT_BUFFER_SIZE, false, true, RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
            .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
            .setCullState(RenderStateShard.NO_CULL)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(false));

    private static final List<Bolt> BOLTS = new ArrayList<>();
    private static int now;
    /** How many bolts this client has been sent, and what it last made of the newest, for a play-test to read. */
    private static int received;
    @Nullable
    private static LaserBolt.Seen seen;

    /**
     * A bolt, from the muzzle to where it struck, in the world or among a ship's blocks, the colour of its glow, the
     * entity id of whoever holds the weapon it left (or {@link LaserBolt#NO_HOLDER}), whether it strikes anything, and
     * the tick it was fired; once the turret that fired it has found it, the tip of that turret's barrel among its
     * airliner's blocks, where it is drawn from; and, for one from a weapon in hand, where it left as first drawn, in
     * the frame it was sent leaving from, whether that was the weapon's muzzle as drawn, how far it had to fly when last
     * drawn, in blocks, and whether it has burst where it struck.
     */
    static final class Bolt {
        private final Vec3 from;
        private final Vec3 to;
        private final int glow;
        private final int holder;
        private final boolean strikes;
        private final int fired;
        @Nullable
        private Vec3 tip;
        @Nullable
        private Vec3 start;
        private boolean fromMuzzle;
        private double length = Double.NaN;
        private boolean burst;
        private int frames;

        private Bolt(LaserBolt bolt, int fired) {
            this.from = bolt.from();
            this.to = bolt.to();
            this.glow = bolt.glow();
            this.holder = bolt.holder();
            this.strikes = bolt.strikes();
            this.fired = fired;
        }

        /** Where the bolt strikes, in the world, though it struck among a ship's blocks. */
        Vec3 to() {
            ClientLevel level = Minecraft.getInstance().level;
            return level == null ? to : SableClient.toWorld(level, to);
        }

        private boolean held() {
            return holder != LaserBolt.NO_HOLDER;
        }

        /** How many ticks after it was fired it has got where it was going: never, before it was first drawn. */
        private double arrives() {
            return Double.isNaN(length) ? Double.POSITIVE_INFINITY : length / LaserBolt.HELD_SPEED;
        }

        /** Whether it is done with, as of this tick: faded, or vanished into what it struck and its burst over. */
        private boolean over() {
            int age = now - fired;
            if (!held()) {
                return age > LIFE_TICKS;
            }
            return age > MOST_HELD_TICKS || age > arrives() + Math.max(BOLT_LENGTH / LaserBolt.HELD_SPEED, BURST_TICKS);
        }
    }

    private LaserBolts() {
    }

    /**
     * A bolt fired; one from a mounted gun strikes at once, sparks flying there. The client draws those from the bolt
     * itself, so a gun firing fast, or eight turrets at once, sends no stream of particle packets besides.
     */
    public static void add(LaserBolt bolt) {
        received++;
        BOLTS.add(new Bolt(bolt, now));
        if (bolt.holder() == LaserBolt.NO_HOLDER) {
            sparks(bolt.to());
        }
        seen = new LaserBolt.Seen(received, bolt.holder(), 0, 0, false, null, 0, 0);
    }

    /** Each client tick: bolts from a weapon in hand that have got to what they strike burst there, and old ones go. */
    public static void tick(ClientTickEvent.Post event) {
        now++;
        for (Bolt bolt : BOLTS) {
            if (bolt.held() && bolt.strikes && !bolt.burst && now - bolt.fired >= bolt.arrives()) {
                bolt.burst = true;
                burst(bolt);
            }
        }
        BOLTS.removeIf(Bolt::over);
    }

    /**
     * A bolt from a weapon in hand bursts where it struck: sparks flying out and crackling bits falling, a wisp of
     * smoke, and its sizzle. They are given off where it struck as it was sent, among a ship's blocks where it struck
     * one of hers or someone aboard, so that Sable carries them along with her.
     */
    private static void burst(Bolt bolt) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        RandomSource random = level.getRandom();
        spray(level, ParticleTypes.ELECTRIC_SPARK, bolt.to, SPARKS, SPARK_SPEED, random);
        spray(level, ParticleTypes.CRIT, bolt.to, CRACKLES, CRACKLE_SPEED, random);
        spray(level, ParticleTypes.SMOKE, bolt.to, SMOKE, 0, random);
        Vec3 heard = SableClient.toWorld(level, bolt.to);
        float pitch = HIT_PITCH + random.nextFloat() * HIT_PITCH_SPREAD;
        level.playLocalSound(heard.x, heard.y, heard.z, SkylinersRegistry.LASER_CROSSBOW_HIT.get(), SoundSource.PLAYERS, HIT_VOLUME, pitch, false);
    }

    /** A mounted gun's bolt's sparks, where it strikes. */
    private static void sparks(Vec3 at) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        RandomSource random = level.getRandom();
        for (int i = 0; i < MOUNTED_SPARKS; i++) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x + random.nextGaussian() * MOUNTED_SPREAD, at.y + random.nextGaussian() * MOUNTED_SPREAD,
                at.z + random.nextGaussian() * MOUNTED_SPREAD, random.nextGaussian() * MOUNTED_SPARK_SPEED, random.nextGaussian() * MOUNTED_SPARK_SPEED,
                random.nextGaussian() * MOUNTED_SPARK_SPEED);
        }
    }

    /** So many particles given off from a point, flying out every way at up to so many blocks a tick. */
    private static void spray(ClientLevel level, ParticleOptions particle, Vec3 at, int count, double speed, RandomSource random) {
        for (int i = 0; i < count; i++) {
            level.addParticle(particle, at.x, at.y, at.z, random.triangle(0, speed), random.triangle(0, speed), random.triangle(0, speed));
        }
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
                Vec3 muzzle = bolt.start != null ? null
                    : muzzles.computeIfAbsent(bolt.holder, holder -> muzzle(holder, camera, event.getProjectionMatrix(), partial));
                flights.add(flightOf(bolt, muzzle, camera, partial));
            }
        }
        flights.removeIf(flight -> flight.length() > MOST_REACH);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-eye.x, -eye.y, -eye.z);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        Matrix4f matrix = pose.last().pose();
        VertexConsumer beams = buffers.getBuffer(RenderType.lightning());
        for (Bolt bolt : BOLTS) {
            if (!bolt.held()) {
                mounted(beams, matrix, bolt, camera, partial);
            }
        }
        buffers.endBatch(RenderType.lightning());
        if (!flights.isEmpty()) {
            VertexConsumer halos = buffers.getBuffer(HALO);
            for (Flight flight : flights) {
                streak(halos, matrix, eye, flight, HALO_HEAD, HALO_TAIL, HALO_ALPHA << 24 | flight.glow() & 0xFFFFFF);
            }
            buffers.endBatch(HALO);
            VertexConsumer glows = buffers.getBuffer(GLOWING);
            for (Flight flight : flights) {
                flight(glows, matrix, camera, flight);
            }
            buffers.endBatch(GLOWING);
        }
        pose.popPose();
    }

    /** A mounted gun's bolt: a beam from its muzzle or its turret's barrel tip, fading out. */
    private static void mounted(VertexConsumer beams, Matrix4f matrix, Bolt bolt, Camera camera, float partial) {
        float fade = 1 - Math.min(1, (now - bolt.fired + partial) / LIFE_TICKS);
        Vec3 from = bolt.tip == null ? bolt.from : inWorld(SableClient.placing(bolt.tip, partial), bolt.tip);
        Vec3 to = inWorld(SableClient.placing(bolt.to, partial), bolt.to);
        drawn(bolt, camera, from, 0, 0);
        beam(beams, matrix, from, to, GLOW, fade(bolt.glow, fade));
        beam(beams, matrix, from, to, CORE, fade(CORE_COLOUR, fade));
    }

    /**
     * Where a bolt from a weapon in hand is drawn this frame, and how: from where it left, as first drawn, to where it
     * strikes, both where they are drawn this frame; how long since it left, in ticks; its glow's colour; and whether it
     * strikes anything there.
     */
    private record Flight(Vec3 from, Vec3 to, double length, double age, int glow, boolean strikes) {
        /** A point so far along it from where it left, in blocks. */
        Vec3 at(double along) {
            return length == 0 ? from : from.add(to.subtract(from).scale(Mth.clamp(along / length, 0, 1)));
        }

        /** How far it has flown, in blocks. */
        double flown() {
            return age * LaserBolt.HELD_SPEED;
        }

        /** Its head, as far as what it strikes. */
        Vec3 head() {
            return at(flown());
        }

        /** Its tail, a bolt's length behind its head and never back past where it left. */
        Vec3 tail() {
            return at(flown() - BOLT_LENGTH);
        }

        /**
         * How strong it shows: wholly while it flies, fading over the last blocks of its range where it strikes nothing,
         * gone once it has wholly vanished into what it strikes.
         */
        float strength() {
            if (strikes) {
                return flown() < length + BOLT_LENGTH ? 1 : 0;
            }
            return (float) (1 - Mth.clamp((flown() - (length - FADE_BLOCKS)) / FADE_BLOCKS, 0, 1));
        }

        /** How long since it got to what it strikes, in ticks; less than nothing before then. */
        double sinceStruck() {
            return age - length / LaserBolt.HELD_SPEED;
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

    /**
     * How a bolt from a weapon in hand is drawn this frame. The first time, where it left is taken from its weapon's
     * muzzle as drawn, if that is to be seen, else from where the server fired it, and kept in the frame the server
     * sent it leaving from, among the blocks of the ship its holder rides when they ride one.
     */
    private static Flight flightOf(Bolt bolt, @Nullable Vec3 muzzle, Camera camera, float partial) {
        SableClient.Placing leaving = SableClient.placing(bolt.from, partial);
        if (bolt.start == null) {
            bolt.fromMuzzle = muzzle != null;
            bolt.start = muzzle == null ? bolt.from : SableClient.toDrawnPlot(bolt.from, muzzle, partial);
        }
        Vec3 from = inWorld(leaving, bolt.start);
        Vec3 to = inWorld(SableClient.placing(bolt.to, partial), bolt.to);
        bolt.length = from.distanceTo(to);
        Flight flight = new Flight(from, to, bolt.length, now - bolt.fired + partial, bolt.glow, bolt.strikes);
        double tailFlown = Mth.clamp(flight.flown() - BOLT_LENGTH, 0, bolt.length);
        drawn(bolt, camera, from, tailFlown, Math.min(flight.flown(), bolt.length) - tailFlown);
        return flight;
    }

    /**
     * Notes a bolt drawn this frame from a point, how far its tail had flown from there and how long it was drawn, in
     * blocks; the newest, for a play-test to read.
     */
    private static void drawn(Bolt bolt, Camera camera, Vec3 from, double flown, double streak) {
        bolt.frames++;
        if (bolt == BOLTS.get(BOLTS.size() - 1)) {
            seen = new LaserBolt.Seen(received, bolt.holder, bolt.frames, now - bolt.fired, bolt.fromMuzzle, fromCamera(camera, from), flown, streak);
        }
    }

    /** Where a point is from the camera, in blocks to the right, up and ahead as it looks. */
    private static Vec3 fromCamera(Camera camera, Vec3 point) {
        Vec3 offset = point.subtract(camera.getPosition());
        Vector3f view = new Quaternionf(camera.rotation()).conjugate().transform(new Vector3f((float) offset.x, (float) offset.y, (float) offset.z));
        return new Vec3(view.x, view.y, -view.z);
    }

    /**
     * A bolt from a weapon in hand: its glow round a white-hot core, its head brightest, as it flies; a flash at the
     * muzzle as it leaves; and a bigger flash where it strikes as it gets there.
     */
    private static void flight(VertexConsumer beams, Matrix4f matrix, Camera camera, Flight flight) {
        Vec3 eye = camera.getPosition();
        streak(beams, matrix, eye, flight, GLOW_HEAD, GLOW_TAIL, flight.glow());
        streak(beams, matrix, eye, flight, CORE_HEAD, CORE_TAIL, HOT_CORE);
        if (flight.age() < FLASH_TICKS) {
            flash(beams, matrix, camera, flight.from(), flight.glow(), 1, (float) (1 - flight.age() / FLASH_TICKS));
        }
        double sinceStruck = flight.sinceStruck();
        if (flight.strikes() && sinceStruck >= 0 && sinceStruck < BURST_TICKS) {
            flash(beams, matrix, camera, flight.to(), flight.glow(), BURST_SIZE, (float) (1 - sinceStruck / BURST_TICKS));
        }
    }

    /** One layer of a flying bolt, from its tail to its head, so wide at each and in this colour, fading toward its tail. */
    private static void streak(VertexConsumer beams, Matrix4f matrix, Vec3 eye, Flight flight, double head, double tail, int colour) {
        float strength = flight.strength();
        if (strength <= 0) {
            return;
        }
        int bright = fade(colour, strength);
        ribbon(beams, matrix, eye, flight.tail(), flight.head(), tail, head, fade(bright, TAIL_STRENGTH), bright);
    }

    /**
     * A flat strip from one point to another, so wide at each end and in a colour at each, turned to face the eye all
     * along it, never thinner than {@link #FAINTEST} nor wider than {@link #BOLDEST} as seen from there.
     */
    private static void ribbon(VertexConsumer beams, Matrix4f matrix, Vec3 eye, Vec3 from, Vec3 to, double halfFrom, double halfTo, int colourFrom,
        int colourTo) {
        Vec3 along = to.subtract(from);
        if (along.lengthSqr() < 1.0E-6) {
            return;
        }
        Vec3 nearSide = across(along, eye, from, halfFrom);
        Vec3 farSide = across(along, eye, to, halfTo);
        vertex(beams, matrix, from.subtract(nearSide), colourFrom);
        vertex(beams, matrix, from.add(nearSide), colourFrom);
        vertex(beams, matrix, to.add(farSide), colourTo);
        vertex(beams, matrix, to.subtract(farSide), colourTo);
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
     * A four-pointed flash facing the camera at a point, its points in its glow's colour round a white-hot heart, so
     * many times the muzzle flash's size and this strong.
     */
    private static void flash(VertexConsumer beams, Matrix4f matrix, Camera camera, Vec3 at, int glow, float size, float strength) {
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        diamond(beams, matrix, at, left, up, FLASH_REACH * size, FLASH_WIDTH * size, fade(glow, strength));
        diamond(beams, matrix, at, up, left, FLASH_REACH * size, FLASH_WIDTH * size, fade(glow, strength));
        diamond(beams, matrix, at, left, up, FLASH_HEART * size, FLASH_HEART * size, fade(HOT_CORE, strength));
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
