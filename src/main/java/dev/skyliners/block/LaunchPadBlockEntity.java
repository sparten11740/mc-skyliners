package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The hub in the middle of a launch pad's grate (see {@code airport.LaunchPad}): it knows how far the pad reaches and
 * which way it faces, so that a rocket's blueprint used anywhere on the pad sets her down right over it, and, on the
 * clients, so that the blueprint's preview shows her there. Digging the hub up leaves a pad that is only concrete, and
 * a hub put up by hand is a pad of its own block alone (see {@link LaunchPadHubBlock}).
 */
public class LaunchPadBlockEntity extends BlockEntity {
    private static final Loaded<LaunchPadBlockEntity> LOADED = new Loaded<>(LaunchPadBlockEntity.class, true);
    /** How many rows under and over the deck a block still counts as on the pad: the pit's floor, what stands on it. */
    private static final int ROWS_UNDER = 7;
    private static final int ROWS_OVER = 3;

    /** How far out the deck reaches each way from here, in blocks: none for a hub put up by hand, or not laid yet. */
    private int half;
    /** The way from the pad's front to its back, which a rocket's tail points on it, her door to the front. */
    private Direction along = Direction.SOUTH;

    public LaunchPadBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.LAUNCH_PAD_HUB_ENTITY.get(), pos, state);
    }

    /** The nearest pad a block is on, in the level, on the server or on this client; null for none. */
    @Nullable
    public static LaunchPadBlockEntity under(Level level, BlockPos at) {
        LaunchPadBlockEntity nearest = null;
        double closest = Double.MAX_VALUE;
        for (LaunchPadBlockEntity pad : LOADED.in(level)) {
            double away = pad.getBlockPos().distSqr(at);
            if (pad.covers(at) && away < closest) {
                nearest = pad;
                closest = away;
            }
        }
        return nearest;
    }

    /** Whether a block is on this pad: over or under its deck, not up its tower. */
    private boolean covers(BlockPos at) {
        BlockPos from = at.subtract(worldPosition);
        return Math.abs(from.getX()) <= half && Math.abs(from.getZ()) <= half && from.getY() >= -ROWS_UNDER && from.getY() <= ROWS_OVER;
    }

    /**
     * Set as the blueprint lays it, or as it is put up by hand: how far out the deck reaches, and the way from its
     * front to its back.
     */
    public void laid(int half, Direction along) {
        this.half = half;
        this.along = along;
    }

    public Direction along() {
        return along;
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
        tag.putInt("Half", half);
        tag.putString("Along", along.getName());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        half = tag.getInt("Half");
        Direction named = Direction.byName(tag.getString("Along"));
        along = named != null && named.getAxis().isHorizontal() ? named : Direction.SOUTH;
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
