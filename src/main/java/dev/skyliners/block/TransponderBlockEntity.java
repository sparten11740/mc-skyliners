package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.compat.SableFlight;
import dev.skyliners.radar.MarkerColour;
import dev.skyliners.radar.MarkerIcon;
import dev.skyliners.radar.Transponders;
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
import net.minecraft.world.phys.Vec3;

/**
 * What a transponder shows on the maps: its picture and its colour, picked on its panel (see
 * {@link TransponderBlock}). Loaded on the server, it is kept track of (see {@link Transponders}): standing on the
 * ground, it is kept in the dimension's saved data, to show on the maps from anywhere; aboard an assembled airliner, it
 * is shown where the airliner flies for as long as it is loaded.
 */
public class TransponderBlockEntity extends BlockEntity {
    private MarkerIcon icon = MarkerIcon.FLAG;
    private MarkerColour colour = MarkerColour.RED;

    public TransponderBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.TRANSPONDER_ENTITY.get(), pos, state);
    }

    public MarkerIcon icon() {
        return icon;
    }

    public MarkerColour colour() {
        return colour;
    }

    /** Shows it by another picture and colour, on the maps and on its own face. */
    public void show(MarkerIcon icon, MarkerColour colour) {
        this.icon = icon;
        this.colour = colour;
        setChanged();
        if (level instanceof ServerLevel server) {
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            Transponders.keep(server, this);
        }
    }

    /** Whether it is aboard an assembled airliner, flying with it. */
    public boolean airborne() {
        return level != null && SableFlight.aboard(level, Vec3.atCenterOf(worldPosition));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        Transponders.LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        Transponders.LOADED.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        Transponders.LOADED.remove(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Icon", icon.name());
        tag.putString("Colour", colour.name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        icon = MarkerIcon.named(tag.getString("Icon"), MarkerIcon.FLAG);
        colour = MarkerColour.named(tag.getString("Colour"), MarkerColour.RED);
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
