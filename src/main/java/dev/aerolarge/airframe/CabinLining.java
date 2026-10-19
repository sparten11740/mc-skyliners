package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.LightStripBlock;
import dev.aerolarge.block.OverheadBinBlock;
import java.util.function.DoublePredicate;
import net.minecraft.core.Direction;

/**
 * The cabin's lining, in both blueprints: a row of overhead lockers along each wall just above head height, and an
 * LED strip along each wall where it turns into the ceiling, from the flight deck's steps back to where the tail
 * closes in. Like the fit-out, it only ever goes into open cabin space. The lockers stop where the tail starts to
 * narrow, so they stay in one straight row along each wall, and stretches the fit-out wants the walls clear in (the
 * gym hall) get the light strips but no lockers.
 */
final class CabinLining {
    /** How high above the floor the lockers hang and the light strips run, in blocks. */
    private static final int LOCKERS = 5;
    private static final int STRIP = 6;
    /** Only where the cabin is at least this tall over the main deck. */
    private static final int MIN_HEADROOM = 8;
    /** Only where the cabin is at least this wide either side of the middle at the lockers' height. */
    private static final int MIN_HALF_WIDTH = 3;

    private final AirframeLayout layout;
    private final int floor;

    CabinLining(AirframeLayout layout) {
        this.layout = layout;
        this.floor = layout.floorY();
    }

    /**
     * Lines the cabin, with lockers wherever the test accepts the station, in metres from the nose. Not under the
     * raised flight deck or in the nose ahead of it, however tall the space there: it is shut away from the cabin.
     */
    void apply(DoublePredicate lockersAt, int fromZ, int toZ) {
        for (int z = Math.max(fromZ, lastUnderFlightDeck(toZ) + 1); z <= toZ; z++) {
            if (layout.ceiling(0, z) >= floor + MIN_HEADROOM) {
                line(z, lockersAt);
            }
        }
    }

    /** The last station up to this one where the main deck has the raised flight deck's floor over it; -1 if none. */
    private int lastUnderFlightDeck(int toZ) {
        int last = -1;
        for (int z = 0; z <= toZ; z++) {
            if (layout.isCabinFloor(0, layout.ceiling(0, z) + 1, z)) {
                last = z;
            }
        }
        return last;
    }

    /**
     * Lines both walls at one station, with lockers if the test accepts it. The fit-out lines the stations it closes
     * off down the middle itself, as {@link #apply} takes those for too low a cabin to line.
     */
    void line(int z, DoublePredicate lockersAt) {
        double metres = (z + 0.5) / layout.scale();
        boolean lockers = metres <= AirframeShape.TAIL_START && lockersAt.test(metres);
        for (int side : new int[]{1, -1}) {
            // Each wall is found from the hull in. Measured out from the middle, the starboard locker and strip just
            // put in the same row, or a light or rope hanging in the cabin, would be taken for the wall, and the
            // lining would stand off it in mid-air.
            int lockerX = layout.besideWall(side, floor + LOCKERS, z);
            if (Math.abs(lockerX) < MIN_HALF_WIDTH) {
                continue;
            }
            Direction wall = side > 0 ? Direction.EAST : Direction.WEST;
            if (lockers) {
                layout.furnish(lockerX, floor + LOCKERS, z,
                    AerolargeRegistry.OVERHEAD_BIN.get().defaultBlockState().setValue(OverheadBinBlock.FACING, wall.getOpposite()));
            }
            layout.furnish(layout.besideWall(side, floor + STRIP, z), floor + STRIP, z,
                AerolargeRegistry.LIGHT_STRIP.get().defaultBlockState().setValue(LightStripBlock.FACING, wall));
        }
    }
}
