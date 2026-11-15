package dev.skyliners.autopilot;

import dev.skyliners.autopilot.AutopilotBlockEntity.Channel;
import java.util.EnumMap;
import java.util.Map;

/**
 * The airliner's engines winding up, as a jet's do: asked for more thrust, by her thrust levers or her autopilot, an
 * engine comes up to it over seconds rather than at once, slowly at first and faster as it comes up, idle to full in
 * the server config's {@code autopilot.engineSpoolSeconds}; asked for less, it drops at once. Each engine's thrust is
 * the total of its relays' signals (see {@link AutopilotBlockEntity#driveEngine}); what it winds up from is what its
 * relays show, so it carries on from wherever they stand after loading or after something else set them. Not saved.
 */
final class EngineSpool {
    /**
     * How fast an engine winds up at idle, as a share of how fast it winds up one share of full thrust higher: low, so
     * it starts slowly and gathers pace as it comes up, a turbofan's spool catching.
     */
    private static final double AT_IDLE = 0.2;
    /** Ticks in a second. */
    private static final double TICKS = 20;
    /** Each engine's thrust as it winds up, in its relays' signal, between the whole steps its relays are set to. */
    private final Map<Channel, Double> wound = new EnumMap<>(Channel.class);
    /** Whether any engine was still short of the thrust asked of it, as last set. */
    private boolean winding;

    /**
     * The total of an engine's relays' signals to set now: what they show, brought toward what is asked of it this
     * tick, out of the most they give, wound up over so many seconds from idle to full; at once for none.
     */
    int toward(Channel engine, int shown, int asked, int most, double seconds) {
        Double was = wound.get(engine);
        double from = was == null || Math.abs(was - shown) >= 1 ? shown : was;
        if (asked <= from || seconds <= 0 || most <= 0) {
            wound.put(engine, (double) asked);
            return asked;
        }
        double share = from / most;
        double step = most * (AT_IDLE + share) * Math.log((AT_IDLE + 1) / AT_IDLE) / (seconds * TICKS);
        double next = Math.min(asked, from + step);
        wound.put(engine, next);
        winding |= next < asked;
        return (int) Math.floor(next);
    }

    /** Whether any engine was still short of the thrust asked of it last tick, looked at afresh from this one on. */
    boolean takeWinding() {
        boolean was = winding;
        winding = false;
        return was;
    }
}
