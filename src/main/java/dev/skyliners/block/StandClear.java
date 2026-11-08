package dev.skyliners.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The wait of something that moves aboard a ship for whoever stands where it would go, as the freighter's boarding ramp
 * and a zeppelin's or a rocket's airstairs each keep one, beside its {@link Hatch}. Held up, it says so with a chime,
 * then looks now and then whether they have stepped clear; what counts as clear is for whatever keeps it to judge. Only
 * whether it is held is saved: loaded, it looks again at once.
 */
final class StandClear {
    /** How often it chimes again while it waits, for one that chimes only as it begins to. */
    static final int CHIME_ONCE = 0;
    /** The chime of stairs or doors waiting for someone to step clear: how loud, and its pitch. */
    static final float WAITING_VOLUME = 1.0F;
    static final float WAITING_PITCH = 0.7F;
    /** While it waits, how often it looks again whether they have stepped clear, in ticks. */
    private static final int CLEAR_CHECK_TICKS = 10;

    /** How often it chimes again while it waits, in ticks, so whoever holds it hears that it waits for them. */
    private final int chimeAgainTicks;
    /** Someone stands where it would go, and it waits for them; the chime has sounded. */
    private boolean held;
    private int cooldown;
    /** How long it has waited so, in ticks, for its chime to sound again; not saved. */
    private int waited;

    StandClear(int chimeAgainTicks) {
        this.chimeAgainTicks = chimeAgainTicks;
    }

    /** Whether it waits for someone to step clear. */
    boolean held() {
        return held;
    }

    /** Someone stands where it would go: it waits, with a chime from a place, looking again in a while. */
    void hold(ServerLevel level, BlockPos chimeFrom) {
        held = true;
        cooldown = CLEAR_CHECK_TICKS;
        waited = 0;
        chime(level, chimeFrom);
    }

    /** It waits no longer, its way clear or its work called off. */
    void release() {
        held = false;
    }

    /**
     * A tick of waiting, the chime sounding from a place whenever it is due again: gives whether this is a tick to look
     * whether they have stepped clear.
     */
    boolean looksAgain(ServerLevel level, BlockPos chimeFrom) {
        waited++;
        if (chimeAgainTicks != CHIME_ONCE && waited % chimeAgainTicks == 0) {
            chime(level, chimeFrom);
        }
        if (--cooldown > 0) {
            return false;
        }
        cooldown = CLEAR_CHECK_TICKS;
        return true;
    }

    private static void chime(ServerLevel level, BlockPos from) {
        level.playSound(null, from, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, WAITING_VOLUME, WAITING_PITCH);
    }

    void save(CompoundTag tag) {
        tag.putBoolean("Held", held);
    }

    void load(CompoundTag tag) {
        held = tag.getBoolean("Held");
    }
}
