package dev.skyliners.client;

import net.minecraft.util.Mth;

/**
 * The game's time as the orrery and its dome are drawn by it, so they move the same for everyone: whole ticks and the
 * share of the next, kept apart so that nothing loses its smoothness however old the world is.
 */
record OrreryClock(long time, float partial) {
    /** How long the twinkling and breathing go on before they start over, in ticks: an hour of the game's time. */
    private static final long LOOP = 72000;

    /** The clock at a time, its whole ticks and the share of the next told apart. */
    static OrreryClock at(double time) {
        long whole = (long) Math.floor(time);
        return new OrreryClock(whole, (float) (time - whole));
    }

    /** How far round something going round once in so many ticks has come, in radians; backward for a negative time. */
    float turned(float period) {
        return (float) ((time % (long) period + partial) / period * Mth.TWO_PI);
    }

    /** The time in ticks for what only rises and falls, starting over every hour of the game's time. */
    float ticks() {
        return time % LOOP + partial;
    }

    /** How many ticks have gone by since a moment of the game's time. */
    float since(long moment) {
        return time - moment + partial;
    }
}
