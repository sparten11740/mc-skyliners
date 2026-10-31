package dev.aerolarge.autopilot;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.Vec3;

/**
 * The hands-on controls on a rocket's centre console, round her control head (see
 * {@link dev.aerolarge.block.RocketControlBlock}), as her pilot (see {@link RocketPilot}) flies by them: what they are
 * set to, how long a boost has left and how long the boost button rests after it, and whether her stability is off.
 * Each tick it hands the pilot the limits it flies by (see {@link Limits}).
 *
 * <ul>
 * <li>Boost: for {@link #BOOST_TICKS} her engines are let climb twice as fast and lean further toward where she goes,
 * faster across, with the thrust she has spare over a usual climb; then the button rests {@link #REST_TICKS}.</li>
 * <li>Stability off: her side jets bring her upright, then let her go: she leans further and further over, quicker as
 * she goes, a broom balancing on its flame, swaying as she goes; her engines still hold her height, and their thrust
 * along her tilted axis drifts her across. Switched back on in time, she is saved; else it comes back on by itself before she
 * tilts too far (reckoned ahead, see {@link #mustSnap}), after {@link #OFF_LONGEST}, low over the ground, out of cruise
 * or drifting too fast, and stays locked on for {@link #LOCKOUT}.</li>
 * <li>Flight mode: straight up and across as ever (VERTICAL), a gravity turn leaning toward her pin as she climbs
 * (ARC), or slowly and gently (GENTLE). Turned while she comes down, it is taken up next flight.</li>
 * </ul>
 *
 * <p>Boost and stability are never saved: a world loaded again finds her stable and unboosted. The mode is saved by
 * name (see {@link #save}). The clients are shown the mode, the boost's end and rest's end as game times, from which
 * they work out the rest themselves, and the stability (see {@link #saveShown}).
 */
public final class RocketControls {
    /** A control on her console, as its block asks her autopilot to work it. */
    public enum Control {
        BOOST, STABILITY, MODE, ABORT, KEY
    }

    /**
     * How she flies: the climb at most, in blocks a second; how far she leans at most climbing (only ARC, which leans
     * toward her pin as she climbs), and cruising, in degrees; how fast across she goes at most cruising, and how fast
     * she falls back at most before her landing burn, in blocks a second.
     */
    public enum Mode implements StringRepresentable {
        VERTICAL(30, 0, 10, 30, 40), ARC(30, 20, 15, 40, 40), GENTLE(12, 0, 5, 12, 20);

        private final double climb;
        private final double climbingLean;
        private final double cruiseLean;
        private final double across;
        private final double fall;

        Mode(double climb, double climbingLean, double cruiseLean, double across, double fall) {
            this.climb = climb;
            this.climbingLean = climbingLean;
            this.cruiseLean = cruiseLean;
            this.across = across;
            this.fall = fall;
        }

        /** The mode one detent round from this one, back the other way if asked. */
        public Mode turned(boolean back) {
            Mode[] all = values();
            return all[Math.floorMod(ordinal() + (back ? -1 : 1), all.length)];
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /**
         * A mode by its saved name; one saved by none, or by a name no longer known, is VERTICAL, as rockets flew
         * before.
         */
        static Mode named(String name) {
            for (Mode mode : values()) {
                if (mode.name().equals(name)) {
                    return mode;
                }
            }
            return VERTICAL;
        }
    }

    /** What the boost button's lamp shows: dark while it cannot boost, ready, boosting, or resting after a boost. */
    public enum Boost {
        DARK, READY, BOOSTING, COOLING
    }

    /** What the stability switch's lamp shows: holding her upright, off, or locked on after it snapped back. */
    public enum Stability {
        HOLDING, OFF, LOCKED
    }

    /** What her pilot flies by this tick, as {@link Mode}'s are, the lean while climbing already eased in by height. */
    record Limits(double climb, double climbingLean, double cruiseLean, double across, double fall) {
    }

