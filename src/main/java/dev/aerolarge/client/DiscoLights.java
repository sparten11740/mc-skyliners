package dev.aerolarge.client;

import com.mojang.logging.LogUtils;
import dev.aerolarge.AerolargeClientConfig;
import dev.aerolarge.block.DiscoBallBlock;
import dev.aerolarge.block.DiscoBallBlockEntity;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Vector3d;
import org.slf4j.Logger;

/**
 * The disco balls' spots of coloured light, sweeping round the room as each ball turns, through Veil's light renderer
 * (see {@link VeilLights}). Each ball near the camera that is on casts {@link #SPOTS} narrow beams out of its mirror
 * tiles, at their own heights all round it, so as it turns they sweep the walls, the floor and the ceiling; each is
 * one of Veil's area lights, a mirror's worth of glowing face that shines only within a narrow angle of straight out.
 * Their colours go round the rainbow, each spot its own colour, red, green, blue and every colour between. They fade
 * in as a ball spins up and out as it slows to a stop.
 *
 * <p>Veil's lights shine through blocks, so each spot reaches only a little past what it falls on: once a tick each is
 * looked along, among the ball's own blocks, for the first thing in its way, and it is made brighter the further that
 * is, so a spot is as bright on a far wall as on a near one, up to {@link #MOST_GAIN} times. Next to none of its light
 * gets through the wall to the hull's skin outside or to the room next door. In the frames between two ticks a spot
 * reaches part of the way from where it fell at the one to where it falls at the other (see {@link #look}).
 *
 * <p>A ball is seen by its client ticks (see {@link DiscoBallBlockEntity#clientTicker}): only the
 * {@link #MOST_BALLS} nearest within {@link #REACH} of the camera cast their spots, and a ball that stops ticking
 * (unloaded with its chunk, or taken down) takes them with it. A ball among an assembled airliner's blocks has its
 * spots placed anew every frame, as Sable draws the airliner at that partial tick, so they fly with it.
 *
 * <p>Never where Veil's lights cannot be drawn ({@link VeilLights#USABLE}), nor with the {@code discoLights} setting
 * off; and once drawing the spots has failed, the failure is logged and they stay off until the game is restarted. The
 * ball still turns and glints then, and lights the room round it as a lamp does.
 */
public final class DiscoLights {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** How far from the camera a ball's spots are drawn, in blocks. */
    private static final double REACH = 24;
    /** The most balls whose spots are drawn at once, the nearest. */
    private static final int MOST_BALLS = 2;
    /** A ball that has not ticked for longer than this, in game ticks, is gone. */
    private static final long GONE_AFTER = 2;
    /** The spots, twelve of them: four up at the ceiling, two out level and six down at the walls and floor. */
    private static final Spot[] SPOTS = {
        spot(0, -20), spot(30, 10), spot(60, -45), spot(90, 30), spot(120, -5), spot(150, -65),
        spot(180, 20), spot(210, -30), spot(240, 0), spot(270, -50), spot(300, 35), spot(330, -15),
    };
    /** How wide a spot is: the angle from straight out at which its light has faded away, in radians. */
    private static final float SPREAD = (float) Math.toRadians(10);
    /** Half the size of the mirror a spot shines from, in blocks. */
    private static final double MIRROR = 0.03;
    /** How far a spot is looked along for what it falls on, from the ball's surface, in blocks. */
    private static final double THROW = 9;
    /**
     * How far past what a spot falls on its light reaches, in blocks. Veil's light fades over its reach, by the cube of
     * what is left of it: the shorter this, the less gets through what it falls on, and the harder it fades in front.
     */
    private static final double BEYOND = 2.5;
    /** How bright a spot is on what it falls on, straight on. */
    private static final float BRIGHTNESS = 1.4F;
    /** The most a spot is brightened for falling on something far off, so it never blinds whoever stands in it. */
    private static final double MOST_GAIN = 20;
    /** How long the spots' colours take to go once round the rainbow, in ticks. */
    private static final float RAINBOW_TICKS = 160;
    /** How far apart round the rainbow one spot's colour is from the next's, as a share of it. */
    private static final float HUE_STEP = 5 / 12F;
    /** The balls near the player, each by when it last ticked, in game ticks: one unloaded and forgotten goes by itself. */
    private static final Map<DiscoBallBlockEntity, Long> TICKED = new WeakHashMap<>();
    /** The balls whose spots are drawn, and their spots. */
    private static final Map<DiscoBallBlockEntity, Lit> LIT = new HashMap<>();
    /** Veil's buffers, kept on while the spots are near. */
    private static final VeilLights.Buffers BUFFERS = new VeilLights.Buffers("disco_lights");
    /** Where a spot goes in the world, a way square to it, and the way it shines: reused, as spots move every frame. */
    private static final Vector3d MIRROR_AT = new Vector3d();
    private static final Vector3d ACROSS = new Vector3d();
    private static final Vector3d OUT = new Vector3d();
    /** Whether drawing the spots has failed; then none are drawn until the game is restarted. */
    private static boolean failed;

