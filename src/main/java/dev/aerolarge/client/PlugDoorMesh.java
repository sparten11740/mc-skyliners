package dev.aerolarge.client;

import dev.aerolarge.block.PlugDoorBlock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;

/**
 * A plug door's panel cut into quads, once for each way it hinges and each height, for {@link PlugDoorRenderer} to
 * draw. Every pixel of the door's picture is a column of the panel running from its inside to its outside, so the
 * panel is two reliefs back to back:
 *
 * <ul>
 *   <li>Outside, the skin flush with the hull, stepping down a pixel at a time to two pixels deep at its edge: the
 *   door's seam in the picture is a real gap all round it, and the flush handle is sunk a pixel into the skin.</li>
 *   <li>Inside, the padded lining bulging out of the panel: a big rounded oval whose sides curve up from its foot to
 *   its crown along a quarter of a sine wave, a pixel of height at a time, a shade lighter with every step up, so it
 *   reads as a cushion under flat light; the handle a bar standing on it, the flag beside it flat on it.</li>
 * </ul>
 *
 * <p>The window is a hole right through the panel, glazed a pixel in from the hull's outer face, set in the lining
 * through a reveal that rounds down into it the way the lining's sides round down to the panel.
 *
 * <p>Drawn in the door's own frame, as the renderer draws it: x across the doorway to the right as seen from outside,
 * y up from the sill, z out, the hull's outer face at z 1. The pictures are of a door hinged on the left, seen from
 * outside, so a door hinged on the right shows them mirrored; the window and the handle are where
 * {@code tools/generate_textures.mjs} paints them.
 */
final class PlugDoorMesh {
    /** Pixels to a block, as the pictures are painted. */
    private static final int PX = 16;
    private static final int WIDE = PlugDoorBlock.COLUMNS * PX;
    /** The panel's inner face, where its outside steps up to the skin, and the glass, in pixels out from the cabin's side. */
    private static final int PANEL_IN = PX - PlugDoorBlock.THICKNESS;
    private static final int PANEL_OUT = PX - 2;
    private static final int GLASS = PX - 1;
    /** The lining's foot: a rounded rectangle this many pixels in from the door's edge, its corners this round. */
    private static final int FOOT_INSET = 2;
    private static final double FOOT_CORNER = 12;
    /** How far in from its foot the lining rounds up to its crown, in pixels. */
    private static final double SHOULDER = 12;
    /**
     * The window, as the pictures paint it: its straight sides start this many pixels down a door two rows tall, and
     * this many further down for each row more, and run on for this many pixels more.
     */
    private static final int WINDOW_TOP = 8;
    private static final int WINDOW_DROP = 4;
    private static final int WINDOW_SIDES = 5;
    /** How far round the window's middle, as the pictures measure it, the opening reaches, and the frame round it. */
    private static final double OPENING = 1.9;
    private static final double FRAME = 2.6;
    /** How many pixels below the top of the window's sides the handle of a door hinged at its side is. */
    private static final int HANDLE_BELOW_WINDOW = 13;
    /** How far the handle stands proud of the lining under it, in pixels. */
    private static final int HANDLE = 1;
    /** How far past the frame the reveal rounds up to the lining's crown, in pixels. */
    private static final double REVEAL = 3;
    /** The shade of the panel's faces where they show past what stands on them: round the lining, in the seam. */
    private static final float PANEL_SHADE = 0.8F;
    /** The shade of the lining's lowest step; each step up is lighter, to its crown at full light. */
    private static final float FOOT_SHADE = 0.72F;
    /** How much a step's side is shaded by the way it faces in the door: up, across, or down. */
    private static final float FACING_UP = 1;
    private static final float FACING_ACROSS = 0.88F;
    private static final float FACING_DOWN = 0.76F;
    /**
     * Where in the door's picture the glass's own picture starts, laid on a pixel of it to a pixel of the door: this
     * many pixels across, and this many above the window's straight sides.
     */
    private static final int GLASS_X = 8;
    private static final int GLASS_ABOVE = 5;

