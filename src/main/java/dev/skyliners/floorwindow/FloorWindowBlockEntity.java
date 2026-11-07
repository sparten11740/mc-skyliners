package dev.skyliners.floorwindow;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.Turning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block of a round floor window (see {@link FloorWindowBlock}): where the window's middle block is, as an offset from
 * it, turned with the block if it is ever loaded facing another way (see {@link Turning}); the floor its shut iris
 * looks like, so the window blends into the floor round it; the block it took the place of, put back when the window is
 * taken out; and how deep a shaft runs down under it, if any. Clients are sent them all to draw it. On the client it
 * also keeps how far its iris has opened, eased toward open or shut at a steady pace, so every block of the window,
 * told at once, opens as one.
 */
public class FloorWindowBlockEntity extends BlockEntity {
    /** The floor a window blends into when it has been told none, as one built before its floor was known. */
    private static final BlockState PLAIN_FLOOR = Blocks.SMOOTH_STONE.defaultBlockState();

    private BlockPos toMiddle = BlockPos.ZERO;
    private BlockState floor = PLAIN_FLOOR;
    private BlockState replaced = Blocks.AIR.defaultBlockState();
    /**
     * How deep a round steel shaft runs down from under the window to where it looks out, in blocks, as through a
     * ship's hull to the glass in her belly; none for a window laid straight into a floor.
     */
    private int shaft;
    /**
     * On the client: how far the iris has opened, from 0 shut to 1 open; and when it was last drawn, on the drawing
     * clock, in ticks.
     */
    private double opened = Double.NaN;
    private double drawnAt = Double.NaN;

    public FloorWindowBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.FLOOR_WINDOW_ENTITY.get(), pos, state);
    }

    /**
     * Set as the window is laid: where its middle block is from this one, the floor it blends into and the block it
     * took the place of.
     */
    public void configure(BlockPos toMiddle, BlockState floor, BlockState replaced) {
        configure(toMiddle, floor, replaced, 0);
    }

    /** The same, with a shaft so many blocks deep under the window, down to where it looks out. */
    public void configure(BlockPos toMiddle, BlockState floor, BlockState replaced, int shaftDepth) {
        this.toMiddle = toMiddle.immutable();
        this.floor = floor;
        this.replaced = replaced;
        this.shaft = Math.max(0, shaftDepth);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** The window's middle block. */
    public BlockPos middle() {
        return worldPosition.offset(toMiddle);
    }

    /** Where the window's middle block is from this one. */
    public BlockPos toMiddle() {
        return toMiddle;
    }

    /** The floor the window's shut iris looks like. */
    public BlockState floor() {
        return floor;
    }

    /** How deep the window's shaft runs down under it, in blocks; none for most. */
    public int shaft() {
        return shaft;
    }

    /** The block this one took the place of, put back when the window is taken out. */
    public BlockState replaced() {
        return replaced;
    }

    /**
     * On the client: brings the iris on toward open or shut by so much of the way each tick, at a time on the drawing
     * clock; gives how far it has opened, from 0 to 1. First drawn, it stands as it is told.
     */
    public double toward(double ticks, boolean open, double perTick) {
        double target = open ? 1 : 0;
        if (Double.isNaN(opened)) {
            opened = target;
        }
        double since = Double.isNaN(drawnAt) ? 0 : Math.max(0, ticks - drawnAt);
        drawnAt = ticks;
        double step = perTick * since;
        opened = opened < target ? Math.min(target, opened + step) : Math.max(target, opened - step);
        return opened;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("MiddleX", toMiddle.getX());
        tag.putInt("MiddleY", toMiddle.getY());
        tag.putInt("MiddleZ", toMiddle.getZ());
        tag.put("Floor", NbtUtils.writeBlockState(floor));
        tag.put("Replaced", NbtUtils.writeBlockState(replaced));
        tag.putInt("Shaft", shaft);
        Turning.save(tag, getBlockState());
    }

    /** Loaded again, its offset to the middle is turned if the block has turned since it was saved. */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        BlockPos saved = new BlockPos(tag.getInt("MiddleX"), tag.getInt("MiddleY"), tag.getInt("MiddleZ"));
        toMiddle = Turning.turn(saved, Turning.since(tag, getBlockState()));
        floor = tag.contains("Floor") ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("Floor")) : PLAIN_FLOOR;
        replaced = tag.contains("Replaced") ? NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("Replaced"))
            : Blocks.AIR.defaultBlockState();
        if (floor.isAir()) {
            floor = PLAIN_FLOOR;
        }
        shaft = Math.max(0, tag.getInt("Shaft"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