    /**
     * What the console's controls show, as each reads it from her autopilot every few ticks: whether it flies a
     * rocket at all, the boost button's lamp and how much of its rest is done (0 to 1), the stability switch's lamp,
     * the mode the knob is turned to, whether pulling the abort handle would do anything, and whether she flies (the
     * launch key turned).
     */
    public record Panel(boolean rocket, Boost boost, double rested, Stability stability, Mode mode, boolean abortReady, boolean flying) {
        public static final Panel NONE = new Panel(false, Boost.DARK, 0, Stability.HOLDING, Mode.VERTICAL, false, false);
    }

    /** How long a boost lasts, in ticks: eight seconds. */
    public static final int BOOST_TICKS = 160;
    /** How long the boost button rests after a boost before it boosts again, in ticks: twenty seconds. */
    public static final int REST_TICKS = 400;
    /** How much faster she climbs boosting, as a multiple, and how much faster across she goes, in blocks a second. */
    private static final double BOOST_CLIMB = 2;
    private static final double BOOST_ACROSS = 15;
    /** How much further she leans boosting, in degrees, and how far she ever leans, boosting or not. */
    private static final double BOOST_LEAN = 4;
    private static final double MOST_LEAN = 20;
    /** ARC's lean while climbing eases in between these heights over the ground, in blocks: none below, all above. */
    private static final double ARC_FROM = 40;
    private static final double ARC_TO = 140;
    /**
     * The tilt stability snaps back on at, in degrees: reckoned ahead, as far as she will have tilted once her jets
     * have stopped her (see {@link #mustSnap}); and the tilt it comes back at whatever the reckoning says.
     */
    public static final double SNAP_TILT = 15;
    private static final double CEILING_TILT = 20;
    /**
     * How hard her side jets slow her tilting at full signal, in degrees a second each second, for the reckoning: a
     * little under the four or so they were measured to in flight, so it errs early.
     */
    private static final double JETS_STOP = 3.5;
    /** How long her jets take to answer once stability is back, in ticks, reckoned ahead with the rest. */
    private static final int ANSWER_TICKS = 2;
    /**
     * How nearly upright she must have come, in degrees, once stability is switched off, before her jets let her go:
     * until then they bring her upright, so the danger always starts from level, not from the lean she flew at.
     */
    private static final double LET_GO_TILT = 2;
    /**
     * Once her jets have let her go, how long the lean she is let fall to takes to grow e-fold, in ticks: from
     * {@link #LET_GO_TILT} to the {@link #SNAP_TILT} it snaps back at in about eleven seconds as she follows it (so it
     * was measured in flight), quicker and quicker, a broom balancing on its flame; and how far the way she falls sways
     * round, in degrees either way, two slow swells of their own, so long each, in ticks.
     */
    private static final double RUNAWAY_EFOLD = 4 * 20;
    private static final double SWAY = 35;
    private static final int SWAY_QUICK = 7 * 20;
    private static final int SWAY_SLOW = 11 * 20;
    /**
     * Stability off comes back on by itself after so long, in ticks; under so high over the ground, in blocks; and
     * drifting across faster than so fast, in blocks a second.
     */
    private static final int OFF_LONGEST = 20 * 20;
    private static final double OFF_LOWEST = 100;
    private static final double OFF_FASTEST = 40;
    /**
     * The fastest she may be crossing for stability to be switched off, in blocks a second: well under
     * {@link #OFF_FASTEST}, so she drifts a while before her speed brings it back on.
     */
    private static final double SWITCH_FASTEST = 35;
    /**
     * How high over the ground she must still be in her landing burn for the abort handle to call it off, in blocks.
     */
    static final double ABORT_LOWEST = 15;
    /** How long stability stays locked on after it snapped back, in ticks. */
    private static final int LOCKOUT = 10 * 20;
    /** How long after her phase changes before stability may be switched off, in ticks. */
    private static final int PHASE_SETTLE = 3 * 20;
    /**
     * How long after a boost or stability off before her pilot learns her engines' trim again, in ticks, and how far
     * she may tilt while it learns, in degrees: pushed about, her climb says little of how hard her engines push.
     */
    private static final int CALM = 3 * 20;
    private static final double LEARN_TILT = 10;
    /**
     * Flying ARC toward her pin, how far she leans before her lift parks, in degrees, and how far back she must come
     * before it runs again, so it never flaps between the two.
     */
    private static final double LIFT_TILT = 10;
    private static final double LIFT_TILT_BACK = 8;
    /** The alarm with stability off: how often it sounds standing upright and at the tilt it snaps at, in ticks. */
    private static final int ALARM_SLOWEST = 40;
    private static final int ALARM_QUICKEST = 10;
    /** How loud the controls' clicks, notes and alarm are, as shares of full. */
    private static final float CLICK_VOLUME = 0.8F;
    private static final float NOTE_VOLUME = 0.7F;
    private static final float ALARM_VOLUME = 1.2F;
    /**
     * Pitches: the refusal's low bass note; the knob's note for a mode taken up next flight, and for each mode as it is
     * turned to it, in the modes' order (VERTICAL, ARC, GENTLE), the gentle one lowest; the click of the stability
     * switch going on and going off, and of the knob's detent.
     */
    private static final float REFUSED_PITCH = 0.5F;
    private static final float NOTED_PITCH = 0.5F;
    private static final float[] MODE_PITCHES = {1.0F, 1.5F, 0.75F};
    private static final float SWITCH_ON_PITCH = 1.2F;
    private static final float SWITCH_OFF_PITCH = 0.8F;
    private static final float DETENT_PITCH = 1.6F;
    /** The pitch of the high note as stability snaps back, and the rising one as a player switches it back in time. */
    private static final float SNAP_PITCH = 2.0F;
    private static final float SAVED_PITCH = 1.5F;
    /** Long before any game time: never. */
    private static final long NEVER = Long.MIN_VALUE / 2;

