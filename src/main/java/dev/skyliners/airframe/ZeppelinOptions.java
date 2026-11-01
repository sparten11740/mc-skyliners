package dev.skyliners.airframe;

/**
 * What a zeppelin is built with, as its blueprint or the build command asks: whether its passenger decks are
 * furnished, the livery it is first painted in (see {@link Livery}), which a festive zeppelin is dressed by too, and
 * how big its hull is (see {@link ZeppelinSize}). Its pieces (see {@link ZeppelinPiece}) are laid as these ask.
 */
public record ZeppelinOptions(boolean furnished, Livery livery, ZeppelinSize size) {
    /** The plain zeppelin, its decks bare. */
    public static final ZeppelinOptions PLAIN = new ZeppelinOptions(false, Livery.SILVER, ZeppelinSize.FULL);
    /** The furnished zeppelin. */
    public static final ZeppelinOptions FURNISHED = new ZeppelinOptions(true, Livery.SILVER, ZeppelinSize.FULL);
    /** The furnished zeppelin dressed for Christmas. */
    public static final ZeppelinOptions FESTIVE = new ZeppelinOptions(true, Livery.FESTIVE, ZeppelinSize.FULL);

    /** The same zeppelin, its hull built at another size. */
    public ZeppelinOptions sized(ZeppelinSize size) {
        return new ZeppelinOptions(furnished, livery, size);
    }
}
