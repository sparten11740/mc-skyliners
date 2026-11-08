package dev.skyliners.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.skyliners.Skyliners;
import dev.skyliners.autopilot.FreighterClimbBoost;
import dev.skyliners.autopilot.FreighterFlight;
import dev.skyliners.autopilot.FreighterSlam;
import dev.skyliners.console.ConsoleBlockEntity;
import dev.skyliners.console.ConsoleKind;
import dev.skyliners.spaceweather.SpaceWeather;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterRenderBuffersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * What a freighter console module shows and the controls standing on it (see {@link ConsoleRenderer}), each drawn on
 * its face in that face's own frame (see {@link ConsoleCasing.Surface}): x across from its middle, y up from its bottom
 * edge and z out of it, in blocks. The screens glow green and amber on dark glass, every reading with its own picture,
 * a bar or a needle beside its figure, so none needs reading: her speed, height, climb and heading; a scope whose beam
 * sweeps round over her pin and whoever is near; gauges of her lift, her mains and her boost; a grid of lit squares;
 * the boost's flame chevrons, green when it is ready, pulsing orange as it fires and ringed red while it rests; and the
 * hyperspace jump's starburst, dark with no pin to jump to, pulsing cyan while she can, and streaming streaks inside a
 * filling ring as her drive spools up, its big blue button lit the same; and the climb boost's arrow up over flames,
 * dark while she cannot climb so, pulsing orange while she can, flashing as dashes race up past it as
 * it fires and ringed red while it rests, its big orange button lit the same; the slam landing's arrow down onto the
 * ground, dark while she cannot slam down, pulsing red while she can and flashing as dashes race down past it as she
 * drives down, its big red button lit the same; and while something holds her take-off,
 * the scanner shows it in place of its scope, her ramp with someone on it flashing amber; and while one of her fault
 * spots is at fault, a red warning sign flashes on the scope at its bearing and another in the scope's corner, so a
 * fault can be tracked down from her cockpit; while space weather is told or blows round her, the scanner flashes its
 * picture in place of its scope, and a solar flare flickers every screen dark now and then. Along each desk stand chunky
 * controls of its kind and a row of small lamps; with no ship to read, the screens show the power sign pulsing slowly
 * and only one lamp blinks.
 */
