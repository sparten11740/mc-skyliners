package dev.aerolarge.airframe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The rocket's lab worked out on its story before a block is chosen (see {@link RocketLab}), so it can be drawn and
 * checked without the game's blocks. Behind the shaft stands the tank of her tardigrade, from the walkway round the
 * shaft right out to her skin behind it and on her left, which are its walls there, and glazed toward the room, thin
 * panes lit along their tops: its floor moss with a shallow pool and beds of pebbles, and wherever the creature never
 * goes something to look at, mossy boulders and rocks, ferns and azaleas growing from beds of moss, glow lichen on her
 * skin and a spore blossom hanging from the ceiling; and on her right, in its glass there, a feeding hatch at chest
 * height with the creature's vitals on a screen over it. In the front half, lab counters curve
 * round her skin either side of the way out, microscopes, a centrifuge, sample racks, specimen jars and scan screens on
 * them, a unit here and there left bare to work at, and a stool before each microscope, pushed back or aside as it was
 * left; by the hatch, stools to sit and watch it from. Either side of the shaft, round her side windows,
 * are the food lab and the wet bench (see {@link LabBays}).
 *
 * <p>The tank takes the ring along the skin that the room's rule keeps clear, as the creature needs the room, its back
 * window looking into it; the lights laid after it along the skin go round it, or light it. The furniture keeps to
 * the room's rule everywhere.
 *
 * <p>The creature paces a straight line along the tank, east and west, so long a body never meets its glass: the line
 * is where its whole outline, seen from above, stays inside the tank's panes, and it turns round only where nothing is
 * within its length all round; in a tank too narrow for that it always faces the hatch.
 */
final class LabPlan {
    /** How far behind the shaft's back wall the tank starts, in blocks: past the walkway round it. */
    private static final int TANK_BEHIND = 2;
    /**
     * How far out on her right the tank reaches, for each block the room reaches back in from the ring along her skin:
     * a little over half as far, its glass there, with the hatch in it, standing in the room. On her left it reaches
     * out to her skin.
     */
    private static final double TANK_SPREAD = 0.6;
    /** How thick the tank's panes are, in blocks, standing at the very edge of its outer blocks. */
    static final double PANE = 1 / 16.0;
    /** How high over the floor the feeding hatch is, at chest height, and the vitals screen over it, in rows. */
    private static final int HATCH_UP = 2;
    private static final int VITALS_UP = 3;
    /**
     * The creature's outline from above, half its length and half its width, in blocks, and how far from its middle it
     * reaches turning round: its round head and its tapered tail.
     */
    static final double HALF_LENGTH = 3.0;
    static final double HALF_WIDTH = 1.375;
    static final double TURN_REACH = 3.0;
    /** How far off its line the creature steps back or forward to turn round at most, in blocks. */
    private static final double TURN_ASIDE = 1.0;
    /**
     * Where its feet are, along it from its middle toward its head (its four pairs of legs, as its model has them) and
     * out to either side, in blocks.
     */
    private static final double[] FEET_ALONG = {1.0, -0.3, -1.6, -2.5};
    private static final double FEET_ACROSS = 1.1;
    /** How finely its outline and its line are tried, in blocks. */
    private static final double STEP = 0.25;
    /** How many beds of pebbles lie on the tank's floor at most, and how many rocks where the creature never goes. */
    private static final int PEBBLE_BEDS = 3;
    private static final int ROCKS = 3;
    /**
     * What else grows and stands where the creature never goes, taken in turn: how many at most, and how far apart,
     * in blocks; how likely a boulder is two high; and how many patches of glow lichen grow on her skin in the tank,
     * and how many spore blossoms hang from its ceiling, at most.
     */
    private static final int GROWING = 8;
    private static final int GROWING_SPACING = 2;
    private static final Part[] GROWS = {
        Part.AZALEA, Part.FERN, Part.BOULDER, Part.LARGE_FERN, Part.FLOWERING_AZALEA, Part.FERN, Part.BOULDER};
    private static final float TALL_BOULDER = 0.5F;
    private static final int LICHENS = 6;
    /** How many rows over the tank's floor the glow lichen grows from, clear of the creature's back as it passes. */
    private static final int LICHEN_FROM = 2;
    private static final int SPORE_BLOSSOMS = 2;
    /** How far apart the pebble beds, and the rocks, lie at least, in blocks. */
    private static final int SPACING = 3;
    /** The pool's size, across and front to back, in blocks. */
    private static final int POOL_ACROSS = 3;
    private static final int POOL_DEEP = 1;
    /**
     * The lab counters: how far in from the skin's inside they stand, in blocks, and where their two runs go round,
     * from and to how far round from her front, in degrees, either side of the way out.
     */
    private static final double COUNTER_IN = 2.5;
    private static final double[][] COUNTER_RUNS = {{20, 75}, {285, 340}};
    /** What stands on the counters, unit by unit along each run, round from her front clockwise. */
    private static final Part[][] ON_COUNTERS = {
        {Part.SCAN, Part.MICROSCOPE, Part.CENTRIFUGE, Part.SAMPLES},
        {Part.SPECIMENS, Part.SCAN, Part.MICROSCOPE, Part.SAMPLES}};
    /** How likely a counter's unit is left bare, a space to work at, unless a microscope or the centrifuge is on it. */
    private static final float BARE_CHANCE = 0.2F;
    /**
     * How likely a microscope's stool was left pushed back from the counter, or else aside, rather than tucked in before
     * it.
     */
    private static final float PUSHED_BACK = 0.3F;
    private static final float PUSHED_ASIDE = 0.3F;
    /** The points the creature's outline is tried at, about its middle, its feet facing either way, and its turning. */
    private static final List<Vec3> OUTLINE = outline(HALF_LENGTH + PANE, HALF_WIDTH + PANE);
    private static final List<Vec3> FEET = feet();
    private static final List<Vec3> TURNING = outline(TURN_REACH + PANE, TURN_REACH + PANE);

