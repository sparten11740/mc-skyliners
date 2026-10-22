package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The tower's radar screen, for its renderer to draw the sweep and the transponders on (see {@link TowerRadarBlock}). */
public class TowerRadarBlockEntity extends BlockEntity {
    public TowerRadarBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.TOWER_RADAR_ENTITY.get(), pos, state);
    }
}
