package dev.skyliners.airframe;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

/**
 * The lab's two bays either side of her shaft, each round one of her side windows (see {@link LabPlan}): on her left
 * the food lab, screened off by glazed partitions toward the counters in front and the tank behind; on her right the
 * wet bench. Each bay is cut into rows running front to back, from the window's front in to the walkway round the
 * shaft: along its outside the bay's wall facing in, an aisle, then pairs of rows back to back, each with its aisle,
 * as many as the room is wide (the wall and its aisle alone at the default scale, three pairs more at the largest). A
 * gap through the middle of every row, level with the window, leaves a way across the bay to it and a look out of it
 * from the walkway.
 *
 * <p>The food lab's wall is its machines: a bioreactor either side of the gap, and at its ends an algae column and a
 * hydroponic rack; its rows further in are hydroponic racks, now and then an algae column, a cabinet or a gap instead.
 * The bioreactors and algae columns are piped up into the ceiling, their pipes joined along it and run in over the
 * gap. The wet bench's wall is cabinets and a sink to wash at, the cabinet nearest the tank holding moss to feed the
 * tardigrade with, jars on some; its rows further in are lab benches with jars, a centrifuge or a sample rack on them
 * or nothing, a stool pushed out before some. What is set out unevenly is drawn from the room's random sequence.
 */
final class LabBays {
    /** How far out from her axis a bay's innermost row is, in blocks: the first past the walkway round the shaft. */
    private static final int BAY_IN = RocketLayout.SHAFT + 2;
    /**
     * Where along a row its places are, before and behind her side-to-side line, in blocks: up to just before the
     * tank, leaving the gap level with the window; the wet bench's from the tank's end.
     */
    private static final int[] PLACES = {-2, -1, 1, 2};
    private static final int[] WET_PLACES = {2, 1, -1, -2};
    /** How far before and behind her side-to-side line the food lab's partitions stand, in blocks: past its rows. */
    private static final int PARTITION_AT = 3;
    /** How many rows over the floor the partitions stand, a little over head height, and how wide one is at least. */
    static final int PARTITION_ROWS = 3;
    private static final int PARTITION_NARROWEST = 2;
    /** Where along the wet bench's wall its sink stands: by the gap, toward the tank. */
    private static final int SINK_AT = 1;
    /**
     * How likely a place in a row further in is left empty, or holds a cabinet, an algae column or a bioreactor, rather
     * than a rack.
     */
    private static final float GAP_CHANCE = 0.15F;
    private static final float CABINET_CHANCE = 0.1F;
    private static final float ALGAE_CHANCE = 0.1F;
    private static final float BIOREACTOR_CHANCE = 0.1F;
    /** How likely a cabinet on the wet bench has jars on it, a bench is bare, and a bench has a stool before it. */
    private static final float JARS_CHANCE = 0.5F;
    private static final float BARE_CHANCE = 0.25F;
    private static final float STOOL_CHANCE = 0.35F;
    /**
     * What stands on a lab bench that is not bare, drawn at random, each as likely as its weight given with it: jars,
     * a sample rack, a scan screen, a microscope or a centrifuge, but no more centrifuges than {@link #CENTRIFUGES}.
     */
    private static final LabPlan.Part[] ON_BENCHES = {LabPlan.Part.SPECIMENS, LabPlan.Part.SAMPLES, LabPlan.Part.SCAN, LabPlan.Part.MICROSCOPE,
        LabPlan.Part.CENTRIFUGE};
    private static final float[] BENCH_WEIGHTS = {0.35F, 0.25F, 0.15F, 0.1F, 0.15F};
    private static final int CENTRIFUGES = 2;
    /** How many rows over the floor a machine's pipe starts, on its hub. */
    private static final int PIPE_FROM = 3;

    /** What a row of a bay is, by how far in from its wall it is. */
    private enum Row {
        WALL, AISLE, OUTWARD, INWARD
    }

