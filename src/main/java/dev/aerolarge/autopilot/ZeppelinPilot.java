package dev.aerolarge.autopilot;

import dev.aerolarge.block.EngineRepeaterBlockEntity;
import dev.aerolarge.block.Relays;
import dev.aerolarge.block.Turning;
import dev.aerolarge.compat.SableFlight;
import dev.aerolarge.boost.SpeedBoost;
import dev.aerolarge.skywriting.Skywriting;
import dev.aerolarge.upship.UpShip;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * How the zeppelin's autopilot flies it, by the physics of a lighter-than-air ship. It floats on the hot air its gas
 * cells' burners fill them with, each m³ lifting {@link #HOT_AIR} of Sable's units as thick as the air is there, so it
 * works out from the zeppelin's mass and the air's thickness how much hot air floats it, and asks the burners for that
 * much, a share more to climb and less to sink, learning whatever share more its own trim needs as it flies. The hot
 * air takes seconds to come and go, and the zeppelin is slow to start and stop, so it climbs and sinks gently, at a
 * couple of blocks a second at most.
 *
 * <p>It holds the nose level by sharing the hot air between the cells: nose up, it burns the forward cells lower and
 * the aft ones higher, learning the share that holds it level. It drives the zeppelin with its four engines'
 * propellers, ahead or astern, at the speed set, and turns it by driving the port engines harder than the starboard
 * ones or the other way round, astern on one side and ahead on the other to turn it on the spot. It flies a heading,
 * round a circle, or to a pin, slowing as it comes near and hovering over it; set to come down at the pin, or told to
 * come down where it is, it sinks gently to the ground, rests on its wheels with the burners holding most of its
 * weight, and stops its engines.
 */
