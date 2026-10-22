package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.airport.Sky;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The weather screen's report (see {@link AtisScreenBlock}): which information it is on, a letter that steps on from
 * A to Z and round again each time the sky over it changes, as a real airfield's ATIS steps its letter with each new
 * report, so pilots can say which one they have heard. The server watches the sky and steps the letter; the client is
 * sent it to draw.
 */
public class AtisScreenBlockEntity extends BlockEntity {
    /** How often the server looks at the sky, in ticks. */
    private static final int LOOK_EVERY = 20;
    private static final int LETTERS = 26;

    /** The information's letter, from 0 for A. */
    private int letter;
    /** The sky the letter was last stepped for; none until the server has first looked. */
    @Nullable
    private Sky reported;

    public AtisScreenBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.ATIS_SCREEN_ENTITY.get(), pos, state);
    }

    /** The information's letter: 'A' for the first report. */
    public char letter() {
        return (char) ('A' + letter);
    }

    /** On the server, every so often: a new report, under the next letter, once the sky has changed. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, AtisScreenBlockEntity screen) {
        if (level.getGameTime() % LOOK_EVERY != 0) {
            return;
        }
        Sky now = Sky.at(level, pos);
        if (now == screen.reported) {
            return;
        }
        if (screen.reported != null) {
            screen.letter = (screen.letter + 1) % LETTERS;
        }
        screen.reported = now;
        screen.setChanged();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Letter", letter);
        if (reported != null) {
            tag.putString("Sky", reported.name());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        letter = Math.floorMod(tag.getInt("Letter"), LETTERS);
        reported = Sky.named(tag.getString("Sky"));
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
