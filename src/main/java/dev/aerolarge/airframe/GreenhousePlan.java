package dev.aerolarge.airframe;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nullable;

/**
 * The rocket's greenhouse worked out on its floor plan before a block is chosen (see {@link RocketGreenhouse}), so it
 * can be drawn and checked without the game's blocks. Paths run out from the walkway round the shaft to the three
 * windows the way out to her front leaves, cutting the room into four quarters, and in each quarter the raised beds lie
 * in rows a path apart, a quarter turn from the quarter before: one bed after another along each row, each as long as
 * a throw of the dice has it and a path's width from the next, a row one block wide or two, so that no two rows end
 * alike. Each bed has its planting dealt from a shuffled deck of vegetables, berries, pumpkins, melons and flowers, and
 * half of a vegetable bed is now and then another vegetable; a sprinkler stands in its middle wherever no sprinkler
 * already waters all of it. What is left over off the paths takes the rest: the irrigation valve nearest the lift, a
 * tree or two among the beds, a potting corner, a bean bag and herb pots; and moss grows over the paths in patches.
 *
 * <p>Nothing here is one of the game's blocks: spots of the floor plan are blocks from her axis, x to her right and z
 * behind her, as the layout has them, and the plan's dice are the room's own random sequence, so every build lays the
 * greenhouse out the same.
 */
final class GreenhousePlan {
    /** How wide every path between the beds is, in blocks. */
    private static final int PATH = 1;
    /** How long a bed is, at least and at most, in blocks. */
    private static final int SHORTEST = 2;
    private static final int LONGEST = 6;
    /** One row in how many is a single block wide, the rest two. */
    private static final int NARROW_ONE_IN = 4;
    /** One vegetable bed in how many has another vegetable in its second half. */
    private static final int MIXED_ONE_IN = 2;
    /**
     * How far from a sprinkler the soil stays moist, in blocks each way: as far as farmland feels water, so no bed is
     * dry.
     */
    static final int WATERED = 4;
    /**
     * Where the irrigation valve stands, kept from the beds: on her left of the way out from the lift's door, at the
     * walkway's corner, so whoever steps out of the lift finds it at their left hand; and where the lift's door lets
     * out onto the floor, which it stands as near as it can where that spot is not free.
     */
    private static final Cell VALVE_AT = new Cell(-2, -RocketLayout.SHAFT - 2);
    private static final Cell LIFT_EXIT = new Cell(0, -RocketLayout.SHAFT - 1);
    /** How far out from her axis the room's reach must run for each tree past the first, in blocks. */
    private static final int TREE_EVERY = 7;
    /** How far apart two trees stand at least, in blocks, so their crowns never meet. */
    private static final int TREES_APART = 6;
    /** Among how many of a quarter's spots furthest out a tree takes one by the dice. */
    private static final int TREE_CHOICES = 3;
    /** One spot left over in how many holds a herb pot. */
    private static final int POT_ONE_IN = 2;
    /**
     * The moss on the paths: one patch for how many blocks of floor it could grow on, and how far out from its middle a
     * patch reaches, at least and at most, in blocks; one block in how many within that is left bare.
     */
    private static final int MOSS_EVERY = 14;
    private static final int MOSS_FROM = 1;
    private static final int MOSS_TO = 2;
    private static final int BARE_ONE_IN = 4;

    /** The sides of a spot, as steps across the floor plan: north (her front) first, then on round clockwise. */
    static final int[][] SIDES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    /** The ways a spot's things face, by their place in {@link #SIDES}. */
    static final int NORTH = 0;
    static final int EAST = 1;
    static final int SOUTH = 2;
    static final int WEST = 3;

    /** What grows in a bed: four vegetables, berry bushes, pumpkins or melons on their vines, or flowers. */
    enum Planting {
        CARROTS, POTATOES, BEETROOTS, WHEAT, BERRIES, PUMPKINS, MELONS, FLOWERS;

        /** Whether it is a vegetable, half of whose bed may be another. */
        boolean vegetable() {
            return ordinal() <= WHEAT.ordinal();
        }
    }