    /** The mode the knob is turned to, saved; and the one she flies by, taken up from it out of her descent. */
    private Mode chosen = Mode.VERTICAL;
    private Mode flying = Mode.VERTICAL;
    /** When the boost under way ends and when the button has rested after it, in game time. */
    private long boostUntil = NEVER;
    private long restUntil = NEVER;
    /** Whether her stability is off, since when, and until when it is locked on after it snapped back, in game time. */
    private boolean unstable;
    /** Whether, her stability off, she has come upright and her jets have let her go (see {@link #LET_GO_TILT}). */
    private boolean adrift;
    private long unstableSince = NEVER;
    private long lockedUntil = NEVER;
    /** When a boost or stability off was last under way, for her trim (see {@link #CALM}), in game time. */
    private long stirred = NEVER;
    /** Her phase as last seen, and since when, for {@link #PHASE_SETTLE}. */
    private AutopilotBlockEntity.Phase phase = AutopilotBlockEntity.Phase.OFF;
    private long phaseSince = NEVER;
    /**
     * When her jets let her go, in game time; the way she leant as they did, a level unit vector in the world; and her
     * sway's two swells' starting points, as shares of a turn.
     */
    private long letGoAt = NEVER;
    private Vec3 letGoWay = new Vec3(1, 0, 0);
    private final double[] swayPhases = new double[2];
    /** When the stability alarm next sounds, in game time. */
    private long nextAlarm = NEVER;
    /** Whether, flying ARC, she leans far enough that her lift parks (see {@link #LIFT_TILT}). */
    private boolean leaning;
    /** Whether something the clients are shown has changed since they were last sent it. */
    private boolean changed;

    /** The mode the knob is turned to, on the clients as on the server. */
    public Mode mode() {
        return chosen;
    }

    /** Whether a boost is under way at a game time, on the clients as on the server. */
    public boolean boosting(long now) {
        return now < boostUntil;
    }

    /** Whether the boost button rests after a boost at a game time, on the clients as on the server. */
    public boolean cooling(long now) {
        return !boosting(now) && now < restUntil;
    }

    /** How much of the boost button's rest is done at a game time, from 0 as it begins to 1 once it is over. */
    public double rested(long now) {
        return cooling(now) ? 1 - (double) (restUntil - now) / REST_TICKS : 1;
    }

    /** How long the boost under way has left at a game time, in ticks; none without one. */
    public int boostLeft(long now) {
        return (int) Math.max(0, Math.min(BOOST_TICKS, boostUntil - now));
    }

    /** Whether her stability is off, on the clients as on the server. */
    public boolean unstable() {
        return unstable;
    }

    /** Whether, her stability off, her jets have let her go, she having come upright first; only on the server. */
    boolean adrift() {
        return unstable && adrift;
    }