    /**
     * What is laid, by what it is. A food machine, the sink and the large fern stand two blocks tall: their upper half
     * is TOP, over the lower half laid first.
     */
    enum Part {
        TANK_GLASS, HATCH, VITALS, MOSS, POOL, PEBBLES, ROCK, BENCH, MICROSCOPE, SAMPLES, SCAN, STOOL,
        CENTRIFUGE, SPECIMENS, BIOREACTOR, HYDROPONICS, ALGAE, TOP, PARTITION, PIPE, CABINET, FEED_CABINET, SINK,
        /**
         * The tank's growth: a bed of moss a plant grows from; a fern, a large fern's halves, an azalea, a flowering
         * one; a mossy boulder; glow lichen on her skin, on the sides given; and a spore blossom under the ceiling.
         */
        MOSS_BED, FERN, LARGE_FERN, AZALEA, FLOWERING_AZALEA, BOULDER, LICHEN, SPORE_BLOSSOM
    }

    /**
     * A part at a block: facing a way (the way a fitting's front looks out), and its sides: for the tank's glass the
     * ways out of the tank it has panes on, and for a pipe the ways it runs.
     */
    record Placed(BlockPos at, Part part, Direction facing, Set<Direction> sides) {
    }

    /**
     * The line the creature paces, its ends and the point where it can turn round (null where it cannot), its feet on
     * the floor, in the layout's blocks (a block's corner at its whole numbers), west end first; where it cannot turn,
     * it faces its east end, by the hatch.
     */
    record Track(Vec3 from, Vec3 to, @Nullable Vec3 turn) {
    }

    /** The lab: its parts, the creature's line, its hatch and its vitals screen; null for each without a creature. */
    record Plan(List<Placed> parts, @Nullable Track track, @Nullable BlockPos hatch, @Nullable BlockPos vitals) {
    }

    /**
     * What the lab is laid in: where nothing stands yet, where the room's rule lets furniture stand, and how far out.
     */
    interface Room {
        /** Whether nothing of the rocket stands at a block yet. */
        boolean open(int x, int y, int z);

        /** Whether furniture may stand at a block, as in every room. */
        boolean free(int x, int y, int z);