public final class ZeppelinPilot {
    /**
     * What may hold a zeppelin on the ground a moment as she takes off, before she rises, such as her ground handlers
     * on their lines: a feature is taken out of the take-off with its line on the list.
     */
    private static final List<TakeoffHold> HOLDS = List.of(UpShip::holds);
    /**
     * What may steer a zeppelin a while in place of what her autopilot is set to fly, such as a speed boost or
     * skywriting a shape, the first that steers her: a feature is taken out of her flying with its line on the list.
     */
    private static final List<Course> COURSES = List.of(SpeedBoost::steer, Skywriting::steer);
    /** The zeppelin's engines, numbered from 0: two to port (0 forward, 1 aft), two to starboard (2 aft, 3 forward). */
    static final int ENGINES = 4;
    /** How much a m³ of hot air lifts at sea level, in Sable's units: Create Aeronautics' {@code hotAirStrength}. */
    private static final double HOT_AIR = 1.5;
    /** A redstone signal at its strongest, which drives a burner or a thruster as hard as it goes. */
    private static final int FULL_SIGNAL = 15;
    /**
     * The most hot air one burner gives, in m³ (its value box at the most), at full signal: as a full-size zeppelin's
     * are built, and a smaller one's are turned down from, to fill no more than their cell holds.
     */
    public static final double BURNER_GAS = 500;
    /** How fast the air thins going up, as Sable's air pressure does: a share each block. */
    private static final double THINNING = 0.004;
    /**
     * The least signal a burner is given, its pilot flame: put out, its cell's balloon would be lost and found afresh.
     */
    private static final int PILOT_FLAME = 1;
    /**
     * The climb asked for per block of height still to go, in blocks a second, and the fastest it climbs and sinks.
     * Coming down to land, the sink asked for per block over the ground, from the slowest, onto the ground, to the
     * fastest, high up, so that it comes down briskly and touches down gently.
     */
    private static final double HEIGHT_GAIN = 0.05;
    private static final double MAX_CLIMB = 2;
    private static final double MAX_SINK = 1.5;
    private static final double LANDING_GAIN = 0.06;
    private static final double LANDING_SINK = 1;
    private static final double FASTEST_LANDING = 3;
    /** How full its burners may be at its ceiling, as a share of all they burn: headroom for the trims and the split. */
    private static final double CEILING_SHARE = 0.85;
    /**
     * The share of hot air over what floats it asked for per block a second it climbs too slowly, how fast the share
     * that holds its height is learnt (a tick, per block a second off), and the most either goes either way.
     */
    private static final double CLIMB_SHARE = 0.03;
    private static final double LIFT_LEARN = 0.0004;
    private static final double LIFT_TRIM_LIMIT = 0.2;
    private static final double MOST_SHARE = 0.3;
    /**
     * On the ground, resting on its wheels, it holds this share of its weight up with hot air: a whole step of the
     * burners' signal under what floats it, or more, so that the signal's rounding never leaves it floating off again.
     */
    private static final double RESTING = 0.8;
    /**
     * The fore-and-aft split of the hot air per degree the nose is up, and per degree a second it is rising, as a share
     * of each cell's burning taken from the forward cells and given to the aft ones (or back); how fast the split that
     * holds the nose level is learnt, a tick per degree; and the most it splits by.
     */
    private static final double PITCH_SPLIT = 0.05;
    private static final double PITCH_DAMPING = 0.1;
    private static final double PITCH_LEARN = 0.0005;
    private static final double MOST_SPLIT = 0.8;
    /**
     * The engines: signal per block a second it goes slower than it should; the signal per block a second of the
     * speed set that holds it against the air, learnt as it flies (a tick, per block a second off), up to the most it
     * learns, and learnt only at speed, the air holding back nothing still; and how much harder the astern thrusters
     * are driven than their share, there being fewer of them.
     */
    private static final double SPEED_GAIN = 1.5;
    private static final double THRUST_LEARN = 0.003;
    private static final double MOST_THRUST_TRIM = 3;
    private static final double LEARNING_SPEED = 1;
    private static final double ASTERN_BOOST = 4;
    /**
     * Turning: the engines' signal split per degree off the heading, taken off per degree a second it is turning
     * (damping), and the most it splits by.
     */
    private static final double YAW_GAIN = 0.4;
    private static final double YAW_DAMPING = 3;
    private static final double MOST_YAW = 10;
    /** How far the nose is held up or down with the elevator wheel turned all the way, flown by hand, in degrees. */
    private static final double HAND_NOSE = 8;
    /**
     * Flying to a pin: the speed it slows to per block nearer than its slowing distance, so it comes in gently, and the
     * least it creeps on at, slower than counts as stopped, until it is over the pin; how near counts as over it, and
     * how slow as stopped.
     */
    private static final double APPROACH_SLOWING = 0.08;
    private static final double APPROACH_LEAST = 0.8;
    private static final double OVER_PIN = 6;
    private static final double STOPPED = 1;
    /**
     * Coming down: how high over the ground, in blocks, and how slowly sinking, in blocks a second, it may be and count
     * as down, once it has been so for this many ticks; measured under the control car, it may never read nothing at
     * all, standing over a dip or with its stern down first on higher ground, and pressing down on its wheels, it
     * jitters on them a little.
     */
    private static final double SETTLED_HEIGHT = 3;
    private static final double SETTLED_SINK = 0.3;
    private static final int SETTLED_TICKS = 20;
    /**
     * Rising off the ground: how many ticks it tries before it gives up, too heavy for its burners to lift, and how
     * high it must have risen by then, in blocks.
     */
    private static final int UP_SHIP_TICKS = 60 * SharedConstants.TICKS_PER_SECOND;
    private static final double UP_SHIP_RISEN = 1;
    /**
     * Circling: how far it turns in toward the middle at most, in degrees, and how many degrees in it turns per
     * circle's radius it is off the circle, steering back onto it.
     */
    private static final double MOST_TURN_IN = 45;
    private static final double TURN_IN = 90;
    /** How near the height set, in blocks, counts as holding it. */
    private static final double LEVEL_OFF = 8;
    /** How far it has risen off the ground, in blocks, before the engines start on the way up. */
    private static final double ENGINES_CLEAR = 15;
    /**
     * Flying to a pin to land at it: how high over the ground it may come down to on the way, in blocks, over the pin
     * and for each block it is still away from it, so that it comes down along its approach rather than all the way
     * over the pin, its engines running until it is there.
     */
    private static final double GLIDE_FLOOR = 20;
    private static final double GLIDE = 0.25;

    /**
     * A gas cell's burners: their relays, as offsets from the autopilot, how far ahead of the cells' middle the cell
     * hangs, in blocks (behind it, less than nothing), which decides whether it lifts the nose or the tail, and the most
     * hot air they give together, at full signal, in m³.
     */
    public record Cell(List<BlockPos> relays, double ahead, double fullGas) {
    }

    /**
     * What it is set to fly, from the autopilot: the phase it is in, the height (world blocks), speed (blocks a
     * second) and heading (Minecraft yaw) to hold, the way it flies, the point an orbit or a pin is at and the orbit's
     * radius, and whether it comes down at the pin.
     */
    record Orders(AutopilotBlockEntity.Phase phase, double altitude, double speed, double heading, AutopilotBlockEntity.Lateral lateral,
                  double pinX, double pinZ, double radius, boolean landAtPin) {
    }

