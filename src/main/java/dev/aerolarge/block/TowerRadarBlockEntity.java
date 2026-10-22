package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.radar.RadarSurvey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The tower's radar screen, for its renderer to draw the sweep and the transponders on (see {@link TowerRadarBlock}).
 * Loaded on the server, the land round it is surveyed for the players' maps (see {@link RadarSurvey}).
 */
public class TowerRadarBlockEntity extends BlockEntity {
    public TowerRadarBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.TOWER_RADAR_ENTITY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        RadarSurvey.RADARS.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        RadarSurvey.RADARS.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        RadarSurvey.RADARS.remove(this);
    }
}
