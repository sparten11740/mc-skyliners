package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A techno deck's block entity: the room it plays, its two corners as offsets from the deck, set as the zeppelin is
 * built. Kept track of on the clients, where the music starts for whoever walks into the room and its lights go with
 * the beat (see the client's {@code TechnoRoom}), and when its record was last scratched, for the lights to flash.
 */
public class TechnoDeckBlockEntity extends BlockEntity {
    private static final Loaded<TechnoDeckBlockEntity> LOADED = new Loaded<>(TechnoDeckBlockEntity.class, true);

    @Nullable
    private BlockPos from;
    @Nullable
    private BlockPos to;
    /** The game tick its record was last scratched (see {@link TechnoDeckBlock}); never, as it is loaded. */
    private long scratchedAt = Long.MIN_VALUE;

    public TechnoDeckBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.TECHNO_DECK_ENTITY.get(), pos, state);
    }

    /** The decks loaded in a level, on this client. */
    public static List<TechnoDeckBlockEntity> loaded(Level level) {
        return LOADED.in(level);
    }

    /**
     * Tells it the room it plays, by its corners from the deck, as the zeppelin is built (turned as she was placed).
     */
    public void room(BlockPos from, BlockPos to) {
        this.from = from;
        this.to = to;
        setChanged();
    }

    /** The room's corner nearest the world's origin, from the deck; null for a deck put down by hand. */
    @Nullable
    public BlockPos roomFrom() {
        return from == null || to == null ? null : BlockPos.min(from, to);
    }

    /** The room's opposite corner, from the deck; null for a deck put down by hand. */
    @Nullable
    public BlockPos roomTo() {
        return from == null || to == null ? null : BlockPos.max(from, to);
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

    /** The game tick its record was last scratched; never, as it is loaded. */
    public long scratchedAt() {
        return scratchedAt;
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id != TechnoDeckBlock.SCRATCHED || level == null) {
            return super.triggerEvent(id, param);
        }
        scratchedAt = level.getGameTime();
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (from != null && to != null) {
            tag.put("From", NbtUtils.writeBlockPos(from));
            tag.put("To", NbtUtils.writeBlockPos(to));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        from = NbtUtils.readBlockPos(tag, "From").orElse(null);
        to = NbtUtils.readBlockPos(tag, "To").orElse(null);
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
