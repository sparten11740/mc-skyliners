package dev.aerolarge.client;

import dev.aerolarge.block.CabinArchBlock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The faces of a stretch of a cabin archway's wall (see {@link CabinArchBlock}), drawn round: the arch's underside, the
 * bead and groove of the moulding along the opening's edge and the white band's edge all follow the circle the archway
 * is cut to, in straight pieces too short to see, as the cockpit door's arch does. Only the blocks' outline, what bumps
 * into them, steps round the curve a pixel at a time. {@link CabinArchModel} bakes the faces into each piece's model
 * and into the whole archway in the hand.
 *
 * <p>Worked out in pixels: u across the wall from the opening's middle, v up it from the top of the opening's sides,
 * where the arch springs from, and w through it from one of its faces to the other. Round the opening the wall is in
 * rings (see {@link Ring}), straight down either side of the opening below the springing line and round the arch's
 * middle over it. Each ring shows on both faces of the wall, set back as far as it is into them; where two rings meet
 * and one is set back further, the other's edge shows between them, and the opening's edge, right through the wall, is
 * the arch's underside. Every piece of the curve starts and ends at the same points in every stretch, and a stretch's
 * faces are clipped to it exactly at its edges, so the faces of neighbouring stretches meet without a gap.
 */
final class ArchwayFaces {
    /** How thick the wall is, in pixels: a whole block. */
    static final double WALL = 16;
    /** How many pixels a block is across. The archway's columns of blocks are centred on its middle. */
    private static final double BLOCK = 16;
    /** How long each straight piece of the curve is at most, in pixels along the opening's edge. */
    private static final double PIECE = 3;
    /**
     * How near an even cut of the curve may come to one where the opening's edge crosses into the next block, as a
     * fraction of the angle between even cuts, before it is left out for that one.
     */
    private static final double NEAR_CUT = 0.25;
    /** How near a point of the curve has to come to an edge between blocks, in pixels, to be put right on it. */
    private static final double ON_EDGE = 1e-9;
    /** Cuts of the curve nearer each other than this, in radians, are one: two edges crossed at the same corner. */
    private static final double SAME_CUT = 1e-12;
    /** Lengths and areas under this, in pixels, count as nothing: a face clipped down to a sliver is left out. */
    private static final double SLIVER = 1e-4;

    /**
     * The rings of the wall round the opening, from its edge out: the moulding's bead, flush with the wall's faces; its
     * groove, set back half a pixel into both; the white panelling out to the band's edge; and the bulkhead's cream
     * panelling beyond it. Each is named in the model's JSON by the texture it is drawn with.
     */
    enum Ring {
        BEAD("trim", 0, 1),
        GROOVE("groove", 0.5, 2),
        BAND("arch", 0, CabinArchBlock.BAND),
        PANEL("wall", 0, Double.POSITIVE_INFINITY);

        /** The name its texture goes by in the model's JSON. */
        final String texture;
        /** How far it is set back into both faces of the wall, in pixels. */
        final double inset;
        /** How far out from the opening's edge it reaches, in pixels. */
        final double reach;

        Ring(String texture, double inset, double reach) {
            this.texture = texture;
            this.inset = inset;
            this.reach = reach;
        }

        /** How far out from the opening's edge it starts, in pixels: where the ring inside it stops. */
        double from() {
            return ordinal() == 0 ? 0 : values()[ordinal() - 1].reach;
        }
    }

    /**
     * A face, its corners anticlockwise seen from in front: each in pixels across, up and through the wall from the
     * stretch's corner, and the way the face looks there, a unit long; the ring it belongs to; and whether it lies
     * along the wall's faces (on them, or the groove's set back into them) rather than round the curve or across the
     * wall's edge.
     */
    record Face(Ring ring, List<double[]> corners, List<double[]> normals, boolean alongWall) {
    }

    /** A stretch of the wall, from its corner (u and v) so wide and so tall, in pixels. */
    record Stretch(double u, double v, double width, double height) {
    }

    private final double radius;
    /**
     * The angles up from the arch's right-hand foot at which its quarter circle up to the crown is cut into straight
     * pieces (the left-hand quarter is the same, mirrored): evenly, no piece longer than {@link #PIECE}, and wherever
     * the opening's edge crosses from one block into the next, so that no straight piece of it cuts across the corner
     * of a block the curve itself only touches, which no piece of the archway is there to draw.
     */
    private final double[] cuts;
    /** How many straight pieces the arch's half circle is drawn in. */
    private final int pieces;
    private final Stretch stretch;
    /** How far out the outermost ring is drawn before it is clipped to the stretch: beyond all of it, in pixels. */
    private final double far;
    /**
     * Whether the stretch's edges are drawn too, wherever the wall reaches them: for the archway in the hand, standing
     * alone, and for each piece, whose model culls them against whatever stands beside it.
     */
    private final boolean rim;
    private final List<Face> faces = new ArrayList<>();