    /**
     * How it is working the zeppelin, for play-testing: each cell's burners' signal, bow first, each engine's ahead and
     * astern, and what it has learnt holds the height, the nose and the speed.
     */
    public record Report(List<Integer> cells, List<Integer> ahead, List<Integer> astern, double liftTrim, double pitchTrim, double thrustTrim) {
    }

    private List<Cell> cells = List.of();
    private Map<Integer, List<BlockPos>> ahead = Map.of();
    private Map<Integer, List<BlockPos>> astern = Map.of();
    private int burners;
    /** The most hot air all its burners give together, at full signal, in m³. */
    private double fullGas;
    /** How far above the autopilot the cells' hot air stands, in blocks, where the air it lifts in is thinner. */
    private double gasAbove;
    /**
     * How far aft of the autopilot the middle of the cells' lift is, in blocks: about where the zeppelin turns on the
     * spot, and what it steers over a pin. Steered from the control car in its bow, a pin under its middle would stay
     * behind the car however it turned.
     */
    private double middleAft;
    private double liftTrim;
    private double pitchTrim;
    private double thrustTrim;
    private double lastPitch;
    private double lastHeading;
    /** Whether it has started from how the zeppelin is since it was last loaded (see {@link #engage}). */
    private boolean primed;
    /** Whether anything on {@link #HOLDS} has been asked yet this take-off, and whether they have all let her go. */
    private boolean holdAsked;
    private boolean letGo;
    /** How many ticks it has been coming down and still at the ground, and rising off it (see {@link #upShip}). */
    private int settled;
    private int risingTicks;
    /** The control car's hand controls, which it flies by while switched off and shows what it does on. */
    private final ZeppelinControls controls = new ZeppelinControls();
    /**
     * What it did last, to show on the controls: the climb it asked for (blocks a second), the split of its burners
     * fore and aft, its engines' thrust and the split between the sides that turns it (signals).
     */
    private double lastClimb;
    private double lastSplit;
    private double lastThrust;
    private double lastTurn;
    /** The signals last set, by cell and by engine, so they are only set when they change. */
    private final Map<Integer, Integer> cellSignals = new HashMap<>();
    private final Map<Integer, Integer> engineSignals = new HashMap<>();

    /** Wired when the zeppelin is built: its cells' burners, its engines' relays and where its hot air stands. */
    void configure(List<Cell> cells, Map<Integer, List<BlockPos>> ahead, Map<Integer, List<BlockPos>> astern, double gasAbove, double middleAft) {
        this.cells = List.copyOf(cells);
        this.ahead = Map.copyOf(ahead);
        this.astern = Map.copyOf(astern);
        this.gasAbove = gasAbove;
        this.middleAft = middleAft;
        this.burners = cells.stream().mapToInt(cell -> cell.relays().size()).sum();
        this.fullGas = cells.stream().mapToDouble(Cell::fullGas).sum();
    }

    boolean wired() {
        return burners > 0;
    }

    /** Wired when the zeppelin is built: the control car's hand controls, as offsets from the autopilot. */
    void wireControls(BlockPos rudder, BlockPos elevator, BlockPos port, BlockPos starboard, BlockPos lever) {
        controls.wire(rudder, elevator, port, starboard, lever);
    }

    /** Every relay it works, for mending its links (see {@link Turning#mend}). */
    List<BlockPos> links() {
        List<BlockPos> links = new ArrayList<>();
        cells.forEach(cell -> links.addAll(cell.relays()));
        ahead.values().forEach(links::addAll);
        astern.values().forEach(links::addAll);
        return links;
    }

    /** Starts afresh from how the zeppelin is now, keeping what it has learnt. */
    void engage(SableFlight.State state) {
        lastPitch = state.pitch();
        lastHeading = state.heading();
        settled = 0;
        risingTicks = 0;
        holdAsked = false;
        letGo = false;
        primed = true;
    }

