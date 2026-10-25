package dev.aerolarge.block;

import dev.aerolarge.autopilot.AutopilotBlockEntity.Button;
import dev.aerolarge.block.AutopilotScreenBlockEntity.Box;
import dev.aerolarge.block.CockpitDisplayBlockEntity.Desk;
import dev.aerolarge.block.CockpitDisplayBlockEntity.Link;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * The touch keys of the flight deck's curved row of screens (see {@link CockpitDisplayBlockEntity}): the tiles of the
 * middle screen's systems page, and the keys of the sloped touch panel under the screens. Where each is and what it
 * works, in blocks from the bottom left corner of the screen's face, or of the slope as the pilots see it (its front
 * edge along the bottom), so the server finds the key touched by the same layout the renderer draws.
 */
public final class CockpitKeys {
    /** The margin round the keys and the gap between two, in blocks. */
    private static final double MARGIN = 0.05;
    private static final double GAP = 0.04;
    /** The systems page's tiles: across, and down. */
    private static final int TILE_COLUMNS = 3;
    private static final int TILE_ROWS = 2;
    /** The systems page's tiles, top row first, left to right. */
    private static final Function[] TILES = {
        Function.CABIN_LIGHTS, Function.SEATBELTS, Function.JUMP_LIGHTS, Function.SHADES, Function.LASER, Function.DROP
    };
    /** The autopilot's keys after its on/off key: each column's lower key (down or left), then its upper. */
    private static final Function[][] AUTOPILOT_COLUMNS = {
        {Function.HEADING_LEFT, Function.HEADING_RIGHT},
        {Function.ORBIT_LEFT, Function.ORBIT_RIGHT},
        {Function.ALTITUDE_DOWN, Function.ALTITUDE_UP},
        {Function.SPEED_DOWN, Function.SPEED_UP},
        {Function.RADIUS_DOWN, Function.RADIUS_UP}
    };

    /** What a key works: a control out of sight under the flight deck, by its link, or one of the autopilot's buttons. */
    public enum Function {
        TILLER_LEFT(Link.TILLER), TILLER_RIGHT(Link.TILLER), BRAKE(Link.BRAKE), GEAR(Link.GEAR),
        CABIN_LIGHTS(Link.CABIN_LIGHTS), SEATBELTS(Link.SEATBELTS), JUMP_LIGHTS(Link.JUMP_LIGHTS), SHADES(Link.SHADES), LASER(Link.LASER),
        DROP(Link.DROP),
        ENGAGE(Button.ENGAGE), HEADING_LEFT(Button.HEADING_LEFT), HEADING_RIGHT(Button.HEADING_RIGHT), ORBIT_LEFT(Button.ORBIT_LEFT),
        ORBIT_RIGHT(Button.ORBIT_RIGHT), ALTITUDE_DOWN(Button.ALTITUDE_DOWN), ALTITUDE_UP(Button.ALTITUDE_UP), SPEED_DOWN(Button.SPEED_DOWN),
        SPEED_UP(Button.SPEED_UP), RADIUS_DOWN(Button.RADIUS_DOWN), RADIUS_UP(Button.RADIUS_UP);

        public final Link link;
        /** The autopilot's button it is; null for a control's key. */
        @Nullable
        public final Button button;

        Function(Link link) {
            this.link = link;
            this.button = null;
        }

        Function(Button button) {
            this.link = Link.AUTOPILOT;
            this.button = button;
        }
    }

    /** A key: what it works, and where it is. */
    public record Key(Function function, Box box) {
    }

    private CockpitKeys() {
    }

    /**
     * The systems page's tiles on a face this wide and tall, three across and two down; a tile only for a control the
     * panel is linked to, its place left empty otherwise.
     */
    public static List<Key> systems(double width, double height, Set<Link> linked) {
        double tileWidth = (width - 2 * MARGIN - (TILE_COLUMNS - 1) * GAP) / TILE_COLUMNS;
        double tileHeight = (height - 2 * MARGIN - (TILE_ROWS - 1) * GAP) / TILE_ROWS;
        List<Key> keys = new ArrayList<>();
        for (int i = 0; i < TILES.length; i++) {
            if (!linked.contains(TILES[i].link)) {
                continue;
            }
            double x0 = MARGIN + i % TILE_COLUMNS * (tileWidth + GAP);
            double y1 = height - MARGIN - i / TILE_COLUMNS * (tileHeight + GAP);
            keys.add(new Key(TILES[i], new Box(x0, y1 - tileHeight, x0 + tileWidth, y1)));
        }
        return keys;
    }

    /** A sloped panel's keys, on a slope this wide and this long from its front edge up to the screen over it. */
    public static List<Key> desk(Desk desk, double width, double length) {
        return switch (desk) {
            case NONE -> List.of();
            case TILLER -> List.of(
                new Key(Function.TILLER_LEFT, new Box(MARGIN, MARGIN, width / 3, length - MARGIN)),
                new Key(Function.TILLER_RIGHT, new Box(2 * width / 3, MARGIN, width - MARGIN, length - MARGIN)));
            case GROUND -> List.of(
                new Key(Function.BRAKE, new Box(MARGIN, MARGIN, width / 2 - GAP / 2, length - MARGIN)),
                new Key(Function.GEAR, new Box(width / 2 + GAP / 2, MARGIN, width - MARGIN, length - MARGIN)));
            case AUTOPILOT -> autopilot(width, length);
        };
    }

    /** The autopilot's keys: on and off at the left, as tall as the slope, then a column of two for each setting. */
    private static List<Key> autopilot(double width, double length) {
        int columns = AUTOPILOT_COLUMNS.length + 1;
        double columnWidth = (width - 2 * MARGIN - (columns - 1) * GAP) / columns;
        double rowHeight = (length - 2 * MARGIN - GAP) / 2;
        List<Key> keys = new ArrayList<>();
        keys.add(new Key(Function.ENGAGE, new Box(MARGIN, MARGIN, MARGIN + columnWidth, length - MARGIN)));
        for (int column = 0; column < AUTOPILOT_COLUMNS.length; column++) {
            double x0 = MARGIN + (column + 1) * (columnWidth + GAP);
            keys.add(new Key(AUTOPILOT_COLUMNS[column][0], new Box(x0, MARGIN, x0 + columnWidth, MARGIN + rowHeight)));
            keys.add(new Key(AUTOPILOT_COLUMNS[column][1], new Box(x0, length - MARGIN - rowHeight, x0 + columnWidth, length - MARGIN)));
        }
        return keys;
    }

    /** The key at a place, among keys; null for none. */
    @Nullable
    public static Function at(List<Key> keys, double across, double up) {
        for (Key key : keys) {
            if (key.box().contains(across, up)) {
                return key.function();
            }
        }
        return null;
    }
}
