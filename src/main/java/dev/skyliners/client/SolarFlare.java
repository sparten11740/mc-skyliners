package dev.skyliners.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.skyliners.spaceweather.SpaceWeather;
import dev.skyliners.spaceweather.SpaceWeatherShown;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * A solar flare on the sun itself, as the player sees it from space while one blows round the ship they ride (see
 * {@link SpaceWeather}): while it is told, the sun stirs, swelling a little and flickering, loops of glowing gas rising
 * off its edge; then it erupts, swelling to several times its size and burning white-gold, a ragged corona of rays
 * bursting out round it and flickering, tendrils of fire arching off it, and a shimmering ring of light racing out
 * across the sky from it, again and again as it pulses, each passing over her with a flash over the view and her
 * screens flickering (see {@link SpaceWeatherHud}); the black of space round it takes on its amber glow; and as the
 * flare dies down, so does all of it.
 *
 * <p>Drawn by {@link SpaceSky} with the sun, in the frame the sun is turned in with the time of day, added to what is
 * behind it as the sun's glow is; a few hundred corners a frame, and nothing at all while no flare blows. Everything
 * moves by the drawing clock, the flare's moments by the moment the server told it.
 */
final class SolarFlare {
    /**
     * How the flare goes, in ticks: how long its eruption takes to reach its height once it is on her, and how long it
     * takes to die down at the end; how strong it stirs at the most while it is told, as a share of its height; and
     * how far it swings between pulses at its height, as a share, and how long a pulse takes, in ticks.
     */
    private static final double ERUPTS_OVER = 20;
    private static final double DIES_OVER = 80;
    private static final double STIRRING = 0.3;
    private static final double PULSE = 0.25;
    private static final double PULSE_TICKS = 37;
    /**
     * How much bigger the sun swells at the flare's height, as a share of its size, and its glow, as a share of its
     * reach; and how opaque its glow is then at its middle.
     */
    static final float SWELLS = 1.6F;
    static final float GLOW_SWELLS = 3.5F;
    static final float GLOW_OPACITY = 0.85F;
    /** The sun's colour at the flare's height, white-gold, which it shades into from white. */
    static final int FLARING = 0xFFE7A0;
    /**
     * Its corona: how many rays; how far out they reach at most past the sun's edge, as shares of its size; how wide
     * each is at its root, in blocks at the sky's distance; and how fast they flicker, in turns a tick at most.
     */
    private static final int RAYS = 28;
    private static final float RAY_REACH = 5.5F;
    private static final float RAY_WIDTH = 2.6F;
    private static final double RAY_FLICKER = 0.09;
    /** The corona's colour at its root, gold, and at its tips, deep orange faded to nothing. */
    private static final int RAY_ROOT = 0xFFD870;
    private static final int RAY_TIP = 0xFF6A10;
    /**
     * Its tendrils: how many loops of fire arch off its edge; how far round its edge each spans, in radians; how high
     * they rise at the most, as shares of the sun's size; how wide they are, in blocks at the sky's distance; how many
     * pieces each is drawn in; and their colour.
     */
    private static final int TENDRILS = 6;
    private static final double TENDRIL_SPAN = 0.7;
    private static final float TENDRIL_RISE = 2.4F;
    private static final float TENDRIL_WIDTH = 1.4F;
    private static final int TENDRIL_PIECES = 14;
    private static final int TENDRIL = 0xFF8A2A;
    /**
     * Its rings of light racing out across the sky: how long one takes to cross from the sun to the far side of the sky,
     * in ticks; how often another follows while it is at its height, in ticks; how wide each is, in radians; how many
     * pieces round it is drawn in; how many ripples run round it as it shimmers; and its colour.
     */
    private static final double RING_TICKS = 60;
    private static final double RING_EVERY = 110;
    private static final double RING_WIDTH = 0.16;
    private static final int RING_PIECES = 72;
    private static final int RIPPLES = 9;
    private static final int RING = 0xFFF2C0;
    /** How much of its amber glow the black of space round the sun takes on at the flare's height, and its colour. */
    private static final float SKY_TINT = 0.35F;
    private static final int SKY_GLOW = 0xFF9030;
    /** How many pieces round the sun's swollen glow is drawn in. */
    private static final int GLOW_PIECES = 32;