    /**
     * Flies one tick: the burners for the height and the nose, the engines for the speed and the heading. The phase it
     * goes on in: up from the ground it climbs, near the height it cruises, stopped over a pin it is to land at it
     * comes down, holding itself over the pin all the way, and down on the ground it is done. Taking off, it rests on
     * the ground while anything on {@link #HOLDS} holds it.
     */
    AutopilotBlockEntity.Phase fly(ServerLevel level, BlockPos origin, Orders orders, SableFlight.State state, double radio, double mass) {
        if (!primed) {
            engage(state);
        }
        if (orders.phase() == AutopilotBlockEntity.Phase.TAKEOFF && held(level, origin, radio)) {
            lastPitch = state.pitch();
            lastHeading = state.heading();
            rest(level, origin, state, mass);
            return AutopilotBlockEntity.Phase.TAKEOFF;
        }
        double pitch = state.pitch();
        double pitchRate = (pitch - lastPitch) * SharedConstants.TICKS_PER_SECOND;
        double yawRate = Mth.wrapDegrees(state.heading() - lastHeading) * SharedConstants.TICKS_PER_SECOND;
        lastPitch = pitch;
        lastHeading = state.heading();
        Vec3 middle = middle(state);
        double distance = Math.hypot(orders.pinX() - middle.x, orders.pinZ() - middle.z);
        boolean overPin = orders.lateral() == AutopilotBlockEntity.Lateral.DESTINATION && distance < OVER_PIN && Math.abs(state.airspeed()) < STOPPED;
        boolean rising = orders.phase() == AutopilotBlockEntity.Phase.TAKEOFF;
        boolean comingDown = orders.phase() == AutopilotBlockEntity.Phase.LAND || !rising && orders.landAtPin() && overPin;
        double altitude = altitude(orders, state, radio, distance);
        double climb = comingDown ? -landingSink(radio) : Mth.clamp(HEIGHT_GAIN * (altitude - state.position().y), -MAX_SINK, MAX_CLIMB);
        burn(level, origin, state, mass, climb, pitch, pitchRate);
        boolean upShip = rising && radio < ENGINES_CLEAR;
        if (upShip || comingDown && radio < ENGINES_CLEAR) {
            drive(level, origin, holdSpeed(state, 0), 0, false);
        } else {
            Course.Steer steer = steer(level, origin, middle, state.heading());
            double heading = steer != null ? steer.heading() : heading(orders, state, middle, distance);
            double ahead = Mth.wrapDegrees(heading - state.heading());
            double speed = steer != null ? steer.speed() : speed(orders, distance, ahead);
            double thrust = steer != null && steer.holdsThrust() ? lastThrust : holdSpeed(state, speed);
            drive(level, origin, thrust, yaw(heading, state.heading(), yawRate), false);
        }
        settled = comingDown && radio < SETTLED_HEIGHT && Math.abs(state.velocity().y) < SETTLED_SINK ? settled + 1 : 0;
        if (settled >= SETTLED_TICKS) {
            return AutopilotBlockEntity.Phase.OFF;
        }
        if (rising) {
            return upShip(radio, orders.phase());
        }
        return comingDown ? AutopilotBlockEntity.Phase.LAND : heightPhase(altitude - state.position().y);
    }

    /**
     * The height it flies at: the one set, or, flying to a pin to land at it, no higher than its glide down to it over
     * the ground under it.
     */
    private static double altitude(Orders orders, SableFlight.State state, double radio, double distance) {
        if (!orders.landAtPin() || orders.lateral() != AutopilotBlockEntity.Lateral.DESTINATION) {
            return orders.altitude();
        }
        double ground = state.position().y - radio;
        return Math.min(orders.altitude(), ground + GLIDE_FLOOR + GLIDE * Math.max(0, distance - OVER_PIN));
    }

    /** How fast it sinks coming down to land, from how high over the ground it is, in blocks a second. */
    private static double landingSink(double radio) {
        return Mth.clamp(LANDING_GAIN * radio, LANDING_SINK, FASTEST_LANDING);
    }

    /**
     * Whether something on {@link #HOLDS} holds her on the ground a moment yet as she takes off; once all have let her
     * go, she is not held again this take-off.
     */
    private boolean held(ServerLevel level, BlockPos origin, double radio) {
        if (letGo || !(level.getBlockEntity(origin) instanceof AutopilotBlockEntity autopilot)) {
            return false;
        }
        boolean first = !holdAsked;
        holdAsked = true;
        boolean holding = false;
        for (TakeoffHold hold : HOLDS) {
            holding |= hold.holds(level, autopilot, radio, first);
        }
        letGo = !holding;
        return holding;
    }

    /** The first heading and speed something on {@link #COURSES} steers her by this tick; null for none. */
    @Nullable
    private static Course.Steer steer(ServerLevel level, BlockPos origin, Vec3 middle, double heading) {
        if (!(level.getBlockEntity(origin) instanceof AutopilotBlockEntity autopilot)) {
            return null;
        }
        for (Course course : COURSES) {
            Course.Steer steer = course.steer(level, autopilot, middle, heading);
            if (steer != null) {
                return steer;
            }
        }
        return null;
    }