    private final LabPlan plan;
    private final LabPlan.Room room;
    private final int floor;
    private final int top;
    private final RandomSource scatter;
    /** How many centrifuges the benches have so far. */
    private int centrifuges;

    private LabBays(LabPlan plan, LabPlan.Room room, int floor, int top, RandomSource scatter) {
        this.plan = plan;
        this.room = room;
        this.floor = floor;
        this.top = top;
        this.scatter = scatter;
    }

    /** Lays both bays into the plan, after its tank and counters, where there is room for them. */
    static void lay(LabPlan plan, LabPlan.Room room, int floor, int top, RandomSource scatter) {
        LabBays bays = new LabBays(plan, room, floor, top, scatter);
        bays.foodLab();
        bays.wetBench();
    }

    /** The food lab, on her left: its partitions, its rows of machines and racks, and the pipes over them. */
    private void foodLab() {
        int outer = outermost(-1);
        if (outer < BAY_IN) {
            return;
        }
        partition(-PARTITION_AT);
        partition(PARTITION_AT);
        boolean algaeInFront = scatter.nextBoolean();
        List<BlockPos> piped = new ArrayList<>();
        for (int out = outer; out >= BAY_IN; out--) {
            foodRow(-out, row(outer - out), algaeInFront, piped);
        }
        pipes(piped);
    }

    /** A row of the food lab, at so far across: its places, if it is not an aisle. */
    private void foodRow(int x, Row row, boolean algaeInFront, List<BlockPos> piped) {
        if (row == Row.AISLE) {
            return;
        }
        for (int z : PLACES) {
            foodPlace(new BlockPos(x, floor + 1, z), row, algaeInFront, piped);
        }
    }

    /** A place in a row of the food lab: on its wall a machine, further in a rack or what is drawn instead. */
    private void foodPlace(BlockPos at, Row row, boolean algaeInFront, List<BlockPos> piped) {
        Direction facing = facing(-1, row);
        LabPlan.Part part = row == Row.WALL ? onWall(at.getZ(), algaeInFront) : drawn(scatter.nextFloat());
        if (part == LabPlan.Part.CABINET) {
            plan.place(at, part, facing, Set.of());
            return;
        }
        boolean piping = part == LabPlan.Part.BIOREACTOR || part == LabPlan.Part.ALGAE;
        if (part != null && plan.placeTall(at, part, facing) && piping) {
            piped.add(at.above(PIPE_FROM - 1));
        }
    }

    /**
     * The machine at a place along the food lab's wall: a bioreactor either side of the gap, and at the ends the algae
     * column, in front or behind as drawn, and the hydroponic rack.
     */
    private static LabPlan.Part onWall(int z, boolean algaeInFront) {
        if (Math.abs(z) == 1) {
            return LabPlan.Part.BIOREACTOR;
        }
        return (z < 0) == algaeInFront ? LabPlan.Part.ALGAE : LabPlan.Part.HYDROPONICS;
    }

    /**
     * What stands at a place in a row of racks, by a draw from 0 to 1: a gap (null), a cabinet, an algae column, a
     * bioreactor or a rack.
     */
    @Nullable
    private static LabPlan.Part drawn(float draw) {
        float[] chances = {GAP_CHANCE, CABINET_CHANCE, ALGAE_CHANCE, BIOREACTOR_CHANCE};
        LabPlan.Part[] parts = {null, LabPlan.Part.CABINET, LabPlan.Part.ALGAE, LabPlan.Part.BIOREACTOR};
        float below = 0;
        for (int kind = 0; kind < chances.length; kind++) {
            below += chances[kind];
            if (draw < below) {
                return parts[kind];
            }
        }
        return LabPlan.Part.HYDROPONICS;
    }

