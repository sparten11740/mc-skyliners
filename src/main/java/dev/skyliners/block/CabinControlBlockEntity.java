package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a cabin control works: the blocks it sets, as offsets from itself in the order they are reached front to back.
 * A new setting sweeps through them a few at a time, so it runs down the cabin like a wave rather than all at once,
 * and works the same in an airliner in flight, whose blocks keep their places relative to one another. Kept track of
 * while loaded, so a decompressing airliner finds its dimmer to turn the lights red (see {@link CabinLightsBlock#alarm}).
 */
public class CabinControlBlockEntity extends BlockEntity {
    private static final Loaded<CabinControlBlockEntity> LOADED = new Loaded<>(CabinControlBlockEntity.class);
    /** Blocks set each tick of a sweep, and how many apart along it the control's sound is heard. */
    private static final int PER_TICK = 24;
    private static final int SOUND_EVERY = 12;

    private List<BlockPos> targets = List.of();
    /** The next target a sweep sets; past the end when none is under way. */
    private int next = Integer.MAX_VALUE;
    /** Whether its links have been looked at since it was loaded, to mend them (see {@link Turning#mend}). */
    private boolean linksChecked;

    public CabinControlBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.CABIN_CONTROL_ENTITY.get(), pos, state);
    }

    /** Every cabin control loaded in the level. */
    static List<CabinControlBlockEntity> all(Level level) {
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

    /** Hands the control the blocks it works, front to back. */
    public void configure(List<BlockPos> targets) {
        this.targets = List.copyOf(targets);
        linksChecked = true;
        setChanged();
    }

    /** Starts the control's setting on its way through the cabin. */
    public void sweep() {
        mendLinks();
        next = 0;
        setChanged();
    }

    /** Once after loading, as the control is first worked, mends its links to what it works if they lead nowhere. */
    private void mendLinks() {
        if (!linksChecked && level != null && getBlockState().getBlock() instanceof CabinControlBlock control) {
            linksChecked = true;
            Turning.mend(level, worldPosition, targets, control::works, this::turnLinks);
        }
    }

    /** Turns the links to what it works, mending them. */
    private void turnLinks(Rotation turn) {
        targets = Turning.turn(targets, turn);
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
            SoundEvent sound = control.sweepSound();
            if (sound != null && next % SOUND_EVERY == 0) {
                level.playSound(null, at, sound, SoundSource.BLOCKS, 0.6F, 1.0F);
            }
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Targets", Relays.write(targets));
        Turning.save(tag, getBlockState());
        tag.putInt("Next", Math.min(next, targets.size()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        targets = Turning.turn(Relays.read(tag.getList("Targets", Tag.TAG_INT_ARRAY)), Turning.since(tag, getBlockState()));
        next = tag.getInt("Next");
    }
}
