package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lights over the cockpit (see {@link dev.aerolarge.block.CabinLightBlock}): six flush panels set into the ring of
 * ceiling over her glass, evenly round it but for dead ahead and dead behind, each in the middle of the ring's width,
 * so the cockpit is lit at night as her rooms are with nothing hanging across the pilots' view out of the glass or up
 * into the dome.
 */
final class RocketCockpitLights implements RocketPiece {
    /** How far round from her front each light is, in degrees: six, evenly round, none dead ahead or behind. */
    private static final double[] ROUNDS = {30, 90, 150, 210, 270, 330};
    /** How far out a step of the look along the ring of ceiling goes, in blocks. */
    private static final double STEP = 0.5;
    private static final BlockState LAMP = AerolargeRegistry.CABIN_LIGHT.get().defaultBlockState();

    private final RocketLayout layout;

    RocketCockpitLights(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public void build() {
        for (double round : ROUNDS) {
            List<BlockPos> ring = ringAlong(round);
            if (!ring.isEmpty()) {
                BlockPos middle = ring.get(ring.size() / 2);
                layout.set(middle.getX(), middle.getY(), middle.getZ(), LAMP);
            }
        }
    }

    /** The blocks of the ring of ceiling going out from her axis so far round from her front, from its inner edge out. */
    private List<BlockPos> ringAlong(double round) {
        List<BlockPos> ring = new ArrayList<>();
        int y = layout.domeFloor();
        double across = Math.sin(Math.toRadians(round));
        double along = -Math.cos(Math.toRadians(round));
        for (double out = 0; out <= layout.reach(); out += STEP) {
            BlockPos at = new BlockPos((int) Math.round(out * across), y, (int) Math.round(out * along));
            if (!ring.contains(at) && layout.stateAt(at.getX(), y, at.getZ()).is(AerolargeRegistry.CABIN_FLOOR.get())) {
                ring.add(at);
            }
        }
        return ring;
    }
}
