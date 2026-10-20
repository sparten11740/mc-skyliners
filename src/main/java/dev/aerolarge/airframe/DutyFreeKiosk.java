package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.AwningBlock;
import dev.aerolarge.block.CabinCarpetBlock;
import dev.aerolarge.block.DisplayShelfBlock;
import dev.aerolarge.block.DutyFreeCounterBlock;
import dev.aerolarge.cabin.Decompression;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;

/**
 * The furnished airliner's duty-free kiosk, at the front of the lounge on the port side, across from the galley and the
 * bar: a row of duty-free counters facing the aisle under a lime and white striped awning hung on steel rods, and
 * behind them against the wall the duty-free display's cabinets, a lit showcase of perfumes and chocolates on them
 * wherever the wall behind has no window in it. It stands as near the front as it can while a shopper at its counters
 * is out of reach of the air rushing out of an open aft door, and it is left out where the cabin is too narrow for it
 * beside a clear aisle. It is put in before the lounge is furnished, so the lounge's seats and tables fit round it.
 */
final class DutyFreeKiosk {
    /** How long the kiosk is along the cabin, in metres, and the fewest stations it can make do with. */
    private static final double LENGTH = 2.0;
    private static final int FEWEST_STATIONS = 3;
    /** How far either side of the centreline the aisle stays clear, in metres: room for the trolley and to pass it. */
    private static final double AISLE = 0.5;
    /** How much further than decompression's reach from the middle of an aft doorway a shopper stands, in metres. */
    private static final double DOOR_CLEARANCE = 0.5;
    /** How high over the floor the awning hangs, in blocks: over the counters and the shoppers, clear of their heads. */
    private static final int AWNING = 4;

    private final AirframeLayout layout;
    private final int floor;

    DutyFreeKiosk(AirframeLayout layout) {
        this.layout = layout;
        this.floor = layout.floorY();
    }

    /**
     * Fits the kiosk into the lounge, which runs between these stations, from the first station it fits at back for as
     * long as it is meant to be, or as far as the wall runs straight; if it fits anywhere for long enough.
     */
    void apply(int loungeFrom, int loungeTo) {
        double scale = layout.scale();
        int length = Math.max(FEWEST_STATIONS, (int) Math.round(LENGTH * scale));
        int aisle = (int) Math.round(AISLE * scale);
        double clearance = (Decompression.REACH_PER_SCALE + DOOR_CLEARANCE) * scale;
        List<Vec2> doorways = aftDoorways();
        // The lounge's first and last stations are its own: its plant and galley stand at the front.
        for (int from = loungeFrom + 1; from + FEWEST_STATIONS <= loungeTo; from++) {
            int wall = layout.besideWall(-1, floor + 1, from);
            int stations = 0;
            while (stations < length && from + stations < loungeTo && fits(wall, from + stations, aisle, clearance, doorways)) {
                stations++;
            }
            if (stations >= FEWEST_STATIONS) {
                build(wall, from, stations);
                return;
            }
        }
    }

    /**
     * The middles of the aft doors' doorways, (x, z) in the layout, where decompression's pull is measured from: across
     * each door, in the hull's side at the main deck floor.
     */
    private List<Vec2> aftDoorways() {
        int fore = (int) Math.round(AirframeShape.PASSENGER_DOORS[AirframeShape.PASSENGER_DOORS.length - 1] * layout.scale());
        return layout.hullDoors().stream()
            .filter(door -> door.fore() == fore && door.bottom() == floor + 1)
            .map(AirframeLayout.HullDoor::middle)
            .toList();
    }

    /**
     * Whether the kiosk fits at this station against the port wall, whose cabin side is at x = wall: the wall there
     * too, the shelf, the counter and the spot its shopper stands on all open and on the floor (by the wall, where the
     * hull curves in under it, the floor's edge is hull), the shopper clear of the aisle and out of the open doors' reach.
     */
    private boolean fits(int wall, int z, int aisle, double clearance, List<Vec2> doorways) {
        int shoppers = wall + 2;
        if (wall >= 0 || shoppers > -aisle - 1 || layout.besideWall(-1, floor + 1, z) != wall) {
            return false;
        }
        for (int x = wall; x <= shoppers; x++) {
            if (layout.stateAt(x, floor, z).isAir() || !layout.isOpen(x, floor + 1, z) || !layout.isOpen(x, floor + 2, z)) {
                return false;
            }
        }
        for (Vec2 doorway : doorways) {
            if (Math.hypot(shoppers + 0.5 - doorway.x, z + 0.5 - doorway.y) < clearance) {
                return false;
            }
        }
        return true;
    }

    /**
     * The display against the wall, its showcase on it where the wall right behind has no window in it, the counters
     * in front facing the aisle, the shoppers' spots carpeted like the rest of the lounge so nothing else goes there,
     * and over the counters and the shoppers the awning, its valance over the shoppers, hung from the crown on a steel
     * rod at each end.
     */
    private void build(int wall, int from, int length) {
        BlockState counter = AerolargeRegistry.DUTY_FREE_COUNTER.get().defaultBlockState().setValue(DutyFreeCounterBlock.FACING, Direction.EAST);
        BlockState display = AerolargeRegistry.DUTY_FREE_DISPLAY.get().defaultBlockState().setValue(DisplayShelfBlock.FACING, Direction.EAST);
        BlockState canopy = AerolargeRegistry.AWNING.get().defaultBlockState().setValue(AwningBlock.FACING, Direction.EAST);
        BlockState rod = canopy.setValue(AwningBlock.PART, AwningBlock.Part.ROD);
        int counters = wall + 1;
        int shoppers = wall + 2;
        for (int i = 0; i < length; i++) {
            int z = from + i;
            layout.furnish(wall, floor + 1, z, display);
            if (layout.besideWall(-1, floor + 2, z) == wall && !layout.besideWindow(wall, floor + 2, z)) {
                layout.furnish(wall, floor + 2, z, display.setValue(DisplayShelfBlock.TIER, DisplayShelfBlock.Tier.SHELVES));
            }
            layout.furnish(counters, floor + 1, z, counter);
            layout.furnish(shoppers, floor + 1, z, CabinCarpetBlock.carpet(AerolargeRegistry.CABIN_CARPET.get(), DyeColor.RED));
            layout.furnish(counters, floor + AWNING, z, canopy.setValue(AwningBlock.VALANCE, false));
            layout.furnish(shoppers, floor + AWNING, z, canopy);
        }
        for (int z : new int[]{from, from + length - 1}) {
            // Up to the crown, stopping short of the light strip along the wall wherever the hull closes in on the rod.
            for (int y = floor + AWNING + 1; layout.isOpen(shoppers, y, z) && layout.besideWall(-1, y, z) != shoppers; y++) {
                layout.furnish(shoppers, y, z, rod);
            }
        }
    }
}
