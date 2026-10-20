package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * The upper deck, as an A380's: a second floor {@link AirframeShape#UPPER_DECK} over the main deck, under the upper
 * row of windows, from just behind the flight deck back to where the tail starts closing in over the lounge. A spiral
 * staircase winds up to it at the front, to port just behind the flight deck, and a straight one climbs to it at the
 * back, up the starboard wall where the gym hall ends.
 *
 * <p>In the empty airliner it is one floor the whole way, left for building in like the main deck. In the furnished
 * one the rooms keep their height: it is a whole floor only behind the flight deck where that is as deep as the
 * forward staircase, and over the rooms (the moon pool and its diving tower, the gym hall with its hoops, ropes and
 * rings) and the doors' aisles, whose bulkheads carry the archways' pictures, a gallery along each wall, railed with
 * glass round an open well, through doorways in the bulkheads, so the rooms can be looked down into.
 *
 * <p>The floor goes in with the airframe, before the rooms under it are fitted out round it; the stairs, the rails and
 * the doorways once they stand. Where the airliner is too small to stand up on both decks there is no upper deck.
 */
final class UpperDeck {
    /** A player's height, in blocks: the headroom the deck keeps over its floor, and over the main deck under it. */
    private static final int STANDING_ROOM = 2;
    /** How wide a gallery's walkway is, from the wall in to its rail, in metres and at least in blocks. */
    private static final double WALKWAY = 1.0;
    private static final int LEAST_WALKWAY = 2;
    /**
     * How many blocks either side of the middle a gallery's well keeps open at least: the archways' pictures over it
     * are five wide.
     */
    private static final int WELL = 2;
    /** Where across the forward staircase's newel stands, in metres to port of the middle. */
    private static final double SPIRAL_X = 2.0;
    /**
     * The forward staircase's steps round its newel, in the order they climb, as (x, z) offsets from it: from
     * starboard of it, climbing forward, then round to port and back, a row up each.
     */
    private static final int[][] SPIRAL = {{1, 0}, {1, -1}, {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}, {0, 1}, {1, 1}};
    /** How many stations deep the forward staircase is, round its newel. */
    private static final int SPIRAL_DEPTH = 3;
    /** Where the aft staircase's top step is, in metres from the nose: a metre short of the gym hall's back end. */
    private static final double AFT_STAIRS_TOP = CabinFitOut.GYM[1] - 1.0;

    private final AirframeLayout layout;
    private final boolean furnished;
    /** The deck's floor row, and the main deck's. */
    private final int deck;
    private final int floor;
    /** The first and last stations the deck reaches. */
    private final int start;
    private int end;
    /** The first station of the forward doors' aisle, behind which the furnished deck's floor is whole. */
    private final int firstAisle;

    private UpperDeck(AirframeLayout layout, boolean furnished) {
        this.layout = layout;
        this.furnished = furnished;
        this.deck = layout.upperDeckY();
        this.floor = layout.floorY();
        // Up to the lounge's bulkhead, where its starlit ceiling keeps the tail's height.
        this.end = (int) Math.floor((CabinFitOut.LOUNGE[0] - 0.5) * layout.scale()) - 1;
        int from = layout.flightDeckBack() + 1;
        // Behind the steps down from the flight deck, where whoever climbs them has a head's room under it.
        while (from <= end && standingRoomUnder(from) < STANDING_ROOM) {
            from++;
        }
        this.start = from;
        int aisle = start;
        while (aisle <= end && !layout.inDoorAisle(aisle)) {
            aisle++;
        }
        this.firstAisle = aisle;
    }

    /**
     * Lays the upper deck's floor, if the airliner is big enough to stand up on both decks; the deck, or null where
     * it is not.
     */
    @Nullable
    static UpperDeck lay(AirframeLayout layout, boolean furnished) {
        UpperDeck upper = new UpperDeck(layout, furnished);
        if (upper.deck - upper.floor - 1 <= STANDING_ROOM || upper.start > upper.end || !upper.openOver(0, upper.start)) {
            return null;
        }
        upper.floor();
        return upper;
    }

