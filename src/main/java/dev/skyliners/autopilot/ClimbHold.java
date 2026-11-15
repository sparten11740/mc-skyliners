package dev.skyliners.autopilot;

import net.minecraft.util.Mth;

/**
 * How the airliner's nose is set and held to climb or sink at a rate, and her wings to a bank, shared by her
 * autopilot's flying (see {@code AutopilotBlockEntity.fly}) and her landing assist (see {@link LandingAssist}), each
 * with a hold of its own: the pitch asked for from how far her climb is off, round the trim she climbs at a level rate
 * from, learnt as she flies; the push on her nose toward that pitch, round the push she needs to stay there, learnt as
 * well; the push on her wings toward a bank; and, landing, the sink eased into from the rate she sank at.
 */
final class ClimbHold {
    /**
     * Degrees of pitch added per block a second she climbs too slowly, taken off per block a second each second she is
     * gaining climb (damping).
     */
    private static final double CLIMB_GAIN = 0.8;
    private static final double CLIMB_DAMPING = 0.5;
    /**
     * How fast the trim learns, in degrees a tick per block a second the climb is off, counting no more than this
     * many blocks a second off, so a big change of height learns no faster than a small one; and only while the nose
     * is within this many degrees of where it is wanted and not held at either end of its range: while the nose is
     * still swinging round, or pinned at a limit, learning would only wind it up.
     */
    private static final double TRIM_RATE = 0.01;
    private static final double TRIM_CLIMB_LIMIT = 3;
    private static final double TRIM_WINDOW = 3;
    /**
     * How fast the push the nose needs to stay where it is wanted is learnt, in signal a tick per degree it is off, and
     * the most that is learnt. Pushed back only as hard as it is off, the nose would rest off wherever the airliner
     * pitches by itself, as far off as it needs to be pushed back; and the trim, which only learns with the nose near
     * where it is wanted, might then never learn the climb at all, the airliner holding a height off the one set.
     */
    private static final double PITCH_HOLD_RATE = 0.01;
    private static final double PITCH_HOLD_LIMIT = 8;
    /** Signal per degree of pitch or bank error, and per degree a second it is changing at (damping). */
    private static final double PITCH_GAIN = 0.75;
    private static final double PITCH_DAMPING = 0.4;
    private static final double ROLL_GAIN = 0.5;
    private static final double ROLL_DAMPING = 0.3;
    /**
     * However she is to sink landing, she sinks faster by no more than this, in blocks a second every second, so the
     * nose goes down gently rather than all at once as she starts to land.
     */
    private static final double LANDING_SINK_EASE = 0.5;
    /** Ticks in a second. */
    private static final double TICKS = 20;
    /** The pitch she climbs at a level rate from, in degrees, learnt as she flies. */
    double trim;
    /** The push her nose needs to stay where it is wanted, in signal, learnt as she flies. */
    double pitchHold;
    /**
     * Landing, the rate she is set to sink at this tick, in blocks a second, eased into; NaN while she is not landing.
     * Not saved: landing on after the world loads, it starts from the rate she sinks at.
     */
    private double sinkCommand = Double.NaN;

    /**
     * The pitch to hold for a climb so far off, in blocks a second, gaining climb so fast, in blocks a second each
     * second, between the lowest and highest pitch given; the trim learnt toward it while her nose, at the pitch given,
     * is near it and not held at either end, kept no higher than the highest trim given.
     */
    double pitchFor(double climbError, double climbRate, double pitch, double lowest, double highest,
                    double highestTrim) {
        double target = Mth.clamp(trim + CLIMB_GAIN * climbError - CLIMB_DAMPING * climbRate, lowest, highest);
        boolean pinned = target <= lowest || target >= highest;
        if (!pinned && Math.abs(target - pitch) < TRIM_WINDOW) {
            double step = TRIM_RATE * Mth.clamp(climbError, -TRIM_CLIMB_LIMIT, TRIM_CLIMB_LIMIT);
            trim = Mth.clamp(trim + step, lowest, highestTrim);
        }
        return target;
    }

    /**
     * The push on her nose toward a pitch, from the pitch it is at, turning so fast, in degrees a second; the push it
     * needs learnt in the air, kept right down to the touchdown, and let go on the ground, where the wheels hold it.
     */
    double pitchPush(double targetPitch, double pitch, double pitchRate, boolean onGround) {
        double error = targetPitch - pitch;
        pitchHold = onGround ? 0 : Mth.clamp(pitchHold + PITCH_HOLD_RATE * error, -PITCH_HOLD_LIMIT, PITCH_HOLD_LIMIT);
        return pitchHold + PITCH_GAIN * error - PITCH_DAMPING * pitchRate;
    }

    /** The push on her nose back by so many degrees, turning so fast, in degrees a second, with nothing learnt. */
    static double pitchBack(double error, double pitchRate) {
        return PITCH_GAIN * error - PITCH_DAMPING * pitchRate;
    }

    /** The push on her wings toward a bank so many degrees off, rolling so fast, in degrees a second. */
    static double rollPush(double error, double rollRate) {
        return ROLL_GAIN * error - ROLL_DAMPING * rollRate;
    }

    /**
     * The rate she sinks at landing this tick, in blocks a second, the rate wanted for her height given and climbing at
     * the rate given: rising to it from the rate she sank at as she started landing no faster than {@link
     * #LANDING_SINK_EASE}; sinking less, as she eases out over the ground and flares, she does at once.
     */
    double easedSink(double wanted, double climb) {
        double from = Double.isNaN(sinkCommand) ? Math.max(0, -climb) : sinkCommand;
        sinkCommand = Math.min(wanted, from + LANDING_SINK_EASE / TICKS);
        return sinkCommand;
    }

    /** Forgets the sink eased into, as she stops landing. */
    void forgetSink() {
        sinkCommand = Double.NaN;
    }
}
