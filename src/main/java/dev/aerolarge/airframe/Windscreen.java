package dev.aerolarge.airframe;

import static dev.aerolarge.airframe.AirframeShape.*;

/**
 * The flight deck's windscreen on the nose: where its glass goes and where the posts stand that frame it into panes.
 * The glass wraps right round the front of the nose between two heights, from the panel up over the sloping crown,
 * and runs back along both sides into side windows beside and behind the pilots. There is no post down the middle,
 * so the pilots look out through one wide windscreen; two posts round each side, both outboard of the pilots' view
 * ahead, part it from the side windows. Heights and stations are in metres.
 */
public final class Windscreen {
    /**
     * The heights the glass spans, as shares of the fuselage height: from the top of the flight deck's floor, so the
     * panel stands in front of the lowest row, up the crown to well above the pilots' heads.
     */
    static final double LOW = 0.56 * HEIGHT;
    static final double HIGH = 0.78 * HEIGHT;
    /** The pilots' eyes are at this height, low in the glass, so they look straight out over the panel. */
    static final double EYES = 0.63 * HEIGHT;
    /** How far back from the nose tip the glass reaches along the sides: past the pilots' shoulders. */
    static final double REAR = 0.72 * NOSE_LENGTH;
    /** The flight deck floor's top lies this many blocks below the pilots' eyes: a player's eye height. */
    static final double EYE_HEIGHT = 1.62;
    /** The side posts, by which way the skin faces where they stand: degrees round from straight ahead. */
    static final double[] SIDE_POSTS = {70, 88};
    /** A post is only put where the skin faces within this many degrees of it. */
    static final double POST_TOLERANCE = 15;

    private Windscreen() {
    }

    /** Whether a hull block centred at this height and station lies under the glass. */
    static boolean covers(double y, double z) {
        return y >= LOW && y <= HIGH && z <= REAR;
    }

    /** The flight deck floor's block row at this scale, so the pilots look straight out through the glass. */
    static int deckRow(double scale) {
        return (int) Math.round(EYES * scale - EYE_HEIGHT) - 1;
    }
}
