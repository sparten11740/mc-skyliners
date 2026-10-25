package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.airframe.OutsideCamera;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Which outside camera the display shows, picked with its buttons (see {@link CameraButtons}) and the same for everyone
 * looking at it: the fin's at first. The picture itself is drawn from that camera live, on the client, as its renderer
 * draws it.
 */
public class TailCameraDisplayBlockEntity extends BlockEntity implements CameraScreen {
    private OutsideCamera camera = OutsideCamera.TAIL;

    public TailCameraDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.TAIL_CAMERA_DISPLAY_ENTITY.get(), pos, state);
    }

    @Override
    public OutsideCamera camera() {
        return camera;
    }

    /** The middle of its blocks, from this one, the first on the left of the bottom row, to the last. */
    @Override
    public Vec3 faceMiddle() {
        Direction facing = getBlockState().getValue(TailCameraDisplayBlock.FACING);
        BlockPos last = worldPosition.relative(TailCameraDisplayBlock.right(facing), TailCameraDisplayBlock.COLUMNS - 1)
            .above(TailCameraDisplayBlock.ROWS - 1);
        return Vec3.atCenterOf(worldPosition).add(Vec3.atCenterOf(last)).scale(0.5);
    }

    /** Out of the wall it stands against, or down and out when it hangs tilted. */
    @Override
    public Vec3 faceOut() {
        Vec3 out = Vec3.atLowerCornerOf(getBlockState().getValue(TailCameraDisplayBlock.FACING).getNormal());
        return getBlockState().getValue(TailCameraDisplayBlock.TILTED) ? out.add(0, -1, 0).normalize() : out;
    }

    @Override
    public double faceReach() {
        return Math.sqrt(TailCameraDisplayBlock.COLUMNS * TailCameraDisplayBlock.COLUMNS + TailCameraDisplayBlock.ROWS * TailCameraDisplayBlock.ROWS + 1) / 2;
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
