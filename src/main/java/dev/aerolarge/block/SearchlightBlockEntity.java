package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A searchlight's block entity: the way it points, among the ship's blocks, never up into the car it hangs under. Its
 * handle swings it (see {@link SearchlightHandleBlockEntity}), and every client draws its beam that way (see the
 * client's {@code SearchlightBeams}). Kept track of on the clients too, for its beam.
 */
public class SearchlightBlockEntity extends BlockEntity {
    private static final Loaded<SearchlightBlockEntity> LOADED = new Loaded<>(SearchlightBlockEntity.class, true);
    /** How far below level it always points, at least, as the drop of its aim's unit direction. */
    private static final double LEAST_DOWN = 0.15;
    /** How far its aim must swing before every client is told, as the change in its unit direction. */
    private static final double TELL_SWING = 0.02;
    /** The way it points when first hung, among the ship's blocks: straight down. */
    private static final Vec3 DOWN = new Vec3(0, -1, 0);

    private Vec3 aim = DOWN;
    private Vec3 told = DOWN;

    public SearchlightBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.SEARCHLIGHT_ENTITY.get(), pos, state);
    }

    /** The searchlights loaded in a level, on the server or on this client. */
    public static List<SearchlightBlockEntity> loaded(Level level) {
        return LOADED.in(level);
    }

    /** The way it points, among the ship's blocks, a unit direction. */
    public Vec3 aim() {
        return aim;
    }

    public boolean lit() {
        return getBlockState().getValue(SearchlightBlock.LIT);
    }

    /** Points it a way among the ship's blocks, on the server, held a little below level; clients told as it swings. */
    public void point(Vec3 way) {
        Vec3 level = new Vec3(way.x, Math.min(way.y, -LEAST_DOWN), way.z);
        aim = level.lengthSqr() < 1.0E-6 ? DOWN : level.normalize();
        setChanged();
        if (aim.distanceTo(told) >= TELL_SWING) {
            told = aim;
            tell();
        }
    }

    /** Switches it on or off, on the server. */
    public void light(boolean on) {
        if (level != null && lit() != on) {
            level.setBlock(worldPosition, getBlockState().setValue(SearchlightBlock.LIT, on), Block.UPDATE_ALL);
            told = aim;
            tell();
        }
    }

    private void tell() {
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
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
        tag.putDouble("AimX", aim.x);
        tag.putDouble("AimY", aim.y);
        tag.putDouble("AimZ", aim.z);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Vec3 read = new Vec3(tag.getDouble("AimX"), tag.getDouble("AimY"), tag.getDouble("AimZ"));
        aim = read.lengthSqr() < 1.0E-6 ? DOWN : read.normalize();
        told = aim;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