    /** The vegetables, for the second half of a mixed bed. */
    private static final Planting[] VEGETABLES = {Planting.CARROTS, Planting.POTATOES, Planting.BEETROOTS, Planting.WHEAT};
    /**
     * The deck the beds' plantings are dealt from, shuffled once a build and dealt round again when it runs out: the
     * vegetables most, so even a small greenhouse grows food, then berries, pumpkins, melons and flowers once each.
     */
    private static final Planting[] DECK = {
        Planting.CARROTS, Planting.POTATOES, Planting.PUMPKINS, Planting.BEETROOTS, Planting.WHEAT, Planting.BERRIES, Planting.CARROTS,
        Planting.MELONS, Planting.POTATOES, Planting.FLOWERS, Planting.BEETROOTS, Planting.WHEAT};

    /** What stands on a spot of the floor plan off the beds. */
    enum Thing {
        VALVE, CHERRY_TREE, AZALEA_TREE, COMPOSTER, WATER_BUTT, BARREL, BENCH, SOIL_HEAP, BEAN_BAG, HERB_POT
    }

    /** The trees, in the order they are planted: a cherry first. */
    private static final Thing[] TREES = {Thing.CHERRY_TREE, Thing.AZALEA_TREE, Thing.CHERRY_TREE};
    /** The potting corner, gathered round the first of it: a composter, a water butt, a barrel, a bench and soil. */
    private static final Thing[] POTTING = {Thing.COMPOSTER, Thing.WATER_BUTT, Thing.BARREL, Thing.BENCH, Thing.BENCH, Thing.SOIL_HEAP};

    /** A spot of the floor plan, from her axis: x to her right, z behind her. */
    record Cell(int x, int z) {
        Cell step(int side) {
            return new Cell(x + SIDES[side][0], z + SIDES[side][1]);
        }

        /** How far it is from another, counting the further of the two ways across the floor, in blocks. */
        int reach(Cell other) {
            return Math.max(Math.abs(x - other.x), Math.abs(z - other.z));
        }
    }

    /**
     * A raised bed: its corners, the lowest x and z first, whether it lies along x, what grows in its first half and
     * its second half along it, and where its sprinkler stands in it, if it has one of its own.
     */
    record Bed(Cell from, Cell to, boolean alongX, Planting first, Planting second, @Nullable Cell sprinkler) {
        boolean contains(Cell cell) {
            return cell.x() >= from.x() && cell.x() <= to.x() && cell.z() >= from.z() && cell.z() <= to.z();
        }

        /** How long it is along the way it lies, in blocks. */
        int length() {
            return alongX ? to.x() - from.x() + 1 : to.z() - from.z() + 1;
        }

        /** How wide it is across the way it lies, in blocks. */
        int width() {
            return alongX ? to.z() - from.z() + 1 : to.x() - from.x() + 1;
        }

        /** How far along it a spot of it is, from its first end, in blocks. */
        int along(Cell cell) {
            return alongX ? cell.x() - from.x() : cell.z() - from.z();
        }

        /** How far across it a spot of it is, from its first side, in blocks. */
        int across(Cell cell) {
            return alongX ? cell.z() - from.z() : cell.x() - from.x();
        }

        /** The spot so far along it and so far across it. */
        Cell at(int along, int across) {
            return alongX ? new Cell(from.x() + along, from.z() + across) : new Cell(from.x() + across, from.z() + along);
        }

        /** What grows at a spot of it: its first planting in its first half, its second beyond. */
        Planting planting(Cell cell) {
            return along(cell) < (length() + 1) / 2 ? first : second;
        }

        /** Its spots, row by row along it. */
        List<Cell> cells() {
            List<Cell> cells = new ArrayList<>();
            for (int i = 0; i < length() * width(); i++) {
                cells.add(at(i % length(), i / length()));
            }
            return cells;
        }

        Bed withSprinkler(Cell at) {
            return new Bed(from, to, alongX, first, second, at);
        }
    }

    /** A thing at a spot, facing one of the four ways (see {@link #SIDES}). */
    record Spot(Cell at, Thing thing, int facing) {
    }

    /** The greenhouse: its beds, what stands off them, and where moss grows on the floor. */
    record Plan(List<Bed> beds, List<Spot> spots, List<Cell> moss) {
    }

    /** What the greenhouse is laid in, its floor's row as every room has it. */
    interface Room {
        /** Whether something may stand on the floor at a spot of the floor plan, as in every room. */
        boolean free(int x, int z);