    /**
     * A glazed partition across the food lab's side of the room, so far before or behind her side-to-side line: from
     * the walkway out as far as the floor is free, full height at each block it stands at; none where it would be
     * narrower than {@link #PARTITION_NARROWEST}.
     */
    private void partition(int z) {
        int out = BAY_IN;
        while (open(-out, z, PARTITION_ROWS)) {
            out++;
        }
        if (out - BAY_IN < PARTITION_NARROWEST) {
            return;
        }
        for (int across = BAY_IN; across < out; across++) {
            for (int up = 1; up <= PARTITION_ROWS; up++) {
                plan.place(new BlockPos(-across, floor + up, z), LabPlan.Part.PARTITION, Direction.NORTH, Set.of());
            }
        }
    }

    /** The wet bench, on her right: cabinets and the sink along its wall, lab benches further in. */
    private void wetBench() {
        int outer = outermost(1);
        boolean fed = false;
        for (int out = outer; out >= BAY_IN; out--) {
            fed = wetRow(out, row(outer - out), fed);
        }
    }

    /** A row of the wet bench, at so far across: its places, if it is not an aisle. Whether the moss is laid yet. */
    private boolean wetRow(int x, Row row, boolean fed) {
        boolean laid = fed;
        if (row == Row.AISLE) {
            return laid;
        }
        for (int z : WET_PLACES) {
            laid |= wetPlace(new BlockPos(x, floor + 1, z), row, !laid);
        }
        return laid;
    }

    /**
     * A place in a row of the wet bench: on its wall the sink or a cabinet, the first one stocked with moss if it is
     * still to be, the floor before it kept clear to stand on; further in a bench. Whether it laid the moss's cabinet.
     */
    private boolean wetPlace(BlockPos at, Row row, boolean feed) {
        Direction facing = facing(1, row);
        if (row != Row.WALL) {
            bench(at, facing);
            return false;
        }
        plan.keepClear(at.relative(facing));
        if (at.getZ() == SINK_AT) {
            plan.place(at, LabPlan.Part.SINK, facing, Set.of());
            return false;
        }
        boolean laid = plan.place(at, feed ? LabPlan.Part.FEED_CABINET : LabPlan.Part.CABINET, facing, Set.of());
        if (laid && scatter.nextFloat() < JARS_CHANCE) {
            plan.place(at.above(), LabPlan.Part.SPECIMENS, facing, Set.of());
        }
        return laid && feed;
    }

    /**
     * A lab bench, what stands on it unless it is bare, and maybe a stool pushed out a little before it, but never into
     * the way across the bay to its window.
     */
    private void bench(BlockPos at, Direction facing) {
        if (!plan.place(at, LabPlan.Part.BENCH, facing, Set.of())) {
            return;
        }
        if (scatter.nextFloat() >= BARE_CHANCE) {
            plan.place(at.above(), onBench(), facing, Set.of());
        }
        if (scatter.nextFloat() < STOOL_CHANCE) {
            BlockPos before = at.relative(facing);
            BlockPos pushed = before.offset(0, 0, scatter.nextInt(3) - 1);
            plan.place(pushed.getZ() == 0 ? before : pushed, LabPlan.Part.STOOL, Direction.NORTH, Set.of());
        }
    }

    /** What stands on a bench, drawn by the weights, jars in place of a centrifuge once there are enough of them. */
    private LabPlan.Part onBench() {
        float draw = scatter.nextFloat();
        int kind = 0;
        while (kind < ON_BENCHES.length - 1 && draw >= BENCH_WEIGHTS[kind]) {
            draw -= BENCH_WEIGHTS[kind];
            kind++;
        }
        if (ON_BENCHES[kind] != LabPlan.Part.CENTRIFUGE) {
            return ON_BENCHES[kind];
        }
        centrifuges++;
        return centrifuges > CENTRIFUGES ? LabPlan.Part.SPECIMENS : LabPlan.Part.CENTRIFUGE;
    }

