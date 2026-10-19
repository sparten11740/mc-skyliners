package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a cabin control works: the blocks it sets, as offsets from itself in the order they are reached front to back.
 * A new setting sweeps through them a few at a time, so it runs down the cabin like a wave rather than all at once,
 * and works the same in an airliner in flight, whose blocks keep their places relative to one another.
 */
public class CabinControlBlockEntity extends BlockEntity {
    /** Blocks set each tick of a sweep. */
    private static final int PER_TICK = 24;

    private List<BlockPos> targets = List.of();
    /** The next target a sweep sets; past the end when none is under way. */
    private int next = Integer.MAX_VALUE;

    public CabinControlBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.CABIN_CONTROL_ENTITY.get(), pos, state);
    }

    /** Hands the control the blocks it works, front to back. */
    public void configure(List<BlockPos> targets) {
        this.targets = List.copyOf(targets);
        setChanged();
    }

    /** Starts the control's setting on its way through the cabin. */
    public void sweep() {
        next = 0;
        setChanged();
    }

    void tick() {
        if (level == null || next >= targets.size() || !(getBlockState().getBlock() instanceof CabinControlBlock control)) {
            return;
        }
        for (int done = 0; done < PER_TICK && next < targets.size(); done++, next++) {
            BlockPos at = worldPosition.offset(targets.get(next));
            BlockState was = level.getBlockState(at);
            BlockState set = control.applyTo(getBlockState(), was);
            if (set != was) {
                level.setBlock(at, set, Block.UPDATE_CLIENTS);
            }
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Targets", Relays.write(targets));
        tag.putInt("Next", Math.min(next, targets.size()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        targets = Relays.read(tag.getList("Targets", Tag.TAG_INT_ARRAY));
        next = tag.getInt("Next");
    }
}
