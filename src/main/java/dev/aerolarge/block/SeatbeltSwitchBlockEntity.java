package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The seatbelt switch's links to the signs, like any other linked control's, and kept track of while loaded, so a
 * seated player's airliner can be asked whether its signs are on. It also knows the airliner's curtains: when the signs
 * come on it opens them all, front to back like the cabin controls' settings, the way cabin crew draw the curtains back
 * for take-off and landing so nothing hides the way to the doors.
 */
public class SeatbeltSwitchBlockEntity extends LinkedControlBlockEntity {
    private static final Loaded<SeatbeltSwitchBlockEntity> LOADED = new Loaded<>(SeatbeltSwitchBlockEntity.class);
    /** Curtain pieces opened each tick as the opening runs down the cabin. */
    private static final int CURTAINS_PER_TICK = 24;

    /** The curtains aboard, as offsets from the switch in the order they are reached front to back. */
    private List<BlockPos> curtains = List.of();
    /** The next curtain the opening reaches; past the end when none is under way. */
    private int nextCurtain = Integer.MAX_VALUE;

    public SeatbeltSwitchBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.SEATBELT_SWITCH_ENTITY.get(), pos, state);
    }

    /** Every seatbelt switch loaded in the level. */
    public static List<SeatbeltSwitchBlockEntity> all(Level level) {
        return LOADED.in(level);
    }

    public boolean isOn() {
        return getBlockState().getValue(SeatbeltSwitchBlock.ON);
    }

    /** Hands the switch the curtains it opens, front to back. */
    public void configureCurtains(List<BlockPos> curtains) {
        this.curtains = List.copyOf(curtains);
        setChanged();
    }

    /** Starts opening every curtain aboard, with one swish at the first still drawn shut; nothing if none is. */
    void openCurtains() {
        if (level == null) {
            return;
        }
        for (BlockPos offset : curtains) {
            BlockPos at = worldPosition.offset(offset);
            if (level.isLoaded(at) && isShut(level.getBlockState(at))) {
                CurtainBlock.swish(level, at);
                nextCurtain = 0;
                setChanged();
                return;
            }
        }
    }

    void tick() {
        if (level == null || nextCurtain >= curtains.size()) {
            return;
        }
        for (int done = 0; done < CURTAINS_PER_TICK && nextCurtain < curtains.size(); done++, nextCurtain++) {
            BlockPos at = worldPosition.offset(curtains.get(nextCurtain));
            if (level.isLoaded(at) && isShut(level.getBlockState(at))) {
                level.setBlock(at, level.getBlockState(at).setValue(CurtainBlock.OPEN, true), Block.UPDATE_CLIENTS);
            }
        }
        setChanged();
    }

    private static boolean isShut(BlockState state) {
        return CurtainBlock.isCurtain(state) && !state.getValue(CurtainBlock.OPEN);
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Curtains", Relays.write(curtains));
        tag.putInt("NextCurtain", Math.min(nextCurtain, curtains.size()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        curtains = Relays.read(tag.getList("Curtains", Tag.TAG_INT_ARRAY));
        nextCurtain = tag.getInt("NextCurtain");
    }
}
