package dev.aerolarge.airframe;

/**
 * A feature laid into the rocket once its body stands (see {@link RocketLayout}): a room on a story, the lift, the
 * cockpit's screens. Each is a class of its own and a line in the layout's list, so taking one out is taking out the
 * two, and each may be switched off by the server's config.
 */
interface RocketPiece {
    /** Whether it is laid at all, as the server's config has it. */
    default boolean wanted() {
        return true;
    }

    /** Lays it into the rocket. */
    void build();
}
