package dev.aerolarge.block;

import dev.aerolarge.Aboard;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The flight deck's voice over the cabin PA, each announcement opening with the cabin chime (see {@link Announcement}):
 * cabin crew to their seats when the autopilot is set to take off, and again when it is set to land, and a word for the
 * passengers once it has landed and stopped; and whatever the announcement panel asks for. Everyone aboard hears it,
 * wherever they are in the cabin, as they move about. Crews switch the seatbelt signs on for take-off and landing,
 * which here also opens the curtains (see {@link SeatbeltSwitchBlockEntity}), and off again once parked, letting
 * everyone up; the announcements that call for it do the same at the airliner's seatbelt switch, and the one for a
 * sleep turns its cabin lights down.
 *
 * <p>One at a time, and not over and over: one asked for while anything else is said on the flight deck (another
 * announcement, a callout or air traffic control, all sharing its {@link Airtime}) waits for it to end (only the latest
 * one asked for waits), and one made in the last {@link #REPEAT} ticks is not made again, so pressing the autopilot
 * screen's buttons again and again does not make a chorus of them. Where each one stands in that, the panel shows.
 */
final class CabinAnnouncements {
    /**
     * How long an announcement is given to play, chime and all, in ticks: tools/generate_sounds.mjs keeps every one
     * shorter (its ANNOUNCEMENT_LONGEST).
     */
    private static final int LONGEST = 15 * 20;
    /** Ticks after an announcement is asked for within which the same one is not made again: twenty seconds. */
    private static final int REPEAT = 20 * 20;

    /** When each announcement was last asked for, in game time. */
    private final Map<Announcement, Long> asked = new EnumMap<>(Announcement.class);
    private final Airtime airtime;
    @Nullable
    private Announcement waiting;
    /** The announcement last made, and the game time at which the time it was given to play ends. */
    @Nullable
    private Announcement onAir;
    private long onAirUntil;

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

    /**
     * Where an announcement stands at this game time: being made, waiting its turn, hushed while another is made or
     * waits, resting after it was asked for until it may be made again, or ready.
     */
    Announcement.Standing standing(Announcement announcement, long now) {
        boolean speaking = onAir != null && now < onAirUntil;
        if (speaking && announcement == onAir) {
            return Announcement.Standing.ON_AIR;
        }
        if (announcement == waiting) {
            return Announcement.Standing.WAITING;
        }
        if (speaking || waiting != null) {
            return Announcement.Standing.HUSHED;
        }
        Long last = asked.get(announcement);
        return last != null && now - last < REPEAT ? Announcement.Standing.RESTING : Announcement.Standing.READY;
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
        long now = level.getGameTime();
        airtime.take(now, LONGEST);
        onAir = announcement;
        onAirUntil = now + LONGEST;
        if (announcement.seatbelts != null) {
            seatbelts(level, autopilot, announcement.seatbelts);
        }
        if (announcement.lights != null) {
            CabinLightsBlock.set(level, autopilot, announcement.lights);
        }
        long seed = level.getRandom().nextLong();
        for (ServerPlayer player : Aboard.players(autopilot)) {
            player.connection.send(new ClientboundSoundEntityPacket(announcement.sound, SoundSource.VOICE, player, 1.0F, 1.0F, seed));
        }
    }

    /** Switches the seatbelt signs on or off at the airliner's seatbelt switch, on the flight deck by its autopilot. */
    private static void seatbelts(ServerLevel level, BlockEntity autopilot, boolean on) {
        for (SeatbeltSwitchBlockEntity seatbelts : SeatbeltSwitchBlockEntity.all(level)) {
            if (seatbelts.getBlockPos().closerThan(autopilot.getBlockPos(), AutopilotBlockEntity.FLIGHT_DECK)) {
                seatbelts.turn(on);
            }
        }
    }
}
