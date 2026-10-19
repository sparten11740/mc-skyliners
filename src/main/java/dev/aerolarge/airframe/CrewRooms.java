package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.CabinLightBlock;
import dev.aerolarge.block.VacuumToiletBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The WC and the crew room behind the flight deck, in both blueprints: on its raised floor between the cockpit door
 * and the steps down into the cabin, the WC to port and the crew room to starboard of the aisle that joins the two.
 * Each is walled off from the aisle and the cabin with the hull's own panels, up to the crown, under a ceiling of
 * them at a room's height for a player with a cabin light set into it, kept on at night as a lavatory's is, and has a
 * door onto the aisle at its back.
 * The WC has a toilet and a basin with its tap; the crew room a couch of seats along the hull and a small galley
 * counter: an oven, and a cupboard with a coffee machine on it. Walls and furniture only go into open space.
 */
final class CrewRooms {
    /** The aisle reaches this far either side of the middle; the rooms' inner walls stand just beyond it. */
    private static final int AISLE = 1;
    /** How many blocks of headroom the rooms have under their ceilings. */
    private static final int HEADROOM = 3;
    /** The least a room is inside, across and along, in blocks, to be built at all. */
    private static final int LEAST = 2;

    private final AirframeLayout layout;
    private final int deck;
    private final int front;
    private final int back;
    private final BlockState wall;
    private final BlockState light;

    /**
     * The rooms on the raised floor at row deck, from station front (just behind the cockpit door) back to station
     * back, the row their back walls stand on at the floor's back edge.
     */
    CrewRooms(AirframeLayout layout, int deck, int front, int back) {
        this.layout = layout;
        this.deck = deck;
        this.front = front;
        this.back = back;
        this.wall = layout.palette().hull();
        this.light = CabinLightBlock.keptOn(layout.palette().light());
    }

    void apply() {
        int inner = AISLE + 2;
        // Measured before any wall goes up, while the space either side of the middle is still open right across.
        int outer = narrowest(deck + 1, front, back - 1);
        int couch = narrowest(deck + 2, front, back - 2);
        int counter = layout.openHalfWidth(deck + 1, back - 1);
        if (back - front < LEAST || outer - inner + 1 < LEAST) {
            return;
        }
        walls(-1, inner, outer);
        walls(1, inner, outer);
        washroom(-1, inner, outer);
        crewRoom(1, inner, couch, counter);
    }

    /** How far out from the middle the cabin is open at row y all the way from one station to another. */
    private int narrowest(int y, int fromZ, int toZ) {
        int width = Integer.MAX_VALUE;
        for (int z = fromZ; z <= toZ; z++) {
            width = Math.min(width, layout.openHalfWidth(y, z));
        }
        return width;
    }

    /**
     * A room's walls: along the aisle and across the floor's back edge, up to the crown, and its ceiling, with a
     * cabin light in the middle of it; the door through the wall onto the aisle at the room's back.
     */
    private void walls(int side, int inner, int outer) {
        int aisleWall = side * (AISLE + 1);
        int ceiling = deck + HEADROOM + 1;
        int lightX = side * (inner + outer) / 2;
        int lightZ = (front + back - 1) / 2;
        // The door and the light only go where the walls themselves stand, never into the skin.
        boolean doorway = layout.isOpen(aisleWall, deck + 1, back - 1) && layout.isOpen(aisleWall, deck + 2, back - 1);
        boolean lit = layout.isOpen(lightX, ceiling, lightZ);
        for (int z = front; z <= back; z++) {
            column(aisleWall, z);
        }
        for (int x = aisleWall + side; layout.isOpen(x, deck + 1, back); x += side) {
            column(x, back);
        }
        for (int z = front; z < back; z++) {
            for (int x = side * inner; layout.isOpen(x, ceiling, z); x += side) {
                layout.set(x, ceiling, z, wall);
            }
        }
        if (lit) {
            layout.set(lightX, ceiling, lightZ, light);
        }
        if (doorway) {
            // Facing out of the room onto the aisle, so it closes flush with the room's side of the wall.
            BlockState door = AerolargeRegistry.AIRLINER_DOOR.get().defaultBlockState()
                .setValue(DoorBlock.FACING, side > 0 ? Direction.WEST : Direction.EAST)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
            layout.set(aisleWall, deck + 1, back - 1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            layout.set(aisleWall, deck + 2, back - 1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        }
    }

    /** Walls up a column from the raised floor to the crown. */
    private void column(int x, int z) {
        for (int y = deck + 1; layout.isOpen(x, y, z); y++) {
            layout.set(x, y, z, wall);
        }
    }

    /**
     * The WC: the vacuum toilet in the front corner against the hull, its back to the bulkhead, and the basin in the
     * front corner by the aisle, its tap on the bulkhead over it.
     */
    private void washroom(int side, int inner, int outer) {
        layout.furnish(side * outer, deck + 1, front,
            AerolargeRegistry.VACUUM_TOILET.get().defaultBlockState().setValue(VacuumToiletBlock.FACING, Direction.SOUTH));
        int basin = side * inner;
        layout.furnish(basin, deck + 1, front,
            Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
        layout.furnish(basin, deck + 2, front, Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(TripWireHookBlock.FACING, Direction.SOUTH));
    }

    /**
     * The crew room: a couch of seats along the hull, at {@code couch} out from the middle so a sitter's head is
     * clear of its curve, and the galley counter across the room's back beside it, reaching in from the hull at
     * {@code counter}: the oven against the hull, its door facing into the room, the cupboard with the coffee machine
     * inboard of it, both clear of the door.
     */
    private void crewRoom(int side, int inner, int couch, int counter) {
        BlockState seat = AirframePalette.seat(DyeColor.LIGHT_GRAY).orElse(Blocks.LIGHT_GRAY_WOOL.defaultBlockState());
        for (int z = front; couch >= inner && z < back - 1; z++) {
            layout.furnish(side * couch, deck + 1, z, seat);
        }
        if (counter > inner) {
            layout.furnish(side * counter, deck + 1, back - 1,
                Blocks.SMOKER.defaultBlockState().setValue(AbstractFurnaceBlock.FACING, Direction.NORTH));
        }
        if (counter - 1 > inner) {
            layout.furnish(side * (counter - 1), deck + 1, back - 1, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
            layout.furnish(side * (counter - 1), deck + 2, back - 1, Blocks.BREWING_STAND.defaultBlockState());
        }
    }
}
