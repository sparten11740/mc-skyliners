package dev.aerolarge.autopilot;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * Who has the floor on the flight deck: the callouts, the cabin announcements and air traffic control share it, so that
 * none of them talks over another, and whatever else aboard is said in its hearing (see
 * {@link AutopilotBlockEntity#holdFloor}). Whatever starts saying something takes it for as long as that lasts. The
 * cabin announcements and air traffic control wait for it, and for any callout foreseen; a callout, which cannot wait,
 * takes it, cutting short whatever announcement or transmission it comes in over.
 */
final class Airtime {
    /** When what is being said ends, in game time. */
    private long until;
    /** How to cut short what is being said, for a callout that must be heard; null for nothing that can be. */
    @Nullable
    private Runnable cut;

    /** Something said to some players, which can be cut short by stopping it for each of them. */
    record Said(List<ServerPlayer> heard, ResourceLocation sound) implements Runnable {
        @Override
        public void run() {
            for (ServerPlayer player : heard) {
                player.connection.send(new ClientboundStopSoundPacket(sound, SoundSource.VOICE));
            }
        }
    }

    /** Whether nothing is being said at this game time. */
    boolean free(long now) {
        return now >= until;
    }

    /** Whether nothing has been said for at least this many ticks before this game time. */
    boolean quietFor(long now, int ticks) {
        return now >= until + ticks;
    }

    /** Something starts being said at this game time, lasting this many ticks, that cannot be cut short. */
    void take(long now, int ticks) {
        take(now, ticks, null);
    }

    /** Something starts being said at this game time, lasting this many ticks, cut short as given if need be. */
    void take(long now, int ticks, @Nullable Runnable cut) {
        if (now + ticks >= until) {
            this.cut = cut;
        }
        until = Math.max(until, now + ticks);
    }

    /** A callout is to be said at this game time: whatever announcement or transmission is being said is cut short. */
    void interrupt(long now) {
        if (cut != null && now < until) {
            cut.run();
            until = now;
        }
        cut = null;
    }
}