        /** Whether a bed may stand there: free, and the block over it too, for what grows in it. */
        boolean plantable(int x, int z);
    }

    /**
     * A quarter of the room between the paths out to its windows: which way x and z run from her axis in it, whether
     * its beds lie along x, and how far from her axis its rows begin across them and its beds along them, in blocks:
     * past the way out at her front, and past the path out to a window elsewhere.
     */
    private record Quarter(int signX, int signZ, boolean alongX, int acrossFrom, int alongFrom) {
        /** The spot so far across its rows and so far along them, from her axis. */
        Cell cell(int across, int along) {
            return alongX ? new Cell(signX * along, signZ * across) : new Cell(signX * across, signZ * along);
        }

        boolean contains(Cell cell) {
            int across = alongX ? signZ * cell.z() : signX * cell.x();
            int along = alongX ? signX * cell.x() : signZ * cell.z();
            return across >= acrossFrom && along >= alongFrom;
        }
    }

    /** Her front right, back right, back left and front left, the beds turned a quarter from each to the next. */
    private static final Quarter[] QUARTERS = {
        new Quarter(1, -1, false, 2, 1), new Quarter(1, 1, true, 1, 1), new Quarter(-1, 1, false, 1, 1), new Quarter(-1, -1, true, 1, 2)};

    private final Room room;
    /** How far out from her axis the room reaches, in blocks, square round it. */
    private final int reach;
    private final IntUnaryOperator dice;
    /** The plantings shuffled for this build, and how many have been dealt from them. */
    private final List<Planting> deck = new ArrayList<>(List.of(DECK));
    private int dealt;
    private final List<Bed> beds = new ArrayList<>();
    /** The paths between the beds: along each row's far side, and across a row between two of its beds. */
    private final Set<Cell> paths = new HashSet<>();
    /**
     * The spots left over, free but neither bed nor path, each with the quarter it is in, and the same in the order
     * they were found, so what takes them is the same every build; a spot leaves them as something is put on it.
     */
    private final Map<Cell, Quarter> spare = new HashMap<>();
    private final List<Cell> spareInOrder = new ArrayList<>();
    /** What stands on the spots left over, by spot, and in the order it was put there. */
    private final Map<Cell, Spot> spots = new HashMap<>();
    private final List<Spot> spotsInOrder = new ArrayList<>();

    private GreenhousePlan(Room room, int reach, IntUnaryOperator dice) {
        this.room = room;
        this.reach = reach;
        this.dice = dice;
    }

    /**
     * The greenhouse in a room whose skin's inside is so far out from her axis, laid with the dice given: a number
     * below the one asked, drawn from the room's own random sequence.
     */
    static Plan of(Room room, double wall, IntUnaryOperator dice) {
        GreenhousePlan plan = new GreenhousePlan(room, (int) Math.ceil(wall), dice);
        plan.shuffle();
        for (Quarter quarter : QUARTERS) {
            plan.lay(quarter);
        }
        plan.water();
        plan.leftOver();
        plan.valve();
        plan.trees();
        plan.pottingCorner();
        plan.beanBag();
        plan.herbPots();
        return new Plan(List.copyOf(plan.beds), List.copyOf(plan.spotsInOrder), plan.moss());
    }

    private int roll(int below) {
        return dice.applyAsInt(below);
    }

    private void shuffle() {
        for (int i = deck.size() - 1; i > 0; i--) {
            deck.set(i, deck.set(roll(i + 1), deck.get(i)));
        }
    }

    /** The next planting off the deck, round again from its top when it runs out. */
    private Planting deal() {
        return deck.get(dealt++ % deck.size());
    }

    /** A quarter's rows of beds, out from her axis, a path after each. */
    private void lay(Quarter quarter) {
        int across = quarter.acrossFrom();
        while (across <= reach) {
            int width = roll(NARROW_ONE_IN) == 0 ? 1 : 2;
            row(quarter, across, width);
            pathAlong(quarter, across + width);
            across += width + PATH;
        }
    }

    /** One row of a quarter, so wide: a bed after another wherever the floor under the whole row's width is free. */
    private void row(Quarter quarter, int across, int width) {
        int along = quarter.alongFrom();
        while (along <= reach) {
            int length = run(quarter, across, width, along);
            cut(quarter, across, width, along, length);
            along += length + 1;
        }
    }

