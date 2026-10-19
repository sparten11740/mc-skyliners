package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The seatbelt switch's links to the signs, like any other linked control's, and kept track of while loaded, so a
 * seated player's airliner can be asked whether its signs are on.
 */
public class SeatbeltSwitchBlockEntity extends LinkedControlBlockEntity {
    private static final Loaded<SeatbeltSwitchBlockEntity> LOADED = new Loaded<>(SeatbeltSwitchBlockEntity.class);

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
