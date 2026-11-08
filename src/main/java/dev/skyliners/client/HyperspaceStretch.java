package dev.skyliners.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.skyliners.autopilot.HyperspaceWake;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * A freighter stretching into a hyperspace jump and snapping back out of one, as those outside her see it, along with
 * the light and the streaks of her wake (see {@link HyperspaceWakes}). Over the last moment before she goes she is
 * drawn longer and longer the way she points, her stern holding where it is and her bow running on ahead of it,
 * whitening as she goes, until she is gone in the flash, the way a ship in the films smears into hyperspace. Where she
 * comes out she is there drawn long, trailing back the way she came, and snaps forward into her own shape.
 *
 * <p>Her blocks are drawn a second time over her own, stretched, from what Sable already has of them (see
 * {@link SableClient#drawBlocks}), and nudged toward the camera so that where the two lie flush the stretched one
 * shows. Nothing is built for it, and nothing is drawn but for the second or so it lasts. Her block entities are left
 * as they are. Alongside Sodium, Sable draws ships another way, and she only vanishes into the flash as before.
 */
public final class HyperspaceStretch {
    /** How many ticks before she is shown gone she starts to stretch: the last of her computer's count. */
    private static final int STRETCH_TICKS = 16;
    /**
     * How many ticks after the server puts her somewhere else she is drawn at her longest, and held so should this
     * client show her going later: a tick before a client on the same machine was seen to show her gone, as Sable
     * smooths her flight.
     */
    private static final int SOONEST_GONE = 1;
    /** How many times her own length she is drawn as she goes, and as she is first seen coming out. */
    private static final double LONGEST = 6;
    /**
     * How sharply the stretch gathers pace as she goes, and lets go as she comes out: the power taken of the share of
     * its time gone by, so the higher, the more of it comes in her last moment going and her first coming out.
     */
    private static final double SHARPNESS = 3;
    /** How long she takes to snap back into her own shape where she comes out, in ticks. */
    private static final int SNAP_TICKS = 8;
    /**
     * How long past when she should be shown gone at the soonest she is still drawn at her longest, in ticks, should
     * this client show her going later; and how long after she should be shown coming out she is looked for there.
     */
    private static final int HELD_TICKS = 10;
    private static final int LOOKED_FOR_TICKS = 20;
    /**
     * The furthest she is drawn moving from one frame to the next flying, in blocks: further, and the jump has put her
     * somewhere else and she is gone from here.
     */
    private static final double FURTHEST_STEP = 32;
    /** How near where she comes out her middle is drawn once she is there, past how far her hull reaches, in blocks. */
    private static final double THERE_WITHIN = 16;
    /** How bright her red, green and blue are drawn at her longest, as a share of how she is lit: a blue white. */
    private static final float[] WHITEST = {2.2F, 2.4F, 3.0F};
    /**
     * How far her stretched copy is nudged toward the camera, as the depth offset's slope factor and units, so it shows
     * over her own blocks wherever the two lie flush, as her deck and her sides do.
     */
    private static final float NUDGE_FACTOR = -1.0F;
    private static final float NUDGE_UNITS = -10.0F;
    /** The layers of her blocks drawn stretched, the solid ones first and the glass last. */
    private static final List<RenderType> LAYERS = List.of(RenderType.solid(), RenderType.cutoutMipped(), RenderType.cutout(), RenderType.translucent());

    /** The stretches under way on this client, and the level they are in. */
    private static final List<Stretch> STRETCHES = new ArrayList<>();
    private static WeakReference<ClientLevel> shownIn = new WeakReference<>(null);
    /**
     * What has been drawn of the last jump shown, for a play-test to read: how many jumps were shown, and how many
     * times her length she was drawn at most and in how many frames, going and coming out.
     */
    private static int jumps;
    private static double longestGoing;
    private static int goingFrames;
    private static double longestComing;
    private static int comingFrames;

    /**
     * A step of a jump she is drawn stretched in, as the server told it, and when it was told; for her going, where her
     * middle was drawn last frame, and for her coming out, when she was first drawn there.
     */
    private static final class Stretch {
        private final HyperspaceWake told;
        private final long began;
        @Nullable
        private Vec3 lastMiddle;
        private double cameAt = Double.NaN;
        private boolean over;

        private Stretch(HyperspaceWake told, long began) {
            this.told = told;
            this.began = began;
        }

        private boolean going() {
            return told.stage() == HyperspaceWake.Stage.GATHER;
        }

        /** How many ticks after it was told this step is let go of, drawn or not. */
        private int lasts() {
            return going() ? told.ticks() + SOONEST_GONE + HELD_TICKS : HyperspaceWakes.SHOWN_LATER + LOOKED_FOR_TICKS + SNAP_TICKS;
        }
    }

    private HyperspaceStretch() {
    }

    /** A step of a jump seen from outside: her letting go and her coming out are drawn stretched. */
    static void seen(ClientLevel level, HyperspaceWake wake) {
        if (wake.stage() != HyperspaceWake.Stage.GATHER && wake.stage() != HyperspaceWake.Stage.ARRIVE) {
            return;
        }
        if (level != shownIn.get()) {
            STRETCHES.clear();
            shownIn = new WeakReference<>(level);
        }
        if (wake.stage() == HyperspaceWake.Stage.GATHER) {
            jumps++;
            longestGoing = 1;
            goingFrames = 0;
            longestComing = 1;
            comingFrames = 0;
        }
        STRETCHES.add(new Stretch(wake, DrawingClock.wholeTicks()));
    }

    /** Once a client tick: the stretches over let go. */
    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level != shownIn.get()) {
            STRETCHES.clear();
            return;
        }
        long now = DrawingClock.wholeTicks();
        STRETCHES.removeIf(stretch -> stretch.over || now - stretch.began > stretch.lasts());
    }

    /** After the block entities are drawn: each ship stretching, drawn stretched over herself. */
    public static void stage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || STRETCHES.isEmpty()) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = DrawingClock.ticks(partialTick);
        for (Stretch stretch : STRETCHES) {
            draw(event, stretch, now, partialTick);
        }
    }

    /** What this client last drew of a jump seen from outside; null before it was shown one. */
    public static HyperspaceWake.Stretched stretched() {
        return jumps > 0 ? new HyperspaceWake.Stretched(jumps, longestGoing, goingFrames, longestComing, comingFrames) : null;
    }

    /** One ship drawn as long as she is stretched now, if she is here and stretched at all. */
    private static void draw(RenderLevelStageEvent event, Stretch stretch, double now, float partialTick) {
        Vec3 ahead = stretch.told.ahead().normalize();
        SableClient.Ends ends = stretch.over ? null : SableClient.ends(stretch.told.aboard(), ahead, partialTick);
        if (ends == null) {
            return;
        }
        boolean going = stretch.going();
        double length = going ? goingLength(stretch, ends, now) : comingLength(stretch, ends, now);
        if (length > 1 && drawStretched(event, stretch.told.aboard(), going ? ends.back() : ends.front(), ahead, length)) {
            drawnAt(going, length);
        }
    }

    /** How many times her own length she is drawn as she goes, now; once she is gone from here, her own. */
    private static double goingLength(Stretch stretch, SableClient.Ends ends, double now) {
        Vec3 last = stretch.lastMiddle;
        stretch.lastMiddle = ends.middle();
        if (last != null && last.distanceToSqr(ends.middle()) > FURTHEST_STEP * FURTHEST_STEP) {
            stretch.over = true;
            return 1;
        }
        double goneAt = stretch.told.ticks() + SOONEST_GONE;
        double share = Mth.clamp((now - stretch.began - goneAt) / STRETCH_TICKS + 1, 0, 1);
        return 1 + (LONGEST - 1) * Math.pow(share, SHARPNESS);
    }

    /**
     * How many times her own length she is drawn as she comes out, now: at her longest the first frame she is drawn
     * there, snapping back from then on; her own before.
     */
    private static double comingLength(Stretch stretch, SableClient.Ends ends, double now) {
        if (Double.isNaN(stretch.cameAt)) {
            double within = stretch.told.reach() + THERE_WITHIN;
            if (ends.middle().distanceToSqr(stretch.told.at()) > within * within) {
                return 1;
            }
            stretch.cameAt = now;
        }
        double share = (now - stretch.cameAt) / SNAP_TICKS;
        if (share >= 1) {
            stretch.over = true;
            return 1;
        }
        return 1 + (LONGEST - 1) * Math.pow(1 - share, SHARPNESS);
    }

    /**
     * Her blocks drawn again so many times her length along a way, from one end of her that holds still, whitening the
     * longer she is; whether they were drawn.
     */
    private static boolean drawStretched(RenderLevelStageEvent event, Vec3 aboard, Vec3 holding, Vec3 way, double length) {
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f modelView = new Matrix4f(event.getModelViewMatrix()).mul(stretching(holding.subtract(camera), way, (float) length));
        float white = (float) ((length - 1) / (LONGEST - 1));
        RenderSystem.setShaderColor(Mth.lerp(white, 1, WHITEST[0]), Mth.lerp(white, 1, WHITEST[1]), Mth.lerp(white, 1, WHITEST[2]), 1);
        boolean drawn = false;
        for (RenderType layer : LAYERS) {
            drawn |= drawLayer(layer, aboard, modelView, event.getProjectionMatrix(), camera);
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        return drawn;
    }

    /** One layer of her blocks drawn through a model-view matrix, nudged toward the camera; whether it was drawn. */
    private static boolean drawLayer(RenderType layer, Vec3 aboard, Matrix4f modelView, Matrix4f projection, Vec3 camera) {
        layer.setupRenderState();
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(NUDGE_FACTOR, NUDGE_UNITS);
        ShaderInstance shader = RenderSystem.getShader();
        boolean drawn = false;
        if (shader != null) {
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, projection, Minecraft.getInstance().getWindow());
            shader.apply();
            drawn = SableClient.drawBlocks(aboard, layer, shader, modelView, camera);
            shader.clear();
        }
        RenderSystem.polygonOffset(0, 0);
        RenderSystem.disablePolygonOffset();
        VertexBuffer.unbind();
        layer.clearRenderState();
        return drawn;
    }

    /**
     * The world round the camera stretched so many times along a way (a unit long), the plane square to it through a
     * point, from the camera, holding still.
     */
    private static Matrix4f stretching(Vec3 holding, Vec3 way, float times) {
        float more = times - 1;
        float x = (float) way.x;
        float y = (float) way.y;
        float z = (float) way.z;
        Matrix4f along = new Matrix4f(
            1 + more * x * x, more * x * y, more * x * z, 0,
            more * y * x, 1 + more * y * y, more * y * z, 0,
            more * z * x, more * z * y, 1 + more * z * z, 0,
            0, 0, 0, 1);
        return new Matrix4f().translation((float) holding.x, (float) holding.y, (float) holding.z).mul(along)
            .translate((float) -holding.x, (float) -holding.y, (float) -holding.z);
    }

    /** What was drawn this frame, kept for a play-test: so many times her length, going or coming out. */
    private static void drawnAt(boolean going, double length) {
        if (going) {
            longestGoing = Math.max(longestGoing, length);
            goingFrames++;
        } else {
            longestComing = Math.max(longestComing, length);
            comingFrames++;
        }
    }
}
