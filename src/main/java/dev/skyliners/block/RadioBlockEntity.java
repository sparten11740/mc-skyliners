package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.compat.SableFlight;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A radio: the flight deck's handset or a tower radio at the airfield. It holds nothing; it is kept track of while
 * loaded, so a call finds every radio in range of it, in airliners too, wherever they fly.
 */
public class RadioBlockEntity extends BlockEntity {
    private static final Loaded<RadioBlockEntity> LOADED = new Loaded<>(RadioBlockEntity.class);

    public RadioBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.RADIO_ENTITY.get(), pos, state);
    }

    /** Every radio loaded in the level. */
    public static List<RadioBlockEntity> all(Level level) {
        return LOADED.in(level);
    }

    /** Where the radio is in the world, aboard an assembled airliner as well. */
    public Vec3 whereInWorld() {
        Vec3 centre = Vec3.atCenterOf(worldPosition);
        return level == null ? centre : SableFlight.toWorld(level, centre);
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
