package dev.skyliners.block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Where an LED strip laid by hand runs through its block (see {@link LedStripLineBlock}): from one end to the other,
 * each where it leaves its block, lying on a face of it at the middle of one of that face's edges. Between two ends on
 * one face it runs through the face's middle, straight across or bending round as a train track does; between ends on
 * two faces that meet, it runs through the middle of the first to the edge they share and on through the middle of the
 * second, round the inside of the corner. A strip leaving its block at an end goes on in the block beside it lying on
 * the same face, or, round the outside of what it lies on, in the block past that edge lying on the next face round.
 *
 * <p>The paths are numbered, as the block state keeps them, in the order {@link #ALL} lists them, which
 * tools/generate_resources.mjs lists them in too: every end in {@link Direction}'s order of faces and then of edges,
 * and every pair of them that makes a path, by the first end and then the second.
 */
public record StripPath(End first, End second) {
    /** Every end, in order: the face it lies on and then the edge it leaves that face by, each in Direction's order. */
    private static final List<End> ENDS = everyEnd();
    /** Every path, numbered by its place here. */
    public static final List<StripPath> ALL = everyPath();

    /** Where a strip leaves its block: lying on a face of it, at the middle of the edge of that face toward a side. */
    public record End(Direction face, Direction edge) {
        /** Turned or mirrored, as its block is. */
        End turned(UnaryOperator<Direction> turn) {
            return new End(turn.apply(face), turn.apply(edge));
        }

        /**
         * Where a strip leaving its block here goes on: lying on the same face in the block beside it past the edge,
         * or, round the outside of what it lies on, lying on the next face round in the block past that, coming in at
         * the edge nearest; each the block and the end of the strip there.
         */
        List<Joint> joints(BlockPos at) {
            BlockPos beside = at.relative(edge);
            return List.of(new Joint(beside, new End(face, edge.getOpposite()), false),
                new Joint(beside.relative(face), new End(edge.getOpposite(), face.getOpposite()), true));
        }
    }

    /**
     * A block a strip's end could go on into, the end of a strip there that would meet it, and whether round a corner.
     */
    public record Joint(BlockPos at, End end, boolean round) {
    }

    /** A bar lying on a face of the block, from the face's middle out to its edge toward a side. */
    public record Arm(Direction face, Direction edge) {
    }

    /** The path numbered so, as the block state keeps it. */
    public static StripPath of(int index) {
        return ALL.get(index);
    }

    /** Its number, as the block state keeps it. */
    public int index() {
        return ALL.indexOf(this);
    }

    /** The path between two ends, either way round, if they make one. */
    @Nullable
    public static StripPath between(End one, End other) {
        boolean ordered = ENDS.indexOf(one) < ENDS.indexOf(other);
        StripPath path = ordered ? new StripPath(one, other) : new StripPath(other, one);
        return ALL.contains(path) ? path : null;
    }

    /**
     * The path straight on from an end: across its face to the far edge; or, for an end on a face round the corner from
     * the one given, from the far edge of that face to the corner and on to the end.
     */
    public static StripPath straightFrom(End end, Direction face) {
        if (end.face() == face) {
            return between(end, new End(face, end.edge().getOpposite()));
        }
        return between(new End(face, end.face().getOpposite()), end);
    }

    /** Whether it has the end given. */
    public boolean has(End end) {
        return first.equals(end) || second.equals(end);
    }

    /** Its two ends. */
    public List<End> ends() {
        return List.of(first, second);
    }

    /** The faces it lies on: one, or two meeting at a corner. */
    public List<Direction> faces() {
        return first.face() == second.face() ? List.of(first.face()) : List.of(first.face(), second.face());
    }

    /** Its bars: out to each end from the middle of its face, and on two faces to the edge they share as well. */
    public List<Arm> arms() {
        if (first.face() == second.face()) {
            return List.of(new Arm(first.face(), first.edge()), new Arm(first.face(), second.edge()));
        }
        return List.of(new Arm(first.face(), first.edge()), new Arm(first.face(), second.face()),
            new Arm(second.face(), first.face()), new Arm(second.face(), second.edge()));
    }

    /** Turned or mirrored, as its block is. */
    public StripPath turned(UnaryOperator<Direction> turn) {
        StripPath path = between(first.turned(turn), second.turned(turn));
        return path == null ? this : path;
    }

    /**
     * Whether two ends make a path: two edges of one face, or an edge of each of two faces that meet, not the shared
     * one.
     */
    private static boolean makesPath(End one, End other) {
        if (one.face() == other.face()) {
            return one.edge() != other.edge();
        }
        return one.face().getAxis() != other.face().getAxis() && one.edge() != other.face() && other.edge() != one.face();
    }

    private static List<End> everyEnd() {
        List<End> ends = new ArrayList<>();
        for (Direction face : Direction.values()) {
            ends.addAll(endsOf(face));
        }
        return List.copyOf(ends);
    }

    /** The ends on a face: one at each of its edges, in Direction's order. */
    static List<End> endsOf(Direction face) {
        return Direction.stream().filter(edge -> edge.getAxis() != face.getAxis()).map(edge -> new End(face, edge)).toList();
    }

    private static List<StripPath> everyPath() {
        List<StripPath> paths = new ArrayList<>();
        for (int one = 0; one < ENDS.size(); one++) {
            End from = ENDS.get(one);
            ENDS.subList(one + 1, ENDS.size()).stream().filter(to -> makesPath(from, to)).forEach(to -> paths.add(new StripPath(from, to)));
        }
        return List.copyOf(paths);
    }
}