    /** Whether, her stability just switched off, her jets are still bringing her upright before they let her go. */
    boolean levelling() {
        return unstable && !adrift;
    }

    /** Whether her stability is locked on after it snapped back, at a game time. */
    public boolean lockedOut(long now) {
        return now < lockedUntil;
    }

    /**
     * Whether nothing unsteadies her at a game time, so her lift may run while she cruises: no boost, stability on, and
     * flying ARC not leaning far.
     */
    boolean steady(long now) {
        return !boosting(now) && !unstable && !leaning;
    }

    /**
     * Whether her pilot may learn her engines' trim: neither a boost nor stability off for {@link #CALM}, and she
     * tilts less than {@link #LEARN_TILT} degrees.
     */
    boolean calm(long now, double tilt) {
        return now - stirred >= CALM && tilt < LEARN_TILT;
    }

    /**
     * The lean her pilot holds her to once her jets have let her go, a level vector in the world, in degrees: the way
     * she leant as they let her go, swaying round, ever further over (see {@link #RUNAWAY_EFOLD}), never past the tilt
     * never passed.
     */
    Vec3 runaway(long time) {
        double far = Math.min(CEILING_TILT, LET_GO_TILT * Math.exp((time - letGoAt) / RUNAWAY_EFOLD));
        double sway = Math.toRadians(SWAY / 2 * (swell(time, SWAY_QUICK, swayPhases[0]) + swell(time, SWAY_SLOW, swayPhases[1])));
        return letGoWay.yRot((float) sway).scale(far);
    }

    /** Whether something the clients are shown has changed since this was last asked, so they are to be sent it. */
    boolean takeChanged() {
        boolean was = changed;
        changed = false;
        return was;
    }

    /**
     * The limits her pilot flies by this tick, in her phase, flying to a pin or not, so high over the ground: her
     * mode's (ARC without a pin, or switched off in the server config, flies as VERTICAL), boosted while a boost is
     * under way.
     */
    Limits limits(boolean toPin, double radio, long now) {
        Mode mode = flies(toPin);
        boolean boosted = boosting(now);
        double ease = Mth.clamp((radio - ARC_FROM) / (ARC_TO - ARC_FROM), 0, 1);
        double climbingLean = Math.min(MOST_LEAN, mode.climbingLean + (boosted ? BOOST_LEAN : 0)) * (mode.climbingLean > 0 ? ease : 0);
        double cruiseLean = Math.min(MOST_LEAN, mode.cruiseLean + (boosted ? BOOST_LEAN : 0));
        return new Limits(mode.climb * (boosted ? BOOST_CLIMB : 1), climbingLean, cruiseLean, mode.across + (boosted ? BOOST_ACROSS : 0), mode.fall);
    }

    /**
     * The mode she flies by, flying to a pin or not: ARC only as it may be flown (see {@link #arcFlown}), else
     * VERTICAL.
     */
    private Mode flies(boolean toPin) {
        return flying == Mode.ARC && !arcFlown(toPin) ? Mode.VERTICAL : flying;
    }

    /**
     * Whether the arc is flown with the knob turned to it, on the clients as on the server: only toward a pin, and with
     * the server config's arcAscent on; else she flies it straight up.
     */
    public static boolean arcFlown(boolean toPin) {
        return toPin && AerolargeConfig.ROCKET_ARC_ASCENT.get();
    }

