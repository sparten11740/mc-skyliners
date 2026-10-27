package dev.aerolarge.airframe;

/**
 * A feature's part of the zeppelin's layout, laid once the hull itself stands: the Sky Deck, the gallery inside a gas
 * cell, the furnished decks, and every feature added since. Each is a class of its own, made for the layout it lays
 * into and listed once, in the order they are laid, in {@link ZeppelinLayout#create}; taking a feature out of the
 * zeppelin is taking out its class and its line there. A piece is a leaf: nothing else is held to the zeppelin only
 * through its blocks, so the zeppelin without it is whole, and Sable still flies it as one body.
 */
interface ZeppelinPiece {
    /** Whether a zeppelin built with these options has this piece: every one does, unless the piece says otherwise. */
    default boolean wanted(ZeppelinOptions options) {
        return true;
    }

    /** Lays the piece into its layout. */
    void build();
}
