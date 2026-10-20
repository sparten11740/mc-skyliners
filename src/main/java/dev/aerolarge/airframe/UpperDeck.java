package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.AisleCarpetBlock;
import dev.aerolarge.block.CabinCarpetBlock;
import dev.aerolarge.block.CabinSeatBlock;
import dev.aerolarge.block.ExitSignBlock;
import dev.aerolarge.block.GalleyBlock;
import dev.aerolarge.block.GalleyOvenBlock;
import dev.aerolarge.block.GalleyUnitBlock;
import dev.aerolarge.block.PassengerScreenBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The upper deck, as an A380's: a second floor {@link AirframeShape#UPPER_DECK} over the main deck, under the upper
 * row of windows, from just behind the flight deck back to where the tail starts closing in over the lounge. A
 * carpeted staircase winds up to it round a newel at the front, to port just behind the flight deck, on a landing at
 * every turn, and a straight one climbs to it at the back, up the starboard wall where the gym hall ends (see
 * {@link DeckStairs}).
 *
 * <p>In the empty airliner it is one floor the whole way, left for building in like the main deck. In the furnished
 * one it is a whole floor only where the rooms under it do not need the cabin's height: over business class, where it
 * has rows of seats of its own, and behind the flight deck where that is as deep as the forward staircase, with the
 * upper deck's galley across from it. Over the tall rooms (the moon pool and its diving tower, the gym hall with its
 * hoops, ropes and rings) and the doors' aisles, whose bulkheads carry the archways' signs, it is a gallery along
 * each wall instead, railed with glass round an open well, through doorways in the bulkheads, so the rooms keep their
 * height and can be looked down into.
 *
 * <p>The floor goes in with the airframe, before the rooms under it are fitted out round it; the floor over business
 * class, the stairs, the rails and the doorways once they stand. Where the airliner is too small to stand up on both
 * decks there is no upper deck.
 */
final class UpperDeck {
    /** A player's height, in blocks: the headroom the deck keeps over its floor, and over the main deck under it. */
    private static final int STANDING_ROOM = 2;
    /** How wide a gallery's walkway is, from the wall in to its rail, in metres and at least in blocks. */
    private static final double WALKWAY = 1.0;
    private static final int LEAST_WALKWAY = 2;
    /**
     * How many blocks either side of the middle a gallery's well keeps open at least: as wide as the widest archway
     * under it, so an archway and the sign over its crown are seen whole from the deck below.
     */
    private static final int WELL = 2;
    /**
     * How big the forward staircase is, in metres across and along, the walk round its newel a block wide: three blocks
     * square at the default scale, where it goes round one and a half times; at the larger scales longer and wider, so
     * it goes round less often, but never more than four blocks across, so its newel stays two blocks thick at most and
     * shows all round.
     */
    private static final double STAIRWELL_ACROSS = 1.25;
    private static final double STAIRWELL_ALONG = 1.5;
    private static final int STAIRWELL_MOST_ACROSS = 4;
    /** The smallest a staircase winding round a newel can be, a block of newel inside it. */
    private static final int STAIRWELL_LEAST = 3;
    /** How far a bulkhead may stand from where it is meant to, in stations (see {@link CabinDividers}). */
    private static final int WALL_SNAP = 2;
    /** Where the aft staircase's top step is, in metres from the nose: a metre short of the gym hall's back end. */
    private static final double AFT_STAIRS_TOP = CabinFitOut.GYM[1] - 1.0;
    /** How far apart the upper deck's rows are, seat to seat, in blocks: a seat and two blocks of legroom in front. */
    private static final int PITCH = 3;
    /** How many seats stand side by side either side of the aisle, a metre and a half's worth. */
    private static final double SEATS_ACROSS = 1.5;
    /** Rows over the floor to seated eye height, where the screens hang on the seat backs. */
    private static final int EYES = 2;
    /** How far the aisle between the rows reaches either side of the middle, in blocks: three blocks wide. */
    private static final int AISLE = 1;

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
    /** How many blocks across and along the forward staircase is. */
    private final int stairwellAcross;
    private final int stairwellAlong;
    /** In the furnished airliner, the stretch over business class where the floor is whole, between its bulkheads. */
    private int businessFrom = 0;
    private int businessTo = -1;
    /** The tops of the staircases, as (station, side): where the exit signs go. */
    private final List<int[]> stairHeads = new ArrayList<>();

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
        this.stairwellAcross = Math.clamp(Math.round(STAIRWELL_ACROSS * layout.scale()), STAIRWELL_LEAST, STAIRWELL_MOST_ACROSS);
        this.stairwellAlong = Math.max(STAIRWELL_LEAST, (int) Math.round(STAIRWELL_ALONG * layout.scale()));
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
     * where that is as deep as the forward staircase, and over business class.
     */
    private boolean whole(int z) {
        return !furnished || z < firstAisle && firstAisle - start >= stairwellAlong || z >= businessFrom && z <= businessTo;
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
        return x != 0 && z >= start && z <= end && layout.isCabinFloor(x, deck, z);
    }

