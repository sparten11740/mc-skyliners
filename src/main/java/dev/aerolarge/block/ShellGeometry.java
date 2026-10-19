package dev.aerolarge.block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The shapes of a skin block cut to the hull's curve: the block cut by the hull's surface where it passes through it,
 * taken as flat there. A shape is that plane's outward direction, one of the whole-number directions with no
 * component over 2 (98 of them, about 13 degrees apart at most), and how deep into the block it lies, in eighths of
 * the block's depth along that direction. Neighbouring blocks cut by the same smooth surface line up into it, so the
 * hull reads as one curved skin. Shape 0 is the whole block; shape {@code 1 + 7 * direction + (eighths - 1)} keeps
 * the part of the block below its plane.
 */
public final class ShellGeometry {
    public static final int WHOLE = 0;
    /** The cut lies this many steps deep, out of the block's whole depth along its direction. */
    private static final int LEVELS = 8;
    private static final List<int[]> DIRECTIONS = directions();
    /** Every shape: the whole block and each direction at each depth from one to seven eighths. */
    public static final int COUNT = 1 + DIRECTIONS.size() * (LEVELS - 1);
    /** Sub-cells per block edge the collision shape is stepped in. */
    private static final int STEPS = 4;
    private static final List<List<Face>> FACES = new ArrayList<>(COUNT);
    private static final VoxelShape[] SHAPES = new VoxelShape[COUNT];

    static {
        for (int shape = 0; shape < COUNT; shape++) {
            List<Face> faces = faces(plane(shape));
            FACES.add(faces);
            SHAPES[shape] = shape == WHOLE ? Shapes.block() : stepped(plane(shape));
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

    /** The faces of a shape. */
    public static List<Face> faces(int shape) {
        return FACES.get(shape);
    }

    /** A shape in steps of a quarter block, for collision, selection and light. */
    public static VoxelShape shape(int shape) {
        return SHAPES[shape];
    }

    /**
     * The shape for a block the surface passes through: its outward direction (any length), and the point on it nearest
     * the block's middle, both in the block's own coordinates (0 to 1 across it). The whole block if the surface lies
     * outside it, and -1 if it leaves less than an eighth of it.
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
        return eighths < 1 ? -1 : 1 + best * (LEVELS - 1) + eighths - 1;
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

    private static int remap(int shape, DirectionMap map) {
        if (shape == WHOLE) {
            return WHOLE;
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

    /** A shape's plane as {nx, ny, nz, offset}: the block keeps where {@code n . p <= offset}; null for the whole block. */
    @Nullable
    private static double[] plane(int shape) {
        if (shape == WHOLE) {
            return null;
        }
        int[] v = DIRECTIONS.get((shape - 1) / (LEVELS - 1));
        int eighths = (shape - 1) % (LEVELS - 1) + 1;
        double length = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        double low = Math.min(0, v[0]) + Math.min(0, v[1]) + Math.min(0, v[2]);
        double high = Math.max(0, v[0]) + Math.max(0, v[1]) + Math.max(0, v[2]);
        double offset = low + (high - low) * eighths / LEVELS;
        return new double[]{v[0] / length, v[1] / length, v[2] / length, offset / length};
    }

    /** The block's six sides cut back to the plane, and the cut itself. */
    private static List<Face> faces(@Nullable double[] plane) {
        List<Face> faces = new ArrayList<>();
        for (Direction side : Direction.values()) {
            List<double[]> kept = clip(side(side), plane);
            if (kept.size() >= 3) {
                faces.add(face(side.getStepX(), side.getStepY(), side.getStepZ(), kept, side));
            }
        }
        if (plane != null) {
            List<double[]> cut = section(plane);
            if (cut.size() >= 3) {
                faces.add(face(plane[0], plane[1], plane[2], cut, null));
            }
        }
        return List.copyOf(faces);
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

    /** The part of a polygon on the kept side of the plane (Sutherland-Hodgman); all of it without a plane. */
    private static List<double[]> clip(List<double[]> polygon, @Nullable double[] plane) {
        if (plane == null) {
            return polygon;
        }
        List<double[]> kept = new ArrayList<>();
        for (int i = 0; i < polygon.size(); i++) {
            double[] a = polygon.get(i);
            double[] b = polygon.get((i + 1) % polygon.size());
            double da = above(plane, a);
            double db = above(plane, b);
            if (da <= 1e-9) {
                kept.add(a);
            }
            if (da > 1e-9 && db < -1e-9 || da < -1e-9 && db > 1e-9) {
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
        double[] u = {points.getFirst()[0] - middle[0], points.getFirst()[1] - middle[1], points.getFirst()[2] - middle[2]};
        double[] v = {plane[1] * u[2] - plane[2] * u[1], plane[2] * u[0] - plane[0] * u[2], plane[0] * u[1] - plane[1] * u[0]};
        return points.stream().sorted(Comparator.comparingDouble(p -> {
            double[] d = {p[0] - middle[0], p[1] - middle[1], p[2] - middle[2]};
            return Math.atan2(d[0] * v[0] + d[1] * v[1] + d[2] * v[2], d[0] * u[0] + d[1] * u[1] + d[2] * u[2]);
        })).toList();
    }

    /** How far a point lies beyond the plane, along its normal: positive where the block is cut away. */
    private static double above(double[] plane, double[] p) {
        return plane[0] * p[0] + plane[1] * p[1] + plane[2] * p[2] - plane[3];
    }

    /** The sub-cells whose middles lie below the plane, run together along x. */
    private static VoxelShape stepped(double[] plane) {
        VoxelShape shape = Shapes.empty();
        double cell = 16.0 / STEPS;
        for (int k = 0; k < STEPS; k++) {
            for (int j = 0; j < STEPS; j++) {
                int start = -1;
                for (int i = 0; i <= STEPS; i++) {
                    boolean inside = i < STEPS && above(plane, new double[]{(i + 0.5) / STEPS, (j + 0.5) / STEPS, (k + 0.5) / STEPS}) <= 0;
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

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }
}