    /**
     * How many rows a player has to stand in under the deck over whatever is highest in the middle of the main deck at
     * a station.
     */
    private int standingRoomUnder(int z) {
        int y = deck - 1;
        while (y > floor && layout.isOpen(0, y, z)) {
            y--;
        }
        return deck - 1 - y;
    }

    /** Whether the deck's row is open at this column, before its floor is laid, and a player can stand over it. */
    private boolean openOver(int x, int z) {
        for (int y = deck; y <= deck + STANDING_ROOM; y++) {
            if (!layout.isOpen(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the deck's floor is whole across the station, not a gallery either side of a well: behind the flight deck
     * where that is as deep as the forward staircase.
     */
    private boolean whole(int z) {
        return !furnished || z < firstAisle && firstAisle - start >= SPIRAL_DEPTH;
    }

    /**
     * Where a gallery's rail stands at a station, out from the middle: as far in from the wall as its walkway is wide,
     * measured where it is a head's height clear, and outside the well; -1 where the cabin is too narrow for both.
     */
    private int railAt(int z) {
        int outer = layout.openHalfWidth(deck + STANDING_ROOM, z);
        int walkway = Math.max(LEAST_WALKWAY, (int) Math.round(WALKWAY * layout.scale()));
        int rail = Math.max(WELL + 1, outer - walkway);
        return rail < outer ? rail : -1;
    }

    /**
     * The floor, of the cabin's own floor panels, station by station: right across it, or a gallery from the rail out
     * to the wall either side. It ends where the tail leaves no room to stand on it.
     */
    private void floor() {
        BlockState panel = layout.palette().floor();
        for (int z = start; z <= end; z++) {
            if (!openOver(0, z)) {
                end = z - 1;
                return;
            }
            int inner = whole(z) ? 0 : railAt(z);
            if (inner < 0) {
                continue;
            }
            for (int x = inner, half = layout.openHalfWidth(deck, z); x <= half; x++) {
                layout.set(x, deck, z, panel);
                layout.set(-x, deck, z, panel);
            }
        }
    }

    /**
     * Whether a gallery's walkway (or the whole floor) beside the wall on this side reaches the station: where the
     * upper deck's own seatbelt signs can go.
     */
    boolean walkwayBeside(int side, int z) {
        int x = layout.besideWall(side, deck + 1, z);
        return x != 0 && z >= start && z <= end && layout.isCabinFloor(x, deck, z) && layout.isOpen(x, deck + 1, z);
    }

    /**
     * Finishes the deck once the rooms under it stand: the stairs up to the deck, the doorways along the galleries
     * through the bulkheads, the cabin lights in the floor and the rails round every edge.
     */
    void finish() {
        spiral();
        straight();
        doorways();
        lights();
        rails();
    }

    /**
     * The forward staircase: a spiral round a newel, three blocks square, to port of the steps up to the flight deck,
     * from the main deck's floor up to the deck's, a row a step, and as many turns as that takes; the top step stands
     * in the deck's floor, level with it. Over the steps high enough for a head to reach the deck, its floor is open.
     * Left out where anything stands in its way.
     */
    private void spiral() {
        int cx = -(int) Math.round(SPIRAL_X * layout.scale());
        int cz = start + 1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!clear(cx + dx, cz + dz, floor + 1, deck - 1) || !layout.isCabinFloor(cx + dx, deck, cz + dz) || !headroom(cx + dx, cz + dz, deck + 1)) {
                    return;
                }
            }
        }
        BlockState newel = Blocks.POLISHED_ANDESITE.defaultBlockState();
        for (int y = floor + 1; y < deck; y++) {
            layout.set(cx, y, cz, newel);
        }
        int steps = deck - floor;
        for (int k = 0; k < steps; k++) {
            int[] at = SPIRAL[k % SPIRAL.length];
            int[] next = SPIRAL[(k + 1) % SPIRAL.length];
            step(cx + at[0], floor + 1 + k, cz + at[1], Direction.fromDelta(next[0] - at[0], 0, next[1] - at[1]));
        }
    }

    /**
     * The aft staircase: a straight flight up the starboard wall, climbing aft to its top step a metre short of the
     * gym hall's back end, along the gallery's walkway as far out as it fits.
     */
    private void straight() {
        int steps = deck - floor;
        int top = (int) Math.floor(AFT_STAIRS_TOP * layout.scale());
        int first = top - steps + 1;
        if (top + 1 > end || first - 1 < start) {
            return;
        }
        int outer = layout.openHalfWidth(deck + STANDING_ROOM, top);
        for (int x = outer; x >= outer - 1 && x > WELL; x--) {
            if (!straightFits(x, first, top)) {
                continue;
            }
            for (int k = 0; k < steps; k++) {
                step(x, floor + 1 + k, first + k, Direction.SOUTH);
            }
            return;
        }
    }

    /**
     * Whether the aft staircase fits up the wall at x: room to step onto its foot, the cabin open over its whole
     * length up to under the deck's floor, the deck's floor over it, and its top landing onto the deck.
     */
    private boolean straightFits(int x, int first, int top) {
        if (!clear(x, first - 1, floor + 1, floor + STANDING_ROOM)) {
            return false;
        }
        for (int z = first; z <= top + 1; z++) {
            if (z <= top && !clear(x, z, floor + 1, deck - 1) || !layout.isCabinFloor(x, deck, z) || !headroom(x, z, deck + 1)) {
                return false;
            }
        }
        return true;
    }

    /** Whether the cabin is open at this column from one row up to another. */
    private boolean clear(int x, int z, int from, int to) {
        for (int y = from; y <= to; y++) {
            if (!layout.isOpen(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a player can stand at this column on the given row. */
    private boolean headroom(int x, int z, int row) {
        return clear(x, z, row, row + STANDING_ROOM - 1);
    }

    /**
     * A step climbing the given way, under it the same stair turned over, so the flight's underside slopes with it,
     * and the deck's floor over it opened for a head's room where it reaches that high; the top step stands in the
     * floor's own row, in place of it.
     */
    private void step(int x, int y, int z, Direction up) {
        BlockState stair = Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, up);
        layout.set(x, y, z, stair);
        if (layout.isOpen(x, y - 1, z)) {
            layout.set(x, y - 1, z, stair.setValue(StairBlock.FACING, up.getOpposite()).setValue(StairBlock.HALF, Half.TOP));
        }
        // Stepping up off it takes a row more than standing on it.
        if (y < deck && y + STANDING_ROOM + 1 >= deck) {
            layout.set(x, deck, z, Blocks.AIR.defaultBlockState());
        }
    }

    /**
     * Doorways along the galleries through the bulkheads the rooms under them are walled off with, which reach up to
     * the crown: the walkway's width and a head's height, the wall left whole over the well, where its picture is.
     */
    private void doorways() {
        for (int z = start; z <= end; z++) {
            for (int side = -1; !whole(z) && side <= 1; side += 2) {
                // The gallery's floor, found from the wall in, so the bulkhead across the middle is not taken for its
                // end; its innermost block is where its rail stands, and stays walled.
                int x = layout.besideWall(side, deck, z);
                for (; x != 0 && layout.isCabinFloor(x - side, deck, z) && onwards(x, z); x -= side) {
                    for (int y = deck + 1; y <= deck + STANDING_ROOM; y++) {
                        if (layout.stateAt(x, y, z).is(AerolargeRegistry.CABIN_PARTITION.get())) {
                            layout.set(x, y, z, Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }
        }
    }

    /** Whether the deck's floor goes on both sides of a bulkhead at this block, so a doorway through it leads somewhere. */
    private boolean onwards(int x, int z) {
        return layout.isCabinFloor(x, deck, z - 1) && layout.isCabinFloor(x, deck, z + 1);
    }

    /**
     * Cabin lights set into the whole floor down its middle, every few stations as in the crown, lighting the main
     * deck under it and the deck over it at once; a station on where something hangs from the floor there.
     */
    private void lights() {
        for (int z = start; z <= end; z++) {
            if (!whole(z) || Math.floorMod(z, AirframeLayout.LIGHT_SPACING) != 0) {
                continue;
            }
            for (int at : new int[]{z, z + 1, z - 1}) {
                if (whole(at) && layout.isCabinFloor(0, deck, at) && layout.isOpen(0, deck - 1, at) && layout.isOpen(0, deck + 1, at)) {
                    layout.set(0, deck, at, layout.palette().light());
                    break;
                }
            }
        }
    }

    /**
     * Glass rails along every edge of the floor someone could fall from: round the galleries' well and the open ends of
     * the floor, a block in from the edge; and round the stairwells, where the floor beyond the rail goes on, so a
     * walkway beside a stairwell is left clear.
     */
    private void rails() {
        int half = (int) Math.ceil(AirframeShape.WIDTH / 2 * layout.scale()) + 1;
        Set<BlockPos> rails = new LinkedHashSet<>();
        for (boolean deep : new boolean[]{true, false}) {
            for (int z = start; z <= end; z++) {
                for (int x = -half; x <= half; x++) {
                    if (!layout.isCabinFloor(x, deck, z) || !layout.isOpen(x, deck + 1, z) || rails.contains(new BlockPos(x, deck + 1, z))) {
                        continue;
                    }
                    for (Direction toward : Direction.Plane.HORIZONTAL) {
                        int nx = x + toward.getStepX();
                        int nz = z + toward.getStepZ();
                        if (!drop(nx, deck, nz) || deep(nx, nz) != deep) {
                            continue;
                        }
                        int fx = x - toward.getStepX();
                        int fz = z - toward.getStepZ();
                        if (deep || layout.isCabinFloor(fx, deck, fz) && layout.isOpen(fx, deck + 1, fz) && !rails.contains(new BlockPos(fx, deck + 1, fz))) {
                            rails.add(new BlockPos(x, deck + 1, z));
                            break;
                        }
                    }
                }
            }
        }
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        rails.forEach(pos -> layout.set(pos.getX(), pos.getY(), pos.getZ(), pane));
        for (BlockPos pos : rails) {
            layout.set(pos.getX(), pos.getY(), pos.getZ(), pane
                .setValue(CrossCollisionBlock.NORTH, joins(pos.north()))
                .setValue(CrossCollisionBlock.SOUTH, joins(pos.south()))
                .setValue(CrossCollisionBlock.EAST, joins(pos.east()))
                .setValue(CrossCollisionBlock.WEST, joins(pos.west())));
        }
    }

    /**
     * Whether the deck's floor has an edge here: inside the cabin, neither the floor nor a wall nor a step up onto it,
     * but open, or something hanging or standing in the well at the floor's height that one could step out onto.
     */
    private boolean drop(int x, int y, int z) {
        BlockState state = layout.stateAt(x, y, z);
        return layout.inFuselage(x, y, z) && !layout.isCabinFloor(x, y, z) && !state.is(layout.palette().light().getBlock())
            && !state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) && !(state.getBlock() instanceof StairBlock);
    }

    /** Whether a drop here goes on down further than a stair climbs off it: a well, not a stairwell. */
    private boolean deep(int x, int z) {
        for (int y = deck - 1; y >= deck - STANDING_ROOM - 1; y--) {
            if (!passable(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    private boolean passable(int x, int y, int z) {
        return layout.stateAt(x, y, z).getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
    }

    /** Whether a rail joins on to what is beside it: another rail, or a wall. */
    private boolean joins(BlockPos pos) {
        BlockState state = layout.stateAt(pos.getX(), pos.getY(), pos.getZ());
        return state.is(Blocks.GLASS_PANE) || state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }
}
