package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.DutyFreeCounterBlock;
import dev.aerolarge.cabin.Decompression;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;

/**
 * The furnished airliner's duty-free kiosk, at the front of the lounge on the port side, across from the galley and the
 * bar: a row of duty-free counters facing the aisle under a lime and white striped awning hung on chains, and behind
 * them against the wall a shelf of goods on show, flowers and coloured candles like a shop's perfumes. It stands as
 * near the front as it can while a shopper at its counters is out of reach of the air rushing out of an open aft door,
 * and it is left out where the cabin is too narrow for it beside a clear aisle. It is put in before the lounge is
 * furnished, so the lounge's seats and tables fit round it.
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
    /** The goods on the shelf, in turn along it. */
    private static final List<BlockState> GOODS = List.of(
        Blocks.POTTED_RED_TULIP.defaultBlockState(),
        Blocks.MAGENTA_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3),
        Blocks.POTTED_BLUE_ORCHID.defaultBlockState(),
        Blocks.LIGHT_BLUE_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 4),
        Blocks.POTTED_ALLIUM.defaultBlockState(),
        Blocks.LIME_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 2)
    );

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
     * The shelf against the wall with its goods, the counters in front facing the aisle, the shoppers' spots carpeted
     * like the rest of the lounge so nothing else goes there, and over the counters and the shoppers the awning, hung
     * from the crown on a chain at each end.
     */
    private void build(int wall, int from, int length) {
        BlockState counter = AerolargeRegistry.DUTY_FREE_COUNTER.get().defaultBlockState().setValue(DutyFreeCounterBlock.FACING, Direction.EAST);
        int counters = wall + 1;
        int shoppers = wall + 2;
        for (int i = 0; i < length; i++) {
            int z = from + i;
            layout.furnish(wall, floor + 1, z, Blocks.SMOOTH_QUARTZ.defaultBlockState());
            layout.furnish(wall, floor + 2, z, GOODS.get(i % GOODS.size()));
            layout.furnish(counters, floor + 1, z, counter);
            layout.furnish(shoppers, floor + 1, z, Blocks.RED_CARPET.defaultBlockState());
            BlockState stripe = (z % 2 == 0 ? Blocks.LIME_WOOL : Blocks.WHITE_WOOL).defaultBlockState();
            layout.furnish(counters, floor + AWNING, z, stripe);
            layout.furnish(shoppers, floor + AWNING, z, stripe);
        }
        for (int z : new int[]{from, from + length - 1}) {
            // Up to the crown, stopping short of the light strip along the wall wherever the hull closes in on the chain.
            for (int y = floor + AWNING + 1; layout.isOpen(shoppers, y, z) && layout.besideWall(-1, y, z) != shoppers; y++) {
                layout.furnish(shoppers, y, z, Blocks.CHAIN.defaultBlockState());
            }
        }
    }
}
