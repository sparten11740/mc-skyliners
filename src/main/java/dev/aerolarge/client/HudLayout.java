package dev.aerolarge.client;

/**
 * Where the parts of the pilots' head-up display ({@link PilotHud}) go on a screen this big, in GUI pixels from its top
 * left corner: clear of the rest of the HUD, and leaving the middle of the view to the horizon round the crosshair. The
 * heading goes in a slim strip at the top in the middle, under any boss bars. The speed and the height go either side
 * of the hotbar at the bottom of the screen, past the offhand slot and the attack indicator, under the chat or beside
 * it, and clear of the subtitles while they show. Where they do not both fit there, as on a small screen or with a big
 * HUD, they go out in the left and right parts of the screen instead, level with the crosshair, or as much higher as
 * keeps the speed above the chat.
 *
 * @param chatWidth  how wide the chat is set, at the screen's left edge
 * @param chatHeight how high it is set, while closed
 * @param subtitles  whether subtitles show, in a box at the bottom right corner
 * @param bossBars   where the boss bars at the top of the screen end; its top while there are none
 */
record HudLayout(float width, float height, float chatWidth, float chatHeight, boolean subtitles, float bossBars) {
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

    /** Places the parts, given how far each reaches round its origin. */
    Placement place(Box speed, Box altitude, Box heading, Box attitude) {
        float middle = width / 2;
        Point centre = new Point(middle, height / 2);
        Point headingAt = new Point(middle, Math.max(MARGIN, bossBars + GAP) - heading.top());
        Point speedAt = new Point(middle - HOTBAR_HALF - GAP - speed.right(), height - MARGIN - speed.bottom());
        Point altitudeAt = new Point(middle + HOTBAR_HALF + GAP - altitude.left(), height - MARGIN - altitude.bottom());
        if (clear(speed.at(speedAt)) && clear(altitude.at(altitudeAt))) {
            return new Placement(speedAt, altitudeAt, headingAt, centre);
        }
        float reach = Math.min(width * SIDE, Math.min(middle - MARGIN + speed.left(), middle - MARGIN - altitude.right()));
        float y = height / 2;
        Box chat = chat();
        if (speed.at(new Point(middle - reach, y)).overlaps(chat)) {
            y = chat.top() - GAP - speed.bottom();
        }
        y = Math.max(y, MARGIN - Math.min(speed.top(), altitude.top()));
        return new Placement(new Point(middle - reach, y), new Point(middle + reach, y), headingAt, centre);
    }

    /** Whether a part placed there is on the screen and clear of the chat and the subtitles. */
    private boolean clear(Box box) {
        return box.left() >= MARGIN && box.right() <= width - MARGIN && box.top() >= MARGIN && !box.overlaps(chat())
            && !(subtitles && box.overlaps(subtitleBox()));
    }

    private Box subtitleBox() {
        return new Box(width - SUBTITLES_WIDTH, height - SUBTITLES_BOTTOM - SUBTITLES_HEIGHT, width, height - SUBTITLES_BOTTOM);
    }

    /** Where the chat's lines show, its own scale at most as big as the GUI's. */
    private Box chat() {
        return new Box(0, height - CHAT_BOTTOM - chatHeight, chatWidth + CHAT_PADDING, height - CHAT_BOTTOM);
    }
}
