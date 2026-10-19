package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.compat.SableFlight;
import java.util.List;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A scoreboard's score, home and guest, kept in sync with the clients so its renderer can show it, and kept track of
 * while loaded so a basket finds the scoreboards near its hoop. It also runs hide and seek's countdown: big numbers
 * from ten down to nought, a beep each second (higher for the last three), then the buzzer; the nought stays up a
 * moment before the score comes back. The clients hear of each new number as it comes up.
 */
public class ScoreboardBlockEntity extends BlockEntity {
    private static final Loaded<ScoreboardBlockEntity> LOADED = new Loaded<>(ScoreboardBlockEntity.class);
    /** The countdown starts from this many seconds. */
    private static final int COUNT_FROM = 10;
    /** The last this many seconds beep higher. */
    private static final int LAST_SECONDS = 3;
    /** How long the nought stays up after the buzzer, in seconds. */
    private static final int HOLD = 2;
    /** How long a countdown shows on the board, in ticks: from its first number until the score comes back. */
    public static final int COUNTDOWN_TICKS = (COUNT_FROM + HOLD) * SharedConstants.TICKS_PER_SECOND;
    /** The beep's pitch for most seconds and for the last few; loud enough to be heard down the whole gym hall. */
    private static final float BEEP_PITCH = 1.0F;
    private static final float LAST_BEEP_PITCH = 1.5F;
    private static final float BEEP_VOLUME = 2.0F;
    /** Sparks off the board at the buzzer: how many, how far they spread and how fast they fly. */
    private static final int SPARKS = 20;
    private static final double SPARK_SPREAD = 0.4;
    private static final double SPARK_SPEED = 0.08;

    private int home;
    private int guest;
    /** Ticks left until the countdown's buzzer, below nought while the nought stays up; null with no countdown. */
    @Nullable
    private Integer countdown;

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

    /** The countdown's number on the board, ten down to nought; empty while the board shows the score. */
    public OptionalInt countdown() {
        if (countdown == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Math.max(0, Math.ceilDiv(countdown, SharedConstants.TICKS_PER_SECOND)));
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

    /** Starts hide and seek's countdown from ten, over again if one is under way. */
    public void startCountdown() {
        countdown = COUNT_FROM * SharedConstants.TICKS_PER_SECOND;
        beep();
        changed();
    }

    /** The score as it reads: home first. */
    public Component describe() {
        return Component.translatable("message.aerolarge.scoreboard.score",
            HoopBlock.Team.HOME.label(), home, guest, HoopBlock.Team.GUEST.label());
    }

    void tick() {
        if (countdown == null) {
            return;
        }
        countdown--;
        if (countdown <= -HOLD * SharedConstants.TICKS_PER_SECOND) {
            countdown = null;
            changed();
        } else if (countdown % SharedConstants.TICKS_PER_SECOND == 0) {
            if (countdown > 0) {
                beep();
            } else if (countdown == 0) {
                buzz();
            }
            changed();
        }
    }

    /** The beep for each new number, at the board in the world. */
    private void beep() {
        if (level instanceof ServerLevel server) {
            Vec3 at = whereInWorld();
            float pitch = countdown <= LAST_SECONDS * SharedConstants.TICKS_PER_SECOND ? LAST_BEEP_PITCH : BEEP_PITCH;
            server.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, BEEP_VOLUME, pitch);
            server.sendParticles(ParticleTypes.NOTE, at.x, at.y + 0.7, at.z, 1, 0, 0, 0, 0);
        }
    }

    /** The buzzer at nought: ready or not, here I come. */
    private void buzz() {
        if (level instanceof ServerLevel server) {
            Vec3 at = whereInWorld();
            server.playSound(null, at.x, at.y, at.z, AerolargeRegistry.SCOREBOARD_BUZZER.get(), SoundSource.BLOCKS, BEEP_VOLUME, 1.0F);
            server.sendParticles(ParticleTypes.FIREWORK, at.x, at.y, at.z, SPARKS, SPARK_SPREAD, SPARK_SPREAD, SPARK_SPREAD, SPARK_SPEED);
        }
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
        if (countdown != null) {
            tag.putInt("Countdown", countdown);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        home = tag.getInt("Home");
        guest = tag.getInt("Guest");
        countdown = tag.contains("Countdown") ? tag.getInt("Countdown") : null;
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