    /**
     * Finishes the deck once the rooms under it stand: the floor over business class in the furnished airliner, the
     * stairs up to the deck, the doorways along the galleries through the bulkheads, the cabin lights in the floor and
     * the rails round every edge.
     */
    void finish() {
        if (furnished) {
            business();
        }
        forward();
        aft();
        doorways();
        lights();
        rails();
    }

    /**
     * The whole floor over business class in the furnished airliner, across the well between its galleries from the
     * bulkhead ahead of it to the one behind it, wherever they ended up standing, so neither is closed over and both
     * keep their signs over their archways. What hung from the crown there now hangs from the floor: a light's
     * cable stops at it, a light that hung at its row or over it moves down under it, and what was above is taken
     * down. Not where it would leave too little room for a row of seats.
     */
    private void business() {
        double scale = layout.scale();
        int front = (int) Math.floor((CabinFitOut.BUSINESS[0] - 0.5) * scale);
        int aft = (int) Math.floor((CabinFitOut.BUSINESS[1] + CabinFitOut.POOL[0]) / 2 * scale);
        int from = front + WALL_SNAP + 1;
        int to = aft - WALL_SNAP - 1;
        for (int z = front + WALL_SNAP; z >= front - WALL_SNAP; z--) {
            if (CabinDividers.standsAt(layout, z)) {
                from = z + 1;
                break;
            }
        }
        for (int z = aft - WALL_SNAP; z <= aft + WALL_SNAP; z++) {
            if (CabinDividers.standsAt(layout, z)) {
                to = z - 1;
                break;
            }
        }
        if (to - from + 1 < PITCH + 2) {
            return;
        }
        BlockState panel = layout.palette().floor();
        for (int z = from; z <= to; z++) {
            for (int x = layout.besideWall(-1, deck, z); x <= layout.besideWall(1, deck, z); x++) {
                if (layout.isCabinFloor(x, deck, z)) {
                    continue;
                }
                hangUnder(x, z);
                if (layout.isOpen(x, deck, z)) {
                    layout.set(x, deck, z, panel);
                }
            }
        }
        businessFrom = from;
        businessTo = to;
    }

    /**
     * Rehangs what hangs from the crown through the deck's row in this column under the deck's floor: a pendant
     * light's cable cut at the floor, and a light hanging at or over the floor's row moved down under it, its cable
     * taken down; anything else stays, and the floor leaves it a hole.
     */
    private void hangUnder(int x, int z) {
        int top = deck;
        while (layout.isOpen(x, top, z) || hanging(x, top, z)) {
            top++;
        }
        BlockState light = null;
        for (int y = deck; y < top; y++) {
            if (layout.stateAt(x, y, z).is(AerolargeRegistry.PENDANT_LIGHT.get())) {
                light = layout.stateAt(x, y, z);
            }
            layout.set(x, y, z, Blocks.AIR.defaultBlockState());
        }
        if (light != null && layout.isOpen(x, deck - 1, z)) {
            layout.set(x, deck - 1, z, light);
        }
    }

    /** Whether a pendant light or its cable hangs here. */
    private boolean hanging(int x, int y, int z) {
        BlockState state = layout.stateAt(x, y, z);
        return state.is(AerolargeRegistry.LIGHT_CABLE.get()) || state.is(AerolargeRegistry.PENDANT_LIGHT.get());
    }

