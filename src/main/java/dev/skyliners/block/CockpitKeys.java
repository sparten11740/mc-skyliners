package dev.skyliners.block;

import dev.skyliners.airframe.OutsideCamera;
import dev.skyliners.autopilot.AutopilotBlockEntity.Button;
import dev.skyliners.block.AutopilotScreenBlockEntity.Box;
import dev.skyliners.block.CockpitDisplayBlockEntity.Desk;
import dev.skyliners.block.CockpitDisplayBlockEntity.Link;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * The touch keys of the flight deck's curved row of screens (see {@link CockpitDisplayBlockEntity}): the tiles of the
 * middle screen's systems page, and the keys of the sloped touch panel under the screens. Where each is and what it
 * works, a touch panel's layout (see {@link TouchPanel}) in blocks from the bottom left corner of the screen's face, or
 * of the slope as the pilots see it (its front edge along the bottom), so the server finds the key touched by the same
 * layout the renderer draws, and the key under the pointer lights up as every touch panel's tiles do. The gaps between
 * keys are no key at all.
 */
public final class CockpitKeys {
    /** The margin round the keys and the gap between two, in blocks. */
    private static final double MARGIN = 0.05;
    private static final double GAP = 0.04;
    /** How many of the systems page's tiles stand side by side in a row: three, in two rows. */
    private static final int TILE_COLUMNS = 3;
    /**
     * The systems page's tiles, top row first, left to right: the everyday switches along the top, where a pilot's
     * look rests, and the ones with something to them, the jump lights, the laser and the drop, below.
     */
    private static final List<Function> TILES = List.of(
        Function.CABIN_LIGHTS, Function.SEATBELTS, Function.SHADES, Function.JUMP_LIGHTS, Function.LASER, Function.DROP);
    /**
     * The cameras' keys, one for each outside camera, two by two, top row first: wide and low, the shape of the
     * airliner seen from its side that each key shows, which a row of four across the slope would shrink to a sliver.
     */
    private static final List<Function> CAMERAS = List.of(Function.TAIL_CAMERA, Function.WING_CAMERA, Function.NOSE_CAMERA, Function.FRONT_CAMERA);
    private static final int CAMERA_COLUMNS = 2;
    /** The parking brake and the landing gear, side by side. */
    private static final List<Function> BRAKE_AND_GEAR = List.of(Function.BRAKE, Function.GEAR);
    /**
     * The autopilot's desk, in blocks: the on/off key's width, the speedometer's and the sky's, the steering taking the
     * rest between them; the row of turns along the bottom of the steering, how tall it is, and the gap between it and
     * the steering fan over it, so a touch on the seam is no touch at all.
     */
    private static final double ENGAGE_WIDTH = 0.24;
    private static final double SPEEDOMETER_WIDTH = 0.5;
    private static final double SKY_WIDTH = 0.4;
    private static final double TURN_ROW = 0.165;
    private static final double TURN_GUTTER = 0.03;
    /**
     * How wide a slope the desk is laid out for, in blocks: a 2x airliner's; on a narrower one the on/off key, the
     * speedometer and the sky shrink with it, so the steering keeps its share.
     */
    private static final double DESK_WIDTH = 2.6;
    /**
     * The turns along the bottom of the autopilot's steering, left to right, like road signs: hardest left out to
     * hardest right.
     */
    public static final List<Function> TURNS = List.of(Function.TIGHT_LEFT, Function.GENTLE_LEFT, Function.STRAIGHT, Function.GENTLE_RIGHT,
        Function.TIGHT_RIGHT);
    /**
     * The speedometer on its key: how far up the key its middle is and how far in its rim from the key's sides, in
     * blocks, and the way round its scale runs, in degrees anticlockwise from the right: from the slowest at the lower
     * left, clockwise over the top, to the fastest at the lower right.
     */
    private static final double DIAL_MIDDLE = 0.3;
    private static final double DIAL_INSET = 0.05;
    private static final double DIAL_START = 225;
    private static final double DIAL_SWEEP = 270;
    /** The steering fan, the half of a disc over the turns: how far in from its key's edges its rim is, in blocks. */
    private static final double FAN_INSET = 0.02;
    /**
     * The tiller's taxi lines: how far up from the key's bottom edge the nose wheel is and how far from its top edge
     * the straight line ends, in blocks; the degrees round from straight ahead each notch's line ends at; where a line
     * bends, as a share of its reach up from the nose wheel; and how far out a hard curl ends, as a share of the
     * straight line's length, it curling in tighter than a gentle bend reaches.
     */
    private static final double NOSE_WHEEL_UP = 0.11;
    private static final double TAXI_TOP = 0.03;
    private static final double TAXI_SPREAD = 38;
    private static final double TAXI_BEND_UP = 0.55;
    private static final double HARD_REACH = 0.85;
    /**
     * The sky on its key: its frame, the ground along its bottom, and the room above and below its scale, in blocks.
     */
    private static final double SKY_INSET = 0.02;
    private static final double GROUND = 0.06;
    private static final double SKY_ROOM = 0.03;

