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
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A propeller's spin on the client: how far it has spun up toward the signal its hub carries, and how far round its
 * blades have turned. A big propeller takes its time: it winds up over a few seconds and runs down slower still. How
 * far its blades reach is its own, saved and sent to the clients: a smaller zeppelin's are shorter.
 */
public class PropellerBlockEntity extends BlockEntity {
    /** Share of full speed gained per tick spinning up, and lost per tick running down. */
    private static final float SPIN_UP = 0.01F;
    private static final float RUN_DOWN = 0.005F;
    /** How far round the blades turn per tick at full speed, in radians: fast, but slow enough to see the blades go. */
    private static final float FULL_SPEED = 0.45F;
    /** A whole turn, in radians. */
    private static final float FULL_TURN = (float) (2 * Math.PI);
    /**
     * How far round the hub a full-size zeppelin's blades reach, in blocks, and any propeller's that was saved without
     * its own.
     */
    public static final float FULL_REACH = 6;

    /** The propellers loaded on this client, for what rings with them (a festive zeppelin's sleigh bells). */
    private static final Loaded<PropellerBlockEntity> LOADED = new Loaded<>(PropellerBlockEntity.class, true);

    /** How far round the hub its blades reach, in blocks: the whole propeller is drawn while any of it is in view. */
    private float reach = FULL_REACH;
    private float spin;
    private float angle;
    private float lastAngle;

    public PropellerBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.PROPELLER_ENTITY.get(), pos, state);
    }

    void clientTick() {
        float wanted = getBlockState().getValue(ThrustRelayBlock.POWER) / 15F;
        spin = wanted > spin ? Math.min(wanted, spin + SPIN_UP) : Math.max(wanted, spin - RUN_DOWN);
        lastAngle = angle;
        angle += spin * FULL_SPEED;
        if (angle > FULL_TURN) {
            angle -= FULL_TURN;
            lastAngle -= FULL_TURN;
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

    /** The propellers loaded in a level, on this client. */
    public static List<PropellerBlockEntity> loaded(Level level) {
        return LOADED.in(level);
    }

    /** How far round the hub its blades reach, in blocks. */
    public float reach() {
        return reach;
    }

    /** Cuts its blades to reach so far round the hub, in blocks, as its zeppelin is built; every client is told. */
    public void reachTo(float blocks) {
        reach = blocks;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** How fast it spins, a share of full speed. */
    public float spin() {
        return spin;
    }

    /** How far round the blades have turned, in radians, between the last tick and this one. */
    public float angle(float partialTick) {
        return Mth.lerp(partialTick, lastAngle, angle);
    }

    /** The box the whole propeller is drawn in: the hub and its blades all round it. */
    public AABB renderBox() {
        return new AABB(worldPosition).inflate(reach);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("Reach", reach);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        reach = tag.contains("Reach") ? tag.getFloat("Reach") : FULL_REACH;
    }

    /** What every client is sent: how far its blades reach. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Reach", reach);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