    /**
     * The forward staircase, winding up round a newel just to port of the steps up to the flight deck (see
     * {@link DeckStairs#round}): in at its corner nearest the forward doors, it climbs forward beside the steps, turns
     * outboard across the front, comes back aft along the hull's side and turns inboard again, round and round on a
     * landing in every corner, until it comes out on the deck. Three blocks square if its full size does not fit; left
     * out where that does not either.
     */
    private void forward() {
        int east = -(AirframeLayout.STEPS_HALF_WIDTH + 1);
        DeckStairs stairs = DeckStairs.round(layout, east, start, stairwellAcross, stairwellAlong);
        if (!stairs.fits()) {
            stairs = DeckStairs.round(layout, east, start, STAIRWELL_LEAST, STAIRWELL_LEAST);
        }
        if (stairs.fits()) {
            stairs.lay();
            stairHeads.add(new int[]{stairs.exitStation(), -1});
        }
    }

    /**
     * The aft staircase: a straight flight up the starboard wall (see {@link DeckStairs#straight}), climbing aft to its
     * top stair a metre short of the gym hall's back end, under the gallery's walkway as far out as it fits.
     */
    private void aft() {
        int top = (int) Math.floor(AFT_STAIRS_TOP * layout.scale());
        int first = top - (deck - floor) + 1;
        if (top + 1 > end || first - 1 < start) {
            return;
        }
        int outer = layout.openHalfWidth(deck + STANDING_ROOM, top);
        for (int x = outer; x >= outer - 1 && x > WELL; x--) {
            DeckStairs stairs = DeckStairs.straight(layout, x, first, Direction.SOUTH);
            if (underWalkway(x, first, top + 1) && stairs.fits()) {
                stairs.lay();
                stairHeads.add(new int[]{top, 1});
                return;
            }
        }
    }

