package dev.skyliners.autopilot;

import dev.skyliners.Aboard;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.compat.SableFlight;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * A landing the freighter cannot finish (see {@link FreighterFlight}). Told to land where she is, or landing by herself
 * with nobody aboard, she does not look for ground first, and coming down on something her feet do not reach, a block
 * taller than her belly's clearance or another ship, she would rest on it with her legs in the air, never touching
 * and never switched off. So her height is watched as she comes down: her feet may be anywhere over any ground, but
 * set to come down with her legs down she always sinks, so stopped for some seconds she is held up by something, or
 * has touched and cannot settle.
 *
 * <p>She gives such a landing up, says so and climbs back clear, then looks for ground round there as she does round a
 * pin (see {@link PadSearch}), never again where she stopped, and holds over it if there is none. A second landing
 * given up round one pin she does not try a third: she holds, and says so. With nobody aboard to tell her otherwise,
 * or told to switch off, she instead lets go on what stopped her: she is switched off, and counts as down from then
 * on, so she does not go up and down over it for as long as she is loaded.
 */
final class BalkedLanding {
    /**
     * How long she may have stopped coming down before she gives the landing up, in ticks (five seconds): well past the
     * two seconds she takes to run her lift down and stand still once her feet touch (see {@link FreighterPilot}).
     */
    private static final int GIVE_UP_TICKS = 100;
    /**
     * How far she must sink within {@link #GIVE_UP_TICKS} to count as coming down, in blocks, and how far rise to count
     * as still climbing out of whatever she was doing as she was told to land. Set to land she sinks a block a second;
     * resting on something and leaning over it she only creeps down, up to 0.16 blocks a second as measured, which is
     * less than this in that time, and rocks by less than either.
     */
    private static final double SINKING = 1.5;
    private static final double RISING = 1;
    /** How many landings she may give up round one pin before she stops trying there, this one among them. */
    private static final int TRIES = 2;
    /** How far apart two landings given up may be and still be round one pin, in blocks. */
    private static final double SAME_PIN = 16;
    /** How far she climbs back, in blocks: as far as she rises straight up lifting off, clear of what is round her. */
    private static final double CLIMB_BACK = 12;
    /**
     * How fast she goes to the ground she finds, at least, in blocks a second, even set to hover where she stood: as
     * fast as she settles onto a spot.
     */
    private static final double LEAST_SPEED = 4;
    /** The longest a line of her computer's may hold the floor, in ticks, once its length is known. */
    private static final int VOICE_LONGEST = 90;

    /** The height she was last seen at while coming down, at the last count of how long she has stopped, in blocks. */
    private double height;
    /** How many ticks in a row she has stopped coming down, set to. */
    private int stoppedFor;
    /** How many landings she has given up round the pin last noted, and where that pin is. */
    private int givenUp;
    private double pinX;
    private double pinZ;
    /** Whether she was told to switch off in the air, and so comes down to do it wherever she can. */
    private boolean offOnceDown;
    /** Whether she was let go resting on something her feet do not reach, which is down as far as she will come. */
    private boolean resting;

    /**
     * Each tick she flies, whether she has stopped coming down with her landing unfinished, from her phase, whether her
     * legs are down to land on, and her height, in blocks: a hover waiting for her legs is not stopping, nor is sinking
     * however slowly, nor climbing out of whatever she was doing as she was told to land.
     */
    boolean watch(AutopilotBlockEntity.Phase phase, boolean legsDown, double height) {
        boolean landing = phase == AutopilotBlockEntity.Phase.DESCENT || phase == AutopilotBlockEntity.Phase.LAND;
        boolean comingDown = legsDown && landing;
        if (!comingDown || height < this.height - SINKING || height > this.height + RISING) {
            this.height = height;
            stoppedFor = 0;
            return false;
        }
        stoppedFor++;
        return stoppedFor >= GIVE_UP_TICKS;
    }

