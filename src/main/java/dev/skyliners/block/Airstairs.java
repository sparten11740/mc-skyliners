package dev.skyliners.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * A flight of integral airstairs, an airliner's out of its door (see {@link AirstairBlockEntity}), a zeppelin's out of
 * her belly or a rocket's out of her door (see {@link BoardingAirstairs}), as its renderer draws it and its steps find
 * who laid them (see {@link AirstairStepBlock}).
 */
public interface Airstairs {
    /**
     * The frame the flight is drawn in (see {@link AirstairFlight}): the block whose bottom is the sill, the way out
     * from it, and the way to the right as seen from outside.
     */
    record Frame(BlockPos at, Direction out, Direction right) {
    }

    /** The flight's frame, where it is; null while it has none, as with its door gone. */
    @Nullable
    Frame frame();

    /** Where the flight is between the last tick and this one, to draw it; null while there is nothing to draw. */
    @Nullable
    AirstairFlight.Motion motion(float partialTick);

    /** Whether a block is one of the steps laid under the flight. */
    boolean holds(Level level, BlockPos pos);
}
