package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds nothing: the screen reads the airliner live, on the client, as its renderer draws it. */
public class PassengerScreenBlockEntity extends BlockEntity {
    public PassengerScreenBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.PASSENGER_SCREEN_ENTITY.get(), pos, state);
    }
}