    private ArchwayFaces(int span, Stretch stretch, boolean rim) {
        this.radius = CabinArchBlock.radius(span);
        int even = (int) Math.ceil(Math.PI / 2 * radius / PIECE);
        this.cuts = cuts(radius, even);
        this.pieces = 2 * (cuts.length - 1);
        this.stretch = stretch;
        this.rim = rim;
        double reach = 0;
        for (double u : new double[]{stretch.u(), stretch.u() + stretch.width()}) {
            for (double v : new double[]{stretch.v(), stretch.v() + stretch.height()}) {
                reach = Math.max(reach, Math.hypot(u, v));
            }
        }
        this.far = reach / Math.cos(Math.PI / 4 / even) + 1;
    }

    /**
     * The angles a quarter circle of this radius is cut at (see {@link #cuts}), from its foot to its top, in order: so
     * many even cuts, and those where it crosses an edge between blocks.
     */
    private static double[] cuts(double radius, int even) {
        List<Double> crossings = new ArrayList<>();
        for (double u = BLOCK / 2; u < radius; u += BLOCK) {
            crossings.add(Math.acos(u / radius));
        }
        for (double v = BLOCK; v < radius; v += BLOCK) {
            crossings.add(Math.asin(v / radius));
        }
        double step = Math.PI / 2 / even;
        List<Double> cuts = new ArrayList<>(crossings);
        for (int i = 0; i <= even; i++) {
            double angle = i == even ? Math.PI / 2 : i * step;
            if (i == 0 || i == even || crossings.stream().noneMatch(crossing -> Math.abs(crossing - angle) < NEAR_CUT * step)) {
                cuts.add(angle);
            }
        }
        double[] sorted = cuts.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return IntStream.range(0, sorted.length).filter(i -> i == 0 || sorted[i] - sorted[i - 1] > SAME_CUT).mapToDouble(i -> sorted[i]).toArray();
    }

    /**
     * The faces of a stretch of the wall of an archway reaching so many blocks either side of its middle; with its rim,
     * the stretch's edges where the wall is solid (see {@link #rim}).
     */
    static List<Face> of(int span, Stretch stretch, boolean rim) {
        ArchwayFaces archway = new ArchwayFaces(span, stretch, rim);
        Ring inside = null;
        for (Ring ring : Ring.values()) {
            archway.ring(ring);
            archway.edge(inside, ring);
            inside = ring;
        }
        return archway.faces;
    }

    /**
     * Adds a ring's faces on both faces of the wall: a piece for each straight piece of the arch, and one down each
     * side of the opening below the springing line.
     */
    private void ring(Ring ring) {
        double from = radius + ring.from();
        double to = Math.min(radius + ring.reach, far);
        for (int i = 0; i < pieces; i++) {
            part(ring, List.of(round(from, i), round(to, i), round(to, i + 1), round(from, i + 1)));
        }
        part(ring, List.of(new double[]{from, -far}, new double[]{to, -far}, new double[]{to, 0}, new double[]{from, 0}));
        part(ring, List.of(new double[]{-to, -far}, new double[]{-from, -far}, new double[]{-from, 0}, new double[]{-to, 0}));
    }

    /**
     * Adds a part of a ring, a polygon anticlockwise across the wall, to both faces of the wall as far as it lies in
     * the stretch, each face set back by the ring's inset; and with the rim, its edges along the stretch's.
     */
    private void part(Ring ring, List<double[]> polygon) {
        List<double[]> clipped = clip(polygon);
        if (clipped.size() < 3 || area(clipped) < SLIVER) {
            return;
        }
        List<double[]> reversed = new ArrayList<>(clipped);
        Collections.reverse(reversed);
        wallFace(ring, at(reversed, ring.inset), new double[]{0, 0, -1});
        wallFace(ring, at(clipped, WALL - ring.inset), new double[]{0, 0, 1});
        if (rim) {
            for (int i = 0; i < clipped.size(); i++) {
                rimEdge(ring, clipped.get(i), clipped.get((i + 1) % clipped.size()));
            }
        }
    }

    /** With the rim, the edge of a ring's part between two of its corners where both lie on one edge of the stretch. */
    private void rimEdge(Ring ring, double[] a, double[] b) {
        double[] out = outOfStretch(a, b);
        if (out != null) {
            strip(ring, a, b, ring.inset, WALL - ring.inset, out, out);
        }
    }