        /** Whether a block lies in from the ring kept clear along the skin. */
        boolean within(int x, int y, int z);

        /** Whether a block's middle lies inside her skin's surface: open air there is her rooms', not the sky's. */
        boolean inside(int x, int y, int z);

        /** Whether a block stands in front of one of her windows, near the skin in the rows they are in. */
        boolean beforeWindow(int x, int y, int z);
    }

    private final Room room;
    private final int floor;
    private final int top;
    private final double wall;
    private final int reach;
    private final Set<Long> tank = new HashSet<>();
    private final Set<BlockPos> laid = new HashSet<>();
    private final List<Placed> parts = new ArrayList<>();

    private LabPlan(Room room, int floor, int top, double wall) {
        this.room = room;
        this.floor = floor;
        this.top = top;
        this.wall = wall;
        this.reach = (int) Math.ceil(wall);
    }

    /**
     * The lab on the story with its floor and its highest row given, the skin's inside so far out from her axis, what
     * is set out unevenly drawn from the room's random sequence.
     */
    static Plan of(Room room, int floor, int top, double wall, RandomSource scatter) {
        LabPlan plan = new LabPlan(room, floor, top, wall);
        plan.findTank();
        Track track = plan.tank.isEmpty() ? null : plan.track();
        BlockPos hatch = track == null ? null : plan.hatch(track);
        if (hatch != null) {
            plan.lay(hatch, track, scatter);
        }
        plan.counters(scatter);
        if (hatch != null) {
            plan.watchers(hatch);
        }
        LabBays.lay(plan, room, floor, top, scatter);
        BlockPos vitals = hatch == null ? null : new BlockPos(hatch.getX(), floor + VITALS_UP, hatch.getZ());
        return new Plan(List.copyOf(plan.parts), hatch == null ? null : track, hatch, vitals);
    }

    /**
     * The points of an outline seen from above, an ellipse of the half sizes given along and across, a step apart, and
     * the tips of its axes.
     */
    private static List<Vec3> outline(double along, double across) {
        List<Vec3> points = new ArrayList<>();
        int steps = (int) Math.ceil(Math.max(along, across) / STEP);
        int side = 2 * steps + 1;
        for (int i = 0; i < side * side; i++) {
            double u = (i % side - steps) * STEP;
            double v = (i / side - steps) * STEP;
            if (u * u / (along * along) + v * v / (across * across) <= 1) {
                points.add(new Vec3(u, 0, v));
            }
        }
        points.addAll(List.of(new Vec3(along, 0, 0), new Vec3(-along, 0, 0), new Vec3(0, 0, across), new Vec3(0, 0, -across)));
        return points;
    }

    /** Its feet's points about its middle, facing along the line either way. */
    private static List<Vec3> feet() {
        List<Vec3> feet = new ArrayList<>();
        for (int i = 0; i < FEET_ALONG.length * 4; i++) {
            double along = FEET_ALONG[i / 4] * ((i & 1) == 0 ? 1 : -1);
            feet.add(new Vec3(along, 0, (i & 2) == 0 ? FEET_ACROSS : -FEET_ACROSS));
        }
        return feet;
    }

    private static long column(int x, int z) {
        return BlockPos.asLong(x, 0, z);
    }

    private static long column(BlockPos at) {
        return column(at.getX(), at.getZ());
    }

    /** Every column within the room's reach from one row front to back to another, west to east along each. */
    private List<BlockPos> columns(int fromZ, int toZ) {
        List<BlockPos> columns = new ArrayList<>();
        int across = 2 * reach + 1;
        for (int i = 0; i < across * Math.max(0, toZ - fromZ + 1); i++) {
            columns.add(new BlockPos(i % across - reach, 0, fromZ + i / across));
        }
        return columns;
    }

