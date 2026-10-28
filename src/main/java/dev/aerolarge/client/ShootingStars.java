package dev.aerolarge.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.aerolarge.AerolargeClientConfig;
import dev.aerolarge.autopilot.EdgeOfSpace;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Shooting stars in space's sky (see {@link SpaceSky}), from y 500 up: one every four to ten seconds, a white head with
 * a tail fading to pale blue, streaking a few degrees and gone within a second, and one in ten a bigger green fireball.
 * Under the edge of space ({@link EdgeOfSpace#HEIGHT}) they cross the sky overhead; over it they burn up below the
 * camera, streaking over the planet just under its rim. They are as bright as the stars there, so they come out with
 * them and never show in a blue day sky, and they make no sound, as nothing does in space.
 *
 * <p>Each one is worked out from the game's time alone, a slot of {@link #SLOT} ticks at a time, so everyone aboard
 * sees the same shooting star at the same moment in the same part of the sky. It is drawn last of space, over the
 * planet and the glow of its air, and added to what is behind it: one draw a frame while a shooting star lasts and none
 * otherwise, its directions worked out in vectors kept for it, and nothing at all with the {@code shootingStars}
 * setting off.
 */
public final class ShootingStars {
    /**
     * How high the camera must be for shooting stars to show, and how high they are as bright as the stars, in world
     * blocks.
     */
    private static final double FROM = 500;
    private static final double FULL = 600;
    /**
     * How long each shooting star's slot of time is, in ticks, and how late in its slot it starts at the latest, in
     * ticks: one starts in every slot, so the next follows four to ten seconds after it.
     */
    private static final int SLOT = 140;
    private static final int LATEST_START = 60;
    /** How long a shooting star lasts, at least and at most, in ticks. */
    private static final float SHORTEST = 12;
    private static final float LONGEST = 20;
    /** How long its tail is, at least and at most, in degrees across the sky. */
    private static final float SHORTEST_TAIL = 6;
    private static final float LONGEST_TAIL = 12;
    /** How far its head travels while it lasts, as a share of its tail's length. */
    private static final float PATH = 1.5F;
    /**
     * How much of its life it takes to flare up, and how much to fade away, as shares: it flares at once and fades more
     * slowly, as a meteor burns out.
     */
    private static final float FLARE = 0.15F;
    private static final float FADE = 0.5F;
    /** How high over the horizon one crossing the sky starts, at least and at most, in degrees. */
    private static final float SKY_LOWEST = 25;
    private static final float SKY_HIGHEST = 70;
    /**
     * How near level one crossing the sky falls at the shallowest, in degrees: it falls at any slant from there round
     * to straight down and on to as near level the other way.
     */
    private static final float SKY_SHALLOWEST = 10;
    /** How far under the planet's rim one seen from space starts, at least and at most, in degrees. */
    private static final float RIM_NEAREST = 1;
    private static final float RIM_FURTHEST = 5;
    /**
     * How steeply one seen from space falls, at least and at most, in degrees: it streaks along the rim, falling a
     * little, and at least enough that the curve of its path never carries it up over the rim.
     */
    private static final float RIM_SHALLOWEST = 5;
    private static final float RIM_STEEPEST = 25;
    /**
     * The chance that a shooting star is a fireball, as a share, and how many times wider its head and its glow are,
     * and how many times longer its tail.
     */
    private static final float FIREBALL_CHANCE = 0.1F;
    private static final float FIREBALL_WIDER = 2.2F;
    private static final float FIREBALL_LONGER = 1.4F;
    /** The colours of a shooting star's head and of the end of its tail, and of a fireball's. */
    private static final int HEAD = 0xFFFFFF;
    private static final int TAIL = 0xA8C8FF;
    private static final int FIREBALL_HEAD = 0xE8FFE4;
    private static final int FIREBALL_TAIL = 0x52FF7C;
    /**
     * How far off a shooting star is drawn, as far as the stars and well short of the nearest the game's far plane
     * comes, the half width of its tail at its head and the radius of the glow round its head, in blocks at that
     * distance, and the glow's opacity at its middle, fading to nothing at its edge.
     */
    private static final float DISTANCE = 100;
    private static final float WIDTH = 0.18F;
    private static final float GLOW = 0.7F;
    private static final float GLOW_OPACITY = 0.6F;
    /** How many pieces its tail, and the glow round its head, are drawn in. */
    private static final int TAIL_PIECES = 8;
    private static final int GLOW_PIECES = 10;
    /** What the slots' hashes start from, so that shooting stars fall differently from anything else hashed by time. */
    private static final long SEED = 0x5407F1A3L;
    /** SplitMix64's step and its two mixing constants, by which each slot's numbers are hashed from it. */
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;
    private static final long MIX_ONE = 0xBF58476D1CE4E5B9L;
    private static final long MIX_TWO = 0x94D049BB133111EBL;

    /**
     * Where the shooting star of the moment starts, the way it heads from there and the side of its path, as directions
     * from the camera; where its head is now and the way on from there: kept for it, so drawing it makes nothing new.
     */
    private static final Vector3f START = new Vector3f();
    private static final Vector3f HEADING = new Vector3f();
    private static final Vector3f SIDE = new Vector3f();
    private static final Vector3f AT = new Vector3f();
    private static final Vector3f ONWARD = new Vector3f();

    /** What each of a shooting star's numbers is picked for, each hashed from its slot in turn. */
    private enum Pick {
        START, LIFE, TAIL, FIREBALL, ROUND, HIGH, SLANT, SIDE
    }

    private ShootingStars() {
    }

    /**
     * The shooting star of the moment, if one is streaking: across the sky, or from over the edge of space under the
     * planet's rim, so many degrees under the horizon; as bright as the stars, a share of their brightest from 0 to 1.
     */
    static void draw(Matrix4f pose, ClientLevel level, float partialTick, double height, double dip, float starlight) {
        float light = starlight * SpaceSky.ramp(height, FROM, FULL);
        if (light <= 0 || !AerolargeClientConfig.SHOOTING_STARS.get()) {
            return;
        }
        long time = level.getGameTime();
        long slot = Math.floorDiv(time, SLOT);
        float lasts = Mth.lerp(share(slot, Pick.LIFE), SHORTEST, LONGEST);
        float age = (time - slot * SLOT + partialTick - share(slot, Pick.START) * LATEST_START) / lasts;
        if (age <= 0 || age >= 1) {
            return;
        }
        if (height < EdgeOfSpace.HEIGHT) {
            acrossSky(slot);
        } else {
            underRim(slot, dip);
        }
        boolean fireball = share(slot, Pick.FIREBALL) < FIREBALL_CHANCE;
        float wider = fireball ? FIREBALL_WIDER : 1;
        float tail = Mth.lerp(share(slot, Pick.TAIL), SHORTEST_TAIL, LONGEST_TAIL) * (fireball ? FIREBALL_LONGER : 1) * Mth.DEG_TO_RAD;
        float head = age * tail * PATH;
        int headColour = FastColor.ARGB32.opaque(fireball ? FIREBALL_HEAD : HEAD);
        int tailColour = FastColor.ARGB32.color(0, fireball ? FIREBALL_TAIL : TAIL);
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, light * Math.min(1, age / FLARE) * Math.min(1, (1 - age) / FADE));
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        drawTail(builder, pose, Math.max(0, head - tail), head, WIDTH * wider, headColour, tailColour);
        drawHead(builder, pose, head, GLOW * wider, headColour);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    /** Aims a shooting star crossing the sky: starting high over the horizon, falling at a slant. */
    private static void acrossSky(long slot) {
        float high = Mth.lerp(share(slot, Pick.HIGH), SKY_LOWEST, SKY_HIGHEST);
        float slant = Mth.lerp(share(slot, Pick.SLANT), SKY_SHALLOWEST, 180 - SKY_SHALLOWEST);
        aim(share(slot, Pick.ROUND) * Mth.TWO_PI, high * Mth.DEG_TO_RAD, -slant * Mth.DEG_TO_RAD);
    }

    /**
     * Aims a shooting star seen from space: starting just under the planet's rim, so many degrees under the horizon,
     * and streaking along it either way, falling a little.
     */
    private static void underRim(long slot, double dip) {
        float under = (float) dip + Mth.lerp(share(slot, Pick.HIGH), RIM_NEAREST, RIM_FURTHEST);
        float slant = Mth.lerp(share(slot, Pick.SLANT), RIM_SHALLOWEST, RIM_STEEPEST);
        float heading = share(slot, Pick.SIDE) < 0.5F ? slant : 180 - slant;
        aim(share(slot, Pick.ROUND) * Mth.TWO_PI, -under * Mth.DEG_TO_RAD, -heading * Mth.DEG_TO_RAD);
    }

    /**
     * Sets where a shooting star starts, so far round the horizon and so high over it, and the way it heads from there,
     * so far round from level to the right, a negative angle falling, all in radians; and the side of its path.
     */
    private static void aim(float round, float high, float heading) {
        float cosRound = Mth.cos(round);
        float sinRound = Mth.sin(round);
        float cosHigh = Mth.cos(high);
        float sinHigh = Mth.sin(high);
        float rising = Mth.sin(heading);
        START.set(cosHigh * cosRound, sinHigh, cosHigh * sinRound);
        HEADING.set(-sinRound, 0, cosRound).mul(Mth.cos(heading)).add(-sinHigh * cosRound * rising, cosHigh * rising, -sinHigh * sinRound * rising);
        SIDE.set(START).cross(HEADING);
    }

    /**
     * The tail, from so far along its path behind the head to the head, in radians: tapering from the half width given
     * at the head to nothing, and fading from the head's colour into the tail's.
     */
    private static void drawTail(BufferBuilder builder, Matrix4f pose, float back, float head, float width, int headColour, int tailColour) {
        for (int piece = 0; piece < TAIL_PIECES; piece++) {
            float behind = (float) piece / TAIL_PIECES;
            float ahead = (float) (piece + 1) / TAIL_PIECES;
            float from = Mth.lerp(behind, back, head);
            float to = Mth.lerp(ahead, back, head);
            int fromColour = FastColor.ARGB32.lerp(behind, tailColour, headColour);
            int toColour = FastColor.ARGB32.lerp(ahead, tailColour, headColour);
            tailVertex(builder, pose, from, -width * behind, fromColour);
            tailVertex(builder, pose, to, -width * ahead, toColour);
            tailVertex(builder, pose, to, width * ahead, toColour);
            tailVertex(builder, pose, from, -width * behind, fromColour);
            tailVertex(builder, pose, to, width * ahead, toColour);
            tailVertex(builder, pose, from, width * behind, fromColour);
        }
    }

    /** A corner of the tail, so far along the path, in radians, and so far to its side, in blocks at its distance. */
    private static void tailVertex(BufferBuilder builder, Matrix4f pose, float along, float aside, int colour) {
        float cos = Mth.cos(along);
        float sin = Mth.sin(along);
        float x = (START.x * cos + HEADING.x * sin) * DISTANCE + SIDE.x * aside;
        float y = (START.y * cos + HEADING.y * sin) * DISTANCE + SIDE.y * aside;
        float z = (START.z * cos + HEADING.z * sin) * DISTANCE + SIDE.z * aside;
        builder.addVertex(pose, x, y, z).setColor(colour);
    }

    /**
     * The round glow on the head, so far along the path, in radians: its radius given, in the head's colour, fading
     * from its middle to nothing at its edge.
     */
    private static void drawHead(BufferBuilder builder, Matrix4f pose, float head, float radius, int colour) {
        float cos = Mth.cos(head);
        float sin = Mth.sin(head);
        AT.set(HEADING).mul(sin).add(START.x * cos, START.y * cos, START.z * cos).mul(DISTANCE);
        ONWARD.set(HEADING).mul(cos).sub(START.x * sin, START.y * sin, START.z * sin);
        int middle = FastColor.ARGB32.color(Math.round(GLOW_OPACITY * 255), colour);
        int edge = FastColor.ARGB32.color(0, colour);
        for (int piece = 0; piece < GLOW_PIECES; piece++) {
            builder.addVertex(pose, AT.x, AT.y, AT.z).setColor(middle);
            glowVertex(builder, pose, Mth.TWO_PI * piece / GLOW_PIECES, radius, edge);
            glowVertex(builder, pose, Mth.TWO_PI * (piece + 1) / GLOW_PIECES, radius, edge);
        }
    }

    /** A point on the edge of the head's glow, so far round it, in radians, from the way on. */
    private static void glowVertex(BufferBuilder builder, Matrix4f pose, float round, float radius, int colour) {
        float on = Mth.cos(round) * radius;
        float aside = Mth.sin(round) * radius;
        builder.addVertex(pose, AT.x + ONWARD.x * on + SIDE.x * aside, AT.y + ONWARD.y * on + SIDE.y * aside, AT.z + ONWARD.z * on + SIDE.z * aside)
            .setColor(colour);
    }

    /**
     * One of a slot's numbers, as a share from 0 to 1: a hash of the slot and what it is picked for, the same on every
     * client that knows the game's time.
     */
    private static float share(long slot, Pick pick) {
        return (mix(mix(slot + SEED) + pick.ordinal()) >>> 40) * 0x1.0p-24F;
    }

    /** SplitMix64's step: a number mixed into one that looks nothing like it, the same every time. */
    private static long mix(long value) {
        long mixed = value + GOLDEN;
        mixed = (mixed ^ (mixed >>> 30)) * MIX_ONE;
        mixed = (mixed ^ (mixed >>> 27)) * MIX_TWO;
        return mixed ^ (mixed >>> 31);
    }
}
