package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the rocket's orrery knows of the dome it hangs in (see {@link OrreryBlock}): how far down from its mount its sun
 * hangs and how far out its outermost planet circles, in blocks, set as she is built to fill the dome at her scale, and
 * where her autopilot's control head is from it, whose flight the lamps round the hole over her cockpit show; sent to
 * the clients, which draw it (see {@code client.OrreryRenderer}).
 */
public class OrreryBlockEntity extends BlockEntity {
    /** How far down its sun hangs and how far out its planets circle, in blocks, for one built without them. */
    private static final double DEFAULT_DROP = 6;
    private static final double DEFAULT_REACH = 3;

    private double drop = DEFAULT_DROP;
    private double reach = DEFAULT_REACH;
    /** Where the control head is, from this block, as it was built; null for a rocket built before it was kept. */
    @Nullable
    private BlockPos autopilot;

    public OrreryBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.ORRERY_ENTITY.get(), pos, state);
    }

    /**
     * Set as the rocket is built: how far under the mount's middle the sun hangs, how far out Saturn circles, and where
     * her control head is from the mount.
     */
    public void configure(double sunDrop, double outermost, @Nullable BlockPos controlHead) {
        drop = sunDrop;
        reach = outermost;
        autopilot = controlHead;
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

    /**
     * Where her control head was from the mount as she was built; null if that was not kept. Her blocks may have been
     * turned since, so whatever is looked for there may be gone.
     */
    @Nullable
    public BlockPos autopilot() {
        return autopilot;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("Drop", drop);
        tag.putDouble("Reach", reach);
        if (autopilot != null) {
            tag.put("Autopilot", NbtUtils.writeBlockPos(autopilot));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        drop = tag.contains("Drop") ? tag.getDouble("Drop") : DEFAULT_DROP;
        reach = tag.contains("Reach") ? tag.getDouble("Reach") : DEFAULT_REACH;
        autopilot = NbtUtils.readBlockPos(tag, "Autopilot").orElse(null);
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
