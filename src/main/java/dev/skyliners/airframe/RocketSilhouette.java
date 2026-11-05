package dev.skyliners.airframe;

/**
 * A rocket's outline in metres, as seen from her right side, for drawing her without her blocks (the client's livery
 * selector pictures her so): how far her body reaches out from her axis at each height up her, and how far out toward
 * her front and her back her fins stand there, between the four ways, as {@link RocketShape} draws her; and her
 * stories' rows as {@link RocketLayout} builds her, which her liveries follow, and which of them are which rooms.
 */
public final class RocketSilhouette {
    /**
     * Her greenhouse's story and her pool's, from the entry story up, and how deep her pool's water is sunk into its
     * floor, in blocks, for clients to hear them by.
     */
    public static final int GREENHOUSE_STORY = RocketGreenhouse.STORY;
    public static final int POOL_STORY = RocketPool.STORY;
    public static final int POOL_DEPTH = RocketPool.DEPTH;

    private RocketSilhouette() {
    }

    /** How tall she stands, from her fins' feet to her nose's tip. */
    public static double height() {
        return RocketShape.HEIGHT;
    }

    /** How far out from her axis she reaches at most, seen from her side: her fins' tips. */
    public static double reach() {
        return RocketShape.FIN_TIP * RocketShape.DIAGONAL;
    }

    /** Her body's radius at a height; 0 under her flat bottom and over her nose's tip. */
    public static double body(double height) {
        return RocketShape.radius(height, RocketShape.BASE);
    }

    /** How far out from her axis her fins reach at a height, seen from her side; 0 over them. */
    public static double finOut(double height) {
        return RocketShape.finReach(height) * RocketShape.DIAGONAL;
    }

    /** How far in toward her axis her fins come at a height under her body, seen from her side; 0 beside it. */
    public static double finIn(double height) {
        return RocketShape.finInner(height, RocketShape.BASE) * RocketShape.DIAGONAL;
    }

    /**
     * The scale one was built at, from how many rows over her fins' feet her control head stands, in her cockpit one
     * row over the nose's foot.
     */
    public static double scaleOf(int headOverFeet) {
        return (headOverFeet - 1) / RocketShape.NOSE_BASE;
    }

    /** How far the middle of each of her fins' feet stands from her axis, in blocks ahead or aside, at a scale. */
    public static double footOut(double scale) {
        return (RocketShape.FIN_FOOT + RocketShape.FIN_TIP) / 2 * RocketShape.DIAGONAL * scale;
    }

    /** How tall one built at a scale stands, in rows. */
    public static int rows(double scale) {
        return RocketLayout.heightRows(scale);
    }

    /** Her stories' floors as one is built at a scale, rows up from her fins' feet, the cockpit's floor last. */
    public static int[] stories(double scale) {
        return RocketLayout.storyRows(scale);
    }
}
