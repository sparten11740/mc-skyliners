package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Where the drop bay the release works is, as an offset from the release, set when the airliner is built. */
public class DropReleaseBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos bay;

    public DropReleaseBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.DROP_RELEASE_ENTITY.get(), pos, state);
    }

    public void configure(BlockPos bay) {
        this.bay = bay;
        setChanged();
    }

    /** The bay it works, if it is still there. */
    @Nullable
    DropBayBlockEntity bay() {
        return bay != null && level != null && level.getBlockEntity(worldPosition.offset(bay)) instanceof DropBayBlockEntity found ? found : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (bay != null) {
            tag.put("Bay", NbtUtils.writeBlockPos(bay));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        bay = NbtUtils.readBlockPos(tag, "Bay").orElse(null);
    }
}
