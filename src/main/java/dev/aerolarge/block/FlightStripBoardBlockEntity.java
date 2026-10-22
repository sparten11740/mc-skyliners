package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds nothing: the board reads the transponders the client was last told of, as its renderer draws it. */
public class FlightStripBoardBlockEntity extends BlockEntity {
    public FlightStripBoardBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.FLIGHT_STRIP_BOARD_ENTITY.get(), pos, state);
    }
}
