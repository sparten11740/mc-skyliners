package dev.skyliners.console;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.block.Mount;
import dev.skyliners.block.MountedModule;
import dev.skyliners.block.Turning;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What a console module keeps (see {@link ConsoleBlock}): its slice of the ring of a console laid round a ship's seats,
 * none for one put up by hand, which fills its own block (see {@link ConsoleShape.Facet}); the autopilot it reads, as
 * an offset from it, set as her console is laid, or none for one put up by hand, which reads the nearest; which of
 * its toggles are up, a bit each; and when it was last touched, in game time, which its client draws a moment of: a
 * scope's ping going out, a lamp test, a big button pressed in. All saved and sent to the clients, which draw it.
 */
public class ConsoleBlockEntity extends BlockEntity {
    /** Never touched. */
    private static final long NEVER = Long.MIN_VALUE;

    @Nullable
    private ConsoleShape.Facet facet;
    @Nullable
    private BlockPos autopilot;
    private int toggles;
    private long touched = NEVER;
    /** Its slice's desk as it stands in its block (see {@link ConsoleShape#deskShape}), worked out once; not saved. */
    @Nullable
    private VoxelShape deskShape;

    public ConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.CONSOLE_ENTITY.get(), pos, state);
    }

    /**
     * Laid as a slice of a console round a ship's seats: its slice of the ring and the autopilot it reads, as an offset
     * from it, both turned as she was placed.
     */
    public void arc(ConsoleShape.Facet slice, @Nullable BlockPos toAutopilot, Rotation rotation) {
        facet = slice.turned(rotation);
        autopilot = toAutopilot == null ? null : toAutopilot.rotate(rotation);
        deskShape = null;
        setChanged();
    }

    /** Its slice of a console's ring; null for one put up by hand. */
    @Nullable
    public ConsoleShape.Facet facet() {
        return facet;
    }

    /** Where it stands in its block (see {@link ConsoleShape}). */
    public ConsoleShape shape() {
        return ConsoleShape.of(getBlockState(), facet);
    }

    /**
     * What of its desk stands in its block, to bump into and pick out, for a slice of a console's ring on a floor (see
     * {@link ConsoleShape#deskShape}); null for one put up by hand, which fills its own block.
     */
    @Nullable
    public VoxelShape deskShape() {
        if (facet == null || MountedModule.of(getBlockState()) != Mount.FLOOR) {
            return null;
        }
        if (deskShape == null) {
            deskShape = shape().deskShape();
        }
        return deskShape;
    }

    /**
     * The autopilot it reads, on either side: the one it was laid with, or for one put up by hand the nearest within a
     * flight deck's reach; null for none.
     */
    @Nullable
    public AutopilotBlockEntity autopilot() {
        if (level == null) {
            return null;
        }
        if (autopilot == null) {
            return AutopilotBlockEntity.nearest(level, worldPosition);
        }
        return level.getBlockEntity(worldPosition.offset(autopilot)) instanceof AutopilotBlockEntity linked ? linked : null;
    }

    /** Whether one of its toggles is up. */
    public boolean toggleUp(int toggle) {
        return (toggles & 1 << toggle) != 0;
    }

    /** Which of its toggles are up, a bit each. */
    public int toggles() {
        return toggles;
    }

    /** One of its toggles flipped, on the server; whether it is up now. */
    boolean flip(int toggle) {
        toggles ^= 1 << toggle;
        changed();
        return toggleUp(toggle);
    }

    /** When it was last touched, in game time; {@link Long#MIN_VALUE} for never. */
    public long touched() {
        return touched;
    }

    /** Touched now, on the server: its client draws the moment. */
    void touch(Level level) {
        touched = level.getGameTime();
        changed();
    }

    /** Saved and sent to the clients. */
    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveShown(tag);
    }

    private void saveShown(CompoundTag tag) {
        if (facet != null) {
            CompoundTag slice = new CompoundTag();
            slice.putDouble("MiddleX", facet.middleX());
            slice.putDouble("MiddleZ", facet.middleZ());
            slice.putDouble("Inner", facet.inner());
            slice.putDouble("Outer", facet.outer());
            slice.putDouble("From", facet.from());
            slice.putDouble("To", facet.to());
            slice.putBoolean("LeftEnd", facet.leftEnd());
            slice.putBoolean("RightEnd", facet.rightEnd());
            tag.put("Facet", slice);
        }
        if (autopilot != null) {
            tag.putLong("Autopilot", autopilot.asLong());
        }
        tag.putInt("Toggles", toggles);
        if (touched != NEVER) {
            tag.putLong("Touched", touched);
        }
        Turning.save(tag, getBlockState());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Rotation turn = Turning.since(tag, getBlockState());
        if (tag.contains("Facet")) {
            CompoundTag slice = tag.getCompound("Facet");
            facet = new ConsoleShape.Facet(slice.getDouble("MiddleX"), slice.getDouble("MiddleZ"), slice.getDouble("Inner"), slice.getDouble("Outer"),
                slice.getDouble("From"), slice.getDouble("To"), slice.getBoolean("LeftEnd"), slice.getBoolean("RightEnd")).turned(turn);
        } else {
            facet = null;
        }
        deskShape = null;
        autopilot = tag.contains("Autopilot") ? Turning.turn(BlockPos.of(tag.getLong("Autopilot")), turn) : null;
        toggles = tag.getInt("Toggles");
        touched = tag.contains("Touched") ? tag.getLong("Touched") : NEVER;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveShown(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