    /** How many blocks along a row from one a bed could stand on together, across the row's whole width. */
    private int run(Quarter quarter, int across, int width, int along) {
        int length = 0;
        while (along + length <= reach && plantable(quarter, across, width, along + length)) {
            length++;
        }
        return length;
    }

    private boolean plantable(Quarter quarter, int across, int width, int along) {
        for (int over = 0; over < width; over++) {
            Cell cell = quarter.cell(across + over, along);
            if (cell.equals(VALVE_AT) || !room.plantable(cell.x(), cell.z())) {
                return false;
            }
        }
        return true;
    }

    /** A run of free floor in a row cut into beds as long as the dice have them, a path's width between each two. */
    private void cut(Quarter quarter, int across, int width, int from, int length) {
        int at = from;
        int end = from + length;
        while (end - at >= SHORTEST) {
            int bed = Math.min(end - at, SHORTEST + roll(LONGEST - SHORTEST + 1));
            beds.add(bed(quarter, across, width, at, bed));
            at += bed;
            if (at < end) {
                pathAcross(quarter, across, width, at);
            }
            at += PATH;
        }
    }

    /** A bed in a row, its plantings dealt. */
    private Bed bed(Quarter quarter, int across, int width, int along, int length) {
        Cell one = quarter.cell(across, along);
        Cell other = quarter.cell(across + width - 1, along + length - 1);
        Cell from = new Cell(Math.min(one.x(), other.x()), Math.min(one.z(), other.z()));
        Cell to = new Cell(Math.max(one.x(), other.x()), Math.max(one.z(), other.z()));
        Planting first = deal();
        boolean mixed = first.vegetable() && roll(MIXED_ONE_IN) == 0;
        Planting second = mixed ? VEGETABLES[roll(VEGETABLES.length)] : first;
        return new Bed(from, to, quarter.alongX(), first, second, null);
    }

    /** The path along a row's far side, where the floor is free. */
    private void pathAlong(Quarter quarter, int across) {
        for (int along = quarter.alongFrom(); along <= reach; along++) {
            path(quarter.cell(across, along));
        }
    }

    /** The path across a row between two of its beds. */
    private void pathAcross(Quarter quarter, int across, int width, int along) {
        for (int over = 0; over < width; over++) {
            path(quarter.cell(across + over, along));
        }
    }

    private void path(Cell cell) {
        if (room.free(cell.x(), cell.z())) {
            paths.add(cell);
        }
    }

    /**
     * A sprinkler in the middle of each bed that the sprinklers already set do not water all of, the biggest beds
     * first, so a small bed beside a big one is watered by the big one's.
     */
    private void water() {
        List<Integer> bySize = new ArrayList<>();
        for (int i = 0; i < beds.size(); i++) {
            bySize.add(i);
        }
        bySize.sort(Comparator.comparingInt((Integer i) -> -beds.get(i).cells().size()).thenComparingInt(i -> i));
        List<Cell> sprinklers = new ArrayList<>();
        for (int i : bySize) {
            Bed bed = beds.get(i);
            if (!watered(bed, sprinklers)) {
                Cell middle = bed.at(bed.length() / 2, roll(bed.width()));
                sprinklers.add(middle);
                beds.set(i, bed.withSprinkler(middle));
            }
        }
    }

    /** Whether every block of a bed is within a sprinkler's reach. */
    private static boolean watered(Bed bed, List<Cell> sprinklers) {
        for (Cell cell : bed.cells()) {
            if (sprinklers.stream().noneMatch(sprinkler -> sprinkler.reach(cell) <= WATERED)) {
                return false;
            }
        }
        return true;
    }

    /** The spots in the quarters that are free but neither bed nor path, quarter by quarter, row by row. */
    private void leftOver() {
        for (Quarter quarter : QUARTERS) {
            for (int i = 0; i < reach * reach; i++) {
                spareAt(quarter, quarter.cell(quarter.acrossFrom() + i / reach, quarter.alongFrom() + i % reach));
            }
        }
    }

    private void spareAt(Quarter quarter, Cell cell) {
        if (room.free(cell.x(), cell.z()) && !paths.contains(cell) && bedAt(cell) == null && !spare.containsKey(cell)) {
            spare.put(cell, quarter);
            spareInOrder.add(cell);
        }
    }