    /** Which picture a quad shows, and which light it is drawn in. */
    enum Side {
        /** The outside: the door's picture, lit by the air outside the doorway. */
        OUTSIDE,
        /** The inside: the lining's picture, lit by the cabin while the door is shut. */
        INSIDE,
        /** The panel's edges, hidden in the frame while it is shut: the door's picture, lit outside. */
        EDGE
    }

    /**
     * One quad: its four corners, each (x, y, z, u, v) in blocks and across the picture 0 to 1 (across the glass's
     * picture for the window's glass), the way it faces, the block of the door (column, row) whose light it is drawn
     * in, how much it is shaded, 0 to 1, and whether it is the window's glass, drawn see-through.
     */
    record Quad(Side side, float[] corners, float nx, float ny, float nz, int column, int row, float shade, boolean glass) {
        /**
         * Whether a point in the door's frame is in front of the quad. A quad is never seen from behind: behind every
         * face and side of the relief is the panel itself, and the glass is drawn from its own side only.
         */
        boolean faces(Vector3f point) {
            if (nz != 0) {
                return (point.z - corners[2]) * nz > 0;
            }
            return nx != 0 ? (point.x - corners[0]) * nx > 0 : (point.y - corners[1]) * ny > 0;
        }
    }

    private static final Map<PlugDoorBlock.Hinge, Map<Integer, List<Quad>>> CACHE = new EnumMap<>(PlugDoorBlock.Hinge.class);

    private PlugDoorMesh() {
    }

    /** The quads of a door hinged this way and this many rows tall. */
    static synchronized List<Quad> of(PlugDoorBlock.Hinge hinge, int rows) {
        return CACHE.computeIfAbsent(hinge, h -> new HashMap<>()).computeIfAbsent(rows, r -> new Builder(hinge, r).build());
    }

    /**
     * How far a pixel of the picture is from the middle of the window, as the pictures measure it: a window whose
     * straight sides run down the picture from this pixel row for {@link #WINDOW_SIDES} more.
     */
    private static double window(int x, int y, int top) {
        double dx = Math.max(Math.abs(x - 15.5) - 1.5, 0);
        double dy = Math.max(Math.max(top - y, y - top - WINDOW_SIDES), 0);
        return Math.hypot(dx, dy);
    }

    /**
     * Where the window's straight sides start down the picture of a door this many rows tall: in a three-row door low
     * enough to stand in the lining's crown, and in sight from the cabin.
     */
    private static int windowTop(int rows) {
        return WINDOW_TOP + WINDOW_DROP * (rows - 2);
    }

    /**
     * A quarter of a sine wave, from 0 at its foot (t 0) to 1 at its top (t 1 and on), steepest at its foot and level at
     * its top: the lining's sides and the reveal's.
     */
    private static double rounded(double t) {
        return Math.sin(Math.PI / 2 * Math.clamp(t, 0, 1));
    }

    /**
     * How far in a point of the picture lies from the lining's foot, in pixels: a rounded rectangle {@link #FOOT_INSET}
     * in from the picture's edge; negative outside it.
     */
    private static double inFromFoot(double x, double y, int tall) {
        double qx = Math.abs(x - WIDE / 2.0) - (WIDE / 2.0 - FOOT_INSET - FOOT_CORNER);
        double qy = Math.abs(y - tall / 2.0) - (tall / 2.0 - FOOT_INSET - FOOT_CORNER);
        double outside = Math.hypot(Math.max(qx, 0), Math.max(qy, 0));
        return FOOT_CORNER - outside - Math.min(Math.max(qx, qy), 0);
    }

    /** The shade of a step of the lining this many pixels high: the panel's own below the lining's foot. */
    private static float stepShade(int height) {
        return height == 0 ? PANEL_SHADE : FOOT_SHADE + (1 - FOOT_SHADE) * (height - 1) / (PlugDoorBlock.LINING - 1);
    }

