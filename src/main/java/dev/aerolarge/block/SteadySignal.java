package dev.aerolarge.block;

/**
 * A signal the autopilot sends its relays, held steady while what it asks for wobbles a little: the control jets on
 * one axis, the engines' thrust, or how far the engines on one side are throttled back to turn. Each change rewrites
 * every relay it reaches, and each rewrite costs: the thrusters beside it update, Sable weighs the airliner afresh,
 * and for a relay the clients are sent, every client redraws that part of the airliner. Rounded straight off, the
 * signals would flip many times a second for no flying worth having: the pitch and the thrust the autopilot learns
 * hold the airliner somewhere between two steps, so they would dither between them for good.
 *
 * <p>So, followed {@link Mode#STEADY steadily}, a signal changes only once what is asked for has moved
 * {@link #HYSTERESIS} of a step past it, a little further than rounding alone would let it, and then no sooner than its
 * rest after its last change. A change of {@link #LEAP} steps or more goes at once, as does a cut of the thrust to
 * idle. The thrusters push on the airliner as a whole, so a step held a little longer evens out in how it flies. Held
 * much further, it would not: the speed would swing between the thrust's steps, and the nose between the jets', by
 * enough to set an orbit wandering off its circle.
 */
final class SteadySignal {
    /**
     * How far past the signal sent what is asked for must move before it changes, in steps: rounding alone would change
     * it at half a step.
     */
    private static final double HYSTERESIS = 0.6;
    /** A change this many steps or more is sent at once, however soon after the last. */
    private static final int LEAP = 2;
    private static final int FULL = 15;
    /**
     * The fewest ticks between the jets' changes, and between the engines': the jets hold the attitude, which swings
     * in moments, while the engines hold the speed, which changes slowly.
     */
    private static final int JET_REST = 2;
    private static final int ENGINE_REST = 10;

    /** How closely a signal follows what is asked for. */
    enum Mode {
        /**
         * Exactly, every tick, rounded: on the runway, flaring to land, as it lifts off or touches down, and righting
         * the airliner from far past the safe attitude.
         */
        EXACT,
        /** Steadily, through small wobbles in what is asked for. */
        STEADY,
        /**
         * Steadily, and only from a whole step off nothing: what is asked for only damps the airliner's swaying, and a
         * slow sway is let be.
         */
        DAMPING
    }

    /** The lowest signal: the full signal the other way for a signed one, nothing for the thrust. */
    private final int lowest;
    /** The fewest ticks between changes but those sent at once. */
    private final int rest;
    /** Whether a cut to nothing is sent at once. */
    private final boolean cutsAtOnce;
    private int signal;
    /** The game time it may change again; until then only a change sent at once goes. */
    private long restUntil = Long.MIN_VALUE;

    private SteadySignal(boolean signed, int rest, boolean cutsAtOnce) {
        this.lowest = signed ? -FULL : 0;
        this.rest = rest;
        this.cutsAtOnce = cutsAtOnce;
    }

    /** The control jets on one axis: up or to the right positive, so the jets either way never fire together. */
    static SteadySignal jets() {
        return new SteadySignal(true, JET_REST, false);
    }

    /** The engines' thrust; cut to idle at once. */
    static SteadySignal thrust() {
        return new SteadySignal(false, ENGINE_REST, true);
    }

    /**
     * How far the engines on one side are throttled back to turn, positive for the right: one signal for both sides,
     * so that the engines stay alike while it flies straight.
     */
    static SteadySignal split() {
        return new SteadySignal(true, ENGINE_REST, false);
    }

    /** The signal to send this tick, for what is asked for (not rounded), at this game time. */
    int follow(double wanted, long now, Mode mode) {
        double asked = Math.clamp(wanted, lowest, FULL);
        int rounded = (int) Math.round(asked);
        if (rounded == signal || mode != Mode.EXACT && !moves(asked, rounded, now, mode)) {
            return signal;
        }
        signal = rounded;
        restUntil = now + rest;
        return signal;
    }

    /** Whether, followed steadily, the signal moves to the rounded one this tick. */
    private boolean moves(double asked, int rounded, long now, Mode mode) {
        if (mode == Mode.DAMPING && signal == 0 && Math.abs(asked) < 1) {
            return false;
        }
        if (Math.abs(asked - signal) < HYSTERESIS) {
            return false;
        }
        return Math.abs(rounded - signal) >= LEAP || cutsAtOnce && rounded == 0 || now >= restUntil;
    }

    /** Back to nothing, free to change at once: the relays have all been set to nothing. */
    void reset() {
        signal = 0;
        restUntil = Long.MIN_VALUE;
    }
}
