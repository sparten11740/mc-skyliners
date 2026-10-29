package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.PilotSeatBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Two of the airliner's pilot seats in the rocket's cockpit, behind her control head either side of the way out of
 * the lift, facing her front: the commander's on the left and the pilot's on the right, each with its sidestick at the
 * outboard hand, to sit through the countdown and the climb looking out over the head and its displays. Only where
 * both halves of a seat are free; the server config's {@code rocket.rooms} leaves them out with her rooms.
 */
final class RocketPilotSeats implements RocketPiece {
    /** How far behind the control head the seats stand, and how far either side of her axis, in blocks. */
    private static final int BEHIND = 2;
    private static final int APART = 3;

    private final RocketLayout layout;

    RocketPilotSeats(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted() {
        return AerolargeConfig.ROCKET_ROOMS.get();
    }

    @Override
    public void build() {
        BlockPos head = layout.autopilot();
        if (head == null) {
            return;
        }
        for (int side = -1; side <= 1; side += 2) {
            seat(side * APART, head.getY(), head.getZ() + BEHIND, side < 0 ? PilotSeatBlock.Stick.LEFT : PilotSeatBlock.Stick.RIGHT);
        }
    }

    private void seat(int x, int y, int z, PilotSeatBlock.Stick stick) {
        if (!layout.isOpen(x, y, z) || !layout.isOpen(x, y + 1, z) || !layout.inside(x, y + 1, z)) {
            return;
        }
        BlockState seat = AerolargeRegistry.PILOT_SEAT.get().defaultBlockState().setValue(PilotSeatBlock.FACING, Direction.NORTH)
            .setValue(PilotSeatBlock.STICK, stick);
        layout.set(x, y, z, seat);
        layout.set(x, y + 1, z, seat.setValue(PilotSeatBlock.HALF, DoubleBlockHalf.UPPER));
    }
}
