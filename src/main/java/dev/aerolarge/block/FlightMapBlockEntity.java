package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds nothing: the flight map reads the airliner live, on the client, as its renderer draws it. */
public class FlightMapBlockEntity extends BlockEntity {
    public FlightMapBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.FLIGHT_MAP_ENTITY.get(), pos, state);
    }
}
