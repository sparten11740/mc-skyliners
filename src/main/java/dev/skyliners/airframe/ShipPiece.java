package dev.skyliners.airframe;

/**
 * A feature laid into a ship once its hull stands: a room, a lift, a cockpit's screens. Each is a class of its own and
 * a line in its ship's list of pieces, so taking one out is taking out the two, and each may be switched off by the
 * server's config.
 */
interface ShipPiece {
    /** Whether it is laid at all, as the server's config has it. */
    default boolean wanted() {
        return true;
    }

    /** Lays it into the ship. */
    void build();
}
