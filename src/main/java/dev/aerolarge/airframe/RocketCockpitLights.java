package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Halo lights over the cockpit (see {@link dev.aerolarge.block.HaloLightBlock}): four warm ones at the diagonals, each
 * hanging on its cable from the inner edge of the ring of ceiling over her glass, so the cockpit is lit at night as her
 * rooms are, out of the way of the pilots' view ahead and the lift's booth.
 */
final class RocketCockpitLights implements RocketPiece {
    /** How far round from her front each light hangs, in degrees: the four diagonals. */
    private static final double[] ROUNDS = {45, 135, 225, 315};
    /** How far out from her axis the search for the ceiling's inner edge starts, in blocks: past the lift's booth. */
    private static final double FROM = 4.5;
    /** How far out a step of the search goes, in blocks. */
    private static final double STEP = 0.5;
    /** How many rows over the cockpit's floor a light hangs: well over the head of anyone standing. */
    private static final int LAMP_UP = 5;
    private static final BlockState LAMP = AerolargeRegistry.HALO_LIGHT.get().defaultBlockState();
    private static final BlockState CABLE = AerolargeRegistry.LIGHT_CABLE.get().defaultBlockState();

    private final RocketLayout layout;

    RocketCockpitLights(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public void build() {
        for (double round : ROUNDS) {
            hang(round);
        }
    }

    /**
     * A light hanging so far round from her front, under the first block of the ceiling going out from her axis, on a
     * cable down from it; none where the room under it is not open all the way down to the light.
     */
    private void hang(double round) {
        int ceiling = layout.domeFloor();
        int lowest = layout.cockpitFloor() + LAMP_UP;
        for (double out = FROM; out <= layout.reach(); out += STEP) {
            int x = (int) Math.round(out * Math.sin(Math.toRadians(round)));
            int z = (int) Math.round(-out * Math.cos(Math.toRadians(round)));
            if (!layout.isOpen(x, ceiling, z)) {
                hangUnder(x, z, lowest, ceiling);
                return;
            }
        }
    }

    /** The light and its cable under a block of the ceiling, where every block of them is open. */
    private void hangUnder(int x, int z, int lowest, int ceiling) {
        for (int y = lowest; y < ceiling; y++) {
            if (!layout.isOpen(x, y, z)) {
                return;
            }
        }
        layout.set(x, lowest, z, LAMP);
        for (int y = lowest + 1; y < ceiling; y++) {
            layout.set(x, y, z, CABLE);
        }
    }
}
