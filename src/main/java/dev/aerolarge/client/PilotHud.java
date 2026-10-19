package dev.aerolarge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.AerolargeClientConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.AutopilotBlockEntity;
import dev.aerolarge.block.AutopilotBlockEntity.Lateral;
import dev.aerolarge.block.AutopilotBlockEntity.Phase;
import dev.aerolarge.client.PanelArt.Icon;
import dev.aerolarge.client.ScreenDrawing.Align;
import dev.aerolarge.item.PilotHelmetItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Matrix4f;

/**
 * The pilots' head-up display, shown by the visor of a pilot helmet: while the local player wears one on the flight
 * deck of an assembled airliner, within reach of its autopilot (as from either pilot's seat), how it flies in the top
 * corners of the screen, in the flight deck's glass-cockpit colours (see {@link AutopilotScreenRenderer}) on dark
 * glass, with what the autopilot is set to in magenta. Top left the speed on a moving tape, the speed set as a
 * magenta bug beside it and a green arrow to the speed ten seconds on; under it a picture of what the autopilot is
 * doing, and its name. Top right the height the autopilot holds (its own block's) on a moving tape, the height set as
 * a magenta bug, the ground rising up the tape as a brown band once it is near and a bar beside it that climbs green
 * or sinks amber; under it the wheels' height above the ground and the climb or sink with an arrow. At the top in the
 * middle a heading strip with the heading set as a magenta bug, the autopilot's mode (straight on or round a circle)
 * beside it, and under it a small attitude indicator: sky and ground, the pitch ladder and the bank pointer, clear of
 * the crosshair. Pictures and colours carry it, and numbers only where the number is the point, so that it works for
 * players who cannot read yet.
 *
 * <p>The airliner's attitude and height come from its pose as the client draws it, so the HUD moves as smoothly as the
 * airliner does; the autopilot's phase, mode and targets from what its block sends the clients. Its shapes are drawn
 * a batch at a time rather than one by one, so it costs a few draw calls a frame. It hides with the rest of the HUD
 * (F1), behind any screen, under the debug screen and while looking through an outside camera, and is off altogether
 * with {@code pilotHud} off in the client config. As it comes on it fades in, with a beep when it is the helmet going
 * on that turns it on; walking back within reach of the autopilot with the helmet on, it only fades in.
 */
public final class PilotHud {
    /** While no autopilot has been found, how often the airliner the player rides is searched for one, in ticks. */
    private static final int SEARCH_TICKS = 20;
    /**
     * How far ahead the speed trend arrow points, in seconds, and how much of each tick's change of speed it takes in,
     * so that it settles rather than jitters.
     */
    private static final double TREND_SECONDS = 10;
    private static final double TREND_SMOOTHING = 0.1;
    /** A trend arrow shorter than this, in blocks a second, is not drawn: the speed is steady. */
    private static final double TREND_MIN = 1;
    /** Climbing or sinking slower than this, in blocks a second, shows no arrow. */
    private static final double STEADY = 0.05;
    /** Banked further than this, in degrees, the bank pointer turns amber, as the callouts say "bank angle" then. */
    private static final double BANK_WARNING = 35;
    /** Vanilla draws the status effects' icons top right, in rows this tall: the helpful ones, then the harmful. */
    private static final int EFFECT_ROW = 26;
    /** How long the HUD takes to fade in as it comes on, in milliseconds. */
    private static final float FADE_IN_MILLIS = 600;
    /** How loud the beep of the HUD coming on is, as a share of full volume: about as loud as a button's click. */
    private static final float BEEP_VOLUME = 0.4F;

    /** How far in from the edges of the screen the glass stands, and its padding round what is on it, in GUI pixels. */
    private static final int MARGIN = 4;
    private static final int PAD = 3;
    /** A row with a picture is as tall as the picture; the gap after a picture, and between the rows and the scales. */
    private static final int ROW = Icon.SIZE;
    private static final int ICON_GAP = 3;
    private static final int GAP = 3;
    /** The speed and height tapes: how tall, and how wide each. */
    private static final int TAPE_HEIGHT = 72;
    private static final int SPEED_TAPE_WIDTH = 30;
    private static final int ALTITUDE_TAPE_WIDTH = 34;
    /** Pixels per block a second on the speed tape and per block on the height tape. */
    private static final float PIXELS_PER_SPEED = 1.5F;
    private static final float PIXELS_PER_BLOCK = 1F;
    /** Each tape is marked every so many blocks a second or blocks, and numbered every so many. */
    private static final int SPEED_TICK = 5;
    private static final int SPEED_LABEL = 10;
    private static final int ALTITUDE_TICK = 10;
    private static final int ALTITUDE_LABEL = 20;
    /** Marks along a scale's edge: a numbered one's length and the others'. */
    private static final int LONG_TICK = 5;
    private static final int SHORT_TICK = 3;
    /** The space beside a tape, on its marked side, for the bug and the trend arrow. */
    private static final int BUG_ROOM = 7;
    /** The bug: how far it reaches from the scale's edge, and half its breadth. */
    private static final float BUG_LENGTH = 5;
    private static final float BUG_HALF = 4;
    /** The box with the figure now in the middle of a tape: half its height. */
    private static final int BOX_HALF = 6;
    /** The climb bar beside the height tape: its width, its gap from the tape, and the climb at its ends. */
    private static final int CLIMB_BAR_WIDTH = 5;
    private static final int CLIMB_BAR_GAP = 2;
    private static final double CLIMB_BAR_RANGE = 8;
    /** The heading strip: half its width, and pixels per degree; marked every 5 degrees and numbered every 10. */
    private static final int HEADING_HALF_WIDTH = 60;
    private static final float PIXELS_PER_DEGREE = 2;
    private static final int HEADING_TICK = 5;
    private static final int HEADING_LABEL = 10;
    /** The heading box's half width, and how far down the strip it reaches. */
    private static final int HEADING_BOX_HALF = 12;
    private static final int HEADING_BOX_HEIGHT = 11;
    /** Room either side of the strip for the mode's picture and the heading or radius set. */
    private static final int HEADING_SIDE = 24;
    /** The attitude indicator: half its width and height, pixels per degree of pitch, and the bank scale's radius. */
    private static final int ADI_HALF_WIDTH = 32;
    private static final int ADI_HALF_HEIGHT = 20;
    private static final float PIXELS_PER_PITCH = 1.2F;
    private static final float BANK_RADIUS = 17;
    /** The bank scale's marks, in degrees, the longer ones at level and at 30 either way. */
    private static final int[] BANK_MARKS = {-45, -30, -20, -10, 0, 10, 20, 30, 45};
    /** Pitch ladder rungs every so many degrees, as far as this either way. */
    private static final int RUNG_STEP = 5;
    private static final int RUNG_LIMIT = 20;
    /** Half the aircraft symbol's span, and where its wings start out from its middle. */
    private static final int SYMBOL_HALF = 18;
    private static final int SYMBOL_GAP = 7;
    /** Where the middle of a line of text is, in font pixels below its top: half its capitals' height. */
    private static final float TEXT_MIDDLE = 3.5F;

