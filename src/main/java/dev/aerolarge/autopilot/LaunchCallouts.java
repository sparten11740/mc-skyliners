package dev.aerolarge.autopilot;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.compat.SableFlight;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;

/**
 * Launch control calling out the rocket's flight as a space centre's loop calls a real launch, on the radio it counts
 * her down on and in the same voice (see {@link RocketSounds}), each call a transmission of its own, opened and closed
 * by the Quindar tones: "Tower cleared." as she rises {@link #TOWER} blocks clear of the ground; "Max Q." as she climbs
 * {@link #MAX_Q} blocks over the ground; "Main engine cut-off." as she reaches her height and goes from her climb to
 * her cruise; and "Landing burn." as her landing burn starts, falling back. Her touchdown is left to the flight deck,
 * which says "Touchdown." (see {@link RocketSounds}).
 *
 * <p>Each is called once a flight, in that order, none once a later one has been passed. None is said over another
 * voice: it waits for her cockpit to fall quiet (see {@link AutopilotBlockEntity#quiet}), as the welcome to space does,
 * and holds the floor while it lasts, so the cabin's landing announcement waits for "Landing burn."; one that has
 * waited {@link #WAIT_MOST} ticks, its milestone well behind her, is let go unsaid, as is one still waiting as she
 * passes the next. Everyone near her hears it, aboard or watching from the ground, as they hear her count. The server
 * config's {@code rocket.callouts} leaves them out. One for each rocket, worked by her autopilot's block entity;
 * nothing of it is saved, so a rocket loaded again in flight calls only what is still ahead of her.
 */
final class LaunchCallouts {
    /** How high over the ground she is clear of her launch tower, by her radio altitude under her fins, in blocks. */
    static final double TOWER = 40;
    /**
     * How high over the ground she passes Max Q, where the air pushes hardest on her, by her radio altitude under her
     * fins, in blocks. A real rocket meets it about a minute up, her speed built up and the air not yet thin. Hers is
     * 160 blocks up, so that it comes some five seconds after "Tower cleared." and before her height, over the clouds
     * from the ground of most worlds (sea level, y 63; the clouds at y 192), climbing fast; reckoned from her fins
     * rather than her cockpit, it comes the same at every scale. Set to cruise lower than some 70 blocks over it, she
     * eases off her climb before she gets there, and it is not called.
     */
    static final double MAX_Q = 160;
    /** How long a call waits for her cockpit to fall quiet, at most, in ticks: three seconds, then it is let go. */
    private static final int WAIT_MOST = 3 * 20;
    /**
     * How long a call is given to play if its sound cannot be read, in ticks: tools/generate_sounds.mjs keeps each
     * shorter (its MILESTONE_LONGEST).
     */
    private static final int LONGEST = 50;

    /** Her flight's milestones, in the order she passes them, and what launch control calls at each. */
    enum Milestone {
        TOWER_CLEARED(AerolargeRegistry.LAUNCH_TOWER_CLEARED),
        MAX_Q(AerolargeRegistry.LAUNCH_MAX_Q),
        MAIN_ENGINE_CUTOFF(AerolargeRegistry.LAUNCH_MAIN_ENGINE_CUTOFF),
        LANDING_BURN(AerolargeRegistry.LAUNCH_LANDING_BURN);

        private final Holder<SoundEvent> call;

        Milestone(Holder<SoundEvent> call) {
            this.call = call;
        }
    }

    /** The last milestone she has passed this flight, called or not; null before the first. */
    @Nullable
    private Milestone passed;
    /** The call waiting for her cockpit to fall quiet, null for none, and how long it has waited, in ticks. */
    @Nullable
    private Milestone waiting;
    private int waited;
    /** Her radio altitude and her height the tick before, to tell her climbing through a milestone's; NaN at first. */
    private double lastRadio = Double.NaN;

    /** A new flight begins: no milestone passed yet. */
    void begin() {
        passed = null;
        waiting = null;
        waited = 0;
        lastRadio = Double.NaN;
    }

    /**
     * Each tick she flies, once her pilot has flown and before the cabin is told of the phase she goes on in, so that a
     * landing announcement waits for "Landing burn.": the milestone she passes this tick, going from one phase to the
     * next, waits its turn, and whatever waits is called once her cockpit is quiet.
     */
    void watch(ServerLevel level, AutopilotBlockEntity autopilot, AutopilotBlockEntity.Phase phase, AutopilotBlockEntity.Phase next,
               SableFlight.State flight, double radio) {
        Milestone reached = reached(phase, next, flight, radio);
        lastRadio = radio;
        if (reached != null && (passed == null || reached.compareTo(passed) > 0)) {
            passed = reached;
            waiting = AerolargeConfig.ROCKET_CALLOUTS.get() ? reached : null;
            waited = 0;
        }
        if (waiting == null) {
            return;
        }
        if (autopilot.quiet()) {
            autopilot.holdFloor(VoiceLength.ticks(waiting.call, LONGEST));
            RocketSounds.overTheRadio(level, flight.position(), waiting.call);
            waiting = null;
        } else if (++waited >= WAIT_MOST) {
            waiting = null;
        }
    }

    /**
     * The milestone she passes this tick, null for none: her climb going over to her cruise, her fall to her landing
     * burn, rising through {@link #TOWER} over the ground, or, still climbing once clear of the tower, through
     * {@link #MAX_Q} over the ground.
     */
    @Nullable
    private Milestone reached(AutopilotBlockEntity.Phase phase, AutopilotBlockEntity.Phase next, SableFlight.State flight, double radio) {
        if (phase == AutopilotBlockEntity.Phase.CLIMB && next == AutopilotBlockEntity.Phase.CRUISE) {
            return Milestone.MAIN_ENGINE_CUTOFF;
        }
        if (phase == AutopilotBlockEntity.Phase.DESCENT && next == AutopilotBlockEntity.Phase.LAND) {
            return Milestone.LANDING_BURN;
        }
        if (lastRadio < TOWER && radio >= TOWER && flight.velocity().y > 0) {
            return Milestone.TOWER_CLEARED;
        }
        boolean climbing = phase == AutopilotBlockEntity.Phase.CLIMB && next == AutopilotBlockEntity.Phase.CLIMB;
        boolean throughMaxQ = lastRadio < MAX_Q && radio >= MAX_Q;
        return climbing && throughMaxQ && passed == Milestone.TOWER_CLEARED ? Milestone.MAX_Q : null;
    }
}
