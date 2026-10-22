package dev.aerolarge.block;

import dev.aerolarge.compat.SableFlight;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Landing near the pin, for the autopilot set to fly to a pin and land there (see {@link AutopilotBlockEntity}). It
 * goes in stages:
 * <ol>
 *     <li>Searching: the airliner flies straight to the pin and on over it, then turns back and flies over it again,
 *     back and forth, so that the land round it comes to be loaded (the game only loads the land near the players
 *     aboard, and a circle round the pin, which an airliner flies wide, would never pass over it). Meanwhile the land
 *     is surveyed as it comes (see {@link GroundSurvey}), the airliner measured (see {@link AirlinerShape}), and a
 *     runway looked for near the pin (see {@link RunwaySearch}).</li>
 *     <li>Entry: with a runway found, it slows to its approach speed and flies to a point far back along the runway's
 *     centre line, coming down to {@link #APPROACH_HEIGHT} over the runway on the way, and never lower than
 *     {@link #TERRAIN_CLEARANCE} over the land under it. Coming from the runway's side of that point, it first flies
 *     out to one side and further back, so it comes to the point from behind, not with the runway behind it.</li>
 *     <li>Final: it follows the centre line, steering for a point {@link #LOOKAHEAD_AIR} along it, and comes down a
 *     glide path, sinking at the rate the plain landing sinks at, aimed so the flare that follows ends at the
 *     touchdown point. The tower clears it to land and the cabin hears the landing announcement as it turns onto it.</li>
 *     <li>Landing: from where the flare starts it lands as the plain landing does, flaring over the lower of the
 *     ground under it and the runway, still steering along the centre line, and on the ground steering the nose
 *     wheel along it while it brakes to a stop.</li>
 * </ol>
 * Not lined up with the runway, or too high, as the flare should start, it goes around: back to the entry, to try
 * again.
 */
final class PinLanding {
    /** Where it is in landing near the pin. */
    enum Stage {
        SEARCHING, ENTRY, FINAL, LANDING
    }

    /** What happened this tick that the autopilot has to act on, or say. */
    enum Event {
        NONE, FOUND, NOWHERE, FINAL, FLARE, MISSED
    }

    /**
     * How the autopilot is to fly this tick: the heading; the height to hold (where the autopilot is, in world blocks),
     * or instead the rate to climb at (NaN for none, holding the height); how high the wheels are over the runway;
     * and whether it is on final, coming down the glide path.
     */
    record Guide(double heading, double altitude, double climb, double aboveRunway, boolean onFinal) {
    }

    /** Within this many blocks of the pin, searching, it has flown over it, and flies on past it the way it goes. */
    private static final double OVERHEAD = 150;
    /** How far past the pin, searching, it flies on before it turns back to fly over it again, in blocks. */
    private static final double SURVEY_REACH = 600;
    /** The height over the runway it comes down to before the glide path, in blocks, at most. */
    static final double APPROACH_HEIGHT = 60;
    /** The least it comes down to over the land under it on its way to the runway, in blocks. */
    private static final double TERRAIN_CLEARANCE = 40;
    /**
     * How far it flies along the centre line before the glide path starts, in blocks: room to settle onto the line
     * after turning onto it, the turn wide at the approach speed.
     */
    private static final double FINAL_SETTLE = 600;
    /** How far out and back the point it turns from, coming from the runway's side, lies, in turns' radii. */
    private static final double TURN_ROOM = 3;
    /** Within this many blocks of the point at the start of final it turns onto the centre line. */
    private static final double ENTRY_CAPTURE = 300;
    /**
     * How far along the centre line it steers for, ahead of where it is beside it, in blocks: in the air, in the flare,
     * and on the ground. The further, the gentler it comes back onto the line.
     */
    private static final double LOOKAHEAD_AIR = 300;
    private static final double LOOKAHEAD_FLARE = 150;
    private static final double LOOKAHEAD_GROUND = 60;
    /** The steepest it heads in toward the centre line, in degrees off the runway's heading. */
    private static final double MAX_INTERCEPT = 45;
    /** As the flare should start, the most it may be off the centre line, in blocks, and off its heading, in degrees. */
    private static final double LINED_UP = RunwayCheck.CENTRE_LINE_TOLERANCE;
    private static final double LINED_UP_HEADING = 10;
    /** How much higher than the flare's start it may still be there, in blocks, and flare. */
    private static final double TOO_HIGH = 15;

    private final GroundSurvey ground = new GroundSurvey();
    private final AirlinerShape shape = new AirlinerShape();
    private final RunwaySearch search = new RunwaySearch();
    private Stage stage = Stage.SEARCHING;
    @Nullable
    private Runway runway;
    /** The pin, in world blocks; none until aimed. */
    private int pinX = Integer.MIN_VALUE;
    private int pinZ = Integer.MIN_VALUE;
    /** Searching: whether it has flown over the pin, whether it flies on away from it now, and on what heading. */
    private boolean overflown;
    private boolean outbound;
    private double outboundHeading;
    /** Whether it has said there is nowhere to land, so it says so only once. */
    private boolean toldNowhere;
    /** Entry: whether it flies out to the side first, on which side (1 right of the centre line, -1 left), and how far. */
    private boolean viaTurn;
    private double turnSide;
    private double turnRoom;
    /** Entry: how far along the runway final starts (negative: before the touchdown point), once planned. */
    private double entryStart;
    private boolean planned;

    Stage stage() {
        return stage;
    }

    /** The runway it lands on, once it has found one. */
    @Nullable
    Runway runway() {
        return runway;
    }

    /** Whether it is landing on the runway: from the flare on, until it has stopped. */
    boolean landing() {
        return stage == Stage.LANDING && runway != null;
    }

    /** Whether it is on final, coming down the glide path. */
    boolean onFinal() {
        return stage == Stage.FINAL;
    }

    /**
     * Lands near a pin: if it is a new one, from the start, searching; and surveys the land round it, starting anew if
     * the pin is in another chunk.
     */
    void aim(int x, int z) {
        ground.aim(x, z);
        if (x == pinX && z == pinZ) {
            return;
        }
        pinX = x;
        pinZ = z;
        overflown = false;
        outbound = false;
        abandon();
    }

    /** Gives up the runway, if it has one, and starts searching again; the land surveyed is kept. */
    void abandon() {
        stage = Stage.SEARCHING;
        runway = null;
        toldNowhere = false;
        search.restart();
    }

    /** Takes up a landing again where it was saved: on a runway near a pin, at a stage. */
    void resume(Runway saved, Stage at, int x, int z) {
        pinX = x;
        pinZ = z;
        runway = saved;
        stage = at;
        // The way into final is planned again from where the airliner is when it next flies.
        planned = false;
    }

    /**
     * Each tick while it flies to the pin to land there: measures the airliner and surveys the land, searches for a
     * runway, and moves on through the stages. The autopilot is at a position in its plot, its nose pointing the given
     * way there; the bottom of its wheels at a height in the world; the speed is the one it flies the approach at.
     */
    Event tick(ServerLevel level, BlockPos autopilot, Direction nose, SableFlight.State state, double radio, double wheels, double speed) {
        shape.measure(level, autopilot, nose);
        ground.tick(level, state.position());
        if (stage == Stage.ENTRY && !planned) {
            startEntry(state, speed);
        }
        return switch (stage) {
            case SEARCHING -> search(state, speed);
            case ENTRY -> entry(state);
            case FINAL -> fin(state, radio, wheels, speed);
            case LANDING -> Event.NONE;
        };
    }

    /** Searching: back and forth over the pin, and a little more of the search each tick. */
    private Event search(SableFlight.State state, double speed) {
        double distance = Math.hypot(pinX + 0.5 - state.position().x, pinZ + 0.5 - state.position().z);
        if (distance < OVERHEAD && !outbound) {
            overflown = true;
            outbound = true;
            outboundHeading = state.heading();
        } else if (distance > SURVEY_REACH) {
            outbound = false;
        }
        if (!shape.measured()) {
            return Event.NONE;
        }
        RunwaySearch.Outcome outcome = search.step(ground, shape, pinX, pinZ, speed, overflown);
        if (outcome == RunwaySearch.Outcome.FOUND) {
            runway = search.found();
            startEntry(state, speed);
            return Event.FOUND;
        }
        if (outcome == RunwaySearch.Outcome.NOWHERE && !toldNowhere) {
            toldNowhere = true;
            return Event.NOWHERE;
        }
        return Event.NONE;
    }

    /**
     * Starts the entry: straight to the start of final if it is behind it already, else out to the side it is on first.
     */
    private void startEntry(SableFlight.State state, double speed) {
        stage = Stage.ENTRY;
        planned = true;
        Vec3 at = state.position();
        entryStart = -entryDistance(speed);
        viaTurn = runway.along(at) > entryStart;
        turnSide = runway.across(at) >= 0 ? 1 : -1;
        double radius = speed * speed / (Math.max(1, state.gravity()) * Math.tan(Math.toRadians(AutopilotBlockEntity.MAX_BANK)));
        turnRoom = TURN_ROOM * radius;
    }

    /** Entry: on to the start of final, by the side first if need be; turning onto final there, or once abeam it. */
    private Event entry(SableFlight.State state) {
        Vec3 at = state.position();
        if (viaTurn && runway.along(at) < entryStart - turnRoom / 2) {
            viaTurn = false;
        }
        if (viaTurn) {
            return Event.NONE;
        }
        Vec3 entry = runway.point(entryStart, 0);
        boolean near = Math.hypot(entry.x - at.x, entry.z - at.z) < ENTRY_CAPTURE;
        if (!near && runway.along(at) < entryStart) {
            return Event.NONE;
        }
        stage = Stage.FINAL;
        return Event.FINAL;
    }

    /**
     * Final: once the flare should start, or the ground or the runway is that close below, it lands if it is lined up
     * and not too high, else it goes around, back to the entry.
     */
    private Event fin(SableFlight.State state, double radio, double wheels, double speed) {
        Vec3 at = state.position();
        double flareStart = -horizontal(state) * flareTime();
        double along = runway.along(at);
        double above = wheels - runway.elevation();
        if (along < flareStart && Math.min(radio, above) >= AutopilotBlockEntity.FLARE_HEIGHT) {
            return Event.NONE;
        }
        boolean linedUp = Math.abs(runway.across(at)) <= LINED_UP
            && Math.abs(Mth.wrapDegrees(runway.heading() - state.heading())) <= LINED_UP_HEADING;
        boolean onTime = along > flareStart - RunwayCheck.TOUCHDOWN_SCATTER && above < AutopilotBlockEntity.FLARE_HEIGHT + TOO_HIGH;
        if (linedUp && onTime) {
            stage = Stage.LANDING;
            return Event.FLARE;
        }
        startEntry(state, speed);
        return Event.MISSED;
    }

    /**
     * How to fly this tick: the bottom of the wheels at a height in the world, the radio altitude, the height set to
     * hold (where the autopilot is), and whether it is on the ground.
     */
    Guide guide(SableFlight.State state, double radio, double wheels, double cruise, boolean onGround) {
        Vec3 at = state.position();
        return switch (stage) {
            case SEARCHING -> new Guide(outbound ? outboundHeading : headingTo(at, pinX + 0.5, pinZ + 0.5), cruise, Double.NaN, Double.NaN, false);
            case ENTRY -> new Guide(entryHeading(at), entryAltitude(at.y, radio, wheels, cruise), Double.NaN, Double.NaN, false);
            case FINAL -> new Guide(centreLine(at, LOOKAHEAD_AIR), at.y, glide(state, wheels), wheels - runway.elevation(), true);
            case LANDING -> new Guide(centreLine(at, onGround ? LOOKAHEAD_GROUND : LOOKAHEAD_FLARE), cruise, Double.NaN,
                wheels - runway.elevation(), false);
        };
    }

    /** Entry: the heading to the point it turns from, or to the start of final. */
    private double entryHeading(Vec3 at) {
        Vec3 aim = viaTurn ? runway.point(entryStart - turnRoom, turnSide * turnRoom) : runway.point(entryStart, 0);
        return headingTo(at, aim.x, aim.z);
    }

    /**
     * Entry: the height to come down to, where the autopilot is: {@link #APPROACH_HEIGHT} over the runway, or the height
     * set if that is lower, but never lower than {@link #TERRAIN_CLEARANCE} over the land under it.
     */
    private double entryAltitude(double y, double radio, double wheels, double cruise) {
        double wheelsUnder = y - wheels;
        double approach = runway.elevation() + APPROACH_HEIGHT + wheelsUnder;
        double terrain = wheels - radio + TERRAIN_CLEARANCE + wheelsUnder;
        return Math.max(Math.min(cruise, approach), terrain);
    }

    /**
     * Final: the rate to climb at (negative: to sink) to follow the glide path: down at the rate the plain landing
     * sinks at, as fast as it goes, to reach the flare's height where the flare should start, and toward the path as
     * the height wanted does; level at {@link #APPROACH_HEIGHT} until the path comes down to that.
     */
    private double glide(SableFlight.State state, double wheels) {
        double speed = horizontal(state);
        double flareStart = -speed * flareTime();
        double toFlare = Math.max(0, flareStart - runway.along(state.position()));
        double slope = AutopilotBlockEntity.FLARE_HEIGHT + toFlare * AutopilotBlockEntity.LANDING_SINK_RATE / speed;
        double path = Math.min(slope, APPROACH_HEIGHT);
        double sinking = slope < APPROACH_HEIGHT ? AutopilotBlockEntity.LANDING_SINK_RATE : 0;
        double above = wheels - runway.elevation();
        return Mth.clamp(AutopilotBlockEntity.HEIGHT_GAIN * (path - above) - sinking,
            -AutopilotBlockEntity.MAX_SINK_RATE, AutopilotBlockEntity.MAX_CLIMB_RATE);
    }

    /**
     * The heading that brings it onto the centre line and along it: toward a point a distance along the line ahead of
     * where it is beside it, no more than {@link #MAX_INTERCEPT} degrees off the runway's heading.
     */
    private double centreLine(Vec3 at, double lookahead) {
        double intercept = Math.toDegrees(Math.atan2(runway.across(at), lookahead));
        return runway.heading() - Mth.clamp(intercept, -MAX_INTERCEPT, MAX_INTERCEPT);
    }

    /** The heading from a point to another, as Minecraft yaw. */
    private static double headingTo(Vec3 at, double x, double z) {
        return Math.toDegrees(Math.atan2(-(x - at.x), z - at.z));
    }

    /** How fast it goes over the ground, in blocks a second, at least one. */
    private static double horizontal(SableFlight.State state) {
        return Math.max(1, Math.hypot(state.velocity().x, state.velocity().z));
    }

    /**
     * How far back from the touchdown point final starts, in blocks, at a speed: the flare's length, the glide path's
     * from {@link #APPROACH_HEIGHT}, and {@link #FINAL_SETTLE}.
     */
    private static double entryDistance(double speed) {
        double glide = (APPROACH_HEIGHT - AutopilotBlockEntity.FLARE_HEIGHT) / AutopilotBlockEntity.LANDING_SINK_RATE;
        return speed * (flareTime() + glide) + FINAL_SETTLE;
    }

    /**
     * How long the flare takes, in seconds, as the autopilot flies it: sinking ever more slowly from
     * {@link AutopilotBlockEntity#FLARE_HEIGHT}, then, once down to its touchdown rate, steadily at that rate to the
     * ground.
     */
    static double flareTime() {
        double knee = AutopilotBlockEntity.TOUCHDOWN_SINK / AutopilotBlockEntity.FLARE_GAIN;
        double steady = knee / AutopilotBlockEntity.TOUCHDOWN_SINK;
        return steady + Math.log(AutopilotBlockEntity.FLARE_HEIGHT / knee) / AutopilotBlockEntity.FLARE_GAIN;
    }

    /**
     * How high the wheels are over the runway a distance before touchdown, in blocks, coming in at a speed: in the
     * flare as the autopilot flies it, and before it on the glide path.
     */
    static double wheelHeight(double before, double speed) {
        double seconds = before / Math.max(1, speed);
        double knee = AutopilotBlockEntity.TOUCHDOWN_SINK / AutopilotBlockEntity.FLARE_GAIN;
        double steady = knee / AutopilotBlockEntity.TOUCHDOWN_SINK;
        if (seconds <= steady) {
            return AutopilotBlockEntity.TOUCHDOWN_SINK * seconds;
        }
        double flare = flareTime();
        if (seconds <= flare) {
            return knee * Math.exp(AutopilotBlockEntity.FLARE_GAIN * (seconds - steady));
        }
        return AutopilotBlockEntity.FLARE_HEIGHT + AutopilotBlockEntity.LANDING_SINK_RATE * (seconds - flare);
    }
}
