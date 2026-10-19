package dev.aerolarge.block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The shapes of a skin block cut to the hull's curve, in two styles. Every shape is the block cut back by one or more
 * planes, keeping what lies below all of them; shape 0 is the whole block.
 * <p>
 * Fitted shapes cut the block by the hull's surface where it passes through it, taken as flat there: one plane, with
 * one of the whole-number directions with no component over 2 (98 of them, about 13 degrees apart at most), as deep
 * into the block as the surface lies, in eighths of the block's depth along that direction. Shape
 * {@code 1 + 7 * direction + (eighths - 1)} keeps the part of the block below its plane. Each block picks its own
 * plane, so the cuts of neighbouring blocks only roughly meet, with a small step where they do not.
 * <p>
 * Snapped shapes are cut through the block's corners and the middles of its edges only, so neighbouring blocks
 * always meet exactly. Each corner is inside the surface, on it ({@link #ON_SURFACE}) or outside; the surface crosses
 * an edge from an inside to an outside corner at the edge's middle, and the block keeps everything within its kept
 * corners and those crossings (their convex hull). Two blocks that share a side see the same four corners and edges
 * there, so their cuts run into each other along it without a step. What comes out is a builder's kit of slopes: 45
 * degrees corner to corner or middle to middle, one in two (about 27 or 63 degrees) corner to middle, the three-sided
 * corners where slopes meet, and a fold where the surface bends within one block. The kit is every corner pattern
 * a flat surface can make, numbered after the fitted shapes and leaving out those a fitted shape already cuts; those
 * that run unchanged along a level axis come first, so the curved cabin window can take just the fitted shapes and
 * those ({@link #WINDOW_COUNT}).
 */
public final class ShellGeometry {
    public static final int WHOLE = 0;
    /** Not a shape: the surface leaves nothing of the block. */
    public static final int NONE = -1;
    /** Not a shape: a snapped corner pattern no flat surface makes, so no shape is cut for it. */
    public static final int UNMATCHED = -2;
    /** Where a block's corner lies against the surface, for {@link #snapped}. */
    public static final int INSIDE = -1;
    public static final int ON = 0;
    public static final int OUTSIDE = 1;
    /**
     * How near the surface, in blocks, a corner has to lie to count as on it in the snapped style: within a quarter,
     * the surface is taken through the corner itself rather than the middles of the edges either side of it.
     */
    public static final double ON_SURFACE = 0.25;
    /** The cut lies this many steps deep, out of the block's whole depth along its direction. */
    private static final int LEVELS = 8;
    private static final List<int[]> DIRECTIONS = directions();
    /** The whole block and every fitted shape. */
    private static final int FITTED = 1 + DIRECTIONS.size() * (LEVELS - 1);
    /**
     * The snapped kit is found by tilting a plane through the block in every whole-number direction with no
     * component over this, and moving it through the block; finer than any pattern needs.
     */
    private static final int SWEEP = 8;
    /** The cube's twelve edges, as pairs of corners (corner {@code x + 2y + 4z}). */
    private static final int[][] EDGES = {
        {0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };
    private static final Kit KIT = kit();
    /** Every shape: the fitted ones, then the snapped kit. */
    public static final int COUNT = FITTED + KIT.corners().size();
    /** The shapes a curved cabin window can take: the fitted ones and the snapped ones that run along a level axis. */
    public static final int WINDOW_COUNT = FITTED + KIT.level();
    /** Sub-cells per block edge the collision shape is stepped in. */
    private static final int STEPS = 4;
    /** How thick, in pixels, the plate is that stands for a whole side in the occlusion shape; any thickness does. */
    private static final double PLATE = 1;
    /** How far from a plane a point may lie and still count as on it, so a corner on the cut is kept. */
    private static final double ON_PLANE = 1e-9;
    /** Faces smaller than this share of a block's side are slivers of rounding, not drawn. */
    private static final double SLIVER = 1e-6;
    private static final double[][][] PLANES = new double[COUNT][][];
    private static final List<List<Face>> FACES = new ArrayList<>(COUNT);
    private static final VoxelShape[] SHAPES = new VoxelShape[COUNT];
    private static final VoxelShape[] OCCLUSIONS = new VoxelShape[COUNT];

    static {
        for (int shape = 0; shape < COUNT; shape++) {
            PLANES[shape] = shape == WHOLE ? new double[0][]
                : shape < FITTED ? new double[][]{plane(shape)}
                : KIT.planes().get(shape - FITTED);
            FACES.add(faces(PLANES[shape]));
            SHAPES[shape] = shape == WHOLE ? Shapes.block() : stepped(PLANES[shape]);
            OCCLUSIONS[shape] = shape == WHOLE ? Shapes.block() : plates(shape);
        }
    }

    private ShellGeometry() {
    }

    /**
     * One face of a shape: its unit outward normal, its corners in order, anticlockwise seen from outside, and the
     * side of the block it lies on, or null for the cut across it.
     */
    public record Face(float nx, float ny, float nz, List<float[]> corners, @Nullable Direction side) {
        /** The block side the face mostly looks toward, for shading and its texture. */
        public Direction facing() {
            return Direction.getNearest(nx, ny, nz);
        }
    }

    /**
     * The snapped kit: each shape's corner pattern and planes, the level-axis ones first, and the shape for every
     * pattern a flat surface makes, the fitted shape where one has the same cut.
     */
    private record Kit(List<int[]> corners, List<double[][]> planes, int level, Map<Integer, Integer> shapes) {
    }

    /** The faces of a shape. */
    public static List<Face> faces(int shape) {
        return FACES.get(shape);
    }

    /** A shape in steps of a quarter block, for collision and selection. */
    public static VoxelShape shape(int shape) {
        return SHAPES[shape];
    }

    /**
     * What a shape shuts off from its neighbours, light and the faces drawn against it: a thin plate on each side the
     * cut leaves whole, nothing on the sides it cuts into. Daylight still gets into the block through those, so its cut
     * face is lit like the skin around it, but no further in than a whole side, which keeps it out of the cabin
     * behind. A neighbour's face is only hidden where the block's own covers all of it: the stepped shape would also
     * hide faces where the cut leaves a sliver of the side open.
     */
    public static VoxelShape occlusion(int shape) {
        return OCCLUSIONS[shape];
    }

    /** Whether the shape's cut leaves this side of the block whole: every corner of it kept. */
    public static boolean whole(int shape, Direction side) {
        for (double[] plane : PLANES[shape]) {
            for (double[] corner : side(side)) {
                if (above(plane, corner) > ON_PLANE) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The fitted shape for a block the surface passes through: its outward direction (any length), and the point on
     * it nearest the block's middle, both in the block's own coordinates (0 to 1 across it). The whole block if the
     * surface lies outside it, and {@link #NONE} if it leaves less than an eighth of it.
     */
    public static int cut(double nx, double ny, double nz, double px, double py, double pz) {
        int best = 0;
        double bestDot = Double.NEGATIVE_INFINITY;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        for (int d = 0; d < DIRECTIONS.size(); d++) {
            int[] v = DIRECTIONS.get(d);
            double dot = (v[0] * nx + v[1] * ny + v[2] * nz) / (length * Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]));
            if (dot > bestDot) {
                bestDot = dot;
                best = d;
            }
        }
        int[] v = DIRECTIONS.get(best);
        double low = Math.min(0, v[0]) + Math.min(0, v[1]) + Math.min(0, v[2]);
        double high = Math.max(0, v[0]) + Math.max(0, v[1]) + Math.max(0, v[2]);
        double depth = (v[0] * px + v[1] * py + v[2] * pz - low) / (high - low);
        int eighths = (int) Math.round(depth * LEVELS);
        if (eighths >= LEVELS) {
            return WHOLE;
        }
        return eighths < 1 ? NONE : 1 + best * (LEVELS - 1) + eighths - 1;
    }

    /**
     * The snapped shape for a block whose eight corners lie as given against the surface ({@link #INSIDE},
     * {@link #ON} or {@link #OUTSIDE}; corner {@code x + 2y + 4z}, each coordinate 0 or 1): the whole block with no
     * corner outside, {@link #NONE} where what lies within the surface has no volume, and {@link #UNMATCHED} for a
     * pattern no flat surface makes.
     */
    public static int snapped(int[] corners) {
        Integer shape = KIT.shapes().get(code(corners));
        if (shape != null) {
            return shape;
        }
        boolean outside = false;
        for (int corner : corners) {
            outside |= corner == OUTSIDE;
        }
        if (!outside) {
            return WHOLE;
        }
        return solid(kept(corners)) ? UNMATCHED : NONE;
    }

    /** The same shape with the block turned about its vertical middle. */
    public static int rotate(int shape, Rotation rotation) {
        return remap(shape, v -> {
            BlockPos turned = new BlockPos(v[0], v[1], v[2]).rotate(rotation);
            return new int[]{turned.getX(), turned.getY(), turned.getZ()};
        });
    }

    /** The same shape with the block mirrored. */
    public static int mirror(int shape, Mirror mirror) {
        return remap(shape, v -> switch (mirror) {
            case LEFT_RIGHT -> new int[]{v[0], v[1], -v[2]};
            case FRONT_BACK -> new int[]{-v[0], v[1], v[2]};
            default -> v;
        });
    }

    private interface DirectionMap {
        int[] map(int[] direction);
    }

    /**
     * The shape with the block turned or mirrored: a fitted plane's direction mapped, or a snapped pattern's corners,
     * each corner taken as its direction from the block's middle.
     */
    private static int remap(int shape, DirectionMap map) {
        if (shape == WHOLE) {
            return WHOLE;
        }
        if (shape >= FITTED) {
            int[] corners = KIT.corners().get(shape - FITTED);
            int[] mapped = new int[8];
            for (int corner = 0; corner < 8; corner++) {
                int[] v = map.map(new int[]{2 * (corner & 1) - 1, 2 * (corner >> 1 & 1) - 1, 2 * (corner >> 2 & 1) - 1});
                mapped[(v[0] + 1) / 2 + (v[1] + 1) + 2 * (v[2] + 1)] = corners[corner];
            }
            return KIT.shapes().getOrDefault(code(mapped), shape);
        }
        int direction = (shape - 1) / (LEVELS - 1);
        int[] mapped = map.map(DIRECTIONS.get(direction));
        for (int d = 0; d < DIRECTIONS.size(); d++) {
            int[] v = DIRECTIONS.get(d);
            if (v[0] == mapped[0] && v[1] == mapped[1] && v[2] == mapped[2]) {
                return 1 + d * (LEVELS - 1) + (shape - 1) % (LEVELS - 1);
            }
        }
        return shape;
    }

    /** The whole-number directions with no component over 2, each once (no common factor). */
    private static List<int[]> directions() {
        List<int[]> directions = new ArrayList<>();
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    if ((x != 0 || y != 0 || z != 0) && gcd(gcd(Math.abs(x), Math.abs(y)), Math.abs(z)) == 1) {
                        directions.add(new int[]{x, y, z});
                    }
                }
            }
        }
        return List.copyOf(directions);
    }

    /** A fitted shape's plane as {nx, ny, nz, offset}: the block keeps where {@code n . p <= offset}. */
    private static double[] plane(int shape) {
        int[] v = DIRECTIONS.get((shape - 1) / (LEVELS - 1));
        int eighths = (shape - 1) % (LEVELS - 1) + 1;
        double length = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        double low = Math.min(0, v[0]) + Math.min(0, v[1]) + Math.min(0, v[2]);
        double high = Math.max(0, v[0]) + Math.max(0, v[1]) + Math.max(0, v[2]);
        double offset = low + (high - low) * eighths / LEVELS;
        return new double[]{v[0] / length, v[1] / length, v[2] / length, offset / length};
    }

    /**
     * Every corner pattern a flat surface makes, found by sweeping a plane through the block in each direction, with
     * its turned and mirrored copies; each gets the planes of its kept hull, or the fitted shape with the same cut.
     */
    private static Kit kit() {
        Map<String, Integer> fitted = new HashMap<>();
        for (int shape = 1; shape < FITTED; shape++) {
            fitted.put(key(plane(shape)), shape);
        }
        TreeSet<Integer> codes = new TreeSet<>();
        for (int a = -SWEEP; a <= SWEEP; a++) {
            for (int b = -SWEEP; b <= SWEEP; b++) {
                for (int c = -SWEEP; c <= SWEEP; c++) {
                    if (a != 0 || b != 0 || c != 0) {
                        sweep(a, b, c, codes);
                    }
                }
            }
        }
        for (int code : List.copyOf(codes)) {
            for (int[] corners : symmetries(decode(code))) {
                codes.add(code(corners));
            }
        }
        Map<Integer, Integer> shapes = new HashMap<>();
        List<int[]> level = new ArrayList<>();
        List<int[]> rest = new ArrayList<>();
        for (int code : codes) {
            int[] corners = decode(code);
            List<double[]> kept = kept(corners);
            boolean outside = false;
            for (int corner : corners) {
                outside |= corner == OUTSIDE;
            }
            if (!outside || !solid(kept)) {
                continue;
            }
            List<double[]> planes = hull(kept);
            Integer same = planes.size() == 1 ? fitted.get(key(planes.getFirst())) : null;
            if (same != null) {
                shapes.put(code, same);
            } else {
                (runsAlong(corners, 1) || runsAlong(corners, 4) ? level : rest).add(corners);
            }
        }
        List<int[]> corners = new ArrayList<>(level);
        corners.addAll(rest);
        List<double[][]> planes = new ArrayList<>();
        for (int i = 0; i < corners.size(); i++) {
            shapes.put(code(corners.get(i)), FITTED + i);
            planes.add(hull(kept(corners.get(i))).toArray(double[][]::new));
        }
        return new Kit(List.copyOf(corners), List.copyOf(planes), level.size(), Map.copyOf(shapes));
    }

    /** The patterns of a plane with whole-number direction (a, b, c), moved through the block a step at a time. */
    private static void sweep(int a, int b, int c, TreeSet<Integer> codes) {
        double length = Math.sqrt(a * a + b * b + c * c);
        double[] heights = new double[8];
        double[] steps = new double[16];
        for (int corner = 0; corner < 8; corner++) {
            heights[corner] = (a * (corner & 1) + b * (corner >> 1 & 1) + c * (corner >> 2 & 1)) / length;
            steps[2 * corner] = heights[corner] - ON_SURFACE;
            steps[2 * corner + 1] = heights[corner] + ON_SURFACE;
        }
        Arrays.sort(steps);
        for (int i = 0; i + 1 < steps.length; i++) {
            if (steps[i + 1] - steps[i] < 1e-9) {
                continue;
            }
            double offset = (steps[i] + steps[i + 1]) / 2;
            int[] corners = new int[8];
            for (int corner = 0; corner < 8; corner++) {
                double above = heights[corner] - offset;
                corners[corner] = above < -ON_SURFACE ? INSIDE : above > ON_SURFACE ? OUTSIDE : ON;
            }
            codes.add(code(corners));
        }
    }

    /** A pattern with the block turned and mirrored every way a cube can be: its axes swapped and flipped. */
    private static List<int[]> symmetries(int[] corners) {
        int[][] orders = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        List<int[]> all = new ArrayList<>();
        for (int[] order : orders) {
            for (int flips = 0; flips < 8; flips++) {
                int[] mapped = new int[8];
                for (int corner = 0; corner < 8; corner++) {
                    int[] bits = {corner & 1, corner >> 1 & 1, corner >> 2 & 1};
                    int to = 0;
                    for (int axis = 0; axis < 3; axis++) {
                        to |= (bits[order[axis]] ^ (flips >> axis & 1)) << axis;
                    }
                    mapped[to] = corners[corner];
                }
                all.add(mapped);
            }
        }
        return all;
    }

    /** Whether the pattern runs unchanged along an axis: each corner matched by the one a step along it. */
    private static boolean runsAlong(int[] corners, int step) {
        for (int corner = 0; corner < 8; corner++) {
            if (corners[corner] != corners[corner ^ step]) {
                return false;
            }
        }
        return true;
    }

    /** The points a snapped shape keeps the hull of: its corners not outside, and where its edges cross the surface. */
    private static List<double[]> kept(int[] corners) {
        List<double[]> points = new ArrayList<>();
        for (int corner = 0; corner < 8; corner++) {
            if (corners[corner] != OUTSIDE) {
                points.add(corner(corner));
            }
        }
        for (int[] edge : EDGES) {
            if (corners[edge[0]] * corners[edge[1]] < 0) {
                double[] a = corner(edge[0]);
                double[] b = corner(edge[1]);
                points.add(new double[]{(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2});
            }
        }
        return points;
    }

    /** Whether the points span a volume, rather than lying in one plane or line. */
    private static boolean solid(List<double[]> points) {
        for (double[] a : points) {
            for (double[] b : points) {
                for (double[] c : points) {
                    double[] n = cross(sub(b, a), sub(c, a));
                    for (double[] d : points) {
                        if (Math.abs(dot(n, sub(d, a))) > 1e-9) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * The planes bounding the points' convex hull, other than the block's own sides: every plane through three of
     * them with all the rest on or below it.
     */
    private static List<double[]> hull(List<double[]> points) {
        List<double[]> planes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < points.size(); i++) {
            for (int j = i + 1; j < points.size(); j++) {
                for (int k = j + 1; k < points.size(); k++) {
                    double[] n = cross(sub(points.get(j), points.get(i)), sub(points.get(k), points.get(i)));
                    double length = Math.sqrt(dot(n, n));
                    if (length < 1e-9) {
                        continue;
                    }
                    double[] plane = {n[0] / length, n[1] / length, n[2] / length, dot(n, points.get(i)) / length};
                    boolean below = true;
                    boolean over = true;
                    for (double[] p : points) {
                        double d = above(plane, p);
                        below &= d <= 1e-9;
                        over &= d >= -1e-9;
                    }
                    if (!below && !over) {
                        continue;
                    }
                    if (!below) {
                        plane = new double[]{-plane[0], -plane[1], -plane[2], -plane[3]};
                    }
                    if (!blockSide(plane) && seen.add(key(plane))) {
                        planes.add(plane);
                    }
                }
            }
        }
        planes.sort(Comparator.comparing(ShellGeometry::key));
        return planes;
    }

    /** Whether a plane is one of the block's own sides, cutting nothing away. */
    private static boolean blockSide(double[] plane) {
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(Math.abs(plane[axis]) - 1) < 1e-9) {
                return plane[axis] > 0 ? Math.abs(plane[3] - 1) < 1e-9 : Math.abs(plane[3]) < 1e-9;
            }
        }
        return false;
    }

    /** A plane rounded for comparing: the same plane always gives the same key. */
    private static String key(double[] plane) {
        StringBuilder key = new StringBuilder();
        for (double v : plane) {
            key.append(Math.round(v * 1e6)).append(',');
        }
        return key.toString();
    }

    /** A corner pattern as one number, each corner a digit in base 3. */
    private static int code(int[] corners) {
        int code = 0;
        for (int corner = 7; corner >= 0; corner--) {
            code = code * 3 + corners[corner] + 1;
        }
        return code;
    }

    private static int[] decode(int code) {
        int[] corners = new int[8];
        for (int corner = 0; corner < 8; corner++) {
            corners[corner] = code % 3 - 1;
            code /= 3;
        }
        return corners;
    }

    private static double[] corner(int corner) {
        return new double[]{corner & 1, corner >> 1 & 1, corner >> 2 & 1};
    }

    /** The block's six sides cut back to the planes, and each plane's cut across it, cut back to the others. */
    private static List<Face> faces(double[][] planes) {
        List<Face> faces = new ArrayList<>();
        for (Direction side : Direction.values()) {
            List<double[]> kept = side(side);
            for (double[] plane : planes) {
                kept = clip(kept, plane);
            }
            if (area(kept) > SLIVER) {
                faces.add(face(side.getStepX(), side.getStepY(), side.getStepZ(), kept, side));
            }
        }
        for (double[] plane : planes) {
            List<double[]> cut = section(plane);
            for (double[] other : planes) {
                if (other != plane) {
                    cut = clip(cut, other);
                }
            }
            if (area(cut) > SLIVER) {
                faces.add(face(plane[0], plane[1], plane[2], cut, null));
            }
        }
        return List.copyOf(faces);
    }

    /** A polygon's area, for telling a face from a sliver left by rounding. */
    private static double area(List<double[]> polygon) {
        double[] sum = new double[3];
        for (int i = 1; i + 1 < polygon.size(); i++) {
            double[] n = cross(sub(polygon.get(i), polygon.getFirst()), sub(polygon.get(i + 1), polygon.getFirst()));
            for (int k = 0; k < 3; k++) {
                sum[k] += n[k];
            }
        }
        return Math.sqrt(dot(sum, sum)) / 2;
    }

    private static Face face(double nx, double ny, double nz, List<double[]> corners, @Nullable Direction side) {
        List<float[]> points = corners.stream().map(p -> new float[]{(float) p[0], (float) p[1], (float) p[2]}).toList();
        return new Face((float) nx, (float) ny, (float) nz, points, side);
    }

    /** A side of the block as a square, anticlockwise seen from outside, as the game's own block faces are. */
    private static List<double[]> side(Direction side) {
        return switch (side) {
            case DOWN -> List.of(new double[]{0, 0, 1}, new double[]{0, 0, 0}, new double[]{1, 0, 0}, new double[]{1, 0, 1});
            case UP -> List.of(new double[]{0, 1, 0}, new double[]{0, 1, 1}, new double[]{1, 1, 1}, new double[]{1, 1, 0});
            case NORTH -> List.of(new double[]{1, 1, 0}, new double[]{1, 0, 0}, new double[]{0, 0, 0}, new double[]{0, 1, 0});
            case SOUTH -> List.of(new double[]{0, 1, 1}, new double[]{0, 0, 1}, new double[]{1, 0, 1}, new double[]{1, 1, 1});
            case WEST -> List.of(new double[]{0, 1, 0}, new double[]{0, 0, 0}, new double[]{0, 0, 1}, new double[]{0, 1, 1});
            case EAST -> List.of(new double[]{1, 1, 1}, new double[]{1, 0, 1}, new double[]{1, 0, 0}, new double[]{1, 1, 0});
        };
    }

    /** The part of a polygon on the kept side of the plane (Sutherland-Hodgman). */
    private static List<double[]> clip(List<double[]> polygon, double[] plane) {
        List<double[]> kept = new ArrayList<>();
        for (int i = 0; i < polygon.size(); i++) {
            double[] a = polygon.get(i);
            double[] b = polygon.get((i + 1) % polygon.size());
            double da = above(plane, a);
            double db = above(plane, b);
            if (da <= ON_PLANE) {
                kept.add(a);
            }
            if (da > ON_PLANE && db < -ON_PLANE || da < -ON_PLANE && db > ON_PLANE) {
                double t = da / (da - db);
                kept.add(new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t});
            }
        }
        return kept;
    }

    /** Where the plane crosses the block's edges, sorted around their middle anticlockwise seen along the plane's normal. */
    private static List<double[]> section(double[] plane) {
        List<double[]> points = new ArrayList<>();
        for (int axis = 0; axis < 3; axis++) {
            for (int u = 0; u <= 1; u++) {
                for (int w = 0; w <= 1; w++) {
                    double[] a = new double[3];
                    double[] b = new double[3];
                    a[(axis + 1) % 3] = b[(axis + 1) % 3] = u;
                    a[(axis + 2) % 3] = b[(axis + 2) % 3] = w;
                    b[axis] = 1;
                    double da = above(plane, a);
                    double db = above(plane, b);
                    if (da * db <= 0 && Math.abs(da - db) > 1e-12) {
                        double t = da / (da - db);
                        double[] p = {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
                        if (points.stream().noneMatch(q -> Math.abs(q[0] - p[0]) + Math.abs(q[1] - p[1]) + Math.abs(q[2] - p[2]) < 1e-9)) {
                            points.add(p);
                        }
                    }
                }
            }
        }
        if (points.size() < 3) {
            return points;
        }
        double[] middle = new double[3];
        for (double[] p : points) {
            for (int k = 0; k < 3; k++) {
                middle[k] += p[k] / points.size();
            }
        }
        double[] u = sub(points.getFirst(), middle);
        double[] v = cross(plane, u);
        return points.stream().sorted(Comparator.comparingDouble(p -> {
            double[] d = sub(p, middle);
            return Math.atan2(dot(d, v), dot(d, u));
        })).toList();
    }

    /** How far a point lies beyond the plane, along its normal: positive where the block is cut away. */
    private static double above(double[] plane, double[] p) {
        return plane[0] * p[0] + plane[1] * p[1] + plane[2] * p[2] - plane[3];
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    /** The sub-cells whose middles lie below every plane, run together along x. */
    private static VoxelShape stepped(double[][] planes) {
        VoxelShape shape = Shapes.empty();
        double cell = 16.0 / STEPS;
        for (int k = 0; k < STEPS; k++) {
            for (int j = 0; j < STEPS; j++) {
                int start = -1;
                for (int i = 0; i <= STEPS; i++) {
                    boolean inside = i < STEPS && below(planes, new double[]{(i + 0.5) / STEPS, (j + 0.5) / STEPS, (k + 0.5) / STEPS});
                    if (inside && start < 0) {
                        start = i;
                    } else if (!inside && start >= 0) {
                        shape = Shapes.or(shape, Block.box(start * cell, j * cell, k * cell, i * cell, (j + 1) * cell, (k + 1) * cell));
                        start = -1;
                    }
                }
            }
        }
        // A thin sliver keeps at least its bottom-most quarter cell, so it can still be hit and broken.
        return shape.isEmpty() ? Block.box(0, 0, 0, cell, cell, cell) : shape.optimize();
    }

    private static boolean below(double[][] planes, double[] p) {
        for (double[] plane : planes) {
            if (above(plane, p) > 0) {
                return false;
            }
        }
        return true;
    }

    /** A plate against each side of the block the shape's cut leaves whole. */
    private static VoxelShape plates(int shape) {
        VoxelShape plates = Shapes.empty();
        for (Direction side : Direction.values()) {
            if (whole(shape, side)) {
                plates = Shapes.or(plates, switch (side) {
                    case DOWN -> Block.box(0, 0, 0, 16, PLATE, 16);
                    case UP -> Block.box(0, 16 - PLATE, 0, 16, 16, 16);
                    case NORTH -> Block.box(0, 0, 0, 16, 16, PLATE);
                    case SOUTH -> Block.box(0, 0, 16 - PLATE, 16, 16, 16);
                    case WEST -> Block.box(0, 0, 0, PLATE, 16, 16);
                    case EAST -> Block.box(16 - PLATE, 0, 0, 16, 16, 16);
                });
            }
        }
        return plates.optimize();
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }
}