    /**
     * Rising off the ground: on to the climb once clear of it, or, still on the ground a minute after it started,
     * switched off again: too heavy for its burners.
     */
    private AutopilotBlockEntity.Phase upShip(double radio, AutopilotBlockEntity.Phase phase) {
        risingTicks++;
        if (radio >= ENGINES_CLEAR) {
            return AutopilotBlockEntity.Phase.CLIMB;
        }
        return risingTicks > UP_SHIP_TICKS && radio < UP_SHIP_RISEN ? AutopilotBlockEntity.Phase.OFF : phase;
    }

    /** Whether it is climbing to the height set, coming down to it, or holding it: near enough, it holds it. */
    private static AutopilotBlockEntity.Phase heightPhase(double toGo) {
        if (Math.abs(toGo) < LEVEL_OFF) {
            return AutopilotBlockEntity.Phase.CRUISE;
        }
        return toGo > 0 ? AutopilotBlockEntity.Phase.CLIMB : AutopilotBlockEntity.Phase.DESCENT;
    }

    /**
     * Burns for the climb asked for: as much hot air as floats the zeppelin here, a share more or less to climb or
     * sink, split between the cells fore and aft of its middle to hold its nose level; learning, as the climb and the
     * nose fall short, the trims that hold them.
     */
    private void burn(ServerLevel level, BlockPos origin, SableFlight.State state, double mass, double climb, double pitch, double pitchRate) {
        double error = climb - state.velocity().y;
        double share = Mth.clamp(CLIMB_SHARE * error + liftTrim, -MOST_SHARE, MOST_SHARE);
        if (Math.abs(share) < MOST_SHARE) {
            liftTrim = Mth.clamp(liftTrim + LIFT_LEARN * error, -LIFT_TRIM_LIMIT, LIFT_TRIM_LIMIT);
        }
        double split = Mth.clamp(-PITCH_SPLIT * pitch - PITCH_DAMPING * pitchRate + pitchTrim, -MOST_SPLIT, MOST_SPLIT);
        if (Math.abs(split) < MOST_SPLIT) {
            pitchTrim = Mth.clamp(pitchTrim - PITCH_LEARN * pitch, -MOST_SPLIT, MOST_SPLIT);
        }
        lastClimb = climb;
        lastSplit = split;
        setBurners(level, origin, floats(state, mass) * (1 + share), split);
    }

    /**
     * The highest it floats with its burners near full, in world blocks at the autopilot: where the thinning air leaves
     * its hot air too little lift for its mass, short of full by a share, for the trims and the split fore and aft to
     * work in. NaN without burners, a mass or air.
     */
    double ceiling(SableFlight.State state, double mass) {
        double most = CEILING_SHARE * fullGas;
        double air = state.air() * Math.exp(-THINNING * gasAbove);
        if (Double.isNaN(mass) || mass <= 0 || most <= 0 || air <= 0) {
            return Double.NaN;
        }
        return state.position().y + Math.log(air * HOT_AIR * most / mass) / THINNING;
    }

    /**
     * How much hot air floats the zeppelin where it is, in m³: its mass (in Sable's units) against what a m³ lifts in
     * the air there.
     */
    private double floats(SableFlight.State state, double mass) {
        double air = state.air() * Math.exp(-THINNING * gasAbove);
        return Double.isNaN(mass) || air <= 0 ? 0 : mass / (HOT_AIR * air);
    }

    /**
     * Sets the burners for so much hot air in all, split fore and aft by a share, each cell's within the signal's
     * steps.
     */
    private void setBurners(ServerLevel level, BlockPos origin, double gas, double split) {
        double reach = cells.stream().mapToDouble(cell -> Math.abs(cell.ahead())).max().orElse(1);
        double each = fullGas <= 0 ? 0 : FULL_SIGNAL * gas / fullGas;
        for (int i = 0; i < cells.size(); i++) {
            Cell cell = cells.get(i);
            double lean = reach > 0 ? cell.ahead() / reach : 0;
            int signal = Mth.clamp((int) Math.round(each * (1 + split * lean)), PILOT_FLAME, FULL_SIGNAL);
            Integer last = cellSignals.get(i);
            if (last == null || last != signal) {
                cellSignals.put(i, signal);
                Relays.drive(level, origin, cell.relays(), signal, false);
            }
        }
    }