    private SolarFlare() {
    }

    /**
     * How strong the flare is now, at a partial tick, from none to 1 at its height: stirring while it is told, erupting
     * as it comes on her, pulsing while on her, dying down at the end; none while no flare blows.
     */
    static float strength(float partial) {
        SpaceWeatherShown shown = SpaceWeatherSky.shown();
        if (shown == null || shown.kind() != SpaceWeather.Kind.SOLAR_FLARE || SpaceWeatherSky.sinceOver() >= 0) {
            return 0;
        }
        double since = DrawingClock.gameTime(partial) - shown.startedAt();
        double told = shown.warningTicks();
        double end = told + shown.stormTicks();
        if (since < 0 || since > end) {
            return 0;
        }
        if (since < told) {
            double stir = since / told;
            return (float) (STIRRING * stir * stir * (0.8 + 0.2 * Math.sin(since * 0.4)));
        }
        double erupting = Math.min(1, (since - told) / ERUPTS_OVER);
        double dying = Math.min(1, (end - since) / DIES_OVER);
        double pulse = 1 - PULSE * (0.5 + 0.5 * Math.sin((since - told) / PULSE_TICKS * Math.PI * 2));
        return (float) (erupting * dying * pulse);
    }

    /**
     * How far the latest ring of light has raced out from the sun now, in radians round the sky, and how bright it is,
     * from 0 to 1; none while none races.
     */
    static double[] ring(float partial) {
        SpaceWeatherShown shown = SpaceWeatherSky.shown();
        if (shown == null || shown.kind() != SpaceWeather.Kind.SOLAR_FLARE || SpaceWeatherSky.sinceOver() >= 0) {
            return new double[] {0, 0};
        }
        double on = DrawingClock.gameTime(partial) - shown.startedAt() - shown.warningTicks();
        if (on < 0 || on > shown.stormTicks()) {
            return new double[] {0, 0};
        }
        double into = on % RING_EVERY;
        double across = into / RING_TICKS;
        return across >= 1 ? new double[] {0, 0} : new double[] {across * Math.PI, (1 - across) * (on < RING_EVERY ? 1 : 0.7)};
    }

    /**
     * Whether a ring of light is passing over her now, at a partial tick: from a third to two thirds of its way across
     * the sky, when it flashes over the view and her screens flicker; how strongly, from 0 to 1.
     */
    static float passing(float partial) {
        double[] ring = ring(partial);
        double across = ring[0] / Math.PI;
        return across > 0.3 && across < 0.7 ? (float) (ring[1] * Math.sin((across - 0.3) / 0.4 * Math.PI)) : 0;
    }

    /** The sky's colour round the sun, from 0 to 1 for each of red, green and blue, taking on the flare's amber glow. */
    static Vec3 tinted(Vec3 sky, float partial) {
        float flare = strength(partial);
        return flare <= 0 ? sky : sky.lerp(Vec3.fromRGB24(SKY_GLOW), flare * SKY_TINT);
    }