    /**
     * Each tick her pilot flies, before it sets her jets, with her tilt in degrees and how fast it grows, in degrees a
     * second, and the way she leans as a level vector in the world, in degrees: notes her phase, and out of her descent
     * takes up the mode the knob was turned to, a boost ending as she comes down; boosting or with stability off, notes
     * she was pushed about; with stability off, sounds the alarm, lets her go once she has come upright and, once it
     * must, snaps it back on (see {@link #mustSnap}). Whether it snapped back this tick.
     */
    boolean watch(ServerLevel level, BlockPos origin, AutopilotBlockEntity.Phase now, double radio, double across, double tilt, double growing,
                  Vec3 leant, boolean toPin) {
        long time = level.getGameTime();
        if (now != phase) {
            phase = now;
            phaseSince = time;
        }
        boolean comingDown = now == AutopilotBlockEntity.Phase.DESCENT || now == AutopilotBlockEntity.Phase.LAND;
        if (!comingDown) {
            flying = chosen;
        } else if (boosting(time)) {
            boostUntil = time;
            changed = true;
        }
        leaning = flies(toPin) == Mode.ARC && (leaning ? tilt > LIFT_TILT_BACK : tilt > LIFT_TILT);
        if (boosting(time) || unstable) {
            stirred = time;
        }
        if (!unstable) {
            return false;
        }
        if (!adrift && tilt <= LET_GO_TILT) {
            letGo(level, leant, time);
        }
        if (mustSnap(now, radio, across, tilt, growing, time)) {
            snap(level, origin, time);
            return true;
        }
        alarm(level, origin, tilt, time);
        return false;
    }

    /** Her jets let her go, to fall the way she leans now, or any way while she stands straight up. */
    private void letGo(ServerLevel level, Vec3 leant, long time) {
        adrift = true;
        letGoAt = time;
        letGoWay = leant.lengthSqr() > 1e-4 ? leant.normalize() : new Vec3(1, 0, 0).yRot(level.getRandom().nextFloat() * Mth.TWO_PI);
    }

    /**
     * Whether stability, off, must come back on by itself now: once her jets have let her go, before she tilts too far,
     * as far as she will have tilted once they have stopped her, reckoned ahead from how fast she tilts (and two ticks
     * for them to answer); at the tilt never passed; after {@link #OFF_LONGEST}; under {@link #OFF_LOWEST} over the
     * ground; out of cruise; or drifting across faster than {@link #OFF_FASTEST}.
     */
    private boolean mustSnap(AutopilotBlockEntity.Phase now, double radio, double across, double tilt, double growing, long time) {
        double rate = Math.max(0, growing);
        double reckoned = tilt + rate * rate / (2 * JETS_STOP) + rate * ANSWER_TICKS / 20.0;
        boolean tooFar = adrift && reckoned >= SNAP_TILT || tilt >= CEILING_TILT;
        return tooFar || time - unstableSince >= OFF_LONGEST || radio < OFF_LOWEST || now != AutopilotBlockEntity.Phase.CRUISE || across > OFF_FASTEST;
    }

    /** Stability snaps back on by itself, with a high note, and stays locked on a while. */
    private void snap(ServerLevel level, BlockPos origin, long time) {
        unstable = false;
        lockedUntil = time + LOCKOUT;
        changed = true;
        level.playSound(null, origin, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, NOTE_VOLUME, SNAP_PITCH);
    }

    /** The alarm while stability is off: every two seconds standing upright, quicker the further she tilts. */
    private void alarm(ServerLevel level, BlockPos origin, double tilt, long time) {
        if (time < nextAlarm) {
            return;
        }
        double over = Mth.clamp(tilt / SNAP_TILT, 0, 1);
        nextAlarm = time + Math.round(Mth.lerp(over, ALARM_SLOWEST, ALARM_QUICKEST));
        level.playSound(null, origin, AerolargeRegistry.ROCKET_STABILITY_ALARM.get(), SoundSource.BLOCKS, ALARM_VOLUME, 1.0F);
    }

    private static double swell(long time, int length, double phase) {
        return Math.sin(Mth.TWO_PI * ((double) time / length + phase));
    }

    /**
     * The boost button pressed by a player: a boost begins if it may (see {@link #boostRefusal}), with a click; else
     * the refusal's low note and why. Whether it began.
     */
    boolean boost(ServerLevel level, BlockPos at, ServerPlayer player, AutopilotBlockEntity.Phase now, int counted, boolean light) {
        long time = level.getGameTime();
        String refused = boostRefusal(now, counted, light, time);
        if (refused != null) {
            return refuse(level, at, player, refused);
        }
        boostUntil = time + BOOST_TICKS;
        restUntil = boostUntil + REST_TICKS;
        stirred = time;
        changed = true;
        level.playSound(null, at, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, CLICK_VOLUME, 1.0F);
        player.displayClientMessage(Component.translatable("message.aerolarge.rocket_controls.boost"), true);
        return true;
    }