final class ConsoleArt {
    /**
     * The screens' shapes, as vanilla's text backgrounds, and the controls, as vanilla's cut-out quads on its white
     * texture, every module's drawn together (see {@link BatchedBuffers}). The shapes are still sorted as vanilla's
     * are, so each lies over the others as it did.
     */
    private static final RenderType SHAPES = RenderType.create(Skyliners.MOD_ID + "_console_shapes", DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
        VertexFormat.Mode.QUADS, RenderType.TRANSIENT_BUFFER_SIZE, false, true, RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_TEXT_BACKGROUND_SHADER)
            .setTextureState(RenderStateShard.NO_TEXTURE)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .createCompositeState(false));
    private static final RenderType CONTROLS = RenderType.create(Skyliners.MOD_ID + "_console_controls", DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.QUADS, RenderType.TRANSIENT_BUFFER_SIZE, true, false, RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
            .setTextureState(new RenderStateShard.TextureStateShard(FlatQuads.WHITE, false, false))
            .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
            .setCullState(RenderStateShard.NO_CULL)
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setOverlayState(RenderStateShard.OVERLAY)
            .createCompositeState(true));
    /** Each layer of a face drawn just off the one under it, in blocks. */
    private static final float LAYER = 0.002F;
    /**
     * How tall a reading's figure is, as a share of its square of the screen, and how tall the font's letters are, in
     * the font's pixels.
     */
    private static final double FIGURE_SHARE = 0.3;
    private static final double FONT_HEIGHT = 8;
    private static final int GLASS = 0xFF050D0A;
    private static final int GLASS_EDGE = 0xFF0E2219;
    private static final int GREEN = 0xFF4CF08A;
    private static final int GREEN_DIM = 0xFF17402A;
    private static final int AMBER = 0xFFFFB238;
    private static final int RED = 0xFFFF4A3A;
    private static final int CYAN = 0xFF56D8FF;
    private static final int ORANGE = 0xFFFF8A1E;
    private static final int UNLIT = 0xFF20262B;
    private static final int[] LAMP_COLOURS = {GREEN, AMBER, CYAN, RED, GREEN, AMBER, CYAN};
    /** The row of lamps along each desk: how many, how far in from its far edge, and each one's radius, in blocks. */
    private static final int LAMPS = 7;
    private static final double LAMP_IN = 0.05;
    private static final double LAMP = 0.018;
    /** How often a lamp blinks, in ticks lit and as many dark, the standby lamp slower. */
    private static final int BLINK = 9;
    private static final int STANDBY_BLINK = 30;
    /** How long what holds her take-off flashes bright on the scanner's screen, and as long dim, in ticks. */
    private static final int HELD_FLASH = 10;
    /** How fast the power sign pulses on standby, in ticks a pulse. */
    private static final int STANDBY_PULSE = 60;
    /** The scope's beam: how far it turns each tick, in degrees, and how far round its fading trail reaches. */
    private static final float SWEEP_PER_TICK = 6;
    private static final int TRAIL = 24;
    private static final float TRAIL_STEP = 3;
    /** How far off the scope sees, in blocks: those near at its rim, the pin at its rim from as far as this or more. */
    static final double SCOPE_NEAR = 48;
    /** How long a fault's warning sign on the scope stays lit, and as long dark, flashing, in ticks. */
    private static final float FAULT_FLASH = 6;
    private static final double SCOPE_FAR = 2000;
    /** How long the scope's ping ring takes to reach the rim, and a lamp test lasts, in ticks. */
    private static final int PING_TICKS = 20;
    /** How long a big button stays pressed in after a press, in ticks. */
    private static final int PRESSED_TICKS = 6;
    /** The big buttons' push, how far in it goes pressed, in blocks. */
    private static final float PUSH = 0.03F;
    /** The bars' full readings: speed and climb in blocks a second, height in blocks over the sea. */
    private static final double SPEED_FULL = 40;
    private static final double CLIMB_FULL = 6;
    private static final double HEIGHT_FULL = 320;
    private static final double SEA = 62;
    /** Toggles: their bat's length and thickness, in blocks, and how far it leans up or down, in degrees. */
    private static final float BAT_LENGTH = 0.09F;
    private static final float BAT_HALF = 0.012F;
    private static final float BAT_LEAN = 32;
    private static final int CHROME = 0xFFB4BCC4;
    private static final int PLATE = 0xFF1B1E22;
    private static final int KNOB = 0xFF26292D;
    private static final int KNOB_MARK = 0xFFE8ECEF;
    private static final int HAZARD_YELLOW = 0xFFF2C01A;
    private static final int HAZARD_BLACK = 0xFF1C1D20;
    private static final int BUTTON_SIDES = 20;
    private static final int JUMP_WHITE = 0xFFEAF6FF;
    private static final int JUMP_BLUE = 0xFF1C4CA8;
    private static final int JUMP_DARK = 0xFF233040;
    /** The streaks out of the jump's starburst as her drive spools up: how many, and how far each runs a tick, a share. */
    private static final int JUMP_STREAKS = 16;
    private static final float JUMP_STREAK_SPEED = 0.06F;
    private static final int CLIMB_DARK = 0xFF3A2C20;
    /**
     * The dashes racing up past the climb boost's arrow as it fires, and down past the slam landing's as she drives
     * down: how many, and how far each runs a tick, a share.
     */
    private static final int CLIMB_DASHES = 6;
    private static final float CLIMB_DASH_SPEED = 0.08F;
    /** The slam landing's dull red while she cannot slam down. */
    private static final int SLAM_DARK = 0xFF3A2222;

    private ConsoleArt() {
    }

    /**
     * The readings a console draws from, read once a frame: whether there is a ship to read, her speed ahead, height,
     * climb and heading as her instruments show them, her lift's and her mains' shares of full and where her boost
     * stands, her hyperspace jump, her climb boost and her slam landing, and whether she is a freighter at all; the
     * drawing clock's ticks for what only moves for show; how long ago the console was last touched, in ticks,
     * negative for never; which of its toggles are up, a bit each; the blips its scope shows; and what holds her
     * take-off, by its picture's word, if anything does.
     */
    record Readings(boolean powered, double speed, double height, double climb, double heading, double lift, double mains,
                    FreighterFlight.BoostShown boost, FreighterFlight.JumpShown jump, FreighterClimbBoost.Shown climbBoost,
                    FreighterSlam.Stage slam, boolean freighter, float ticks,
                    float sinceTouched, int toggles,
                    List<Blip> blips, @Nullable String heldBy) {
    }

    /** One blip on the scope: its bearing off her heading, in degrees, its distance, in blocks, and what it marks. */
    record Blip(double bearing, double distance, Mark mark) {
    }

    /** What a blip on the scope marks: someone near, her pin, or one of her fault spots at fault. */
    enum Mark {
        SOMEONE, PIN, FAULT
    }

    /** A module's screen, on its face. */
    static void screen(ConsoleKind kind, PoseStack pose, MultiBufferSource buffers, double width, double height, Readings readings) {
        Matrix4f matrix = pose.last().pose();
        VertexConsumer shapes = shapes(buffers);
        double half = width / 2;
        ScreenDrawing.rect(shapes, matrix, -half, 0, half, height, LAYER, GLASS_EDGE);
        ScreenDrawing.rect(shapes, matrix, -half + LAYER, LAYER, half - LAYER, height - LAYER, 2 * LAYER, GLASS);
        if (!readings.powered() && kind != ConsoleKind.SCANNER && kind != ConsoleKind.SWITCHES) {
            standby(buffers, matrix, height, readings);
            return;
        }
        if (kind == ConsoleKind.SCANNER && readings.heldBy() != null) {
            held(buffers, matrix, height, readings);
            return;
        }
        if (readings.freighter() && SpaceWeatherHud.screensFlicker()) {
            return;
        }
        SpaceWeather.Kind weather = readings.freighter() ? SpaceWeatherSky.blowing() : null;
        if (kind == ConsoleKind.SCANNER && weather != null) {
            spaceWeather(buffers, matrix, height, readings, weather);
            return;
        }
        switch (kind) {
            case READOUTS -> readouts(pose, buffers, width, height, readings);
            case SCANNER -> scope(buffers, matrix, width, height, readings);
            case ENGINES -> gauges(buffers, matrix, width, height, readings);
            case SWITCHES -> annunciators(buffers, matrix, width, height, readings);
            case BOOST -> boostScreen(buffers, matrix, width, height, readings);
            case JUMP -> jumpScreen(buffers, matrix, width, height, readings);
            case CLIMB -> climbScreen(buffers, matrix, width, height, readings);
            case SLAM -> slamScreen(buffers, matrix, width, height, readings);
        }
    }

    /**
     * Her take-off held on her legs: in place of the scope, what holds her, her ramp with someone standing on it, say,
     * flashing big and amber in the middle, so whoever is to fly her sees why she waits.
     */
    private static void held(MultiBufferSource buffers, Matrix4f matrix, double height, Readings readings) {
        boolean lit = (int) (readings.ticks() / HELD_FLASH) % 2 == 0;
        ScreenDrawing.picture(buffers, matrix, icon("held_" + readings.heldBy()), 0, height / 2, height * 0.4, 3 * LAYER, lit ? AMBER : fade(AMBER, 0.35F));
    }

    /**
     * Space weather told or blowing round her (see {@link SpaceWeatherSky}): in place of the scope, its picture flashing
     * big in its colour in the middle, as on everyone's screen.
     */
    private static void spaceWeather(MultiBufferSource buffers, Matrix4f matrix, double height, Readings readings, SpaceWeather.Kind weather) {
        boolean lit = (int) (readings.ticks() / HELD_FLASH) % 2 == 0;
        int colour = SpaceWeatherHud.colour(weather);
        ScreenDrawing.picture(buffers, matrix, icon(SpaceWeatherHud.picture(weather)), 0, height / 2, height * 0.4, 3 * LAYER, lit ? colour : fade(colour, 0.35F));
    }

    /** With no ship to read: the power sign, pulsing slowly amber in the middle. */
    private static void standby(MultiBufferSource buffers, Matrix4f matrix, double height, Readings readings) {
        float pulse = 0.5F + 0.5F * Mth.sin(Mth.TWO_PI * (readings.ticks() % STANDBY_PULSE) / STANDBY_PULSE);
        ScreenDrawing.picture(buffers, matrix, icon("power"), 0, height / 2, height * 0.3, 3 * LAYER, fade(AMBER, 0.3F + 0.6F * pulse));
    }

    /**
     * Her flight readings in four squares: speed, height, climb and heading, each its picture over its figure and a bar
     * under it, the climb's arrow turned up or down and green or amber, the heading a needle in a ring.
     */
    private static void readouts(PoseStack pose, MultiBufferSource buffers, double width, double height, Readings readings) {
        Matrix4f matrix = pose.last().pose();
        VertexConsumer shapes = shapes(buffers);
        double cellW = width / 2;
        double cellH = height / 2;
        bar(shapes, matrix, -cellW, cellH, cellW, cellH, readings.speed() / SPEED_FULL, GREEN);
        bar(shapes, matrix, 0, cellH, cellW, cellH, (readings.height() - SEA) / HEIGHT_FULL, CYAN);
        double climb = readings.climb() / CLIMB_FULL;
        bar(shapes, matrix, -cellW, 0, cellW, cellH, Math.abs(climb), climb >= 0 ? GREEN : AMBER);
        compass(shapes, matrix, cellW / 2, cellH * 0.55, Math.min(cellW, cellH) * 0.32, readings.heading());
        double icon = cellH * 0.22;
        ScreenDrawing.picture(buffers, matrix, icon("speed"), -cellW * 0.72, cellH * 1.62, icon, 3 * LAYER, GREEN);
        ScreenDrawing.picture(buffers, matrix, icon("height"), cellW * 0.28, cellH * 1.62, icon, 3 * LAYER, CYAN);
        climbArrow(buffers, pose, -cellW * 0.72, cellH * 0.62, icon, climb);
        Font font = Minecraft.getInstance().font;
        float size = (float) (cellH * FIGURE_SHARE / FONT_HEIGHT);
        ScreenDrawing.text(font, pose, buffers, figure(readings.speed()), -cellW * 0.12, cellH * 1.62, 4 * LAYER, size, GREEN, ScreenDrawing.Align.RIGHT);
        ScreenDrawing.text(font, pose, buffers, figure(readings.height()), cellW * 0.88, cellH * 1.62, 4 * LAYER, size, CYAN, ScreenDrawing.Align.RIGHT);
        ScreenDrawing.text(font, pose, buffers, figure(readings.climb()), -cellW * 0.12, cellH * 0.62, 4 * LAYER, size, climb >= 0 ? GREEN : AMBER,
            ScreenDrawing.Align.RIGHT);
    }

    /** A bar along the foot of a square of a screen, from its left corner, so wide, filled a share of the way. */
    private static void bar(VertexConsumer shapes, Matrix4f matrix, double left, double bottom, double cellW, double cellH, double share, int colour) {
        double inset = cellW * 0.12;
        double barBottom = bottom + cellH * 0.14;
        double barTop = bottom + cellH * 0.26;
        double length = cellW - 2 * inset;
        ScreenDrawing.rect(shapes, matrix, left + inset, barBottom, left + inset + length, barTop, 3 * LAYER, GREEN_DIM);
        double filled = length * Mth.clamp(share, 0, 1);
        ScreenDrawing.rect(shapes, matrix, left + inset, barBottom, left + inset + filled, barTop, 4 * LAYER, colour);
    }

    /** The heading: a ring with a mark for north at its top turning against her heading, and a needle straight up. */
    private static void compass(VertexConsumer shapes, Matrix4f matrix, double x, double y, double radius, double heading) {
        ScreenDrawing.disc(shapes, matrix, x, y, radius, BUTTON_SIDES, 3 * LAYER, GREEN_DIM);
        ScreenDrawing.disc(shapes, matrix, x, y, radius * 0.82, BUTTON_SIDES, 4 * LAYER, GLASS);
        double north = Math.toRadians(-heading);
        ScreenDrawing.disc(shapes, matrix, x + Math.sin(north) * radius * 0.9, y + Math.cos(north) * radius * 0.9, radius * 0.16, 8, 5 * LAYER, RED);
        ScreenDrawing.line(shapes, matrix, x, y, x, y + radius * 0.75, 5 * LAYER, (float) (radius * 0.14), GREEN);
    }

    /** The climb's arrow, up and green climbing, down and amber sinking. */
    private static void climbArrow(MultiBufferSource buffers, PoseStack pose, double x, double y, double half, double climb) {
        pose.pushPose();
        pose.translate(x, y, 0);
        if (climb < 0) {
            pose.mulPose(Axis.ZP.rotationDegrees(180));
        }
        ScreenDrawing.picture(buffers, pose.last().pose(), icon("climb"), 0, 0, half, 3 * LAYER, climb >= 0 ? GREEN : AMBER);
        pose.popPose();
    }

    /** A reading as a whole figure. */
    private static Component figure(double value) {
        return Component.literal(String.format(Locale.ROOT, "%d", Math.round(value)));
    }

    /**
     * The scope: rings and a cross on dark green glass, the beam sweeping round with its fading trail, her pin an amber
     * diamond and whoever is near a green dot, brightest just after the beam passes; the ping's ring going out when it
     * is touched.
     */
    private static void scope(MultiBufferSource buffers, Matrix4f matrix, double width, double height, Readings readings) {
        VertexConsumer shapes = shapes(buffers);
        double radius = Math.min(width, height) / 2 * 0.9;
        double y = height / 2;
        ScreenDrawing.disc(shapes, matrix, 0, y, radius, 32, 3 * LAYER, GREEN_DIM);
        ScreenDrawing.disc(shapes, matrix, 0, y, radius * 0.95, 32, 4 * LAYER, GLASS);
        ScreenDrawing.disc(shapes, matrix, 0, y, radius * 0.67, 32, 5 * LAYER, GREEN_DIM);
        ScreenDrawing.disc(shapes, matrix, 0, y, radius * 0.64, 32, 6 * LAYER, GLASS);
        ScreenDrawing.disc(shapes, matrix, 0, y, radius * 0.34, 32, 7 * LAYER, GREEN_DIM);
        ScreenDrawing.disc(shapes, matrix, 0, y, radius * 0.31, 32, 8 * LAYER, GLASS);
        float line = (float) (radius * 0.02);
        ScreenDrawing.line(shapes, matrix, -radius, y, radius, y, 9 * LAYER, line, GREEN_DIM);
        ScreenDrawing.line(shapes, matrix, 0, y - radius, 0, y + radius, 9 * LAYER, line, GREEN_DIM);
        float beam = (readings.ticks() * SWEEP_PER_TICK) % 360;
        for (int step = TRAIL; step >= 0; step--) {
            double angle = Math.toRadians(beam - step * TRAIL_STEP);
            int colour = fade(GREEN, 1 - (float) step / TRAIL);
            ScreenDrawing.line(shapes, matrix, 0, y, Math.sin(angle) * radius * 0.95, y + Math.cos(angle) * radius * 0.95, 10 * LAYER,
                (float) (radius * 0.05), colour);
        }
        for (Blip blip : readings.blips()) {
            blip(shapes, matrix, y, radius, beam, blip, readings.ticks());
        }
        if (readings.blips().stream().anyMatch(blip -> blip.mark() == Mark.FAULT) && faultLit(readings.ticks())) {
            warning(shapes, matrix, -width / 2 + height * 0.16, height * 0.82, height * 0.11);
        }
        ping(shapes, matrix, y, radius, readings.sinceTouched());
    }

    /** A blip on the scope, brightest just after the beam passes over it, her pin's always lit and pulsing. */
    private static void blip(VertexConsumer shapes, Matrix4f matrix, double y, double radius, float beam, Blip blip, float ticks) {
        boolean pin = blip.mark() == Mark.PIN;
        double out = pin ? Math.min(1, Math.log1p(blip.distance()) / Math.log1p(SCOPE_FAR)) : Math.min(1, blip.distance() / SCOPE_NEAR);
        double angle = Math.toRadians(blip.bearing());
        double x = Math.sin(angle) * out * radius * 0.92;
        double up = y + Math.cos(angle) * out * radius * 0.92;
        if (blip.mark() == Mark.FAULT) {
            if (faultLit(ticks)) {
                warning(shapes, matrix, x, up, radius * 0.13);
            }
            return;
        }
        if (pin) {
            float pulse = 0.6F + 0.4F * Mth.sin(ticks * 0.3F);
            ScreenDrawing.disc(shapes, matrix, x, up, radius * 0.09, 4, 12 * LAYER, fade(AMBER, pulse));
            return;
        }
        float since = (float) Mth.positiveModulo(beam - blip.bearing(), 360) / 360;
        ScreenDrawing.disc(shapes, matrix, x, up, radius * 0.05, 8, 11 * LAYER, fade(GREEN, 1 - since * 0.8F));
    }

    /** Whether a fault's warning sign is lit now, flashing. */
    private static boolean faultLit(float ticks) {
        return (int) (ticks / FAULT_FLASH) % 2 == 0;
    }

    /** A warning sign round a middle, so big: a red triangle, its tip up, with a yellow mark down its middle. */
    private static void warning(VertexConsumer shapes, Matrix4f matrix, double x, double y, double size) {
        double bottom = y - size * 0.8;
        ScreenDrawing.vertex(shapes, matrix, x - size, bottom, 14 * LAYER, RED);
        ScreenDrawing.vertex(shapes, matrix, x + size, bottom, 14 * LAYER, RED);
        ScreenDrawing.vertex(shapes, matrix, x, y + size, 14 * LAYER, RED);
        ScreenDrawing.vertex(shapes, matrix, x, y + size, 14 * LAYER, RED);
        ScreenDrawing.rect(shapes, matrix, x - size * 0.1, y - size * 0.25, x + size * 0.1, y + size * 0.5, 15 * LAYER, AMBER);
        ScreenDrawing.rect(shapes, matrix, x - size * 0.1, y - size * 0.6, x + size * 0.1, y - size * 0.4, 15 * LAYER, AMBER);
    }

    /** The ping's ring going out from the middle after the scope is touched. */
    private static void ping(VertexConsumer shapes, Matrix4f matrix, double y, double radius, float since) {
        if (since < 0 || since > PING_TICKS) {
            return;
        }
        double reach = radius * since / PING_TICKS;
        int colour = fade(GREEN, 1 - since / PING_TICKS);
        int pieces = 32;
        for (int piece = 0; piece < pieces; piece++) {
            double a = Mth.TWO_PI * piece / pieces;
            double b = Mth.TWO_PI * (piece + 1) / pieces;
            ScreenDrawing.line(shapes, matrix, Math.sin(a) * reach, y + Math.cos(a) * reach, Math.sin(b) * reach, y + Math.cos(b) * reach,
                13 * LAYER, (float) (radius * 0.04), colour);
        }
    }

    /**
     * Her engines as gauges standing side by side, each its picture under it: her lift thrusters' push in cyan, her
     * mains' in amber, and her boost, full and pulsing orange as it fires, filling red as it rests, green when ready.
     */
    private static void gauges(MultiBufferSource buffers, Matrix4f matrix, double width, double height, Readings readings) {
        VertexConsumer shapes = shapes(buffers);
        double slot = width / 3;
        FreighterFlight.BoostShown boost = readings.boost();
        double boostFill = switch (boost.stage()) {
            case READY -> 1;
            case FIRING -> boost.left();
            case RESTING -> 1 - boost.left();
        };
        int boostColour = switch (boost.stage()) {
            case READY -> GREEN;
            case FIRING -> fade(ORANGE, 0.6F + 0.4F * Mth.sin(readings.ticks()));
            case RESTING -> RED;
        };
        gauge(shapes, matrix, -slot, height, slot, readings.lift(), CYAN);
        gauge(shapes, matrix, 0, height, slot, readings.mains(), AMBER);
        gauge(shapes, matrix, slot, height, slot, readings.freighter() ? boostFill : 0, boostColour);
        double icon = height * 0.13;
        ScreenDrawing.picture(buffers, matrix, icon("lift"), -slot, height * 0.15, icon, 3 * LAYER, CYAN);
        ScreenDrawing.picture(buffers, matrix, icon("mains"), 0, height * 0.15, icon, 3 * LAYER, AMBER);
        ScreenDrawing.picture(buffers, matrix, icon("boost"), slot, height * 0.15, icon, 3 * LAYER, boostColour);
    }

    /** One gauge: a tall dim slot round a middle, filled from its foot a share of the way, ticked every quarter. */
    private static void gauge(VertexConsumer shapes, Matrix4f matrix, double x, double height, double slot, double share, int colour) {
        double half = slot * 0.18;
        double bottom = height * 0.32;
        double top = height * 0.92;
        ScreenDrawing.rect(shapes, matrix, x - half, bottom, x + half, top, 3 * LAYER, GREEN_DIM);
        ScreenDrawing.rect(shapes, matrix, x - half, bottom, x + half, bottom + (top - bottom) * Mth.clamp(share, 0, 1), 4 * LAYER, colour);
        for (int tick = 1; tick < 4; tick++) {
            double at = bottom + (top - bottom) * tick / 4;
            ScreenDrawing.rect(shapes, matrix, x + half, at - half * 0.1, x + half * 1.6, at + half * 0.1, 3 * LAYER, GREEN_DIM);
        }
    }

    /**
     * A grid of lit squares, as on a freighter's systems board: the top row one for each toggle on the desk under it,
     * lit in its lamp's colour while it is up, and the bottom row blinking to itself.
     */
    private static void annunciators(MultiBufferSource buffers, Matrix4f matrix, double width, double height, Readings readings) {
        VertexConsumer shapes = shapes(buffers);
        int columns = ConsoleKind.SWITCHES.controls();
        double cellW = width / columns;
        for (int column = 0; column < columns; column++) {
            double left = -width / 2 + column * cellW;
            boolean up = (readings.toggles() & 1 << column) != 0;
            square(shapes, matrix, left, height * 0.55, cellW, height * 0.38, up ? LAMP_COLOURS[column] : UNLIT);
            boolean blinking = readings.powered() && ((int) (readings.ticks() / (BLINK + column * 3)) + column) % 2 == 0;
            square(shapes, matrix, left, height * 0.1, cellW, height * 0.38, blinking ? LAMP_COLOURS[(column + 2) % LAMP_COLOURS.length] : UNLIT);
        }
    }

    /** A lit square in a cell of a grid, kept in from its edges. */
    private static void square(VertexConsumer shapes, Matrix4f matrix, double left, double bottom, double cellW, double cellH, int colour) {
        double inset = cellW * 0.12;
        ScreenDrawing.rect(shapes, matrix, left + inset, bottom, left + cellW - inset, bottom + cellH, 3 * LAYER, colour);
    }

    /**
     * The boost: its fat chevrons big in the middle, green and gently glowing when ready, flashing orange as it fires
     * with a bar running down under them, red while it rests with a ring of segments lighting one by one round them.
     */
    private static void boostScreen(MultiBufferSource buffers, Matrix4f matrix, double width, double height, Readings readings) {
        VertexConsumer shapes = shapes(buffers);
        FreighterFlight.BoostShown boost = readings.boost();
        double size = Math.min(width, height) * 0.3;
        double y = height * 0.55;
        int colour = switch (boost.stage()) {
            case READY -> fade(GREEN, 0.75F + 0.25F * Mth.sin(readings.ticks() * 0.1F));
            case FIRING -> (int) readings.ticks() % 4 < 2 ? ORANGE : AMBER;
            case RESTING -> RED;
        };
        if (!readings.freighter()) {
            colour = UNLIT;
        }
        if (boost.stage() == FreighterFlight.BoostStage.FIRING) {
            double length = width * 0.7 * boost.left();
            ScreenDrawing.rect(shapes, matrix, -length / 2, height * 0.1, length / 2, height * 0.17, 3 * LAYER, ORANGE);
        }
        if (boost.stage() == FreighterFlight.BoostStage.RESTING) {
            restRing(shapes, matrix, y, size * 1.35, 1 - boost.left());
        }
        ScreenDrawing.picture(buffers, matrix, icon("boost"), 0, y, size, 5 * LAYER, colour);
    }

    /**
     * The hyperspace jump: its starburst big in the middle, dark with nothing to jump to, pulsing cyan while she can
     * jump, and white as her drive spools up, streaks streaming out of it longer and longer and a ring of segments
     * round it lighting one by one.
     */
    private static void jumpScreen(MultiBufferSource buffers, Matrix4f matrix, double width, double height, Readings readings) {
        VertexConsumer shapes = shapes(buffers);
        FreighterFlight.JumpShown jump = readings.jump();
        double size = Math.min(width, height) * 0.28;
        double y = height * 0.5;
        int colour = switch (jump.stage()) {
            case UNAVAILABLE -> UNLIT;
            case READY -> fade(CYAN, 0.7F + 0.3F * Mth.sin(readings.ticks() * 0.15F));
            case SPOOLING -> JUMP_WHITE;
        };
        if (jump.stage() == FreighterFlight.JumpStage.SPOOLING) {
            streaks(shapes, matrix, y, size, jump.spooled(), readings.ticks());
            spoolRing(shapes, matrix, y, size * 1.5, jump.spooled());
        }
        ScreenDrawing.picture(buffers, matrix, icon("jump"), 0, y, size, 5 * LAYER, colour);
    }

    /**
     * The climb boost: its arrow up over flames big in the middle, dark while she cannot climb so, gently glowing
     * orange while she can climb, flashing orange and amber as it fires with dashes racing up either side, and red
     * while it rests with a ring of segments lighting one by one round it.
     */
    private static void climbScreen(MultiBufferSource buffers, Matrix4f matrix, double width, double height, Readings readings) {
        VertexConsumer shapes = shapes(buffers);
        FreighterClimbBoost.Shown climb = readings.climbBoost();
        double size = Math.min(width, height) * 0.3;
        double y = height * 0.55;
        if (climb.stage() == FreighterClimbBoost.Stage.FIRING) {
            dashes(shapes, matrix, width, height, readings.ticks(), false);
        }
        if (climb.stage() == FreighterClimbBoost.Stage.RESTING) {
            restRing(shapes, matrix, y, size * 1.35, 1 - climb.left());
        }
        ScreenDrawing.picture(buffers, matrix, icon("climb_boost"), 0, y, size, 5 * LAYER, climbColour(readings));
    }

    /**
     * The slam landing: its arrow down onto the ground big in the middle, dark while she cannot slam down, pulsing red
     * while she can, and flashing red and amber as she drives down, dashes racing down either side.
     */
    private static void slamScreen(MultiBufferSource buffers, Matrix4f matrix, double width, double height, Readings readings) {
        if (readings.slam() == FreighterSlam.Stage.FIRING) {
            dashes(ScreenDrawing.shapes(buffers), matrix, width, height, readings.ticks(), true);
        }
        double size = Math.min(width, height) * 0.3;
        ScreenDrawing.picture(buffers, matrix, icon("slam_landing"), 0, height * 0.55, size, 5 * LAYER, slamColour(readings));
    }

    /** The colour the slam landing is lit in, on its screen and its button, as it stands (see {@link #slamScreen}). */
    private static int slamColour(Readings readings) {
        if (!readings.freighter()) {
            return UNLIT;
        }
        return switch (readings.slam()) {
            case UNAVAILABLE -> SLAM_DARK;
            case READY -> fade(RED, 0.75F + 0.25F * Mth.sin(readings.ticks() * 0.12F)) | 0xFF000000;
            case FIRING -> (int) readings.ticks() % 4 < 2 ? RED : AMBER;
        };
    }

    /**
     * Dashes racing up either side of the climb boost's arrow as it fires, as the sky would past her, or down either
     * side of the slam landing's as she drives down.
     */
    private static void dashes(VertexConsumer shapes, Matrix4f matrix, double width, double height, float ticks, boolean down) {
        double length = height * 0.12;
        for (int dash = 0; dash < CLIMB_DASHES; dash++) {
            double side = (dash % 2 == 0 ? -1 : 1) * width * (0.3 + 0.05 * (dash % 3));
            double run = (ticks * CLIMB_DASH_SPEED + dash * 0.29) % 1;
            double bottom = height * 0.1 + (down ? 1 - run : run) * (height * 0.8 - length);
            ScreenDrawing.line(shapes, matrix, side, bottom, side, bottom + length, 4 * LAYER, (float) (width * 0.015), AMBER);
        }
    }

    /** The colour the climb boost is lit in, on its screen and its button: by where it stands (see {@link #climbScreen}). */
    private static int climbColour(Readings readings) {
        if (!readings.freighter()) {
            return UNLIT;
        }
        return switch (readings.climbBoost().stage()) {
            case UNAVAILABLE -> CLIMB_DARK;
            case READY -> fade(ORANGE, 0.75F + 0.25F * Mth.sin(readings.ticks() * 0.12F)) | 0xFF000000;
            case FIRING -> (int) readings.ticks() % 4 < 2 ? ORANGE : AMBER;
            case RESTING -> RED;
        };
    }

    /** Streaks streaming out of the jump's starburst as her drive spools up, longer the further it has spooled. */
    private static void streaks(VertexConsumer shapes, Matrix4f matrix, double y, double size, double spooled, float ticks) {
        for (int streak = 0; streak < JUMP_STREAKS; streak++) {
            double angle = Mth.TWO_PI * streak / JUMP_STREAKS;
            double run = ((ticks * JUMP_STREAK_SPEED + streak * 0.37) % 1);
            double from = size * (0.9 + run * 0.6);
            double to = from + size * (0.1 + spooled * 0.8);
            ScreenDrawing.line(shapes, matrix, Math.sin(angle) * from, y + Math.cos(angle) * from, Math.sin(angle) * to, y + Math.cos(angle) * to,
                3 * LAYER, (float) (size * 0.05), fade(CYAN, (float) (1 - run)));
        }
    }

    /** The ring round the jump's starburst as her drive spools up, its segments lit one by one. */
    private static void spoolRing(VertexConsumer shapes, Matrix4f matrix, double y, double radius, double spooled) {
        int segments = 12;
        for (int segment = 0; segment < segments; segment++) {
            double a = Mth.TWO_PI * (segment + 0.15) / segments;
            double b = Mth.TWO_PI * (segment + 0.85) / segments;
            int colour = segment < spooled * segments ? CYAN : UNLIT;
            ScreenDrawing.line(shapes, matrix, Math.sin(a) * radius, y + Math.cos(a) * radius, Math.sin(b) * radius, y + Math.cos(b) * radius,
                4 * LAYER, (float) (radius * 0.08), colour);
        }
    }

    /** The ring of segments round the boost's chevrons while it rests, lit one by one as its rest runs out. */
    private static void restRing(VertexConsumer shapes, Matrix4f matrix, double y, double radius, double rested) {
        int segments = 8;
        for (int segment = 0; segment < segments; segment++) {
            double a = Mth.TWO_PI * (segment + 0.15) / segments;
            double b = Mth.TWO_PI * (segment + 0.85) / segments;
            int colour = segment < rested * segments ? RED : UNLIT;
            ScreenDrawing.line(shapes, matrix, Math.sin(a) * radius, y + Math.cos(a) * radius, Math.sin(b) * radius, y + Math.cos(b) * radius,
                4 * LAYER, (float) (radius * 0.12), colour);
        }
    }

    /**
     * A module's controls standing on its desk, in the desk's own frame, lit with the light where it stands: its kind's
     * row of controls across the middle, and its row of small lamps along the far edge, glowing.
     */
    static void controls(ConsoleKind kind, PoseStack pose, MultiBufferSource buffers, double width, double height, int light, Readings readings,
                         ConsoleBlockEntity console) {
        VertexConsumer solid = buffers.getBuffer(CONTROLS);
        int count = kind.controls();
        double slot = width / count;
        for (int control = 0; control < count; control++) {
            double x = -width / 2 + slot * (control + 0.5);
            control(kind, solid, pose, x, height * 0.42, slot, light, readings, console, control);
        }
        lamps(buffers, pose.last().pose(), width, height, readings);
    }

    /** One control of a module's kind, standing round a point of its desk. */
    private static void control(ConsoleKind kind, VertexConsumer solid, PoseStack pose, double x, double y, double slot, int light, Readings readings,
                                ConsoleBlockEntity console, int index) {
        switch (kind) {
            case SWITCHES -> toggle(solid, pose, x, y, light, console.toggleUp(index));
            case SCANNER -> knob(solid, pose, x, y, slot, light, (index * 97 + readings.ticks() * (readings.powered() ? 0.4F : 0)) % 270 - 135);
            case ENGINES -> lever(solid, pose, x, y, slot, light, index == 0 ? readings.lift() : index == 1 ? readings.mains() : index == 2
                ? readings.speed() / SPEED_FULL : readings.climb() / CLIMB_FULL * 0.5 + 0.5);
            case BOOST -> bigButton(solid, pose, y, slot, light, readings.sinceTouched() >= 0 && readings.sinceTouched() < PRESSED_TICKS,
                readings.freighter() ? RED : UNLIT, HAZARD_YELLOW, HAZARD_BLACK);
            case JUMP -> bigButton(solid, pose, y, slot, light, readings.sinceTouched() >= 0 && readings.sinceTouched() < PRESSED_TICKS,
                jumpCap(readings), JUMP_WHITE, JUMP_BLUE);
            case CLIMB -> bigButton(solid, pose, y, slot, light, readings.sinceTouched() >= 0 && readings.sinceTouched() < PRESSED_TICKS,
                climbColour(readings), ORANGE, HAZARD_BLACK);
            case SLAM -> bigButton(solid, pose, y, slot, light, readings.sinceTouched() >= 0 && readings.sinceTouched() < PRESSED_TICKS,
                slamColour(readings), RED, HAZARD_BLACK);
            default -> pushButton(solid, pose, x, y, slot, light, LAMP_COLOURS[index % LAMP_COLOURS.length]);
        }
    }

    /** A chunky toggle: its dark plate and a chrome bat leaning up or down on it, toward the screen up. */
    private static void toggle(VertexConsumer solid, PoseStack pose, double x, double y, int light, boolean up) {
        FlatQuads.box(solid, pose.last(), light, PLATE, (float) x - 0.035F, (float) y - 0.05F, 0, (float) x + 0.035F, (float) y + 0.05F, 0.012F);
        pose.pushPose();
        pose.translate(x, y, 0.012);
        pose.mulPose(Axis.XP.rotationDegrees(up ? -BAT_LEAN : BAT_LEAN));
        FlatQuads.box(solid, pose.last(), light, CHROME, -BAT_HALF, -BAT_HALF, 0, BAT_HALF, BAT_HALF, BAT_LENGTH);
        pose.popPose();
    }

    /** A round knob, as a short prism, its white mark turned so far round from straight up, in degrees. */
    private static void knob(VertexConsumer solid, PoseStack pose, double x, double y, double slot, int light, float turned) {
        float radius = (float) Math.min(slot * 0.28, 0.05);
        prism(solid, pose, x, y, radius, 0.03F, light, KNOB);
        pose.pushPose();
        pose.translate(x, y, 0.031);
        pose.mulPose(Axis.ZP.rotationDegrees(-turned));
        FlatQuads.box(solid, pose.last(), light, KNOB_MARK, -0.005F, 0, 0, 0.005F, radius * 0.9F, 0.002F);
        pose.popPose();
    }

    /** A lever in its slot, its handle so far up the slot, a share of the way. */
    private static void lever(VertexConsumer solid, PoseStack pose, double x, double y, double slot, int light, double share) {
        float half = (float) Math.min(slot * 0.08, 0.012);
        float bottom = (float) y - 0.07F;
        float top = (float) y + 0.07F;
        FlatQuads.box(solid, pose.last(), light, PLATE, (float) x - half, bottom, 0, (float) x + half, top, 0.004F);
        float at = (float) (bottom + (top - bottom) * Mth.clamp(share, 0, 1));
        FlatQuads.box(solid, pose.last(), light, CHROME, (float) x - half * 3, at - 0.012F, 0, (float) x + half * 3, at + 0.012F, 0.035F);
    }

    /** A small square push button, its cap of a colour. */
    private static void pushButton(VertexConsumer solid, PoseStack pose, double x, double y, double slot, int light, int colour) {
        float half = (float) Math.min(slot * 0.25, 0.035);
        FlatQuads.box(solid, pose.last(), light, PLATE, (float) x - half * 1.3F, (float) y - half * 1.3F, 0, (float) x + half * 1.3F,
            (float) y + half * 1.3F, 0.008F);
        FlatQuads.box(solid, pose.last(), light, colour, (float) x - half, (float) y - half, 0.008F, (float) x + half, (float) y + half, 0.02F);
    }

    /** The jump button's cap: lit cyan, pulsing, while she can jump, flashing white as she spools up, dark otherwise. */
    private static int jumpCap(Readings readings) {
        return switch (readings.jump().stage()) {
            case UNAVAILABLE -> JUMP_DARK;
            case READY -> fade(CYAN, 0.75F + 0.25F * Mth.sin(readings.ticks() * 0.15F)) | 0xFF000000;
            case SPOOLING -> (int) readings.ticks() % 6 < 3 ? JUMP_WHITE : CYAN;
        };
    }

    /**
     * A big button in the middle of its desk: a ring of hazard stripes in two colours round a round cap standing proud,
     * pressed in a moment after a press.
     */
    private static void bigButton(VertexConsumer solid, PoseStack pose, double y, double slot, int light, boolean pressed, int colour, int stripes,
                                  int between) {
        float radius = (float) Math.min(slot * 0.22, 0.13);
        for (int stripe = 0; stripe < BUTTON_SIDES; stripe++) {
            wedge(solid, pose, y, radius * 1.15F, radius * 1.55F, stripe, light, stripe % 2 == 0 ? stripes : between);
        }
        prism(solid, pose, 0, y, radius * 1.1F, 0.02F, light, PLATE);
        prism(solid, pose, 0, y, radius, pressed ? 0.06F - PUSH : 0.06F, LightTexture.FULL_BRIGHT, colour);
    }

    /** One stripe of a flat ring round a point on the desk's middle line, between two radii. */
    private static void wedge(VertexConsumer solid, PoseStack pose, double y, float inner, float outer, int stripe, int light, int colour) {
        double a = Mth.TWO_PI * stripe / BUTTON_SIDES;
        double b = Mth.TWO_PI * (stripe + 1) / BUTTON_SIDES;
        FlatQuads.quad(solid, pose.last(), light, colour, 0, 0, 1,
            (float) (Math.sin(a) * inner), (float) (y + Math.cos(a) * inner), 0.003F, (float) (Math.sin(a) * outer), (float) (y + Math.cos(a) * outer), 0.003F,
            (float) (Math.sin(b) * outer), (float) (y + Math.cos(b) * outer), 0.003F, (float) (Math.sin(b) * inner), (float) (y + Math.cos(b) * inner), 0.003F);
    }

    /** A short round prism standing on the desk round a point, so tall: its top and its sides. */
    private static void prism(VertexConsumer solid, PoseStack pose, double x, double y, float radius, float tall, int light, int colour) {
        int darker = fade(colour, 0.7F) | 0xFF000000;
        for (int side = 0; side < BUTTON_SIDES; side++) {
            double a = Mth.TWO_PI * side / BUTTON_SIDES;
            double b = Mth.TWO_PI * (side + 1) / BUTTON_SIDES;
            float ax = (float) (x + Math.sin(a) * radius);
            float ay = (float) (y + Math.cos(a) * radius);
            float bx = (float) (x + Math.sin(b) * radius);
            float by = (float) (y + Math.cos(b) * radius);
            FlatQuads.quad(solid, pose.last(), light, colour, 0, 0, 1, (float) x, (float) y, tall, bx, by, tall, ax, ay, tall, (float) x, (float) y, tall);
            float nx = (float) Math.sin((a + b) / 2);
            float ny = (float) Math.cos((a + b) / 2);
            FlatQuads.quad(solid, pose.last(), light, darker, nx, ny, 0, ax, ay, 0, ax, ay, tall, bx, by, tall, bx, by, 0);
        }
    }

    /**
     * The row of small lamps along the desk's far edge, glowing: blinking each to its own beat with a ship to read, all
     * lit a moment after a touch, as a lamp test; with none, all dark but one blinking slowly green.
     */
    private static void lamps(MultiBufferSource buffers, Matrix4f matrix, double width, double height, Readings readings) {
        VertexConsumer shapes = shapes(buffers);
        double y = height - LAMP_IN;
        boolean testing = readings.sinceTouched() >= 0 && readings.sinceTouched() < PING_TICKS;
        for (int lamp = 0; lamp < LAMPS; lamp++) {
            double x = -width / 2 + width * (lamp + 0.5) / LAMPS;
            ScreenDrawing.disc(shapes, matrix, x, y, LAMP * 1.4, 10, LAYER, PLATE);
            ScreenDrawing.disc(shapes, matrix, x, y, LAMP, 10, 2 * LAYER, lampColour(lamp, readings, testing));
        }
    }

    private static int lampColour(int lamp, Readings readings, boolean testing) {
        if (testing) {
            return LAMP_COLOURS[lamp % LAMP_COLOURS.length];
        }
        if (!readings.powered()) {
            return lamp == 0 && (int) (readings.ticks() / STANDBY_BLINK) % 2 == 0 ? GREEN : UNLIT;
        }
        boolean lit = lamp % 3 == 0 || ((int) (readings.ticks() / (BLINK + lamp * 2)) + lamp) % 3 != 0;
        return lit ? LAMP_COLOURS[lamp % LAMP_COLOURS.length] : UNLIT;
    }

    /** The buffer the screens' shapes go into. */
    private static VertexConsumer shapes(MultiBufferSource buffers) {
        return buffers.getBuffer(SHAPES);
    }

    /** Gives the screens' shapes and the controls buffers of their own, which every module adds to through a frame. */
    static void registerBuffers(RegisterRenderBuffersEvent event) {
        BatchedBuffers.register(event, SHAPES, CONTROLS);
    }

    /** Draws every module's screens' shapes and controls together, once the level's block entities are drawn. */
    static void drawBatched(RenderLevelStageEvent event) {
        BatchedBuffers.draw(event, SHAPES, CONTROLS);
    }

    /** A colour as bright as a share of itself, its alpha kept. */
    private static int fade(int colour, float share) {
        float kept = Mth.clamp(share, 0, 1);
        int r = Math.round((colour >> 16 & 0xFF) * kept);
        int g = Math.round((colour >> 8 & 0xFF) * kept);
        int b = Math.round((colour & 0xFF) * kept);
        return colour & 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** One of the console's pictures, white to be tinted (see tools/generate_textures.mjs). */
    private static ResourceLocation icon(String name) {
        return ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/gui/console/" + name + ".png");
    }
}
