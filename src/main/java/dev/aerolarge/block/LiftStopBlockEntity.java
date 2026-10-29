package dev.aerolarge.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a lift's board or call button knows of its lift: its machine, as an offset from it, and the landing it stands
 * at, both set as the ship is built and sent to the clients, which draw the board from the machine (see
 * {@link LiftMachineBlockEntity}).
 */
public abstract class LiftStopBlockEntity extends BlockEntity {
    /** Its lift's machine, as an offset from it; null for one not built with one. */
    @Nullable
    private BlockPos machine;
    private int landing;

    protected LiftStopBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Set as the ship is built: its lift's machine as an offset from it, turned as she was placed, and its landing. */
    public void configure(BlockPos machineOffset, int at, Rotation turn) {
        machine = machineOffset.rotate(turn);
        landing = at;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** The landing it stands at. */
    public int landing() {
        return landing;
    }

    /** Its lift's machine, if it is loaded, on either side. */
    @Nullable
    public LiftMachineBlockEntity lift() {
        if (machine == null || level == null) {
            return null;
        }
        return level.getBlockEntity(worldPosition.offset(machine)) instanceof LiftMachineBlockEntity lift ? lift : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (machine != null) {
            tag.putLong("Machine", machine.asLong());
        }
        tag.putInt("Landing", landing);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        machine = tag.contains("Machine") ? BlockPos.of(tag.getLong("Machine")) : null;
        landing = tag.getInt("Landing");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