    /** Whether the deck's floor runs over a column from one station to another, with a head's room over it. */
    private boolean underWalkway(int x, int from, int to) {
        for (int z = from; z <= to; z++) {
            for (int y = deck + 1; y <= deck + STANDING_ROOM; y++) {
                if (!layout.isOpen(x, y, z)) {
                    return false;
                }
            }
            if (!layout.isCabinFloor(x, deck, z)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Doorways along the galleries through the bulkheads the rooms under them are walled off with, which reach up to
     * the crown: the walkway's width and a head's height, the wall left whole over the well, where its sign is.
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
     * Glass rails along every edge of the floor someone could step off (see {@link DeckStairs#dropsAway}): round the
     * galleries' well, the open ends of the floor and the stairwells, each standing along the edge of the floor.
     */
    private void rails() {
        int half = (int) Math.ceil(AirframeShape.WIDTH / 2 * layout.scale()) + 1;
        for (int z = start; z <= end; z++) {
            for (int x = -half; x <= half; x++) {
                if (!layout.isCabinFloor(x, deck, z)) {
                    continue;
                }
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    if (DeckStairs.dropsAway(layout, x + side.getStepX(), deck, z + side.getStepZ())) {
                        DeckStairs.rail(layout, x, deck + 1, z, side);
                    }
                }
            }
        }
    }

    /**
     * The furnished upper deck: its galley at the top of the forward staircase, rows of seats over business class with
     * a screen on the back of each seat that has another behind it, a grey carpet wherever its floor is whole and has
     * headroom, the aisle's runner with its path lights along the aisle between the rows, and an exit sign over the
     * top of each staircase, the way down.
     */
    void furnish() {
        galley();
        rows();
        BlockState carpet = CabinCarpetBlock.carpet(AerolargeRegistry.CABIN_CARPET.get(), DyeColor.LIGHT_GRAY);
        BlockState runner = CabinCarpetBlock.carpet(AerolargeRegistry.AISLE_CARPET.get(), DyeColor.LIGHT_GRAY);
        int half = (int) Math.ceil(AirframeShape.WIDTH / 2 * layout.scale()) + 1;
        for (int z = start; z <= end; z++) {
            for (int x = -half; whole(z) && x <= half; x++) {
                // Not on the ledge under the hull's curve, where nobody stands; between the rows, the aisle's runner
                // along both edges of the aisle, its path lights toward the seats.
                boolean aisle = Math.abs(x) == AISLE && z >= businessFrom && z <= businessTo;
                if (layout.isCabinFloor(x, deck, z) && layout.isOpen(x, deck + STANDING_ROOM, z)) {
                    layout.furnish(x, deck + 1, z, aisle ? runner.setValue(AisleCarpetBlock.FACING, x > 0 ? Direction.EAST : Direction.WEST) : carpet);
                }
            }
        }
        exitSigns();
    }

    /**
     * The upper deck's galley, against the starboard wall across from the top of the forward staircase, all of it
     * facing the aisle: the galley's trolley bay that stocks the cabin crew's trolley, galley stowage beside it and a
     * galley oven beyond where the floor runs on; where the floor behind the flight deck is whole.
     */
    private void galley() {
        if (firstAisle - start < stairwellAlong) {
            return;
        }
        int x = room(start);
        if (x < 2 || !layout.isCabinFloor(x, deck, start) || !layout.isCabinFloor(x, deck, start + 1)) {
            return;
        }
        layout.furnish(x, deck + 1, start, AerolargeRegistry.GALLEY.get().defaultBlockState().setValue(GalleyBlock.FACING, Direction.WEST));
        layout.furnish(x, deck + 1, start + 1, AerolargeRegistry.GALLEY_STOWAGE.get().defaultBlockState().setValue(GalleyUnitBlock.FACING, Direction.WEST));
        if (layout.isCabinFloor(x, deck, start + 2)) {
            layout.furnish(x, deck + 1, start + 2, AerolargeRegistry.GALLEY_OVEN.get().defaultBlockState().setValue(GalleyOvenBlock.FACING, Direction.WEST));
        }
    }

    /**
     * Rows of economy seats over business class, facing forward, either side of a three-wide aisle, each with its back
     * as high as a seated passenger's head; clear of the walls, where a walkway runs along the windows, and of either
     * end, where a cross aisle runs between the galleries' doorways, with legroom in front of each. Then a screen on
     * the back of each seat that has another right behind it, facing it.
     */
    private void rows() {
        BlockState seat = CabinSeatBlock.seat(AerolargeRegistry.ECONOMY_SEAT.get(), DyeColor.LIGHT_BLUE, Direction.NORTH);
        int across = Math.max(1, (int) Math.round(SEATS_ACROSS * layout.scale()));
        TreeMap<Integer, Set<Integer>> rows = new TreeMap<>();
        // A cross aisle at either end, onto the galleries' doorways through the bulkheads.
        for (int z = businessFrom + 1; z + 1 < businessTo; z += PITCH) {
            int outer = Math.min(room(z), room(z + 1));
            for (int x = AISLE + 1; x < AISLE + 1 + across && x < outer; x++) {
                for (int side : new int[]{1, -1}) {
                    int at = side * x;
                    // On the floor, with its legroom behind it open for the screen on its back.
                    if (!layout.isCabinFloor(at, deck, z) || !layout.isCabinFloor(at, deck, z + 1) || !layout.isOpen(at, deck + 1, z + 1)) {
                        continue;
                    }
                    if (layout.seat(at, deck + 1, z, seat)) {
                        rows.computeIfAbsent(z, ignored -> new TreeSet<>()).add(at);
                    }
                }
            }
        }
        BlockState screen = AerolargeRegistry.PASSENGER_SCREEN.get().defaultBlockState().setValue(PassengerScreenBlock.FACING, Direction.SOUTH);
        rows.forEach((z, seats) -> {
            Set<Integer> behind = rows.get(z + PITCH);
            for (int x : seats) {
                if (behind != null && behind.contains(x)) {
                    layout.furnish(x, deck + EYES, z + 1, screen);
                }
            }
        });
    }

    /** How far out from the middle the deck is a head's height clear at a station, to the nearer wall. */
    private int room(int z) {
        return Math.min(-layout.besideWall(-1, deck + STANDING_ROOM, z), layout.besideWall(1, deck + STANDING_ROOM, z));
    }

    /**
     * A green running man on the wall over the top of each staircase, just over head height, facing into the cabin:
     * the way down to the doors. At the head's own station, or the next one where a window takes its wall or anything
     * hangs on it under the sign.
     */
    private void exitSigns() {
        int y = deck + 1 + STANDING_ROOM;
        for (int[] head : stairHeads) {
            int side = head[1];
            for (int z : new int[]{head[0], head[0] + 1, head[0] - 1}) {
                int x = layout.besideWall(side, y, z);
                if (x != 0 && layout.isOpen(x, y, z) && layout.isPlainHull(x + side, y, z) && layout.isOpen(x, y - 1, z)) {
                    layout.set(x, y, z, AerolargeRegistry.EXIT_SIGN.get().defaultBlockState()
                        .setValue(ExitSignBlock.FACING, side > 0 ? Direction.WEST : Direction.EAST));
                    break;
                }
            }
        }
    }
}
