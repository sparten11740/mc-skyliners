package dev.aerolarge.autopilot;

/**
 * Who has the floor on the flight deck: the callouts, the cabin announcements and air traffic control share it, so that
 * none of them talks over another. Whatever starts saying something takes it for as long as that lasts. The cabin
 * announcements and air traffic control wait for it; the callouts, which cannot wait, only take it.
 */
final class Airtime {
    /** When what is being said ends, in game time. */
    private long until;

    /** Whether nothing is being said at this game time. */
    boolean free(long now) {
        return now >= until;
    }

    /** Whether nothing has been said for at least this many ticks before this game time. */
    boolean quietFor(long now, int ticks) {
        return now >= until + ticks;
    }

    /** Something starts being said at this game time, lasting this many ticks. */
    void take(long now, int ticks) {
        until = Math.max(until, now + ticks);
    }
}
