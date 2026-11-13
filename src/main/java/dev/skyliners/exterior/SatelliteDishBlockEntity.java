package dev.skyliners.exterior;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.Loaded;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A satellite dish's block entity (see {@link SatelliteDishBlock}): it keeps nothing, and is there so the client's
 * {@code SatelliteDishRenderer} draws the dish turning over its mount, and so a ship's collisions know where a dish
 * stands whose bowl reaches out past its block.
 */
public class SatelliteDishBlockEntity extends BlockEntity {
    /**
     * How far past its block a dish's bowl and feed horn reach at most, however it is turned, in whole blocks: so far
     * round a player a ship's collisions look for one (see {@link #reaches}).
     */
    public static final int REACH_BLOCKS = 2;
    private static final Loaded<SatelliteDishBlockEntity> LOADED = new Loaded<>(SatelliteDishBlockEntity.class, true);

    public SatelliteDishBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.SATELLITE_DISH_ENTITY.get(), pos, state);
    }

    /**
     * Whether a dish loaded in a level stands near enough a box of blocks, its lowest and highest corners given in the
     * level's or a ship's plot's blocks, for its bowl to reach into it: for a ship's collisions, which otherwise look
     * only at the blocks a player stands in (see {@code SubLevelEntityCollisionMixin}).
     */
    public static boolean reaches(Level level, BlockPos low, BlockPos high) {
        return LOADED.anyNear(level, low, high, REACH_BLOCKS);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED.remove(this);
    }
}
