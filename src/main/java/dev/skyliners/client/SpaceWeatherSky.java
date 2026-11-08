package dev.skyliners.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.skyliners.Skyliners;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.spaceweather.IncomingStrike;
import dev.skyliners.spaceweather.RockBurst;
import dev.skyliners.spaceweather.RockField;
import dev.skyliners.spaceweather.SpaceRocks;
import dev.skyliners.spaceweather.SpaceWeather;
import dev.skyliners.spaceweather.SpaceWeatherShown;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Space weather blowing round the rocket or freighter the player rides, or flies near, as their client draws it (see
 * {@link SpaceWeather}): a meteor shower's streaks, white-hot heads trailing orange, crossing low over the horizon the
 * way it comes from while it is told, then streaming past all round her; an asteroid storm's rocks, lumpy, cratered
 * stones tumbling as they go (see {@link RockMeshes}), a few big ones far off on the horizon at first, then dozens
 * sweeping by, some close enough to fill the view; a giant asteroid's giant, many times bigger; and each meteor or rock
 * that strikes her, seen flying in to the spot on her hull where it lands, knocked off its course and slowed where her
 * laser cannon strikes it. A rock her cannon breaks bursts in a flash into tumbling chunks and a cloud of dust, with a
 * crunch. A solar flare shows only as its glare (see {@link SpaceWeatherHud}).
 *
 * <p>Nothing pops into view: every streak and rock sets out from far beyond the farthest the game draws, and fades in
 * as it comes, faint and small at first, and out again as it goes; so does each that strikes her.
 *
 * <p>The weather shown is one for the player, the last they were told of: a second ship blowing her own near by
 * replaces it. Every streak and rock is worked out once, as the weather is told, from its seed, so everyone near sees
 * the same ones at the same moments, as the server knows them to shoot at (see {@link RockField}), and they stream past
 * where she would be had nothing struck her: she is seen thrown about among them. They are drawn after the particles,
 * past the fog; those the camera cannot see are left out, and nothing at all is drawn while no weather blows.
 */
