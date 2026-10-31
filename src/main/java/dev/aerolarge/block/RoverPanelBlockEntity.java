package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Holds nothing: the rover panel's screen is drawn live from its block state, its lamp's colour, and from where the
 * player looks (see {@link RoverPanelBlock}).
 */
public class RoverPanelBlockEntity extends BlockEntity {
    public RoverPanelBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.ROVER_PANEL_ENTITY.get(), pos, state);
    }
}