    /**
     * The way out of the stretch across the edge of it that both points lie on, across the wall; null where they do
     * not lie on one edge.
     */
    private double[] outOfStretch(double[] a, double[] b) {
        double[][] edges = {
            {0, stretch.u(), -1}, {0, stretch.u() + stretch.width(), 1}, {1, stretch.v(), -1}, {1, stretch.v() + stretch.height(), 1},
        };
        for (double[] edge : edges) {
            int axis = (int) edge[0];
            if (Math.abs(a[axis] - edge[1]) < SLIVER && Math.abs(b[axis] - edge[1]) < SLIVER) {
                return axis == 0 ? new double[]{edge[2], 0, 0} : new double[]{0, edge[2], 0};
            }
        }
        return null;
    }

    /**
     * Adds the edge between a ring and the one inside it (or the opening), where one is set back further than the
     * other: the edge of the one standing further out, round the curve and down the opening's sides, facing the one
     * set back. The opening's edge reaches right through the wall: the arch's underside.
     */
    private void edge(Ring inside, Ring ring) {
        double insideInset = inside == null ? WALL / 2 : inside.inset;
        if (insideInset == ring.inset) {
            return;
        }
        boolean facesIn = insideInset > ring.inset;
        Ring showing = facesIn ? ring : inside;
        double shallow = Math.min(insideInset, ring.inset);
        double deep = Math.max(insideInset, ring.inset);
        double at = radius + ring.from();
        List<double[]> line = new ArrayList<>();
        line.add(new double[]{at, -far});
        for (int i = 0; i <= pieces; i++) {
            line.add(round(at, i));
        }
        line.add(new double[]{-at, -far});
        for (int i = 0; i + 1 < line.size(); i++) {
            edgePiece(showing, line.get(i), line.get(i + 1), shallow, deep, facesIn);
        }
    }

    /**
     * Adds a straight piece of an edge as far as it lies in the stretch, through the wall between the two depths, from
     * both faces of the wall (in one piece where it reaches right through), facing in toward the opening or out.
     */
    private void edgePiece(Ring ring, double[] from, double[] to, double shallow, double deep, boolean facesIn) {
        List<double[]> clipped = clip(List.of(from, to));
        if (clipped.size() != 2 || length(clipped.get(0), clipped.get(1)) < SLIVER) {
            return;
        }
        double[] a = clipped.get(0);
        double[] b = clipped.get(1);
        double[] aOut = outward(a, facesIn);
        double[] bOut = outward(b, facesIn);
        if (deep >= WALL / 2) {
            strip(ring, a, b, shallow, WALL - shallow, aOut, bOut);
            return;
        }
        strip(ring, a, b, shallow, deep, aOut, bOut);
        strip(ring, a, b, WALL - deep, WALL - shallow, aOut, bOut);
    }

    /**
     * The way a face round the opening looks at a point of it: straight out from the arch's middle over the springing
     * line, straight across below it, and back toward the opening for a face that faces in.
     */
    private static double[] outward(double[] p, boolean facesIn) {
        double sign = facesIn ? -1 : 1;
        if (p[1] <= 0) {
            return new double[]{sign * Math.signum(p[0]), 0, 0};
        }
        double length = Math.hypot(p[0], p[1]);
        return new double[]{sign * p[0] / length, sign * p[1] / length, 0};
    }

    /**
     * Adds a strip through the wall between two depths along a line across it, from a to b, looking the way given at
     * each end, its corners turned anticlockwise toward the way it looks.
     */
    private void strip(Ring ring, double[] a, double[] b, double w0, double w1, double[] aOut, double[] bOut) {
        List<double[]> corners = List.of(point(a, w0), point(b, w0), point(b, w1), point(a, w1));
        List<double[]> normals = List.of(aOut, bOut, bOut, aOut);
        double[] across = {b[0] - a[0], b[1] - a[1], 0};
        double[] through = {0, 0, w1 - w0};
        double[] facing = cross(across, through);
        double[] mean = {aOut[0] + bOut[0], aOut[1] + bOut[1], aOut[2] + bOut[2]};
        if (dot(facing, mean) >= 0) {
            faces.add(new Face(ring, corners, normals, false));
            return;
        }
        faces.add(new Face(ring, List.of(corners.get(3), corners.get(2), corners.get(1), corners.get(0)),
            List.of(normals.get(3), normals.get(2), normals.get(1), normals.get(0)), false));
    }

    /** Adds a face along the wall, its corners anticlockwise seen from the way it looks. */
    private void wallFace(Ring ring, List<double[]> corners, double[] normal) {
        faces.add(new Face(ring, corners, Collections.nCopies(corners.size(), normal), true));
    }

