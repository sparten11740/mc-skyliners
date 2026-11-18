package dev.skyliners.autopilot;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.compat.SableFlight;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * Hopping a stuck airliner free: with its engines running and its brakes off, yet standing still, held by whatever it
 * has run into or sunk into. Wherever it rests: on the ground, on trees, on a roof, its nose out over a drop; standing
 * still for a while, it rests on something, as no airliner stands still in the air. Asked from the autopilot's settings
 * screen, the control jets that push up (under the wings and the tailplane) fire for a moment, taking the weight off
 * the wheels so the engines can move it again (the autopilot sets them, see {@link AutopilotBlockEntity}). Those jets
 * all push from behind the centre of mass, so at full together they would tip the airliner onto its nose: the
 * tailplane's hold the nose where it stood, under it at full while the nose stays up, eased off as it dips and pushing
 * the tail down past that.
 *
 * <p>Stuck means slower than {@link #STILL} for {@link #STUCK_TICKS} ticks with the engines straining. A burst lasts
 * {@link #BURST} ticks, and goes on for as long as the button is held (see {@link #keep}), so the airliner can be
 * lifted over whatever it ran into, a house in a village say; but never once it would coast up {@link #HOP} blocks.
 * Then the jets rest for {@link #REST} ticks before a tap hops it again. Held on, the button hops it again as soon as
 * it has come back down and stood still for {@link #HELD_STUCK_TICKS}, without the rest, so holding it hops the
 * airliner over and over until it is free.
 */
final class Hop {
    /**
     * Slower than this, in blocks a second, the airliner stands still; this many ticks of it, straining, and it is
     * stuck.
     */
    private static final double STILL = 1;
    private static final int STUCK_TICKS = 40;
    /**
     * The burst a tap of the button fires, in ticks: long enough for the jets to take the weight off the wheels and
     * the tailplane to settle the nose, and for the engines to pull the airliner clear meanwhile.
     */
    private static final int BURST = 50;
    /**
     * How long a burst goes on after the button was last held, in ticks: the screen asks every tick it is held, so
     * this only bridges a packet come late.
     */
    private static final int HELD = 10;
    /**
     * How long the airliner has to stand still, in ticks, to be hopped again while the button is held on after a burst:
     * longer than it hangs still at the top of a hop, so it is hopped from where it comes to rest, never higher and
     * higher in mid-air.
     */
    private static final int HELD_STUCK_TICKS = 10;
    /**
     * How high the airliner hops at most, in blocks: over a house in its way, not up into the sky. The burst ends once
     * it would coast up this far.
     */
    private static final double HOP = 12;
    /** How long the jets rest after a burst, in ticks. */
    private static final int REST = 100;
    /**
     * Tailplane signal taken off per degree the nose has dipped, and per degree a second it is dipping (damping).
     * Stiffer than in flight: on the ground the airliner tips about its nose wheel, far from its centre of mass.
     */
    private static final double HOLD_GAIN = 4;
    private static final double HOLD_DAMPING = 2.5;
    /**
     * The roar of the jets on the flight deck: the engines' sound, pitched up from its four seconds to about the
     * burst's length.
     */
    private static final float ROAR_PITCH = 1.6F;

    /** How the airliner stands this tick, as far as hopping it free goes. */
    record Stance(long now, double speed, boolean engines, boolean braked) {
    }

    /** What keeps the jets from hopping the airliner free, in the order it is said; NONE when they can. */
    enum Hold {
        NONE, FIRING, RESTING, ENGINES_OFF, BRAKED, MOVING;

        /** The key of what the autopilot says: that it hops the airliner free, or why it cannot. */
        String message() {
            return "message.skyliners.autopilot.unstuck" + (this == NONE ? "" : "." + name().toLowerCase(Locale.ROOT));
        }
    }

    /** Ticks it has stood still with the engines straining. */
    private int still;
    /** Ticks of the burst still to go; 0 while the jets are not firing for it. */
    private int burst;
    /** The game time the jets have rested till after the last burst. */
    private long rested = Long.MIN_VALUE;
    /** The game time the button was last asked, to tell it held on from a fresh tap; never yet, so long ago. */
    private long askedAt = -HELD - 1;
    /** Where the burst started: the nose's attitude, in degrees, and the height, in blocks. */
    private double startPitch;
    private double startHeight;
    private double lastPitch;

    /** One tick of watching how the airliner stands; what keeps the jets from hopping it free now. */
    Hold watch(Stance stance) {
        boolean straining = stance.engines() && !stance.braked();
        still = straining && stance.speed() < STILL && burst == 0 ? Math.min(still + 1, STUCK_TICKS) : 0;
        return hold(stance);
    }

    /** What keeps the jets from hopping the airliner free, standing as it does, if anything. */
    Hold hold(Stance stance) {
        return hold(stance, false);
    }

    /**
     * The button asked at a game time: whether it has been held on since it was last asked, as the screen asks every
     * tick it is held, rather than tapped afresh.
     */
    boolean asked(long now) {
        boolean held = now - askedAt <= HELD;
        askedAt = now;
        return held;
    }

    /**
     * What keeps the jets from hopping the airliner free, standing as it does, if anything; with the button held on,
     * the jets need no rest and the airliner only a moment's standing still.
     */
    Hold hold(Stance stance, boolean held) {
        if (burst > 0) {
            return Hold.FIRING;
        }
        if (!held && stance.now() < rested) {
            return Hold.RESTING;
        }
        if (!stance.engines()) {
            return Hold.ENGINES_OFF;
        }
        if (stance.braked()) {
            return Hold.BRAKED;
        }
        return still < (held ? HELD_STUCK_TICKS : STUCK_TICKS) ? Hold.MOVING : Hold.NONE;
    }

    /** Fires the jets, from how the airliner stands now, with their roar on the flight deck at the autopilot. */
    void start(ServerLevel level, BlockPos autopilot, SableFlight.State state) {
        burst = BURST;
        still = 0;
        startPitch = state.pitch();
        lastPitch = startPitch;
        startHeight = state.position().y;
        level.playSound(null, autopilot, SkylinersRegistry.ENGINE_SOUND.get(), SoundSource.BLOCKS, 1.0F, ROAR_PITCH);
    }

    boolean firing() {
        return burst > 0;
    }

    /** Keeps the burst firing, the button still held. */
    void keep() {
        if (burst > 0) {
            burst = Math.max(burst, HELD);
        }
    }

    /**
     * One tick of a burst: it ends once its time is up and the button let go, or the airliner would coast up high
     * enough; then the jets rest.
     */
    void tick(SableFlight.State state, long now) {
        if (burst == 0) {
            return;
        }
        burst = state.apex() - startHeight >= HOP ? 0 : burst - 1;
        if (burst == 0) {
            rested = now + REST;
        }
    }

    /** Ends a burst at once, the airliner gone from under it; whether one was firing. */
    boolean stop() {
        boolean firing = burst > 0;
        burst = 0;
        still = 0;
        return firing;
    }

    /**
     * The tailplane's jets this tick of a burst, from -15 (over it, pushing the tail down) to 15 (under it, pushing it
     * up): under it at full while the nose stays where it stood, eased off as it dips, and pushing down past that.
     */
    int tail(double pitch) {
        double rate = (pitch - lastPitch) * 20;
        lastPitch = pitch;
        double noseUp = HOLD_GAIN * (startPitch - pitch) - HOLD_DAMPING * rate;
        return Mth.clamp((int) Math.round(15 - noseUp), -15, 15);
    }
}
