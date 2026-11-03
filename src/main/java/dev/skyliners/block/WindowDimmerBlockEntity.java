package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A window dimmer's block entity (see {@link WindowDimmerBlock}): where the oval windows it sets are, the box round its
 * story's as two corners from the dimmer, set as the rocket is built and turned with her, and the darkness it last set
 * them to, which the clients border in white. A dimmer put up by hand has no story and sets the oval windows round it
 * instead, as far as a room reaches.
 */
public class WindowDimmerBlockEntity extends BlockEntity {
    /** What {@link #windows} says before the dimmer has set any. */
    public static final int UNSET = -1;
    /**
     * How far a dimmer put up by hand reaches for the oval windows it sets, in blocks: across each way, and up and down
     * from it, about a room's worth, short of the stories over and under it.
     */
    private static final int HAND_REACH = 16;
    private static final int HAND_ROWS = 4;

    /** The box round its story's windows, its two corners as offsets from the dimmer; null for one put up by hand. */
    @Nullable
    private BlockPos from;
    @Nullable
    private BlockPos to;
    /** How dark it last set the windows, or {@link #UNSET}. */
    private int windows = UNSET;

    public WindowDimmerBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.WINDOW_DIMMER_ENTITY.get(), pos, state);
    }

    /**
     * Tells it where its story's windows are, as the rocket is built: the corners of the box round them, from it; and
     * how dark they were laid, which it shows as if it had set them so ({@link #UNSET} where they differ).
     */
    public void story(BlockPos from, BlockPos to, int windows) {
        this.from = from;
        this.to = to;
        this.windows = windows;
        setChanged();
    }

    /** How dark it last set the windows, clear ({@code 0}) to darkest, or {@link #UNSET}. */
    public int windows() {
        return windows;
    }

    /**
     * The box it sets the oval windows in, in the level as the dimmer stands: round its story's, or for one put up by
     * hand, round it so far each way.
     */
    public BoundingBox box() {
        if (from == null || to == null) {
            return new BoundingBox(worldPosition.getX() - HAND_REACH, worldPosition.getY() - HAND_ROWS, worldPosition.getZ() - HAND_REACH,
                worldPosition.getX() + HAND_REACH, worldPosition.getY() + HAND_ROWS, worldPosition.getZ() + HAND_REACH);
        }
        return BoundingBox.fromCorners(worldPosition.offset(from), worldPosition.offset(to));
    }

    /** Sets every oval window it reaches to a darkness at once, on the server, and shows the clients it did. */
    void dim(int dim) {
        if (level == null || level.isClientSide) {
            return;
        }
        windows = dim;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        OvalWindowPieceBlock.dimWithin(level, box(), dim);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (from != null && to != null) {
            tag.put("From", NbtUtils.writeBlockPos(from));
            tag.put("To", NbtUtils.writeBlockPos(to));
            Turning.save(tag, getBlockState());
        }
        tag.putInt("Windows", windows);
    }

    /** Its box turned with the rocket, if it faces another way than it was saved facing (see {@link Turning}). */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Rotation turn = Turning.since(tag, getBlockState());
        from = Turning.turn(NbtUtils.readBlockPos(tag, "From").orElse(null), turn);
        to = Turning.turn(NbtUtils.readBlockPos(tag, "To").orElse(null), turn);
        int saved = tag.contains("Windows", Tag.TAG_INT) ? tag.getInt("Windows") : UNSET;
        windows = saved >= 0 && saved <= OvalWindowPieceBlock.DARKEST ? saved : UNSET;
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
