package dev.skyliners.client;

import javax.annotation.Nullable;

/**
 * Where the parts of the pilots' head-up display ({@link PilotHud}) go on the visor's screen, in GUI pixels from its
 * top left corner: clear of the rest of the HUD, and leaving the middle of the view to the horizon round the
 * crosshair. The heading goes in a slim strip at the top in the middle, under any boss bars. The speed and the height
 * go out against the screen's left and right edges, just in from them, level with the crosshair; both as much higher
 * as keeps the speed above the chat and the height above the subtitles. Each part is placed by where it lands once
 * bent onto the visor's curve, which draws it in toward the middle of the screen.
 *
 * @param curve      the visor's curve, on a screen as big as the GUI's
 * @param chatWidth  how wide the chat is set, at the screen's left edge
 * @param chatHeight how high it is set, while closed
 * @param subtitles  whether subtitles show, in a box at the bottom right corner
 * @param bossBars   where the boss bars at the top of the screen end; its top while there are none
 * @param edge       how far in from the screen's left and right edges the speed and the height stand
 */
record HudLayout(VisorCurve curve, float chatWidth, float chatHeight, boolean subtitles, float bossBars, float edge) {
    /** How far above the screen's bottom the chat's lowest line ends, and how much wider its lines are than it is set. */
    private static final float CHAT_BOTTOM = 40;
    private static final float CHAT_PADDING = 13;
    /** The subtitles' box at the bottom right: as wide as a long one, a few lines high, over the bottom of the screen. */
    private static final float SUBTITLES_WIDTH = 200;
    private static final float SUBTITLES_HEIGHT = 50;
    private static final float SUBTITLES_BOTTOM = 30;
    /** How far the parts keep from the screen's edges, and from the rest of the HUD. */
    private static final float MARGIN = 2;
    private static final float GAP = 4;
    /** How many times a part is moved to put its bent box where wanted: each step leaves a fifth of the miss at most. */
    private static final int FIT_STEPS = 4;

    /** An area, or how far a part reaches round its origin: its left, top, right and bottom edges. */
    record Box(float left, float top, float right, float bottom) {
        Box at(Point origin) {
            return new Box(origin.x() + left, origin.y() + top, origin.x() + right, origin.y() + bottom);
        }

        Box scaled(float by) {
            return new Box(left * by, top * by, right * by, bottom * by);
        }

        Box grown(float by) {
            return new Box(left - by, top - by, right + by, bottom + by);
        }

        boolean overlaps(Box other) {
            return left < other.right && other.left < right && top < other.bottom && other.top < bottom;
        }
    }

    record Point(float x, float y) {
    }

    /** Where each part's origin goes. */
    record Placement(Point speed, Point altitude, Point heading, Point attitude) {
    }

    /** An edge of a box. */
    private enum Edge {
        LEFT, TOP, RIGHT, BOTTOM;

        float of(Box box) {
            return switch (this) {
                case LEFT -> box.left();
                case TOP -> box.top();
                case RIGHT -> box.right();
                case BOTTOM -> box.bottom();
            };
        }
    }

    /** Places the parts, given how far each reaches round its origin. */
    Placement place(Box speed, Box altitude, Box heading, Box attitude) {
        float middle = curve.width() / 2;
        float height = curve.height();
        Point centre = new Point(middle, height / 2);
        Point headingAt = fit(heading, null, middle, Edge.TOP, Math.max(MARGIN, bossBars + GAP));
        float left = Math.max(MARGIN, edge);
        float right = curve.width() - left;
        Point speedAt = fit(speed, Edge.LEFT, left, null, height / 2);
        Point altitudeAt = fit(altitude, Edge.RIGHT, right, null, height / 2);
        // Lifted together, as much as the lower of the two needs, so they stay level.
        float above = height;
        if (onScreen(speed, speedAt).overlaps(chat())) {
            above = Math.min(above, chat().top() - GAP);
        }
        if (subtitles && onScreen(altitude, altitudeAt).overlaps(subtitleBox())) {
            above = Math.min(above, subtitleBox().top() - GAP);
        }
        if (above < height) {
            speedAt = fit(speed, Edge.LEFT, left, Edge.BOTTOM, above);
            altitudeAt = fit(altitude, Edge.RIGHT, right, Edge.BOTTOM, above);
        }
        if (onScreen(speed, speedAt).top() < MARGIN || onScreen(altitude, altitudeAt).top() < MARGIN) {
            speedAt = fit(speed, Edge.LEFT, left, Edge.TOP, MARGIN);
            altitudeAt = fit(altitude, Edge.RIGHT, right, Edge.TOP, MARGIN);
        }
        return new Placement(speedAt, altitudeAt, headingAt, centre);
    }

    /**
     * Where a part's origin goes for its bent box's edges to land where wanted: one edge across at x (or, with none,
     * its origin at x) and one up or down at y (or, with none, its origin at y). The curve draws things in, so the
     * part is moved out until they land.
     */
    private Point fit(Box reach, @Nullable Edge across, float x, @Nullable Edge down, float y) {
        Point at = new Point(across == null ? x : x - across.of(reach), down == null ? y : y - down.of(reach));
        for (int step = 0; step < FIT_STEPS; step++) {
            Box bent = onScreen(reach, at);
            at = new Point(across == null ? at.x() : at.x() + x - across.of(bent), down == null ? at.y() : at.y() + y - down.of(bent));
        }
        return at;
    }

    /** Where a part with its origin there lands on the screen, bent onto the visor. */
    private Box onScreen(Box reach, Point origin) {
        return curve.bent(reach.at(origin));
    }

    /** Where the chat's lines show, its own scale at most as big as the GUI's. */
    private Box chat() {
        float bottom = curve.height() - CHAT_BOTTOM;
        return new Box(0, bottom - chatHeight, chatWidth + CHAT_PADDING, bottom);
    }

    private Box subtitleBox() {
        float width = curve.width();
        float bottom = curve.height() - SUBTITLES_BOTTOM;
        return new Box(width - SUBTITLES_WIDTH, bottom - SUBTITLES_HEIGHT, width, bottom);
    }
}
