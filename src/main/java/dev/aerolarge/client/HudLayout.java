package dev.aerolarge.client;

import javax.annotation.Nullable;

/**
 * Where the parts of the pilots' head-up display ({@link PilotHud}) go on the visor's screen, in GUI pixels from its
 * top left corner: clear of the rest of the HUD, and leaving the middle of the view to the horizon round the
 * crosshair. The heading goes in a slim strip at the top in the middle, under any boss bars. The speed and the height
 * go either side of the hotbar at the bottom of the screen, past the offhand slot and the attack indicator, under the
 * chat or beside it, and clear of the subtitles while they show. Where they do not both fit there, as on a small
 * screen or with a big HUD, they go out in the left and right parts of the screen instead, level with the crosshair,
 * or as much higher as keeps the speed above the chat. Each part is placed by where it lands once bent onto the
 * visor's curve, which draws it in toward the middle of the screen.
 *
 * @param curve      the visor's curve, on a screen as big as the GUI's
 * @param chatWidth  how wide the chat is set, at the screen's left edge
 * @param chatHeight how high it is set, while closed
 * @param subtitles  whether subtitles show, in a box at the bottom right corner
 * @param bossBars   where the boss bars at the top of the screen end; its top while there are none
 */
record HudLayout(VisorCurve curve, float chatWidth, float chatHeight, boolean subtitles, float bossBars) {
    /** Half the hotbar's width, with the offhand slot or the attack indicator beside it, either side. */
    private static final float HOTBAR_HALF = 91 + 29;
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
    /** Out in the left and right parts of the screen, how far the tapes are from its middle, as a share of its width. */
    private static final float SIDE = 0.25F;
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
        Point speedAt = fit(speed, Edge.RIGHT, middle - HOTBAR_HALF - GAP, Edge.BOTTOM, height - MARGIN);
        Point altitudeAt = fit(altitude, Edge.LEFT, middle + HOTBAR_HALF + GAP, Edge.BOTTOM, height - MARGIN);
        if (clear(onScreen(speed, speedAt)) && clear(onScreen(altitude, altitudeAt))) {
            return new Placement(speedAt, altitudeAt, headingAt, centre);
        }
        float reach = Math.min(curve.width() * SIDE, Math.min(middle - MARGIN + speed.left(), middle - MARGIN - altitude.right()));
        Point speedSide = new Point(middle - reach, height / 2);
        Box chat = chat();
        if (onScreen(speed, speedSide).overlaps(chat)) {
            speedSide = fit(speed, null, speedSide.x(), Edge.BOTTOM, chat.top() - GAP);
        }
        if (onScreen(speed, speedSide).top() < MARGIN) {
            speedSide = fit(speed, null, speedSide.x(), Edge.TOP, MARGIN);
        }
        return new Placement(speedSide, new Point(middle + reach, speedSide.y()), headingAt, centre);
    }

    /**
     * Where a part's origin goes for its bent box's edges to land where wanted: one edge across at x (or, with none,
     * its origin at x) and one up or down at y. The curve draws things in, so the part is moved out until they land.
     */
    private Point fit(Box reach, @Nullable Edge across, float x, Edge down, float y) {
        Point at = new Point(across == null ? x : x - across.of(reach), y - down.of(reach));
        for (int step = 0; step < FIT_STEPS; step++) {
            Box bent = onScreen(reach, at);
            at = new Point(across == null ? at.x() : at.x() + x - across.of(bent), at.y() + y - down.of(bent));
        }
        return at;
    }

    /** Where a part with its origin there lands on the screen, bent onto the visor. */
    private Box onScreen(Box reach, Point origin) {
        return curve.bent(reach.at(origin));
    }

    /** Whether a part placed there is on the screen and clear of the chat and the subtitles. */
    private boolean clear(Box box) {
        return box.left() >= MARGIN && box.right() <= curve.width() - MARGIN && box.top() >= MARGIN && !box.overlaps(chat())
            && !(subtitles && box.overlaps(subtitleBox()));
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
