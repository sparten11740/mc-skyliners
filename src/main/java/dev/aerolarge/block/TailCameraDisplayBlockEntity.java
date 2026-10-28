package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.airframe.CameraSpot;
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
 * looking at it: an airliner's fin camera at first, or the camera a zeppelin's display is built showing. The camera it
 * shows says whose cameras its buttons pick among, so a zeppelin's display keeps to hers. The picture itself is drawn
 * from that camera live, on the client, as its renderer draws it.
 */
public class TailCameraDisplayBlockEntity extends BlockEntity implements CameraScreen {
    private CameraSpot camera = OutsideCamera.TAIL;

    public TailCameraDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.TAIL_CAMERA_DISPLAY_ENTITY.get(), pos, state);
    }

    @Override
    public CameraSpot camera() {
        return camera;
    }

    /** The middle of its blocks, from this one, the first on the left of the bottom row, to the last. */
    @Override
    public Vec3 faceMiddle() {
        BlockState state = getBlockState();
        Direction facing = state.getValue(TailCameraDisplayBlock.FACING);
        BlockPos last = worldPosition.relative(TailCameraDisplayBlock.right(facing), TailCameraDisplayBlock.columns(state) - 1)
            .above(TailCameraDisplayBlock.rows(state) - 1);
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
        int columns = TailCameraDisplayBlock.columns(getBlockState());
        int rows = TailCameraDisplayBlock.rows(getBlockState());
        return Math.sqrt(columns * columns + rows * rows + 1) / 2;
    }

    /** Shows another camera, one of its airframe's, for everyone looking at the display. */
    public void look(CameraSpot camera) {
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
        camera = CameraSpot.named(tag.getString("Camera"), OutsideCamera.TAIL);
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