    /** What a key works: a control out of sight under the flight deck, by its link, or one of the autopilot's buttons. */
    public enum Function {
        TILLER(Link.TILLER), BRAKE(Link.BRAKE), GEAR(Link.GEAR),
        CABIN_LIGHTS(Link.CABIN_LIGHTS), SEATBELTS(Link.SEATBELTS), JUMP_LIGHTS(Link.JUMP_LIGHTS), SHADES(Link.SHADES), LASER(Link.LASER),
        DROP(Link.DROP),
        ENGAGE(Button.ENGAGE), SPEED(Button.SPEED), HEADING(Button.HEADING), ALTITUDE(Button.ALTITUDE), TIGHT_LEFT(Button.TIGHT_LEFT),
        GENTLE_LEFT(Button.GENTLE_LEFT), STRAIGHT(Button.STRAIGHT), GENTLE_RIGHT(Button.GENTLE_RIGHT), TIGHT_RIGHT(Button.TIGHT_RIGHT),
        TAIL_CAMERA(OutsideCamera.TAIL), WING_CAMERA(OutsideCamera.WING), NOSE_CAMERA(OutsideCamera.NOSE), FRONT_CAMERA(OutsideCamera.FRONT);

        /** What it works, by the panel's link to it; null for a camera's key, which works the panel itself. */
        @Nullable
        public final Link link;
        /** The autopilot's button it is; null for any other key. */
        @Nullable
        public final Button button;
        /** The outside camera it shows on the panel; null for any other key. */
        @Nullable
        public final OutsideCamera camera;

        Function(Link link) {
            this(link, null, null);
        }

        Function(Button button) {
            this(Link.AUTOPILOT, button, null);
        }

        Function(OutsideCamera camera) {
            this(null, null, camera);
        }

        Function(@Nullable Link link, @Nullable Button button, @Nullable OutsideCamera camera) {
            this.link = link;
            this.button = button;
            this.camera = camera;
        }
    }

    /** A key: what it works, and its tile on the panel's face. */
    public record Key(Function function, TouchPanel.Tile tile) {
        /** Its tile as a box, which its drawings and the reckoning of where along it a touch lands are fitted to. */
        public Box box() {
            return new Box(tile.left(), tile.bottom(), tile.right(), tile.top());
        }
    }

    /** A panel's keys in order, and their tiles as a touch panel's layout, by which a touch finds its key. */
    public record Keys(List<Key> list, TouchPanel.Layout layout) {
        public static final Keys NONE = of(List.of());

        public Keys {
            list = List.copyOf(list);
        }

        public static Keys of(List<Key> keys) {
            return new Keys(keys, new TouchPanel.Layout(keys.stream().map(Key::tile).toList()));
        }

        /** The key at a place on the face; null for none. */
        @Nullable
        public Key at(double across, double up) {
            int key = layout.at(across, up);
            return key < 0 ? null : list.get(key);
        }
    }

    /**
     * The speedometer, on its key: its middle and the radius of its scale, in blocks. A share of the way from the
     * slowest speed to the fastest lies round it from the lower left, clockwise over the top, to the lower right.
     */
    public record Dial(double x, double y, double radius) {
        public static Dial on(Box box) {
            return new Dial((box.x0() + box.x1()) / 2, box.y0() + DIAL_MIDDLE, (box.x1() - box.x0()) / 2 - DIAL_INSET);
        }

        /** Where a share of the way along the scale lies round it, in radians anticlockwise from the right. */
        public double angle(double share) {
            return Math.toRadians(DIAL_START - share * DIAL_SWEEP);
        }

        /** The share of the way along the scale at a place round it; the gap under it splits between the two ends. */
        public double share(double across, double up) {
            double degrees = Math.toDegrees(Math.atan2(up - y, across - x));
            double fromStart = ((DIAL_START - degrees) % 360 + 360) % 360;
            if (fromStart > DIAL_SWEEP) {
                return fromStart > DIAL_SWEEP + (360 - DIAL_SWEEP) / 2 ? 0 : 1;
            }
            return fromStart / DIAL_SWEEP;
        }
    }

