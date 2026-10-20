package dev.aerolarge.client;

import dev.aerolarge.block.PlugDoorBlock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A plug door's panel cut into quads, once for each way it hinges and each height, for {@link PlugDoorRenderer} to
 * draw. It is built up in layers, each one a shape cut out of the door's picture and stood on the one below, so its
 * edges step the way a rounded edge does in blocks:
 *
 * <ul>
 *   <li>The panel itself, four pixels thick, its outside stepping down a pixel at a time from the skin flush with the
 *   hull to two pixels deep at its edge: the door's seam in the picture is a real gap all round it, and the flush
 *   handle is sunk a pixel into the skin.</li>
 *   <li>Inside, the padded lining: rounded rectangles stacked three pixels proud of the panel, each smaller than the
 *   one under it, steep at its edge and flat on top as a cushion is, a shade darker toward its edge; the window set
 *   into it through a reveal as deep as it is, and the handle a bar a pixel proud of it.</li>
 * </ul>
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
    /** The panel's inner face and where its outside steps up to the skin, in pixels out from the cabin's side. */
    private static final int PANEL_IN = PX - PlugDoorBlock.THICKNESS;
    private static final int PANEL_OUT = PX - 2;
    /**
     * The lining's tiers, from the panel in, one for each of the {@link PlugDoorBlock#LINING} pixels it stands proud:
     * how far in from the door's edge each one is, its corners' radius (so round the lining reads as an oval in a
     * two-block door) and its shade.
     */
    private static final int[] TIER_INSET = {2, 3, 5};
    private static final int[] TIER_RADIUS = {8, 7, 5};
    private static final float[] TIER_SHADE = {0.86F, 0.93F, 1};
    /** The shade of the panel's faces where they show past what stands on them: round the lining, in the seam. */
    private static final float PANEL_SHADE = 0.8F;
    /** How far round the window's middle its frame reaches, as the pictures paint it. */
    private static final double WINDOW = 2.6;

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
     * One quad: its four corners, each (x, y, z, u, v) in blocks and across the picture 0 to 1, the way it faces, the
     * block of the door (column, row) whose light it is drawn in, and how much it is shaded, 0 to 1.
     */
    record Quad(Side side, float[] corners, float nx, float ny, float nz, int column, int row, float shade) {
    }

    private static final Map<PlugDoorBlock.Hinge, Map<Integer, List<Quad>>> CACHE = new EnumMap<>(PlugDoorBlock.Hinge.class);

    private PlugDoorMesh() {
    }

    /** The quads of a door hinged this way and this many rows tall. */
    static synchronized List<Quad> of(PlugDoorBlock.Hinge hinge, int rows) {
        return CACHE.computeIfAbsent(hinge, h -> new HashMap<>()).computeIfAbsent(rows, r -> build(hinge, r));
    }

    private static List<Quad> build(PlugDoorBlock.Hinge hinge, int rows) {
        Builder mesh = new Builder(hinge, rows);
        boolean top = hinge == PlugDoorBlock.Hinge.TOP;
        int tall = rows * PX;
        // The handle, in the picture's pixels: as the pictures paint it, three pixels tall.
        int handleY = top ? tall - 6 : Math.round(tall * 0.55F);
        int handleFrom = top ? 12 : 21;
        int handleTo = top ? 19 : 27;
        Shape handle = (x, y) -> x >= handleFrom && x <= handleTo && Math.abs(y - handleY) <= 1;

        // The panel, its outside stepping up to the skin: the seam's dark gap, its pale edge, then the skin, the
        // flush handle sunk into it.
        mesh.layer(Side.EDGE, Side.INSIDE, Side.OUTSIDE, PANEL_IN, PANEL_OUT, (x, y) -> true, PANEL_SHADE);
        mesh.layer(Side.OUTSIDE, null, Side.OUTSIDE, PANEL_OUT, PANEL_OUT + 1, inset(rows, 1), 1);
        Shape skin = inset(rows, 2);
        mesh.layer(Side.OUTSIDE, null, Side.OUTSIDE, PANEL_OUT + 1, PX, (x, y) -> skin.has(x, y) && !handle.has(x, y), 1);
        // The lining, tier on tier, the window through it.
        for (int tier = 0; tier < PlugDoorBlock.LINING; tier++) {
            Shape oval = rounded(rows, TIER_INSET[tier], TIER_RADIUS[tier]);
            mesh.layer(Side.INSIDE, Side.INSIDE, null, PANEL_IN - tier - 1, PANEL_IN - tier,
                (x, y) -> oval.has(x, y) && window(x, y) > WINDOW, TIER_SHADE[tier]);
        }
        // The handle, down to the tier under the top one, so it stands whole where it runs past the top one's edge.
        int lining = PANEL_IN - PlugDoorBlock.LINING;
        mesh.layer(Side.INSIDE, Side.INSIDE, null, lining - 1, lining + 1, handle, 1);
        return List.copyOf(mesh.quads);
    }

    /** How far a pixel of the picture is from the window's middle, as the pictures measure it. */
    private static double window(int x, int y) {
        double dx = Math.max(Math.abs(x - 15.5) - 1.5, 0);
        double dy = Math.max(Math.max(8 - y, y - 13), 0);
        return Math.hypot(dx, dy);
    }

    /** The whole picture but this many pixels round its edge. */
    private static Shape inset(int rows, int by) {
        int tall = rows * PX;
        return (x, y) -> x >= by && y >= by && x < WIDE - by && y < tall - by;
    }

    /** A rectangle this many pixels in from the picture's edge, its corners rounded to this radius. */
    private static Shape rounded(int rows, int by, int radius) {
        int tall = rows * PX;
        return (x, y) -> {
            double cx = x + 0.5;
            double cy = y + 0.5;
            double dx = Math.max(Math.max(by + radius - cx, cx - (WIDE - by - radius)), 0);
            double dy = Math.max(Math.max(by + radius - cy, cy - (tall - by - radius)), 0);
            return Math.hypot(dx, dy) <= radius;
        };
    }

    /** Which pixels of the picture, x across and y down from its top left, a layer covers. */
    @FunctionalInterface
    private interface Shape {
        boolean has(int x, int y);
    }

    /** Cuts layers into quads, in the door's frame. */
    private static final class Builder {
        private final boolean mirrored;
        private final int tall;
        private final List<Quad> quads = new ArrayList<>();

        Builder(PlugDoorBlock.Hinge hinge, int rows) {
            mirrored = hinge == PlugDoorBlock.Hinge.RIGHT;
            tall = rows * PX;
        }

        /**
         * A layer from z0 to z1 (pixels out from the cabin's side of the doorway) over the picture's pixels in the
         * shape: its edges, its face toward the cabin (unless {@code in} is null) and its face out (unless {@code out}
         * is null), its faces this shade. Where one layer stands on another, the face between them is left out.
         */
        void layer(Side edges, Side in, Side out, int z0, int z1, Shape shape, float shade) {
            // The shape in the frame's pixels: x across to the right as seen from outside, y up from the sill.
            boolean[][] mask = new boolean[WIDE][tall];
            for (int x = 0; x < WIDE; x++) {
                for (int y = 0; y < tall; y++) {
                    mask[x][y] = shape.has(mirrored ? WIDE - 1 - x : x, tall - 1 - y);
                }
            }
            if (in != null) {
                faces(mask, in, z0, -1, shade);
            }
            if (out != null) {
                faces(mask, out, z1, 1, shade);
            }
            walls(mask, edges, z0, z1);
        }

        /** The layer's face at depth z, facing in (-1) or out (1): rectangles, none across a block's edge. */
        private void faces(boolean[][] mask, Side side, int z, int facing, float shade) {
            for (int column = 0; column < PlugDoorBlock.COLUMNS; column++) {
                for (int row = 0; row < tall / PX; row++) {
                    // Runs along each pixel row of the block, carried up while the rows above run alike.
                    Map<Long, Integer> open = new HashMap<>();
                    for (int y = row * PX; y <= (row + 1) * PX; y++) {
                        Map<Long, Integer> next = new HashMap<>();
                        for (int x = column * PX; y < (row + 1) * PX && x < (column + 1) * PX; x++) {
                            if (!mask[x][y]) {
                                continue;
                            }
                            int from = x;
                            while (x + 1 < (column + 1) * PX && mask[x + 1][y]) {
                                x++;
                            }
                            long run = (long) from << 32 | (x + 1);
                            next.put(run, open.getOrDefault(run, y));
                        }
                        for (Map.Entry<Long, Integer> run : open.entrySet()) {
                            if (!next.containsKey(run.getKey())) {
                                int x0 = (int) (run.getKey() >> 32);
                                int x1 = (int) (long) run.getKey();
                                face(side, x0, run.getValue(), x1, y, z, facing, column, row, shade);
                            }
                        }
                        open = next;
                    }
                }
            }
        }

        private void face(Side side, int x0, int y0, int x1, int y1, int z, int facing, int column, int row, float shade) {
            float[] corners = {
                b(x0), b(y0), b(z), u(x0), v(y0),
                b(x1), b(y0), b(z), u(x1), v(y0),
                b(x1), b(y1), b(z), u(x1), v(y1),
                b(x0), b(y1), b(z), u(x0), v(y1),
            };
            quads.add(new Quad(side, corners, 0, 0, facing, column, row, shade));
        }

        /**
         * The layer's edges, from z0 to z1, wherever a pixel of it has none of it beside it: each a strip along the
         * edge in one block, showing the colour of the pixels it is the edge of.
         */
        private void walls(boolean[][] mask, Side side, int z0, int z1) {
            for (int facing = -1; facing <= 1; facing += 2) {
                // Edges facing left and right, along x; then down and up, along y.
                for (int x = 0; x < WIDE; x++) {
                    int from = -1;
                    for (int y = 0; y <= tall; y++) {
                        boolean edge = y < tall && mask[x][y] && !in(mask, x + facing, y);
                        if (from >= 0 && (!edge || y % PX == 0)) {
                            int at = facing > 0 ? x + 1 : x;
                            float u = u(x + 0.5F);
                            quads.add(new Quad(side, new float[]{
                                b(at), b(from), b(z0), u, v(from),
                                b(at), b(y), b(z0), u, v(y),
                                b(at), b(y), b(z1), u, v(y),
                                b(at), b(from), b(z1), u, v(from),
                            }, facing, 0, 0, x / PX, from / PX, 1));
                            from = -1;
                        }
                        if (edge && from < 0) {
                            from = y;
                        }
                    }
                }
                for (int y = 0; y < tall; y++) {
                    int from = -1;
                    for (int x = 0; x <= WIDE; x++) {
                        boolean edge = x < WIDE && mask[x][y] && !in(mask, x, y + facing);
                        if (from >= 0 && (!edge || x % PX == 0)) {
                            int at = facing > 0 ? y + 1 : y;
                            float v = v(y + 0.5F);
                            quads.add(new Quad(side, new float[]{
                                b(from), b(at), b(z0), u(from), v,
                                b(x), b(at), b(z0), u(x), v,
                                b(x), b(at), b(z1), u(x), v,
                                b(from), b(at), b(z1), u(from), v,
                            }, 0, facing, 0, from / PX, y / PX, 1));
                            from = -1;
                        }
                        if (edge && from < 0) {
                            from = x;
                        }
                    }
                }
            }
        }

        private boolean in(boolean[][] mask, int x, int y) {
            return x >= 0 && y >= 0 && x < WIDE && y < tall && mask[x][y];
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
