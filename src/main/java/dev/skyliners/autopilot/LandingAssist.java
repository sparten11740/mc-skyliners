package dev.skyliners.autopilot;

import dev.skyliners.SkylinersConfig;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.util.Mth;

/**
 * A hand to land the airliner by: flown by hand with her thrust levers back at idle, low over the ground and not yet
 * down, she was left to slow until her wings no longer held her, and fell nose first. Instead, the autopilot's landing
 * steers her down, its sink and pitch held as the autopilot's own landing holds them (see {@link ClimbHold}):
 * her nose set for a sink that eases off the nearer the ground, flaring over it (see {@link
 * AutopilotBlockEntity#landingSink}), her wings held level, her engines holding her at her rotation speed, so her wings
 * keep lifting, and her landing gear down. It takes her only coming down and flying, never hanging slowly, and lets go
 * at once as she touches down, as the levers come up off idle, or as she climbs out of its reach; none with the server
 * config's {@code autopilot.landingAssist} off.
 */
final class LandingAssist {
    /** How far over the ground it takes her, in blocks: low enough that she is plainly coming in to land. */
    private static final double HIGHEST = 60;
    /** How fast it holds her coming in, as a share of her rotation speed. */
    private static final double APPROACH = 1.0;
    /** The slowest she must fly for it to take her, as a share of her rotation speed: slower, she is not landing. */
    private static final double SLOWEST = 0.5;
    /**
     * How fast she must be sinking for it to take her, in blocks a second; once it has, it keeps her through the flare.
     */
    private static final double SINKING = 0.5;
    /** Ticks in a second. */
    private static final double TICKS = 20;
    /**
     * How her nose is set and held to sink, as the autopilot's is, with a trim and a push learnt afresh each landing.
     */
    private final ClimbHold hold = new ClimbHold();
    /** Whether it has her. */
    private boolean on;
    /** Her rate of climb last tick, in blocks a second. */
    private double lastClimb;

    /**
     * What it steers her with this tick: the push on her nose and on her wings, in the jets' signal, and her thrust.
     */
    record Steer(double pitch, double roll, double thrust) {
    }

    /**
     * Whether it takes her this tick: switched on, her thrust levers at idle (a signal of 0; less for a craft without
     * them, which it never takes), in the air and low enough over the ground, flying at least {@link #SLOWEST} of her
     * rotation speed, and sinking, unless it has her already.
     */
    boolean wanted(double radio, int leverSignal, double speed, double climb) {
        boolean low = radio > Callouts.ON_GROUND && radio < HIGHEST;
        boolean landing = speed > SLOWEST * SkylinersConfig.AUTOPILOT_ROTATE_SPEED.get() && (on || climb < -SINKING);
        return SkylinersConfig.LANDING_ASSIST.get() && leverSignal == 0 && low && landing;
    }

    /** Whether it has her, as last steered. */
    boolean on() {
        return on;
    }

    /**
     * Steers her this tick toward the sink her height wants, in blocks a second, her nose held between the lowest and
     * highest pitch given, at a pitch turning so fast and her wings at a roll turning so fast, in degrees, climbing at
     * so many blocks a second: thrust from what holds her at a speed.
     */
    Steer steer(double wantedSink, double pitch, double pitchRate, double roll, double rollRate, double climb,
                double lowest, double highest, DoubleUnaryOperator thrustFor) {
        if (!on) {
            on = true;
            hold.trim = Mth.clamp(pitch, lowest, highest);
            hold.pitchHold = 0;
            hold.forgetSink();
            lastClimb = climb;
        }
        double climbError = -hold.easedSink(wantedSink, climb) - climb;
        double climbRate = (climb - lastClimb) * TICKS;
        lastClimb = climb;
        double targetPitch = hold.pitchFor(climbError, climbRate, pitch, lowest, highest, highest);
        double pitchPush = hold.pitchPush(targetPitch, pitch, pitchRate, false);
        double rollPush = ClimbHold.rollPush(-roll, rollRate);
        double approach = APPROACH * SkylinersConfig.AUTOPILOT_ROTATE_SPEED.get();
        return new Steer(pitchPush, rollPush, thrustFor.applyAsDouble(approach));
    }

    /** Lets go of her, so it starts afresh next time. */
    void letGo() {
        on = false;
    }
}
