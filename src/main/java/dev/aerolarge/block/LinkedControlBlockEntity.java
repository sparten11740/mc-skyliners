package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The relays a flight deck control works, in named groups (the tiller's left and right, the parking brake's brakes),
 * as offsets from the control. What signal each group gets is up to the control's block, from its own state.
 */
public class LinkedControlBlockEntity extends BlockEntity implements Control {
    /** A block whose state says what each of its groups should get. */
    public interface Driver {
        /** The signal the group gets with the control in this state. */
        int signal(BlockState state, String group);
    }

    private Map<String, List<BlockPos>> groups = Map.of();

    public LinkedControlBlockEntity(BlockPos pos, BlockState state) {
        this(AerolargeRegistry.LINKED_CONTROL_ENTITY.get(), pos, state);
    }

    /** For a control that needs to be told apart from the others, with a type of its own. */
    protected LinkedControlBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void configure(Map<String, List<BlockPos>> groups) {
        this.groups = Map.copyOf(groups);
        setChanged();
        resync();
    }

    @Override
    public void resync() {
        if (level == null || !(getBlockState().getBlock() instanceof Driver driver)) {
            return;
        }
        groups.forEach((group, offsets) -> Relays.drive(level, worldPosition, offsets, driver.signal(getBlockState(), group)));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag saved = new CompoundTag();
        groups.forEach((group, offsets) -> saved.put(group, Relays.write(offsets)));
        tag.put("Groups", saved);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        CompoundTag saved = tag.getCompound("Groups");
        Map<String, List<BlockPos>> loaded = new HashMap<>();
        for (String group : saved.getAllKeys()) {
            loaded.put(group, Relays.read(saved.getList(group, Tag.TAG_INT_ARRAY)));
        }
        groups = Map.copyOf(loaded);
    }
}
