package dev.skyliners.autopilot;

import dev.skyliners.SkylinersConfig;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.util.Mth;

/**
 * A hand to land the airliner by: flown by hand with her thrust levers back at idle, low over the ground and not yet
 * down, she was left to slow until her wings no longer held her, and fell nose first. Instead, the autopilot's landing
 * steers her down, its sink and pitch as the autopilot's own landing has them (see {@code AutopilotBlockEntity.fly}):
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
    /** Whether it has her. */
    private boolean on;
    /**
     * The push its pitch learns to hold her nose where it wants it, as the autopilot's does; none while it is not on.
     */
    private double pitchHold;
    /** The pitch it climbs at a level rate from, learnt as she comes in, in degrees; NaN while it is not on. */
    private double trim = Double.NaN;
    /** The rate it last let her sink at, in blocks a second, eased onto; NaN before it has. */
    private double sinkCommand = Double.NaN;
    /** Her rate of climb last tick, in blocks a second; NaN before it had one. */
    private double lastClimb = Double.NaN;

    /**
     * What it steers her with this tick: the push on her nose and on her wings, in the jets' signal, and her thrust.
     */
    record Steer(double pitch, double roll, double thrust) {
    }

    /**
     * Whether it takes her this tick: switched on, her levers at idle, in the air and low enough over the ground,
     * flying at least {@link #SLOWEST} of her rotation speed, and sinking, unless it has her already.
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
     * Steers her this tick, so high over the ground, her nose at a pitch turning so fast and her wings at a roll
     * turning so fast, in degrees, climbing and flying so fast, in blocks a second: thrust from what holds her at a
     * speed.
     */
    Steer steer(double radio, double pitch, double pitchRate, double roll, double rollRate, double climb, double speed,
                DoubleUnaryOperator thrustFor) {
        if (!on) {
            on = true;
            trim = Mth.clamp(pitch, AutopilotBlockEntity.MIN_PITCH, AutopilotBlockEntity.ROTATION_PITCH);
            lastClimb = climb;
        }
        double from = Double.isNaN(sinkCommand) ? Math.max(0, -climb) : sinkCommand;
        double eased = from + AutopilotBlockEntity.LANDING_SINK_EASE / TICKS;
        sinkCommand = Math.min(AutopilotBlockEntity.landingSink(radio), eased);
        double climbError = -sinkCommand - climb;
        double climbRate = (climb - lastClimb) * TICKS;
        lastClimb = climb;
        double wanted = trim + AutopilotBlockEntity.CLIMB_GAIN * climbError
            - AutopilotBlockEntity.CLIMB_DAMPING * climbRate;
        double targetPitch = Mth.clamp(wanted, AutopilotBlockEntity.MIN_PITCH, AutopilotBlockEntity.ROTATION_PITCH);
        learnTrim(targetPitch, wanted, pitch, climbError);
        double pitchError = targetPitch - pitch;
        double limit = AutopilotBlockEntity.PITCH_HOLD_LIMIT;
        pitchHold = Mth.clamp(pitchHold + AutopilotBlockEntity.PITCH_HOLD_RATE * pitchError, -limit, limit);
        double pitchPush = pitchHold + AutopilotBlockEntity.PITCH_GAIN * pitchError
            - AutopilotBlockEntity.PITCH_DAMPING * pitchRate;
        double rollPush = -AutopilotBlockEntity.ROLL_GAIN * roll - AutopilotBlockEntity.ROLL_DAMPING * rollRate;
        double approach = APPROACH * SkylinersConfig.AUTOPILOT_ROTATE_SPEED.get();
        return new Steer(pitchPush, rollPush, thrustFor.applyAsDouble(approach));
    }

    /**
     * Learns the pitch she holds a level rate at, while her nose is near where it is wanted and not held at a limit.
     */
    private void learnTrim(double targetPitch, double wanted, double pitch, double climbError) {
        if (targetPitch != wanted || Math.abs(targetPitch - pitch) >= AutopilotBlockEntity.TRIM_WINDOW) {
            return;
        }
        double most = AutopilotBlockEntity.TRIM_CLIMB_LIMIT;
        double learnt = trim + AutopilotBlockEntity.TRIM_RATE * Mth.clamp(climbError, -most, most);
        trim = Mth.clamp(learnt, AutopilotBlockEntity.MIN_PITCH, AutopilotBlockEntity.ROTATION_PITCH);
    }

    /** Lets go of her, forgetting what it learnt, so it starts afresh next time. */
    void letGo() {
        on = false;
        pitchHold = 0;
        trim = Double.NaN;
        sinkCommand = Double.NaN;
        lastClimb = Double.NaN;
    }
}