public final class SpaceWeatherSky {
    /** How long after the weather should have passed it is let go, unless told so before, in ticks. */
    private static final int GRACE_TICKS = 200;
    /** How long its all-clear shows, in ticks, after it is told over. */
    private static final int CLEAR_TICKS = 60;
    /**
     * A meteor shower's streaks: how many come each second while it is told and while it is on her; how far off they
     * pass while told, at the nearest and the farthest, in blocks, out on the horizon the way it comes from, and how
     * far either side of that way; and how far off they pass her while it is on her, at the nearest and the farthest.
     */
    private static final double TOLD_METEORS = 2;
    private static final double STORM_METEORS = 12;
    private static final double TOLD_NEAREST = 380;
    private static final double TOLD_FARTHEST = 560;
    private static final double TOLD_ACROSS = 260;
    private static final double STORM_NEAREST = 25;
    private static final double STORM_FARTHEST = 320;
    /**
     * How fast a streak flies, at least and at most, in blocks a second; how far it is seen flying, in blocks, from far
     * beyond where the game draws to as far again past her; how long its tail is, at least and at most, in blocks, and
     * how wide its head, in blocks; how far its way strays from the weather's, in degrees, round and up or down.
     */
    private static final double STREAK_SLOWEST = 140;
    private static final double STREAK_FASTEST = 240;
    private static final double STREAK_PATH = 1600;
    private static final double TAIL_SHORTEST = 18;
    private static final double TAIL_LONGEST = 40;
    private static final float STREAK_WIDTH = 0.5F;
    /**
     * How wide a streak's head is seen at the least, far off, as a share of how far off it is: about a quarter degree.
     */
    private static final double STREAK_SEEN_WIDTH = 0.004;
    private static final double STREAK_STRAY = 25;
    private static final double STREAK_STRAY_UP = 15;
    /** A streak's head, white-hot, and the end of its tail, orange, faded to nothing. */
    private static final int STREAK_HEAD = 0xFFF8E8;
    private static final int STREAK_TAIL = 0xFF7A1E;
    /** Dust streaks in an asteroid storm, each second while it is on her. */
    private static final double STORM_DUST = 3;
    /**
     * How far off anything of the weather is seen at the farthest, in blocks, as a share of the farthest the game draws
     * and at most; and from how much nearer it is seen whole, as a share of that: it fades in between.
     */
    private static final double SEEN_SHARE = 0.92;
    private static final double SEEN_FARTHEST = 720;
    private static final double WHOLE_SHARE = 0.55;
    /** How long a rock takes to fade in from nothing once it sets out, and out again at the end, in ticks. */
    private static final double FADE_TICKS = 40;
    /**
     * A rock flying in to strike her: how long its faint glowing trail of dust is, in blocks, as a share of its size;
     * how wide, as a share of its size; and how bright, from 0 to 1; how long one knocked off its course flies on past
     * her after it would have landed, in ticks, as the server lets it.
     */
    private static final double TRAIL_SHARE = 4;
    private static final float TRAIL_WIDTH = 0.12F;
    private static final float TRAIL_BRIGHT = 0.45F;
    private static final int FLIES_ON_TICKS = 100;
    /**
     * A rock bursting: how many chunks it bursts into, at least and how many more at most; how big each is, as a share
     * of the rock, at least and at most; how fast they fly out, in blocks a second for each block across the rock was,
     * and how much of the shot's way they carry on along, as a share; how fast they tumble at most, in degrees a second;
     * and how long they are seen, in ticks, fading out at the end.
     */
    private static final int CHUNKS = 8;
    private static final int MORE_CHUNKS = 7;
    private static final double CHUNK_SMALLEST = 0.12;
    private static final double CHUNK_BIGGEST = 0.35;
    private static final double CHUNK_SPEED = 1.6;
    private static final double CHUNK_CARRIED = 0.3;
    /**
     * How much of the rock's own speed its chunks keep, as a share; and how much faster or slower than the speed they
     * fly out at each one flies, at most, as a share either way.
     */
    private static final double CHUNK_KEEPS = 0.6;
    private static final double CHUNK_SPREAD = 0.6;
    private static final double CHUNK_TUMBLE = 400;
    private static final double CHUNK_TICKS = 80;
    /**
     * Its cloud of dust: how many puffs, how big each grows, as a share of the rock's size, from and to, how long it is
     * seen, in ticks, its colour and how bright at first, from 0 to 1; and its flash: how big, as a share of the rock,
     * how long it is seen, in ticks, and its colour.
     */
    private static final int PUFFS = 6;
    private static final double PUFF_FROM = 0.5;
    private static final double PUFF_TO = 2.2;
    private static final double PUFF_TICKS = 60;
    private static final int DUST = 0x8C7E6E;
    private static final float DUST_BRIGHT = 0.55F;
    private static final double FLASH_SIZE = 1.6;
    private static final double FLASH_TICKS = 8;
    private static final int FLASH = 0xFFE6B0;
    /**
     * How loud a burst's crunch is at the least, and how much louder for each block off it is, so it carries to the
     * gunner far off; and its pitch, higher for a small rock.
     */
    private static final float CRUNCH_LEAST = 2;
    private static final double CRUNCH_PER_BLOCK = 1.0 / 10;
    private static final float CRUNCH_PITCH = 1.15F;
    /**
     * How streaks, trails, dust and flashes are drawn: glowing, added to what is behind them as lightning is, seen from
     * either side, and hiding nothing behind them.
     */
    private static final RenderType GLOWING = RenderType.create(Skyliners.MOD_ID + "_space_streak", DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS, RenderType.TRANSIENT_BUFFER_SIZE, false, true, RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
            .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
            .setCullState(RenderStateShard.NO_CULL)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(false));
    /** Ticks a second. */
    private static final double TICKS = 20;
    /** Long after any game time: never. */
    private static final long NEVER = Long.MAX_VALUE;

