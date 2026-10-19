package dev.aerolarge.airframe;

import static dev.aerolarge.airframe.AirframeShape.*;

/**
 * The flight deck's windscreen on the nose: where its glass goes and where the posts stand that divide it into panes.
 * The glass wraps around the sloping crown between two heights, from the centreline back along both sides; a post
 * runs down the middle and two more round each side, the way the A380's is framed. Heights and stations are in
 * metres.
 */
public final class Windscreen {
    /** The heights the glass spans, as shares of the fuselage height: around the pilots' eyes on the flight deck. */
    static final double LOW = 0.56 * HEIGHT;
    static final double HIGH = 0.70 * HEIGHT;
    /** How far back from the nose tip the glass reaches along the sides. */
    static final double REAR = 0.62 * NOSE_LENGTH;
    /** The flight deck floor's top lies this many blocks below the middle of the glass: a player's eye height. */
    static final double EYE_HEIGHT = 1.62;
    /** The side posts, by which way the skin faces where they stand: degrees round from straight ahead. */
    static final double[] SIDE_POSTS = {50, 75};
    /** A post is only put where the skin faces within this many degrees of it. */
    static final double POST_TOLERANCE = 15;

    private Windscreen() {
    }

    /** Whether a hull block centred at this height and station lies under the glass. */
    static boolean covers(double y, double z) {
        return y >= LOW && y <= HIGH && z <= REAR && z >= front(HIGH) - 0.5;
    }

    /** The flight deck floor's block row at this scale, so the pilots look straight out through the glass. */
    static int deckRow(double scale) {
        return (int) Math.round((LOW + HIGH) / 2 * scale - EYE_HEIGHT) - 1;
    }

    /** Where the crown reaches height y: the front edge of the glass at that height, on the centreline. */
    static double front(double y) {
        double tip = NOSE_TIP * HEIGHT;
        double risen = Math.min(1, Math.max(0, (y - tip) / (HEIGHT - tip)));
        return NOSE_LENGTH * (1 - Math.sqrt(1 - risen));
    }
}