    /** Builds a door's quads from its relief: in the door's frame, each pixel's inner and outer face, or a hole. */
    private static final class Builder {
        private final boolean mirrored;
        private final int tall;
        private final int windowTop;
        /** Each pixel's inner and outer face, in pixels out from the cabin's side; the window's pixels are open. */
        private final int[][] inner;
        private final int[][] outer;
        private final boolean[][] open;
        private final List<Quad> quads = new ArrayList<>();

        Builder(PlugDoorBlock.Hinge hinge, int rows) {
            mirrored = hinge == PlugDoorBlock.Hinge.RIGHT;
            tall = rows * PX;
            inner = new int[WIDE][tall];
            outer = new int[WIDE][tall];
            open = new boolean[WIDE][tall];
            boolean top = hinge == PlugDoorBlock.Hinge.TOP;
            windowTop = windowTop(rows);
            // The handle and the flag beside it that shows the door armed, in the picture's pixels, as the pictures paint
            // them: three pixels tall, below the window's reveal by the edge away from the hinge, or low down in the
            // middle of a door hinged on top.
            int handleY = top ? tall - 6 : windowTop + HANDLE_BELOW_WINDOW;
            int handleFrom = top ? 12 : 21;
            int handleTo = top ? 19 : 27;
            int flagFrom = top ? handleTo + 2 : handleFrom - 4;
            boolean[][] handle = new boolean[WIDE][tall];
            boolean[][] flag = new boolean[WIDE][tall];
            int[][] lining = new int[WIDE][tall];
            int handleTop = 0;
            int flagTop = 0;
            for (int x = 0; x < WIDE; x++) {
                for (int y = 0; y < tall; y++) {
                    int px = picture(x);
                    int py = tall - 1 - y;
                    double window = window(px, py, windowTop);
                    double foot = inFromFoot(px + 0.5, py + 0.5, tall);
                    // The lining: up its rounded sides from its foot, and down the reveal's into the window.
                    lining[x][y] = window <= FRAME || foot <= 0 ? 0
                        : (int) Math.round(PlugDoorBlock.LINING * Math.min(rounded(foot / SHOULDER), rounded((window - FRAME) / REVEAL)));
                    boolean level = Math.abs(py - handleY) <= 1;
                    handle[x][y] = level && px >= handleFrom && px <= handleTo;
                    flag[x][y] = level && px >= flagFrom && px <= flagFrom + 1;
                    handleTop = handle[x][y] ? Math.max(handleTop, lining[x][y] + HANDLE) : handleTop;
                    flagTop = flag[x][y] ? Math.max(flagTop, lining[x][y]) : flagTop;
                    open[x][y] = window <= OPENING;
                    int edge = Math.min(Math.min(px, WIDE - 1 - px), Math.min(py, tall - 1 - py));
                    outer[x][y] = edge == 0 ? PANEL_OUT : edge == 1 || handle[x][y] ? PANEL_OUT + 1 : PX;
                }
            }
            // The handle stands whole, a pixel proud of the highest of the lining under it but no higher than the crown,
            // and the flag lies flat, as high as the lining under it reaches.
            handleTop = Math.min(handleTop, PlugDoorBlock.LINING);
            for (int x = 0; x < WIDE; x++) {
                for (int y = 0; y < tall; y++) {
                    inner[x][y] = PANEL_IN - (handle[x][y] ? handleTop : flag[x][y] ? flagTop : lining[x][y]);
                }
            }
        }

        /** Where a pixel this far across the frame is in the picture: mirrored for a door hinged on the right. */
        private int picture(int x) {
            return mirrored ? WIDE - 1 - x : x;
        }

        List<Quad> build() {
            // The faces: the lining's steps and the panel round it inside, the skin and the seam outside, and the glass
            // both ways across the opening.
            faces((x, y) -> open[x][y] ? -1 : inner[x][y], Side.INSIDE, -1, false);
            faces((x, y) -> open[x][y] ? -1 : outer[x][y], Side.OUTSIDE, 1, false);
            faces((x, y) -> open[x][y] ? GLASS : -1, Side.INSIDE, -1, true);
            faces((x, y) -> open[x][y] ? GLASS : -1, Side.OUTSIDE, 1, true);
            // The sides of every step, of the seam and of the opening, and the panel's edges.
            for (int[] way : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                walls(way[0], way[1]);
            }
            return List.copyOf(quads);
        }