    private DiscoLights() {
    }

    /** A spot's way out of the ball as it hangs unturned, in radians: round from south toward east, and up from level. */
    private record Spot(float round, float up) {
    }

    /**
     * A ball's spots, as Veil's lights, and how far each falls from the ball's surface as the ball lay at the tick before
     * and as it lies at this one (see {@link #look}).
     */
    private static final class Lit {
        final Object[] lights = new Object[SPOTS.length];
        final double[] before = new double[SPOTS.length];
        final double[] now = new double[SPOTS.length];
        /** The game tick the spots were last looked along at. */
        long lookedAt = Long.MIN_VALUE;
    }

    /** A spot from its way out in degrees. */
    private static Spot spot(float round, float up) {
        return new Spot((float) Math.toRadians(round), (float) Math.toRadians(up));
    }

    /** A ball near the player ticked: it is still there. */
    public static void tick(DiscoBallBlockEntity ball) {
        if (ball.getLevel() != null) {
            TICKED.put(ball, ball.getLevel().getGameTime());
        }
    }

    /** Before each frame: lights the nearest balls' spots, and places them as the balls have turned. */
    public static void frame(RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !available() || !VeilLights.settled(minecraft)) {
            forget();
            return;
        }
        try {
            // The partial tick the balls are drawn with, and Sable the airliners.
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            List<DiscoBallBlockEntity> near = near(minecraft.level, minecraft.gameRenderer.getMainCamera().getPosition(), partialTick);
            Iterator<Map.Entry<DiscoBallBlockEntity, Lit>> lit = LIT.entrySet().iterator();
            while (lit.hasNext()) {
                Map.Entry<DiscoBallBlockEntity, Lit> ball = lit.next();
                if (!near.contains(ball.getKey())) {
                    remove(ball.getValue());
                    lit.remove();
                }
            }
            for (DiscoBallBlockEntity ball : near) {
                shine(minecraft.level, ball, LIT.computeIfAbsent(ball, key -> add()), partialTick);
            }
            BUFFERS.keep(!LIT.isEmpty(), Util.getMillis());
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            LOGGER.error("The disco balls' coloured spots could not be drawn; they stay off until the game is restarted", e);
            forget();
        }
    }

    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        forget();
        TICKED.clear();
    }

    /** A spot's colour at a moment, as RGB: round the rainbow, each spot a step on from the one before. */
    static int colour(int spot, float ticks) {
        return Mth.hsvToRgb(Mth.frac(spot * HUE_STEP + ticks / RAINBOW_TICKS), 1, 1);
    }

    /** Whether the spots may be drawn now at all. */
    private static boolean available() {
        return VeilLights.USABLE && !failed && AerolargeClientConfig.DISCO_LIGHTS.get();
    }

    /** The balls in this level still ticking and on (or slowing down) within reach of the camera, nearest first. */
    private static List<DiscoBallBlockEntity> near(Level level, Vec3 camera, float partialTick) {
        record Near(DiscoBallBlockEntity ball, double distance) {
        }
        long now = level.getGameTime();
        return TICKED.entrySet().stream()
            .filter(ticked -> now - ticked.getValue() <= GONE_AFTER)
            .map(Map.Entry::getKey)
            .filter(ball -> ball.getLevel() == level && !ball.isRemoved() && ball.power(partialTick) > 0)
            .map(ball -> new Near(ball, SableClient.toWorld(level, DiscoBallBlock.middle(ball.getBlockPos())).distanceTo(camera)))
            .filter(ball -> ball.distance() <= REACH)
            .sorted(Comparator.comparingDouble(Near::distance))
            .limit(MOST_BALLS)
            .map(Near::ball)
            .toList();
    }

    /** A ball's spots, dark until shone and not yet looked along. */
    private static Lit add() {
        Lit lit = new Lit();
        for (int i = 0; i < SPOTS.length; i++) {
            lit.lights[i] = VeilLights.add(MIRROR, MIRROR, SPREAD);
        }
        return lit;
    }

    private static void remove(Lit lit) {
        for (Object light : lit.lights) {
            VeilLights.remove(light);
        }
    }

    /**
     * Shines and places a ball's spots as it has turned at this partial tick: out of its surface, each as far as what it
     * falls on (see {@link #look}), in its colour now and as bright as the ball's speed has it; among an assembled
     * airliner's blocks, as Sable draws the airliner.
     */
    private static void shine(Level level, DiscoBallBlockEntity ball, Lit lit, float partialTick) {
        Vec3 middle = DiscoBallBlock.middle(ball.getBlockPos());
        look(level, ball, lit, middle);
        SableClient.Placing placing = SableClient.placing(middle, partialTick);
        float turned = ball.turned(partialTick);
        float power = ball.power(partialTick);
        float ticks = level.getGameTime() + partialTick;
        for (int i = 0; i < SPOTS.length; i++) {
            Vec3 out = out(SPOTS[i], turned);
            double reach = Mth.lerp(partialTick, lit.before[i], lit.now[i]) + BEYOND;
            double gain = Math.min(MOST_GAIN, Math.pow(reach / BEYOND, 3));
            int colour = colour(i, ticks);
            VeilLights.shine(lit.lights[i], (float) reach, (colour >> 16 & 0xFF) / 255F, (colour >> 8 & 0xFF) / 255F, (colour & 0xFF) / 255F,
                (float) (BRIGHTNESS * gain * power));
            set(MIRROR_AT, middle.add(out.scale(DiscoBallBlock.RADIUS)));
            set(ACROSS, new Vec3(-out.z, 0, out.x).normalize());
            set(OUT, out);
            if (placing != null) {
                placing.transform().transformPosition(MIRROR_AT);
                placing.turn().transform(ACROSS);
                placing.turn().transform(OUT);
            }
            VeilLights.place(lit.lights[i], MIRROR_AT, ACROSS, OUT);
        }
    }

    /**
     * Looks along a ball's spots once a game tick, for how far each falls with the ball turned as it lies at this tick,
     * and keeps how far they fell at the tick before, so the frames in between draw each spot part of the way from the
     * one to the other. A ball turns a degree and a half a tick: on the furthest wall a spot moves about a quarter of a
     * block in between, and where it runs off an edge onto a wall further off, it stretches out to it over that tick
     * instead of in one frame.
     */
    private static void look(Level level, DiscoBallBlockEntity ball, Lit lit, Vec3 middle) {
        long tick = level.getGameTime();
        if (lit.lookedAt == tick) {
            return;
        }
        // Looked along at the tick before, the ball lay then as it did at the start of this one.
        boolean next = lit.lookedAt == tick - 1;
        float before = ball.turned(0);
        float now = ball.turned(1);
        for (int i = 0; i < SPOTS.length; i++) {
            lit.before[i] = next ? lit.now[i] : falls(level, ball.getBlockPos(), middle, out(SPOTS[i], before));
            lit.now[i] = falls(level, ball.getBlockPos(), middle, out(SPOTS[i], now));
        }
        lit.lookedAt = tick;
    }

    /** The way a spot shines out of the ball, among its blocks, once the ball has turned so far about its rod. */
    private static Vec3 out(Spot spot, float turned) {
        float round = spot.round() + turned;
        float level = Mth.cos(spot.up());
        return new Vec3(level * Mth.sin(round), Mth.sin(spot.up()), level * Mth.cos(round));
    }

    /**
     * How far a spot going out this way falls from the ball's surface: to the first thing in its way past the ball's own
     * block (whose shape it starts inside), or {@link #THROW} if nothing is.
     */
    private static double falls(Level level, BlockPos own, Vec3 middle, Vec3 out) {
        // How far along it the spot leaves the ball's own block, through the first of its faces it reaches.
        double leave = Double.MAX_VALUE;
        for (Direction.Axis axis : Direction.Axis.values()) {
            double way = out.get(axis);
            if (Math.abs(way) > Mth.EPSILON) {
                int face = own.get(axis) + (way > 0 ? 1 : 0);
                leave = Math.min(leave, (face - middle.get(axis)) / way);
            }
        }
        Vec3 end = middle.add(out.scale(DiscoBallBlock.RADIUS + THROW));
        BlockHitResult hit = level.clip(new ClipContext(middle.add(out.scale(leave)), end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
            CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? THROW : hit.getLocation().distanceTo(middle) - DiscoBallBlock.RADIUS;
    }

    /** Drops every spot and lets Veil's buffers go. */
    private static void forget() {
        if (LIT.isEmpty() && !BUFFERS.held()) {
            return;
        }
        try {
            LIT.values().forEach(DiscoLights::remove);
            BUFFERS.release();
        } catch (RuntimeException | LinkageError e) {
            if (!failed) {
                failed = true;
                LOGGER.error("The disco balls' coloured spots could not be let go; they stay off until the game is restarted", e);
            }
        } finally {
            LIT.clear();
        }
    }

    private static Vector3d set(Vector3d into, Vec3 vector) {
        return into.set(vector.x, vector.y, vector.z);
    }
}
