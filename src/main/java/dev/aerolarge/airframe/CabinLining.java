package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.LightStripBlock;
import net.minecraft.core.Direction;

/**
 * The cabin's lining, in both blueprints: an LED strip along each wall where it turns into the ceiling, from the
 * flight deck's steps back to where the tail closes in. Like the fit-out, it only ever goes into open cabin space.
 */
final class CabinLining {
    /** How high above the floor the light strips run, in blocks. */
    private static final int STRIP = 6;
    /** Only where the cabin is at least this tall, so not under the raised flight deck. */
    private static final int MIN_HEADROOM = 8;
    /** Only where the cabin is at least this wide either side of the middle a block under the strips. */
    private static final int MIN_HALF_WIDTH = 3;

    private final AirframeLayout layout;
    private final int floor;

    CabinLining(AirframeLayout layout) {
        this.layout = layout;
        this.floor = layout.floorY();
    }

    /** Lines the cabin between the two stations, in blocks from the nose. */
    void apply(int fromZ, int toZ) {
        for (int z = fromZ; z <= toZ; z++) {
            if (layout.ceiling(0, z) < floor + MIN_HEADROOM || layout.openHalfWidth(floor + STRIP - 1, z) < MIN_HALF_WIDTH) {
                continue;
            }
            for (int side : new int[]{1, -1}) {
                Direction wall = side > 0 ? Direction.EAST : Direction.WEST;
                int x = side * layout.openHalfWidth(floor + STRIP, z);
                layout.furnish(x, floor + STRIP, z, AerolargeRegistry.LIGHT_STRIP.get().defaultBlockState().setValue(LightStripBlock.FACING, wall));
            }
        }
    }
}
