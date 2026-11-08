package dev.skyliners.beam;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.Loaded;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A beam pad's block entity (see {@link BeamPadBlock}): it holds nothing, but keeps track of the pad as loaded, so a
 * tractor beam switched on finds the pad nearest it without searching the blocks round it.
 */
public class BeamPadBlockEntity extends BlockEntity {
    private static final Loaded<BeamPadBlockEntity> LOADED = new Loaded<>(BeamPadBlockEntity.class);

    public BeamPadBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.BEAM_PAD_ENTITY.get(), pos, state);
    }

    /** The beam pads loaded in a level, on the server. */
    public static List<BeamPadBlockEntity> loaded(Level level) {
        return LOADED.in(level);
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
