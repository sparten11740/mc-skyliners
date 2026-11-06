package dev.skyliners.autopilot;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.airframe.RocketSilhouette;
import dev.skyliners.airframe.Thrusters;
import dev.skyliners.block.Relays;
import dev.skyliners.block.Turning;
import dev.skyliners.compat.SableFlight;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * What flies the rocket for its autopilot (see {@link AutopilotBlockEntity}): its engines push it straight up its axis,
 * and two rings of side jets, near its nose and near its bottom, firing against each other, tilt that axis. It flies
 * a hop: a countdown, lift-off and a climb straight up to the height it is set to; there it holds its height and leans
 * toward where it is going, a pin or, without one, back over where it rose, until it is over it and hardly drifting;
 * then it falls back down and a landing burn catches it, standing upright for the last of the way down, settling on
 * its fins.
 *
 * <p>Each tick it works out the climb it wants for its phase and how high it is, and the thrust that gives it, from
 * the weight it carries (Sable weighs it) and a trim it learns for how hard its engines push, spread over every engine
 * by a relay each; braking, it adds the slowing it plans on as it goes, rather than waiting to fall behind. It never
 * falls faster than {@link #FASTEST_FALL} of gravity, so whoever stands aboard stays on the floor. Its tilt is held by
 * its side jets, against how fast it tilts, toward straight up and the lean it wants, toward the speed across it
 * wants, which is as fast as it can still slow from by where it is going.
 *
 * <p>How fast it climbs, leans and goes across, and falls back, follow the controls on its centre console (see
 * {@link RocketControls}): its flight mode, a boost, and its stability, which switched off leaves its side jets
 * pushing it over rather than holding it upright, until it is switched back on or snaps back on by itself.
 */
public final class RocketPilot {
    /** How long the countdown before lift-off lasts, in ticks. */
    static final int COUNTDOWN = 200;
    /** How high over the ground it rises straight up, leaning nowhere, in blocks: clear of anything round it. */
    private static final double CLEAR = 30;
    /** How fast it touches down, in blocks a second, and how close over the ground it counts as down, in blocks. */
    private static final double TOUCHDOWN = 1.5;
    private static final double DOWN = 1;
    /** How many ticks it rests on the ground, hardly moving, before its engines are let go and it switches off. */
    private static final int SETTLED_TICKS = 20;
    /**
     * How long it may be neither falling nor rising, told to come down, before its engines are let go wherever it is,
     * in ticks: far longer than it takes to pass the top of a climb or start down from a hover. Something the ground
     * reading does not see holds it up, the top of a tree or another ship. Should nothing hold it after all, it starts
     * to fall, and its engines catch it again.
     */
    private static final int HELD_UP_TICKS = 40;
    /** How much of gravity it falls at most, as a share: its engines always hold up the rest of its weight. */
    static final double FASTEST_FALL = 0.6;
    /**
     * How much of the thrust it has to spare over its weight its landing burn counts on, as a share: the rest is kept
     * in hand, should it come down faster than it planned.
     */
    private static final double BRAKING_SHARE = 0.5;
    /** How quickly it closes on the climb it wants, in blocks a second each second for each block a second off it. */
    private static final double CLIMB_GAIN = 1.2;
    /**
     * How fast its trim for its engines' push is learnt, a share of what is still off each second, and how far off the
     * climb it wants it may be while it learns, in blocks a second.
     */
    private static final double TRIM_RATE = 0.05;
    private static final double LEARNING_ERROR = 3;
    private static final double MOST_TRIM = 2;
    /**
     * How far it leans at most coming down, in degrees, whatever its flight mode, and how far it leans for each block a
     * second across it wants changed.
     */
    private static final double MOST_LEAN = 10;
    private static final double LEAN_GAIN = 0.8;
    /**
     * How steeply it plans to lean back to slow across, in degrees: less than its most, to keep some in hand; and,
     * flying across in a mode that leans less than that at most, the share of its most it plans to brake at instead.
     */
    private static final double BRAKING_LEAN = 6;
    private static final double BRAKING_LEAN_SHARE = 0.8;
    /**
     * Over where it is going: how near it counts as there, in blocks, and how slowly it drifts across there at most, in
     * blocks a second, before it starts down; and how fast across it lets itself drift at most on the way down.
     */
    private static final double ARRIVED = 6;
    private static final double STILL = 0.5;
    private static final double SETTLING_ACROSS = 4;
    /**
     * Over where it is going for a while without settling, as a heavy load leaves it circling the spot, slower than it
     * leans to correct: how near it counts as there, in blocks, how long it may circle there, in ticks, and how fast it
     * may drift at most then, in blocks a second, before it starts down all the same, correcting on the way down.
     */
    private static final double CIRCLING_REACH = 12;
    private static final int CIRCLING_MOST = 10 * 20;
    private static final double CIRCLING_ACROSS = 3;
    /** Cruising, how fast it closes on its height, in blocks a second a block off it, and its climb at most then. */
    private static final double HOLD_GAIN = 0.5;
    private static final double HOLD_CLIMB = 8;
    /**
     * Coming down to touch: how high over the ground it leans no more than {@link #LAST_LEAN} degrees, to stop drifting
     * without touching down tilted, and how high it leans for nothing, in blocks.
     */
    private static final double UPRIGHT = 20;
    private static final double LAST_LEAN = 2;
    private static final double TOUCHING = 3;
    /** How hard its side jets push for each degree it tilts off what it wants, and against each degree a second. */
    private static final double TILT_GAIN = 1.5;
    private static final double TILT_DAMPING = 6;
    /** How strong a signal drives an engine or a jet at most. */
    private static final int FULL = 15;
    /**
     * How far the signal asked of its engines together must move from the one they were set to before they are set
     * anew, in steps. Rounded afresh each tick, a cruise's thrust wobbling between two steps would set an engine back
     * and forth several times a second, and each change of a thruster is sent to every client.
     */
    private static final double SIGNAL_HOLD = 0.75;
    /** How loud each side jet puffs as its stability snaps back on, every one of them at once, as a share of full. */
    private static final float SNAP_PUFF_VOLUME = 0.5F;
    /**
     * What holds her countdown before it begins, each asked every tick it waits with her level and her autopilot's
     * place: her rover out of its bay, for one. Held, she waits on the ground, her engines quiet, launch control silent.
     */
    private static final List<BiPredicate<ServerLevel, BlockPos>> HOLDS = new CopyOnWriteArrayList<>();

    /** A ring of side jets' relays, by the way each pushes the rocket, seen from its control head facing its front. */
    public record Jets(@Nullable BlockPos ahead, @Nullable BlockPos right, @Nullable BlockPos back, @Nullable BlockPos left) {
        static final Jets NONE = new Jets(null, null, null, null);

        List<BlockPos> all() {
            List<BlockPos> all = new ArrayList<>();
            for (BlockPos jet : new BlockPos[]{ahead, right, back, left}) {
                if (jet != null) {
                    all.add(jet);
                }
            }
            return all;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            put(tag, "Ahead", ahead);
            put(tag, "Right", right);
            put(tag, "Back", back);
            put(tag, "Left", left);
            return tag;
        }

        Jets turned(Rotation turn) {
            return new Jets(Turning.turn(ahead, turn), Turning.turn(right, turn), Turning.turn(back, turn), Turning.turn(left, turn));
        }

        static Jets load(CompoundTag tag, Rotation turn) {
            return new Jets(get(tag, "Ahead", turn), get(tag, "Right", turn), get(tag, "Back", turn), get(tag, "Left", turn));
        }

        private static void put(CompoundTag tag, String key, @Nullable BlockPos jet) {
            if (jet != null) {
                tag.putLong(key, jet.asLong());
            }
        }

        @Nullable
        private static BlockPos get(CompoundTag tag, String key, Rotation turn) {
            return tag.contains(key) ? Turning.turn(BlockPos.of(tag.getLong(key)), turn) : null;
        }
    }

    /**
     * What it is doing, for play-testing: the share of full thrust its engines were last set to, its learnt trim, how
     * far into its countdown it is, its tilt across its front and to its right, in degrees, each side jet's signal
     * (near the nose ahead, right, back and left, then near the bottom), and the climb it last wanted, in blocks a
     * second.
     */
    public record Report(double thrust, double trim, int counted, double tiltFront, double tiltRight, List<Integer> jets, double climbWanted) {
    }

    /**
     * What the autopilot is set to fly: its phase, the height it climbs to, and where it goes, a pin or, without one,
     * back where it rose from.
     */
    record Orders(AutopilotBlockEntity.Phase phase, double altitude, boolean toPin, double pinX, double pinZ) {
    }

    /**
     * Its attitude this tick: its tilt across its front and to its right, in degrees, how fast each changes, in degrees
     * a second, and its front and right along the ground.
     */
    private record Attitude(double front, double right, double frontRate, double rightRate, Vec3 frontWay, Vec3 rightWay) {
        /** The way it leans, a level vector in the world as long as its tilt in degrees. */
        Vec3 leant() {
            return frontWay.scale(front).add(rightWay.scale(right));
        }

        /** How far it tilts from straight up, in degrees. */
        double tilt() {
            return Math.hypot(front, right);
        }

        /** How fast its tilt grows, in degrees a second; less than none as it rights itself. */
        double growing() {
            double tilt = tilt();
            return tilt < 1e-3 ? Math.hypot(frontRate, rightRate) : (front * frontRate + right * rightRate) / tilt;
        }
    }

    private List<List<BlockPos>> bells = List.of();
    private Jets top = Jets.NONE;
    private Jets bottom = Jets.NONE;
    /** Whether its side jets' plumes have been left out since it was loaded, not saved. */
    private boolean jetPlumesLeftOut;
    /** How many engines it has, each with its relay. */
    private int engines;
    /** The trim learnt for its engines' push: how much more thrust than it works out it takes to climb as it wants. */
    private double trim = 1;
    /** The ticks since the countdown began, while it counts down and lifts off. */
    private int counted;
    /** Where it rose from, to come back down to without a pin. */
    @Nullable
    private Vec3 rose;
    /**
     * Whether it holds its height where it is rather than coming down: once its landing has been called off, until it
     * is told to land again or given a pin to fly to.
     */
    private boolean holding;
    /** Its tilt the tick before, across its front and to its right, in degrees, to tell how fast it tilts. */
    private double lastFront;
    private double lastRight;
    private int settled;
    /** How long it has been neither falling nor rising, told to come down, in ticks. */
    private int stoppedFor;
    /** How long it has cruised near enough over where it is going without settling there, in ticks. */
    private int circled;
    /** What it last set, for its report: the share of full thrust, each jet's signal and the climb it wanted. */
    private double lastShare;
    private final int[] jetSignals = new int[8];
    /** The signal its engines were last set to together. */
    private int lastTotal;
    private double lastClimb;
    /** How high over the ground it was as it last flew, in blocks. */
    private double lastRadio;
    /** What the controls on its centre console are set to, and the limits it flies by for them. */
    private final RocketControls controls = new RocketControls();

    /**
     * How high the lowest of its four fins' feet is above the ground under it, in blocks, each followed out into the
     * world from among its blocks. It stands on them, so with a launch pad's flame trench or any other hollow under its
     * control head it is down all the same. As high as can be with no frame to find them by, for one not built from
     * its blueprint.
     */
    static double feetOverGround(Level level, @Nullable BoundingBox frame, int footBelow) {
        if (frame == null) {
            return Double.MAX_VALUE;
        }
        double out = RocketSilhouette.footOut(RocketSilhouette.scaleOf(footBelow));
        double x = (frame.minX() + frame.maxX() + 1) / 2.0;
        double z = (frame.minZ() + frame.maxZ() + 1) / 2.0;
        double lowest = Double.MAX_VALUE;
        for (int across : new int[]{-1, 1}) {
            for (int along : new int[]{-1, 1}) {
                lowest = Math.min(lowest, Legs.overGround(level, new Vec3(x + across * out, frame.minY(), z + along * out)));
            }
        }
        return lowest;
    }

    /** Holds her countdown before it begins for as long as something says so (see {@link #HOLDS}). */
    public static void holdCountdownWhile(BiPredicate<ServerLevel, BlockPos> hold) {
        HOLDS.add(hold);
    }

    /**
     * Whether something holds her countdown this tick before it begins (see {@link #HOLDS}): set to take off and not
     * yet counting, her autopilot at a place.
     */
    static boolean countdownHeld(ServerLevel level, BlockPos origin, AutopilotBlockEntity.Phase phase, int counted) {
        if (phase != AutopilotBlockEntity.Phase.TAKEOFF || counted > 0) {
            return false;
        }
        for (BiPredicate<ServerLevel, BlockPos> hold : HOLDS) {
            if (hold.test(level, origin)) {
                return true;
            }
        }
        return false;
    }

    void configure(List<List<BlockPos>> bells, Jets top, Jets bottom) {
        this.bells = bells.stream().map(List::copyOf).toList();
        this.top = top;
        this.bottom = bottom;
        this.engines = bells.stream().mapToInt(List::size).sum();
    }

    boolean wired() {
        return engines > 0;
    }

    /** Every relay it works, for mending its links (see {@link Turning#mend}). */
    List<BlockPos> links() {
        List<BlockPos> links = new ArrayList<>();
        bells.forEach(links::addAll);
        links.addAll(top.all());
        links.addAll(bottom.all());
        return links;
    }

    /** Its links turned with the rocket, once they were found to lead nowhere as they were (see {@link Turning}). */
    void turn(Rotation turn) {
        bells = bells.stream().map(bell -> Turning.turn(bell, turn)).toList();
        top = top.turned(turn);
        bottom = bottom.turned(turn);
    }

    /** How far into its countdown it is, in ticks; nothing once it has lifted off or before it began. */
    int counted() {
        return counted;
    }

    /** The controls on its centre console, on the clients as on the server. */
    RocketControls controls() {
        return controls;
    }

    /** How high over the ground it was as it last flew, in blocks. */
    double radio() {
        return lastRadio;
    }

    /** Begins a new flight from the ground: the countdown from the top, nothing learnt of this flight yet. */
    void begin(SableFlight.State state) {
        counted = 0;
        settled = 0;
        stoppedFor = 0;
        circled = 0;
        rose = state.position();
        lastFront = 0;
        lastRight = 0;
    }

    /**
     * Flies one tick: the phase it flies in next. Counting down, it waits on the ground; then it climbs, falls back
     * and lands, and once it has stood still on the ground a moment it is switched off.
     */
    AutopilotBlockEntity.Phase fly(ServerLevel level, BlockPos origin, Orders orders, SableFlight.State state, double radio, double mass) {
        if (rose == null) {
            begin(state);
        }
        leaveJetPlumesOut(level, origin);
        lastRadio = radio;
        AutopilotBlockEntity.Phase phase = orders.phase();
        if (phase == AutopilotBlockEntity.Phase.TAKEOFF && counted < COUNTDOWN) {
            counted++;
            rest(level, origin);
            return phase;
        }
        long now = level.getGameTime();
        Vec3 goal = orders.toPin() ? new Vec3(orders.pinX(), 0, orders.pinZ()) : rose;
        Vec3 toGo = new Vec3(goal.x - state.position().x, 0, goal.z - state.position().z);
        double highest = Math.max(orders.altitude(), state.position().y - radio + CLEAR * 2);
        Attitude attitude = attitude(state);
        if (controls.watch(level, origin, phase, radio, across(state), attitude.tilt(), attitude.growing(), attitude.leant())) {
            puffAll(level, origin);
        }
        RocketControls.Limits limits = controls.limits(orders.toPin(), radio, now);
        double climb = climbWanted(phase, highest, state, radio, mass, limits);
        lastClimb = climb;
        boolean learning = phase == AutopilotBlockEntity.Phase.CRUISE && controls.calm(now, attitude.tilt());
        thrust(level, origin, state, mass, climb, slowing(phase, state, climb, mass), learning);
        jets(level, origin, attitude, lean(phase, state, toGo, radio, limits, now));
        // Down on its fins and no longer falling, it lets its engines go at once: held up even a little, it would skate
        // across the ground on them.
        boolean resting = resting(phase, state, radio);
        if (resting) {
            rest(level, origin);
        }
        settled = resting ? settled + 1 : 0;
        if (settled >= SETTLED_TICKS) {
            return AutopilotBlockEntity.Phase.OFF;
        }
        return next(phase, highest, state, radio, mass, holding && !orders.toPin() ? null : toGo);
    }

    /**
     * Whether it rests, told to come down: close over the ground and no longer falling, or a while neither falling nor
     * rising wherever it is (see {@link #HELD_UP_TICKS}). Only no longer falling is not enough up there: it is still
     * rising as it starts down from its climb, and would have its engines let go at the top of it.
     */
    private boolean resting(AutopilotBlockEntity.Phase phase, SableFlight.State state, double radio) {
        boolean landing = phase == AutopilotBlockEntity.Phase.LAND;
        double climb = state.velocity().y;
        stoppedFor = (landing || phase == AutopilotBlockEntity.Phase.DESCENT) && Math.abs(climb) < STILL ? stoppedFor + 1 : 0;
        return landing && radio < TOUCHING && climb > -STILL || stoppedFor >= HELD_UP_TICKS;
    }

    /**
     * The phase it goes on in: up through the climb to the height set, across holding it until over where it is going
     * and hardly drifting, then down, braking once it must; holding its height where it is, with nowhere to go (null),
     * once its landing has been called off.
     */
    private AutopilotBlockEntity.Phase next(AutopilotBlockEntity.Phase phase, double highest, SableFlight.State state, double radio, double mass,
                                            @Nullable Vec3 toGo) {
        return switch (phase) {
            case TAKEOFF -> radio > CLEAR ? AutopilotBlockEntity.Phase.CLIMB : phase;
            case CLIMB -> coastsTo(state) >= highest ? AutopilotBlockEntity.Phase.CRUISE : phase;
            case CRUISE -> toGo != null && arrived(state, toGo) ? AutopilotBlockEntity.Phase.DESCENT : phase;
            case DESCENT -> radio <= brakingHeight(state, mass) ? AutopilotBlockEntity.Phase.LAND : phase;
            default -> phase;
        };
    }

    /**
     * Whether, cruising, it is over where it is going and may start down: hardly drifting there, or there a while and
     * drifting slowly, circling it (see {@link #CIRCLING_REACH}).
     */
    private boolean arrived(SableFlight.State state, Vec3 toGo) {
        double distance = toGo.length();
        circled = distance < CIRCLING_REACH ? circled + 1 : 0;
        boolean still = distance < ARRIVED && across(state) < STILL;
        return still || circled >= CIRCLING_MOST && across(state) < CIRCLING_ACROSS;
    }

    /**
     * How hard it plans to be slowed this tick in its landing burn, in blocks a second each second: falling along the
     * curve it brakes by, as hard as that curve brakes; falling slower than the curve, as much less; nothing outside
     * the burn, or at its touchdown speed.
     */
    private double slowing(AutopilotBlockEntity.Phase phase, SableFlight.State state, double climb, double mass) {
        if (phase != AutopilotBlockEntity.Phase.LAND || climb >= -TOUCHDOWN) {
            return 0;
        }
        return braking(state, mass) * Mth.clamp(state.velocity().y / climb, 0, 1);
    }

    /** How fast it crosses the ground, in blocks a second; none while it is not flying. */
    static double across(@Nullable SableFlight.State state) {
        return state == null ? 0 : Math.hypot(state.velocity().x, state.velocity().z);
    }

    /**
     * How high it would coast up to were its engines eased back now: slowed by as much of gravity as it ever falls by,
     * the rest of its weight still held up by them.
     */
    static double coastsTo(SableFlight.State state) {
        double climb = Math.max(0, state.velocity().y);
        return state.position().y + climb * climb / (2 * FASTEST_FALL * Math.max(1, state.gravity()));
    }

    /**
     * How fast it wants to climb, in blocks a second: up at its climbing speed as its controls have it, easing off so
     * it coasts no higher than the height it goes to; across, closing on that height; falling back at its falling
     * speed; and in its landing burn, as fast as it can still stop by the ground, down to its touchdown speed.
     */
    private double climbWanted(AutopilotBlockEntity.Phase phase, double highest, SableFlight.State state, double radio, double mass,
                               RocketControls.Limits limits) {
        double below = highest - state.position().y;
        return switch (phase) {
            case TAKEOFF, CLIMB -> Math.min(limits.climb(), Math.sqrt(2 * state.gravity() * Math.max(0, below)) + 1);
            case CRUISE -> Mth.clamp(HOLD_GAIN * below, -HOLD_CLIMB, HOLD_CLIMB);
            case DESCENT -> -limits.fall();
            default -> -Math.max(TOUCHDOWN, Math.sqrt(2 * braking(state, mass) * Math.max(0, radio - DOWN)));
        };
    }

    /**
     * How hard its landing burn slows it, in blocks a second each second: a share of the thrust it has to spare over
     * its weight, as hard as its engines push by its learnt trim.
     */
    private double braking(SableFlight.State state, double mass) {
        double spare = mostThrust() / (trim * Math.max(1, mass * state.gravity())) - 1;
        return Math.max(1, BRAKING_SHARE * spare * state.gravity());
    }

    /** How high over the ground its landing burn begins, falling as fast as it is, in blocks, with a second in hand. */
    private double brakingHeight(SableFlight.State state, double mass) {
        double falling = Math.max(0, -state.velocity().y);
        return DOWN + falling * falling / (2 * braking(state, mass)) + falling;
    }

    /** How hard all its engines push together at full signal, in Sable's units. */
    private double mostThrust() {
        return engines * Thrusters.CREATIVE_PUSH * SkylinersConfig.THRUST_SETTING.get() / 99.0;
    }

    /**
     * The lean it wants, as a tilt of its axis across, in degrees toward the world's x and z: none climbing (but in a
     * gravity turn, see {@link RocketControls.Mode#ARC}, leaning as it cruises, as far as its controls let it
     * climbing), nor in the last of the way down; across, toward the speed across it wants, toward where it goes and as
     * fast as it can still slow from by there, leaning back at {@link #BRAKING_LEAN} (less in a mode that leans less),
     * as far and as fast as its controls let it; coming down, toward a drift no faster than {@link #SETTLING_ACROSS},
     * holding it over where it goes.
     */
    private static Vec3 leanWanted(AutopilotBlockEntity.Phase phase, SableFlight.State state, Vec3 toGo, double radio, RocketControls.Limits limits) {
        boolean turning = phase == AutopilotBlockEntity.Phase.CLIMB && limits.climbingLean() > 0;
        boolean upright = phase == AutopilotBlockEntity.Phase.TAKEOFF || phase == AutopilotBlockEntity.Phase.CLIMB && !turning
            || phase == AutopilotBlockEntity.Phase.LAND && radio < TOUCHING;
        if (upright) {
            return Vec3.ZERO;
        }
        double most = switch (phase) {
            case CLIMB -> limits.climbingLean();
            case CRUISE -> limits.cruiseLean();
            default -> phase == AutopilotBlockEntity.Phase.LAND && radio < UPRIGHT ? LAST_LEAN : MOST_LEAN;
        };
        double distance = toGo.length();
        boolean crossing = phase == AutopilotBlockEntity.Phase.CRUISE || turning;
        double braking = crossing ? Math.min(BRAKING_LEAN, BRAKING_LEAN_SHARE * most) : BRAKING_LEAN;
        double slowing = state.gravity() * Math.tan(Math.toRadians(braking));
        double fastest = crossing ? limits.across() : SETTLING_ACROSS;
        double speed = Math.min(fastest, Math.sqrt(2 * slowing * distance));
        Vec3 wanted = distance < 1e-3 ? Vec3.ZERO : toGo.scale(speed / distance);
        Vec3 off = wanted.subtract(state.velocity().x, 0, state.velocity().z).scale(LEAN_GAIN);
        double lean = off.length();
        return lean > most ? off.scale(most / lean) : off;
    }

    /**
     * The lean it holds to this tick: the one it wants, as its stability has it; with its stability switched off,
     * upright until it has come upright, then ever further over once its jets have let it go (see
     * {@link RocketControls#runaway}).
     */
    private Vec3 lean(AutopilotBlockEntity.Phase phase, SableFlight.State state, Vec3 toGo, double radio, RocketControls.Limits limits, long now) {
        if (controls.adrift()) {
            return controls.runaway(now);
        }
        return controls.levelling() ? Vec3.ZERO : leanWanted(phase, state, toGo, radio, limits);
    }

    /**
     * Sets its engines for the climb it wants: the thrust that holds up its weight, slows it as much more as it plans
     * to be slowed (in blocks a second each second), and closes on the climb, through its trim, never so little it
     * falls faster than it may, spread over every engine. Its trim is learnt only cruising, as it holds its height, and
     * only once its controls have left it calm a while (see {@link RocketControls#calm}).
     */
    private void thrust(ServerLevel level, BlockPos origin, SableFlight.State state, double mass, double climb, double slowing, boolean learning) {
        double error = climb - state.velocity().y;
        double tilt = Math.max(0.5, state.up().y);
        double wanted = mass * (state.gravity() + slowing + CLIMB_GAIN * error) / tilt;
        double least = mass * state.gravity() * (1 - FASTEST_FALL) / tilt;
        double push = Math.max(least, wanted) * trim;
        // Learnt holding its height with the engines giving what is asked of them, near the climb it wants: held back,
        // flat out, braking or touching down, the climb says nothing of how hard they push.
        if (learning && wanted > least && push < mostThrust() && Math.abs(error) < LEARNING_ERROR) {
            trim = Mth.clamp(trim + TRIM_RATE / 20 * error / (Math.abs(climb) + 1), 1 / MOST_TRIM, MOST_TRIM);
        }
        engines(level, origin, push / Math.max(1, mostThrust()));
    }

    /** Every engine at the share of full thrust given, the signals spread so their sum gives it as nearly as it can. */
    private void engines(ServerLevel level, BlockPos origin, double share) {
        lastShare = Mth.clamp(share, 0, 1);
        int total = held(lastShare * FULL * engines, lastTotal);
        lastTotal = total;
        int each = total / Math.max(1, engines);
        int engine = 0;
        for (List<BlockPos> bell : bells) {
            for (BlockPos relay : bell) {
                // The signals left over once each has its even share go one more to the first engines.
                Relays.drive(level, origin, relay, each + (engine < total - each * engines ? 1 : 0), false);
                engine++;
            }
        }
    }

    /**
     * Its attitude this tick, from where its axis points: its tilt across its front and to its right, and how fast
     * each changes since the tick before, which it notes for the next, stability on or off.
     */
    private Attitude attitude(SableFlight.State state) {
        Vec3 axis = state.up();
        Vec3 front = flat(state.nose());
        Vec3 right = flat(state.right());
        double tiltFront = Math.toDegrees(Math.asin(Mth.clamp(axis.dot(front), -1, 1)));
        double tiltRight = Math.toDegrees(Math.asin(Mth.clamp(axis.dot(right), -1, 1)));
        Attitude attitude = new Attitude(tiltFront, tiltRight, (tiltFront - lastFront) * 20, (tiltRight - lastRight) * 20, front, right);
        lastFront = tiltFront;
        lastRight = tiltRight;
        return attitude;
    }

    /**
     * The side jets against its tilt off the lean it wants and how fast it tilts; the jets near the nose push its top
     * the way it should go and those near the bottom push its bottom the other way, so it turns about its middle
     * without being pushed across.
     */
    private void jets(ServerLevel level, BlockPos origin, Attitude attitude, Vec3 lean) {
        double pushFront = TILT_GAIN * (lean.dot(attitude.frontWay()) - attitude.front()) - TILT_DAMPING * attitude.frontRate();
        double pushRight = TILT_GAIN * (lean.dot(attitude.rightWay()) - attitude.right()) - TILT_DAMPING * attitude.rightRate();
        int frontSignal = signal(pushFront);
        int sideSignal = signal(pushRight);
        pair(level, origin, top.ahead(), top.back(), pushFront);
        pair(level, origin, top.right(), top.left(), pushRight);
        pair(level, origin, bottom.back(), bottom.ahead(), pushFront);
        pair(level, origin, bottom.left(), bottom.right(), pushRight);
        int[] now = {pushFront > 0 ? frontSignal : 0, pushRight > 0 ? sideSignal : 0, pushFront < 0 ? frontSignal : 0, pushRight < 0 ? sideSignal : 0,
            pushFront < 0 ? frontSignal : 0, pushRight < 0 ? sideSignal : 0, pushFront > 0 ? frontSignal : 0, pushRight > 0 ? sideSignal : 0};
        System.arraycopy(now, 0, jetSignals, 0, jetSignals.length);
    }

    /**
     * Leaves out its side jets' plumes, once each time it is loaded and flies: a rocket built before they went without
     * one has them still.
     */
    private void leaveJetPlumesOut(ServerLevel level, BlockPos origin) {
        if (jetPlumesLeftOut) {
            return;
        }
        jetPlumesLeftOut = true;
        for (Jets ring : List.of(top, bottom)) {
            ring.all().forEach(relay -> Thrusters.leavePlumeOut(level, origin.offset(relay)));
        }
    }

    /** The signal a push asks of a jet: as strong as the push, up to full. */
    private static int signal(double push) {
        return (int) Math.round(Mth.clamp(Math.abs(push), 0, FULL));
    }

    /** The signal asked for, held at the one set last until it moves {@link #SIGNAL_HOLD} from it, then the nearest. */
    private static int held(double wanted, int last) {
        return Math.abs(wanted - last) < SIGNAL_HOLD ? last : (int) Math.round(wanted);
    }

    Report report() {
        List<Integer> jets = new ArrayList<>();
        for (int signal : jetSignals) {
            jets.add(signal);
        }
        return new Report(lastShare, trim, counted, lastFront, lastRight, jets, lastClimb);
    }

    /** A way along the ground, flattened from one of the rocket's own and made a unit long. */
    private static Vec3 flat(Vec3 way) {
        Vec3 flat = new Vec3(way.x, 0, way.z);
        return flat.lengthSqr() < 1e-9 ? Vec3.ZERO : flat.normalize();
    }

    /** Two opposite jets for a push one way, as strong as asked: the one pushing that way fires, the other rests. */
    private static void pair(ServerLevel level, BlockPos origin, @Nullable BlockPos forward, @Nullable BlockPos backward, double push) {
        int signal = signal(push);
        drive(level, origin, forward, push > 0 ? signal : 0);
        drive(level, origin, backward, push < 0 ? signal : 0);
    }

    private static void drive(ServerLevel level, BlockPos origin, @Nullable BlockPos relay, int signal) {
        if (relay != null) {
            Relays.drive(level, origin, relay, signal, false);
        }
    }

    /** Every side jet puffs at once, as its stability snaps back on and they catch it. */
    private void puffAll(ServerLevel level, BlockPos origin) {
        for (BlockPos jet : jetRelays()) {
            if (jet != null) {
                level.playSound(null, origin.offset(jet), SkylinersRegistry.ROCKET_JET_PUFF.get(), SoundSource.BLOCKS, SNAP_PUFF_VOLUME, 1.0F);
            }
        }
    }

    /**
     * At rest on the ground or switched off: every engine and jet let go, and its report says so, as her sounds follow
     * it (see {@link RocketSounds}). Its relays are never sent to the clients: nothing there reads them, and each sent
     * would have them redraw that part of the rocket.
     */
    void rest(ServerLevel level, BlockPos origin) {
        Relays.drive(level, origin, links(), 0, false);
        lastShare = 0;
        Arrays.fill(jetSignals, 0);
    }

    /**
     * Each side jet's relay, as an offset from the control head, in the order its report gives their signals (near
     * the nose ahead, right, back and left, then near the bottom); null where there is none.
     */
    List<BlockPos> jetRelays() {
        return Arrays.asList(top.ahead(), top.right(), top.back(), top.left(), bottom.ahead(), bottom.right(), bottom.back(), bottom.left());
    }

    /** Told to land, or switched off in the air: it comes down where it is now, rather than where it was going. */
    void landHere(SableFlight.State state) {
        rose = state.position();
        holding = false;
    }

    /**
     * Its landing called off: it holds its height where it climbs back to, rather than coming down, until told to
     * land again or given a pin.
     */
    void holdHere() {
        holding = true;
    }

    /** Whether it holds its height where it is, its landing called off, until told to land or given a pin. */
    boolean holding() {
        return holding;
    }

    /** Landed, or the flight given up: the next flight begins with a countdown again. */
    void stop(ServerLevel level, BlockPos origin) {
        rest(level, origin);
        controls.stop();
        rose = null;
        holding = false;
        counted = 0;
        settled = 0;
        stoppedFor = 0;
        circled = 0;
        lastShare = 0;
        lastClimb = 0;
        lastTotal = 0;
        Arrays.fill(jetSignals, 0);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag saved = new ListTag();
        bells.forEach(bell -> saved.add(Relays.write(bell)));
        tag.put("Bells", saved);
        tag.put("TopJets", top.save());
        tag.put("BottomJets", bottom.save());
        tag.putDouble("Trim", trim);
        tag.putInt("Counted", counted);
        controls.save(tag);
        if (rose != null) {
            tag.putDouble("RoseX", rose.x);
            tag.putDouble("RoseY", rose.y);
            tag.putDouble("RoseZ", rose.z);
        }
        return tag;
    }

    void load(CompoundTag tag, Rotation turn) {
        List<List<BlockPos>> loaded = new ArrayList<>();
        for (Tag entry : tag.getList("Bells", Tag.TAG_LIST)) {
            loaded.add(Turning.turn(Relays.read((ListTag) entry), turn));
        }
        configure(loaded, Jets.load(tag.getCompound("TopJets"), turn), Jets.load(tag.getCompound("BottomJets"), turn));
        trim = tag.contains("Trim") ? Mth.clamp(tag.getDouble("Trim"), 1 / MOST_TRIM, MOST_TRIM) : 1;
        counted = tag.getInt("Counted");
        controls.load(tag);
        rose = tag.contains("RoseX") ? new Vec3(tag.getDouble("RoseX"), tag.getDouble("RoseY"), tag.getDouble("RoseZ")) : null;
    }
}