    /**
     * The pipes: one up from the hub of each machine given to the ceiling, those of a row joined along it as far as the
     * gap's line, and a run along that line over the gap joining the rows and reaching in to the walkway's edge.
     */
    private void pipes(List<BlockPos> hubs) {
        Set<BlockPos> run = new LinkedHashSet<>();
        for (BlockPos hub : hubs) {
            List<BlockPos> rise = new ArrayList<>();
            for (int y = hub.getY(); y <= top; y++) {
                rise.add(hub.atY(y));
            }
            if (rise.stream().allMatch(plan::placeable)) {
                run.addAll(rise);
            }
        }
        List<BlockPos> risen = run.stream().filter(at -> at.getY() == top).toList();
        for (BlockPos column : risen) {
            along(run, column.getX(), Math.min(0, column.getZ()), Math.max(0, column.getZ()));
        }
        int outer = risen.stream().mapToInt(at -> -at.getX()).max().orElse(0);
        for (int out = outer; out >= BAY_IN; out--) {
            along(run, -out, 0, 0);
        }
        for (BlockPos at : run) {
            plan.place(at, LabPlan.Part.PIPE, Direction.NORTH, ways(at, run, hubs));
        }
    }

    /** The ceiling's blocks along a line front to back from one place to another, those where a pipe may go. */
    private void along(Set<BlockPos> run, int x, int fromZ, int toZ) {
        for (int z = fromZ; z <= toZ; z++) {
            BlockPos at = new BlockPos(x, top, z);
            if (plan.placeable(at)) {
                run.add(at);
            }
        }
    }

    /**
     * The ways a pipe runs: to each pipe beside it, down into the hub of the machine under it, and up into the ceiling
     * where a run along the ceiling ends.
     */
    private Set<Direction> ways(BlockPos at, Set<BlockPos> run, List<BlockPos> hubs) {
        Set<Direction> ways = EnumSet.noneOf(Direction.class);
        for (Direction way : Direction.values()) {
            if (run.contains(at.relative(way))) {
                ways.add(way);
            }
        }
        if (hubs.contains(at)) {
            ways.add(Direction.DOWN);
        }
        long beside = ways.stream().filter(way -> way.getAxis().isHorizontal()).count();
        if (at.getY() == top && beside < 2) {
            ways.add(Direction.UP);
        }
        return ways;
    }

    /**
     * How far out the bay on a side reaches, -1 her left and 1 her right: its wall's column, the farthest out where a
     * machine is free to stand at every place along it, and in the gap, which the window's front keeps narrowest.
     */
    private int outermost(int side) {
        int out = BAY_IN - 1;
        while (standing(side * (out + 1))) {
            out++;
        }
        return out;
    }

    /** Whether a machine two blocks tall is free by the room's rule to stand at every place of a column, and the gap. */
    private boolean standing(int x) {
        for (int z = PLACES[0]; z <= PLACES[PLACES.length - 1]; z++) {
            if (!room.free(x, floor + 1, z) || !room.free(x, floor + 2, z)) {
                return false;
            }
        }
        return true;
    }

    /** Whether furniture may go in a column of the floor plan from the floor up so many rows, nothing laid there yet. */
    private boolean open(int x, int z, int rows) {
        for (int up = 1; up <= rows; up++) {
            if (!plan.placeable(new BlockPos(x, floor + up, z))) {
                return false;
            }
        }
        return true;
    }

    /** What a row of a bay is, so many in from its wall: the wall, its aisle, then back to back pairs with theirs. */
    private static Row row(int fromWall) {
        if (fromWall == 0) {
            return Row.WALL;
        }
        return switch ((fromWall - 1) % 3) {
            case 0 -> Row.AISLE;
            case 1 -> Row.OUTWARD;
            default -> Row.INWARD;
        };
    }

    /** The way the fronts of a row look, on a side: the wall's and an inward row's toward her shaft, the other's away. */
    private static Direction facing(int side, Row row) {
        Direction in = side < 0 ? Direction.EAST : Direction.WEST;
        return row == Row.OUTWARD ? in.getOpposite() : in;
    }
}