    /**
     * On the ground, done: the burners holding most of its weight, so it rests light on its wheels, the engines
     * stopped. What it learnt of its height's trim on the way down, pressing on the ground, is forgotten.
     */
    void rest(ServerLevel level, BlockPos origin, SableFlight.State state, double mass) {
        liftTrim = 0;
        settled = 0;
        risingTicks = 0;
        setBurners(level, origin, RESTING * floats(state, mass), pitchTrim);
        drive(level, origin, 0, 0, false);
        controls.show(level, origin, ZeppelinControls.Setting.AT_REST);
    }

    /**
     * Switched off: the engines stopped, and the zeppelin handed over to the control car's controls, set to hold it
     * where it floats until someone moves them. Without any, the burners are left as they are.
     */
    void stop(ServerLevel level, BlockPos origin) {
        drive(level, origin, 0, 0, false);
        controls.handOver(level, origin, ZeppelinControls.Setting.FLOATING);
    }

    /**
     * Flies one tick by hand, the autopilot switched off, once someone has moved a control in the control car (see
     * {@link ZeppelinControls}): the burner lever climbs or sinks it, holding its height in the middle; the elevator
     * wheel holds its nose up or down; each telegraph drives its side's engines, and the rudder wheel turns it. On the
     * ground with the lever down, it rests there as it does landed, learning nothing from the ground holding it up.
     */
    void flyByHand(ServerLevel level, BlockPos origin, SableFlight.State state, double radio, double mass) {
        ZeppelinControls.Setting hand = controls.byHand(level, origin);
        if (hand == null) {
            return;
        }
        if (!primed) {
            engage(state);
        }
        double pitch = state.pitch();
        double pitchRate = (pitch - lastPitch) * SharedConstants.TICKS_PER_SECOND;
        lastPitch = pitch;
        lastHeading = state.heading();
        double climb = hand.lever() > 0 ? hand.lever() * MAX_CLIMB : hand.lever() * MAX_SINK;
        if (climb < 0 && radio < SETTLED_HEIGHT) {
            lastClimb = climb;
            setBurners(level, origin, RESTING * floats(state, mass), pitchTrim);
        } else {
            burn(level, origin, state, mass, climb, pitch + hand.elevator() * HAND_NOSE, pitchRate);
        }
        double thrust = (hand.port() + hand.starboard()) / 2 * FULL_SIGNAL;
        drive(level, origin, thrust, (hand.port() - hand.starboard()) / 2 * FULL_SIGNAL + hand.rudder() * MOST_YAW, true);
    }

    /**
     * Shows on the control car's controls what it does as it flies: the climb on the lever, the nose's trim on the
     * elevator wheel, the engines' thrust on both telegraphs and the turn on the rudder wheel.
     */
    void showControls(ServerLevel level, BlockPos origin) {
        double lever = lastClimb > 0 ? lastClimb / MAX_CLIMB : lastClimb / MAX_SINK;
        double thrust = lastThrust / FULL_SIGNAL;
        controls.show(level, origin, new ZeppelinControls.Setting(lever, -lastSplit / MOST_SPLIT, thrust, thrust, lastTurn / MOST_YAW));
    }

    /**
     * The speed it flies at: the one set, or, flying to a pin, slower the nearer it comes, and slower still the further
     * the pin lies off its nose, down to none with the pin abeam or behind it, where it turns on the spot to face it
     * first: past it, it comes back to it rather than swinging round in a wide circle.
     */
    private static double speed(Orders orders, double distance, double offNose) {
        if (orders.lateral() != AutopilotBlockEntity.Lateral.DESTINATION) {
            return orders.speed();
        }
        double facing = Math.max(0, Math.cos(Math.toRadians(offNose)));
        double slowing = distance > OVER_PIN ? Math.max(APPROACH_LEAST, APPROACH_SLOWING * (distance - OVER_PIN / 2)) : APPROACH_SLOWING * Math.max(0, distance - OVER_PIN / 2);
        return Math.min(orders.speed(), slowing) * facing;
    }

    /** The heading it steers for: the one set, the way round its circle, or toward the pin (held once over it). */
    private static double heading(Orders orders, SableFlight.State state, Vec3 middle, double distance) {
        return switch (orders.lateral()) {
            case HEADING -> orders.heading();
            case DESTINATION -> distance < OVER_PIN / 2 ? state.heading() : bearingTo(middle, orders.pinX(), orders.pinZ());
            case ORBIT_LEFT, ORBIT_RIGHT -> round(orders, middle);
        };
    }

