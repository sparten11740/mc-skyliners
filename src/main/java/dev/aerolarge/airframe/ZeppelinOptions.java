package dev.aerolarge.airframe;

/**
 * What a zeppelin is built with, as its blueprint or the build command asks: whether its passenger decks are
 * furnished. Its pieces (see {@link ZeppelinPiece}) are laid as these ask.
 */
public record ZeppelinOptions(boolean furnished) {
    /** The plain zeppelin, its decks bare. */
    public static final ZeppelinOptions PLAIN = new ZeppelinOptions(false);
    /** The furnished zeppelin. */
    public static final ZeppelinOptions FURNISHED = new ZeppelinOptions(true);
}