    /**
     * The steering fan, on its key: the half of a disc whose middle, where the airliner sits nose up, is the middle of
     * the key's bottom edge, and its radius, in blocks. Straight up is the way the airliner goes.
     */
    public record Fan(double x, double y, double radius) {
        public static Fan on(Box box) {
            return new Fan((box.x0() + box.x1()) / 2, box.y0(), Math.min((box.x1() - box.x0()) / 2, box.y1() - box.y0()) - FAN_INSET);
        }

        /** How far right of the airliner's nose a place on it lies, in degrees, from -90 (left) to 90 (right). */
        public double turn(double across, double up) {
            return Math.max(-90, Math.min(90, Math.toDegrees(Math.atan2(across - x, Math.max(0, up - y)))));
        }
    }

    /**
     * The tiller's taxi lines, on its key, seen from above: the nose wheel at the middle of the key's bottom edge, just
     * up from it, under the airliner's nose, and five taxi lines fanning out from it, hard left round to hard right,
     * each rising straight from the wheel before it bends away. A touch picks the line drawn nearest it.
     */
    public record Taxi(double x, double y, double radius) {
        /** The straight pieces each taxi line is drawn in, and a touch is measured against. */
        public static final int PIECES = 24;

        public static Taxi on(Box box) {
            return new Taxi((box.x0() + box.x1()) / 2, box.y0() + NOSE_WHEEL_UP, box.y1() - box.y0() - NOSE_WHEEL_UP - TAXI_TOP);
        }

        /**
         * The degrees right of straight ahead a taxi line ends at, for a tiller position from hard left (0) to hard
         * right (4).
         */
        public static double bend(int steer) {
            return (steer - TillerBlock.CENTRE) * TAXI_SPREAD;
        }

        /**
         * A point a share of the way along a tiller position's taxi line, across and up the key: from the nose wheel
         * straight up, bending towards where the line ends; a hard curl ends nearer the wheel, curling tighter than a
         * gentle bend.
         */
        public double[] point(int steer, double t) {
            double bend = Math.toRadians(bend(steer));
            double reach = Math.abs(steer - TillerBlock.CENTRE) == 2 ? radius * HARD_REACH : radius;
            double endX = x + Math.sin(bend) * reach;
            double endY = y + Math.cos(bend) * reach;
            return new double[]{curve(x, x, endX, t), curve(y, y + reach * TAXI_BEND_UP, endY, t)};
        }

        /** The tiller position, hard left (0) to hard right (4), whose taxi line runs nearest a place on the key. */
        public int steer(double across, double up) {
            int nearest = TillerBlock.CENTRE;
            double nearestDistance = Double.MAX_VALUE;
            for (int steer : TillerBlock.STEER.getPossibleValues()) {
                double distance = distance(steer, across, up);
                if (distance < nearestDistance) {
                    nearest = steer;
                    nearestDistance = distance;
                }
            }
            return nearest;
        }

        /** How far a place on the key is from a tiller position's taxi line, as drawn in its pieces, in blocks. */
        private double distance(int steer, double across, double up) {
            double nearest = Double.MAX_VALUE;
            double[] from = point(steer, 0);
            for (int k = 1; k <= PIECES; k++) {
                double[] to = point(steer, (double) k / PIECES);
                nearest = Math.min(nearest, toPiece(across, up, from, to));
                from = to;
            }
            return nearest;
        }

        /** How far a place is from a straight piece of line between two points, in blocks. */
        private static double toPiece(double across, double up, double[] from, double[] to) {
            double dx = to[0] - from[0];
            double dy = to[1] - from[1];
            double lengthSquared = dx * dx + dy * dy;
            double share = lengthSquared == 0 ? 0 : Math.clamp(((across - from[0]) * dx + (up - from[1]) * dy) / lengthSquared, 0.0, 1.0);
            return Math.hypot(across - from[0] - share * dx, up - from[1] - share * dy);
        }
    }

    /** A point a share of the way along a quadratic curve, in one coordinate, from its start by its bend to its end. */
    public static double curve(double start, double bend, double end, double t) {
        return (1 - t) * (1 - t) * start + 2 * (1 - t) * t * bend + t * t * end;
    }

    /**
     * The sky, on its key, seen from the side: its frame inside the key's rim, the ground along its bottom, and its
     * scale of heights between, the lowest just over the ground and the highest just under the top, in blocks.
     */
    public record Sky(double x0, double x1, double ground, double top, double lowest, double highest) {
        public static Sky on(Box box) {
            double ground = box.y0() + SKY_INSET + GROUND;
            double top = box.y1() - SKY_INSET;
            return new Sky(box.x0() + SKY_INSET, box.x1() - SKY_INSET, ground, top, ground + SKY_ROOM, top - SKY_ROOM);
        }

