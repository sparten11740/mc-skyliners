package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A towed banner's cleat's block entity (see {@link BannerCleatBlock}): nothing but a way for each client to find the
 * zeppelins whose banners stream, kept track of there.
 */
public class BannerCleatBlockEntity extends BlockEntity {
    private static final Loaded<BannerCleatBlockEntity> LOADED = new Loaded<>(BannerCleatBlockEntity.class, true);

    public BannerCleatBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.BANNER_CLEAT_ENTITY.get(), pos, state);
    }

    /** The cleats loaded in a level, on the server or on this client. */
    public static List<BannerCleatBlockEntity> loaded(Level level) {
        return LOADED.in(level);
    }

    /** Whether its banner streams. */
    public boolean streaming() {
        return getBlockState().getValue(BannerCleatBlock.STREAMING);
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
