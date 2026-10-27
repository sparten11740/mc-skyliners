package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Lookout's Glass's block entity: the flags the lookouts have dropped through it, one for each lookout, their next
 * spot moving it, each where on the land it stands, whose face it carries and when it was dropped. They are sent to
 * every client, where the autopilot's map of the same zeppelin shows them to be picked as its pin, and go away after
 * ten minutes. Looking through the glass lives in the client package and is reached through {@link #clientUse}, which
 * the client sets, so this class stays safe to load on a dedicated server.
 */
public class LookoutGlassBlockEntity extends BlockEntity {
    private static final Loaded<LookoutGlassBlockEntity> LOADED = new Loaded<>(LookoutGlassBlockEntity.class, true);
    /** How many flags stand at once, at most, the oldest taken down for one more. */
    private static final int FLAGS_AT_MOST = 4;
    /** How long a flag stands, in ticks: ten minutes, long enough to fly to. */
    private static final long FLAG_TICKS = 20 * 60 * 10;
    /** How soon after a spot another can be marked through the glass, in ticks. */
    public static final long SPOT_COOLDOWN = 10;

    /** Set by the client: the local player has used the glass. */
    public static Consumer<LookoutGlassBlockEntity> clientUse = glass -> { };

    /**
     * A flag dropped through the glass: whose face it carries, their name, where it stands, and when it was dropped.
     */
    public record Flag(UUID lookout, String name, int x, int z, long dropped) {
        /** Whether it still stands at a game time. */
        public boolean standing(long now) {
            return now >= dropped && now - dropped < FLAG_TICKS;
        }
    }

    private final List<Flag> flags = new ArrayList<>();
    private long lastSpot = Long.MIN_VALUE;

    public LookoutGlassBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.LOOKOUT_GLASS_ENTITY.get(), pos, state);
    }

    /** The glasses loaded in a level, on the server or on this client. */
    public static List<LookoutGlassBlockEntity> loaded(Level level) {
        return LOADED.in(level);
    }

    /** The flags still standing at a game time, oldest first. */
    public List<Flag> flags(long now) {
        return flags.stream().filter(flag -> flag.standing(now)).toList();
    }

    /** Whether a spot can be marked through it at a game time: not straight after the last. */
    public boolean ready(long now) {
        return lastSpot == Long.MIN_VALUE || now < lastSpot || now - lastSpot >= SPOT_COOLDOWN;
    }

    /**
     * Drops a lookout's flag on a spot on the land, on the server, moving theirs if they have one standing already;
     * one more than the glass holds takes the oldest down.
     */
    public void spot(Player lookout, int x, int z, long now) {
        flags.removeIf(flag -> !flag.standing(now) || flag.lookout().equals(lookout.getUUID()));
        flags.add(new Flag(lookout.getUUID(), lookout.getGameProfile().getName(), x, z, now));
        while (flags.size() > FLAGS_AT_MOST) {
            flags.remove(0);
        }
        lastSpot = now;
        setChanged();
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
        ListTag saved = new ListTag();
        for (Flag flag : flags) {
            CompoundTag one = new CompoundTag();
            one.putUUID("Lookout", flag.lookout());
            one.putString("Name", flag.name());
            one.putInt("X", flag.x());
            one.putInt("Z", flag.z());
            one.putLong("Dropped", flag.dropped());
            saved.add(one);
        }
        tag.put("Flags", saved);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        flags.clear();
        for (Tag each : tag.getList("Flags", Tag.TAG_COMPOUND)) {
            Flag flag = read((CompoundTag) each);
            if (flag != null) {
                flags.add(flag);
            }
        }
    }

    /** A flag as it was saved; null for one saved without whose it is. */
    @Nullable
    private static Flag read(CompoundTag tag) {
        if (!tag.hasUUID("Lookout")) {
            return null;
        }
        return new Flag(tag.getUUID("Lookout"), tag.getString("Name"), tag.getInt("X"), tag.getInt("Z"), tag.getLong("Dropped"));
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