        /** Where a share of the way from the lowest height to the highest lies up it. */
        public double up(double share) {
            return lowest + share * (highest - lowest);
        }

        /** The share of the way from the lowest height to the highest at a place up it. */
        public double share(double up) {
            return Math.max(0, Math.min(1, (up - lowest) / (highest - lowest)));
        }
    }

    private CockpitKeys() {
    }

    /**
     * The systems page's tiles on a face this wide and tall, three across and two down; a tile only for a control the
     * panel is linked to, its place left empty otherwise.
     */
    public static Keys systems(double width, double height, Set<Link> linked) {
        List<Key> tiles = grid(TILES, TILE_COLUMNS, new TouchPanel.Tile(MARGIN, MARGIN, width - MARGIN, height - MARGIN));
        return Keys.of(tiles.stream().filter(tile -> linked.contains(tile.function().link)).toList());
    }

    /**
     * A sloped panel's keys, on a slope this long from its front edge up to the screen over it, between these two
     * places across it (inside its mitred ends).
     */
    public static Keys desk(Desk desk, double left, double right, double length) {
        TouchPanel.Tile area = new TouchPanel.Tile(left + MARGIN, MARGIN, right - MARGIN, length - MARGIN);
        return switch (desk) {
            case NONE -> Keys.NONE;
            case TILLER -> Keys.of(grid(List.of(Function.TILLER), 1, area));
            case GROUND -> Keys.of(grid(BRAKE_AND_GEAR, BRAKE_AND_GEAR.size(), area));
            case AUTOPILOT -> Keys.of(autopilot(left, right - left, length));
            case CAMERA -> Keys.of(grid(CAMERAS, CAMERA_COLUMNS, area));
        };
    }

    /**
     * Keys for these, in order, in rows of so many over an area of the face, the top row first and each from the left,
     * a gap between two: the tiles of a touch panel's grid over the area grown by half a gap, each shrunk by as much.
     */
    private static List<Key> grid(List<Function> functions, int columns, TouchPanel.Tile area) {
        double half = GAP / 2;
        TouchPanel.Tile grown = new TouchPanel.Tile(area.left() - half, area.bottom() - half, area.right() + half, area.top() + half);
        TouchPanel.Layout cells = TouchPanel.Layout.grid(functions.size(), columns, grown, true);
        List<Key> keys = new ArrayList<>();
        for (int key = 0; key < functions.size(); key++) {
            keys.add(new Key(functions.get(key), cells.get(key).inset(half)));
        }
        return keys;
    }

    /**
     * The autopilot's keys, in the order the pilots' flight displays read: the on/off key at the left, as tall as the
     * slope; the speedometer; the steering, the fan over the row of turns; and the sky at the right.
     */
    private static List<Key> autopilot(double left, double width, double length) {
        double y0 = MARGIN;
        double y1 = length - MARGIN;
        double shrink = Math.min(1, width / DESK_WIDTH);
        double engage = ENGAGE_WIDTH * shrink;
        double speedometer = SPEEDOMETER_WIDTH * shrink;
        double sky = SKY_WIDTH * shrink;
        double x = left + MARGIN;
        List<Key> keys = new ArrayList<>();
        keys.add(new Key(Function.ENGAGE, new TouchPanel.Tile(x, y0, x + engage, y1)));
        x += engage + GAP;
        keys.add(new Key(Function.SPEED, new TouchPanel.Tile(x, y0, x + speedometer, y1)));
        x += speedometer + GAP;
        double steering = left + width - MARGIN - sky - GAP - x;
        keys.add(new Key(Function.HEADING, new TouchPanel.Tile(x, y0 + TURN_ROW + TURN_GUTTER, x + steering, y1)));
        keys.addAll(grid(TURNS, TURNS.size(), new TouchPanel.Tile(x, y0, x + steering, y0 + TURN_ROW)));
        x += steering + GAP;
        keys.add(new Key(Function.ALTITUDE, new TouchPanel.Tile(x, y0, x + sky, y1)));
        return keys;
    }

    /**
     * Where along a key a touch landed, for the keys set by where they are touched (see {@link
     * dev.skyliners.autopilot.AutopilotBlockEntity#press}): the share of the way round the speedometer's scale or up
     * the sky's, the degrees right of the nose on the steering fan, or the tiller position whose taxi line was touched;
     * 0 for any other key.
     */
    public static double along(Key key, double across, double up) {
        return switch (key.function()) {
            case SPEED -> Dial.on(key.box()).share(across, up);
            case ALTITUDE -> Sky.on(key.box()).share(up);
            case HEADING -> Fan.on(key.box()).turn(across, up);
            case TILLER -> Taxi.on(key.box()).steer(across, up);
            default -> 0;
        };
    }
}