    /**
     * Why a boost may not begin now, as the last part of its message's key, or null if it may: switched off in the
     * server config; boosting or resting already; counting down or not yet up (it boosts from lift-off through her
     * climb and cruise, never on her way down); or light aboard in space, where a sudden push would throw everyone.
     */
    @Nullable
    private String boostRefusal(AutopilotBlockEntity.Phase now, int counted, boolean light, long time) {
        if (!AerolargeConfig.ROCKET_BOOST.get()) {
            return "switched_off";
        }
        if (boosting(time) || cooling(time)) {
            return "boost_resting";
        }
        boolean up = now == AutopilotBlockEntity.Phase.CLIMB || now == AutopilotBlockEntity.Phase.CRUISE
            || now == AutopilotBlockEntity.Phase.TAKEOFF && counted >= RocketPilot.COUNTDOWN;
        if (!up) {
            return "boost_not_now";
        }
        return light ? "boost_light" : null;
    }

    /**
     * The stability switch worked by a player, its cover up: with stability off, back on, saved, with a rising note;
     * on, off if it may be (see {@link #stabilityRefusal}), with a click and the alarm from now on; else the refusal's
     * low note and why. Whether it switched.
     */
    boolean stability(ServerLevel level, BlockPos at, ServerPlayer player, AutopilotBlockEntity.Phase now, double radio, double across) {
        long time = level.getGameTime();
        if (unstable) {
            unstable = false;
            changed = true;
            level.playSound(null, at, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, CLICK_VOLUME, SWITCH_ON_PITCH);
            level.playSound(null, at, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, NOTE_VOLUME, SAVED_PITCH);
            player.displayClientMessage(Component.translatable("message.aerolarge.rocket_controls.stability_on"), true);
            return true;
        }
        String refused = stabilityRefusal(now, radio, across, time);
        if (refused != null) {
            return refuse(level, at, player, refused);
        }
        unstable = true;
        adrift = false;
        unstableSince = time;
        nextAlarm = time;
        stirred = time;
        changed = true;
        for (int i = 0; i < swayPhases.length; i++) {
            swayPhases[i] = level.getRandom().nextDouble();
        }
        level.playSound(null, at, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, CLICK_VOLUME, SWITCH_OFF_PITCH);
        player.displayClientMessage(Component.translatable("message.aerolarge.rocket_controls.stability_off"), true);
        return true;
    }

    /**
     * Why stability may not be switched off now, as the last part of its message's key, or null if it may: switched
     * off in the server config; locked on after it snapped back; not cruising, or cruising so lately the phase has
     * hardly changed; under {@link #OFF_LOWEST} over the ground; boosting, or crossing faster than
     * {@link #SWITCH_FASTEST}, as it would only snap back on at once.
     */
    @Nullable
    private String stabilityRefusal(AutopilotBlockEntity.Phase now, double radio, double across, long time) {
        if (!AerolargeConfig.ROCKET_STABILITY_SWITCH.get()) {
            return "switched_off";
        }
        if (lockedOut(time)) {
            return "stability_locked";
        }
        boolean settled = now == AutopilotBlockEntity.Phase.CRUISE && now == phase && time - phaseSince >= PHASE_SETTLE;
        if (!settled || radio < OFF_LOWEST) {
            return "stability_not_now";
        }
        return boosting(time) || across > SWITCH_FASTEST ? "stability_unsteady" : null;
    }

    /**
     * The flight mode knob turned a detent by a player, back the other way sneaking: a detent's click and a note
     * pitched for the mode; while she comes down, taken up next flight, with a low note instead; ARC with arcAscent
     * switched off in the server config, the refusal's low note, the knob turned all the same (she flies it as
     * VERTICAL). Whether it was taken as it is.
     */
    boolean turn(ServerLevel level, BlockPos at, ServerPlayer player, AutopilotBlockEntity.Phase now) {
        chosen = chosen.turned(player.isShiftKeyDown());
        changed = true;
        level.playSound(null, at, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, CLICK_VOLUME, DETENT_PITCH);
        if (chosen == Mode.ARC && !AerolargeConfig.ROCKET_ARC_ASCENT.get()) {
            return refuse(level, at, player, "arc_switched_off");
        }
        boolean later = now == AutopilotBlockEntity.Phase.DESCENT || now == AutopilotBlockEntity.Phase.LAND;
        float pitch = later ? NOTED_PITCH : MODE_PITCHES[chosen.ordinal()];
        level.playSound(null, at, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, NOTE_VOLUME, pitch);
        String said = "message.aerolarge.rocket_controls.mode_" + chosen.getSerializedName() + (later ? "_later" : "");
        player.displayClientMessage(Component.translatable(said), true);
        return true;
    }

