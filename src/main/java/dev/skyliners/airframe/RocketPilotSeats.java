package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.PilotSeatBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The airliner's pilot seats in the rocket's cockpit, where her flight deck's plan puts them (see
 * {@link RocketFlightDeck}): the commander's on the left of the control head and the pilot's on its right, facing her
 * front with a sidestick at each outboard hand, every instrument within reach of whoever sits in them, to sit through
 * the countdown and the climb; jump seats behind them for the crew; and a row of launch seats at her back glass looking
 * out, without sticks. Only where both halves of a seat are free; the server config's {@code rocket.rooms} leaves them
 * out with her rooms.
 */
final class RocketPilotSeats implements RocketPiece {
    private final RocketLayout layout;

    RocketPilotSeats(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted() {
        return SkylinersConfig.ROCKET_ROOMS.get();
    }

    @Override
    public void build() {
        layout.flightDeck().seats().forEach(this::seat);
    }

    private void seat(RocketFlightDeck.Seat planned) {
        BlockPos at = planned.at();
        if (!layout.isOpen(at.getX(), at.getY(), at.getZ()) || !layout.isOpen(at.getX(), at.getY() + 1, at.getZ())) {
            return;
        }
        BlockState seat = SkylinersRegistry.PILOT_SEAT.get().defaultBlockState().setValue(PilotSeatBlock.FACING, planned.facing())
            .setValue(PilotSeatBlock.STICK, planned.stick());
        layout.set(at.getX(), at.getY(), at.getZ(), seat);
        layout.set(at.getX(), at.getY() + 1, at.getZ(), seat.setValue(PilotSeatBlock.HALF, DoubleBlockHalf.UPPER));
    }
}
