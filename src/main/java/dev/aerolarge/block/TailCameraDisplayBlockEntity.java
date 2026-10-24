package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.airframe.OutsideCamera;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which outside camera the display shows, picked with its buttons (see {@link CameraButtons}) and the same for everyone
 * looking at it: the fin's at first. The picture itself is drawn from that camera live, on the client, as its renderer
 * draws it.
 */
public class TailCameraDisplayBlockEntity extends BlockEntity {
    private OutsideCamera camera = OutsideCamera.TAIL;

    public TailCameraDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.TAIL_CAMERA_DISPLAY_ENTITY.get(), pos, state);
    }

    public OutsideCamera camera() {
        return camera;
    }

    /** Shows another camera, for everyone looking at the display. */
    public void look(OutsideCamera camera) {
        if (camera == this.camera) {
            return;
        }
        this.camera = camera;
        setChanged();
        if (level instanceof ServerLevel server) {
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Camera", camera.name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        camera = OutsideCamera.named(tag.getString("Camera"), OutsideCamera.TAIL);
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
