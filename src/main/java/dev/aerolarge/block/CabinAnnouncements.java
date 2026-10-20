package dev.aerolarge.block;

import dev.aerolarge.Aboard;
import dev.aerolarge.AerolargeRegistry;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The flight deck's voice over the cabin PA, each announcement opening with the cabin chime: cabin crew to their seats
 * when the autopilot is set to take off, and again when it is set to land, and a word for the passengers once it has
 * landed and stopped. Everyone aboard hears it, wherever they are in the cabin, as they move about. Crews switch the
 * seatbelt signs on for take-off and landing, which here also opens the curtains (see
 * {@link SeatbeltSwitchBlockEntity}), and off again once parked, letting everyone up; the announcements do the same at
 * the airliner's seatbelt switch.
 *
 * <p>One at a time, and not over and over: one asked for while anything else is said on the flight deck (another
 * announcement, a callout or air traffic control, all sharing its {@link Airtime}) waits for it to end (only the latest
 * one asked for waits), and one made in the last {@link #REPEAT} ticks is not made again, so pressing the autopilot
 * screen's buttons again and again does not make a chorus of them.
 */
final class CabinAnnouncements {
    /** What it announces, and whether the seatbelt signs come on or go off with it. */
    enum Announcement {
        TAKEOFF(AerolargeRegistry.ANNOUNCEMENT_TAKEOFF, true),
        LANDING(AerolargeRegistry.ANNOUNCEMENT_LANDING, true),
        ARRIVAL(AerolargeRegistry.ANNOUNCEMENT_ARRIVAL, false);

        private final Holder<SoundEvent> sound;
        private final boolean seatbelts;

        Announcement(Holder<SoundEvent> sound, boolean seatbelts) {
            this.sound = sound;
            this.seatbelts = seatbelts;
        }
    }

    /**
     * How long an announcement is given to play, chime and all, in ticks: tools/generate_sounds.mjs keeps every one
     * shorter (its ANNOUNCEMENT_LONGEST).
     */
    private static final int LONGEST = 15 * 20;
    /** Ticks after an announcement is asked for within which the same one is not made again: a minute. */
    private static final int REPEAT = 60 * 20;
    /**
     * How far the airliner's seatbelt switch can be from its autopilot, in blocks: both are on the flight deck, and
     * another airliner's are far off, out in the world or in a plot of its own.
     */
    private static final double FLIGHT_DECK = 24;

    /** When each announcement was last asked for, in game time. */
    private final Map<Announcement, Long> asked = new EnumMap<>(Announcement.class);
    private final Airtime airtime;
    @Nullable
    private Announcement waiting;

    CabinAnnouncements(Airtime airtime) {
        this.airtime = airtime;
    }

    /**
     * Makes the announcement now, or once whatever is being said on the flight deck has ended; not again so soon after
     * it was last asked for.
     */
    void announce(Announcement announcement, BlockEntity autopilot) {
        if (!(autopilot.getLevel() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        Long last = asked.get(announcement);
        if (last != null && now - last < REPEAT) {
            return;
        }
        asked.put(announcement, now);
        if (!airtime.free(now)) {
            waiting = announcement;
        } else {
            play(level, autopilot, announcement);
        }
    }

    /** Each tick: makes the one waiting, once whatever is being said on the flight deck has ended. */
    void tick(BlockEntity autopilot) {
        if (waiting != null && autopilot.getLevel() instanceof ServerLevel level && airtime.free(level.getGameTime())) {
            Announcement next = waiting;
            waiting = null;
            play(level, autopilot, next);
        }
    }

    private void play(ServerLevel level, BlockEntity autopilot, Announcement announcement) {
        airtime.take(level.getGameTime(), LONGEST);
        for (SeatbeltSwitchBlockEntity seatbelts : SeatbeltSwitchBlockEntity.all(level)) {
            if (seatbelts.getBlockPos().closerThan(autopilot.getBlockPos(), FLIGHT_DECK)) {
                seatbelts.turn(announcement.seatbelts);
            }
        }
        long seed = level.getRandom().nextLong();
        for (ServerPlayer player : Aboard.players(autopilot)) {
            player.connection.send(new ClientboundSoundEntityPacket(announcement.sound, SoundSource.VOICE, player, 1.0F, 1.0F, seed));
        }
    }
}
