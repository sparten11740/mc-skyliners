package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Where the drop bay the release works is, as an offset from the release, set when the airliner is built. */
public class DropReleaseBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos bay;
    /** Whether its link to the bay has been looked at since it was loaded, to mend it (see {@link Turning#mend}). */
    private boolean linkChecked;

    public DropReleaseBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.DROP_RELEASE_ENTITY.get(), pos, state);
    }

    public void configure(BlockPos bay) {
        this.bay = bay;
        linkChecked = true;
        setChanged();
    }

    /** The bay it works, if it is still there. */
    @Nullable
    DropBayBlockEntity bay() {
        if (bay == null || level == null) {
            return null;
        }
        mendLink();
        return level.getBlockEntity(worldPosition.offset(bay)) instanceof DropBayBlockEntity found ? found : null;
    }

    /** Once after loading, as the release is first worked, mends its link to the bay if it leads nowhere. */
    private void mendLink() {
        if (!linkChecked && level != null && bay != null) {
            linkChecked = true;
            Turning.mend(level, worldPosition, List.of(bay), state -> state.getBlock() instanceof DropBayBlock, this::turnLink);
        }
    }

    /** Turns the link to the bay, mending it. */
    private void turnLink(Rotation turn) {
        bay = Turning.turn(bay, turn);
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (bay != null) {
            tag.put("Bay", NbtUtils.writeBlockPos(bay));
        }
        Turning.save(tag, getBlockState());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        bay = Turning.turn(NbtUtils.readBlockPos(tag, "Bay").orElse(null), Turning.since(tag, getBlockState()));
    }
}