    /**
     * The flare drawn round the sun, so big and its glow reaching so far, where the game puts it, so many blocks off,
     * as opaque as the sky round it lets it be: its swollen glow, its corona, its tendrils and its rings of light, added
     * to what is behind them.
     */
    static void draw(Matrix4f pose, float sunSize, float glowReach, float distance, float opacity, float partial) {
        float flare = strength(partial);
        double[] ring = ring(partial);
        if (flare <= 0 && ring[1] <= 0) {
            return;
        }
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, opacity);
        double time = DrawingClock.ticks(partial);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        glow(builder, pose, glowReach * (1 + GLOW_SWELLS * flare), distance, flare);
        corona(builder, pose, sunSize * (1 + SWELLS * flare), distance, flare, time);
        tendrils(builder, pose, sunSize * (1 + SWELLS * flare), distance, flare, time);
        rings(builder, pose, distance, ring, time);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    /** The sun's glow swollen round it, so far across, white-gold, fading out. */
    private static void glow(BufferBuilder builder, Matrix4f pose, float reach, float distance, float flare) {
        int middle = FastColor.ARGB32.color(Math.round(GLOW_OPACITY * flare * 255), FLARING);
        int faded = FastColor.ARGB32.color(0, RAY_TIP);
        for (int piece = 0; piece < GLOW_PIECES; piece++) {
            double from = Math.PI * 2 * piece / GLOW_PIECES;
            double to = Math.PI * 2 * (piece + 1) / GLOW_PIECES;
            builder.addVertex(pose, 0, distance, 0).setColor(middle);
            builder.addVertex(pose, (float) Math.cos(from) * reach, distance, (float) Math.sin(from) * reach).setColor(faded);
            builder.addVertex(pose, (float) Math.cos(to) * reach, distance, (float) Math.sin(to) * reach).setColor(faded);
        }
    }

    /** Its corona: rays bursting out round the sun's edge, each its own length, flickering, gold fading to orange. */
    private static void corona(BufferBuilder builder, Matrix4f pose, float size, float distance, float flare, double time) {
        int root = FastColor.ARGB32.color(Math.round(0.9F * flare * 255), RAY_ROOT);
        int tip = FastColor.ARGB32.color(0, RAY_TIP);
        for (int ray = 0; ray < RAYS; ray++) {
            double round = Math.PI * 2 * (ray + 0.37 * Math.sin(ray * 12.9898)) / RAYS;
            double flicker = 0.55 + 0.45 * Math.sin(time * RAY_FLICKER * (1 + (ray * 7 % 5) * 0.3) + ray * 2.3);
            float reach = size * (1 + RAY_REACH * flare * (float) flicker * (ray % 3 == 0 ? 1 : 0.55F));
            float across = RAY_WIDTH * (0.6F + flare);
            float cos = (float) Math.cos(round);
            float sin = (float) Math.sin(round);
            builder.addVertex(pose, cos * size * 0.8F - sin * across, distance, sin * size * 0.8F + cos * across).setColor(root);
            builder.addVertex(pose, cos * size * 0.8F + sin * across, distance, sin * size * 0.8F - cos * across).setColor(root);
            builder.addVertex(pose, cos * reach, distance, sin * reach).setColor(tip);
        }
    }

    /** Its tendrils: loops of fire arching off the sun's edge, rising and falling, each a ribbon of triangles. */
    private static void tendrils(BufferBuilder builder, Matrix4f pose, float size, float distance, float flare, double time) {
        int colour = FastColor.ARGB32.color(Math.round(0.85F * Math.min(1, flare * 1.5F) * 255), TENDRIL);
        int faded = FastColor.ARGB32.color(0, TENDRIL);
        for (int tendril = 0; tendril < TENDRILS; tendril++) {
            double start = Math.PI * 2 * tendril / TENDRILS + Math.sin(tendril * 4.1) * 0.5;
            float rise = size * TENDRIL_RISE * flare * (float) (0.6 + 0.4 * Math.sin(time * 0.05 + tendril * 1.7));
            for (int piece = 0; piece < TENDRIL_PIECES; piece++) {
                double a = (double) piece / TENDRIL_PIECES;
                double b = (double) (piece + 1) / TENDRIL_PIECES;
                tendrilPiece(builder, pose, size, distance, start, rise, a, b, colour, faded);
            }
        }
    }