    /** The weather shown now; null while none blows. */
    @Nullable
    private static SpaceWeatherShown shown;
    /** When it was told over, in game time; never while it still blows. */
    private static long overAt = NEVER;
    /** Its streaks and rocks, worked out once from its seed as it was told. */
    private static final List<Streak> STREAKS = new ArrayList<>();
    private static List<RockField.Rock> rocks = List.of();
    /** How each of its rocks looks, by number, worked out as it is first drawn. */
    private static final Map<Integer, RockMeshes.Look> LOOKS = new HashMap<>();
    /** What strikes her, seen flying in, by number, as the server last told it. */
    private static final Map<Integer, IncomingStrike> INCOMING = new LinkedHashMap<>();
    /** The numbers of its rocks burst by a laser cannon, as the server told them. */
    private static final Set<Integer> BURST = new HashSet<>();
    /** The chunks, dust and flashes of rocks bursting. */
    private static final List<Chunk> CHUNKS_FLYING = new ArrayList<>();
    private static final List<Puff> PUFFS_SEEN = new ArrayList<>();

    /**
     * A streak: when it comes, in ticks after the weather was told; where along its way it passes nearest her, from
     * where she would be; the way it flies, of length 1; how fast, in blocks a second; and its tail's length, in
     * blocks.
     */
    private record Streak(double born, Vec3 passing, Vec3 way, double speed, double tail) {
        /** How long it is seen, in ticks. */
        double life() {
            return STREAK_PATH / speed * TICKS;
        }
    }

    /**
     * A chunk of a rock burst: where it set out, in the world, and when, in game time; how fast it flies, in blocks a
     * second; how big it is, in blocks across; and how it looks.
     */
    private record Chunk(Vec3 from, double born, Vec3 velocity, double size, RockMeshes.Look look) {
    }

    /**
     * A puff of dust or a flash where a rock burst: where, in the world, and when, in game time; how big, from and to, in
     * blocks across; how long it is seen, in ticks; its colour and how bright at first.
     */
    private record Puff(Vec3 at, double born, double from, double to, double life, int colour, float bright) {
    }

    private SpaceWeatherSky() {
    }

    /** Weather told, or told over, by the server. */
    public static void shown(SpaceWeatherShown told) {
        if (told.over()) {
            if (shown != null && shown.startedAt() == told.startedAt()) {
                overAt = DrawingClock.wholeTicks();
                shown = told;
            }
            return;
        }
        forget();
        shown = told;
        RandomSource random = RandomSource.create(told.seed());
        Vec3 out = SpaceWeather.toward(told.from());
        if (told.kind() == SpaceWeather.Kind.METEOR_SHOWER) {
            meteors(random, out, 0, told.warningTicks(), TOLD_METEORS, true);
            meteors(random, out, told.warningTicks(), told.warningTicks() + told.stormTicks(), STORM_METEORS, false);
        } else if (told.kind().rocky()) {
            meteors(random, out, told.warningTicks(), told.warningTicks() + told.stormTicks(), STORM_DUST, false);
        }
        rocks = RockField.of(told);
    }

    /**
     * Something seen flying in to strike her, told by the server, or the same again on a new course once a laser cannon
     * has struck it; none while no weather is shown, as to someone come late.
     */
    public static void incoming(IncomingStrike strike) {
        if (shown != null) {
            INCOMING.put(strike.id(), strike);
        }
    }

    /**
     * A rock burst by a laser cannon, told by the server: drawn no more, and bursting where this client draws it, or
     * where the server had it, into chunks and dust in a flash, with a crunch.
     */
    public static void burst(RockBurst burst) {
        if (shown == null || shown.startedAt() != burst.startedAt() || !BURST.add(burst.id())) {
            return;
        }
        double now = DrawingClock.gameTime(0);
        IncomingStrike strike = INCOMING.remove(burst.id());
        RockField.Rock rock = burst.id() >= 0 && burst.id() < rocks.size() ? rocks.get(burst.id()) : null;
        double size = strike != null ? strike.size() : rock != null ? rock.size() : 3;
        Vec3 drawn = strike != null ? strike.position(target(strike, 0), now) : rock != null ? rock.at(shown.anchor(now - shown.startedAt()), now - shown.startedAt()) : null;
        Vec3 at = drawn != null && drawn.distanceTo(burst.at()) < size * 2 ? drawn : burst.at();
        RandomSource random = RandomSource.create(burst.id() * 31L + burst.startedAt());
        Vec3 moving = strike != null ? strike.way().scale(strike.speed()) : rock != null ? rock.way().scale(rock.speed()) : Vec3.ZERO;
        bursting(random, at, burst.way(), moving, size, now);
        crunch(at, size);
    }

