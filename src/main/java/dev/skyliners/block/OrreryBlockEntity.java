package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import java.util.function.Supplier;
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
    /**
     * Whether the orrery this client drew nearest its player draws its planetarium's sky round it, null for none drawn;
     * the client sets it, for a play-test to read (see {@code BlockpilotCommands}).
     */
    public static Supplier<Boolean> clientSkyNearest = () -> null;
    /** How far down its sun hangs and how far out its planets circle, in blocks, for one built without them. */
    private static final double DEFAULT_DROP = 6;
    private static final double DEFAULT_REACH = 3;
    /**
     * The furthest an orrery's outermost planet circles from its sun, in blocks, so that all of it reads at once from
     * the cockpit under it; and the least worth hanging one for, under which only the dome's planetarium is drawn.
     */
    public static final double REACH_MOST = 6;
    public static final double REACH_LEAST = 1.5;
    /** How far out the orrery reaches for each block its outermost planet circles out, a share: its ecliptic hoop. */
    public static final double EXTENT = 1.2;
    /** How far inside the edge of the hole under the dome Saturn circles, in blocks, so he is seen through it. */
    private static final double OCULUS_CLEAR = 0.5;
    /** How far inside the dome's lining the orrery's outermost part keeps, in blocks. */
    private static final double LINING_CLEAR = 0.2;

    private double drop = DEFAULT_DROP;
    private double reach = DEFAULT_REACH;
    /** Where the control head is, from this block, as it was built; null for a rocket built before it was kept. */
    @Nullable
    private BlockPos autopilot;

    public OrreryBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.ORRERY_ENTITY.get(), pos, state);
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

    /**
     * How near a block of a row of her dome comes to her axis, in blocks, from where it is from the axis's column: her
     * axis runs up the middle of the blocks over x 0, z 0. The same as she is built and on the clients, so both measure
     * her dome's rows alike for {@link #reachIn}.
     */
    public static double nearAxis(int x, int z) {
        return Math.hypot(Math.max(0, Math.abs(x) - 0.5), Math.max(0, Math.abs(z) - 0.5));
    }

    /**
     * How far out an orrery's outermost planet circles in a dome, in blocks: no further than the most, inside the hole
     * under the dome, so wide from her axis to its edge, and with all of it inside the dome's lining, so far out from
     * her axis round the sun. The same as she is built and on the clients, which measure the dome for themselves.
     */
    public static double reachIn(double oculus, double room) {
        return Math.min(REACH_MOST, Math.min(oculus - OCULUS_CLEAR, (room - LINING_CLEAR) / EXTENT));
    }

    /** How far down from the mount's middle the sun hangs, in blocks. */
    public double drop() {
        return drop;
    }

    /** How far out from the sun the outermost planet circles, in blocks; none where only the planetarium is drawn. */
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