    /** Where the middle of the cells' lift is in the world, {@link #middleAft} behind the autopilot. */
    private Vec3 middle(SableFlight.State state) {
        Vec3 nose = new Vec3(state.nose().x, 0, state.nose().z).normalize();
        return state.position().subtract(nose.scale(middleAft));
    }

    /**
     * The heading round a circle: along it, the way it goes round, turned in toward the middle as far as it is
     * outside the circle and out as far as it is inside, so it steers onto it.
     */
    private static double round(Orders orders, Vec3 middle) {
        double side = orders.lateral() == AutopilotBlockEntity.Lateral.ORBIT_RIGHT ? 1 : -1;
        double toMiddle = bearingTo(middle, orders.pinX(), orders.pinZ());
        double off = Math.hypot(orders.pinX() - middle.x, orders.pinZ() - middle.z) - orders.radius();
        double correction = Mth.clamp(off / orders.radius() * TURN_IN, -MOST_TURN_IN, MOST_TURN_IN);
        return Mth.wrapDegrees(toMiddle - side * (90 - correction));
    }

    /** The heading from a point to another, as Minecraft yaw (0 south, 90 west). */
    private static double bearingTo(Vec3 from, double x, double z) {
        return Mth.wrapDegrees(Math.toDegrees(Math.atan2(-(x - from.x), z - from.z)));
    }

    /**
     * The engines' signal for a speed: ahead above nothing, astern below. What holds it against the air grows with the
     * speed, learnt as it flies; stopped, nothing does, so holding still over a pin it turns on the spot freely.
     */
    private double holdSpeed(SableFlight.State state, double speed) {
        Vec3 nose = new Vec3(state.nose().x, 0, state.nose().z).normalize();
        double ahead = state.velocity().x * nose.x + state.velocity().z * nose.z;
        double error = speed - ahead;
        double signal = thrustTrim * speed + SPEED_GAIN * error;
        if (Math.abs(signal) < FULL_SIGNAL && Math.abs(speed) >= LEARNING_SPEED) {
            thrustTrim = Mth.clamp(thrustTrim + THRUST_LEARN * error / speed, -MOST_THRUST_TRIM, MOST_THRUST_TRIM);
        }
        return signal;
    }

    /** The split between the port and starboard engines that turns it onto a heading. */
    private static double yaw(double wanted, double heading, double yawRate) {
        double off = Mth.wrapDegrees(wanted - heading);
        return Mth.clamp(YAW_GAIN * off - YAW_DAMPING * yawRate, -MOST_YAW, MOST_YAW);
    }

    /**
     * Drives the engines: so much thrust, ahead or astern, split between the sides to turn it, the port engines (the
     * first two) the harder to turn it to starboard. An engine with a repeater in its car follows only once its order
     * is answered there (see {@link EngineRepeaterBlockEntity}), and runs on as it was until then: flown by hand, its
     * engineer has a few seconds to answer before its crew do; under the autopilot, its crew answer at once.
     */
    private void drive(ServerLevel level, BlockPos origin, double thrust, double turn, boolean byHand) {
        lastThrust = thrust;
        lastTurn = turn;
        BoundingBox frame = level.getBlockEntity(origin) instanceof AutopilotBlockEntity autopilot ? autopilot.frame() : null;
        for (int engine = 0; engine < ENGINES; engine++) {
            double side = engine < ENGINES / 2 ? 1 : -1;
            double push = thrust + side * turn;
            if (!answered(level, frame, engine, push / FULL_SIGNAL, byHand)) {
                continue;
            }
            int forward = Mth.clamp((int) Math.round(push), 0, FULL_SIGNAL);
            int back = Mth.clamp((int) Math.round(-push * ASTERN_BOOST), 0, FULL_SIGNAL);
            int key = engine * 2;
            setEngine(level, origin, key, ahead.getOrDefault(engine, List.of()), forward);
            setEngine(level, origin, key + 1, astern.getOrDefault(engine, List.of()), back);
        }
    }

    /**
     * Whether an engine may do as it is asked, a share of full thrust, by hand or by the autopilot: its order
     * answered, or no repeater by it.
     */
    private static boolean answered(ServerLevel level, @Nullable BoundingBox frame, int engine, double share, boolean byHand) {
        EngineRepeaterBlockEntity repeater = frame == null ? null : EngineRepeaterBlockEntity.standingBy(level, frame, engine);
        return repeater == null || repeater.ask(share, byHand);
    }

