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
    /** Only where the cabin is at least this tall, so not under the raised flight deck. */
    private static final int MIN_HEADROOM = 8;
    /** Only where the cabin is at least this wide either side of the middle at the lockers' height. */
    private static final int MIN_HALF_WIDTH = 3;

    private final AirframeLayout layout;
    private final int floor;

    CabinLining(AirframeLayout layout) {
        this.layout = layout;
        this.floor = layout.floorY();
    }

    /** Lines the cabin, with lockers wherever the test accepts the station, in metres from the nose. */
    void apply(DoublePredicate lockersAt, int fromZ, int toZ) {
        for (int z = fromZ; z <= toZ; z++) {
            if (layout.ceiling(0, z) < floor + MIN_HEADROOM || layout.openHalfWidth(floor + LOCKERS, z) < MIN_HALF_WIDTH) {
                continue;
            }
            double metres = (z + 0.5) / layout.scale();
            boolean lockers = metres <= AirframeShape.TAIL_START && lockersAt.test(metres);
            for (int side : new int[]{1, -1}) {
                Direction wall = side > 0 ? Direction.EAST : Direction.WEST;
                if (lockers) {
                    int x = side * layout.openHalfWidth(floor + LOCKERS, z);
                    layout.furnish(x, floor + LOCKERS, z,
                        AerolargeRegistry.OVERHEAD_BIN.get().defaultBlockState().setValue(OverheadBinBlock.FACING, wall.getOpposite()));
                }
                int x = side * layout.openHalfWidth(floor + STRIP, z);
                layout.furnish(x, floor + STRIP, z, AerolargeRegistry.LIGHT_STRIP.get().defaultBlockState().setValue(LightStripBlock.FACING, wall));
            }
        }
    }
}