    /**
     * She has stopped coming down (see {@link #watch}): she lets go where she is if she was told to switch off, or
     * has tried enough with nobody aboard to tell her otherwise; otherwise she climbs back and looks for ground round
     * there, or, having tried enough, holds over it.
     */
    void giveUp(ServerLevel level, AutopilotBlockEntity autopilot, SableFlight.State flight, FreighterPilot pilot) {
        stoppedFor = 0;
        Vec3 middle = pilot.middle(flight);
        boolean pinned = autopilot.lateral == AutopilotBlockEntity.Lateral.DESTINATION;
        count(pinned ? autopilot.orbitX : middle.x, pinned ? autopilot.orbitZ : middle.z);
        boolean enough = givenUp >= TRIES;
        if (offOnceDown || enough && Aboard.players(autopilot).isEmpty()) {
            letGo(level, autopilot, pilot);
            return;
        }
        if (!pinned) {
            autopilot.lateral = AutopilotBlockEntity.Lateral.DESTINATION;
            autopilot.orbitX = middle.x;
            autopilot.orbitZ = middle.z;
        }
        autopilot.targetAltitude = flight.position().y + CLIMB_BACK;
        autopilot.landAtPin = !enough;
        pilot.balked(middle);
        if (enough) {
            hold(level, autopilot);
        } else {
            goRound(level, autopilot);
        }
        autopilot.changed();
    }

    /** A landing given up round a pin: one more than before round the same one, or the first round a new one. */
    private void count(double x, double z) {
        givenUp = Math.hypot(x - pinX, z - pinZ) <= SAME_PIN ? givenUp + 1 : 1;
        pinX = x;
        pinZ = z;
    }

    /** She climbs back to look for ground round there, a rising note saying so to those who cannot read. */
    private static void goRound(ServerLevel level, AutopilotBlockEntity autopilot) {
        autopilot.targetSpeed = Math.max(autopilot.targetSpeed, LEAST_SPEED);
        autopilot.goAround(level);
        sayAboard(autopilot, SkylinersRegistry.FREIGHTER_COMPUTER_BALKED,
            Component.translatable("message.skyliners.autopilot.balked_freighter"));
    }

    /** She climbs back and holds over it, the sky's lowest note saying so to those who cannot read. */
    private static void hold(ServerLevel level, AutopilotBlockEntity autopilot) {
        autopilot.callOffLanding();
        level.playSound(null, autopilot.getBlockPos(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS,
            AutopilotBlockEntity.NOTE_VOLUME, AutopilotBlockEntity.NOTE_LOW);
        sayAboard(autopilot, SkylinersRegistry.FREIGHTER_COMPUTER_HOLDING,
            Component.translatable("message.skyliners.autopilot.balked_holding_freighter"));
    }

    /** She stays on what stopped her and is switched off there, with her bell. */
    private void letGo(ServerLevel level, AutopilotBlockEntity autopilot, FreighterPilot pilot) {
        resting = true;
        pilot.stop(level, autopilot.getBlockPos());
        autopilot.settle(level);
        Component said = Component.translatable("message.skyliners.autopilot.balked_off_freighter");
        Aboard.players(autopilot).forEach(player -> player.displayClientMessage(said, false));
    }

    /** Her computer says a line to everyone aboard, over her loudspeakers, with the same in words. */
    static void sayAboard(AutopilotBlockEntity autopilot, Holder<SoundEvent> line, Component words) {
        long seed = autopilot.getLevel().getRandom().nextLong();
        for (ServerPlayer player : Aboard.players(autopilot)) {
            player.displayClientMessage(words, false);
            player.connection.send(new ClientboundSoundEntityPacket(line, SoundSource.VOICE, player, 1.0F, 1.0F, seed));
        }
        autopilot.holdFloor(VoiceLength.ticks(line, VOICE_LONGEST));
    }

    /** Told to switch off in the air: she lets go on whatever she comes down on, rather than climbing back off it. */
    void offOnceDown() {
        offOnceDown = true;
    }

    /** Her landing called off by new settings: told to switch off or not, she flies them now. */
    void calledOff() {
        offOnceDown = false;
    }

    /** Engaged afresh: nothing is held against the next landing, and she is no longer down on what stopped her. */
    void forget() {
        stoppedFor = 0;
        givenUp = 0;
        offOnceDown = false;
        resting = false;
    }

    /** Whether she was let go resting on something her feet do not reach (see {@link #giveUp}). */
    boolean resting() {
        return resting;
    }

    /** Whether she rests so, and whether she is to switch off once down, into her flight's save. */
    void save(CompoundTag tag) {
        tag.putBoolean("RestingOffHerLegs", resting);
        tag.putBoolean("OffOnceDown", offOnceDown);
    }

    /** Both as saved; neither, in a save from before she could. */
    void load(CompoundTag tag) {
        resting = tag.getBoolean("RestingOffHerLegs");
        offOnceDown = tag.getBoolean("OffOnceDown");
    }
}