    private static final int GLASS = 0xA005080D;
    private static final int GLASS_EDGE = 0x904A525E;
    private static final int TAPE = 0xC0000000;
    private static final int BOX = 0xFF000000;
    private static final int WHITE = AutopilotScreenRenderer.WHITE;
    private static final int GREEN = AutopilotScreenRenderer.GREEN;
    private static final int AMBER = AutopilotScreenRenderer.AMBER;
    private static final int MAGENTA = AutopilotScreenRenderer.TARGET;
    private static final int GREY = AutopilotScreenRenderer.GREY;
    /** The fixed marks the moving scales are read against, yellow as the flight displays' aircraft symbol. */
    private static final int YELLOW = 0xFFF2CF30;
    private static final int SKY = 0xC02D6FD1;
    private static final int GROUND = 0xC07A4E26;

    private static final Icon SPEED = Icon.of("autopilot/speed");
    private static final Icon ALTITUDE = Icon.of("autopilot/altitude");
    private static final Icon GROUND_HEIGHT = Icon.of("autopilot/ground_height");
    private static final Icon OFF = Icon.of("autopilot/disengage");
    private static final Icon TAKE_OFF = Icon.of("autopilot/take_off");
    private static final Icon CLIMB = Icon.of("autopilot/climb");
    private static final Icon CRUISE = Icon.of("autopilot/engage");
    private static final Icon DESCEND = Icon.of("autopilot/descend");
    private static final Icon LAND = Icon.of("autopilot/land");
    private static final Icon ROLL_OUT = Icon.of("autopilot/roll_out");
    private static final Icon STRAIGHT = Icon.of("autopilot/hold_heading");
    private static final Icon CIRCLE_LEFT = Icon.of("autopilot/orbit_left");
    private static final Icon CIRCLE_RIGHT = Icon.of("autopilot/orbit_right");
    private static final String PHASE_KEY = "message.aerolarge.autopilot.phase.";
    private static final String COMPASS_KEY = "screen.aerolarge.passenger_screen.compass.";
    private static final Component AP_OFF = Component.translatable("screen.aerolarge.pilot_hud.off");

    /** The autopilot of the airliner the player rides, once found. */
    @Nullable
    private static Cockpit cockpit;
    private static int untilSearch;
    /** Whether the HUD is on as of the last tick: the helmet on, within reach of the autopilot. */
    private static boolean on;
    /** Whether the player wore a pilot helmet as of the last tick, to tell it going on. */
    private static boolean wearing;
    /** How the HUD came on, while it waits to be drawn for the first time since; and when it was. */
    private static Boot boot = Boot.NONE;
    private static long bootedAt;

    private PilotHud() {
    }

    /** How the HUD came on: not since it was last drawn, as the helmet went on (with a beep), or otherwise. */
    private enum Boot {
        NONE, HELMET, QUIET
    }

    /**
     * The autopilot of the airliner the player rides, and what the HUD keeps of it from tick to tick: how fast the
     * speed changes, for the trend arrow.
     */
    private static final class Cockpit {
        final AutopilotBlockEntity autopilot;
        final Direction nose;
        /** The middle of the autopilot's block, where it reads the airliner's height, and how high over the wheels. */
        final Vec3 centre;
        final double aboveWheels;
        /** The airspeed last tick, NaN before the first; and how fast it changes, in blocks a second each second. */
        double lastAirspeed = Double.NaN;
        double acceleration;

        Cockpit(AutopilotBlockEntity autopilot, SableClient.Frame frame) {
            this.autopilot = autopilot;
            nose = frame.nose();
            centre = Vec3.atCenterOf(autopilot.getBlockPos());
            aboveWheels = centre.y - frame.origin().y;
        }