    private void setEngine(ServerLevel level, BlockPos origin, int key, List<BlockPos> relays, int signal) {
        Integer last = engineSignals.get(key);
        if (last == null || last != signal) {
            engineSignals.put(key, signal);
            Relays.drive(level, origin, relays, signal, true);
        }
    }

    Report report() {
        List<Integer> burning = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            burning.add(cellSignals.getOrDefault(i, 0));
        }
        List<Integer> forward = new ArrayList<>();
        List<Integer> back = new ArrayList<>();
        for (int engine = 0; engine < ENGINES; engine++) {
            forward.add(engineSignals.getOrDefault(engine * 2, 0));
            back.add(engineSignals.getOrDefault(engine * 2 + 1, 0));
        }
        return new Report(burning, forward, back, liftTrim, pitchTrim, thrustTrim);
    }

    /** Turns its links as its block has turned (see {@link Turning}). */
    void turn(Rotation turn) {
        cells = cells.stream().map(cell -> new Cell(Turning.turn(cell.relays(), turn), cell.ahead(), cell.fullGas())).toList();
        Map<Integer, List<BlockPos>> turnedAhead = new HashMap<>();
        ahead.forEach((engine, relays) -> turnedAhead.put(engine, Turning.turn(relays, turn)));
        ahead = turnedAhead;
        Map<Integer, List<BlockPos>> turnedAstern = new HashMap<>();
        astern.forEach((engine, relays) -> turnedAstern.put(engine, Turning.turn(relays, turn)));
        astern = turnedAstern;
        controls.turn(turn);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Cell cell : cells) {
            CompoundTag saved = new CompoundTag();
            saved.put("Relays", Relays.write(cell.relays()));
            saved.putDouble("Ahead", cell.ahead());
            saved.putDouble("FullGas", cell.fullGas());
            list.add(saved);
        }
        tag.put("Cells", list);
        tag.put("AheadRelays", engines(ahead));
        tag.put("AsternRelays", engines(astern));
        tag.putDouble("GasAbove", gasAbove);
        tag.putDouble("LiftTrim", liftTrim);
        tag.putDouble("PitchTrim", pitchTrim);
        tag.putDouble("ThrustPerSpeed", thrustTrim);
        tag.putDouble("MiddleAft", middleAft);
        tag.put("Controls", controls.save());
        return tag;
    }

    private static CompoundTag engines(Map<Integer, List<BlockPos>> relays) {
        CompoundTag tag = new CompoundTag();
        relays.forEach((engine, offsets) -> tag.put(Integer.toString(engine), Relays.write(offsets)));
        return tag;
    }

    /** Loads what was saved, turned as its block has turned since; anything missing starts afresh. */
    void load(CompoundTag tag, Rotation turn) {
        List<Cell> loaded = new ArrayList<>();
        for (Tag entry : tag.getList("Cells", Tag.TAG_COMPOUND)) {
            CompoundTag saved = (CompoundTag) entry;
            List<BlockPos> relays = Relays.read(saved.getList("Relays", Tag.TAG_INT_ARRAY));
            // Saved before a cell's burners could be turned down: each at the most a burner gives.
            double full = saved.contains("FullGas") ? saved.getDouble("FullGas") : BURNER_GAS * relays.size();
            loaded.add(new Cell(Turning.turn(relays, turn), saved.getDouble("Ahead"), full));
        }
        configure(loaded, engines(tag.getCompound("AheadRelays"), turn), engines(tag.getCompound("AsternRelays"), turn), tag.getDouble("GasAbove"),
            tag.getDouble("MiddleAft"));
        liftTrim = tag.getDouble("LiftTrim");
        pitchTrim = tag.getDouble("PitchTrim");
        thrustTrim = Mth.clamp(tag.getDouble("ThrustPerSpeed"), -MOST_THRUST_TRIM, MOST_THRUST_TRIM);
        controls.load(tag.getCompound("Controls"), turn);
        primed = false;
        cellSignals.clear();
        engineSignals.clear();
    }

    private static Map<Integer, List<BlockPos>> engines(CompoundTag tag, Rotation turn) {
        Map<Integer, List<BlockPos>> relays = new HashMap<>();
        for (int engine = 0; engine < ENGINES; engine++) {
            String key = Integer.toString(engine);
            if (tag.contains(key)) {
                relays.put(engine, Turning.turn(Relays.read(tag.getList(key, Tag.TAG_INT_ARRAY)), turn));
            }
        }
        return relays;
    }
}
