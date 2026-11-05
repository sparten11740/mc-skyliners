package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.block.Mount;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The self-destruct button in the rocket's cockpit, the airliner's own (see {@link SelfDestructFitting}): on its
 * pedestal, as her curved glass leaves no wall to hang it on, on the commander's side just behind the LED console (see
 * {@link RocketFlightDeck#selfDestruct}), a few steps from either seat as it is on the airliner. Its plate tilts back
 * toward whoever comes forward from the lift, the jump seat beside it standing in the way of any view from inboard.
 * Only where that wing and the spot behind it fit; the server config's {@code selfDestruct} leaves it out, as it does
 * on the airliner.
 */
final class RocketSelfDestruct implements RocketPiece {
    private final RocketLayout layout;

    RocketSelfDestruct(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted() {
        return SkylinersConfig.SELF_DESTRUCT.get();
    }

    @Override
    public void build() {
        BlockPos head = layout.autopilot();
        BlockPos at = layout.flightDeck().selfDestruct();
        if (head == null || at == null || !layout.isOpen(at.getX(), at.getY(), at.getZ()) || !layout.inside(at.getX(), at.getY(), at.getZ())) {
            return;
        }
        // Nose north, whoever comes forward to it faces north.
        layout.set(at.getX(), at.getY(), at.getZ(), SelfDestructFitting.button(Mount.FLOOR, Direction.NORTH));
        layout.fit(at, SelfDestructFitting.linkedTo(at, head));
    }
}