        /** The autopilot of the airliner the player rides, if it has one. */
        @Nullable
        static Cockpit find(LocalPlayer player) {
            AutopilotBlockEntity autopilot = SableClient.autopilot(player);
            SableClient.Frame frame = autopilot != null ? SableClient.frame(autopilot) : null;
            return frame != null ? new Cockpit(autopilot, frame) : null;
        }

        /** Whether this is still the autopilot of the airliner the player rides. */
        boolean holds(LocalPlayer player) {
            return !autopilot.isRemoved() && SableClient.isAboard(player, autopilot);
        }

        /** Takes in how fast the airliner goes this tick. */
        void sample() {
            SableClient.Attitude attitude = SableClient.attitude(autopilot, nose, 1);
            if (attitude == null) {
                lastAirspeed = Double.NaN;
                acceleration = 0;
                return;
            }
            if (!Double.isNaN(lastAirspeed)) {
                double change = (attitude.airspeed() - lastAirspeed) * SharedConstants.TICKS_PER_SECOND;
                acceleration += TREND_SMOOTHING * (change - acceleration);
            }
            lastAirspeed = attitude.airspeed();
        }

        /** How the airliner flies as it is drawn this frame; null once it is no longer assembled. */
        @Nullable
        Reading read(Level level, float partialTick) {
            SableClient.Attitude attitude = SableClient.attitude(autopilot, nose, partialTick);
            SableClient.View view = SableClient.view(centre, Vec3.atLowerCornerOf(nose.getNormal()), new Vec3(0, 1, 0), partialTick);
            if (attitude == null || view == null) {
                return null;
            }
            Vec3 at = view.eye();
            int x = Mth.floor(at.x);
            int z = Mth.floor(at.z);
            double radio = Double.NaN;
            if (level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) {
                radio = Math.max(0, at.y - aboveWheels - level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
            }
            return new Reading(attitude.airspeed(), acceleration * TREND_SECONDS, at.y, radio, attitude.verticalSpeed(),
                attitude.pitch(), attitude.roll(), attitude.heading());
        }
    }

    /**
     * How the airliner flies: its airspeed and how much faster it will be going in {@link #TREND_SECONDS}, in blocks a
     * second; the height of the autopilot's block, and the wheels' height above the ground (NaN while the ground there
     * is not loaded), in blocks; its climb, in blocks a second; and its pitch, bank and compass heading, in degrees.
     */
    private record Reading(double airspeed, double trend, double altitude, double radio, double climb, double pitch, double roll,
                           double heading) {
    }

    /**
     * What the autopilot is set to, each NaN where it has none to show: off, or once it has nothing more to fly to
     * (no height while landing, nothing once down and braking). The heading is a compass bearing, only while it holds
     * one.
     */
    private record Targets(double speed, double altitude, double heading) {
        static Targets of(AutopilotBlockEntity autopilot) {
            Phase phase = autopilot.phase();
            if (phase == Phase.OFF || phase == Phase.ROLLOUT) {
                return new Targets(Double.NaN, Double.NaN, Double.NaN);
            }
            double altitude = phase == Phase.LAND ? Double.NaN : autopilot.targetAltitude();
            double heading = autopilot.lateral() == Lateral.HEADING ? Mth.positiveModulo(autopilot.targetHeading() + 180, 360) : Double.NaN;
            return new Targets(autopilot.targetSpeed(), altitude, heading);
        }
    }