    /** One piece of a tendril, from one share of its way along to the next, brighter along its middle. */
    private static void tendrilPiece(BufferBuilder builder, Matrix4f pose, float size, float distance, double start, float rise, double a, double b,
        int colour, int faded) {
        float[] from = arch(size, start, rise, a);
        float[] to = arch(size, start, rise, b);
        float half = TENDRIL_WIDTH / 2;
        float dx = to[0] - from[0];
        float dz = to[1] - from[1];
        float length = Mth.sqrt(dx * dx + dz * dz);
        if (length < 1e-4F) {
            return;
        }
        float sideX = -dz / length * half;
        float sideZ = dx / length * half;
        builder.addVertex(pose, from[0] + sideX, distance, from[1] + sideZ).setColor(faded);
        builder.addVertex(pose, from[0] - sideX, distance, from[1] - sideZ).setColor(colour);
        builder.addVertex(pose, to[0] + sideX, distance, to[1] + sideZ).setColor(faded);
        builder.addVertex(pose, to[0] + sideX, distance, to[1] + sideZ).setColor(faded);
        builder.addVertex(pose, from[0] - sideX, distance, from[1] - sideZ).setColor(colour);
        builder.addVertex(pose, to[0] - sideX, distance, to[1] - sideZ).setColor(colour);
    }

    /** A point along a tendril's arch, so far along it, as x and z round the sun. */
    private static float[] arch(float size, double start, float rise, double along) {
        double round = start + TENDRIL_SPAN * along;
        float out = size * 0.95F + rise * (float) Math.sin(Math.PI * along);
        return new float[] {(float) Math.cos(round) * out, (float) Math.sin(round) * out};
    }

    /**
     * Its latest ring of light, so far out from the sun round the sky, in radians, and so bright: a shimmering band on
     * the sky's sphere round the way to the sun, its ripples running round it.
     */
    private static void rings(BufferBuilder builder, Matrix4f pose, float distance, double[] ring, double time) {
        if (ring[1] <= 0) {
            return;
        }
        double inner = Math.max(0, ring[0] - RING_WIDTH);
        double outer = Math.min(Math.PI, ring[0] + RING_WIDTH);
        for (int piece = 0; piece < RING_PIECES; piece++) {
            double from = Math.PI * 2 * piece / RING_PIECES;
            double to = Math.PI * 2 * (piece + 1) / RING_PIECES;
            float shimmer = (float) (0.65 + 0.35 * Math.sin(from * RIPPLES + time * 0.6));
            int lit = FastColor.ARGB32.color(Math.round((float) ring[1] * shimmer * 0.8F * 255), RING);
            int faded = FastColor.ARGB32.color(0, RING);
            band(builder, pose, distance, inner, ring[0], from, to, faded, lit);
            band(builder, pose, distance, ring[0], outer, from, to, lit, faded);
        }
    }

    /**
     * A piece of a band on the sky's sphere round the way to the sun, from one angle out from it to another and from one
     * angle round to another, each edge in its own colour.
     */
    private static void band(BufferBuilder builder, Matrix4f pose, float distance, double near, double far, double from, double to, int nearColour,
        int farColour) {
        float[] a = onSky(distance, near, from);
        float[] b = onSky(distance, near, to);
        float[] c = onSky(distance, far, to);
        float[] d = onSky(distance, far, from);
        builder.addVertex(pose, a[0], a[1], a[2]).setColor(nearColour);
        builder.addVertex(pose, b[0], b[1], b[2]).setColor(nearColour);
        builder.addVertex(pose, c[0], c[1], c[2]).setColor(farColour);
        builder.addVertex(pose, a[0], a[1], a[2]).setColor(nearColour);
        builder.addVertex(pose, c[0], c[1], c[2]).setColor(farColour);
        builder.addVertex(pose, d[0], d[1], d[2]).setColor(farColour);
    }

    /** A point on the sky's sphere so far out from the way to the sun, and so far round it, in radians. */
    private static float[] onSky(float distance, double out, double round) {
        double across = Math.sin(out) * distance;
        return new float[] {(float) (Math.cos(round) * across), (float) (Math.cos(out) * distance), (float) (Math.sin(round) * across)};
    }
}