    @Nullable
    private Bed bedAt(Cell cell) {
        for (Bed bed : beds) {
            if (bed.contains(cell)) {
                return bed;
            }
        }
        return null;
    }

    /** Whether a spot can be stood on: a path, or kept clear, as the walkway and the windows' fronts are. */
    private boolean walkable(Cell cell) {
        boolean shaft = Math.abs(cell.x()) <= RocketLayout.SHAFT && Math.abs(cell.z()) <= RocketLayout.SHAFT;
        if (shaft || bedAt(cell) != null || spots.containsKey(cell) || spare.containsKey(cell)) {
            return false;
        }
        return paths.contains(cell) || !room.free(cell.x(), cell.z()) || inNoQuarter(cell);
    }

    private static boolean inNoQuarter(Cell cell) {
        for (Quarter quarter : QUARTERS) {
            if (quarter.contains(cell)) {
                return false;
            }
        }
        return true;
    }

    /** The way from a spot to a spot beside it that can be stood on; none, less than nothing. */
    private int toWalkable(Cell cell) {
        for (int side = 0; side < SIDES.length; side++) {
            if (walkable(cell.step(side))) {
                return side;
            }
        }
        return -1;
    }

    private void place(Cell cell, Thing thing, int facing) {
        Spot spot = new Spot(cell, thing, facing);
        spots.put(cell, spot);
        spotsInOrder.add(spot);
        spare.remove(cell);
    }

    /** The spots still left over, in the order they were found. */
    private List<Cell> stillSpare() {
        return spareInOrder.stream().filter(spare::containsKey).toList();
    }

    /**
     * The irrigation valve, on its own spot by the way out, or else on the spot left over nearest the lift's door,
     * facing whoever stands beside it.
     */
    private void valve() {
        if (spare.containsKey(VALVE_AT) && toWalkable(VALVE_AT) >= 0) {
            place(VALVE_AT, Thing.VALVE, toWalkable(VALVE_AT));
            return;
        }
        Cell nearest = null;
        for (Cell cell : stillSpare()) {
            boolean nearer = nearest == null || distance(cell, LIFT_EXIT) < distance(nearest, LIFT_EXIT);
            if (nearer && toWalkable(cell) >= 0) {
                nearest = cell;
            }
        }
        if (nearest != null) {
            place(nearest, Thing.VALVE, toWalkable(nearest));
        }
    }

    private static double distance(Cell one, Cell other) {
        return Math.hypot(one.x() - other.x(), one.z() - other.z());
    }

    /**
     * The trees, one to a quarter in quarters the dice choose, as many as the room's size allows: each on one of the
     * spots left over furthest out in its quarter, and well apart from the others.
     */
    private void trees() {
        List<Quarter> order = new ArrayList<>(List.of(QUARTERS));
        for (int i = order.size() - 1; i > 0; i--) {
            order.set(i, order.set(roll(i + 1), order.get(i)));
        }
        int count = Math.min(TREES.length, 1 + reach / TREE_EVERY);
        int planted = 0;
        for (int i = 0; i < order.size() && planted < count; i++) {
            Cell at = treeSpot(order.get(i));
            if (at != null) {
                place(at, TREES[planted++], NORTH);
            }
        }
    }

    /** Where a tree stands in a quarter: among the spots left over furthest out, apart from the other trees. */
    @Nullable
    private Cell treeSpot(Quarter quarter) {
        List<Cell> choices = stillSpare().stream()
            .filter(cell -> spare.get(cell) == quarter && apartFromTrees(cell))
            .sorted(Comparator.comparingDouble((Cell cell) -> -Math.hypot(cell.x(), cell.z())))
            .limit(TREE_CHOICES)
            .toList();
        return choices.isEmpty() ? null : choices.get(roll(choices.size()));
    }

    private boolean apartFromTrees(Cell cell) {
        return spotsInOrder.stream().noneMatch(spot -> isTree(spot.thing()) && spot.at().reach(cell) < TREES_APART);
    }

    private static boolean isTree(Thing thing) {
        return thing == Thing.CHERRY_TREE || thing == Thing.AZALEA_TREE;
    }

