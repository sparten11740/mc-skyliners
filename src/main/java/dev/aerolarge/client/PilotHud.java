package dev.aerolarge.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.AerolargeClientConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.AutopilotBlockEntity.Lateral;
import dev.aerolarge.autopilot.AutopilotBlockEntity.Phase;
import dev.aerolarge.client.HudLayout.Box;
import dev.aerolarge.client.HudLayout.Placement;
import dev.aerolarge.client.PanelArt.Icon;
import dev.aerolarge.client.ScreenDrawing.Align;
import dev.aerolarge.item.PilotHelmetItem;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import org.joml.Matrix4f;

/**
 * The pilots' head-up display, shown by the visor of a pilot helmet: while the local player wears one on the flight
 * deck of an assembled airliner, within reach of its autopilot (as from either pilot's seat), how it flies, drawn as a
 * real HUD is, in thin green lines and figures straight over the view, edged dark so they read against bright sky, with
 * what the autopilot is set to in magenta, bent onto the visor's curve ({@link HelmetVisor}). It comes in parts,
 * placed by {@link HudLayout} out of the way of the rest of the HUD and of the middle of the view. Round the crosshair
 * a slim horizon line and the pitch ladder, which tilt against the bank and drop as the nose comes up (the horizon
 * amber once banked as far as the "bank angle" callout), and the flight path marker, where the airliner is going. By
 * the screen's left edge the speed on a narrow moving tape, the speed set as a magenta bug and a trend arrow to the
 * speed ten seconds on, and beside it a picture of what the autopilot is doing, with its name. By its right edge the
 * height the autopilot holds (its own block's) on a moving tape, the height set as a magenta bug, the ground as an
 * amber line rising up it once near, a bar beyond it that climbs green or sinks amber, and beside that the wheels'
 * height above the ground and the climb or sink with an arrow. At the top of the screen a heading scale, the heading
 * in a box over it and the heading set as a magenta bug, with the autopilot's mode (straight on or round a circle)
 * beside it.
 * Pictures and colours carry it, and numbers only where the number is the point, so that it works for players who
 * cannot read yet.
 *
 * <p>It is drawn in units of its own, a screen pixel each at GUI scale 2 and growing with the GUI scale, times
 * {@code pilotHudScale} from the client config: about half as big as the rest of the HUD. The airliner's attitude and
 * height come from its pose as the client draws it, so the HUD moves as smoothly as the airliner does; the autopilot's
 * phase, mode and targets from what its block sends the clients. Being on the visor, it shows only in first person. It
 * hides with the rest of the HUD (F1), behind any screen, under the debug screen and while looking through an outside
 * camera, and is off altogether with {@code pilotHud} off in the client config. As it comes on it fades in, with a
 * beep when it is the helmet going on that turns it on; walking back within reach of the autopilot with the helmet on,
 * it only fades in.
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
    /** Banked further than this, in degrees, the horizon turns amber, as the callouts say "bank angle" then. */
    private static final double BANK_WARNING = 35;
    /** How long the HUD takes to fade in as it comes on, in milliseconds. */
    private static final float FADE_IN_MILLIS = 600;
    /** How loud the beep of the HUD coming on is, as a share of full volume: about as loud as a button's click. */
    private static final float BEEP_VOLUME = 0.4F;

    /**
     * The least room the HUD needs on the screen, in its units: on a smaller one it is drawn smaller, so that the
     * horizon keeps clear of the hearts and the action bar under it.
     */
    private static final float ROOM_WIDTH = 300;
    private static final float ROOM_HEIGHT = 280;
    /** How far below a boss bar's top it ends, in GUI pixels. */
    private static final int BOSS_BAR_HEIGHT = 5;
    /** How far in from the screen's left and right edges the speed and the height stand, in screen pixels. */
    private static final float EDGE_PADDING = 40;
    /**
     * Screen pixels per unit of the HUD for each step of the GUI scale, at a {@code pilotHudScale} of 1: half the GUI's
     * own size. Never fewer than the least, below which its text could not be read.
     */
    private static final float PIXELS_PER_GUI_SCALE = 0.5F;
    private static final float LEAST_PIXELS_PER_UNIT = 1;
    /**
     * How opaque the HUD is laid over the view, see-through as a HUD's light is; and how dark the thin edge round its
     * lines and figures is, enough to read them against bright sky and white skin without a panel behind them.
     */
    private static final float OPACITY = 0.72F;
    private static final float OUTLINE = 0.4F;

    /** Half the speed and height tapes' height: slim, so they keep clear of the chat and the subtitles on a short screen. */
    private static final float TAPE_HALF_HEIGHT = 28;
    /** The room for a tape's numbers, on the outer side of its spine; its marks are on the inner side. */
    private static final float TAPE_NUMBERS = 30;
    /** The gap between a tape's numbers (or the climb bar) and the pictures beside them. */
    private static final float COLUMN_GAP = 6;
    /** How far from a tape's or a scale's ends its marks and numbers fade out, so they slide in and out softly. */
    private static final float END_FADE = 8;
    /** Units per block a second on the speed tape and per block on the height tape. */
    private static final float UNITS_PER_SPEED = 1.2F;
    private static final float UNITS_PER_BLOCK = 0.8F;
    /** Each tape is marked every so many blocks a second or blocks, and numbered every so many. */
    private static final int SPEED_TICK = 5;
    private static final int SPEED_LABEL = 10;
    private static final int ALTITUDE_TICK = 10;
    private static final int ALTITUDE_LABEL = 20;
    /** Marks along a scale: a numbered one's length and the others'. */
    private static final float LONG_TICK = 4;
    private static final float SHORT_TICK = 2;
    /** The gap between a scale's marks and its numbers. */
    private static final float LABEL_GAP = 3;
    /** The bug: how far it reaches from the spine, and half its breadth. */
    private static final float BUG_LENGTH = 4;
    private static final float BUG_HALF = 3;
    /** The box with the figure now in the middle of a tape or over the heading scale: half its height. */
    private static final float BOX_HALF = 5.5F;
    /** The climb bar beyond the height tape's numbers: its gap from them, half its width, and the climb at its ends. */
    private static final float CLIMB_BAR_GAP = 3;
    private static final float CLIMB_BAR_HALF = 1;
    private static final double CLIMB_BAR_RANGE = 8;
    /** The ground on the height tape: how far its line reaches in from the spine, and the spacing of its hatching. */
    private static final float GROUND_WIDTH = 7;
    private static final float GROUND_HATCH = 4;
    /** The gap between a tape and what is above or below it, and between a picture and its words. */
    private static final float GAP = 4;
    private static final float ICON_GAP = 3;

    /** The heading scale: half its width, and units per degree. */
    private static final float HEADING_HALF_WIDTH = 50;
    private static final float UNITS_PER_DEGREE = 1.6F;
    /** It is marked every 5 degrees and numbered every 10. */
    private static final int HEADING_TICK = 5;
    private static final int HEADING_LABEL = 10;
    /** The heading box's half width, and how far above the scale's line its middle is. */
    private static final float HEADING_BOX_HALF = 11;
    private static final float HEADING_BOX_Y = 22;

    /** Units per degree of pitch on the ladder. */
    private static final float UNITS_PER_PITCH = 2;
    /** The horizon line: half its length, and the gap in its middle, clear of the crosshair. */
    private static final float HORIZON_HALF = 40;
    private static final float HORIZON_GAP = 10;
    /** Pitch ladder rungs every so many degrees, as far as this either way. */
    private static final int RUNG_STEP = 5;
    private static final int RUNG_LIMIT = 30;
    /** A rung's half length every 10 degrees and between, the gap in its middle, and the ticks at its ends. */
    private static final float LONG_RUNG = 11;
    private static final float SHORT_RUNG = 7;
    private static final float RUNG_GAP = 4;
    private static final float RUNG_TICK = 2;
    /** How far beyond a rung's end the middle of its degrees is, and half their width, two figures. */
    private static final float RUNG_LABEL = LABEL_GAP + 5;
    private static final float RUNG_LABEL_HALF = 6;
    /** How opaque the rungs are beside the horizon line, so the ladder stays faint round the crosshair. */
    private static final float RUNG_SHARE = 0.6F;
    /**
     * The ladder only reaches this far from the middle, fading out over its last stretch, so it keeps close round the
     * crosshair: about 15 degrees of pitch either way.
     */
    private static final float LADDER_REACH = 40;
    private static final float LADDER_FADE = 10;
    /** The flight path marker: its ring's radius, its wings' and fin's length. */
    private static final float MARKER_RADIUS = 3;
    private static final float MARKER_WING = 4;
    private static final float MARKER_FIN = 3;
    /** The marker's ring, drawn as so many straight pieces. */
    private static final int MARKER_SIDES = 12;
    /** Where the middle of a line of text is, in font pixels below its top: half its capitals' height. */
    private static final float TEXT_MIDDLE = 3.5F;
    /** The widest figures the HUD shows beside a picture or a scale: four digits. */
    private static final float FIGURES = 24;

    /**
     * How far each part reaches round its origin, in units. The horizon round the middle of the screen, as far as its
     * line or the furthest rung's degrees reach, turned any way. The height round the middle of its tape's spine: the
     * ground's hatching and the bug inside it, then its numbers, the climb bar, and the pictures with their figures; up
     * to the height set over its numbers and down to a bug at the tape's end. The heading round the middle of its
     * scale's line: the mode's picture left of it, the heading or radius set right of it, the heading's box over it and
     * the bug under it. (The speed's, as wide as the names of what the autopilot does, is {@link #speedReach}.)
     */
    private static final float ATTITUDE_RADIUS = Math.max(HORIZON_HALF, (float) Math.hypot(LADDER_REACH, LONG_RUNG + RUNG_LABEL) + RUNG_LABEL_HALF) + 1;
    private static final Box ATTITUDE_REACH = new Box(-ATTITUDE_RADIUS, -ATTITUDE_RADIUS, ATTITUDE_RADIUS, ATTITUDE_RADIUS);
    private static final float TAPE_TOP = -(TAPE_HALF_HEIGHT + GAP + 2 * TEXT_MIDDLE + 2);
    private static final float TAPE_BOTTOM = TAPE_HALF_HEIGHT + BUG_HALF;
    private static final float CLIMB_BAR_X = TAPE_NUMBERS + CLIMB_BAR_GAP;
    private static final float CLIMB_COLUMN_X = CLIMB_BAR_X + 2 * CLIMB_BAR_HALF + 1 + COLUMN_GAP;
    private static final Box ALTITUDE_REACH = new Box(-GROUND_WIDTH - 1, TAPE_TOP, CLIMB_COLUMN_X + Icon.SIZE + ICON_GAP + FIGURES, TAPE_BOTTOM);
    private static final Box HEADING_REACH = new Box(-HEADING_HALF_WIDTH - ICON_GAP - Icon.SIZE, -HEADING_BOX_Y - BOX_HALF - 1,
        HEADING_HALF_WIDTH + ICON_GAP + FIGURES, 1 + BUG_LENGTH + 1);

    /** The symbology's green, a HUD's, bright enough to read against the sky once laid on see-through. */
    private static final int HUD = 0xFF70FFA0;
    /** What the autopilot is set to: a lighter magenta than the flight displays', to show up on the view. */
    private static final int TARGET = 0xFFFF80F4;
    private static final int WARNING = AutopilotScreenRenderer.AMBER;

    private static final Icon GROUND_HEIGHT = Icon.of("autopilot/ground_height");
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
    /** Where the boss bars drawn this frame end, in GUI pixels; the top of the screen while there are none. */
    private static int bossBarsBottom;

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
        /**
         * Degrees above the horizon it is going. Its speed along the nose stands in for its whole speed, which is near
         * enough as it flies nose first; standing still, it is level.
         */
        double flightPath() {
            double along = Math.sqrt(Math.max(0, airspeed * airspeed - climb * climb));
            return along < 1e-3 && Math.abs(climb) < 1e-3 ? 0 : Math.toDegrees(Math.atan2(climb, along));
        }
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

    /**
     * Takes in where each boss bar drawn this frame ends, so that the heading goes under them. They are drawn before
     * the HUD, whose layer is over theirs.
     */
    public static void bossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        bossBarsBottom = Math.max(bossBarsBottom, event.getY() + BOSS_BAR_HEIGHT);
    }

    /** Draws the HUD onto the visor, if it is on and nothing hides it. */
    public static void overlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        // Taken each frame, drawn or not, so that it is never left over from an earlier one.
        int bossBars = bossBarsBottom;
        bossBarsBottom = 0;
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
        Window window = minecraft.getWindow();
        float pixelsPerUnit = pixelsPerUnit(window);
        float unit = pixelsPerUnit / (float) window.getGuiScale();
        Font font = minecraft.font;
        Box speedReach = speedReach(font);
        ChatComponent chat = minecraft.gui.getChat();
        VisorCurve curve = new VisorCurve(graphics.guiWidth(), graphics.guiHeight(), unit);
        HudLayout layout = new HudLayout(curve, chat.getWidth(), chat.getHeight(), minecraft.options.showSubtitles().get(), bossBars,
            EDGE_PADDING / (float) window.getGuiScale());
        Placement at = layout.place(speedReach.scaled(unit), ALTITUDE_REACH.scaled(unit), HEADING_REACH.scaled(unit), ATTITUDE_REACH.scaled(unit));
        HelmetVisor.draw(graphics, curve, OPACITY * fade, OUTLINE * fade, List.of(
            new HelmetVisor.Part(at.attitude(), ATTITUDE_REACH, painter(graphics, font, pixelsPerUnit, canvas -> attitude(canvas, reading))),
            new HelmetVisor.Part(at.speed(), speedReach,
                painter(graphics, font, pixelsPerUnit, canvas -> speedTape(canvas, autopilot.phase(), reading, targets.speed(), speedReach.left()))),
            new HelmetVisor.Part(at.altitude(), ALTITUDE_REACH,
                painter(graphics, font, pixelsPerUnit, canvas -> altitudeTape(canvas, reading, targets.altitude()))),
            new HelmetVisor.Part(at.heading(), HEADING_REACH,
                painter(graphics, font, pixelsPerUnit, canvas -> headingScale(canvas, autopilot, reading, targets.heading())))));
    }

    /** A part's painter: drawing onto a canvas through the GUI's pose as the visor has set it up for the part. */
    private static HelmetVisor.Painter painter(GuiGraphics graphics, Font font, float pixelsPerUnit, Consumer<Canvas> drawing) {
        return () -> {
            Canvas canvas = new Canvas(graphics, font, pixelsPerUnit);
            drawing.accept(canvas);
            canvas.flush();
        };
    }

    /**
     * Whether something hides the HUD while it is on: the rest of the HUD hidden, a screen open, or the player not
     * looking out through their own eyes, and so not through the visor: in third person, or through an outside camera.
     */
    private static boolean hidden(Minecraft minecraft, LocalPlayer player) {
        return minecraft.options.hideGui || minecraft.screen != null || minecraft.getDebugOverlay().showDebugScreen()
            || !minecraft.options.getCameraType().isFirstPerson() || PassengerCameras.looking() || minecraft.getCameraEntity() != player;
    }

    /** Screen pixels per unit of the HUD: as the GUI scale and the player's size setting make it, but all on screen. */
    private static float pixelsPerUnit(Window window) {
        double size = window.getGuiScale() * PIXELS_PER_GUI_SCALE * AerolargeClientConfig.PILOT_HUD_SCALE.get();
        float wanted = Math.max(LEAST_PIXELS_PER_UNIT, (float) size);
        return Math.min(wanted, Math.min(window.getWidth() / ROOM_WIDTH, window.getHeight() / ROOM_HEIGHT));
    }

    /**
     * Round the middle: the horizon line and the pitch ladder, tilting against the bank and dropping as the nose comes
     * up, each rung fading out as it nears the ladder's reach; then the flight path marker, upright, as far above or
     * below the middle as the airliner is going above or below where its nose points.
     */
    private static void attitude(Canvas canvas, Reading reading) {
        double roll = Math.toRadians(reading.roll());
        // Toward the sky, on a screen whose y runs down; and along the horizon, left to right.
        float upX = (float) -Math.sin(roll);
        float upY = (float) -Math.cos(roll);
        float alongX = -upY;
        float alongY = upX;
        float drop = (float) (reading.pitch() * UNITS_PER_PITCH);
        for (int degrees = -RUNG_LIMIT; degrees <= RUNG_LIMIT; degrees += RUNG_STEP) {
            float x = upX * (degrees * UNITS_PER_PITCH - drop);
            float y = upY * (degrees * UNITS_PER_PITCH - drop);
            float reach = degrees == 0 ? HORIZON_HALF : degrees % (2 * RUNG_STEP) == 0 ? LONG_RUNG : SHORT_RUNG;
            float gap = degrees == 0 ? HORIZON_GAP : RUNG_GAP;
            int colour = degrees == 0 && Math.abs(reading.roll()) > BANK_WARNING ? WARNING : HUD;
            float shown = Mth.clamp((LADDER_REACH - (float) Math.hypot(x, y)) / LADDER_FADE, 0, 1);
            int faded = fade(colour, degrees == 0 ? shown : shown * RUNG_SHARE);
            if (faded >>> 24 == 0) {
                continue;
            }
            for (int side = -1; side <= 1; side += 2) {
                float startX = x + side * alongX * gap;
                float startY = y + side * alongY * gap;
                float endX = x + side * alongX * reach;
                float endY = y + side * alongY * reach;
                if (degrees < 0) {
                    // Below the horizon the rungs are dashed, as on a real HUD, so up and down cannot be mistaken.
                    float midX = (startX + endX) / 2;
                    float midY = (startY + endY) / 2;
                    canvas.line(startX, startY, midX - side * alongX, midY - side * alongY, 1, faded);
                    canvas.line(midX + side * alongX, midY + side * alongY, endX, endY, 1, faded);
                } else {
                    canvas.line(startX, startY, endX, endY, 1, faded);
                }
                if (degrees != 0 && degrees % (2 * RUNG_STEP) == 0) {
                    // Ticks at its ends pointing toward the horizon, and its degrees beside them.
                    float toward = -Math.signum(degrees) * RUNG_TICK;
                    canvas.line(endX, endY, endX + upX * toward, endY + upY * toward, 1, faded);
                    canvas.text(String.valueOf(Math.abs(degrees)), endX + side * alongX * RUNG_LABEL, endY + side * alongY * RUNG_LABEL, faded,
                        Align.CENTRE);
                }
            }
        }
        float rise = (float) Mth.clamp((reading.flightPath() - reading.pitch()) * UNITS_PER_PITCH, -LADDER_REACH, LADDER_REACH);
        float markerX = upX * rise;
        float markerY = upY * rise;
        canvas.ring(markerX, markerY, MARKER_RADIUS, HUD);
        canvas.line(markerX - MARKER_RADIUS - MARKER_WING, markerY, markerX - MARKER_RADIUS, markerY, 1, HUD);
        canvas.line(markerX + MARKER_RADIUS, markerY, markerX + MARKER_RADIUS + MARKER_WING, markerY, 1, HUD);
        canvas.line(markerX, markerY - MARKER_RADIUS, markerX, markerY - MARKER_RADIUS - MARKER_FIN, 1, HUD);
    }

    /**
     * The speed, round the middle of its tape's spine: the tape, its bug and trend arrow, the speed set over its
     * numbers, and out beyond them, as far as the given edge, a picture of what the autopilot is doing over its name.
     */
    private static void speedTape(Canvas canvas, Phase phase, Reading reading, double target, float left) {
        Tape tape = new Tape(0, 1, reading.airspeed(), UNITS_PER_SPEED, 0);
        tape.draw(canvas, SPEED_TICK, SPEED_LABEL);
        if (Math.abs(reading.trend()) >= TREND_MIN) {
            float x = tape.spine() + 2;
            float end = tape.clamp(tape.y(reading.airspeed() + reading.trend()));
            float way = -Math.signum(end);
            canvas.line(x, 0, x, end + way * 2, 1, HUD);
            canvas.triangle(x, end, x - 2, end + way * 3, x + 2, end + way * 3, HUD);
        }
        tape.bug(canvas, target);
        tape.readout(canvas, String.valueOf(Math.round(reading.airspeed())));
        tape.set(canvas, target);

        float middle = (left + tape.spine() - TAPE_NUMBERS - COLUMN_GAP) / 2;
        canvas.flush();
        canvas.icon(Icon.phase(phase), middle - Icon.SIZE / 2F, -Icon.SIZE);
        canvas.text(phaseName(phase), middle, ICON_GAP + TEXT_MIDDLE, phase == Phase.OFF ? WARNING : HUD, Align.CENTRE);
    }

    /**
     * How far the speed's part reaches round the middle of its tape's spine: out to its bug and trend arrow on the
     * inner side, and on the outer side past its numbers to a column as wide as the picture or the widest name of what
     * the autopilot does.
     */
    private static Box speedReach(Font font) {
        int widest = Icon.SIZE;
        for (Phase phase : Phase.values()) {
            widest = Math.max(widest, font.width(phaseName(phase)));
        }
        return new Box(-(TAPE_NUMBERS + COLUMN_GAP + widest), TAPE_TOP, 1 + BUG_LENGTH + 1, TAPE_BOTTOM);
    }

    private static Component phaseName(Phase phase) {
        return phase == Phase.OFF ? AP_OFF : Component.translatable(PHASE_KEY + phase.name().toLowerCase(Locale.ROOT));
    }

    /**
     * The height, round the middle of its tape's spine: the tape with its bug and the ground, the climb bar beyond its
     * numbers, the height set over them, and out beyond the climb bar the wheels' height above the ground over the
     * climb or sink.
     */
    private static void altitudeTape(Canvas canvas, Reading reading, double target) {
        Tape tape = new Tape(0, -1, reading.altitude(), UNITS_PER_BLOCK, Double.NEGATIVE_INFINITY);
        tape.draw(canvas, ALTITUDE_TICK, ALTITUDE_LABEL);
        if (!Double.isNaN(reading.radio())) {
            tape.ground(canvas, reading.altitude() - reading.radio());
        }
        climbBar(canvas, CLIMB_BAR_X, reading.climb());
        tape.bug(canvas, target);
        tape.readout(canvas, String.valueOf(Math.round(reading.altitude())));
        tape.set(canvas, target);

        float x = CLIMB_COLUMN_X;
        float radioY = -Icon.SIZE;
        float climbY = 0;
        float textX = x + Icon.SIZE + ICON_GAP;
        double climb = reading.climb();
        float arrowX = x + Icon.SIZE / 2F;
        float arrowY = climbY + Icon.SIZE / 2F;
        if (climb > STEADY) {
            canvas.triangle(arrowX, arrowY - 4, arrowX - 4, arrowY + 3, arrowX + 4, arrowY + 3, HUD);
        } else if (climb < -STEADY) {
            canvas.triangle(arrowX, arrowY + 4, arrowX + 4, arrowY - 3, arrowX - 4, arrowY - 3, WARNING);
        }
        canvas.flush();
        canvas.icon(GROUND_HEIGHT, x, radioY);
        if (!Double.isNaN(reading.radio())) {
            canvas.text(String.valueOf(Math.round(reading.radio())), textX, radioY + Icon.SIZE / 2F, HUD, Align.LEFT);
        }
        canvas.text(String.format(Locale.ROOT, "%.1f", Math.abs(climb)), textX, arrowY, HUD, Align.LEFT);
    }

    /** The climb bar: a thin line up from its middle as the airliner climbs, down in amber as it sinks. */
    private static void climbBar(Canvas canvas, float x, double climb) {
        float end = -(float) (Mth.clamp(climb / CLIMB_BAR_RANGE, -1, 1) * TAPE_HALF_HEIGHT);
        canvas.rect(x - CLIMB_BAR_HALF, Math.min(0, end), x + CLIMB_BAR_HALF, Math.max(0, end), climb >= 0 ? HUD : WARNING);
        canvas.rect(x - 2 * CLIMB_BAR_HALF - 1, -0.5F, x + 2 * CLIMB_BAR_HALF + 1, 0.5F, HUD);
    }

    /**
     * The heading, round the middle of its scale's line: the scale, the heading now in a box over its middle and a
     * caret under it, the heading set as a magenta bug, and beside it the autopilot's mode, straight on or round a
     * circle, with the heading or the circle's radius set.
     */
    private static void headingScale(Canvas canvas, AutopilotBlockEntity autopilot, Reading reading, double target) {
        double heading = reading.heading();
        float span = HEADING_HALF_WIDTH / UNITS_PER_DEGREE;
        for (int bearing = Mth.ceil((heading - span) / HEADING_TICK) * HEADING_TICK; bearing <= heading + span; bearing += HEADING_TICK) {
            float x = (float) ((bearing - heading) * UNITS_PER_DEGREE);
            int colour = fade(HUD, (HEADING_HALF_WIDTH - Math.abs(x)) / END_FADE);
            boolean numbered = bearing % HEADING_LABEL == 0;
            canvas.rect(x - 0.5F, -(numbered ? LONG_TICK : SHORT_TICK), x + 0.5F, 0, colour);
            if (numbered) {
                canvas.text(headingLabel(Math.floorMod(bearing, 360)), x, -LONG_TICK - LABEL_GAP - TEXT_MIDDLE, colour, Align.CENTRE);
            }
        }
        if (!Double.isNaN(target)) {
            float x = (float) (Mth.clamp(Mth.wrapDegrees(target - heading), -span, span) * UNITS_PER_DEGREE);
            canvas.triangle(x, 1, x - BUG_HALF, 1 + BUG_LENGTH, x + BUG_HALF, 1 + BUG_LENGTH, TARGET);
        }
        canvas.triangle(0, 1, -2, 4, 2, 4, HUD);
        float boxY = -HEADING_BOX_Y;
        canvas.box(-HEADING_BOX_HALF, boxY - BOX_HALF, HEADING_BOX_HALF, boxY + BOX_HALF);
        canvas.text(String.format(Locale.ROOT, "%03d", Math.round(heading) % 360), 0, boxY, HUD, Align.CENTRE);
        if (autopilot.phase() != Phase.OFF) {
            float middle = -LONG_TICK;
            canvas.flush();
            canvas.icon(Icon.lateral(autopilot.lateral()), -HEADING_HALF_WIDTH - ICON_GAP - Icon.SIZE, middle - Icon.SIZE / 2F);
            String set = autopilot.lateral() != Lateral.HEADING ? String.valueOf(Math.round(autopilot.orbitRadius()))
                : !Double.isNaN(target) ? String.format(Locale.ROOT, "%03d", Math.round(target) % 360) : "";
            canvas.text(set, HEADING_HALF_WIDTH + ICON_GAP, middle, TARGET, Align.LEFT);
        }
    }

    /** A heading scale's number: the compass point at north, east, south and west, else the tens of degrees. */
    private static Component headingLabel(int bearing) {
        return bearing % 90 == 0 ? Component.translatable(COMPASS_KEY + bearing / 45) : Component.literal(String.valueOf(bearing / 10));
    }

    /** A colour with its alpha scaled by a share, kept between none and all of it. */
    private static int fade(int colour, float share) {
        return Math.round((colour >>> 24) * Mth.clamp(share, 0, 1)) << 24 | colour & 0xFFFFFF;
    }

    /**
     * A moving tape round the middle of the HUD's height: a spine with values up it, the one now level with the
     * middle; marked on the spine's inner side (toward the middle of the screen, the given way along x) and numbered
     * on its outer side, from its lowest value on, both fading out toward its ends.
     */
    private record Tape(float spine, float inward, double value, float unitsPer, double lowest) {
        /** Where a value is up the tape, however far beyond its ends. */
        float y(double of) {
            return (float) -((of - value) * unitsPer);
        }

        float clamp(float y) {
            return Mth.clamp(y, -TAPE_HALF_HEIGHT, TAPE_HALF_HEIGHT);
        }

        /** How much of a mark shows that far up or down: fading out toward the ends. */
        float shown(float y) {
            return (TAPE_HALF_HEIGHT - Math.abs(y)) / END_FADE;
        }

        /** The spine, its marks, and its numbers, those near the middle left out for the box over it. */
        void draw(Canvas canvas, int tick, int label) {
            canvas.rect(spine - 0.5F, -TAPE_HALF_HEIGHT, spine + 0.5F, TAPE_HALF_HEIGHT, HUD);
            float reach = TAPE_HALF_HEIGHT / unitsPer;
            for (int mark = Mth.ceil(Math.max(lowest, value - reach) / tick) * tick; mark <= value + reach; mark += tick) {
                float y = y(mark);
                float length = mark % label == 0 ? LONG_TICK : SHORT_TICK;
                canvas.rect(Math.min(spine, spine + inward * length), y - 0.5F, Math.max(spine, spine + inward * length), y + 0.5F, fade(HUD, shown(y)));
            }
            float textX = spine - inward * LABEL_GAP;
            for (int mark = Mth.ceil(Math.max(lowest, value - reach) / label) * label; mark <= value + reach; mark += label) {
                float y = y(mark);
                float clear = (Math.abs(y) - BOX_HALF - 1) / BOX_HALF;
                canvas.text(String.valueOf(mark), textX, y, fade(HUD, Math.min(shown(y), clear)), inward > 0 ? Align.RIGHT : Align.LEFT);
            }
        }

        /**
         * The ground, at the height it stands at, once it is on the tape: an amber line in from the spine, hatched
         * below. It is never above the value now, the wheels' height over it being at least nothing.
         */
        void ground(Canvas canvas, double ground) {
            float top = y(ground);
            if (top >= TAPE_HALF_HEIGHT) {
                return;
            }
            float inner = spine + inward * GROUND_WIDTH;
            canvas.rect(Math.min(spine, inner), top - 0.5F, Math.max(spine, inner), top + 0.5F, fade(WARNING, shown(top)));
            for (float y = top; y + GROUND_HATCH <= TAPE_HALF_HEIGHT; y += GROUND_HATCH) {
                canvas.line(spine, y + GROUND_HATCH, inner, y, 1, fade(WARNING, shown(y + GROUND_HATCH)));
            }
        }

        /** The value set, as a magenta bug pointing at the spine from its inner side, kept to the tape's ends. */
        void bug(Canvas canvas, double target) {
            if (Double.isNaN(target)) {
                return;
            }
            float y = clamp(y(target));
            float tip = spine + inward;
            float base = tip + inward * BUG_LENGTH;
            canvas.triangle(tip, y, base, y - BUG_HALF, base, y + BUG_HALF, TARGET);
        }

        /** The value now in a box over the numbers' middle, against the spine. */
        void readout(Canvas canvas, String figure) {
            float near = spine - inward;
            float far = spine - inward * TAPE_NUMBERS;
            canvas.box(Math.min(near, far), -BOX_HALF, Math.max(near, far), BOX_HALF);
            canvas.text(figure, (near + far) / 2, 0, HUD, Align.CENTRE);
        }

        /** The value set, in magenta over the tape's numbers; nothing while there is none. */
        void set(Canvas canvas, double target) {
            if (!Double.isNaN(target)) {
                canvas.text(String.valueOf(Math.round(target)), spine - inward * LABEL_GAP, -TAPE_HALF_HEIGHT - GAP - TEXT_MIDDLE, TARGET,
                    inward > 0 ? Align.RIGHT : Align.LEFT);
            }
        }
    }

    /**
     * Drawing a part of the HUD a batch at a time, in its own units round the part's origin (the GUI's pose is set up
     * for them by {@link HelmetVisor}): shapes and text go into the GUI's shared buffer in the order they are drawn, each
     * run of shapes or of text drawn in one go as the next begins. Pictures are drawn at once instead, so whatever they
     * go over is flushed first. Text lands on whole screen pixels, so it stays sharp.
     */
    private static final class Canvas {
        /** Text this faint is not drawn: the font draws a colour with next to no alpha fully opaque. */
        private static final int FAINTEST_TEXT = 0x04000000;

        private final GuiGraphics graphics;
        private final Font font;
        private final float pixelsPerUnit;
        private final Matrix4f matrix;

        Canvas(GuiGraphics graphics, Font font, float pixelsPerUnit) {
            this.graphics = graphics;
            this.font = font;
            this.pixelsPerUnit = pixelsPerUnit;
            matrix = graphics.pose().last().pose();
        }

        /** A box framed a unit wide, for a figure, left clear inside. */
        void box(float x0, float y0, float x1, float y1) {
            rect(x0, y0, x1, y0 + 1, HUD);
            rect(x0, y1 - 1, x1, y1, HUD);
            rect(x0, y0 + 1, x0 + 1, y1 - 1, HUD);
            rect(x1 - 1, y0 + 1, x1, y1 - 1, HUD);
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

        /** A ring a unit wide, as a run of short lines. */
        void ring(float cx, float cy, float radius, int colour) {
            for (int side = 0; side < MARKER_SIDES; side++) {
                double from = 2 * Math.PI * side / MARKER_SIDES;
                double to = 2 * Math.PI * (side + 1) / MARKER_SIDES;
                line(cx + radius * (float) Math.cos(from), cy + radius * (float) Math.sin(from), cx + radius * (float) Math.cos(to),
                    cy + radius * (float) Math.sin(to), 1, colour);
            }
        }

        /** A convex quad, its corners taken round the way the GUI's own shapes go, as the other way is culled. */
        void quad(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy, int colour) {
            if (colour >>> 24 == 0) {
                return;
            }
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
            shapes.addVertex(matrix, x, y, 0).setColor(colour);
        }

        void text(String text, float x, float y, int colour, Align align) {
            text(Component.literal(text), x, y, colour, align);
        }

        /** Text placed against x as aligned, centred on y. */
        void text(Component text, float x, float y, int colour, Align align) {
            if (Integer.compareUnsigned(colour, FAINTEST_TEXT) < 0) {
                return;
            }
            float left = switch (align) {
                case LEFT -> x;
                case CENTRE -> x - font.width(text) / 2F;
                case RIGHT -> x - font.width(text);
            };
            font.drawInBatch(text, snap(left), snap(y - TEXT_MIDDLE), colour, false, matrix, graphics.bufferSource(), Font.DisplayMode.NORMAL, 0,
                LightTexture.FULL_BRIGHT);
        }

        private float snap(float at) {
            return Math.round(at * pixelsPerUnit) / pixelsPerUnit;
        }

        /**
         * A picture, a unit to each of its pixels, blended as the rest of the HUD is so its edges come out right on the
         * visor: drawn at once, pictures are not blended by themselves.
         */
        void icon(Icon icon, float x, float y) {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            graphics.pose().pushPose();
            graphics.pose().translate(snap(x), snap(y), 0);
            graphics.blit(icon.texture(), 0, 0, Icon.SIZE, Icon.SIZE, 0, 0, Icon.SIZE, Icon.SIZE, Icon.SIZE, Icon.SIZE);
            graphics.pose().popPose();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }

        /** Draws whatever is still waiting in a batch. */
        void flush() {
            graphics.flush();
        }
    }
}
