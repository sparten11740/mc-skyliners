package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The WCs an overhead WC sign shows, as the WC signs over their doors (see {@link WcSignBlockEntity}), held as offsets
 * from the sign so they hold once the airliner is turned, assembled and flown. A few times a second it reads whether
 * each of those signs is lit for a WC taken, and lights its own toilet red only while every one of them is: green while
 * any is free. The airliner's blueprint tells each sign which WCs it points to; one put up by hand shows every WC whose
 * sign is round it.
 */
public class OverheadWcSignBlockEntity extends BlockEntity {
    /** How often the sign reads its WCs' signs, in ticks, as often as they look into their WCs. */
    private static final int LOOK_TICKS = 10;
    /** How far round a sign put up by hand the WC signs it shows are looked for, in blocks across and up and down. */
    private static final int LINK_ACROSS = 16;
    private static final int LINK_UP_DOWN = 8;

    private List<BlockPos> wcs = List.of();

    public OverheadWcSignBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.OVERHEAD_WC_SIGN_ENTITY.get(), pos, state);
    }

    /** Hands the sign the WCs it shows: their WC signs, as offsets from it. */
    public void configure(List<BlockPos> wcs) {
        this.wcs = List.copyOf(wcs);
        setChanged();
    }

    /** Whether the sign shows any WC. */
    boolean linked() {
        return !wcs.isEmpty();
    }

    /** Put up by hand: it shows every WC whose sign is round it. */
    void link() {
        if (level == null) {
            return;
        }
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos at : BlockPos.betweenClosed(worldPosition.offset(-LINK_ACROSS, -LINK_UP_DOWN, -LINK_ACROSS),
            worldPosition.offset(LINK_ACROSS, LINK_UP_DOWN, LINK_ACROSS))) {
            if (level.getBlockState(at).getBlock() instanceof WcSignBlock) {
                found.add(at.subtract(worldPosition));
            }
        }
        configure(found);
    }

    void tick() {
        // Each sign reads on a tick of its own, as the WC signs look on theirs.
        if (!(level instanceof ServerLevel server) || Math.floorMod(server.getGameTime() + worldPosition.hashCode(), LOOK_TICKS) != 0) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof OverheadWcSignBlock)) {
            return;
        }
        boolean shown = false;
        boolean free = false;
        for (BlockPos offset : wcs) {
            BlockPos at = worldPosition.offset(offset);
            // Never load a chunk just to read a sign in it; a WC whose sign is gone is shown no more.
            BlockState wc = server.isLoaded(at) ? server.getBlockState(at) : null;
            if (wc == null || !(wc.getBlock() instanceof WcSignBlock)) {
                continue;
            }
            shown = true;
            free |= !wc.getValue(WcSignBlock.OCCUPIED);
        }
        boolean occupied = shown && !free;
        if (occupied != state.getValue(OverheadWcSignBlock.OCCUPIED)) {
            server.setBlock(worldPosition, state.setValue(OverheadWcSignBlock.OCCUPIED, occupied), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Wcs", Relays.write(wcs));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        wcs = Relays.read(tag.getList("Wcs", Tag.TAG_INT_ARRAY));
    }
}