    /** Points across the wall, set this deep into it, from the stretch's corner. */
    private List<double[]> at(List<double[]> polygon, double w) {
        List<double[]> points = new ArrayList<>();
        for (double[] p : polygon) {
            points.add(point(p, w));
        }
        return points;
    }

    /** A point across the wall, this deep into it, from the stretch's corner. */
    private double[] point(double[] p, double w) {
        return new double[]{p[0] - stretch.u(), p[1] - stretch.v(), w};
    }

    /**
     * The point at this distance from the arch's middle where the i-th straight piece of its half circle starts, from
     * its right-hand foot: worked out the same for both halves, mirrored, so the arch is the same both ways round, and
     * put right on the edge between blocks where it crosses one.
     */
    private double[] round(double distance, int i) {
        int fromFoot = Math.min(i, pieces - i);
        double angle = cuts[fromFoot];
        double u = fromFoot == cuts.length - 1 ? 0 : onEdge(distance * Math.cos(angle), BLOCK / 2);
        double v = onEdge(distance * Math.sin(angle), 0);
        return new double[]{i > pieces - i ? -u : u, v};
    }

    /**
     * A distance across or up the wall put right on the nearest edge between blocks where it is that near one, the
     * edges lying a block apart from the one given.
     */
    private static double onEdge(double at, double edge) {
        double nearest = edge + Math.round((at - edge) / BLOCK) * BLOCK;
        return Math.abs(at - nearest) < ON_EDGE ? nearest : at;
    }

    /**
     * A convex polygon across the wall, or a line from one point to another, cut down to the stretch; fewer than two
     * points where none of it is in the stretch.
     */
    private List<double[]> clip(List<double[]> polygon) {
        List<double[]> clipped = clip(polygon, 0, stretch.u(), true);
        clipped = clip(clipped, 0, stretch.u() + stretch.width(), false);
        clipped = clip(clipped, 1, stretch.v(), true);
        return clip(clipped, 1, stretch.v() + stretch.height(), false);
    }

    /**
     * The part of a convex polygon on one side of a line across or up the wall, at the given u (axis 0) or v (axis 1):
     * at or past it, or at or short of it. Where a side of the polygon crosses the line, the crossing lies on the line
     * exactly, and is the same point whichever way round the side runs.
     */
    private static List<double[]> clip(List<double[]> polygon, int axis, double line, boolean past) {
        List<double[]> kept = new ArrayList<>();
        for (int i = 0; i < polygon.size(); i++) {
            double[] a = polygon.get(i);
            double[] b = polygon.get((i + 1) % polygon.size());
            boolean aKept = past ? a[axis] >= line : a[axis] <= line;
            boolean bKept = past ? b[axis] >= line : b[axis] <= line;
            if (aKept) {
                kept.add(a);
            }
            if (aKept != bKept) {
                kept.add(crossing(a, b, axis, line));
            }
        }
        return withoutRepeats(kept);
    }

    /** Where the line from a to b crosses the given u (axis 0) or v (axis 1), worked out from its lower end. */
    private static double[] crossing(double[] a, double[] b, int axis, double line) {
        boolean aFirst = a[0] < b[0] || a[0] == b[0] && a[1] <= b[1];
        double[] p = aFirst ? a : b;
        double[] q = aFirst ? b : a;
        double t = (line - p[axis]) / (q[axis] - p[axis]);
        int other = 1 - axis;
        double[] crossing = new double[2];
        crossing[axis] = line;
        crossing[other] = p[other] + t * (q[other] - p[other]);
        return crossing;
    }

    /** The points of a polygon without any that repeat the one before it, the last counting as before the first. */
    private static List<double[]> withoutRepeats(List<double[]> points) {
        List<double[]> kept = new ArrayList<>();
        for (double[] p : points) {
            if (kept.isEmpty() || length(kept.getLast(), p) >= SLIVER) {
                kept.add(p);
            }
        }
        while (kept.size() > 1 && length(kept.getLast(), kept.getFirst()) < SLIVER) {
            kept.removeLast();
        }
        return kept;
    }

    /** A polygon's area across the wall, in square pixels: positive where it runs anticlockwise. */
    private static double area(List<double[]> polygon) {
        double sum = 0;
        for (int i = 0; i < polygon.size(); i++) {
            double[] a = polygon.get(i);
            double[] b = polygon.get((i + 1) % polygon.size());
            sum += a[0] * b[1] - b[0] * a[1];
        }
        return sum / 2;
    }

    private static double length(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
}
