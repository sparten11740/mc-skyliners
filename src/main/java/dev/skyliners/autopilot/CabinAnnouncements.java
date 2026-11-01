package dev.skyliners.autopilot;

import dev.skyliners.Aboard;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.Announcement;
import dev.skyliners.block.CabinLightsBlock;
import dev.skyliners.block.SeatbeltSwitchBlockEntity;
import java.util.EnumMap;
import java.util.List;
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
 * The flight deck's voice over the cabin PA, as on an airline's recorded announcements, each opening with the cabin
 * chime (see {@link Announcement}): cabin crew to their seats when the autopilot is set to take off, and again when it
 * is set to land, and a word for the passengers once it has landed and stopped; and whatever the announcement panel
 * asks for. Everyone aboard hears it, wherever they are in the cabin, as they move about. Crews switch the seatbelt
 * signs on for take-off and landing, which here also opens the curtains (see {@link SeatbeltSwitchBlockEntity}), and
 * off again once parked, letting everyone up; the announcements that call for it do the same at the airliner's seatbelt
 * switch, and the one for a sleep turns its cabin lights down.
 *
 * <p>One at a time, and not over and over: one asked for while anything else is said on the flight deck (another
 * announcement, a callout or air traffic control, all sharing its {@link Airtime}), or while a callout is foreseen
 * before it would end, waits for that to end (only the latest one asked for waits), and one made in the last
 * {@link #REPEAT} ticks is not made again, so pressing the autopilot screen's buttons again and again does not make a
 * chorus of them. Each holds the floor for as long as it runs (see {@link VoiceLength}); a callout nobody could
 * foresee, a warning, cuts it short. Where each one stands in that, the panel shows.
 */
public final class CabinAnnouncements {
    /**
     * How long an announcement whose sound cannot be read is given to play, chime and all, in ticks:
     * tools/generate_sounds.mjs keeps every one shorter (its ANNOUNCEMENT_LONGEST).
     */
    private static final int LONGEST = 15 * 20;
    /** Ticks after an announcement is asked for within which the same one is not made again: twenty seconds. */
    private static final int REPEAT = 20 * 20;
    /**
     * How long an announcement waits for anyone to be aboard to hear it, in ticks after it was asked for: a zeppelin
     * just assembled to take off carries nobody for a moment, until those aboard are found riding it, while the server
     * catches up on the ticks the assembly took.
     */
    private static final int AUDIENCE_WAIT = 10 * 20;

    /** When each announcement was last asked for, in game time. */
    private final Map<Announcement, Long> asked = new EnumMap<>(Announcement.class);
    private final Airtime airtime;
    private final Callouts callouts;
    @Nullable
    private Announcement waiting;
    /** The announcement last made, the game time at which it ends, and whom it was played to. */
    @Nullable
    private Announcement onAir;
    private long onAirUntil;
    @Nullable
    private Airtime.Said heard;

    CabinAnnouncements(Airtime airtime, Callouts callouts) {
        this.airtime = airtime;
        this.callouts = callouts;
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
        if (!clear(now, announcement, autopilot) || !play(level, autopilot, announcement)) {
            waiting = announcement;
        }
    }

    /** Whether an announcement may be made at this game time: nothing else said, nor a callout due before it ends. */
    private boolean clear(long now, Announcement announcement, BlockEntity autopilot) {
        return airtime.free(now) && !callouts.mayCall(now, VoiceLength.ticks(sound(announcement, autopilot), LONGEST));
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

    /** Whether an announcement waits its turn, or for someone aboard to hear it. */
    boolean waiting() {
        return waiting != null;
    }

    /**
     * An announcement still waiting its turn let go unsaid: the take-off announcement, as a rocket's countdown begins
     * without it, would otherwise be made once she had lifted off.
     */
    void letGo(Announcement announcement) {
        if (waiting == announcement) {
            waiting = null;
        }
    }

    /** Each tick: makes the one waiting, once whatever is being said on the flight deck has ended. */
    void tick(BlockEntity autopilot) {
        if (waiting != null && autopilot.getLevel() instanceof ServerLevel level && clear(level.getGameTime(), waiting, autopilot) && play(level, autopilot, waiting)) {
            waiting = null;
        }
    }

    /**
     * Makes the announcement to everyone aboard; whether it was made, which it is not while nobody is aboard to hear
     * it, for a moment after it was asked for.
     */
    private boolean play(ServerLevel level, BlockEntity autopilot, Announcement announcement) {
        long now = level.getGameTime();
        List<ServerPlayer> aboard = Aboard.players(autopilot);
        if (aboard.isEmpty() && now - asked.getOrDefault(announcement, now) < AUDIENCE_WAIT) {
            return false;
        }
        Holder<SoundEvent> sound = sound(announcement, autopilot);
        int length = VoiceLength.ticks(sound, LONGEST);
        heard = new Airtime.Said(aboard, sound.value().getLocation());
        airtime.take(now, length, this::cutShort);
        onAir = announcement;
        onAirUntil = now + length;
        if (announcement.seatbelts != null) {
            seatbelts(level, autopilot, announcement.seatbelts);
        }
        if (announcement.lights != null) {
            CabinLightsBlock.set(level, autopilot, announcement.lights);
        }
        long seed = level.getRandom().nextLong();
        for (ServerPlayer player : aboard) {
            player.connection.send(new ClientboundSoundEntityPacket(sound, SoundSource.VOICE, player, 1.0F, 1.0F, seed));
        }
        return true;
    }

    /** What an announcement says aboard this craft: a rocket arrives as a spacecraft, not as an aircraft. */
    private static Holder<SoundEvent> sound(Announcement announcement, BlockEntity autopilot) {
        boolean rocket = autopilot instanceof AutopilotBlockEntity pilot && pilot.craft() == Craft.ROCKET;
        return rocket && announcement == Announcement.ARRIVAL ? SkylinersRegistry.ANNOUNCEMENT_ARRIVAL_ROCKET : announcement.sound;
    }

    /** The announcement being made is cut short for a callout: stopped for everyone who heard it, and over. */
    private void cutShort() {
        if (heard != null) {
            heard.run();
        }
        onAirUntil = 0;
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
