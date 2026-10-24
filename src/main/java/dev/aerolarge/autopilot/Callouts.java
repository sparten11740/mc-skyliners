package dev.aerolarge.autopilot;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.FirstFlight;
import dev.aerolarge.compat.SableFlight;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The flight deck's synthetic voice, watching the assembled airliner whether the autopilot flies it or the pilots
 * do: "V one" and "rotate" down the runway and "positive rate" climbing away; on the way down the radio altitude
 * (the wheels' height above the ground below) called out from a hundred to ten and "retard" just before touchdown;
 * and "bank angle" and "pull up" when it banks too far or sinks too fast too low, politely. Each is heard by everyone
 * aboard, following them about the cabin. The same calm, evenly pitched voice makes the cabin announcements (see
 * {@link CabinAnnouncements}). Climbing away and touching down gently are milestones for everyone aboard, too (see
 * {@link FirstFlight}).
 *
 * <p>A callout cannot wait, so it takes the flight deck's {@link Airtime} whatever else is being said; air traffic
 * control asks first whether one is due soon (see {@link #quietFor}), so as not to be talked over.
 */
final class Callouts {
    /** Radio altitudes called out on the way down, in blocks, from the highest. */
    private static final int[] HEIGHTS = {100, 50, 40, 30, 20, 10};
    private static final List<Holder<SoundEvent>> HEIGHT_CALLS = List.of(
        AerolargeRegistry.CALLOUT_100, AerolargeRegistry.CALLOUT_50, AerolargeRegistry.CALLOUT_40,
        AerolargeRegistry.CALLOUT_30, AerolargeRegistry.CALLOUT_20, AerolargeRegistry.CALLOUT_10
    );
    /** "Retard" (throttles to idle) is called at this radio altitude on the way down. */
    private static final double RETARD = 4;
    /** Below this radio altitude the wheels are on the ground; the autopilot lands by it too. */
    static final double ON_GROUND = 1.5;
    /** "V one" at this share of the rotation speed. */
    private static final double V1 = 0.85;
    /** Climbing faster than this, in blocks a second, and higher than this, after rotation: "positive rate". */
    private static final double POSITIVE_RATE = 1.5;
    private static final double POSITIVE_RATE_HEIGHT = 4;
    /** Landing callouts are armed once the airliner has been this high. */
    private static final double ARM_HEIGHT = 110;
    /** Banked further than this, in degrees: "bank angle". */
    private static final double BANK_LIMIT = 35;
    /** Sinking faster than this, in blocks a second, lower than this: "pull up". */
    private static final double SINK_LIMIT = 12;
    private static final double PULL_UP_HEIGHT = 40;
    /** Higher than this, in blocks, it has flown: touching down after it is a landing. */
    private static final double FLOWN = 10;
    /** Touching down sinking slower than this, in blocks a second, is a butter landing; the autopilot aims under it. */
    static final double BUTTER_SINK = 1.0;
    /**
     * Ticks between repeats of a warning: long enough to say it and draw breath, tools/generate_sounds.mjs keeping
     * every callout shorter (its CALLOUT_LONGEST).
     */
    private static final int WARNING_REPEAT = 80;
    /**
     * How long a callout is given to be said, in ticks: tools/generate_sounds.mjs keeps every one shorter (its
     * CALLOUT_LONGEST).
     */
    private static final int SPOKEN = 70;
    /**
     * How near a warning's limit the airliner may be and still count as quiet: degrees of bank short of "bank angle",
     * and blocks above the height "pull up" is called under, while sinking at half the rate it is called at.
     */
    private static final double BANK_MARGIN = 10;
    private static final double PULL_UP_MARGIN = 20;
    /** Ticks more than asked for that a height to be called must be away, at the rate it sinks, to count as quiet. */
    private static final int LOOKAHEAD_MARGIN = 20;
    /** Everyone this close to the flight deck hears the callouts: everyone aboard. */
    private static final double HEARING = 64;

    private boolean calledV1;
    private boolean calledRotate;
    private boolean calledPositiveRate;
    private boolean armed;
    private boolean calledRetard;
    private boolean flown;
    private double lastRadio = Double.NaN;
    private double lastClimb;
    private double lastRoll;
    /** Whether it is rolling along the ground, or has rotated and not yet climbed away: before "positive rate". */
    private boolean rolling;
    private int warningCooldown;
    private final Airtime airtime;

    Callouts(Airtime airtime) {
        this.airtime = airtime;
    }

    /** One tick of watching: says whatever the airliner has just come to, to everyone aboard. */
    void watch(ServerLevel level, SableFlight.State state, double wheelsBelow, Supplier<List<ServerPlayer>> aboard) {
        Vec3 position = state.position();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(position.x), Mth.floor(position.z));
        double radio = position.y - 0.5 - wheelsBelow - ground;
        double speed = state.airspeed();
        double climb = state.velocity().y;
        double rotateSpeed = AerolargeConfig.AUTOPILOT_ROTATE_SPEED.get();
        boolean onGround = radio < ON_GROUND;
        double last = Double.isNaN(lastRadio) ? radio : lastRadio;
        lastRadio = radio;
        // How fast it sank the tick before: on the tick the wheels touch, the ground has already stopped it.
        double sink = -lastClimb;
        lastClimb = climb;
        lastRoll = state.roll();
        rolling = !calledPositiveRate && (onGround ? speed >= 3 : calledRotate);
        warningCooldown = Math.max(0, warningCooldown - 1);

        if (radio > FLOWN) {
            flown = true;
        } else if (onGround && last >= ON_GROUND && flown) {
            flown = false;
            if (sink < BUTTER_SINK) {
                aboard.get().forEach(FirstFlight.BUTTER_LANDING::award);
            }
        }

        if (onGround && speed < 3) {
            calledV1 = false;
            calledRotate = false;
            calledPositiveRate = false;
            calledRetard = false;
            armed = false;
            return;
        }
        if (onGround && !calledV1 && speed >= V1 * rotateSpeed) {
            calledV1 = true;
            say(level, position, AerolargeRegistry.CALLOUT_V1);
        } else if (onGround && calledV1 && !calledRotate && speed >= rotateSpeed) {
            calledRotate = true;
            say(level, position, AerolargeRegistry.CALLOUT_ROTATE);
        } else if (!onGround && !calledPositiveRate && climb > POSITIVE_RATE && radio > POSITIVE_RATE_HEIGHT) {
            calledPositiveRate = true;
            say(level, position, AerolargeRegistry.CALLOUT_POSITIVE_RATE);
            aboard.get().forEach(FirstFlight.CLEARED_FOR_TAKEOFF::award);
        }

        if (radio > ARM_HEIGHT) {
            armed = true;
            calledRetard = false;
        }
        if (armed && climb < 0) {
            for (int i = 0; i < HEIGHTS.length; i++) {
                if (last > HEIGHTS[i] && radio <= HEIGHTS[i]) {
                    say(level, position, HEIGHT_CALLS.get(i));
                }
            }
            if (!calledRetard && last > RETARD && radio <= RETARD) {
                calledRetard = true;
                say(level, position, AerolargeRegistry.CALLOUT_RETARD);
            }
        }

        if (warningCooldown == 0 && !onGround) {
            if (Math.abs(state.roll()) > BANK_LIMIT) {
                warningCooldown = WARNING_REPEAT;
                say(level, position, AerolargeRegistry.CALLOUT_BANK_ANGLE);
            } else if (radio < PULL_UP_HEIGHT && climb < -SINK_LIMIT) {
                warningCooldown = WARNING_REPEAT;
                say(level, position, AerolargeRegistry.CALLOUT_PULL_UP);
            }
        }
    }

    /**
     * Whether nothing is foreseen to be called out over the next so many ticks, from how the airliner went on the last
     * tick watched: not along the ground, nor lifting off before "positive rate"; not coming down where a height or
     * "retard" will be called within that time at the rate it sinks; and not banked or sinking near enough to a
     * warning's limit for one to come. Nothing but a warning out of the blue can then be called.
     */
    boolean quietFor(int ticks) {
        if (Double.isNaN(lastRadio) || rolling) {
            return false;
        }
        if (Math.abs(lastRoll) > BANK_LIMIT - BANK_MARGIN || lastRadio < PULL_UP_HEIGHT + PULL_UP_MARGIN && lastClimb < -SINK_LIMIT / 2) {
            return false;
        }
        if (!armed || lastClimb >= 0) {
            return true;
        }
        double lowest = lastRadio + lastClimb * (ticks + LOOKAHEAD_MARGIN) / 20.0;
        for (int height : HEIGHTS) {
            if (lastRadio > height && lowest <= height) {
                return false;
            }
        }
        return calledRetard || lastRadio <= RETARD || lowest > RETARD;
    }

    /** Plays the callout to everyone aboard, each hearing it from where they stand as they move about. */
    private void say(ServerLevel level, Vec3 position, Holder<SoundEvent> sound) {
        airtime.take(level.getGameTime(), SPOKEN);
        long seed = level.getRandom().nextLong();
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(position) < HEARING * HEARING) {
                player.connection.send(new ClientboundSoundEntityPacket(sound, SoundSource.VOICE, player, 1.0F, 1.0F, seed));
            }
        }
    }
}