    /**
     * The tank's columns: behind the walkway round the shaft, out to her skin behind it and on her left, on her right
     * a little over half as far as the room reaches back in from the ring along her skin, and open all the way up.
     */
    private void findTank() {
        int from = RocketLayout.SHAFT + TANK_BEHIND;
        int back = from;
        while (back < reach && room.open(0, floor + 1, back + 1)) {
            back++;
        }
        int roomBack = from;
        while (roomBack < reach && room.within(0, floor + 1, roomBack + 1)) {
            roomBack++;
        }
        int right = (int) (roomBack * TANK_SPREAD);
        for (BlockPos at : columns(from, back)) {
            if (at.getX() <= right && RocketRoom.offTheWays(at.getX(), at.getZ(), 0) && openColumn(at.getX(), at.getZ())) {
                tank.add(column(at));
            }
        }
    }

    /** Whether a column is open from the floor to the ceiling, inside her skin. */
    private boolean openColumn(int x, int z) {
        for (int y = floor + 1; y <= top; y++) {
            if (!room.open(x, y, z) || !room.inside(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    private boolean inTank(int x, int z) {
        return tank.contains(column(x, z));
    }

    /**
     * The ways out of the tank a column of it has panes on: toward each column beside it in the room, not the tank's;
     * toward her skin, which is the tank's wall there, none.
     */
    private Set<Direction> panes(int x, int z) {
        Set<Direction> panes = new HashSet<>();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            int besideX = x + side.getStepX();
            int besideZ = z + side.getStepZ();
            if (!inTank(besideX, besideZ) && room.open(besideX, floor + 1, besideZ)) {
                panes.add(side);
            }
        }
        return panes;
    }

    /** The ways from a column of the tank that her skin stands, the tank's wall there. */
    private Set<Direction> skinBeside(int x, int z) {
        Set<Direction> skin = new HashSet<>();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (!inTank(x + side.getStepX(), z + side.getStepZ()) && !room.open(x + side.getStepX(), floor + 1, z + side.getStepZ())) {
                skin.add(side);
            }
        }
        return skin;
    }

    /** Whether every point of an outline about a middle lies inside the tank. */
    private boolean inside(List<Vec3> outline, double x, double z) {
        for (Vec3 point : outline) {
            if (!inTank((int) Math.floor(x + point.x), (int) Math.floor(z + point.z))) {
                return false;
            }
        }
        return true;
    }

    /** Whether the creature stands at a middle, facing along the line: its outline and its feet inside the panes. */
    private boolean fits(double x, double z) {
        return inside(OUTLINE, x, z) && inside(FEET, x, z);
    }

    /**
     * The creature's line: of the lines east and west across the tank in a row its hatch can go in, so its mouth meets
     * the hatch at the line's end, the longest stretch its outline fits along, preferring one it can turn round from;
     * null where it fits nowhere.
     */
    @Nullable
    private Track track() {
        Track best = null;
        double bestScore = 0;
        for (double z = RocketLayout.SHAFT + TANK_BEHIND; z <= reach; z += STEP) {
            Track line = hatchable((int) Math.floor(z)) ? stretch(z) : null;
            double score = line == null ? 0 : line.from().distanceTo(line.to()) + (line.turn() != null ? reach : 0);
            if (line != null && (best == null || score > bestScore)) {
                best = line;
                bestScore = score;
            }
        }
        return best;
    }

    /** The longest stretch of a line east and west the outline fits along, and where it turns round. */
    @Nullable
    private Track stretch(double z) {
        int steps = (int) Math.round(2 * reach / STEP);
        boolean[] fits = new boolean[steps + 1];
        for (int i = 0; i <= steps; i++) {
            fits[i] = fits(-reach + i * STEP, z);
        }
        int[] run = longestRun(fits);
        if (run == null) {
            return null;
        }
        double from = -reach + run[0] * STEP;
        double to = -reach + run[1] * STEP;
        double y = floor + 1;
        Vec3 turn = turn(from, to, z);
        return new Track(new Vec3(from, y, z), new Vec3(to, y, z), turn == null ? null : new Vec3(turn.x, y, turn.z));
    }

    /** The first and last index of the longest run of trues; null for none. */
    @Nullable
    private static int[] longestRun(boolean[] values) {
        int[] best = null;
        int start = -1;
        for (int i = 0; i <= values.length; i++) {
            boolean on = i < values.length && values[i];
            if (on && start < 0) {
                start = i;
            }
            if (!on && start >= 0 && (best == null || i - 1 - start > best[1] - best[0])) {
                best = new int[]{start, i - 1};
            }
            start = on ? start : -1;
        }
        return best;
    }

    /**
     * Where along a stretch of a line, or a little off it, the creature turns round: nearest the stretch's middle where
     * nothing is within its reach all round, its outline fitting all the way from the line; null for nowhere.
     */
    @Nullable
    private Vec3 turn(double from, double to, double z) {
        Vec3 middle = new Vec3((from + to) / 2, 0, z);
        Vec3 best = null;
        int alongs = (int) Math.round((to - from) / STEP) + 1;
        int asides = (int) Math.round(2 * TURN_ASIDE / STEP) + 1;
        for (int i = 0; i < alongs * asides; i++) {
            Vec3 at = new Vec3(from + i % alongs * STEP, 0, z - TURN_ASIDE + i / alongs * STEP);
            boolean nearer = best == null || at.distanceTo(middle) < best.distanceTo(middle);
            if (nearer && inside(TURNING, at.x, at.z) && reachable(at.x, z, at.z)) {
                best = at;
            }
        }
        return best;
    }

    /** Whether the outline fits all the way from a point on the line to one a little off it, straight across. */
    private boolean reachable(double x, double fromZ, double toZ) {
        double step = Math.signum(toZ - fromZ) * STEP;
        for (double z = fromZ; step != 0 && Math.abs(z - fromZ) <= Math.abs(toZ - fromZ); z += step) {
            if (!fits(x, z)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Where the feeding hatch goes: in the tank's glass on her right, level with the creature's line, which is only
     * ever laid along a row the hatch can go in (see {@link #hatchable}).
     */
    private BlockPos hatch(Track track) {
        int z = (int) Math.floor(track.from().z);
        return new BlockPos(edge(z, -1), floor + HATCH_UP, z);
    }

    /**
     * Whether the feeding hatch can go in a row of the tank: in its glass on her right, where that glass has a pane
     * only on its outside and the room's floor before it is free to stand on.
     */
    private boolean hatchable(int z) {
        int x = edge(z, -1);
        return x != Integer.MIN_VALUE && panes(x, z).equals(Set.of(Direction.EAST)) && room.free(x + 1, floor + 1, z);
    }

    /**
     * The tank's last column along a row, coming in from the east (a step of -1) or the west (1); none for a row
     * without.
     */
    private int edge(int z, int step) {
        for (int x = -step * reach; Math.abs(x) <= reach; x += step) {
            if (inTank(x, z)) {
                return x;
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Lays the tank: the hatch and the vitals screen in its glass on her right, the floor before the hatch kept clear,
     * its glass all the way up its edges in the room, its floor, and what grows in it.
     */
    private void lay(BlockPos hatch, Track track, RandomSource scatter) {
        add(hatch, Part.HATCH, Direction.EAST, Set.of());
        add(new BlockPos(hatch.getX(), floor + VITALS_UP, hatch.getZ()), Part.VITALS, Direction.EAST, Set.of());
        laid.add(new BlockPos(hatch.getX() + 1, floor + 1, hatch.getZ()));
        for (long at : tank) {
            glass(BlockPos.getX(at), BlockPos.getZ(at));
        }
        Set<Long> swept = swept(track);
        List<BlockPos> growing = tankFloor(track, swept);
        grow(growing, scatter);
        lichen(swept, scatter);
        sporeBlossoms(track);
    }

    /** A column of the tank's glass on its edge, floor to ceiling, its panes on the sides that look out of it. */
    private void glass(int x, int z) {
        Set<Direction> panes = panes(x, z);
        if (panes.isEmpty()) {
            return;
        }
        for (int y = floor + 1; y <= top; y++) {
            add(new BlockPos(x, y, z), Part.TANK_GLASS, Direction.NORTH, panes);
        }
    }

    /**
     * The tank's floor inside its glass (the glass's own blocks are moss underfoot): rocks where the creature never
     * goes, moss on their tops; a pool toward the back behind the middle of its line; beds of pebbles, low enough to
     * walk over, apart from the rocks; and moss over the rest.
     */
    private List<BlockPos> tankFloor(Track track, Set<Long> swept) {
        List<BlockPos> inner = columns(-reach, reach).stream().filter(at -> inTank(at.getX(), at.getZ()) && panes(at.getX(), at.getZ()).isEmpty()).toList();
        List<BlockPos> rocks = apart(inner.stream().filter(at -> !swept.contains(column(at))).toList(), ROCKS);
        Set<Long> taken = new HashSet<>();
        rocks.forEach(at -> taken.add(column(at)));
        Set<Long> pool = pool(inner, taken, track);
        taken.addAll(pool);
        List<BlockPos> beds = apart(inner.stream().filter(at -> !taken.contains(column(at))).toList(), PEBBLE_BEDS);
        beds.forEach(at -> taken.add(column(at)));
        List<BlockPos> growing = apart(inner.stream().filter(at -> !swept.contains(column(at)) && !taken.contains(column(at))).toList(), GROWING,
            GROWING_SPACING);
        growing.forEach(at -> taken.add(column(at)));
        for (BlockPos at : inner) {
            if (!growing.contains(at)) {
                add(at.atY(floor + 1), floorPart(at, rocks, pool, beds), Direction.NORTH, Set.of());
            }
        }
        for (BlockPos rock : rocks) {
            add(rock.atY(floor + 2), Part.MOSS, Direction.NORTH, Set.of());
        }
        return growing;
    }

    /**
     * What grows and stands in the tank where the creature never goes, one of its kinds in turn at each spot: a plant
     * growing from a bed of moss, or a mossy boulder, one or two high, moss on its top.
     */
    private void grow(List<BlockPos> spots, RandomSource scatter) {
        for (int i = 0; i < spots.size(); i++) {
            BlockPos at = spots.get(i).atY(floor + 1);
            Part kind = GROWS[i % GROWS.length];
            if (kind == Part.BOULDER) {
                boulder(at, scatter.nextFloat() < TALL_BOULDER);
            } else {
                plant(at, kind);
            }
        }
    }

    /** A plant growing from a bed of moss, the large fern two blocks tall. */
    private void plant(BlockPos at, Part kind) {
        add(at, Part.MOSS_BED, Direction.NORTH, Set.of());
        add(at.above(), kind, Direction.NORTH, Set.of());
        if (kind == Part.LARGE_FERN) {
            add(at.above(2), Part.TOP, Direction.NORTH, Set.of());
        }
    }

    /** A mossy boulder, one or two high, moss on its top. */
    private void boulder(BlockPos at, boolean tall) {
        int high = tall ? 2 : 1;
        for (int up = 0; up < high; up++) {
            add(at.above(up), Part.BOULDER, Direction.NORTH, Set.of());
        }
        add(at.above(high), Part.MOSS, Direction.NORTH, Set.of());
    }

    /**
     * Glow lichen on her skin where it is the tank's wall, out of the creature's way: a patch at a time, a few rows up,
     * on the skin beside a column it never passes over, never on a window's glass.
     */
    private void lichen(Set<Long> swept, RandomSource scatter) {
        List<BlockPos> walls = columns(-reach, reach).stream()
            .filter(at -> inTank(at.getX(), at.getZ()) && !swept.contains(column(at)) && !skinBeside(at.getX(), at.getZ()).isEmpty()).toList();
        for (BlockPos at : apart(walls, LICHENS, GROWING_SPACING)) {
            List<Integer> rows = IntStream.range(floor + LICHEN_FROM, top).filter(y -> !room.beforeWindow(at.getX(), y, at.getZ())).boxed().toList();
            if (!rows.isEmpty()) {
                add(at.atY(rows.get(scatter.nextInt(rows.size()))), Part.LICHEN, Direction.NORTH, skinBeside(at.getX(), at.getZ()));
            }
        }
    }

    /** Spore blossoms under the tank's ceiling, over the creature's line, dropping their spores slowly down to it. */
    private void sporeBlossoms(Track track) {
        for (int i = 1; i <= SPORE_BLOSSOMS; i++) {
            Vec3 over = track.from().lerp(track.to(), i / (SPORE_BLOSSOMS + 1.0));
            add(new BlockPos((int) Math.floor(over.x), top, (int) Math.floor(over.z)), Part.SPORE_BLOSSOM, Direction.NORTH, Set.of());
        }
    }

    private static Part floorPart(BlockPos at, List<BlockPos> rocks, Set<Long> pool, List<BlockPos> beds) {
        if (rocks.contains(at)) {
            return Part.ROCK;
        }
        if (pool.contains(column(at))) {
            return Part.POOL;
        }
        return beds.contains(at) ? Part.PEBBLES : Part.MOSS;
    }

    /** The tank's columns the creature's outline or feet pass over, pacing its line or turning round. */
    private Set<Long> swept(Track track) {
        Set<Long> swept = new HashSet<>();
        double z = track.from().z;
        for (double x = track.from().x; x <= track.to().x; x += STEP) {
            sweep(swept, OUTLINE, x, z);
            sweep(swept, FEET, x, z);
        }
        if (track.turn() != null) {
            sweep(swept, TURNING, track.turn().x, track.turn().z);
        }
        return swept;
    }

    private static void sweep(Set<Long> swept, List<Vec3> outline, double x, double z) {
        for (Vec3 point : outline) {
            swept.add(column((int) Math.floor(x + point.x), (int) Math.floor(z + point.z)));
        }
    }

    /** Up to so many of the columns given, toward the tank's back first and apart from each other. */
    private static List<BlockPos> apart(List<BlockPos> candidates, int most) {
        return apart(candidates, most, SPACING);
    }

    /** Up to so many of the columns given, toward the tank's back first and so far apart from each other at least. */
    private static List<BlockPos> apart(List<BlockPos> candidates, int most, int spacing) {
        List<BlockPos> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingInt((BlockPos at) -> -at.getZ()).thenComparingInt(at -> Math.abs(at.getX())).thenComparingInt(BlockPos::getX));
        List<BlockPos> chosen = new ArrayList<>();
        for (BlockPos candidate : sorted) {
            boolean clear = chosen.stream().allMatch(other -> other.distManhattan(candidate) >= spacing);
            if (chosen.size() < most && clear) {
                chosen.add(candidate);
            }
        }
        return chosen;
    }

    /**
     * The pool: the stretch of the tank's floor its size nearest the tank's back, behind the middle of the creature's
     * line.
     */
    private Set<Long> pool(List<BlockPos> inner, Set<Long> taken, Track track) {
        Set<Long> open = new HashSet<>();
        for (BlockPos at : inner) {
            if (!taken.contains(column(at))) {
                open.add(column(at));
            }
        }
        int middle = (int) Math.floor((track.from().x + track.to().x) / 2);
        for (int z = reach; z >= -reach; z--) {
            Set<Long> pool = poolAt(middle - POOL_ACROSS / 2, z, open);
            if (pool != null) {
                return pool;
            }
        }
        return Set.of();
    }

    @Nullable
    private static Set<Long> poolAt(int fromX, int fromZ, Set<Long> open) {
        Set<Long> pool = new HashSet<>();
        for (int i = 0; i < POOL_ACROSS * POOL_DEEP; i++) {
            long at = column(fromX + i % POOL_ACROSS, fromZ + i / POOL_ACROSS);
            if (!open.contains(at)) {
                return null;
            }
            pool.add(at);
        }
        return pool;
    }

    /**
     * The lab counters: two runs curving round her skin either side of the way out, each unit facing in with what
     * stands on it, a stool before each microscope, and the floor before the others kept clear to stand on once all
     * stand.
     */
    private void counters(RandomSource scatter) {
        double out = wall - COUNTER_IN;
        List<List<BlockPos>> runs = new ArrayList<>();
        List<BlockPos> fronts = new ArrayList<>();
        for (double[] ends : COUNTER_RUNS) {
            List<BlockPos> run = RocketRoom.run(floor + 1, out, ends[0], ends[1]).stream()
                .filter(at -> room.free(at.getX(), at.getY(), at.getZ())).toList();
            runs.add(run);
            run.forEach(at -> fronts.add(at.relative(counterFacing(at))));
        }
        for (int side = 0; side < runs.size(); side++) {
            List<BlockPos> run = runs.get(side);
            for (int unit = 0; unit < run.size(); unit++) {
                counter(run.get(unit), ON_COUNTERS[side][unit % ON_COUNTERS[side].length], scatter, fronts);
            }
        }
        fronts.forEach(this::keepClear);
    }

    /** The way a counter's unit faces: in toward her axis. */
    private static Direction counterFacing(BlockPos at) {
        return RocketRoom.inward(RocketRoom.roundOf(at.getX(), at.getZ()));
    }

    /**
     * A counter's unit facing in toward her axis, what stands on it unless it was left bare, and before it a stool if
     * it has a microscope, clear of the floor before the other units.
     */
    private void counter(BlockPos at, Part on, RandomSource scatter, List<BlockPos> fronts) {
        Direction facing = counterFacing(at);
        add(at, Part.BENCH, facing, Set.of());
        boolean kept = on == Part.MICROSCOPE || on == Part.CENTRIFUGE;
        boolean bare = !kept && scatter.nextFloat() < BARE_CHANCE;
        if (!bare && room.free(at.getX(), at.getY() + 1, at.getZ())) {
            add(at.above(), on, facing, Set.of());
        }
        if (on == Part.MICROSCOPE) {
            stool(at.relative(facing), facing, scatter, fronts);
        }
    }

    /**
     * A microscope's stool before its counter: tucked in, or as it was left, pushed back or aside, where that is free
     * and not the floor before another unit.
     */
    private void stool(BlockPos before, Direction facing, RandomSource scatter, List<BlockPos> fronts) {
        float pushed = scatter.nextFloat();
        BlockPos left = before;
        if (pushed < PUSHED_BACK) {
            left = before.relative(facing);
        } else if (pushed < PUSHED_BACK + PUSHED_ASIDE) {
            left = before.relative(scatter.nextBoolean() ? facing.getClockWise() : facing.getCounterClockWise());
        }
        if (fronts.contains(left) && !left.equals(before) || !place(left, Part.STOOL, Direction.NORTH, Set.of())) {
            place(before, Part.STOOL, Direction.NORTH, Set.of());
        }
    }

    /** Stools either side of the floor before the hatch, to sit and watch the creature from. */
    private void watchers(BlockPos hatch) {
        int x = hatch.getX() + 1;
        for (int side = -1; side <= 1; side += 2) {
            int z = hatch.getZ() + side;
            if (room.free(x, floor + 1, z)) {
                add(new BlockPos(x, floor + 1, z), Part.STOOL, Direction.NORTH, Set.of());
            }
        }
    }

    /** A part at a block, where nothing stands yet, of the rocket or of the lab. */
    private void add(BlockPos at, Part part, Direction facing, Set<Direction> sides) {
        if (room.open(at.getX(), at.getY(), at.getZ()) && laid.add(at)) {
            parts.add(new Placed(at, part, facing, Set.copyOf(sides)));
        }
    }

    /** Whether furniture may go at a block: free by the room's rule, and nothing of the lab laid or kept clear there. */
    boolean placeable(BlockPos at) {
        return room.free(at.getX(), at.getY(), at.getZ()) && !laid.contains(at);
    }

    /** A part at a block where furniture may go, its ways given (see {@link Placed}), and whether it went. */
    boolean place(BlockPos at, Part part, Direction facing, Set<Direction> ways) {
        if (!placeable(at)) {
            return false;
        }
        add(at, part, facing, ways);
        return true;
    }

    /** A part two blocks tall, its upper half over it, where both may go, and whether it went. */
    boolean placeTall(BlockPos at, Part part, Direction facing) {
        if (!placeable(at) || !placeable(at.above())) {
            return false;
        }
        add(at, part, facing, Set.of());
        add(at.above(), Part.TOP, facing, Set.of());
        return true;
    }

    /** Keeps a block clear of anything laid after, as the floor before a counter or a hatch. */
    void keepClear(BlockPos at) {
        laid.add(at);
    }
}
