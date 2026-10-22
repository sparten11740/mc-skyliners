package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds nothing: the sock reads the wind live, on the client, as its renderer draws it. */
public class WindSockBlockEntity extends BlockEntity {
    public WindSockBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.WIND_SOCK_ENTITY.get(), pos, state);
    }
}
