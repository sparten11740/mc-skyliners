package dev.skyliners.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.skyliners.Skyliners;
import dev.skyliners.spaceweather.IncomingStrike;
import dev.skyliners.spaceweather.SpaceWeather;
import dev.skyliners.spaceweather.SpaceWeatherShown;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Space weather blowing round the freighter the player rides, or flies near, as their client draws it (see
 * {@link SpaceWeather}): a meteor shower's streaks, white-hot heads trailing orange, crossing low over the horizon the
 * way it comes from while it is told, then streaming past all round her; an asteroid storm's rocks, lumps of grey stone
 * tumbling as they go, a few far off on the horizon at first, then big ones sweeping by, some close enough to fill the
 * view; and each meteor or rock that strikes her, seen flying in to the spot on her hull where it lands. A solar flare
 * shows only as its glare (see {@link SpaceWeatherHud}).
 *
 * <p>The weather shown is one for the player, the last they were told of: a second freighter blowing her own near by
 * replaces it. Every streak and rock is worked out once, as the weather is told, from its seed, so everyone near sees
 * the same ones at the same moments, and they stream past where she would be had nothing struck her: she is seen thrown
 * about among them. They are drawn after the particles, past the fog, as far as the game draws anything; those the
 * camera cannot see are left out, and nothing at all is drawn while no weather blows.
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
     * How fast a streak flies, at least and at most, in blocks a second; how far it is seen flying, in blocks; how long
     * its tail is, at least and at most, in blocks, and how wide its head, in blocks; how far its way strays from the
     * weather's, in degrees, round and up or down.
     */
    private static final double STREAK_SLOWEST = 140;
    private static final double STREAK_FASTEST = 240;
    private static final double STREAK_PATH = 700;
    private static final double TAIL_SHORTEST = 18;
    private static final double TAIL_LONGEST = 40;
    private static final float STREAK_WIDTH = 0.5F;
    /**
     * How wide a streak's head is seen at the least, far off, as a share of how far off it is: about a quarter degree.
     */
    private static final double STREAK_SEEN_WIDTH = 0.004;
    private static final double STREAK_STRAY = 25;
    private static final double STREAK_STRAY_UP = 15;
    /** How much of its path a streak takes to flare up and to fade out, as shares. */
    private static final double STREAK_FLARE = 0.08;
    private static final double STREAK_FADE = 0.25;
    /** A streak's head, white-hot, and the end of its tail, orange, faded to nothing. */
    private static final int STREAK_HEAD = 0xFFF8E8;
    private static final int STREAK_TAIL = 0xFF7A1E;
    /**
     * An asteroid storm's rocks: how many show on the horizon while it is told, and how many sweep past while it is on
     * her; how far off the first are, at the nearest and farthest, in blocks; how big they are, the far ones and the
     * passing ones, at least and at most, in blocks across; how fast they go, in blocks a second; how far off the
     * passing ones go by, at the nearest and farthest; and how fast they tumble at most, in degrees a second.
     */
    private static final int TOLD_ROCKS = 8;
    private static final int STORM_ROCKS = 40;
    private static final double FAR_ROCK_NEAREST = 300;
    private static final double FAR_ROCK_FARTHEST = 460;
    private static final double FAR_ROCK_SMALLEST = 14;
    private static final double FAR_ROCK_BIGGEST = 30;
    private static final double ROCK_SMALLEST = 2;
    private static final double ROCK_BIGGEST = 14;
    private static final double FAR_ROCK_SPEED = 12;
    private static final double ROCK_SLOWEST = 25;
    private static final double ROCK_FASTEST = 60;
    private static final double ROCK_PATH = 900;
    private static final double ROCK_NEAREST = 30;
    private static final double ROCK_FARTHEST = 260;
    private static final double MOST_TUMBLE = 90;
    /** Dust streaks in an asteroid storm, each second while it is on her. */
    private static final double STORM_DUST = 3;
    /**
     * A rock's lumps: how many stick out of its middle block, how big each is, at least and at most, as a share of the
     * middle one, and how far out of it they stick, as a share of its size.
     */
    private static final int LUMPS = 3;
    private static final float LUMP_SMALLEST = 0.45F;
    private static final float LUMP_BIGGEST = 0.75F;
    private static final float LUMP_OUT = 0.4F;
    /** The stones a rock is made of, one picked for each, and how often one has a glowing lump of magma, as a share. */
    private static final BlockState[] STONES = {
        Blocks.STONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(), Blocks.TUFF.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState()
    };
    private static final BlockState MAGMA = Blocks.MAGMA_BLOCK.defaultBlockState();
    private static final float MAGMA_CHANCE = 0.3F;
    /**
     * Something striking her, seen flying in: how fast a meteor and a rock come, in blocks a second, and how big a rock
     * is, at least and at most, in blocks across.
     */
    private static final double STRIKING_METEOR_SPEED = 160;
    private static final double STRIKING_ROCK_SPEED = 70;
    private static final double STRIKING_ROCK_SMALLEST = 2.5;
    private static final double STRIKING_ROCK_BIGGEST = 4;
    /**
     * How streaks are drawn: glowing, added to what is behind them as lightning is, seen from either side, and hiding
     * nothing behind them.
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
    private static final List<Rock> ROCKS = new ArrayList<>();
    /** What strikes her, seen flying in. */
    private static final List<IncomingStrike> INCOMING = new ArrayList<>();

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
     * A rock: when it comes, in ticks after the weather was told; where along its way it passes nearest her, from where
     * she would be; the way it goes and how fast, in blocks a second; how far it is seen going, in blocks; how big it
     * is, in blocks across; the axis it tumbles about and how fast, in degrees a second; and its seed, for its lumps.
     */
    private record Rock(double born, Vec3 passing, Vec3 way, double speed, double path, double size, Vector3f axis, double tumble, long seed) {
        /** How long it is seen, in ticks. */
        double life() {
            return path / speed * TICKS;
        }
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
        shown = told;
        overAt = NEVER;
        INCOMING.clear();
        STREAKS.clear();
        ROCKS.clear();
        RandomSource random = RandomSource.create(told.seed());
        Vec3 out = SpaceWeather.toward(told.from());
        if (told.kind() == SpaceWeather.Kind.METEOR_SHOWER) {
            meteors(random, out, 0, told.warningTicks(), TOLD_METEORS, true);
            meteors(random, out, told.warningTicks(), told.warningTicks() + told.stormTicks(), STORM_METEORS, false);
        } else if (told.kind() == SpaceWeather.Kind.ASTEROID_STORM) {
            farRocks(random, out, told);
            passingRocks(random, out, told);
            meteors(random, out, told.warningTicks(), told.warningTicks() + told.stormTicks(), STORM_DUST, false);
        }
    }

    /**
     * Something seen flying in to strike her, told by the server; none while no weather is shown, as to someone come
     * late.
     */
    public static void incoming(IncomingStrike strike) {
        if (shown != null) {
            INCOMING.add(strike);
        }
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
    }

    private static void forget() {
        shown = null;
        overAt = NEVER;
        STREAKS.clear();
        ROCKS.clear();
        INCOMING.clear();
    }

    /**
     * Streaks coming so many a second between two moments after the weather was told, in ticks: out on the horizon the
     * way it comes from, crossing it either way, or passing her by, coming from there.
     */
    private static void meteors(RandomSource random, Vec3 out, double from, double to, double perSecond, boolean onHorizon) {
        int count = (int) Math.round((to - from) / TICKS * perSecond);
        Vec3 across = new Vec3(-out.z, 0, out.x);
        for (int i = 0; i < count; i++) {
            double born = Mth.lerp(random.nextDouble(), from, to);
            Vec3 way = strayed(random, onHorizon ? across.scale(random.nextBoolean() ? 1 : -1) : out.reverse(), STREAK_STRAY, STREAK_STRAY_UP);
            Vec3 passing = onHorizon ? onHorizon(random, out, TOLD_NEAREST, TOLD_FARTHEST) : beside(random, way, STORM_NEAREST, STORM_FARTHEST);
            STREAKS.add(new Streak(born, passing, way, Mth.lerp(random.nextDouble(), STREAK_SLOWEST, STREAK_FASTEST),
                Mth.lerp(random.nextDouble(), TAIL_SHORTEST, TAIL_LONGEST)));
        }
    }

    /** The big rocks on the horizon while an asteroid storm is told, drifting slowly in. */
    private static void farRocks(RandomSource random, Vec3 out, SpaceWeatherShown told) {
        for (int i = 0; i < TOLD_ROCKS; i++) {
            Vec3 passing = onHorizon(random, out, FAR_ROCK_NEAREST, FAR_ROCK_FARTHEST);
            double size = Mth.lerp(random.nextDouble(), FAR_ROCK_SMALLEST, FAR_ROCK_BIGGEST);
            double path = FAR_ROCK_SPEED * (told.warningTicks() + told.stormTicks()) / TICKS;
            ROCKS.add(rock(random, random.nextDouble() * told.warningTicks() * 0.5, passing, strayed(random, out.reverse(), 40, 10), FAR_ROCK_SPEED, path,
                size));
        }
    }

    /** The rocks sweeping past her while an asteroid storm is on her. */
    private static void passingRocks(RandomSource random, Vec3 out, SpaceWeatherShown told) {
        for (int i = 0; i < STORM_ROCKS; i++) {
            Vec3 way = strayed(random, out.reverse(), STREAK_STRAY, STREAK_STRAY_UP);
            double born = told.warningTicks() - ROCK_PATH / 2 / ROCK_SLOWEST * TICKS * random.nextDouble() + random.nextDouble() * told.stormTicks();
            double size = Mth.lerp(random.nextDouble() * random.nextDouble(), ROCK_SMALLEST, ROCK_BIGGEST);
            ROCKS.add(rock(random, Math.max(0, born), beside(random, way, ROCK_NEAREST, ROCK_FARTHEST), way,
                Mth.lerp(random.nextDouble(), ROCK_SLOWEST, ROCK_FASTEST), ROCK_PATH, size));
        }
    }

    private static Rock rock(RandomSource random, double born, Vec3 passing, Vec3 way, double speed, double path, double size) {
        Vector3f axis = new Vector3f(random.nextFloat() - 0.5F, random.nextFloat() - 0.5F, random.nextFloat() - 0.5F).normalize();
        return new Rock(born, passing, way, speed, path, size, axis, (random.nextDouble() * 2 - 1) * MOST_TUMBLE, random.nextLong());
    }

    /** A way strayed from another at random, so far round and so far up or down at most, in degrees; of length 1. */
    private static Vec3 strayed(RandomSource random, Vec3 way, double round, double up) {
        return way.yRot((float) Math.toRadians((random.nextDouble() * 2 - 1) * round))
            .add(0, Math.sin(Math.toRadians((random.nextDouble() * 2 - 1) * up)), 0).normalize();
    }

    /** A spot out on the horizon the way the weather comes from, so far off, give or take a little either side. */
    private static Vec3 onHorizon(RandomSource random, Vec3 out, double nearest, double farthest) {
        Vec3 across = new Vec3(-out.z, 0, out.x);
        double off = Mth.lerp(random.nextDouble(), nearest, farthest);
        return out.scale(off).add(across.scale((random.nextDouble() * 2 - 1) * TOLD_ACROSS)).add(0, (random.nextDouble() - 0.3) * off * 0.15, 0);
    }

    /**
     * A spot so far off at random to one side of a way, more often far than near, its nearest to her along that way.
     */
    private static Vec3 beside(RandomSource random, Vec3 way, double nearest, double farthest) {
        Vec3 side = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5);
        Vec3 across = side.subtract(way.scale(side.dot(way)));
        if (across.lengthSqr() < 1e-6) {
            across = new Vec3(-way.z, 0, way.x);
        }
        return across.normalize().scale(Mth.lerp(Math.sqrt(random.nextDouble()), nearest, farthest));
    }

    /** Each frame, after the particles: the weather's streaks and rocks and what strikes her, all past the fog. */
    public static void stage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || shown == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = DrawingClock.gameTime(partial);
        double since = now - shown.startedAt();
        if (since > shown.warningTicks() + shown.stormTicks() + GRACE_TICKS || (overAt != NEVER && DrawingClock.wholeTicks() - overAt > CLEAR_TICKS)) {
            forget();
            return;
        }
        INCOMING.removeIf(strike -> strike.at() < now);
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        Vec3 anchor = shown.centre().add(shown.velocity().scale(since / TICKS));
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        FogShape fogShape = RenderSystem.getShaderFogShape();
        FogRenderer.setupNoFog();
        drawRocks(pose, buffers, event.getFrustum(), eye, anchor, since);
        drawStreaks(pose.last().pose(), buffers, eye, anchor, since);
        drawIncoming(pose, buffers, eye, now, partial);
        buffers.endBatch();
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
        RenderSystem.setShaderFogShape(fogShape);
        pose.popPose();
    }

    /** The streaks seen now, each a glowing ribbon from its white head back along its fading orange tail. */
    private static void drawStreaks(Matrix4f matrix, MultiBufferSource buffers, Vec3 eye, Vec3 anchor, double since) {
        VertexConsumer glow = null;
        for (Streak streak : STREAKS) {
            double age = (since - streak.born()) / streak.life();
            if (age <= 0 || age >= 1) {
                continue;
            }
            glow = glow == null ? buffers.getBuffer(GLOWING) : glow;
            double along = (age - 0.5) * STREAK_PATH;
            Vec3 head = anchor.add(streak.passing()).add(streak.way().scale(along));
            float strength = (float) (Math.min(1, age / STREAK_FLARE) * Math.min(1, (1 - age) / STREAK_FADE));
            ribbon(glow, matrix, eye, head, head.subtract(streak.way().scale(Math.min(streak.tail(), along + STREAK_PATH / 2))), STREAK_WIDTH, strength);
        }
    }

    /** The rocks seen now, each tumbling as it goes, those the camera cannot see left out. */
    private static void drawRocks(PoseStack pose, MultiBufferSource buffers, Frustum frustum, Vec3 eye, Vec3 anchor, double since) {
        for (Rock rock : ROCKS) {
            double age = (since - rock.born()) / rock.life();
            if (age <= 0 || age >= 1) {
                continue;
            }
            Vec3 at = anchor.add(rock.passing()).add(rock.way().scale((age - 0.5) * rock.path()));
            if (frustum.isVisible(AABB.ofSize(at, rock.size() * 2, rock.size() * 2, rock.size() * 2))) {
                drawRock(pose, buffers, at.subtract(eye), rock.size(), rock.axis(), (float) (Math.toRadians(rock.tumble() * since / TICKS) % Mth.TWO_PI), rock.seed());
            }
        }
    }

    /**
     * What strikes her, seen flying in along its way to the spot on her hull it lands on, as that spot is drawn this
     * frame: a meteor's streak, or a rock tumbling in.
     */
    private static void drawIncoming(PoseStack pose, MultiBufferSource buffers, Vec3 eye, double now, float partial) {
        for (IncomingStrike strike : INCOMING) {
            Vec3 target = LaserBolts.inWorld(SableClient.placing(strike.onHer(), partial), strike.onHer());
            double left = (strike.at() - now) / TICKS;
            boolean rock = strike.kind() == SpaceWeather.Kind.ASTEROID_STORM;
            Vec3 at = target.subtract(strike.way().scale(left * (rock ? STRIKING_ROCK_SPEED : STRIKING_METEOR_SPEED)));
            if (rock) {
                RandomSource random = RandomSource.create(strike.seed());
                double size = Mth.lerp(random.nextDouble(), STRIKING_ROCK_SMALLEST, STRIKING_ROCK_BIGGEST);
                Vector3f axis = new Vector3f(random.nextFloat() - 0.5F, random.nextFloat() - 0.5F, random.nextFloat() - 0.5F).normalize();
                drawRock(pose, buffers, at.subtract(eye), size, axis, (float) (left * Math.toRadians(MOST_TUMBLE)), strike.seed());
            }
            VertexConsumer glow = buffers.getBuffer(GLOWING);
            float width = rock ? STREAK_WIDTH * 3 : STREAK_WIDTH * 1.5F;
            ribbon(glow, pose.last().pose(), eye, at, at.subtract(strike.way().scale(rock ? TAIL_SHORTEST : TAIL_LONGEST)), width, 1);
        }
    }

    /**
     * A rock at a spot from the camera, so big across, turned about its axis so far, in radians: a block of stone with
     * lumps of others sticking out of it, picked by its seed, now and then one of glowing magma; lit by the sun, as
     * space is.
     */
    private static void drawRock(PoseStack pose, MultiBufferSource buffers, Vec3 at, double size, Vector3f axis, float turned, long seed) {
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        RandomSource random = RandomSource.create(seed);
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(new Quaternionf().rotationAxis(turned, axis));
        float whole = (float) size;
        lump(pose, buffers, blocks, STONES[random.nextInt(STONES.length)], 0, 0, 0, whole);
        for (int i = 0; i < LUMPS; i++) {
            float share = Mth.lerp(random.nextFloat(), LUMP_SMALLEST, LUMP_BIGGEST);
            BlockState stone = random.nextFloat() < MAGMA_CHANCE && i == 0 ? MAGMA : STONES[random.nextInt(STONES.length)];
            Vector3f out = new Vector3f(random.nextFloat() - 0.5F, random.nextFloat() - 0.5F, random.nextFloat() - 0.5F).normalize().mul(whole * LUMP_OUT);
            lump(pose, buffers, blocks, stone, out.x, out.y, out.z, whole * share);
        }
        pose.popPose();
    }

    /** One block of a rock, so big across, its middle so far out of the rock's. */
    private static void lump(PoseStack pose, MultiBufferSource buffers, BlockRenderDispatcher blocks, BlockState stone, float x, float y, float z, float size) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(size, size, size);
        pose.translate(-0.5F, -0.5F, -0.5F);
        blocks.renderSingleBlock(stone, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
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
        int headColour = FastColor.ARGB32.color(Math.round(255 * strength), STREAK_HEAD);
        int tailColour = FastColor.ARGB32.color(0, STREAK_TAIL);
        glow.addVertex(matrix, (float) (from.x + half.x), (float) (from.y + half.y), (float) (from.z + half.z)).setColor(headColour);
        glow.addVertex(matrix, (float) (from.x - half.x), (float) (from.y - half.y), (float) (from.z - half.z)).setColor(headColour);
        glow.addVertex(matrix, (float) to.x, (float) to.y, (float) to.z).setColor(tailColour);
        glow.addVertex(matrix, (float) to.x, (float) to.y, (float) to.z).setColor(tailColour);
    }
}
