package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds nothing: the display reads the airliner live, on the client, as its renderer draws it. */
public class FlightDisplayBlockEntity extends BlockEntity {
    public FlightDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.FLIGHT_DISPLAY_ENTITY.get(), pos, state);
    }
}