    /**
     * Each tick: keeps hold of the autopilot of the airliner the player rides, finding it again once they ride another
     * (or none), takes in its speed for the trend arrow, and switches the HUD on or off.
     */
    public static void tick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !AerolargeClientConfig.PILOT_HUD.get()) {
            cockpit = null;
            switchTo(false, false);
            return;
        }
        Cockpit held = cockpit;
        if (held != null && !held.holds(player)) {
            held = null;
            untilSearch = 0;
        }
        if (held == null && --untilSearch <= 0) {
            untilSearch = SEARCH_TICKS;
            held = Cockpit.find(player);
        }
        cockpit = held;
        if (held != null) {
            held.sample();
        }
        boolean helmet = PilotHelmetItem.worn(player);
        switchTo(helmet && held != null && held.autopilot.canBeWorkedBy(player), helmet);
    }

    /** Switches the HUD on or off; coming on, it waits to fade in, beeping first if the helmet has just gone on. */
    private static void switchTo(boolean nowOn, boolean nowWearing) {
        if (nowOn && !on) {
            boot = nowWearing && !wearing ? Boot.HELMET : Boot.QUIET;
        } else if (!nowOn) {
            boot = Boot.NONE;
        }
        on = nowOn;
        wearing = nowWearing;
    }

    /** Draws the HUD, if it is on and nothing hides it. */
    public static void overlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Cockpit held = cockpit;
        if (player == null || held == null || !on || hidden(minecraft, player)) {
            return;
        }
        Reading reading = held.read(player.level(), deltaTracker.getGameTimeDeltaPartialTick(true));
        if (reading == null) {
            return;
        }
        if (boot != Boot.NONE) {
            if (boot == Boot.HELMET) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(AerolargeRegistry.HUD_ON.get(), 1F, BEEP_VOLUME));
            }
            boot = Boot.NONE;
            bootedAt = Util.getMillis();
        }
        float fade = Math.min(1, (Util.getMillis() - bootedAt) / FADE_IN_MILLIS);
        AutopilotBlockEntity autopilot = held.autopilot;
        Targets targets = Targets.of(autopilot);
        Canvas canvas = new Canvas(graphics, minecraft.font, (float) minecraft.getWindow().getGuiScale(), fade);
        int middle = graphics.guiWidth() / 2;
        speedPanel(canvas, MARGIN, MARGIN, autopilot.phase(), reading, targets.speed());
        altitudePanel(canvas, graphics.guiWidth() - MARGIN, MARGIN + effectsBelow(player), reading, targets.altitude());
        int strip = headingPanel(canvas, middle, MARGIN, autopilot, reading, targets.heading());
        attitudeIndicator(canvas, middle, strip + GAP + ADI_HALF_HEIGHT, reading);
    }

    /**
     * Whether something hides the HUD while it is on: the rest of the HUD hidden, a screen open, or the player not
     * looking out through their own eyes.
     */
    private static boolean hidden(Minecraft minecraft, LocalPlayer player) {
        return minecraft.options.hideGui || minecraft.screen != null
            || minecraft.getDebugOverlay().showDebugScreen() || PassengerCameras.looking() || minecraft.getCameraEntity() != player;
    }

    /** How far down the top right corner's figures move to clear the status effects' icons there. */
    private static int effectsBelow(LocalPlayer player) {
        boolean helpful = false;
        boolean harmful = false;
        for (MobEffectInstance effect : player.getActiveEffects()) {
            if (effect.showIcon()) {
                if (effect.getEffect().value().isBeneficial()) {
                    helpful = true;
                } else {
                    harmful = true;
                }
            }
        }
        return harmful ? 2 * EFFECT_ROW : helpful ? EFFECT_ROW : 0;
    }

    /**
     * The top left corner: the speed set, the speed tape with its bug and trend arrow, and what the autopilot is
     * doing, as wide as the longest name of what it can be doing.
     */
    private static void speedPanel(Canvas canvas, int x0, int y0, Phase phase, Reading reading, double target) {
        int nameRoom = canvas.font.width(AP_OFF);
        for (Phase each : Phase.values()) {
            nameRoom = Math.max(nameRoom, canvas.font.width(phaseName(each)));
        }
        int width = 2 * PAD + Math.max(SPEED_TAPE_WIDTH + BUG_ROOM, ROW + ICON_GAP + nameRoom);
        int tapeTop = y0 + PAD + ROW + GAP;
        int phaseY = tapeTop + TAPE_HEIGHT + GAP;
        canvas.glass(x0, y0, x0 + width, phaseY + ROW + PAD);
        canvas.flush();
        canvas.icon(SPEED, x0 + PAD, y0 + PAD);
        canvas.icon(phaseIcon(phase), x0 + PAD, phaseY);

        Tape tape = new Tape(x0 + PAD, tapeTop, x0 + PAD + SPEED_TAPE_WIDTH, tapeTop + TAPE_HEIGHT, true, reading.airspeed(), PIXELS_PER_SPEED, 0);
        tape.draw(canvas, SPEED_TICK, SPEED_LABEL, Double.NaN);
        if (Math.abs(reading.trend()) >= TREND_MIN) {
            float x = tape.x1() + 2;
            float end = tape.clamp(tape.y(reading.airspeed() + reading.trend()));
            float way = Math.signum(tape.middle() - end);
            canvas.line(x, tape.middle(), x, end + way * 2, 1, GREEN);
            canvas.triangle(x, end, x - 2.5F, end + way * 3, x + 2.5F, end + way * 3, GREEN);
        }
        tape.box(canvas, String.valueOf(Math.round(reading.airspeed())));
        tape.bug(canvas, target);
        if (!Double.isNaN(target)) {
            canvas.text(String.valueOf(Math.round(target)), x0 + PAD + ROW + ICON_GAP, y0 + PAD + ROW / 2F, MAGENTA, Align.LEFT);
        }
        Component name = phase == Phase.OFF ? AP_OFF : phaseName(phase);
        canvas.text(name, x0 + PAD + ROW + ICON_GAP, phaseY + ROW / 2F, phase == Phase.OFF ? AMBER : GREEN, Align.LEFT);
        canvas.flush();
    }

    private static Component phaseName(Phase phase) {
        return Component.translatable(PHASE_KEY + phase.name().toLowerCase(Locale.ROOT));
    }

    /** The picture of what the autopilot is doing: the buttons' pictures where it has one, else the HUD's own. */
    private static Icon phaseIcon(Phase phase) {
        return switch (phase) {
            case OFF -> OFF;
            case TAKEOFF -> TAKE_OFF;
            case CLIMB -> CLIMB;
            case CRUISE -> CRUISE;
            case DESCENT -> DESCEND;
            case LAND -> LAND;
            case ROLLOUT -> ROLL_OUT;
        };
    }

    /**
     * The top right corner, standing in from the right edge: the height set, the height tape with its bug and the
     * ground, the climb bar beside it, and under them the height above the ground and the climb or sink.
     */
    private static void altitudePanel(Canvas canvas, int x1, int y0, Reading reading, double target) {
        int width = 2 * PAD + BUG_ROOM + ALTITUDE_TAPE_WIDTH + CLIMB_BAR_GAP + CLIMB_BAR_WIDTH;
        int x0 = x1 - width;
        int tapeTop = y0 + PAD + ROW + GAP;
        int radioY = tapeTop + TAPE_HEIGHT + GAP;
        int climbY = radioY + ROW;
        canvas.glass(x0, y0, x1, climbY + ROW + PAD);
        canvas.flush();
        canvas.icon(ALTITUDE, x0 + PAD, y0 + PAD);
        canvas.icon(GROUND_HEIGHT, x0 + PAD, radioY);

        int barX1 = x1 - PAD;
        int barX0 = barX1 - CLIMB_BAR_WIDTH;
        int tapeX1 = barX0 - CLIMB_BAR_GAP;
        Tape tape = new Tape(tapeX1 - ALTITUDE_TAPE_WIDTH, tapeTop, tapeX1, tapeTop + TAPE_HEIGHT, false, reading.altitude(), PIXELS_PER_BLOCK,
            Double.NEGATIVE_INFINITY);
        double ground = Double.isNaN(reading.radio()) ? Double.NaN : reading.altitude() - reading.radio();
        tape.draw(canvas, ALTITUDE_TICK, ALTITUDE_LABEL, ground);
        climbBar(canvas, barX0, barX1, tape, reading.climb());
        tape.box(canvas, String.valueOf(Math.round(reading.altitude())));
        tape.bug(canvas, target);

        float textX = x0 + PAD + ROW + ICON_GAP;
        if (!Double.isNaN(target)) {
            canvas.text(String.valueOf(Math.round(target)), textX, y0 + PAD + ROW / 2F, MAGENTA, Align.LEFT);
        }
        if (!Double.isNaN(reading.radio())) {
            canvas.text(String.valueOf(Math.round(reading.radio())), textX, radioY + ROW / 2F, WHITE, Align.LEFT);
        }
        double climb = reading.climb();
        float arrowX = x0 + PAD + ROW / 2F;
        float arrowY = climbY + ROW / 2F;
        if (climb > STEADY) {
            canvas.triangle(arrowX, arrowY - 4, arrowX - 4, arrowY + 3, arrowX + 4, arrowY + 3, GREEN);
        } else if (climb < -STEADY) {
            canvas.triangle(arrowX, arrowY + 4, arrowX + 4, arrowY - 3, arrowX - 4, arrowY - 3, AMBER);
        }
        canvas.text(String.format(Locale.ROOT, "%.1f", Math.abs(climb)), textX, arrowY, WHITE, Align.LEFT);
        canvas.flush();
    }

    /** The climb bar: up from its middle in green as the airliner climbs, down in amber as it sinks. */
    private static void climbBar(Canvas canvas, float x0, float x1, Tape tape, double climb) {
        float middle = tape.middle();
        float reach = tape.y1() - middle - 1;
        canvas.rect(x0, tape.y0(), x1, tape.y1(), TAPE);
        float end = middle - (float) (Mth.clamp(climb / CLIMB_BAR_RANGE, -1, 1) * reach);
        canvas.rect(x0 + 1, Math.min(middle, end), x1 - 1, Math.max(middle, end), climb >= 0 ? GREEN : AMBER);
        // Marks halfway to either end, and the middle across.
        for (int mark = -1; mark <= 1; mark += 2) {
            float y = middle + mark * reach / 2;
            canvas.rect(x0, y - 0.5F, x0 + 2, y + 0.5F, GREY);
        }
        canvas.rect(x0, middle - 0.5F, x1, middle + 0.5F, WHITE);
    }

    /**
     * At the top in the middle: the heading strip, the heading now in a box over its middle, the heading set as a bug
     * under it, and beside it the autopilot's mode, straight on or round a circle, with the heading or the circle's
     * radius set. Returns where the glass behind it ends.
     */
    private static int headingPanel(Canvas canvas, int middle, int y0, AutopilotBlockEntity autopilot, Reading reading, double target) {
        int top = y0 + PAD;
        int bottom = top + ROW;
        int y1 = bottom + (int) BUG_LENGTH + 1;
        float x0 = middle - HEADING_HALF_WIDTH;
        float x1 = middle + HEADING_HALF_WIDTH;
        canvas.glass(x0 - ICON_GAP - HEADING_SIDE - PAD, y0, x1 + ICON_GAP + HEADING_SIDE + PAD, y1);
        canvas.rect(x0, top, x1, bottom, TAPE);
        double heading = reading.heading();
        float span = HEADING_HALF_WIDTH / PIXELS_PER_DEGREE;
        for (int bearing = Mth.ceil((heading - span) / HEADING_TICK) * HEADING_TICK; bearing <= heading + span; bearing += HEADING_TICK) {
            float x = (float) (middle + (bearing - heading) * PIXELS_PER_DEGREE);
            float length = bearing % HEADING_LABEL == 0 ? LONG_TICK : SHORT_TICK;
            canvas.rect(x - 0.5F, bottom - length, x + 0.5F, bottom, WHITE);
        }
        // The numbers just past either end too, so they slide in cut off rather than appear whole.
        canvas.clip(x0, top, x1, bottom);
        int first = Mth.ceil((heading - span - HEADING_LABEL) / HEADING_LABEL) * HEADING_LABEL;
        for (int bearing = first; bearing <= heading + span + HEADING_LABEL; bearing += HEADING_LABEL) {
            float x = (float) (middle + (bearing - heading) * PIXELS_PER_DEGREE);
            canvas.text(headingLabel(Math.floorMod(bearing, 360)), x, top + 1 + TEXT_MIDDLE, WHITE, Align.CENTRE);
        }
        canvas.unclip();

        canvas.box(middle - HEADING_BOX_HALF, top, middle + HEADING_BOX_HALF, top + HEADING_BOX_HEIGHT);
        canvas.rect(middle - 0.5F, top + HEADING_BOX_HEIGHT, middle + 0.5F, bottom, YELLOW);
        if (!Double.isNaN(target)) {
            float x = (float) (middle + Mth.clamp(Mth.wrapDegrees(target - heading), -span, span) * PIXELS_PER_DEGREE);
            canvas.triangle(x, bottom, x - BUG_HALF, bottom + BUG_LENGTH, x + BUG_HALF, bottom + BUG_LENGTH, MAGENTA);
        }
        canvas.text(String.format(Locale.ROOT, "%03d", Math.round(heading) % 360), middle, top + HEADING_BOX_HEIGHT / 2F, WHITE, Align.CENTRE);
        if (autopilot.phase() != Phase.OFF) {
            canvas.flush();
            canvas.icon(lateralIcon(autopilot.lateral()), (int) x0 - ICON_GAP - ROW, top);
            String set = autopilot.lateral() != Lateral.HEADING ? String.valueOf(Math.round(autopilot.orbitRadius()))
                : !Double.isNaN(target) ? String.format(Locale.ROOT, "%03d", Math.round(target) % 360) : "";
            canvas.text(set, x1 + ICON_GAP, top + ROW / 2F, MAGENTA, Align.LEFT);
        }
        canvas.flush();
        return y1;
    }

    /** A heading strip's number: the compass point at north, east, south and west, else the tens of degrees. */
    private static Component headingLabel(int bearing) {
        return bearing % 90 == 0 ? Component.translatable(COMPASS_KEY + bearing / 45) : Component.literal(String.valueOf(bearing / 10));
    }

    /** The mode's picture, as on the settings' mode buttons. */
    private static Icon lateralIcon(Lateral lateral) {
        return switch (lateral) {
            case HEADING -> STRAIGHT;
            case ORBIT_LEFT -> CIRCLE_LEFT;
            case ORBIT_RIGHT -> CIRCLE_RIGHT;
        };
    }

    /**
     * The attitude indicator round a middle: sky over ground split by the horizon, which tilts against the bank and
     * drops as the nose comes up, the pitch ladder along it, all cut to the indicator; the bank scale round its top
     * and the pointer that turns with the sky; and the aircraft symbol fixed in its middle.
     */
    private static void attitudeIndicator(Canvas canvas, float cx, float cy, Reading reading) {
        float x0 = cx - ADI_HALF_WIDTH;
        float y0 = cy - ADI_HALF_HEIGHT;
        float x1 = cx + ADI_HALF_WIDTH;
        float y1 = cy + ADI_HALF_HEIGHT;
        canvas.frame(x0 - 1, y0 - 1, x1 + 1, y1 + 1, GLASS_EDGE);
        canvas.rect(x0, y0, x1, y1, SKY);
        double roll = Math.toRadians(reading.roll());
        // Toward the sky, on a screen whose y runs down; and along the horizon, left to right.
        float upX = (float) -Math.sin(roll);
        float upY = (float) -Math.cos(roll);
        float alongX = -upY;
        float alongY = upX;
        float drop = (float) (reading.pitch() * PIXELS_PER_PITCH);
        float px = cx - upX * drop;
        float py = cy - upY * drop;
        float[][] box = {{x0, y0}, {x0, y1}, {x1, y1}, {x1, y0}};
        canvas.polygon(below(box, px, py, upX, upY), GROUND);
        for (int degrees = -RUNG_LIMIT; degrees <= RUNG_LIMIT; degrees += RUNG_STEP) {
            float half = degrees == 0 ? 2 * ADI_HALF_WIDTH : degrees % (2 * RUNG_STEP) == 0 ? 10 : 5;
            float rx = px + upX * degrees * PIXELS_PER_PITCH;
            float ry = py + upY * degrees * PIXELS_PER_PITCH;
            float[] rung = clip(rx - alongX * half, ry - alongY * half, rx + alongX * half, ry + alongY * half, x0, y0, x1, y1);
            if (rung != null) {
                canvas.line(rung[0], rung[1], rung[2], rung[3], 1, WHITE);
            }
        }
        for (int mark : BANK_MARKS) {
            float length = mark % 30 == 0 ? 4 : 2.5F;
            float sx = (float) Math.sin(Math.toRadians(mark));
            float sy = (float) -Math.cos(Math.toRadians(mark));
            canvas.line(cx + sx * BANK_RADIUS, cy + sy * BANK_RADIUS, cx + sx * (BANK_RADIUS - length), cy + sy * (BANK_RADIUS - length), 1, WHITE);
        }
        int pointer = Math.abs(reading.roll()) > BANK_WARNING ? AMBER : WHITE;
        float tip = BANK_RADIUS - 4.5F;
        float base = tip - 4;
        canvas.triangle(cx + upX * tip, cy + upY * tip, cx + upX * base - alongX * 3, cy + upY * base - alongY * 3,
            cx + upX * base + alongX * 3, cy + upY * base + alongY * 3, pointer);
        canvas.rect(cx - SYMBOL_HALF, cy - 1, cx - SYMBOL_GAP, cy + 1, YELLOW);
        canvas.rect(cx + SYMBOL_GAP, cy - 1, cx + SYMBOL_HALF, cy + 1, YELLOW);
        canvas.rect(cx - 1, cy - 1, cx + 1, cy + 1, YELLOW);
        canvas.flush();
    }

    /** The part of a convex polygon on the far side of a line from its up direction (Sutherland-Hodgman). */
    private static List<float[]> below(float[][] polygon, float px, float py, float upX, float upY) {
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < polygon.length; i++) {
            float[] a = polygon[i];
            float[] b = polygon[(i + 1) % polygon.length];
            float da = (a[0] - px) * upX + (a[1] - py) * upY;
            float db = (b[0] - px) * upX + (b[1] - py) * upY;
            if (da <= 0) {
                out.add(a);
            }
            if (da * db < 0) {
                float t = da / (da - db);
                out.add(new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t});
            }
        }
        return out;
    }

    /** The part of a line inside a box (Liang-Barsky), as its two ends; null if none of it is. */
    @Nullable
    private static float[] clip(float ax, float ay, float bx, float by, float x0, float y0, float x1, float y1) {
        float dx = bx - ax;
        float dy = by - ay;
        float t0 = 0;
        float t1 = 1;
        float[] p = {-dx, dx, -dy, dy};
        float[] q = {ax - x0, x1 - ax, ay - y0, y1 - ay};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0) {
                if (q[i] < 0) {
                    return null;
                }
                continue;
            }
            float t = q[i] / p[i];
            if (p[i] < 0) {
                t0 = Math.max(t0, t);
            } else {
                t1 = Math.min(t1, t);
            }
        }
        return t0 > t1 ? null : new float[]{ax + dx * t0, ay + dy * t0, ax + dx * t1, ay + dy * t1};
    }

    /**
     * A moving tape: values up it, the one now level with its middle, marked along the side toward the middle of the
     * screen and numbered on the other, from its lowest value on.
     */
    private record Tape(float x0, float y0, float x1, float y1, boolean marksRight, double value, float pixelsPer, double lowest) {
        float middle() {
            return (y0 + y1) / 2;
        }

        /** Where a value is up the tape, however far beyond its ends. */
        float y(double of) {
            return (float) (middle() - (of - value) * pixelsPer);
        }

        float clamp(float y) {
            return Mth.clamp(y, y0, y1);
        }

        /** The edge the marks are along, and which way is out from it. */
        float edge() {
            return marksRight ? x1 : x0;
        }

        float out() {
            return marksRight ? 1 : -1;
        }

        /**
         * The tape and its marks, the ground as a brown band up from the bottom where the value the ground stands at
         * (NaN for none) is on it; then its numbers, cut to it.
         */
        void draw(Canvas canvas, int tick, int label, double ground) {
            canvas.rect(x0, y0, x1, y1, TAPE);
            if (!Double.isNaN(ground) && y(ground) < y1) {
                float top = Math.max(y0, y(ground));
                canvas.rect(x0, top, x1, y1, GROUND);
                canvas.rect(x0, top, x1, top + 1, AMBER);
            }
            float reach = (y1 - y0) / 2 / pixelsPer;
            for (int mark = Mth.ceil(Math.max(lowest, value - reach) / tick) * tick; mark <= value + reach; mark += tick) {
                float y = y(mark);
                float length = mark % label == 0 ? LONG_TICK : SHORT_TICK;
                canvas.rect(edge() - out() * length, y - 0.5F, edge(), y + 0.5F, WHITE);
            }
            canvas.clip(x0, y0, x1, y1);
            float textX = edge() - out() * (LONG_TICK + 2);
            int first = Mth.ceil(Math.max(lowest, value - reach - label) / label) * label;
            for (int mark = first; mark <= value + reach + label; mark += label) {
                canvas.text(String.valueOf(mark), textX, y(mark), WHITE, marksRight ? Align.RIGHT : Align.LEFT);
            }
            canvas.unclip();
        }

        /** The value set, as a magenta bug pointing at the tape from beside its marks, kept to the tape's ends. */
        void bug(Canvas canvas, double target) {
            if (Double.isNaN(target)) {
                return;
            }
            float y = clamp(y(target));
            float tip = edge() + out();
            float base = tip + out() * BUG_LENGTH;
            canvas.triangle(tip, y, base, y - BUG_HALF, base, y + BUG_HALF, MAGENTA);
        }

        /** The value now in a box over the tape's middle, with the yellow mark it is read against beside it. */
        void box(Canvas canvas, String figure) {
            float middle = middle();
            canvas.triangle(edge(), middle, edge() + out() * 4, middle - 3, edge() + out() * 4, middle + 3, YELLOW);
            canvas.box(x0, middle - BOX_HALF, x1, middle + BOX_HALF);
            canvas.text(figure, (x0 + x1) / 2, middle, WHITE, Align.CENTRE);
        }
    }

    /**
     * Drawing onto the GUI a batch at a time: shapes and text go into the GUI's shared buffer in the order they are
     * drawn, each run of shapes or of text drawn in one go as the next begins, rather than each shape on its own as
     * {@link GuiGraphics#fill} does. Pictures are drawn at once instead, so whatever they go over is flushed first.
     * Positions are in GUI pixels, fractions and all; text lands on whole screen pixels, so it stays sharp at any GUI
     * scale. Everything is drawn as see-through as the HUD is faded in.
     */
    private static final class Canvas {
        /** Text this faint is not drawn: the font draws a colour with next to no alpha fully opaque. */
        private static final int FAINTEST_TEXT = 0x04000000;

        private final GuiGraphics graphics;
        private final Font font;
        private final float guiScale;
        private final Matrix4f matrix;
        private final float fade;

        Canvas(GuiGraphics graphics, Font font, float guiScale, float fade) {
            this.graphics = graphics;
            this.font = font;
            this.guiScale = guiScale;
            this.fade = fade;
            matrix = graphics.pose().last().pose();
        }

        /** A colour as faded in. */
        private int faded(int colour) {
            return Math.round((colour >>> 24) * fade) << 24 | colour & 0xFFFFFF;
        }

        /** Dark glass, edged a shade lighter. */
        void glass(float x0, float y0, float x1, float y1) {
            frame(x0, y0, x1, y1, GLASS_EDGE);
            rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, GLASS);
        }

        /** A black box framed in white, for a figure. */
        void box(float x0, float y0, float x1, float y1) {
            frame(x0, y0, x1, y1, WHITE);
            rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, BOX);
        }

        /** A frame a pixel wide inside a rectangle, its inside left clear: see-through colours would add up there. */
        void frame(float x0, float y0, float x1, float y1, int colour) {
            rect(x0, y0, x1, y0 + 1, colour);
            rect(x0, y1 - 1, x1, y1, colour);
            rect(x0, y0 + 1, x0 + 1, y1 - 1, colour);
            rect(x1 - 1, y0 + 1, x1, y1 - 1, colour);
        }

        void rect(float x0, float y0, float x1, float y1, int colour) {
            quad(x0, y0, x0, y1, x1, y1, x1, y0, colour);
        }

        void triangle(float ax, float ay, float bx, float by, float cx, float cy, int colour) {
            quad(ax, ay, bx, by, cx, cy, cx, cy, colour);
        }

        /** A line as a thin quad. */
        void line(float ax, float ay, float bx, float by, float width, int colour) {
            float length = (float) Math.hypot(bx - ax, by - ay);
            if (length < 1e-4F) {
                return;
            }
            float nx = -(by - ay) / length * width / 2;
            float ny = (bx - ax) / length * width / 2;
            quad(ax - nx, ay - ny, bx - nx, by - ny, bx + nx, by + ny, ax + nx, ay + ny, colour);
        }

        /** A convex polygon as a fan of quads. */
        void polygon(List<float[]> points, int colour) {
            for (int i = 1; i + 1 < points.size(); i += 2) {
                float[] a = points.getFirst();
                float[] b = points.get(i);
                float[] c = points.get(i + 1);
                float[] d = i + 2 < points.size() ? points.get(i + 2) : c;
                quad(a[0], a[1], b[0], b[1], c[0], c[1], d[0], d[1], colour);
            }
        }

        /** A convex quad, its corners taken round the way the GUI's own shapes go, as the other way is culled. */
        void quad(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy, int colour) {
            float turning = ax * by - bx * ay + bx * cy - cx * by + cx * dy - dx * cy + dx * ay - ax * dy;
            VertexConsumer shapes = graphics.bufferSource().getBuffer(RenderType.gui());
            if (turning > 0) {
                vertex(shapes, dx, dy, colour);
                vertex(shapes, cx, cy, colour);
                vertex(shapes, bx, by, colour);
                vertex(shapes, ax, ay, colour);
            } else {
                vertex(shapes, ax, ay, colour);
                vertex(shapes, bx, by, colour);
                vertex(shapes, cx, cy, colour);
                vertex(shapes, dx, dy, colour);
            }
        }

        private void vertex(VertexConsumer shapes, float x, float y, int colour) {
            shapes.addVertex(matrix, x, y, 0).setColor(faded(colour));
        }

        void text(String text, float x, float y, int colour, Align align) {
            text(Component.literal(text), x, y, colour, align);
        }

        /** Text placed against x as aligned, centred on y. */
        void text(Component text, float x, float y, int colour, Align align) {
            int drawn = faded(colour);
            if (Integer.compareUnsigned(drawn, FAINTEST_TEXT) < 0) {
                return;
            }
            float left = switch (align) {
                case LEFT -> x;
                case CENTRE -> x - font.width(text) / 2F;
                case RIGHT -> x - font.width(text);
            };
            font.drawInBatch(text, snap(left), snap(y - TEXT_MIDDLE), drawn, false, matrix, graphics.bufferSource(), Font.DisplayMode.NORMAL, 0,
                LightTexture.FULL_BRIGHT);
        }

        private float snap(float at) {
            return Math.round(at * guiScale) / guiScale;
        }

        /** A picture, blended in as faded in: drawn at once, pictures are not blended by themselves. */
        void icon(Icon icon, int x, int y) {
            RenderSystem.enableBlend();
            graphics.setColor(1F, 1F, 1F, fade);
            graphics.blit(icon.texture(), x, y, Icon.SIZE, Icon.SIZE, 0, 0, Icon.SIZE, Icon.SIZE, Icon.SIZE, Icon.SIZE);
            graphics.setColor(1F, 1F, 1F, 1F);
            RenderSystem.disableBlend();
        }

        /** Draws whatever is still waiting in a batch. */
        void flush() {
            graphics.flush();
        }

        /** Flushes, then cuts what is drawn from now until {@link #unclip} to a box. */
        void clip(float x0, float y0, float x1, float y1) {
            graphics.flush();
            graphics.enableScissor(Mth.floor(x0), Mth.floor(y0), Mth.ceil(x1), Mth.ceil(y1));
        }

        void unclip() {
            graphics.flush();
            graphics.disableScissor();
        }
    }
}