    /**
     * The potting corner, where the most spots left over lie together: gathered from one of them out over those beside
     * it, as many of its things as there are spots, the corner nearest the walkway round the shaft of those as big.
     */
    private void pottingCorner() {
        List<Cell> corner = List.of();
        for (Cell cell : stillSpare()) {
            List<Cell> gathered = gathered(cell, POTTING.length);
            boolean bigger = gathered.size() > corner.size();
            boolean asBigAndNearer = gathered.size() == corner.size() && !corner.isEmpty() && ring(cell) < ring(corner.get(0));
            if (bigger || asBigAndNearer) {
                corner = gathered;
            }
        }
        for (int i = 0; i < corner.size(); i++) {
            place(corner.get(i), POTTING[i], Math.max(0, toWalkable(corner.get(i))));
        }
    }

    /** How many blocks out from her axis a spot is, square round the shaft as the walkway is. */
    private static int ring(Cell cell) {
        return Math.max(Math.abs(cell.x()), Math.abs(cell.z()));
    }

    /** Spots left over gathered from one out over those beside them, nearest first, as many as asked at most. */
    private List<Cell> gathered(Cell first, int most) {
        Set<Cell> found = new LinkedHashSet<>();
        Deque<Cell> next = new ArrayDeque<>(List.of(first));
        while (!next.isEmpty() && found.size() < most) {
            Cell cell = next.poll();
            if (found.add(cell)) {
                next.addAll(beside(cell));
            }
        }
        return List.copyOf(found);
    }

    /** The spots left over beside one. */
    private List<Cell> beside(Cell cell) {
        List<Cell> beside = new ArrayList<>();
        for (int side = 0; side < SIDES.length; side++) {
            if (spare.containsKey(cell.step(side))) {
                beside.add(cell.step(side));
            }
        }
        return beside;
    }

    /** A bean bag on a spot left over beside somewhere to stand, looking out from her axis through the plants. */
    private void beanBag() {
        List<Cell> choices = stillSpare().stream().filter(cell -> toWalkable(cell) >= 0).toList();
        if (!choices.isEmpty()) {
            Cell at = choices.get(roll(choices.size()));
            place(at, Thing.BEAN_BAG, outward(at));
        }
    }

    /** The way of the four nearest to looking out from her axis at a spot. */
    private static int outward(Cell cell) {
        if (Math.abs(cell.x()) > Math.abs(cell.z())) {
            return cell.x() > 0 ? EAST : WEST;
        }
        return cell.z() > 0 ? SOUTH : NORTH;
    }

    /** A herb pot on some of the spots still left over. */
    private void herbPots() {
        for (Cell cell : stillSpare()) {
            if (roll(POT_ONE_IN) == 0) {
                place(cell, Thing.HERB_POT, NORTH);
            }
        }
    }

    /**
     * Where moss grows on the floor: in patches over the paths and the bare spots, a patch round each tree, the rest
     * where the dice have them, a block in a few within each left bare.
     */
    private List<Cell> moss() {
        List<Cell> ground = new ArrayList<>();
        for (int i = 0; i < (2 * reach + 1) * (2 * reach + 1); i++) {
            Cell cell = new Cell(i % (2 * reach + 1) - reach, i / (2 * reach + 1) - reach);
            if (room.free(cell.x(), cell.z()) && bedAt(cell) == null && !spots.containsKey(cell)) {
                ground.add(cell);
            }
        }
        List<Cell> middles = new ArrayList<>();
        spotsInOrder.stream().filter(spot -> isTree(spot.thing())).forEach(spot -> middles.add(spot.at()));
        int patches = ground.isEmpty() ? 0 : Math.max(1, ground.size() / MOSS_EVERY);
        for (int i = 0; i < patches; i++) {
            middles.add(ground.get(roll(ground.size())));
        }
        Set<Cell> moss = new LinkedHashSet<>();
        for (Cell middle : middles) {
            patch(ground, middle, MOSS_FROM + roll(MOSS_TO - MOSS_FROM + 1), moss);
        }
        return List.copyOf(moss);
    }

    private void patch(List<Cell> ground, Cell middle, int radius, Set<Cell> moss) {
        for (Cell cell : ground) {
            if (distance(cell, middle) <= radius + 0.5 && roll(BARE_ONE_IN) != 0) {
                moss.add(cell);
            }
        }
    }
}
