package dev.skyliners.airframe;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.BeanBagBlock;
import dev.skyliners.block.Brightness;
import dev.skyliners.block.CabinCarpetBlock;
import dev.skyliners.block.CabinRailBlock;
import dev.skyliners.block.CabinStairsBlock;
import dev.skyliners.block.CoffeeMachineBlock;
import dev.skyliners.block.DisplayShelfBlock;
import dev.skyliners.block.GalleyOvenBlock;
import dev.skyliners.block.GalleyUnitBlock;
import dev.skyliners.block.PlanterBlock;
import dev.skyliners.block.SnackMachineBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * The observation deck, her highest story under the cockpit and her chillout lounge: in place of its oval windows, a
 * band of the cockpit's smoked glass all the way round her at eye height, so the whole horizon is seen from it; and
 * overhead, as the airliner's lounge has, the airliner's starlight panels, dimmed so the stars outside outshine them,
 * all over the ceiling and up the wall from the band to it, so the room over the band is a starry dome.
 *
 * <p>Over her back third, from the back of the shaft's walkway out to her skin, a mezzanine holds her cafeteria, a
 * glass rail along its edge and a flight of carpeted stairs climbing her right-hand wall to it from the deck's floor:
 * round her back wall a serving counter of the galley's units with the coffee machine on it, the snack machine (see
 * {@link dev.skyliners.block.SnackMachineBlock}) on the way from the stairs, a lit drinks cabinet and plants, and
 * between them tables with stools or armchairs. Below, the lounge is set out in groups to talk in, scattered unevenly
 * round the room: armchairs and bean bags round low tables on round rugs, bean bags by the glass to watch the land fall
 * away or the stars come out, plants, and a bookcase under the top of the stairs; cabin lights set into the
 * mezzanine's underside light the groups under it and its edge. Everything is set about by the room's own random
 * sequence (see {@link #scatter}), so each build of her comes out the same.
 */
final class RocketObservationDeck extends RocketRoom {
    /** Her observation deck's story, from the entry story up. */
    static final int STORY = 8;
    /**
     * How many rows its room is at most from its floor to its ceiling: on a bigger rocket, whose stories stay a
     * player's size, its story would otherwise rise up into her nose as a tower of bare skin.
     */
    private static final int TALLEST = 16;
    /** The band of glass's lowest and highest rows over the floor. */
    private static final int BAND_FROM = 1;
    private static final int BAND_TO = 5;
    /** How many rows over the deck's floor the mezzanine's floor is: over the band of glass, under the starlight. */
    private static final int MEZZANINE_UP = 7;
    /**
     * How far behind her axis the mezzanine's front edge is, in blocks: along the back row of the shaft's walkway, so
     * the mezzanine meets the shaft's glass and the walkway runs on under it.
     */
    private static final int MEZZANINE_FRONT = RocketLayout.SHAFT + 1;
    /** How many clear rows the mezzanine needs over it under the starlight ceiling, at least; a lower room has none. */
    private static final int CLEAR_OVER = 3;
    /**
     * How many rows over the mezzanine's floor are the cafeteria's own, reaching out to her skin, the starry wall
     * running on over them.
     */
    private static final int CAFE_ROWS = 3;
    /** How many rows are kept clear over a tread of the stairs, over their landing at the top and before their foot. */
    private static final int HEADROOM = 3;
    /** The lowest tread with a rail along its open sides: beside the first, the drop is only a step. */
    private static final int RAILED_FROM = 2;
    /** How far to her right of her axis the lane kept open to the stairs' foot begins, in blocks: by the way out. */
    private static final int LANE_FROM = 2;
    /** How many rows tall the bookcase under the top of the stairs stands. */
    private static final int BOOKCASE_ROWS = 2;
    /**
     * Where round her back wall the cafeteria's things stand, from her front clockwise, in degrees: the snack machine
     * from the first, nearest the counter, the counter from the second to the third, the drinks cabinet from there to
     * the fourth, nearest the counter.
     */
    private static final double SNACKS_FROM = 115;
    private static final double COUNTER_FROM = 150;
    private static final double COUNTER_TO = 210;
    private static final double CABINET_TO = 245;
    /** How many of the cafeteria's free blocks there are for each try at setting a table at one of them. */
    private static final int SPOTS_A_TABLE = 4;
    /** The share of the cafeteria's tables that have bar stools rather than armchairs. */
    private static final float STOOLS = 0.5F;
    /** The share of the free places along the cafeteria's wall that have a plant. */
    private static final float CAFE_PLANTS = 0.5F;
    /**
     * How far out from her axis the first ring of the lounge's groups is, how far apart the rings are and how far
     * apart round a ring each group is, in blocks.
     */
    private static final double GROUPS_OUT = 5.75;
    private static final double GROUPS_APART = 5;
    private static final double GROUP_ROUND = 8;
    /** How far a group is set off its place at most: out, in blocks, and round, as a share of the gap to the next. */
    private static final double GROUP_OUT_JITTER = 0.75;
    private static final double GROUP_ROUND_JITTER = 0.4;
    /** The share of a group's seats that are armchairs rather than bean bags, and of its groups with a plant. */
    private static final float ARMCHAIRS = 0.6F;
    private static final float GROUP_PLANTS = 0.5F;
    /** How many seats a group has at least, round its table's four sides. */
    private static final int GROUP_SEATS = 3;
    /** How far from its spot a group's table is looked for, in blocks. */
    private static final int GROUP_SEARCH = 2;
    /** The offsets round a spot of the floor where a group's table is looked for, nearest first. */
    private static final List<BlockPos> NEARBY = BlockPos.betweenClosedStream(-GROUP_SEARCH, 0, -GROUP_SEARCH, GROUP_SEARCH, 0, GROUP_SEARCH)
        .filter(at -> Math.hypot(at.getX(), at.getZ()) <= GROUP_SEARCH)
        .map(BlockPos::immutable)
        .sorted(Comparator.comparingDouble(at -> Math.hypot(at.getX(), at.getZ())))
        .toList();
    /** How far out from a group's table its round rug reaches, in blocks. */
    private static final double RUG_RADIUS = 2.25;
    /**
     * The bean bags by the glass: how far in from the skin's inside each pair is, how far round apart the pairs are and
     * how far apart a pair's two bean bags are, in blocks.
     */
    private static final double WINDOW_IN = 2.25;
    private static final double WINDOW_ROUND = 9;
    private static final double PAIR_APART = 2;
    /** How far round apart the plants at the lounge's edge are, on average, in blocks, and how far in they stand. */
    private static final double PLANT_ROUND = 12;
    private static final double PLANT_IN = 2;
    /** How far apart the cabin lights set into the mezzanine's front edge are, in blocks. */
    private static final int EDGE_LIGHT_APART = 3;
    /** The colours the lounge's armchairs and bean bags, the cafeteria's armchairs and the rugs are in. */
    private static final List<DyeColor> ARMCHAIR_COLOURS = List.of(DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.CYAN, DyeColor.MAGENTA);
    private static final List<DyeColor> BEAN_BAG_COLOURS = List.of(DyeColor.LIGHT_BLUE, DyeColor.PINK, DyeColor.YELLOW, DyeColor.LIME);
    private static final List<DyeColor> CAFE_COLOURS = List.of(DyeColor.RED, DyeColor.WHITE, DyeColor.BLACK);
    private static final List<DyeColor> RUG_COLOURS = List.of(DyeColor.PURPLE, DyeColor.BLUE, DyeColor.CYAN, DyeColor.BROWN, DyeColor.GRAY);
    /** What grows in the planters, one of them at random in each. */
    private static final List<PlanterBlock.Plant> PLANTS = List.of(PlanterBlock.Plant.values());
    /** The flowers potted on the cafeteria's counter, one of them. */
    private static final List<Block> POTTED = List.of(Blocks.POTTED_ORANGE_TULIP, Blocks.POTTED_BLUE_ORCHID, Blocks.POTTED_ALLIUM);

    /** The room's random sequence, taken once as it is built. */
    private final RandomSource random = scatter();
    /** The blocks kept open: the stairs' headroom, landing and way to their foot, and the fronts of the counters. */
    private final Set<BlockPos> kept = new HashSet<>();
    /** The mezzanine's floor, block by block, and where cabin lights go into it. */
    private final List<BlockPos> upperFloor = new ArrayList<>();
    private final List<BlockPos> lamps = new ArrayList<>();
    /** Whether the room has its mezzanine, and the column of its stairs' outer treads, against her skin. */
    private boolean hasMezzanine;
    private int outer;

    RocketObservationDeck(RocketLayout layout) {
        super(layout, STORY);
    }

    /** Its band of glass takes the windows' place. */
    @Override
    protected boolean windowed() {
        return false;
    }

    /**
     * Whether a story is the observation deck furnished as her room: its band of glass in place of its windows, its
     * starry dome over it.
     */
    static boolean furnished(RocketLayout layout, int story) {
        return story == STORY && layout.rooms();
    }

    /**
     * The row a story's ceiling is over its floor, given the row of the floor over it: that row, or for the observation
     * deck the lower ceiling of its own that it has, furnished as her room, where its story is taller than
     * {@link #TALLEST}. Her LED strips run round the room under it (see {@link #underCeiling}).
     */
    static int ceilingOf(RocketLayout layout, int story, int floor, int over) {
        int lid = floor + TALLEST;
        boolean lidded = furnished(layout, story) && lid < over;
        return lidded ? lid : over;
    }

    /**
     * The row a story's ring of LED strip under its ceiling runs in (see {@link RocketLedStrips}), given its ceiling's
     * row: the row under it; but on the observation deck furnished as her room, whose starlight ceiling fills that row
     * out to her skin, the row under the starlight, the room's highest, where the starry wall meets the starry ceiling.
     */
    static int underCeiling(RocketLayout layout, int story, int ceiling) {
        return furnished(layout, story) ? ceiling - 2 : ceiling - 1;
    }

    @Override
    public void build() {
        band();
        int ceiling = Math.min(top() + 1, floor() + TALLEST);
        if (ceiling <= top()) {
            lid(ceiling);
        }
        OptionalInt flight = ceiling - 1 - mezzanineFloor() > CLEAR_OVER ? flightColumn() : OptionalInt.empty();
        hasMezzanine = flight.isPresent();
        if (hasMezzanine) {
            outer = flight.getAsInt();
            stairs();
            mezzanine();
        }
        starlight(ceiling - 1);
        starryWall(ceiling - 1);
        if (hasMezzanine) {
            rails();
            cafeteria(mezzanineFloor() + 1);
            bookcase();
        }
        lounge(floor() + 1);
        lights();
    }

    /**
     * Whether a block is free for furniture: in the cafeteria's rows over the mezzanine, any open block inside her but
     * the row along its rail, which runs on behind the shaft; anywhere else, as in any room, and clear of the stairs;
     * never a block kept open.
     */
    @Override
    protected boolean free(int x, int y, int z) {
        if (kept.contains(new BlockPos(x, y, z))) {
            return false;
        }
        if (inCafe(x, y, z)) {
            return openAir(x, y, z) && z > MEZZANINE_FRONT;
        }
        return super.free(x, y, z) && !underStairs(x, y, z);
    }

    /** Whether a block is in the cafeteria's own rows over the mezzanine. */
    private boolean inCafe(int x, int y, int z) {
        int up = y - mezzanineFloor();
        return hasMezzanine && up >= 1 && up <= CAFE_ROWS && z >= MEZZANINE_FRONT;
    }

    /** Whether a block is in the stairs' columns, from the deck's floor to their top. */
    private boolean underStairs(int x, int y, int z) {
        boolean column = x == outer || x == outer - 1;
        return hasMezzanine && column && z >= treadZ(1) && z <= treadZ(MEZZANINE_UP) && y <= mezzanineFloor();
    }

    /** Whether a block is open air inside her. */
    private boolean openAir(int x, int y, int z) {
        return layout.isOpen(x, y, z) && layout.inFrom(x, y, z, 0);
    }

    /** The row of the mezzanine's floor. */
    private int mezzanineFloor() {
        return floor() + MEZZANINE_UP;
    }

    /**
     * How far behind her axis a tread of the stairs is, counted from their foot: the top one in the mezzanine's front
     * edge, level with its floor. So the floor before their foot is as far ahead of her axis as the landing at their
     * top is behind it, and her round skin, which leaves room for the landing beside her wall, leaves room before both
     * treads at their foot too.
     */
    private static int treadZ(int step) {
        return MEZZANINE_FRONT - (MEZZANINE_UP - step);
    }

    /** How far behind her axis the landing at the stairs' top is: on the mezzanine, just past their top tread. */
    private static int landingZ() {
        return treadZ(MEZZANINE_UP) + 1;
    }

    /**
     * The band of smoked glass: every block of her skin in its rows, cover or oval window, turned to glass cut to her
     * curve and one pane thick (see {@link RocketLayout#glaze}).
     */
    private void band() {
        int reach = layout.reach();
        BlockState glass = SkylinersRegistry.SMOKED_GLASS.get().defaultBlockState();
        for (BlockPos at : BlockPos.betweenClosed(-reach, floor() + BAND_FROM, -reach, reach, floor() + BAND_TO, reach)) {
            BlockState skin = layout.stateAt(at.getX(), at.getY(), at.getZ());
            if (skin.is(SkylinersRegistry.ZEPPELIN_COVER.get()) || skin.is(SkylinersRegistry.OVAL_WINDOW.get())) {
                layout.glaze(at.getX(), at.getY(), at.getZ(), glass);
            }
        }
    }

    /**
     * A ceiling over the room at a row, under the rest of her story, as a floor is laid: out to her skin round her lift
     * shaft, which runs on up through it to her cockpit.
     */
    private void lid(int y) {
        int reach = (int) Math.ceil(wall()) + 1;
        BlockState floor = SkylinersRegistry.CABIN_FLOOR.get().defaultBlockState();
        for (BlockPos at : BlockPos.betweenClosed(-reach, y, -reach, reach, y, reach)) {
            boolean inShaft = Math.abs(at.getX()) < RocketLayout.SHAFT && Math.abs(at.getZ()) < RocketLayout.SHAFT;
            if (!inShaft && layout.isOpen(at.getX(), y, at.getZ()) && layout.inFrom(at.getX(), y, at.getZ(), 0)) {
                layout.set(at.getX(), y, at.getZ(), floor);
            }
        }
    }

    /**
     * Where the stairs' outer treads go: the column furthest out, against her skin on her right, in which they and
     * their inner treads beside them fit, with the floor open before both treads at their foot, so either side of the
     * flight is walked straight onto from the deck; none where they fit nowhere, and then the room has no mezzanine.
     */
    private OptionalInt flightColumn() {
        for (int column = (int) Math.ceil(wall()); column - 1 > RocketLayout.SHAFT + 1; column--) {
            if (footOpen(column - 1) && footOpen(column) && flightFits(column - 1) && flightFits(column)) {
                return OptionalInt.of(column);
            }
        }
        return OptionalInt.empty();
    }

    /** Whether the floor before a column's first tread is open air inside her, up through its headroom. */
    private boolean footOpen(int x) {
        return columnOpen(x, floor() + 1, floor() + HEADROOM, treadZ(1) - 1);
    }

    /**
     * Whether a column of the stairs fits: open air inside her under every tread, where its underside goes, up through
     * its headroom, and the same over the landing at its top.
     */
    private boolean flightFits(int x) {
        for (int step = 1; step <= MEZZANINE_UP; step++) {
            if (!columnOpen(x, floor() + Math.max(1, step - 1), floor() + step + HEADROOM, treadZ(step))) {
                return false;
            }
        }
        return columnOpen(x, mezzanineFloor(), mezzanineFloor() + HEADROOM, landingZ());
    }

    /** Whether a column of blocks is open air inside her from one row to another. */
    private boolean columnOpen(int x, int from, int to, int z) {
        for (int y = from; y <= to; y++) {
            if (!openAir(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The mezzanine's floor: the light cabin floor a lid is laid in, out to her skin behind its front edge, but where
     * the stairs' top treads already stand in that edge.
     */
    private void mezzanine() {
        int y = mezzanineFloor();
        int reach = (int) Math.ceil(wall()) + 1;
        BlockState floor = SkylinersRegistry.CABIN_FLOOR.get().defaultBlockState();
        for (BlockPos at : BlockPos.betweenClosed(-reach, y, MEZZANINE_FRONT, reach, y, reach)) {
            if (openAir(at.getX(), y, at.getZ())) {
                layout.set(at.getX(), y, at.getZ(), floor);
                upperFloor.add(at.immutable());
            }
        }
    }

    /**
     * The stairs up to the mezzanine: two treads wide against her right-hand wall, climbing toward her back from the
     * deck's floor a row a tread, so the top one, in the mezzanine's front edge, is level with its floor and steps off
     * onto it, ahead onto the landing or aside onto the row along its rail; each over the turned-over step that is its
     * underside but the first, which stands on the floor. Laid before the mezzanine's floor, whose edge they take.
     * Their headroom is kept clear, and so are the landing at their top, the floor before both treads at their foot
     * and a lane to it from the way out from the lift's door.
     */
    private void stairs() {
        BlockState tread = SkylinersRegistry.CABIN_STAIRS.get().defaultBlockState().setValue(CabinStairsBlock.FACING, Direction.SOUTH);
        BlockState underside = tread.setValue(CabinStairsBlock.FACING, Direction.NORTH).setValue(CabinStairsBlock.HALF, Half.TOP);
        for (int step = 1; step <= MEZZANINE_UP; step++) {
            tread(outer - 1, step, tread, underside);
            tread(outer, step, tread, underside);
        }
        for (int x = LANE_FROM; x <= outer; x++) {
            keepOver(x, floor(), treadZ(1) - 1);
        }
        keepOver(outer - 1, mezzanineFloor(), landingZ());
        keepOver(outer, mezzanineFloor(), landingZ());
    }

    /** A tread of the stairs in a column, the turned-over step under it, and its headroom kept clear. */
    private void tread(int x, int step, BlockState tread, BlockState underside) {
        int y = floor() + step;
        int z = treadZ(step);
        layout.set(x, y, z, tread);
        if (step > 1) {
            layout.set(x, y - 1, z, underside);
        }
        keepOver(x, y, z);
    }

    /** Keeps open the {@link #HEADROOM} rows over a block that is walked on. */
    private void keepOver(int x, int y, int z) {
        for (int up = 1; up <= HEADROOM; up++) {
            kept.add(new BlockPos(x, y + up, z));
        }
    }

    /** The starlight ceiling: the airliner's starlight panels, dimmed, in every free block of the row under it. */
    private void starlight(int y) {
        int reach = (int) Math.ceil(wall());
        BlockState panel = SkylinersRegistry.STARLIGHT_PANEL.get().defaultBlockState().setValue(Brightness.PROPERTY, Brightness.DIM);
        for (BlockPos at : BlockPos.betweenClosed(-reach, y, -reach, reach, y, reach)) {
            put(at.getX(), at.getY(), at.getZ(), panel);
        }
    }

    /**
     * The starlight panels up the wall too, over the band of glass to the ceiling, in every open block from her skin in
     * to the ceiling's panels, so that the whole room over the band is a starry dome, a planetarium; but for the
     * cafeteria's own rows over the mezzanine and the stairs' headroom. Her LED strips run round its top, under the
     * starlight ceiling (see {@link #underCeiling}), and along the foot of the band of glass under it.
     */
    private void starryWall(int highest) {
        int reach = (int) Math.ceil(wall()) + 1;
        BlockState panel = SkylinersRegistry.STARLIGHT_PANEL.get().defaultBlockState().setValue(Brightness.PROPERTY, Brightness.DIM);
        for (BlockPos at : BlockPos.betweenClosed(-reach, floor() + BAND_TO + 1, -reach, reach, highest, reach)) {
            int x = at.getX();
            int y = at.getY();
            int z = at.getZ();
            boolean wall = layout.inFrom(x, y, z, 0) && !inRoom(x, y, z, 0) && layout.isOpen(x, y, z) && !kept.contains(at) && !inCafe(x, y, z);
            if (wall) {
                layout.set(x, y, z, panel);
            }
        }
    }

    /** Whether the cover of her skin stands beside a block, further out from her axis: a place along her wall. */
    private boolean againstSkin(int x, int y, int z) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            boolean outward = side.getStepX() * x + side.getStepZ() * z > 0;
            if (outward && layout.stateAt(x + side.getStepX(), y, z + side.getStepZ()).is(SkylinersRegistry.ZEPPELIN_COVER.get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The glass rails: along the mezzanine's edge wherever it drops away to the deck below, and along the open sides of
     * the stairs from {@link #RAILED_FROM} up, sloping with them.
     */
    private void rails() {
        for (BlockPos at : upperFloor) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                railBeside(at.getX(), at.getY(), at.getZ(), side);
            }
        }
        for (int step = RAILED_FROM; step <= MEZZANINE_UP; step++) {
            railBeside(outer - 1, floor() + step, treadZ(step), Direction.WEST);
            railBeside(outer, floor() + step, treadZ(step), Direction.EAST);
        }
    }

    /**
     * A glass rail along one side of the block over one that is walked on, where the block beside it is open air, a
     * drop: added to a rail already standing there, sloping with a tread under it (see {@link CabinRailBlock}).
     */
    private void railBeside(int x, int y, int z, Direction side) {
        BlockState rail = SkylinersRegistry.CABIN_RAIL.get().defaultBlockState();
        BlockState there = layout.stateAt(x, y + 1, z);
        boolean room = layout.isOpen(x, y + 1, z) || there.is(rail.getBlock());
        if (room && openAir(x + side.getStepX(), y, z + side.getStepZ())) {
            layout.set(x, y + 1, z, CabinRailBlock.along(there, side, layout.stateAt(x, y, z), rail));
        }
    }

    /**
     * The cafeteria on the mezzanine, its things standing on its floor in the row given: round her back wall the
     * counter, the snack machine on the way to it from the stairs and the drinks cabinet beyond it, each with its front
     * kept clear; then tables set about at random between them and the rail, and plants in the places left along the
     * wall.
     */
    private void cafeteria(int y) {
        counter(alongSkin(y, COUNTER_FROM, COUNTER_TO));
        snackMachine(alongSkin(y, SNACKS_FROM, COUNTER_FROM).reversed());
        drinksCabinet(alongSkin(y, COUNTER_TO, CABINET_TO));
        int reach = (int) Math.ceil(wall()) + 1;
        List<BlockPos> spots = BlockPos.betweenClosedStream(-reach, y, MEZZANINE_FRONT + 2, reach, y, reach)
            .filter(at -> free(at.getX(), y, at.getZ()))
            .map(BlockPos::immutable)
            .toList();
        for (int table = 0; table < spots.size() / SPOTS_A_TABLE; table++) {
            cafeTable(pick(spots));
        }
        for (BlockPos at : alongSkin(y, 0, 360)) {
            plant(at, CAFE_PLANTS);
        }
    }

    /**
     * The cafeteria's free blocks in a row that stand against her skin between two angles round from her front, in
     * degrees, in order round.
     */
    private List<BlockPos> alongSkin(int y, double from, double to) {
        int reach = (int) Math.ceil(wall()) + 1;
        return BlockPos.betweenClosedStream(-reach, y, MEZZANINE_FRONT + 1, reach, y, reach)
            .filter(at -> free(at.getX(), y, at.getZ()) && againstSkin(at.getX(), y, at.getZ()))
            .filter(at -> roundOf(at.getX(), at.getZ()) >= from && roundOf(at.getX(), at.getZ()) < to)
            .map(BlockPos::immutable)
            .sorted(Comparator.comparingDouble(at -> roundOf(at.getX(), at.getZ())))
            .toList();
    }

    /**
     * The serving counter, a run of the galley's units along her back wall facing in, an oven in its middle, stowage
     * and worktops by turns; on the worktops the coffee machine, a cake at the far end and a pot of flowers between.
     */
    private void counter(List<BlockPos> run) {
        List<BlockPos> worktops = new ArrayList<>();
        for (int unit = 0; unit < run.size(); unit++) {
            BlockPos at = run.get(unit);
            Direction facing = inward(roundOf(at.getX(), at.getZ()));
            boolean worktop = unit % 2 == 0 && unit != run.size() / 2;
            if (put(at.getX(), at.getY(), at.getZ(), galleyUnit(unit, run.size(), facing)) && worktop) {
                worktops.add(at);
            }
            keepFront(at, facing);
        }
        if (!worktops.isEmpty()) {
            BlockPos first = worktops.getFirst();
            put(first.getX(), first.getY() + 1, first.getZ(), SkylinersRegistry.COFFEE_MACHINE.get().defaultBlockState()
                .setValue(CoffeeMachineBlock.FACING, inward(roundOf(first.getX(), first.getZ()))));
        }
        if (worktops.size() > 1) {
            BlockPos last = worktops.getLast();
            put(last.getX(), last.getY() + 1, last.getZ(), Blocks.CAKE.defaultBlockState());
        }
        if (worktops.size() > 2) {
            BlockPos middle = worktops.get(worktops.size() / 2);
            put(middle.getX(), middle.getY() + 1, middle.getZ(), pick(POTTED).defaultBlockState());
        }
    }

    /** A unit of the counter, so far along a run of so many: the oven in its middle, else stowage or a worktop. */
    private static BlockState galleyUnit(int unit, int units, Direction facing) {
        if (unit == units / 2) {
            return SkylinersRegistry.GALLEY_OVEN.get().defaultBlockState().setValue(GalleyOvenBlock.FACING, facing);
        }
        Block kind = unit % 2 == 1 ? SkylinersRegistry.GALLEY_STOWAGE.get() : SkylinersRegistry.GALLEY_WORKTOP.get();
        return kind.defaultBlockState().setValue(GalleyUnitBlock.FACING, facing);
    }

    /** Keeps open the blocks in front of something standing against the wall, for whoever comes up to it. */
    private void keepFront(BlockPos at, Direction facing) {
        keepOver(at.getX() + facing.getStepX(), at.getY() - 1, at.getZ() + facing.getStepZ());
    }

    /** The snack machine, two blocks tall, in the first of a run of places along the wall where it fits, facing in. */
    private void snackMachine(List<BlockPos> run) {
        for (BlockPos at : run) {
            if (free(at.getX(), at.getY() + 1, at.getZ())) {
                Direction facing = inward(roundOf(at.getX(), at.getZ()));
                BlockState lower = SnackMachineBlock.lower(SkylinersRegistry.SNACK_MACHINE.get(), facing);
                layout.set(at.getX(), at.getY(), at.getZ(), lower);
                layout.set(at.getX(), at.getY() + 1, at.getZ(), SnackMachineBlock.upper(lower));
                keepFront(at, facing);
                return;
            }
        }
    }

    /** The drinks cabinet, its lit shelves of bottles on it, in the first of a run of places where it fits. */
    private void drinksCabinet(List<BlockPos> run) {
        for (BlockPos at : run) {
            if (free(at.getX(), at.getY() + 1, at.getZ())) {
                Direction facing = inward(roundOf(at.getX(), at.getZ()));
                BlockState cabinet = SkylinersRegistry.BACK_BAR.get().defaultBlockState().setValue(DisplayShelfBlock.FACING, facing);
                layout.set(at.getX(), at.getY(), at.getZ(), cabinet);
                layout.set(at.getX(), at.getY() + 1, at.getZ(), cabinet.setValue(DisplayShelfBlock.TIER, DisplayShelfBlock.Tier.SHELVES));
                keepFront(at, facing);
                return;
            }
        }
    }

    /**
     * A table of the cafeteria at a place where there is room round it, and two seats facing each other across it,
     * either side or front and back: bar stools, or armchairs in one of the cafeteria's colours; an aisle is kept open
     * round them, so the tables never wall anything off.
     */
    private void cafeTable(BlockPos at) {
        int x = at.getX();
        int y = at.getY();
        int z = at.getZ();
        if (!roomAround(x, y, z) || !put(x, y, z, SkylinersRegistry.LOUNGE_TABLE.get().defaultBlockState())) {
            return;
        }
        Direction side = random.nextBoolean() ? Direction.EAST : Direction.NORTH;
        boolean stools = random.nextFloat() < STOOLS;
        DyeColor colour = pick(CAFE_COLOURS);
        for (Direction way : List.of(side, side.getOpposite())) {
            if (stools) {
                put(x + way.getStepX(), y, z + way.getStepZ(), SkylinersRegistry.BAR_STOOL.get().defaultBlockState());
            } else {
                seat(x + way.getStepX(), y, z + way.getStepZ(), way.getOpposite(), colour);
            }
        }
        for (BlockPos aisle : BlockPos.betweenClosed(x - 2, y, z - 2, x + 2, y, z + 2)) {
            if (Math.max(Math.abs(aisle.getX() - x), Math.abs(aisle.getZ() - z)) == 2) {
                keepOver(aisle.getX(), y - 1, aisle.getZ());
            }
        }
    }

    /** Whether a block and the eight round it in its row are all free, room for a table and its seats. */
    private boolean roomAround(int x, int y, int z) {
        return BlockPos.betweenClosedStream(x - 1, y, z - 1, x + 1, y, z + 1).allMatch(at -> free(at.getX(), y, at.getZ()));
    }

    /** One of a list, at random. */
    private <T> T pick(List<T> from) {
        return from.get(random.nextInt(from.size()));
    }

    /** A planter at a place, facing in, by a chance; one of the plants at random in it. */
    private void plant(BlockPos at, float chance) {
        if (random.nextFloat() < chance) {
            Direction facing = inward(roundOf(at.getX(), at.getZ()));
            put(at.getX(), at.getY(), at.getZ(), PlanterBlock.planted(SkylinersRegistry.PLANTER.get(), pick(PLANTS), facing));
        }
    }

    /**
     * The bookcase under the top of the stairs, where the treads overhead leave the room: vanilla bookshelves on the
     * floor under the two highest inner treads, facing into the room.
     */
    private void bookcase() {
        for (int step = MEZZANINE_UP - 1; step <= MEZZANINE_UP; step++) {
            shelves(outer - 1, treadZ(step));
        }
    }

    /** A column of bookshelves on the deck's floor, {@link #BOOKCASE_ROWS} tall, where it is open. */
    private void shelves(int x, int z) {
        for (int y = floor() + 1; y <= floor() + BOOKCASE_ROWS; y++) {
            if (openAir(x, y, z)) {
                layout.set(x, y, z, Blocks.BOOKSHELF.defaultBlockState());
            }
        }
    }

    /**
     * The lounge on the deck's floor, its furniture standing in the row given: rings of groups round her shaft, each
     * set a little off its place, then pairs of bean bags by the glass and plants at the room's edge where room is
     * left.
     */
    private void lounge(int y) {
        for (double out = GROUPS_OUT; out < wall(); out += GROUPS_APART) {
            ringOfGroups(y, out);
        }
        double window = wall() - WINDOW_IN;
        int pairs = (int) (2 * Math.PI * window / WINDOW_ROUND);
        double first = random.nextDouble() * 360;
        for (int pair = 0; pair < pairs; pair++) {
            beanBagsByTheGlass(y, window, first + pair * 360.0 / pairs);
        }
        double edge = wall() - PLANT_IN;
        int plants = (int) (2 * Math.PI * edge / PLANT_ROUND);
        for (int plant = 0; plant < plants; plant++) {
            double round = random.nextDouble() * 360;
            plant(new BlockPos(across(edge, round), y, along(edge, round)), 1);
        }
    }

    /** A ring of the lounge's groups so far out from her axis, as many as fit round it, each a little off its place. */
    private void ringOfGroups(int y, double out) {
        int groups = (int) (2 * Math.PI * out / GROUP_ROUND);
        double apart = 360.0 / groups;
        double first = random.nextDouble() * apart;
        for (int group = 0; group < groups; group++) {
            double round = first + apart * (group + (random.nextDouble() - 0.5) * GROUP_ROUND_JITTER);
            double at = out + (random.nextDouble() * 2 - 1) * GROUP_OUT_JITTER;
            group(across(at, round), y, along(at, round));
        }
    }

    /**
     * A group to talk in round a low table, at the nearest place to a spot of the floor where one fits: on three of its
     * four sides, the fourth left as the way in, an armchair or a bean bag facing it, the armchairs all in one colour
     * and the bean bags in another; maybe a plant at a corner, and a round rug under it all. Under the mezzanine, a
     * cabin light over it.
     */
    private void group(int x, int y, int z) {
        BlockPos table = NEARBY.stream()
            .map(near -> new BlockPos(x + near.getX(), y, z + near.getZ()))
            .filter(this::groupFits)
            .findFirst()
            .orElse(null);
        if (table == null || !put(table.getX(), y, table.getZ(), SkylinersRegistry.LOUNGE_TABLE.get().defaultBlockState())) {
            return;
        }
        DyeColor armchairs = pick(ARMCHAIR_COLOURS);
        DyeColor beanBags = pick(BEAN_BAG_COLOURS);
        int wayIn = wayInto(table);
        for (int side = 0; side < 4; side++) {
            Direction way = Direction.from2DDataValue(side);
            if (side != wayIn) {
                sitFacing(table.relative(way), way.getOpposite(), armchairs, beanBags);
            }
        }
        Direction corner = Direction.from2DDataValue(random.nextInt(4));
        plant(table.relative(corner).relative(corner.getClockWise()), GROUP_PLANTS);
        rug(table.getX(), y, table.getZ(), pick(RUG_COLOURS));
        if (table.getZ() >= MEZZANINE_FRONT) {
            lamps.add(new BlockPos(table.getX(), mezzanineFloor(), table.getZ()));
        }
    }

    /**
     * Whether a group's table fits at a block: free, a block further in from the skin than the room's edge, with room
     * for a seat on {@link #GROUP_SEATS} of its sides at least.
     */
    private boolean groupFits(BlockPos at) {
        boolean table = free(at.getX(), at.getY(), at.getZ()) && inRoom(at.getX(), at.getY(), at.getZ(), 1);
        return table && Direction.Plane.HORIZONTAL.stream().filter(side -> seatFree(at.relative(side))).count() >= GROUP_SEATS;
    }

    /** Whether there is room for a seat at a block, an armchair's two blocks tall. */
    private boolean seatFree(BlockPos at) {
        return free(at.getX(), at.getY(), at.getZ()) && free(at.getX(), at.getY() + 1, at.getZ());
    }

    /** The side of a group's table left as its way in, by its 2D value: one without room for a seat, or any. */
    private int wayInto(BlockPos table) {
        for (int side = 0; side < 4; side++) {
            if (!seatFree(table.relative(Direction.from2DDataValue(side)))) {
                return side;
            }
        }
        return random.nextInt(4);
    }

    /** A seat of a group at a block facing the way given: an armchair, or a bean bag, by a chance. */
    private void sitFacing(BlockPos at, Direction facing, DyeColor armchairs, DyeColor beanBags) {
        if (random.nextFloat() < ARMCHAIRS) {
            seat(at.getX(), at.getY(), at.getZ(), facing, armchairs);
        } else {
            put(at.getX(), at.getY(), at.getZ(), beanBag(facing, beanBags));
        }
    }

    /** A round rug in a colour under a group, on the free blocks of the floor round its table. */
    private void rug(int x, int y, int z, DyeColor colour) {
        BlockState carpet = CabinCarpetBlock.carpet(SkylinersRegistry.CABIN_CARPET.get(), colour);
        int reach = (int) Math.ceil(RUG_RADIUS);
        for (BlockPos at : BlockPos.betweenClosed(x - reach, y, z - reach, x + reach, y, z + reach)) {
            if (Math.hypot(at.getX() - x, at.getZ() - z) <= RUG_RADIUS) {
                put(at.getX(), y, at.getZ(), carpet);
            }
        }
    }

    /** A pair of bean bags so far out and so far round from her front, in one colour, facing out of the glass. */
    private void beanBagsByTheGlass(int y, double out, double round) {
        DyeColor colour = pick(BEAN_BAG_COLOURS);
        double half = Math.toDegrees(PAIR_APART / 2 / out);
        for (int side = -1; side <= 1; side += 2) {
            double at = round + side * half;
            put(across(out, at), y, along(out, at), beanBag(inward(at).getOpposite(), colour));
        }
    }

    /**
     * The cabin lights set into the mezzanine's floor, dimmed, lighting the deck under it and the cafeteria over it:
     * one over each group under it and a row along its front edge, one every {@link #EDGE_LIGHT_APART} blocks past the
     * shaft.
     */
    private void lights() {
        BlockState light = SkylinersRegistry.CABIN_LIGHT.get().defaultBlockState().setValue(Brightness.PROPERTY, Brightness.DIM);
        for (BlockPos at : upperFloor) {
            boolean edge = at.getZ() == MEZZANINE_FRONT && Math.abs(at.getX()) > RocketLayout.SHAFT && at.getX() % EDGE_LIGHT_APART == 0;
            if ((edge || lamps.contains(at)) && layout.stateAt(at.getX(), at.getY(), at.getZ()).is(SkylinersRegistry.CABIN_FLOOR.get())) {
                layout.set(at.getX(), at.getY(), at.getZ(), light);
            }
        }
    }

    private static BlockState beanBag(Direction facing, DyeColor colour) {
        return SkylinersRegistry.BEAN_BAG.get().defaultBlockState().setValue(BeanBagBlock.FACING, facing).setValue(BeanBagBlock.COLOR, colour);
    }
}