    /** How many rocks this client has seen burst in the weather it shows, for a play-test to read. */
    public static int burstsSeen() {
        return BURST.size();
    }

    /** Whether a rock, by its number, has been burst. */
    private static boolean gone(int id) {
        return BURST.contains(id);
    }

    /**
     * A rock bursting at a point, so big across, struck along a way of length 1 and moving so fast, in blocks a second:
     * its chunks flying out, carried on along its way and the shot's, and its dust and flash.
     */
    private static void bursting(RandomSource random, Vec3 at, Vec3 shot, Vec3 moving, double size, double now) {
        int count = CHUNKS + random.nextInt(MORE_CHUNKS + 1);
        for (int i = 0; i < count; i++) {
            Vec3 out = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).normalize();
            double speed = CHUNK_SPEED * size * (1 + (random.nextDouble() * 2 - 1) * CHUNK_SPREAD);
            Vec3 velocity = out.scale(speed).add(shot.scale(CHUNK_SPEED * size * CHUNK_CARRIED)).add(moving.scale(CHUNK_KEEPS));
            double chunk = size * Mth.lerp(random.nextDouble(), CHUNK_SMALLEST, CHUNK_BIGGEST);
            CHUNKS_FLYING.add(new Chunk(at.add(out.scale(size * 0.3)), now, velocity, chunk, RockMeshes.look(random.nextLong())));
        }
        for (int i = 0; i < PUFFS; i++) {
            Vec3 off = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).scale(size * 0.6);
            PUFFS_SEEN.add(new Puff(at.add(off), now, size * PUFF_FROM, size * PUFF_TO * (0.7 + random.nextDouble() * 0.6), PUFF_TICKS, DUST, DUST_BRIGHT));
        }
        PUFFS_SEEN.add(new Puff(at, now, size * FLASH_SIZE, size * FLASH_SIZE * 1.4, FLASH_TICKS, FLASH, 1));
    }

    /** A rock's crunch as it bursts, heard as far off as the gunner who broke it, deeper for a big one. */
    private static void crunch(Vec3 at, double size) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 ear = minecraft.gameRenderer.getMainCamera().getPosition();
        float volume = (float) (CRUNCH_LEAST + ear.distanceTo(at) * CRUNCH_PER_BLOCK);
        float pitch = (float) (CRUNCH_PITCH * Mth.clamp(4 / Math.max(size, 1), 0.5, 1.4));
        minecraft.getSoundManager().play(new SimpleSoundInstance(SkylinersRegistry.SPACE_ASTEROID_STRIKE.get(), SoundSource.BLOCKS, volume, pitch,
            RandomSource.create(), at.x, at.y, at.z));
    }

    /** What blows now, told or on her, for her scanner's and the screen's pictures; null for none, or once over. */
    @Nullable
    static SpaceWeather.Kind blowing() {
        return shown == null || overAt != NEVER ? null : shown.kind();
    }

    /** The weather shown now, told or on her or over a moment since; null for none. */
    @Nullable
    static SpaceWeatherShown shown() {
        return shown;
    }

    /** How long ago it was told over, in ticks; less than nothing while it still blows. */
    static long sinceOver() {
        return overAt == NEVER ? -1 : DrawingClock.wholeTicks() - overAt;
    }

    /** Leaving a world lets go of any weather shown there. */
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        forget();
        shown = null;
    }

    private static void forget() {
        overAt = NEVER;
        STREAKS.clear();
        rocks = List.of();
        LOOKS.clear();
        INCOMING.clear();
        BURST.clear();
        CHUNKS_FLYING.clear();
        PUFFS_SEEN.clear();
    }

    /**
     * Whether a laser cannon's sight from a point along a way rests on a rock of the weather shown within so many
     * blocks, as the server would find it (see {@link SpaceRocks}): for the sight to lock on to it.
     */
    static boolean rockSighted(Vec3 from, Vec3 way, double reach) {
        if (shown == null || overAt != NEVER) {
            return false;
        }
        Vec3 unit = way.normalize();
        double now = DrawingClock.gameTime(0);
        double since = now - shown.startedAt();
        Vec3 anchor = shown.anchor(since);
        for (RockField.Rock rock : rocks) {
            boolean seen = !gone(rock.id()) && rock.flying(since);
            if (seen && SpaceRocks.meets(from, unit, rock.at(anchor, since), rock.size() / 2 + SpaceRocks.GRACE, reach) >= 0) {
                return true;
            }
        }
        for (IncomingStrike strike : INCOMING.values()) {
            boolean seen = strike.rock() && now < strike.at() + (strike.misses() ? FLIES_ON_TICKS : 0);
            if (seen && SpaceRocks.meets(from, unit, strike.position(target(strike, 0), now), strike.size() / 2 + SpaceRocks.GRACE, reach) >= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Streaks coming so many a second between two moments after the weather was told, in ticks: out on the horizon the
     * way it comes from, crossing it either way, or passing her by, coming from there.
     */
    private static void meteors(RandomSource random, Vec3 out, double from, double to, double perSecond, boolean onHorizon) {
        int count = (int) Math.round((to - from) / TICKS * perSecond);
        Vec3 across = new Vec3(-out.z, 0, out.x);
        for (int i = 0; i < count; i++) {
            Vec3 way = strayed(random, onHorizon ? across.scale(random.nextBoolean() ? 1 : -1) : out.reverse(), STREAK_STRAY, STREAK_STRAY_UP);
            Vec3 passing = onHorizon ? onHorizon(random, out) : beside(random, way);
            double speed = Mth.lerp(random.nextDouble(), STREAK_SLOWEST, STREAK_FASTEST);
            double born = Math.max(0, Mth.lerp(random.nextDouble(), from, to) - STREAK_PATH / 2 / speed * TICKS);
            STREAKS.add(new Streak(born, passing, way, speed, Mth.lerp(random.nextDouble(), TAIL_SHORTEST, TAIL_LONGEST)));
        }
    }

    /** A way strayed from another at random, so far round and so far up or down at most, in degrees; of length 1. */
    private static Vec3 strayed(RandomSource random, Vec3 way, double round, double up) {
        return way.yRot((float) Math.toRadians((random.nextDouble() * 2 - 1) * round))
            .add(0, Math.sin(Math.toRadians((random.nextDouble() * 2 - 1) * up)), 0).normalize();
    }

    /** A spot out on the horizon the way the weather comes from, so far off, give or take a little either side. */
    private static Vec3 onHorizon(RandomSource random, Vec3 out) {
        Vec3 across = new Vec3(-out.z, 0, out.x);
        double off = Mth.lerp(random.nextDouble(), TOLD_NEAREST, TOLD_FARTHEST);
        return out.scale(off).add(across.scale((random.nextDouble() * 2 - 1) * TOLD_ACROSS)).add(0, (random.nextDouble() - 0.3) * off * 0.15, 0);
    }

    /** A spot at random to one side of a way, more often far than near, its nearest to her along that way. */
    private static Vec3 beside(RandomSource random, Vec3 way) {
        Vec3 side = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5);
        Vec3 across = side.subtract(way.scale(side.dot(way)));
        if (across.lengthSqr() < 1e-6) {
            across = new Vec3(-way.z, 0, way.x);
        }
        return across.normalize().scale(Mth.lerp(Math.sqrt(random.nextDouble()), STORM_NEAREST, STORM_FARTHEST));
    }

    /** Where the spot on her a strike comes at is drawn at a partial tick, in the world. */
    private static Vec3 target(IncomingStrike strike, float partial) {
        return LaserBolts.inWorld(SableClient.placing(strike.onHer(), partial), strike.onHer());
    }

    /**
     * Each frame, after the particles: the weather's streaks and rocks, what strikes her, and the chunks and dust of
     * rocks bursting, all past the fog.
     */
    public static void stage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || shown == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = DrawingClock.gameTime(partial);
        double since = now - shown.startedAt();
        if (since > shown.warningTicks() + shown.stormTicks() + GRACE_TICKS || (overAt != NEVER && DrawingClock.wholeTicks() - overAt > CLEAR_TICKS)) {
            forget();
            shown = null;
            return;
        }
        INCOMING.values().removeIf(strike -> now > strike.at() + (strike.misses() ? FLIES_ON_TICKS : 0));
        CHUNKS_FLYING.removeIf(chunk -> now - chunk.born() > CHUNK_TICKS);
        PUFFS_SEEN.removeIf(puff -> now - puff.born() > puff.life());
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        Vec3 anchor = shown.anchor(since);
        Vector3f sun = sunWay(partial);
        double seen = Math.min(SEEN_FARTHEST, Minecraft.getInstance().gameRenderer.getDepthFar() * SEEN_SHARE);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        FogShape fogShape = RenderSystem.getShaderFogShape();
        FogRenderer.setupNoFog();
        Sky sky = new Sky(pose, buffers, event.getFrustum(), eye, sun, seen);
        drawRocks(sky, anchor, since);
        drawIncoming(sky, now, partial);
        drawChunks(sky, now);
        buffers.endBatch(RockMeshes.ROCK);
        drawStreaks(sky, anchor, since);
        drawTrails(sky, now, partial);
        drawPuffs(sky, camera, now);
        buffers.endBatch(GLOWING);
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
        RenderSystem.setShaderFogShape(fogShape);
        pose.popPose();
    }

    /**
     * What a frame of the weather is drawn with: its pose and buffers, what the camera sees, where its eye is, the way
     * to the sun, and how far off anything is seen at the farthest, in blocks.
     */
    private record Sky(PoseStack pose, MultiBufferSource buffers, Frustum frustum, Vec3 eye, Vector3f sun, double seen) {
        /** How much of something so far off is seen, from 1 near to none past {@link #seen}, fading in between. */
        float fade(Vec3 at) {
            double away = at.distanceTo(eye);
            double whole = seen * WHOLE_SHARE;
            return away >= seen ? 0 : away <= whole ? 1 : (float) (1 - (away - whole) / (seen - whole));
        }

        /** Whether a ball so big across round a point is in the camera's sight. */
        boolean inSight(Vec3 at, double size) {
            return frustum.isVisible(AABB.ofSize(at, size, size, size));
        }
    }

    /**
     * The way to the sun, of length 1, where the game puts it at this partial tick: overhead at noon, setting in the
     * west.
     */
    private static Vector3f sunWay(float partial) {
        ClientLevel level = Minecraft.getInstance().level;
        double turned = level == null ? 0 : level.getTimeOfDay(partial) * Math.PI * 2;
        return new Vector3f((float) -Math.sin(turned), (float) Math.cos(turned), 0);
    }

    /** How a rock looks, by its number and seed, worked out once. */
    private static RockMeshes.Look look(int id, long seed) {
        return LOOKS.computeIfAbsent(id, key -> RockMeshes.look(seed));
    }

    /**
     * The rocks streaming past now, each tumbling as it goes, fading in as it comes from far off and out as it goes,
     * those the camera cannot see left out.
     */
    private static void drawRocks(Sky sky, Vec3 anchor, double since) {
        VertexConsumer buffer = sky.buffers().getBuffer(RockMeshes.ROCK);
        for (RockField.Rock rock : rocks) {
            double age = rock.age(since);
            if (age <= 0 || age >= 1 || gone(rock.id())) {
                continue;
            }
            Vec3 at = rock.at(anchor, since);
            float fade = Math.min(sky.fade(at), (float) Math.min(1, Math.min(age, 1 - age) * rock.life() / FADE_TICKS));
            if (fade > 0 && sky.inSight(at, rock.size() * 2)) {
                RockMeshes.Look look = look(rock.id(), rock.seed());
                Vec3 off = at.subtract(sky.eye());
                RockMeshes.draw(buffer, sky.pose(), off.x, off.y, off.z, rock.size(), look, look.turned(since / TICKS), fade, sky.sun());
            }
        }
    }

    /** The rocks flying in to strike her, along their courses to the spots on her hull they land on. */
    private static void drawIncoming(Sky sky, double now, float partial) {
        VertexConsumer buffer = sky.buffers().getBuffer(RockMeshes.ROCK);
        for (IncomingStrike strike : INCOMING.values()) {
            if (!strike.rock() || gone(strike.id())) {
                continue;
            }
            Vec3 at = strike.position(target(strike, partial), now);
            float fade = sky.fade(at);
            if (fade > 0 && sky.inSight(at, strike.size() * 2)) {
                RockMeshes.Look look = look(strike.id(), strike.seed());
                Vec3 off = at.subtract(sky.eye());
                RockMeshes.draw(buffer, sky.pose(), off.x, off.y, off.z, strike.size(), look, look.turned((strike.at() - now) / TICKS), fade, sky.sun());
            }
        }
    }

    /** The chunks of rocks bursting, flying out and tumbling, fading as they go. */
    private static void drawChunks(Sky sky, double now) {
        VertexConsumer buffer = sky.buffers().getBuffer(RockMeshes.ROCK);
        for (Chunk chunk : CHUNKS_FLYING) {
            double age = now - chunk.born();
            Vec3 at = chunk.from().add(chunk.velocity().scale(age / TICKS));
            float fade = Math.min(sky.fade(at), (float) Math.min(1, (CHUNK_TICKS - age) / FADE_TICKS));
            if (fade > 0 && sky.inSight(at, chunk.size() * 2)) {
                Vec3 off = at.subtract(sky.eye());
                Quaternionf turned = chunk.look().turned(age / TICKS * CHUNK_TUMBLE / Math.max(1, Math.abs(chunk.look().firstTumble())));
                RockMeshes.draw(buffer, sky.pose(), off.x, off.y, off.z, chunk.size(), chunk.look(), turned, fade, sky.sun());
            }
        }
    }

    /** The streaks seen now, each a glowing ribbon from its white head back along its fading orange tail. */
    private static void drawStreaks(Sky sky, Vec3 anchor, double since) {
        VertexConsumer glow = sky.buffers().getBuffer(GLOWING);
        Matrix4f matrix = sky.pose().last().pose();
        for (Streak streak : STREAKS) {
            double age = (since - streak.born()) / streak.life();
            if (age <= 0 || age >= 1) {
                continue;
            }
            double along = (age - 0.5) * STREAK_PATH;
            Vec3 head = anchor.add(streak.passing()).add(streak.way().scale(along));
            Vec3 tail = head.subtract(streak.way().scale(Math.min(streak.tail(), along + STREAK_PATH / 2)));
            ribbon(glow, matrix, sky.eye(), head, tail, STREAK_WIDTH, sky.fade(head));
        }
    }

    /** What flies in to strike her: a meteor's streak, or the glowing trail of a rock, along its course. */
    private static void drawTrails(Sky sky, double now, float partial) {
        VertexConsumer glow = sky.buffers().getBuffer(GLOWING);
        Matrix4f matrix = sky.pose().last().pose();
        for (IncomingStrike strike : INCOMING.values()) {
            if (gone(strike.id())) {
                continue;
            }
            Vec3 at = strike.position(target(strike, partial), now);
            Vec3 course = strike.way().scale(strike.speed()).add(strike.drift()).normalize();
            double tail = strike.rock() ? strike.size() * TRAIL_SHARE : TAIL_LONGEST;
            float width = strike.rock() ? (float) Math.max(STREAK_WIDTH, strike.size() * TRAIL_WIDTH) : STREAK_WIDTH * 1.5F;
            ribbon(glow, matrix, sky.eye(), at, at.subtract(course.scale(tail)), width, sky.fade(at) * (strike.rock() ? TRAIL_BRIGHT : 1));
        }
    }

    /** The dust and flashes of rocks bursting, each a soft glow facing the camera, growing and fading. */
    private static void drawPuffs(Sky sky, Camera camera, double now) {
        VertexConsumer glow = sky.buffers().getBuffer(GLOWING);
        Matrix4f matrix = sky.pose().last().pose();
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        for (Puff puff : PUFFS_SEEN) {
            double age = (now - puff.born()) / puff.life();
            if (age < 0 || age >= 1 || !sky.inSight(puff.at(), puff.to())) {
                continue;
            }
            float half = (float) (Mth.lerp(Math.sqrt(age), puff.from(), puff.to()) / 2);
            float bright = (float) (puff.bright() * (1 - age) * (1 - age)) * sky.fade(puff.at());
            Vec3 off = puff.at().subtract(sky.eye());
            billboard(glow, matrix, off, left, up, half, FastColor.ARGB32.color(Math.round(255 * bright), puff.colour()));
        }
    }

    /** A soft square glowing in a colour, facing the camera, its middle so far from the camera and so big each way. */
    private static void billboard(VertexConsumer glow, Matrix4f matrix, Vec3 off, Vector3f left, Vector3f up, float half, int colour) {
        float x = (float) off.x;
        float y = (float) off.y;
        float z = (float) off.z;
        int faded = FastColor.ARGB32.color(0, colour);
        float lx = left.x() * half;
        float ly = left.y() * half;
        float lz = left.z() * half;
        float ux = up.x() * half;
        float uy = up.y() * half;
        float uz = up.z() * half;
        quad(glow, matrix, x, y, z, faded, colour, x + lx, y + ly, z + lz, x + ux, y + uy, z + uz);
        quad(glow, matrix, x, y, z, faded, colour, x + ux, y + uy, z + uz, x - lx, y - ly, z - lz);
        quad(glow, matrix, x, y, z, faded, colour, x - lx, y - ly, z - lz, x - ux, y - uy, z - uz);
        quad(glow, matrix, x, y, z, faded, colour, x - ux, y - uy, z - uz, x + lx, y + ly, z + lz);
    }

    /** One quarter of a soft glow: bright at its middle, faded at its two outer corners. */
    private static void quad(VertexConsumer glow, Matrix4f matrix, float x, float y, float z, int faded, int colour, float ax, float ay, float az,
        float bx, float by, float bz) {
        glow.addVertex(matrix, x, y, z).setColor(colour);
        glow.addVertex(matrix, ax, ay, az).setColor(faded);
        glow.addVertex(matrix, (ax + bx) / 2, (ay + by) / 2, (az + bz) / 2).setColor(faded);
        glow.addVertex(matrix, bx, by, bz).setColor(faded);
    }

    /**
     * A glowing ribbon from a head back to a tail, both in the world, drawn from the camera's eye, turned to face it,
     * as wide as given at the head and tapering to nothing at the tail, white-hot fading into orange and to nothing, as
     * bright as the strength given, 0 to 1.
     */
    private static void ribbon(VertexConsumer glow, Matrix4f matrix, Vec3 eye, Vec3 head, Vec3 tail, float width, float strength) {
        Vec3 from = head.subtract(eye);
        Vec3 to = tail.subtract(eye);
        Vec3 side = from.subtract(to).cross(from);
        if (side.lengthSqr() < 1e-9 || strength <= 0) {
            return;
        }
        Vec3 half = side.normalize().scale(Math.max(width, from.length() * STREAK_SEEN_WIDTH));
        int headColour = FastColor.ARGB32.color(Math.round(255 * Math.min(1, strength)), STREAK_HEAD);
        int tailColour = FastColor.ARGB32.color(0, STREAK_TAIL);
        glow.addVertex(matrix, (float) (from.x + half.x), (float) (from.y + half.y), (float) (from.z + half.z)).setColor(headColour);
        glow.addVertex(matrix, (float) (from.x - half.x), (float) (from.y - half.y), (float) (from.z - half.z)).setColor(headColour);
        glow.addVertex(matrix, (float) to.x, (float) to.y, (float) to.z).setColor(tailColour);
        glow.addVertex(matrix, (float) to.x, (float) to.y, (float) to.z).setColor(tailColour);
    }
}
