package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
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
import net.minecraft.world.level.block.state.BlockState;

/**
 * A lift's picture board's block entity, in the part holding the board: its lift's machine, as an offset from the
 * board, and the landing it stands at, both set as the ship is built and sent to the clients, whose renderer reads the
 * floors to draw from the machine (see {@link LiftMachineBlockEntity}).
 */
public class LiftBoardBlockEntity extends BlockEntity {
    /** Its lift's machine, as an offset from the board; null for a board not built with one. */
    @Nullable
    private BlockPos machine;
    private int landing;

    public LiftBoardBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.LIFT_BOARD_ENTITY.get(), pos, state);
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
