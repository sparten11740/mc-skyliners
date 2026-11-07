package dev.skyliners.exterior;

import dev.skyliners.SkylinersRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A satellite dish's block entity (see {@link SatelliteDishBlock}): it keeps nothing, and is there so the client's
 * {@code SatelliteDishRenderer} draws the dish turning over its mount.
 */
public class SatelliteDishBlockEntity extends BlockEntity {
    public SatelliteDishBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.SATELLITE_DISH_ENTITY.get(), pos, state);
    }
}
