package dev.aerolarge.airframe;

/**
 * What a zeppelin is built with, as its blueprint or the build command asks: whether its passenger decks are
 * furnished, and the livery it is first painted in (see {@link Livery}), which a festive zeppelin is dressed by too.
 * Its pieces (see {@link ZeppelinPiece}) are laid as these ask.
 */
public record ZeppelinOptions(boolean furnished, Livery livery) {
    /** The plain zeppelin, its decks bare. */
    public static final ZeppelinOptions PLAIN = new ZeppelinOptions(false, Livery.SILVER);
    /** The furnished zeppelin. */
    public static final ZeppelinOptions FURNISHED = new ZeppelinOptions(true, Livery.SILVER);
    /** The furnished zeppelin dressed for Christmas. */
    public static final ZeppelinOptions FESTIVE = new ZeppelinOptions(true, Livery.FESTIVE);
}