        /** Which face each pixel has at some depth, or -1 for none: the depth. */
        @FunctionalInterface
        private interface Depths {
            int at(int x, int y);
        }

        /**
         * Faces at the depths given, facing in (-1) or out (1), shaded by their depth: rectangles, none across a block's
         * edge, each running over pixels alike.
         */
        private void faces(Depths depths, Side side, int facing, boolean glass) {
            for (int column = 0; column < PlugDoorBlock.COLUMNS; column++) {
                for (int row = 0; row < tall / PX; row++) {
                    // Runs along each pixel row of the block, carried up while the rows above run alike.
                    Map<Long, Integer> runs = new HashMap<>();
                    for (int y = row * PX; y <= (row + 1) * PX; y++) {
                        Map<Long, Integer> next = new HashMap<>();
                        for (int x = column * PX; y < (row + 1) * PX && x < (column + 1) * PX; x++) {
                            int z = depths.at(x, y);
                            if (z < 0) {
                                continue;
                            }
                            int from = x;
                            while (x + 1 < (column + 1) * PX && depths.at(x + 1, y) == z) {
                                x++;
                            }
                            long run = (long) z << 40 | (long) from << 20 | (x + 1);
                            next.put(run, runs.getOrDefault(run, y));
                        }
                        for (Map.Entry<Long, Integer> run : runs.entrySet()) {
                            if (!next.containsKey(run.getKey())) {
                                long key = run.getKey();
                                int z = (int) (key >> 40);
                                face(side, (int) (key >> 20) & 0xFFFFF, run.getValue(), (int) key & 0xFFFFF, y, z, facing, column, row, glass);
                            }
                        }
                        runs = next;
                    }
                }
            }
        }

        private void face(Side side, int x0, int y0, int x1, int y1, int z, int facing, int column, int row, boolean glass) {
            float shade = glass ? 1 : facing > 0 ? (z == PANEL_OUT ? PANEL_SHADE : 1) : stepShade(PANEL_IN - z);
            // Corners anticlockwise seen from the way it faces, so the glass, drawn one side only, shows from that side.
            int[] xs = facing > 0 ? new int[]{x0, x1, x1, x0} : new int[]{x0, x0, x1, x1};
            int[] ys = facing > 0 ? new int[]{y0, y0, y1, y1} : new int[]{y0, y1, y1, y0};
            float[] corners = new float[20];
            for (int i = 0; i < 4; i++) {
                corners[i * 5] = b(xs[i]);
                corners[i * 5 + 1] = b(ys[i]);
                corners[i * 5 + 2] = b(z);
                corners[i * 5 + 3] = glass ? glassU(xs[i]) : u(xs[i]);
                corners[i * 5 + 4] = glass ? glassV(ys[i]) : v(ys[i]);
            }
            quads.add(new Quad(side, corners, 0, 0, facing, column, row, shade, glass));
        }

        /** How far across the glass's picture a point this many pixels from the frame's left edge is. */
        private float glassU(int x) {
            return ((mirrored ? WIDE - x : x) - GLASS_X) / (float) PX;
        }

        /** How far down the glass's picture a point this many pixels up from the sill is. */
        private float glassV(int y) {
            return (tall - y - windowTop + GLASS_ABOVE) / (float) PX;
        }

