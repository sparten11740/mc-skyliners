package dev.skyliners.autopilot;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.block.Announcement;
import dev.skyliners.compat.SableFlight;
import dev.skyliners.spaceweather.SpaceWeather;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * A craft that stands on feet of its own, comes straight down onto them where it is, and can climb to space (see
 * {@link CraftFlight}): the rocket on her fins and the freighter on her legs. Everyone aboard is welcomed to space as
 * it climbs past its edge (see {@link EdgeOfSpace}) and floats there until its gravity is announced (see
 * {@link SpaceGravity}); it may be sent as high as the rocket's ceiling; and it counts itself no higher than the lowest
 * of its feet.
 */
abstract class StandingFlight extends CraftFlight {
    /** Its welcoming everyone aboard to space as it climbs past its edge. */
    private final EdgeOfSpace space = new EdgeOfSpace();
    /** Its low gravity as it reaches space, until its artificial gravity is announced. */
    private final SpaceGravity gravity = new SpaceGravity();
    /** The space weather it meets far past the black of space. */
    private final SpaceWeather weather = new SpaceWeather();

    /** Its pilot told to come straight down where it is now, giving up wherever it was going. */
    abstract void landHere(AutopilotBlockEntity autopilot, SableFlight.State state);

    /** The lang key of what it says as it comes down, told to land. */
    abstract String comingDownKey();

    /** The lang key of what it says switched off in the air, as it comes down first. */
    abstract String offOnceDownKey();

    /** How high the lowest of its feet stands over the ground, in blocks. */
    abstract double feetOverGround(Level level, AutopilotBlockEntity autopilot);

    /**
     * What it does each tick before it is flown: kept aloft as every ship assembled through Sable is (see
     * {@link #keptAloft}), everyone aboard welcomed to space and floating there as it climbs past its edge, and space
     * weather met far past it; how high it is over the ground, in blocks.
     */
    double aloft(ServerLevel level, AutopilotBlockEntity autopilot, SableFlight.State flight) {
        double radio = keptAloft(level, autopilot, flight);
        space.watch(level, autopilot, flight);
        gravity.tick(level, autopilot, flight);
        weather.tick(level, autopilot, flight);
        return radio;
    }

    /** The space weather it meets far past the black of space. */
    @Override
    SpaceWeather spaceWeather() {
        return weather;
    }

    /** Whether everyone aboard is light now, in space before its artificial gravity is announced (see SpaceGravity). */
    boolean light() {
        return gravity.light();
    }

    /**
     * Told to land, or switched off in the air: it comes straight down where it is now, its landing announced once.
     * Coming down already, it only gives up where it was going for where it is, its phase and the cabin's landing
     * announcement left as they were: the rocket set back to falling in her landing burn would fall faster again, and
     * launch control would call the burn twice.
     */
    @Override
    Component comeDown(AutopilotBlockEntity autopilot, SableFlight.State state) {
        if (autopilot.phase != AutopilotBlockEntity.Phase.DESCENT && autopilot.phase != AutopilotBlockEntity.Phase.LAND) {
            autopilot.announcements.announce(Announcement.LANDING, autopilot);
            autopilot.phase = AutopilotBlockEntity.Phase.DESCENT;
        }
        landHere(autopilot, state);
        autopilot.lateral = AutopilotBlockEntity.Lateral.HEADING;
        autopilot.landAtPin = false;
        autopilot.changed();
        return Component.translatable(comingDownKey());
    }

    /**
     * Its thrust is all that holds it up, and it has no controls to be handed to: switched off in the air, coming down
     * already or not, it comes down where it is first and switches off on its feet. The sky's lowest note says so to
     * those who cannot read the message: down first.
     */
    @Override
    Component offInTheAir(AutopilotBlockEntity autopilot, SableFlight.State state) {
        comeDown(autopilot, state);
        autopilot.getLevel().playSound(null, autopilot.getBlockPos(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS,
            AutopilotBlockEntity.NOTE_VOLUME, AutopilotBlockEntity.NOTE_LOW);
        return Component.translatable(offOnceDownKey());
    }

    /** Coming straight down to land, as well as in the last of it. */
    @Override
    boolean landing(AutopilotBlockEntity.Phase phase) {
        return phase == AutopilotBlockEntity.Phase.DESCENT;
    }

    /**
     * The rocket's own, far over the top of the world, where there is only sky to climb through
     * ({@link SkylinersConfig#ROCKET_CEILING}).
     */
    @Override
    double ceiling(Level level, AutopilotBlockEntity autopilot, double highest) {
        return SkylinersConfig.ROCKET_CEILING.get();
    }

    /**
     * No higher than the lowest of its feet: standing on them, with the ground lower under its control head, it would
     * seem to be in the air, and could never finish landing or be switched off.
     */
    @Override
    double radio(Level level, AutopilotBlockEntity autopilot, double radio) {
        return Math.min(radio, feetOverGround(level, autopilot));
    }
}