    /**
     * Whether pulling the abort handle does anything in her phase, so high over the ground: counting down (it scrubs),
     * lifting off, climbing and cruising (it stops her where she is), and coming down, in her landing burn only while
     * still {@link #ABORT_LOWEST} up (it calls her landing off).
     */
    static boolean abortable(AutopilotBlockEntity.Phase in, double radio) {
        return switch (in) {
            case TAKEOFF, CLIMB, CRUISE, DESCENT -> true;
            case LAND -> radio > ABORT_LOWEST;
            default -> false;
        };
    }

    /** A control that will not do what it was asked: the low bass note, and why. Never taken. */
    private static boolean refuse(ServerLevel level, BlockPos at, ServerPlayer player, String why) {
        level.playSound(null, at, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, NOTE_VOLUME, REFUSED_PITCH);
        player.displayClientMessage(Component.translatable("message.aerolarge.rocket_controls." + why), true);
        return false;
    }

    /**
     * What the console's controls show at a game time, in her phase, so far into her countdown, light aboard or not,
     * whether the abort handle would do anything and whether she flies.
     */
    Panel panel(long now, AutopilotBlockEntity.Phase in, int counted, boolean light, boolean abortReady, boolean flies) {
        Boost lamp = boosting(now) ? Boost.BOOSTING : cooling(now) ? Boost.COOLING
            : boostRefusal(in, counted, light, now) == null ? Boost.READY : Boost.DARK;
        Stability stability = unstable ? Stability.OFF : lockedOut(now) ? Stability.LOCKED : Stability.HOLDING;
        return new Panel(true, lamp, rested(now), stability, chosen, abortReady, flies);
    }

    /** Landed, or the flight given up: no boost, stability on, nothing locked. The mode stays as the knob is turned. */
    void stop() {
        boolean were = boostUntil != NEVER || restUntil != NEVER || unstable || lockedUntil != NEVER;
        boostUntil = NEVER;
        restUntil = NEVER;
        unstable = false;
        lockedUntil = NEVER;
        leaning = false;
        changed |= were;
    }

    /** The mode the knob is turned to, into her pilot's save. */
    void save(CompoundTag tag) {
        tag.putString("Mode", chosen.name());
    }

    /** The mode from her pilot's save, VERTICAL for one saved before there were modes. */
    void load(CompoundTag tag) {
        chosen = Mode.named(tag.getString("Mode"));
        flying = chosen;
    }

    /** What the clients are shown, in the update sent to them: the mode, the boost's and rest's ends, the stability. */
    void saveShown(CompoundTag tag) {
        tag.putString("RocketMode", chosen.name());
        tag.putLong("RocketBoostUntil", boostUntil);
        tag.putLong("RocketRestUntil", restUntil);
        tag.putBoolean("RocketUnstable", unstable);
        tag.putLong("RocketLockedUntil", lockedUntil);
    }

    /**
     * What the clients are shown, from the update the server sent; a save carries none of it but the mode, in her
     * pilot's own (see {@link #load}), so a world loaded again finds her unboosted and stable, her mode as it was.
     */
    void loadShown(CompoundTag tag) {
        if (tag.contains("RocketMode")) {
            chosen = Mode.named(tag.getString("RocketMode"));
        }
        boostUntil = tag.contains("RocketBoostUntil") ? tag.getLong("RocketBoostUntil") : NEVER;
        restUntil = tag.contains("RocketRestUntil") ? tag.getLong("RocketRestUntil") : NEVER;
        unstable = tag.getBoolean("RocketUnstable");
        lockedUntil = tag.contains("RocketLockedUntil") ? tag.getLong("RocketLockedUntil") : NEVER;
    }
}