        /**
         * The sides of the relief facing one way (dx, dy): wherever a pixel stands further in or further out than the
         * one beside it, or has an opening or the door's edge beside it, a strip along the edge in one block, showing the
         * colour of the pixels it is the edge of. Each run of pixels alike along the edge is one quad.
         */
        private void walls(int dx, int dy) {
            boolean across = dx != 0;
            int lines = across ? WIDE : tall;
            int along = across ? tall : WIDE;
            for (int line = 0; line < lines; line++) {
                // Each kind of strip (its side, depths and shade) runs from where it started along the line.
                Map<Wall, Integer> runs = new HashMap<>();
                for (int step = 0; step <= along; step++) {
                    List<Wall> here = step == along ? List.of() : across ? walls(line, step, dx, dy) : walls(step, line, dx, dy);
                    // A strip ends where the next pixel has none like it, and at a block's edge.
                    boolean blockEdge = step % PX == 0;
                    for (Map.Entry<Wall, Integer> run : new ArrayList<>(runs.entrySet())) {
                        if (blockEdge || !here.contains(run.getKey())) {
                            wall(run.getKey(), line, run.getValue(), step, dx, dy);
                            runs.remove(run.getKey());
                        }
                    }
                    for (Wall wall : here) {
                        runs.putIfAbsent(wall, step);
                    }
                }
            }
        }

        /** A strip of the relief's side: which picture and light, its depths from and to, and its shade. */
        private record Wall(Side side, int z0, int z1, float shade) {
        }

        /** The strips along one pixel's side facing (dx, dy). */
        private List<Wall> walls(int x, int y, int dx, int dy) {
            if (open[x][y]) {
                return List.of();
            }
            int nx = x + dx;
            int ny = y + dy;
            int in = inner[x][y];
            int out = outer[x][y];
            if (nx < 0 || ny < 0 || nx >= WIDE || ny >= tall) {
                return List.of(new Wall(Side.EDGE, in, out, 1));
            }
            float facing = dy > 0 ? FACING_UP : dy < 0 ? FACING_DOWN : FACING_ACROSS;
            float shade = stepShade(PANEL_IN - in) * facing;
            if (open[nx][ny]) {
                // The opening's sides: the lining's and the panel's in the cabin's light, past the seam outside.
                return List.of(new Wall(Side.INSIDE, in, PANEL_OUT, shade), new Wall(Side.OUTSIDE, PANEL_OUT, out, 1));
            }
            List<Wall> walls = new ArrayList<>(2);
            if (in < inner[nx][ny]) {
                walls.add(new Wall(Side.INSIDE, in, inner[nx][ny], shade));
            }
            if (out > outer[nx][ny]) {
                walls.add(new Wall(Side.OUTSIDE, outer[nx][ny], out, 1));
            }
            return walls;
        }

        /** One strip, along a line of pixels (x for one facing across, y for one facing up or down) from one to another. */
        private void wall(Wall wall, int line, int from, int to, int dx, int dy) {
            float[] corners;
            if (dx != 0) {
                int at = dx > 0 ? line + 1 : line;
                float u = u(line + 0.5F);
                corners = new float[]{
                    b(at), b(from), b(wall.z0()), u, v(from),
                    b(at), b(to), b(wall.z0()), u, v(to),
                    b(at), b(to), b(wall.z1()), u, v(to),
                    b(at), b(from), b(wall.z1()), u, v(from),
                };
            } else {
                int at = dy > 0 ? line + 1 : line;
                float v = v(line + 0.5F);
                corners = new float[]{
                    b(from), b(at), b(wall.z0()), u(from), v,
                    b(to), b(at), b(wall.z0()), u(to), v,
                    b(to), b(at), b(wall.z1()), u(to), v,
                    b(from), b(at), b(wall.z1()), u(from), v,
                };
            }
            int column = (dx != 0 ? line : from) / PX;
            int row = (dx != 0 ? from : line) / PX;
            quads.add(new Quad(wall.side(), corners, dx, dy, 0, column, row, wall.shade(), false));
        }

        /** Pixels to blocks. */
        private static float b(float pixels) {
            return pixels / PX;
        }

        /** How far across the picture a point this many pixels from the frame's left edge is: mirrored for a right hinge. */
        private float u(float x) {
            return mirrored ? 1 - x / WIDE : x / WIDE;
        }

        /** How far down the picture a point this many pixels up from the sill is. */
        private float v(float y) {
            return 1 - y / tall;
        }
    }
}
