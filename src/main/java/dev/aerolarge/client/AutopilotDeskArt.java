package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.Craft;
import dev.aerolarge.autopilot.AutopilotBlockEntity.CircleSize;
import dev.aerolarge.autopilot.AutopilotBlockEntity.Lateral;
import dev.aerolarge.block.AutopilotScreenBlockEntity.Box;
import dev.aerolarge.block.CockpitDisplayBlockEntity;
import dev.aerolarge.block.CockpitKeys;
import dev.aerolarge.block.CockpitKeys.Dial;
import dev.aerolarge.block.CockpitKeys.Fan;
import dev.aerolarge.block.CockpitKeys.Function;
import dev.aerolarge.block.CockpitKeys.Key;
import dev.aerolarge.block.CockpitKeys.Sky;
import dev.aerolarge.client.PanelArt.Icon;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the autopilot's desk on the flight deck's sloped touch panel (see {@link CockpitKeys#desk}), each control a
 * picture of what it sets, so that whoever cannot read can fly too. At the left, the on/off key: the green airliner,
 * lit while the autopilot flies and pulsing while it is off, the one to find first. A speedometer, its needle at the
 * speed now and a magenta bug on its rim at the speed set. The steering: a fan seen from above, the airliner at its
 * foot nose up the slope as on the moving map, a magenta bug on its rim at the way set and a path curving to it from
 * the nose, over the row of turns, like road signs, the one flown lit. And the sky seen from the side over the ground,
 * the airliner in green at the height set and faintly at the height now, a dotted climb or descent between them.
 * Pointed at, the speedometer, the fan and the sky show a ghost of what a touch there would set, before it is touched.
 * Small windows give the numbers, for those who read them. The settings screen draws its controls the same way, with
 * the same scales (see {@link AutopilotSettingsScreen}).
 */
final class AutopilotDeskArt {
    /** Each layer lifted off the one below, in blocks. */
    private static final float LAYER = 0.002F;
    /**
     * The layers a control is drawn in, one over the other: its key's face; the glass of its dial, fan or sky; its
     * scale; its marks; the lines over them; a number's window; the bugs; their ghosts; the pictures; the numbers.
     */
    private static final float FACE = 2 * LAYER;
    private static final float GLASS_LAYER = 3 * LAYER;
    private static final float SCALE = 4 * LAYER;
    private static final float MARKS = 5 * LAYER;
    private static final float LINES = 6 * LAYER;
    private static final float WINDOW = 7 * LAYER;
    private static final float BUGS = 9 * LAYER;
    private static final float GHOSTS = 10 * LAYER;
    private static final float PICTURES = 11 * LAYER;
    private static final float NUMBERS = 14 * LAYER;
    /** A key's rim, and a lit key's, in blocks. */
    private static final double RIM = 0.012;
    private static final double LIT_RIM = 0.02;
    /** How much of its colour a lit key's face takes on. */
    private static final float LIT_TINT = 0.25F;
    /** How long the on/off key takes to pulse once while the autopilot is off, in milliseconds. */
    private static final float PULSE_PERIOD = 1400;
    /** Segments a full circle is drawn with, and pieces a curved path is. */
    private static final int ROUND = 48;
    private static final int PATH_PIECES = 12;
    /**
     * The speedometer: the speeds its band turns amber and red at, the airliner's (see {@link Craft}), in blocks a
     * second, and its ticks, every so many blocks a second, numbered every so many, which the settings screen's dial
     * spaces its own ticks from.
     */
    static final int AMBER_FROM = Craft.AIRLINER.amberFrom;
    static final int RED_FROM = Craft.AIRLINER.redFrom;
    static final int SPEED_TICK = 10;
    static final int SPEED_NUMBER = 30;
    /**
     * The speedometer here, as shares of its radius: its glass, past the rim; how thick its band is; how long its
     * ticks are, the numbered ones and the rest, and how wide; how far in its numbers are, from the band; the bug's
     * size; and the needle's width, and how far short of the band it stops.
     */
    private static final double DIAL_GLASS = 1.08;
    private static final double BAND = 0.14;
    private static final double LONG_TICK = 0.3;
    private static final double SHORT_TICK = 0.18;
    private static final double TICK_WIDTH = 0.02;
    private static final double SPEED_NUMBERS_IN = 0.44;
    private static final double BUG = 0.16;
    private static final double NEEDLE = 0.05;
    private static final double NEEDLE_SHORT = 0.05;
    private static final double HUB = 0.1;
    /**
     * The steering fan's ticks round its rim, shared with the settings screen's: every so many degrees, the long ones
     * every so many.
     */
    static final int FAN_TICK = 15;
    static final int FAN_LONG_TICK = 45;
    /**
     * The steering fan here, as shares of its radius: its rim's width; its ticks' lengths, the long ones and the rest;
     * north's mark, how far out and how big; the bug's radius and the path's width and how far out it ends; the
     * airliner at its foot; and how far up the fan the path from its nose to the bug bends.
     */
    private static final double FAN_RIM = 0.03;
    private static final double FAN_LONG = 0.15;
    private static final double FAN_SHORT = 0.08;
    private static final double FAN_TICK_WIDTH = 0.012;
    private static final double NORTH_AT = 0.8;
    private static final double NORTH_SIZE = 0.07;
    private static final double FAN_BUG = 0.07;
    private static final double PATH_WIDTH = 0.035;
    private static final double PATH_END = 0.93;
    private static final double FAN_AIRLINER = 0.4;
    private static final double PATH_BEND = 0.7;
    /** A turn's picture, as a share of its key's height. */
    private static final double TURN_PICTURE = 0.92;
    /**
     * The sky's ticks up its side, shared with the settings screen's: every so many blocks of height, numbered every so
     * many.
     */
    static final int HEIGHT_TICK = 10;
    static final int HEIGHT_NUMBER = 50;
    /**
     * The sky here: its airliners' size and where across it they fly, as shares of its width; its ticks' lengths, the
     * numbered ones and the rest, the grass's depth and the gap between a number and its tick, in blocks; and how far
     * the airliner must be from the height set for the climb or descent to it to be drawn, in blocks.
     */
    private static final double SKY_AIRLINER = 0.42;
    private static final double SKY_AIRLINER_AT = 0.4;
    private static final double HEIGHT_LONG = 0.035;
    private static final double HEIGHT_SHORT = 0.02;
    private static final double GRASS_DEPTH = 0.012;
    private static final double NUMBER_GAP = 0.045;
    private static final double CLIMB_DRAWN = 0.05;
    /**
     * The climb or descent drawn in the sky, in blocks: the gap it leaves at the airliner now; and its arrowhead, how
     * far short of the height set its tip stops, how long it is and how wide either side.
     */
    private static final double CLIMB_GAP = 0.03;
    private static final double ARROW_SHORT = 0.025;
    private static final double ARROW_LENGTH = 0.022;
    private static final double ARROW_HALF = 0.016;
    /**
     * The windows under the speedometer, as wide as a share of its radius, and in the sky's top left corner, its middle
     * a share of the sky's width across, as wide as a share of it, and so many window heights down from the top.
     */
    private static final double SPEED_WINDOW = 0.9;
    private static final double HEIGHT_WINDOW_AT = 0.32;
    private static final double HEIGHT_WINDOW = 0.36;
    private static final double HEIGHT_WINDOW_DOWN = 0.75;
    /**
     * The pictures: the on/off key's airliner, as a share of the key's smaller side; the airliner at the fan's foot,
     * lifted off it, in blocks; and the sky's airliners, lifted as a share of their size, their body being in the top
     * part of their picture, so that it sits on the height.
     */
    private static final double ENGAGE_PICTURE = 0.8;
    private static final double FAN_AIRLINER_LIFT = 0.01;
    private static final double SKY_AIRLINER_LIFT = 0.25;
    /**
     * The corners of the steering's key either side of the fan: the pin's lamp in the top left, as a share of the key's
     * height, and the heading's window in the top right, as wide as a share of the key's height, both this far in
     * from the corner, in blocks.
     */
    private static final double PIN_LAMP = 0.2;
    private static final double HEADING_WINDOW = 0.42;
    private static final double CORNER_INSET = 0.03;
    /** The numbers: their size, in blocks per font pixel, and their windows' height and frame, in blocks. */
    private static final float NUMBER = 0.0045F;
    private static final float SMALL_NUMBER = 0.0034F;
    private static final double WINDOW_HEIGHT = 0.056;
    private static final double WINDOW_FRAME = 0.006;
    /** How far apart a dotted line's dots are, in blocks, how much of that each dot takes, and how wide they are. */
    private static final double DOTS = 0.024;
    private static final double DOT = 0.55;
    private static final double DOT_WIDTH = 0.006;

    private static final int KEY = AutopilotScreenRenderer.BUTTON;
    private static final int EDGE = AutopilotScreenRenderer.BUTTON_EDGE;
    private static final int GLASS = AutopilotScreenRenderer.GLASS;
    private static final int WHITE = AutopilotScreenRenderer.WHITE;
    private static final int GREY = AutopilotScreenRenderer.GREY;
    private static final int GREEN = AutopilotScreenRenderer.GREEN;
    private static final int AMBER = AutopilotScreenRenderer.AMBER;
    private static final int CYAN = AutopilotScreenRenderer.TRACK;
    private static final int MAGENTA = AutopilotScreenRenderer.TARGET;
    private static final int RED = PanelArt.RED;
    private static final int OFF = PanelArt.OFF;
    /** A ghost: a colour drawn half see-through. */
    static final int GHOST = 0x80FFFFFF;
    /** The fan's rim while it does not steer, the windows' frame and glass, and north's faint marker. */
    static final int RIM_IDLE = 0xFF3D5560;
    private static final int WINDOW_EDGE = 0xFF30363F;
    private static final int WINDOW_GLASS = 0xFF0B1418;
    static final int NORTH = 0x99E8382C;
    /** The sky, darkening upward, the ground under it, and the faint white of its ticks and of its numbers. */
    static final int SKY_LOW = 0xFF2A6FA8;
    static final int SKY_HIGH = 0xFF0A1A33;
    static final int EARTH = 0xFF5B4A2E;
    static final int GRASS = 0xFF3F7D3A;
    static final int HEIGHT_TICKS = 0x99F2F2F2;
    static final int HEIGHT_NUMBERS = 0xBFF2F2F2;
    /** A picture as it is, darkened while the autopilot is off, or the pin's lamp unlit. */
    private static final int PICTURE_ON = 0xFFFFFFFF;
    private static final int PICTURE_OFF = 0xFF8A8A8A;
    private static final int PIN_UNLIT = 0x40FFFFFF;

    private static final Icon ENGAGE = Icon.of("autopilot/engage");
    private static final Icon AIRLINER = Icon.of("autopilot/airliner");
    private static final Icon AIRLINER_SIDE = Icon.of("autopilot/airliner_side");
    private static final Icon PIN = Icon.of("autopilot/destination");

    /** The desk's controls, by what they work. */
    private record Desk(Map<Function, Box> boxes) {
        static Desk of(List<Key> keys) {
            Map<Function, Box> boxes = new EnumMap<>(Function.class);
            keys.forEach(key -> boxes.put(key.function(), key.box()));
            return new Desk(boxes);
        }

        Box at(Function function) {
            return boxes.get(function);
        }

        /**
         * Whether it has every control it is drawn with: the panel holding it is set up when the airliner is built,
         * and until then has no keys at all.
         */
        boolean complete() {
            boolean controls = boxes.containsKey(Function.ENGAGE) && boxes.containsKey(Function.SPEED) && boxes.containsKey(Function.HEADING)
                && boxes.containsKey(Function.ALTITUDE);
            return controls && boxes.keySet().containsAll(CockpitKeys.TURNS);
        }
    }

    /**
     * How the autopilot is set and how the airliner flies now: whether the autopilot flies it; the speed, height and
     * compass bearing set and how it steers; the speed, height and bearing now, NaN while the airliner is not
     * assembled; the runway's height, which the lowest height it can be set to hold is measured from, and the highest.
     */
    private record Flight(boolean on, double speed, double altitude, double bearing, Lateral lateral, CircleSize circle, double speedNow,
                          double altitudeNow, double bearingNow, double runway, double highest) {
        static Flight of(CockpitDisplayBlockEntity display, Level level, @Nullable AutopilotBlockEntity autopilot, float partialTick) {
            SableClient.Attitude attitude = SableClient.attitude(display, display.facing().getOpposite(), partialTick);
            boolean on = AutopilotScreenRenderer.engaged(autopilot);
            double highest = AutopilotBlockEntity.highestAltitude(level);
            if (autopilot == null) {
                return new Flight(false, 0, 0, 0, Lateral.HEADING, CircleSize.TIGHT, Double.NaN, Double.NaN, Double.NaN, highest, highest);
            }
            double altitudeNow = attitude == null ? Double.NaN : SableClient.toWorld(level, Vec3.atCenterOf(autopilot.getBlockPos())).y;
            return new Flight(on, autopilot.targetSpeed(), autopilot.targetAltitude(), AutopilotBlockEntity.bearing(autopilot.targetHeading()),
                autopilot.lateral(), autopilot.circle(), attitude == null ? Double.NaN : attitude.airspeed(), altitudeNow,
                attitude == null ? Double.NaN : attitude.heading(), autopilot.runwayAltitude(), highest);
        }

        double lowest() {
            return AutopilotBlockEntity.lowestAltitude(runway);
        }

        /** Whether the airliner is assembled, so how it flies now is known. */
        boolean assembled() {
            return !Double.isNaN(bearingNow);
        }

        /** The share of the way from the slowest speed to the fastest a speed is. */
        static double speedShare(double speed) {
            return Mth.clamp((speed - AutopilotBlockEntity.MIN_SPEED) / (AutopilotBlockEntity.MAX_SPEED - AutopilotBlockEntity.MIN_SPEED), 0, 1);
        }

        /** The share of the way from the lowest height it can be set to the highest a height is. */
        double heightShare(double height) {
            return highest > lowest() ? Mth.clamp((height - lowest()) / (highest - lowest()), 0, 1) : 0;
        }

        /** How far right of the nose a compass bearing lies, in degrees, -180 to 180; 0 with no heading now. */
        double turnTo(double to) {
            return assembled() ? Mth.wrapDegrees(to - bearingNow) : 0;
        }

        /** Whether it is set to fly the turn a key along the bottom of the steering stands for. */
        boolean flies(Function turn) {
            return on && turn.button != null && turn.button.flies(lateral, circle);
        }
    }

    /**
     * What a touch where the player points would set, as the server will set it (see {@link
     * AutopilotBlockEntity#press}): the speed, the height or the compass bearing; and which of them it is. None while
     * the autopilot is off or the airliner not assembled, when a touch sets nothing.
     */
    private record Ghost(Function function, double value) {
        @Nullable
        static Ghost at(Desk desk, Flight flight, @Nullable CockpitDisplayRenderer.Pointer pointer) {
            if (pointer == null || !pointer.onSlope() || !flight.on() || !flight.assembled()) {
                return null;
            }
            for (Function function : new Function[]{Function.SPEED, Function.HEADING, Function.ALTITUDE}) {
                Box box = desk.at(function);
                if (box.contains(pointer.across(), pointer.up())) {
                    return new Ghost(function, value(function, box, flight, pointer));
                }
            }
            return null;
        }

        /** What a touch at the pointer on a control would set, by the same reckoning as the server's. */
        private static double value(Function function, Box box, Flight flight, CockpitDisplayRenderer.Pointer pointer) {
            double along = CockpitKeys.along(new Key(function, box), pointer.across(), pointer.up());
            return switch (function) {
                case SPEED -> AutopilotBlockEntity.touchedSpeed(along);
                case ALTITUDE -> AutopilotBlockEntity.touchedAltitude(along, flight.runway(), flight.highest());
                default -> AutopilotBlockEntity.touchedBearing((int) Math.round(flight.bearingNow()), along);
            };
        }
    }

    /** A window for a number: its middle, across and up, and its width. */
    private record Window(double x, double y, double width) {
        /** The heading's, in the steering key's top right corner, beside the fan. */
        static Window heading(Box box) {
            double width = (box.y1() - box.y0()) * HEADING_WINDOW;
            return new Window(box.x1() - width / 2 - CORNER_INSET, box.y1() - WINDOW_HEIGHT / 2 - CORNER_INSET, width);
        }

        /** The speed's, under the speedometer. */
        static Window speed(Box box) {
            Dial dial = Dial.on(box);
            return new Window(dial.x(), box.y0() + WINDOW_HEIGHT, dial.radius() * SPEED_WINDOW);
        }

        /** The height's, in the sky's top left corner. */
        static Window height(Sky sky) {
            double width = sky.x1() - sky.x0();
            return new Window(sky.x0() + width * HEIGHT_WINDOW_AT, sky.top() - WINDOW_HEIGHT * HEIGHT_WINDOW_DOWN, width * HEIGHT_WINDOW);
        }
    }

    private AutopilotDeskArt() {
    }

    /**
     * The desk, on the slope from its front left corner: the shapes first, then the pictures, then the numbers, each of
     * which takes a buffer of its own and ends the one before.
     */
    static void draw(CockpitDisplayBlockEntity display, Level level, MultiBufferSource buffers, PoseStack pose, float partialTick,
                     @Nullable CockpitDisplayRenderer.Pointer pointer) {
        Desk desk = Desk.of(display.slopeKeys());
        if (!desk.complete()) {
            return;
        }
        Flight flight = Flight.of(display, level, display.autopilot(), partialTick);
        Ghost ghost = Ghost.at(desk, flight, pointer);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer shapes = ScreenDrawing.shapes(buffers);
        engageKey(shapes, matrix, desk.at(Function.ENGAGE), flight.on());
        speedometer(shapes, matrix, desk.at(Function.SPEED), flight, ghost);
        fan(shapes, matrix, desk.at(Function.HEADING), flight, ghost);
        for (Function turn : CockpitKeys.TURNS) {
            turnKey(shapes, matrix, desk.at(turn), turn, flight.flies(turn));
        }
        sky(shapes, matrix, desk.at(Function.ALTITUDE), flight);
        pictures(buffers, matrix, desk, flight, ghost);
        numbers(pose, buffers, desk, flight, ghost);
    }

    /** The on/off key: its rim green and its face lit while the autopilot flies; off, its rim pulses green. */
    private static void engageKey(VertexConsumer shapes, Matrix4f matrix, Box box, boolean on) {
        int rim = on ? GREEN : PanelArt.blend(EDGE, GREEN, PanelArt.pulse(PULSE_PERIOD));
        tile(shapes, matrix, box, rim, on ? PanelArt.blend(KEY, GREEN, LIT_TINT) : KEY, on ? LIT_RIM : RIM);
    }

    /**
     * The speedometer: its glass face, its band green, amber and red from slow to fast, a tick every so many blocks a
     * second, its window for the speed set, the needle at the speed now, and on its rim the magenta bug at the speed
     * set, and a ghost of one where it is pointed at.
     */
    private static void speedometer(VertexConsumer shapes, Matrix4f matrix, Box box, Flight flight, @Nullable Ghost ghost) {
        tile(shapes, matrix, box, EDGE, KEY, RIM);
        Dial dial = Dial.on(box);
        ScreenDrawing.disc(shapes, matrix, dial.x(), dial.y(), dial.radius() * DIAL_GLASS, ROUND, GLASS_LAYER, GLASS);
        double band = dial.radius() * (1 - BAND / 2);
        arc(shapes, matrix, dial, band, AutopilotBlockEntity.MIN_SPEED, AMBER_FROM, flight.on() ? GREEN : OFF);
        arc(shapes, matrix, dial, band, AMBER_FROM, RED_FROM, flight.on() ? AMBER : OFF);
        arc(shapes, matrix, dial, band, RED_FROM, AutopilotBlockEntity.MAX_SPEED, flight.on() ? RED : OFF);
        for (int speed = AutopilotBlockEntity.MIN_SPEED; speed <= AutopilotBlockEntity.MAX_SPEED; speed += SPEED_TICK) {
            double length = speed % SPEED_NUMBER == 0 ? LONG_TICK : SHORT_TICK;
            spoke(shapes, matrix, dial, Flight.speedShare(speed), 1 - BAND - length, 1 - BAND, MARKS, TICK_WIDTH, WHITE);
        }
        Window speed = Window.speed(box);
        window(shapes, matrix, speed);
        if (flight.on()) {
            bug(shapes, matrix, dial, Flight.speedShare(flight.speed()), BUGS, MAGENTA);
        }
        if (ghost != null && ghost.function() == Function.SPEED) {
            bug(shapes, matrix, dial, Flight.speedShare(ghost.value()), GHOSTS, GHOST & MAGENTA);
        }
        if (flight.assembled()) {
            spoke(shapes, matrix, dial, Flight.speedShare(flight.speedNow()), 0, 1 - BAND - NEEDLE_SHORT, LINES, NEEDLE, WHITE);
        }
        ScreenDrawing.disc(shapes, matrix, dial.x(), dial.y(), dial.radius() * HUB, ROUND / 3, BUGS, GREY);
    }

    /**
     * The steering fan: its glass, its rim cyan while it holds a heading, a tick every so many degrees, north faintly
     * on the rim; holding a heading, the magenta bug where it is set to fly and a path curving to it from the nose; and
     * a ghost of the bug where it is pointed at. A heading set behind the airliner keeps its bug at the edge of the
     * fan.
     */
    private static void fan(VertexConsumer shapes, Matrix4f matrix, Box box, Flight flight, @Nullable Ghost ghost) {
        tile(shapes, matrix, box, EDGE, KEY, RIM);
        Fan fan = Fan.on(box);
        halfDisc(shapes, matrix, fan, GLASS);
        boolean steering = flight.flies(Function.STRAIGHT);
        int rim = steering ? CYAN : RIM_IDLE;
        for (int k = 0; k < ROUND / 2; k++) {
            double a0 = Math.PI * k / (ROUND / 2);
            double a1 = Math.PI * (k + 1) / (ROUND / 2);
            ScreenDrawing.line(shapes, matrix, fan.x() + Math.cos(a0) * fan.radius(), fan.y() + Math.sin(a0) * fan.radius(),
                fan.x() + Math.cos(a1) * fan.radius(), fan.y() + Math.sin(a1) * fan.radius(), SCALE, (float) (fan.radius() * FAN_RIM), rim);
        }
        for (int turn = -90; turn <= 90; turn += FAN_TICK) {
            double inner = 1 - (turn % FAN_LONG_TICK == 0 ? FAN_LONG : FAN_SHORT);
            fanLine(shapes, matrix, fan, turn, inner, 1, FAN_TICK_WIDTH, rim);
        }
        double north = flight.turnTo(0);
        if (flight.assembled() && Math.abs(north) <= 90) {
            fanMark(shapes, matrix, fan, north, NORTH_AT, NORTH_SIZE, NORTH);
        }
        if (steering) {
            double turn = Mth.clamp(flight.turnTo(flight.bearing()), -90, 90);
            path(shapes, matrix, fan, turn);
            fanBug(shapes, matrix, fan, turn, BUGS, MAGENTA);
        }
        if (ghost != null && ghost.function() == Function.HEADING) {
            fanBug(shapes, matrix, fan, Mth.clamp(flight.turnTo(ghost.value()), -90, 90), GHOSTS, GHOST & MAGENTA);
        }
        window(shapes, matrix, Window.heading(box));
    }

    /**
     * One of the turns along the bottom of the steering: lit cyan for straight on, magenta for a circle, while flown.
     */
    private static void turnKey(VertexConsumer shapes, Matrix4f matrix, Box box, Function turn, boolean flown) {
        int colour = turn == Function.STRAIGHT ? CYAN : MAGENTA;
        tile(shapes, matrix, box, flown ? colour : EDGE, flown ? PanelArt.blend(GLASS, colour, LIT_TINT) : GLASS, flown ? LIT_RIM : RIM);
    }

    /**
     * The sky from the side: darkening upward over the brown earth and its grass, a tick every so many blocks of height
     * up its side; the height set as a green dotted level across it, and, while the airliner is far from it, a dotted
     * line from the height now to it, an arrowhead the way it goes.
     */
    private static void sky(VertexConsumer shapes, Matrix4f matrix, Box box, Flight flight) {
        tile(shapes, matrix, box, EDGE, KEY, RIM);
        Sky sky = Sky.on(box);
        gradient(shapes, matrix, sky.x0(), sky.ground(), sky.x1(), sky.top(), GLASS_LAYER, SKY_LOW, SKY_HIGH);
        ScreenDrawing.rect(shapes, matrix, sky.x0(), box.y0() + (sky.ground() - box.y0()) / 3, sky.x1(), sky.ground(), GLASS_LAYER, EARTH);
        ScreenDrawing.rect(shapes, matrix, sky.x0(), sky.ground() - GRASS_DEPTH, sky.x1(), sky.ground(), SCALE, GRASS);
        double first = Math.ceil(flight.lowest() / HEIGHT_TICK) * HEIGHT_TICK;
        for (double height = first; height <= flight.highest(); height += HEIGHT_TICK) {
            double y = sky.up(flight.heightShare(height));
            double length = height % HEIGHT_NUMBER == 0 ? HEIGHT_LONG : HEIGHT_SHORT;
            ScreenDrawing.rect(shapes, matrix, sky.x1() - length, y - DOT_WIDTH / 3, sky.x1(), y + DOT_WIDTH / 3, SCALE, HEIGHT_TICKS);
        }
        window(shapes, matrix, Window.height(sky));
        if (!flight.on()) {
            return;
        }
        double set = sky.up(flight.heightShare(flight.altitude()));
        dotted(shapes, matrix, sky.x0(), set, sky.x1() - NUMBER_GAP, set, GREEN);
        if (!flight.assembled()) {
            return;
        }
        double now = sky.up(flight.heightShare(flight.altitudeNow()));
        double x = skyAirlinerX(sky);
        if (Math.abs(set - now) > CLIMB_DRAWN) {
            double way = Math.signum(set - now);
            double base = set - way * (ARROW_SHORT + ARROW_LENGTH);
            dotted(shapes, matrix, x, now + way * CLIMB_GAP, x, base, GHOST & WHITE);
            triangle(shapes, matrix, x, set - way * ARROW_SHORT, x - way * ARROW_HALF, base, x + way * ARROW_HALF, base, LINES, WHITE);
        }
    }

    /** Where across the sky its airliners fly. */
    private static double skyAirlinerX(Sky sky) {
        return sky.x0() + (sky.x1() - sky.x0()) * SKY_AIRLINER_AT;
    }

    /**
     * The pictures: the on/off key's airliner, always bright; each turn's path, bright while the autopilot flies; the
     * airliner at the steering fan's foot; the pin's lamp, lit while it flies to a pin; and the sky's airliners.
     */
    private static void pictures(MultiBufferSource buffers, Matrix4f matrix, Desk desk, Flight flight, @Nullable Ghost ghost) {
        Box engage = desk.at(Function.ENGAGE);
        double side = Math.min(engage.x1() - engage.x0(), engage.y1() - engage.y0()) * ENGAGE_PICTURE / 2;
        ScreenDrawing.picture(buffers, matrix, ENGAGE.texture(), (engage.x0() + engage.x1()) / 2, (engage.y0() + engage.y1()) / 2, side, PICTURES, PICTURE_ON);
        int tint = flight.on() ? PICTURE_ON : PICTURE_OFF;
        for (Function turn : CockpitKeys.TURNS) {
            Box box = desk.at(turn);
            double half = (box.y1() - box.y0()) * TURN_PICTURE / 2;
            ScreenDrawing.picture(buffers, matrix, turnIcon(turn).texture(), (box.x0() + box.x1()) / 2, (box.y0() + box.y1()) / 2, half, PICTURES, tint);
        }
        Fan fan = Fan.on(desk.at(Function.HEADING));
        double plane = fan.radius() * FAN_AIRLINER / 2;
        ScreenDrawing.picture(buffers, matrix, AIRLINER.texture(), fan.x(), fan.y() + plane + FAN_AIRLINER_LIFT, plane, PICTURES, tint);
        Box steering = desk.at(Function.HEADING);
        double lamp = (steering.y1() - steering.y0()) * PIN_LAMP / 2;
        int pin = flight.on() && flight.lateral() == Lateral.DESTINATION ? PICTURE_ON : PIN_UNLIT;
        ScreenDrawing.picture(buffers, matrix, PIN.texture(), steering.x0() + lamp + CORNER_INSET, steering.y1() - lamp - CORNER_INSET, lamp, PICTURES, pin);
        skyAirliners(buffers, matrix, Sky.on(desk.at(Function.ALTITUDE)), flight, ghost);
    }

    /**
     * The sky's airliners, seen from the side: faintly at the height it flies now, in green at the height set, and a
     * ghost of it where the sky is pointed at, each a layer over the one before.
     */
    private static void skyAirliners(MultiBufferSource buffers, Matrix4f matrix, Sky sky, Flight flight, @Nullable Ghost ghost) {
        double half = (sky.x1() - sky.x0()) * SKY_AIRLINER / 2;
        double x = skyAirlinerX(sky);
        double lift = half * SKY_AIRLINER_LIFT;
        if (flight.assembled()) {
            double now = sky.up(flight.heightShare(flight.altitudeNow())) + lift;
            ScreenDrawing.picture(buffers, matrix, AIRLINER_SIDE.texture(), x, now, half, PICTURES, GHOST & WHITE);
        }
        if (flight.on()) {
            double set = sky.up(flight.heightShare(flight.altitude())) + lift;
            ScreenDrawing.picture(buffers, matrix, AIRLINER_SIDE.texture(), x, set, half, PICTURES + LAYER, GREEN);
        }
        if (ghost != null && ghost.function() == Function.ALTITUDE) {
            double there = sky.up(flight.heightShare(ghost.value())) + lift;
            ScreenDrawing.picture(buffers, matrix, AIRLINER_SIDE.texture(), x, there, half, PICTURES + 2 * LAYER, GHOST & GREEN);
        }
    }

    /** A turn's picture: the path flown from a small airliner's nose. */
    private static Icon turnIcon(Function turn) {
        return switch (turn) {
            case TIGHT_LEFT -> PanelArt.TURN_TIGHT_LEFT;
            case GENTLE_LEFT -> PanelArt.TURN_GENTLE_LEFT;
            case GENTLE_RIGHT -> PanelArt.TURN_GENTLE_RIGHT;
            case TIGHT_RIGHT -> PanelArt.TURN_TIGHT_RIGHT;
            default -> PanelArt.STRAIGHT_ON;
        };
    }

    /**
     * The numbers, last: the speedometer's every so many blocks a second round its band; the sky's heights up its side;
     * in their windows the speed, the bearing and the height set; and beside a ghost what it would set.
     */
    private static void numbers(PoseStack pose, MultiBufferSource buffers, Desk desk, Flight flight, @Nullable Ghost ghost) {
        Font font = Minecraft.getInstance().font;
        Box speed = desk.at(Function.SPEED);
        Dial dial = Dial.on(speed);
        for (int each = AutopilotBlockEntity.MIN_SPEED; each <= AutopilotBlockEntity.MAX_SPEED; each += SPEED_NUMBER) {
            double angle = dial.angle(Flight.speedShare(each));
            double at = dial.radius() * (1 - BAND - SPEED_NUMBERS_IN);
            text(font, pose, buffers, String.valueOf(each), dial.x() + Math.cos(angle) * at, dial.y() + Math.sin(angle) * at, SMALL_NUMBER, GREY,
                ScreenDrawing.Align.CENTRE);
        }
        Sky sky = Sky.on(desk.at(Function.ALTITUDE));
        double first = Math.ceil(flight.lowest() / HEIGHT_NUMBER) * HEIGHT_NUMBER;
        for (double height = first; height <= flight.highest(); height += HEIGHT_NUMBER) {
            text(font, pose, buffers, String.valueOf(Math.round(height)), sky.x1() - NUMBER_GAP, sky.up(flight.heightShare(height)), SMALL_NUMBER,
                HEIGHT_NUMBERS, ScreenDrawing.Align.RIGHT);
        }
        if (!flight.on()) {
            return;
        }
        // Pointed at, a control's window shows in white what a touch there would set, instead of what is set.
        boolean speedGhost = ghost != null && ghost.function() == Function.SPEED;
        boolean headingGhost = ghost != null && ghost.function() == Function.HEADING;
        boolean heightGhost = ghost != null && ghost.function() == Function.ALTITUDE;
        Window speedWindow = Window.speed(speed);
        double speedShown = speedGhost ? ghost.value() : flight.speed();
        text(font, pose, buffers, String.valueOf(Math.round(speedShown)), speedWindow.x(), speedWindow.y(), NUMBER, speedGhost ? WHITE : MAGENTA,
            ScreenDrawing.Align.CENTRE);
        Window heading = Window.heading(desk.at(Function.HEADING));
        int steering = headingGhost ? WHITE : flight.lateral() == Lateral.HEADING ? CYAN : GREY;
        text(font, pose, buffers, bearing(headingGhost ? ghost.value() : flight.bearing()), heading.x(), heading.y(), NUMBER, steering,
            ScreenDrawing.Align.CENTRE);
        Window height = Window.height(sky);
        double heightShown = heightGhost ? ghost.value() : flight.altitude();
        text(font, pose, buffers, String.valueOf(Math.round(heightShown)), height.x(), height.y(), NUMBER, heightGhost ? WHITE : GREEN,
            ScreenDrawing.Align.CENTRE);
    }

    /** A compass bearing as the windows show it: three figures and a degree sign. */
    private static String bearing(double bearing) {
        return String.format(Locale.ROOT, "%03d°", Math.round(bearing));
    }

    private static void text(Font font, PoseStack pose, MultiBufferSource buffers, String text, double x, double y, float size, int colour,
                             ScreenDrawing.Align align) {
        ScreenDrawing.text(font, pose, buffers, Component.literal(text), x, y, NUMBERS, size, colour, align);
    }

    /** A key's tile: its rim, so wide, and inside it its face. */
    private static void tile(VertexConsumer shapes, Matrix4f matrix, Box box, int rim, int face, double rimWidth) {
        ScreenDrawing.rect(shapes, matrix, box.x0(), box.y0(), box.x1(), box.y1(), LAYER, rim);
        ScreenDrawing.rect(shapes, matrix, box.x0() + rimWidth, box.y0() + rimWidth, box.x1() - rimWidth, box.y1() - rimWidth, FACE, face);
    }

    /** A window for a number: a frame and its dark glass. */
    private static void window(VertexConsumer shapes, Matrix4f matrix, Window window) {
        double half = WINDOW_HEIGHT / 2;
        double x0 = window.x() - window.width() / 2;
        double x1 = window.x() + window.width() / 2;
        ScreenDrawing.rect(shapes, matrix, x0 - WINDOW_FRAME, window.y() - half - WINDOW_FRAME, x1 + WINDOW_FRAME, window.y() + half + WINDOW_FRAME, WINDOW,
            WINDOW_EDGE);
        ScreenDrawing.rect(shapes, matrix, x0, window.y() - half, x1, window.y() + half, WINDOW + LAYER, WINDOW_GLASS);
    }

    /** The speedometer's band between two speeds, in a colour. */
    private static void arc(VertexConsumer shapes, Matrix4f matrix, Dial dial, double radius, int from, int to, int colour) {
        int pieces = Math.max(2, (to - from) / 2);
        for (int k = 0; k < pieces; k++) {
            double a0 = dial.angle(Flight.speedShare(from + (double) (to - from) * k / pieces));
            double a1 = dial.angle(Flight.speedShare(from + (double) (to - from) * (k + 1) / pieces));
            ScreenDrawing.line(shapes, matrix, dial.x() + Math.cos(a0) * radius, dial.y() + Math.sin(a0) * radius, dial.x() + Math.cos(a1) * radius,
                dial.y() + Math.sin(a1) * radius, SCALE, (float) (dial.radius() * BAND), colour);
        }
    }

    /**
     * A line out along a share of the speedometer's scale, between two shares of its radius, in a layer, so wide a
     * share of it.
     */
    private static void spoke(VertexConsumer shapes, Matrix4f matrix, Dial dial, double share, double from, double to, float z, double width, int colour) {
        double angle = dial.angle(share);
        double r = dial.radius();
        ScreenDrawing.line(shapes, matrix, dial.x() + Math.cos(angle) * r * from, dial.y() + Math.sin(angle) * r * from, dial.x() + Math.cos(angle) * r * to,
            dial.y() + Math.sin(angle) * r * to, z, (float) (r * width), colour);
    }

    /** The speedometer's bug: a triangle on its rim, pointing in at a share of its scale. */
    private static void bug(VertexConsumer shapes, Matrix4f matrix, Dial dial, double share, float z, int colour) {
        double angle = dial.angle(share);
        double r = dial.radius();
        double tipX = dial.x() + Math.cos(angle) * r;
        double tipY = dial.y() + Math.sin(angle) * r;
        double outX = dial.x() + Math.cos(angle) * r * (1 + BUG);
        double outY = dial.y() + Math.sin(angle) * r * (1 + BUG);
        double sideX = -Math.sin(angle) * r * BUG * 0.7;
        double sideY = Math.cos(angle) * r * BUG * 0.7;
        triangle(shapes, matrix, tipX, tipY, outX - sideX, outY - sideY, outX + sideX, outY + sideY, z, colour);
    }

    /** The half of a disc the steering fan is, in a colour. */
    private static void halfDisc(VertexConsumer shapes, Matrix4f matrix, Fan fan, int colour) {
        for (int k = 0; k < ROUND / 2; k++) {
            double a0 = Math.PI * k / (ROUND / 2);
            double a1 = Math.PI * (k + 1) / (ROUND / 2);
            triangle(shapes, matrix, fan.x(), fan.y(), fan.x() + Math.cos(a0) * fan.radius(), fan.y() + Math.sin(a0) * fan.radius(),
                fan.x() + Math.cos(a1) * fan.radius(), fan.y() + Math.sin(a1) * fan.radius(), GLASS_LAYER, colour);
        }
    }

    /** A line out along the fan, so many degrees right of the nose, between two shares of its radius. */
    private static void fanLine(VertexConsumer shapes, Matrix4f matrix, Fan fan, double turn, double from, double to, double width, int colour) {
        double r = fan.radius();
        ScreenDrawing.line(shapes, matrix, fan.x() + sin(turn) * r * from, fan.y() + cos(turn) * r * from, fan.x() + sin(turn) * r * to,
            fan.y() + cos(turn) * r * to, MARKS, (float) width, colour);
    }

    /**
     * A small triangle on the fan pointing out, so many degrees right of the nose, at a share of its radius: over the
     * ticks it may fall on.
     */
    private static void fanMark(VertexConsumer shapes, Matrix4f matrix, Fan fan, double turn, double at, double size, int colour) {
        double r = fan.radius();
        double x = fan.x() + sin(turn) * r * at;
        double y = fan.y() + cos(turn) * r * at;
        double s = r * size;
        triangle(shapes, matrix, x + sin(turn) * s, y + cos(turn) * s, x - cos(turn) * s * 0.5, y + sin(turn) * s * 0.5, x + cos(turn) * s * 0.5,
            y - sin(turn) * s * 0.5, LINES, colour);
    }

    /** The fan's bug: a disc on its rim, so many degrees right of the nose. */
    private static void fanBug(VertexConsumer shapes, Matrix4f matrix, Fan fan, double turn, float z, int colour) {
        ScreenDrawing.disc(shapes, matrix, fan.x() + sin(turn) * fan.radius(), fan.y() + cos(turn) * fan.radius(), fan.radius() * FAN_BUG, ROUND / 3, z,
            colour);
    }

    /** The path from the airliner's nose round to the bug, so many degrees right of the nose: a magenta curve. */
    private static void path(VertexConsumer shapes, Matrix4f matrix, Fan fan, double turn) {
        double r = fan.radius();
        double startY = fan.y() + r * FAN_AIRLINER;
        double bendY = fan.y() + r * PATH_BEND;
        double endX = fan.x() + sin(turn) * r * PATH_END;
        double endY = fan.y() + cos(turn) * r * PATH_END;
        for (int k = 0; k < PATH_PIECES; k++) {
            double t0 = (double) k / PATH_PIECES;
            double t1 = (double) (k + 1) / PATH_PIECES;
            ScreenDrawing.line(shapes, matrix, CockpitKeys.curve(fan.x(), fan.x(), endX, t0), CockpitKeys.curve(startY, bendY, endY, t0), CockpitKeys.curve(fan.x(), fan.x(), endX, t1),
                CockpitKeys.curve(startY, bendY, endY, t1), LINES, (float) (r * PATH_WIDTH), MAGENTA);
        }
    }

    /** A dotted line from one point to another. */
    private static void dotted(VertexConsumer shapes, Matrix4f matrix, double x0, double y0, double x1, double y1, int colour) {
        double length = Math.hypot(x1 - x0, y1 - y0);
        int dots = (int) (length / DOTS);
        for (int k = 0; k < dots; k++) {
            double t0 = k * DOTS / length;
            double t1 = (k + DOT) * DOTS / length;
            ScreenDrawing.line(shapes, matrix, Mth.lerp(t0, x0, x1), Mth.lerp(t0, y0, y1), Mth.lerp(t1, x0, x1), Mth.lerp(t1, y0, y1), MARKS,
                (float) DOT_WIDTH, colour);
        }
    }

    /** A rectangle shading from one colour along its bottom to another along its top. */
    private static void gradient(VertexConsumer shapes, Matrix4f matrix, double x0, double y0, double x1, double y1, float z, int bottom, int top) {
        ScreenDrawing.vertex(shapes, matrix, x0, y0, z, bottom);
        ScreenDrawing.vertex(shapes, matrix, x1, y0, z, bottom);
        ScreenDrawing.vertex(shapes, matrix, x1, y1, z, top);
        ScreenDrawing.vertex(shapes, matrix, x0, y1, z, top);
    }

    /**
     * A filled triangle, its corners given anticlockwise, as every shape on the panel is wound, the last given twice to
     * make the shapes' four corners.
     */
    private static void triangle(VertexConsumer shapes, Matrix4f matrix, double ax, double ay, double bx, double by, double cx, double cy, float z, int colour) {
        ScreenDrawing.vertex(shapes, matrix, ax, ay, z, colour);
        ScreenDrawing.vertex(shapes, matrix, bx, by, z, colour);
        ScreenDrawing.vertex(shapes, matrix, cx, cy, z, colour);
        ScreenDrawing.vertex(shapes, matrix, cx, cy, z, colour);
    }

    private static double sin(double degrees) {
        return Math.sin(Math.toRadians(degrees));
    }

    private static double cos(double degrees) {
        return Math.cos(Math.toRadians(degrees));
    }
}
