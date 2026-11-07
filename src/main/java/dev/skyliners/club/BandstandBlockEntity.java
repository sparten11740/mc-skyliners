package dev.skyliners.club;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.Loaded;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The bandstand's block entity (see {@link BandstandBlock}): it keeps nothing, but is kept track of on the clients, so
 * each finds the band nearest its player to play its song (see the client's {@code ClubBand}) and draws the players
 * swaying to it (see the client's {@code BandstandRenderer}).
 */
public class BandstandBlockEntity extends BlockEntity {
    private static final Loaded<BandstandBlockEntity> LOADED = new Loaded<>(BandstandBlockEntity.class, true);

    public BandstandBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.BANDSTAND_ENTITY.get(), pos, state);
    }

    /** The bandstands loaded in a level, on the server or a client. */
    public static List<BandstandBlockEntity> loaded(Level level) {
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
