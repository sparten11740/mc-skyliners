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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the rocket's orrery knows of the dome it hangs in (see {@link OrreryBlock}): how far down from its mount its sun
 * hangs and how far out its outermost planet circles, in blocks, set as she is built to fill the dome at her scale and
 * sent to the clients, which draw it (see {@code client.OrreryRenderer}).
 */
public class OrreryBlockEntity extends BlockEntity {
    /** How far down its sun hangs and how far out its planets circle, in blocks, for one built without them. */
    private static final double DEFAULT_DROP = 6;
    private static final double DEFAULT_REACH = 3;

    private double drop = DEFAULT_DROP;
    private double reach = DEFAULT_REACH;

    public OrreryBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.ORRERY_ENTITY.get(), pos, state);
    }

    /** Set as the rocket is built: how far under the mount's middle the sun hangs, and how far out Saturn circles. */
    public void configure(double sunDrop, double outermost) {
        drop = sunDrop;
        reach = outermost;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** How far down from the mount's middle the sun hangs, in blocks. */
    public double drop() {
        return drop;
    }

    /** How far out from the sun the outermost planet circles, in blocks. */
    public double reach() {
        return reach;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("Drop", drop);
        tag.putDouble("Reach", reach);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        drop = tag.contains("Drop") ? tag.getDouble("Drop") : DEFAULT_DROP;
        reach = tag.contains("Reach") ? tag.getDouble("Reach") : DEFAULT_REACH;
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
