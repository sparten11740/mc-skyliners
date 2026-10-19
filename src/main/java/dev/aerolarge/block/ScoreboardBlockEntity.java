package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.compat.SableFlight;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A scoreboard's score, home and guest, kept in sync with the clients so its renderer can show it, and kept track of
 * while loaded so a basket finds the scoreboards near its hoop.
 */
public class ScoreboardBlockEntity extends BlockEntity {
    private static final Loaded<ScoreboardBlockEntity> LOADED = new Loaded<>(ScoreboardBlockEntity.class);

    private int home;
    private int guest;

    public ScoreboardBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.SCOREBOARD_ENTITY.get(), pos, state);
    }

    /** Every scoreboard loaded in the level. */
    public static List<ScoreboardBlockEntity> all(Level level) {
        return LOADED.in(level);
    }

    public int home() {
        return home;
    }

    public int guest() {
        return guest;
    }

    /** Where it is in the world, aboard an assembled airliner as well. */
    public Vec3 whereInWorld() {
        Vec3 centre = Vec3.atCenterOf(worldPosition);
        return level == null ? centre : SableFlight.toWorld(level, centre);
    }

    public void add(HoopBlock.Team team, int points) {
        if (team == HoopBlock.Team.HOME) {
            home += points;
        } else {
            guest += points;
        }
        changed();
    }

    public void reset() {
        home = 0;
        guest = 0;
        changed();
    }

    /** The score as it reads: home first. */
    public Component describe() {
        return Component.translatable("message.aerolarge.scoreboard.score",
            HoopBlock.Team.HOME.label(), home, guest, HoopBlock.Team.GUEST.label());
    }

    private void changed() {
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
        tag.putInt("Home", home);
        tag.putInt("Guest", guest);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        home = tag.getInt("Home");
        guest = tag.getInt("Guest");
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
