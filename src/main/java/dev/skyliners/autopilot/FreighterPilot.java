package dev.skyliners.autopilot;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.airframe.Thrusters;
import dev.skyliners.block.Relays;
import dev.skyliners.block.Turning;
import dev.skyliners.compat.SableFlight;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * What flies the freighter for her autopilot (see {@link AutopilotBlockEntity}): four clusters of lift thrusters under
 * her belly hold her up and keep her deck level, six main engines in her stern push her ahead and two retros in her
 * prongs' tips hold her back, and a pair of jets at her nose and another at her tail turn her and push her sideways.
 * She never tilts to go anywhere: her deck stays level under whoever stands on it, and she moves on her mains and her
 * jets alone.
 *
 * <p>She lifts straight off her legs once her thrusters have spooled up, rises clear of anything round her, then climbs
 * to the height set, going the way she is set to: on a heading, round a circle, or to a pin, where she stops turning a
 * little short of it and slides sideways onto it, then hovers there or, set to land at it, comes straight down. Each
 * tick she works out the climb she wants and the lift that gives it, from the weight she carries (Sable weighs it) and
 * a trim she learns for how hard her thrusters push; the lift is spread over her clusters, more on the side that dips,
 * as a quadcopter levels itself, with a trim it learns for where her weight sits; and the speed ahead and across she
 * wants, and the heading, set her mains, retros and jets, each against what she does now.
 */
public final class FreighterPilot {
    /** How strong a signal drives a thruster at most. */
    private static final int FULL = 15;
    /**
     * How far each kind of her thrusters is turned up, as a share of the configured setting (see {@link Thrusters}):
     * her lift thrusters so she hovers near two fifths of their full push furnished, a little less bare; her mains for
     * a brisk shove ahead, her retros to brake as hard, and her jets, given at most {@link #JET_MOST_SIGNAL}, for a
     * gentle turn.
     */
    public static final double LIFT_SETTING = 0.35;
    public static final double MAIN_SETTING = 0.25;
    public static final double RETRO_SETTING = 0.35;
    public static final double JET_SETTING = 0.75;
    /**
     * The strongest signal her jets are given, under a third of full: Create Propulsion sets fire to whatever stands in
     * the exhaust of a thruster run at a third of full or more, and her jets fire inside her, where anyone may walk.
     * Never 5 or more.
     */
    private static final int JET_MOST_SIGNAL = 4;
    /** Her jets' two pairs, her nose's and her tail's, in the order she holds and reports them. */
    private static final int NOSE_PAIR = 0;
    private static final int TAIL_PAIR = 1;
    /**
     * Lifting off: how long her thrusters spool up on the ground, in ticks, and the share of the lift that would hold
     * her up they give meanwhile, so she rises the moment they are given the rest; how high she rises straight up
     * before going anywhere, in blocks, and how fast, in blocks a second.
     */
    private static final int SPOOL_TICKS = 60;
    private static final double SPOOL_SHARE = 0.9;
    private static final double CLEAR = 12;
    private static final double LIFT_OFF_CLIMB = 3;
    /**
     * How fast she climbs and sinks at most, and how fast she closes on her height, a block a second a block off it.
     */
    private static final double MOST_CLIMB = 5;
    private static final double MOST_SINK = 3;
    private static final double HOLD_GAIN = 0.5;
    /**
     * How fast the climb she wants may change, in blocks a second each second, so nobody aboard feels a jolt; she also
     * eases off as she nears her height by as much, so she comes to it without overshooting.
     */
    private static final double CLIMB_EASE = 1;
    /**
     * Her climb boost (see {@link FreighterClimbBoost}): how fast she climbs at most as it fires, in blocks a second,
     * and how fast that climb may change, in blocks a second each second, both speeding up and slowing to stop at the
     * height it climbs to, no harder than her lift can slow her with its least (see {@link #LEAST_LIFT}); she slows as
     * fast from any climb faster than she ever climbs without it.
     */
    private static final double CLIMB_BOOST_SPEED = 30;
    private static final double CLIMB_BOOST_EASE = 4;
    /** How quickly her lift closes on the climb she wants, in blocks a second each second a block a second off it. */
    private static final double CLIMB_GAIN = 1.2;
    /** The least share of her weight her lift holds up in flight: she never falls faster than the rest of gravity. */
    private static final double LEAST_LIFT = 0.6;
    /**
     * The least of her deck's up counted toward holding her weight, leaning: however far she leans, she asks no more
     * than twice the lift that holds her up level.
     */
    private static final double LEAST_UPRIGHT = 0.5;
    /** The least signal a lift thruster is held at in flight, so none has to spool up again from nothing. */
    private static final int IDLE_SIGNAL = 1;
    /**
     * How fast her trim for her lift's push is learnt, a share of what is still off each second, how far off the climb
     * she wants she may be while she learns, in blocks a second, and how far the trim may go either way.
     */
    private static final double TRIM_RATE = 0.05;
    private static final double LEARNING_ERROR = 2;
    private static final double MOST_TRIM = 2;
    /**
     * Holding her deck level: how much of her lift moves to the side that dips for each degree it dips and for each
     * degree a second it dips faster, as a share, and how much at most; and how fast her trim for what is left after
     * her weight's own balance (see {@link #balance}) is learnt, a share of her lift each second for each degree she
     * still dips, and how far it may go.
     */
    private static final double LEVEL_GAIN = 0.03;
    private static final double LEVEL_DAMPING = 0.015;
    private static final double MOST_SPLIT = 0.6;
    private static final double LEVEL_TRIM_RATE = 0.005;
    private static final double MOST_LEVEL_TRIM = 0.2;
    /**
     * Going ahead and across: how hard she closes on the speed she wants, in blocks a second each second a block a
     * second off it, and how hard at most; how fast the drag she pushes against is learnt, a share of what is still off
     * each second, and how much it may come to.
     */
    private static final double SPEED_GAIN = 0.6;
    private static final double MOST_ACCELERATION = 1.5;
    private static final double DRAG_RATE = 0.05;
    private static final double MOST_DRAG = 0.05;
    /**
     * The drag she starts with, as a share of her mains' full push for each block a second ahead, before she has
     * learnt her own: what her mains were seen to push against, holding her at 20 blocks a second on about a fifth of
     * their push; and the least she may learn, half of it, so she never forgets the air entirely and creeps up to
     * speed.
     */
    private static final double FIRST_DRAG = 0.01;
    private static final double LEAST_DRAG = 0.005;
    /**
     * Her drag is learnt only cruising, going at least this fast ahead, within this of the speed she wants, in blocks
     * a second, and with less than her mains' or retros' full push asked of them: steady, so what she learns is drag,
     * not speeding up, nor drifting back onto a spot; or going faster than she wants by more than that, when she has
     * learnt too much drag and her mains push her on past the speed set, which she unlearns the faster the further
     * past it she is.
     */
    private static final double DRAG_LEARNING_SPEED = 2;
    private static final double DRAG_LEARNING_ERROR = 1;
    /**
     * Turning: how fast she turns for each degree off the heading she wants, in degrees a second, and how fast at most;
     * how hard her jets push for each degree a second off the turn she wants, as a share of full, and how fast the trim
     * for what turns her unasked is learnt.
     */
    private static final double YAW_GAIN = 0.8;
    private static final double MOST_YAW_RATE = 12;
    private static final double YAW_RATE_GAIN = 0.05;
    private static final double YAW_TRIM_RATE = 0.02;
    private static final double MOST_YAW_TRIM = 0.3;
    /** How near the heading she wants she must be for her turning trim to be learnt, in degrees. */
    private static final double YAW_LEARNING = 2;
    /**
     * Holding a spot: how fast she moves toward it for each block off it, in blocks a second, and how fast at most, so
     * she settles onto it rather than overshooting.
     */
    private static final double POSITION_GAIN = 0.4;
    private static final double MOST_SETTLING = 4;
    /**
     * Flying to a pin: how far short of it she stops turning and slides the rest of the way, how near it counts as over
     * it and how slowly she drifts there at most, in blocks and blocks a second, for how long before she is there, in
     * ticks; and how hard she brakes for it, in blocks a second each second.
     */
    private static final double SLIDE = 30;
    /** How near the height set counts as at it, climbing, in blocks: she cruises from there. */
    private static final double AT_HEIGHT = 2;
    private static final double OVER = 3;
    private static final double STILL = 0.5;
    private static final int OVER_TICKS = 40;
    private static final double BRAKING = 1.0;
    /**
     * Coming down: how high over the ground she sinks fast to, in blocks, before she comes the rest of the way at her
     * landing speed, in blocks a second; how near the ground counts as touching, in blocks; and how long her lift runs
     * down once she touches and how long she rests still before switching off, in ticks.
     */
    private static final double PAD_HOVER = 10;
    private static final double LANDING_SINK = 1;
    private static final double TOUCHING = 0.6;
    private static final int RUN_DOWN_TICKS = 20;
    private static final int SETTLED_TICKS = 20;
    /**
     * How much slower than the speed set, in blocks a second, she may be asked to go before a boost stops firing her
     * mains flat out: she slows for a pin as she nears it, and boosting on she would race past it.
     */
    private static final double BOOST_SLOWER = 0.5;
    /**
     * How far the signal asked of a set of thrusters together must move from the one they were set to before they are
     * set anew, in steps: rounded afresh each tick, a hover wobbling between two steps would set a thruster back and
     * forth several times a second, and each change is sent to every client. Held wider, or paced to a change every few
     * ticks, she hunts round her height and level, and her thrusters change more often, not less.
     */
    private static final double SIGNAL_HOLD = 0.75;
    /**
     * How much of each new reading of how fast she tilts, turns and moves is taken each tick, the rest kept from
     * before: read from one tick to the next, they jitter, and her thrusters would follow every jitter, each change
     * sent to every client.
     */
    private static final double SMOOTHING = 0.3;
    /** How short a way flattened onto the ground may be before it counts as none: one pointing straight up or down. */
    private static final double NO_WAY = 1e-9;

    /**
     * Her four lift clusters, in this order, as which way each is ahead and to her right, 1 or -1: ahead on her right,
     * ahead on her left, astern on her right, astern on her left. Her layout lays them in this order.
     */
    public static final int[][] CLUSTER_SIDES = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    /**
     * Her jets' relays, by what each pushes which way, seen from her control head facing her bow: her nose to her right
     * and to her left, her tail to her right and to her left.
     */
    public record Jets(@Nullable BlockPos noseRight, @Nullable BlockPos noseLeft, @Nullable BlockPos tailRight,
                       @Nullable BlockPos tailLeft) {
        static final Jets NONE = new Jets(null, null, null, null);

        List<BlockPos> all() {
            List<BlockPos> all = new ArrayList<>();
            for (BlockPos jet : new BlockPos[]{noseRight, noseLeft, tailRight, tailLeft}) {
                if (jet != null) {
                    all.add(jet);
                }
            }
            return all;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            put(tag, "NoseRight", noseRight);
            put(tag, "NoseLeft", noseLeft);
            put(tag, "TailRight", tailRight);
            put(tag, "TailLeft", tailLeft);
            return tag;
        }

        Jets turned(Rotation turn) {
            return new Jets(Turning.turn(noseRight, turn), Turning.turn(noseLeft, turn), Turning.turn(tailRight, turn),
                Turning.turn(tailLeft, turn));
        }

        static Jets load(CompoundTag tag, Rotation turn) {
            return new Jets(get(tag, "NoseRight", turn), get(tag, "NoseLeft", turn), get(tag, "TailRight", turn),
                get(tag, "TailLeft", turn));
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
     * Her thrusters as she is built, as offsets from her control head: her lift clusters' relays in the order of
     * {@link #CLUSTER_SIDES}, her mains' and her retros', her jets, the blocks her legs' feet stand on, and her engine
     * grille's blocks (see {@link EngineGlow}); where her disc's middle is from her head, so many blocks to her right
     * and ahead; and how far ahead or astern and to either side of it her lift clusters' middles stand, in blocks.
     */
    public record Wiring(List<List<BlockPos>> clusters, List<BlockPos> mains, List<BlockPos> retros, Jets jets,
                         List<BlockPos> feet, List<BlockPos> grille, double middleRight, double middleAhead, double clustersOut) {
        static final Wiring NONE = new Wiring(List.of(), List.of(), List.of(), Jets.NONE, List.of(), List.of(), 0, 0, 0);
    }

    /**
     * What the autopilot is set to fly: its phase, the height and speed set, the heading, and how it goes, on the
     * heading, round a circle about a middle so wide, or to a pin there, landing at it or not; whether her legs are
     * down to land on (see {@link FreighterLegs}); where her boost stands; and whether her climb boost fires, when she
     * climbs straight up as fast as she can to the height set, holding the spot it began over.
     */
    record Orders(AutopilotBlockEntity.Phase phase, double altitude, double speed, double heading,
                  AutopilotBlockEntity.Lateral lateral, double pinX, double pinZ, double radius, boolean landAtPin,
                  boolean legsDown, Boost boost, boolean climbBoost) {
    }

    /**
     * Her boost as her pilot flies it (see {@link FreighterFlight#boost}): none; firing, her mains flat out and her
     * retros quiet while she goes no slower than she is set to, so she never races at a pin she is braking for; or
     * settling after it as she slows back, when she learns nothing of her drag from it.
     */
    enum Boost {
        NONE, FIRING, SETTLING
    }

    /**
     * What she is doing, for play-testing: the share of full lift she was last set to and her learnt trims, her tilt
     * across her front and to her right, in degrees, each lift cluster's signal, her mains' and retros', each jet's
     * (nose right, nose left, tail right, tail left), how fast her middle climbs and goes ahead and across, in blocks a
     * second, the climb, speed ahead and across and heading she last wanted, the spot she found near a pin to come down
     * on or came down on last, its middle's column, null while she has found none, and where her disc's middle was last
     * in the world.
     */
    public record Report(double lift, double trim, double pitchTrim, double rollTrim, double drag, double tiltFront,
                         double tiltRight, List<Integer> clusters, int mains, int retros, List<Integer> jets, double climb,
                         double ahead, double across, double climbWanted, double aheadWanted, double acrossWanted,
                         double headingWanted, @Nullable PadSearch.Pad pad, Vec3 middle) {
    }

    /**
     * Her tilt across her front and to her right, in degrees: how far her deck's up leans that way, its front or its
     * right dipping.
     */
    private record Tilt(double front, double right) {
        static final Tilt LEVEL = new Tilt(0, 0);

        static Tilt of(SableFlight.State state) {
            Vec3 up = state.up();
            double front = Math.toDegrees(Math.asin(Mth.clamp(up.dot(flat(state.nose())), -1, 1)));
            double right = Math.toDegrees(Math.asin(Mth.clamp(up.dot(flat(state.right())), -1, 1)));
            return new Tilt(front, right);
        }
    }

    /**
     * How she moves this tick: her tilt, how fast it changes across her front and to her right, and how fast she turns,
     * in degrees a second.
     */
    private record Motion(Tilt tilt, double frontRate, double rightRate, double yawRate) {
        static final Motion STILL = new Motion(Tilt.LEVEL, 0, 0, 0);
    }

    /** How her lift is split for her weight to be held up where it sits, as shares moved ahead and to her right. */
    private record Balance(double ahead, double right) {
        static final Balance EVEN = new Balance(0, 0);
    }

    /**
     * What she learns as she flies and keeps, saved with her: the trim for her lift's push, how much more lift than she
     * works out it takes to climb as she wants; the trims for what her weight's own balance leaves her dipping by, as
     * shares of her lift moved ahead and to her right; the drag her mains push against for each block a second ahead,
     * as a share of their full push; and the trim for what turns her unasked, as a share of a jet's full push.
     */
    private static final class Learnt {
        double lift = 1;
        double pitch;
        double roll;
        double drag = FIRST_DRAG;
        double yaw;

        void save(CompoundTag tag) {
            tag.putDouble("Trim", lift);
            tag.putDouble("PitchTrim", pitch);
            tag.putDouble("RollTrim", roll);
            tag.putDouble("Drag", drag);
            tag.putDouble("YawTrim", yaw);
        }

        void load(CompoundTag tag) {
            lift = tag.contains("Trim") ? Mth.clamp(tag.getDouble("Trim"), 1 / MOST_TRIM, MOST_TRIM) : 1;
            pitch = Mth.clamp(tag.getDouble("PitchTrim"), -MOST_LEVEL_TRIM, MOST_LEVEL_TRIM);
            roll = Mth.clamp(tag.getDouble("RollTrim"), -MOST_LEVEL_TRIM, MOST_LEVEL_TRIM);
            drag = tag.contains("Drag") ? Mth.clamp(tag.getDouble("Drag"), LEAST_DRAG, MOST_DRAG) : FIRST_DRAG;
            yaw = Mth.clamp(tag.getDouble("YawTrim"), -MOST_YAW_TRIM, MOST_YAW_TRIM);
        }
    }

    private Wiring wiring = Wiring.NONE;
    /** Her looking for clear, level ground near a pin she is set to land at (see {@link PadSearch}). */
    private final PadSearch pads = new PadSearch();
    /** How many lift thrusters she has, all her clusters' together. */
    private int liftThrusters;
    private final Learnt learnt = new Learnt();
    /** Whether she has started from how she is since she was last loaded or engaged (see {@link #begin}). */
    private boolean primed;
    /** The spot she holds over lifting off and coming down; and the heading she holds there. */
    @Nullable
    private Vec3 spot;
    private double spotHeading;
    /**
     * Ticks her thrusters have spooled up lifting off; ticks she has hovered over a pin; ticks touching; ticks settled.
     */
    private int spooled;
    private int overFor;
    private int touchingFor;
    private int settled;
    /** Her tilt and heading the tick before, to tell how fast each changes. */
    private Tilt lastTilt = Tilt.LEVEL;
    private double lastHeading;
    /** How fast she tilts, turns and moves, smoothed (see {@link #SMOOTHING}); none to begin with. */
    private Motion smoothed = Motion.STILL;
    @Nullable
    private Vec3 smoothedGoing;
    /** Her jets' pairs' signed signals as last set, her nose's and her tail's, positive pushing to her right. */
    private final int[] jetsHeld = new int[2];
    /** The climb she wanted last tick, eased toward the one she wants now. */
    private double easedClimb;
    /** Whether her climb boost fired last tick, so the spot she holds is taken as it begins. */
    private boolean climbBoosting;
    /** The signals each set of her thrusters was last set to together: her clusters, her mains and her retros. */
    private final int[] clusterTotals = new int[4];
    private int mainsTotal;
    private int retrosTotal;
    private final int[] jetSignals = new int[4];
    /** What she last did and wanted, for her report: the spot she last found near a pin among them. */
    @Nullable
    private PadSearch.Pad lastPad;
    private Vec3 lastMiddle = Vec3.ZERO;
    private double lastShare;
    private double lastClimb;
    private double lastGoingAhead;
    private double lastGoingAcross;
    private double lastAhead;
    private double lastAcross;
    private double lastHeadingWanted;

    void configure(Wiring wiring) {
        this.wiring = wiring;
        liftThrusters = wiring.clusters().stream().mapToInt(List::size).sum();
    }

    boolean wired() {
        return liftThrusters > 0;
    }

    /** What she is wired to. */
    Wiring wiring() {
        return wiring;
    }

    /** How hard her mains were last set to push together, as a share of their full push; 0 with none. */
    double mainsShare() {
        return wiring.mains().isEmpty() ? 0 : (double) mainsTotal / (FULL * wiring.mains().size());
    }

    /** Every relay she works, for mending her links (see {@link Turning#mend}). */
    List<BlockPos> links() {
        List<BlockPos> links = new ArrayList<>();
        wiring.clusters().forEach(links::addAll);
        links.addAll(wiring.mains());
        links.addAll(wiring.retros());
        links.addAll(wiring.jets().all());
        return links;
    }

    /** Her links turned with her, once they were found to lead nowhere as they were (see {@link Turning}). */
    void turn(Rotation turn) {
        configure(new Wiring(wiring.clusters().stream().map(cluster -> Turning.turn(cluster, turn)).toList(),
            Turning.turn(wiring.mains(), turn), Turning.turn(wiring.retros(), turn), wiring.jets().turned(turn),
            Turning.turn(wiring.feet(), turn), Turning.turn(wiring.grille(), turn), wiring.middleRight(), wiring.middleAhead(),
            wiring.clustersOut()));
    }

    /** How high the lowest of her legs' feet stands over the ground, in blocks; as high as can be with none wired. */
    double feetOverGround(Level level, BlockPos origin) {
        return Legs.lowest(level, origin, wiring.feet());
    }

    /** Begins afresh from how she is now, keeping what she has learnt: she holds the spot she is over. */
    void begin(SableFlight.State state) {
        primed = true;
        spot = middle(state);
        spotHeading = state.heading();
        startCounting();
        lastTilt = Tilt.of(state);
        lastHeading = state.heading();
        pads.reset();
        lastPad = null;
        smoothed = Motion.STILL;
        smoothedGoing = null;
        easedClimb = state.velocity().y;
    }

    /** Every count she keeps of how long something has gone on, from nothing. */
    private void startCounting() {
        spooled = 0;
        overFor = 0;
        touchingFor = 0;
        settled = 0;
    }

    /** Told to land, or switched off in the air: she comes straight down where she is now. */
    void landHere(SableFlight.State state) {
        spot = middle(state);
        spotHeading = state.heading();
        touchingFor = 0;
        settled = 0;
    }

    /**
     * Flies one tick: the phase she flies in next. Lifting off, she spools up on her legs first, then rises straight
     * up; climbing and cruising, she goes where she is set to; coming down, she holds her spot as she sinks onto it,
     * and once she has touched and stood still a moment she is switched off.
     */
    AutopilotBlockEntity.Phase fly(ServerLevel level, BlockPos origin, Orders orders, SableFlight.State state, double radio,
                                   double mass, @Nullable Vec3 centreOfMass) {
        if (!primed) {
            begin(state);
        }
        AutopilotBlockEntity.Phase phase = orders.phase();
        Motion motion = motion(state);
        Vec3 middle = middle(state);
        lastMiddle = middle;
        Vec3 going = smoothedGoing(going(state, middle, motion));
        lastClimb = going.y;
        Balance balance = balance(state, middle, centreOfMass);
        if (orders.climbBoost() && !climbBoosting) {
            spot = middle;
            spotHeading = state.heading();
        }
        climbBoosting = orders.climbBoost();
        if (phase == AutopilotBlockEntity.Phase.TAKEOFF && spooled < SPOOL_TICKS) {
            spooled++;
            lift(level, origin, SPOOL_SHARE * hoverShare(state, mass) * spooled / SPOOL_TICKS, balance.ahead(), balance.right());
            return phase;
        }
        touchingFor = phase == AutopilotBlockEntity.Phase.LAND && radio < TOUCHING ? touchingFor + 1 : 0;
        if (touchingFor > 0) {
            runDown(level, origin, state, going, mass, balance);
        } else {
            lookForPad(level, phase, orders, state, middle);
            Course course = course(phase, orders, state, middle);
            double climb = easedClimb(climbWanted(phase, orders, state, radio), orders.climbBoost());
            boolean learning = phase == AutopilotBlockEntity.Phase.CRUISE && orders.boost() == Boost.NONE && !orders.climbBoost();
            boolean boosting = orders.boost() == Boost.FIRING && course.ahead() >= orders.speed() - BOOST_SLOWER;
            level(motion.tilt());
            lift(level, origin, liftShare(state, going, mass, climb, learning),
                split(motion.tilt().front(), motion.frontRate(), balance.ahead() + learnt.pitch),
                split(motion.tilt().right(), motion.rightRate(), balance.right() + learnt.roll));
            move(level, origin, state, going, mass, learning, course.ahead(), course.across(),
                turn(course.heading(), state.heading(), motion.yawRate(), learning), boosting);
        }
        settled = touchingFor > RUN_DOWN_TICKS && Math.abs(going.y) < STILL ? settled + 1 : 0;
        if (settled >= SETTLED_TICKS) {
            return AutopilotBlockEntity.Phase.OFF;
        }
        return next(phase, orders, state, going, middle, radio);
    }

    /** How she moves this tick, from how she stood the tick before, which it notes for the next. */
    private Motion motion(SableFlight.State state) {
        Tilt tilt = Tilt.of(state);
        double frontRate = (tilt.front() - lastTilt.front()) * SharedConstants.TICKS_PER_SECOND;
        double rightRate = (tilt.right() - lastTilt.right()) * SharedConstants.TICKS_PER_SECOND;
        double yawRate = Mth.wrapDegrees(state.heading() - lastHeading) * SharedConstants.TICKS_PER_SECOND;
        lastTilt = tilt;
        lastHeading = state.heading();
        smoothed = new Motion(tilt, Mth.lerp(SMOOTHING, smoothed.frontRate(), frontRate), Mth.lerp(SMOOTHING, smoothed.rightRate(), rightRate),
            Mth.lerp(SMOOTHING, smoothed.yawRate(), yawRate));
        return smoothed;
    }

    /** How fast her middle moves, smoothed as her rates are (see {@link #SMOOTHING}). */
    private Vec3 smoothedGoing(Vec3 going) {
        smoothedGoing = smoothedGoing == null ? going : smoothedGoing.lerp(going, SMOOTHING);
        return smoothedGoing;
    }

    /**
     * How fast her disc's middle moves, in blocks a second. Sable gives how fast her control head moves, out in her
     * cockpit's pod some thirty blocks from her middle, which swings round it as she turns and rises and falls as she
     * tilts: that taken away, or she would fight her own turns with her jets. Her heading falls as she turns the
     * right-handed way about the world's up.
     */
    private static Vec3 going(SableFlight.State state, Vec3 middle, Motion motion) {
        Vec3 out = state.position().subtract(middle);
        double turning = -Math.toRadians(motion.yawRate());
        double ahead = out.dot(flat(state.nose()));
        double right = out.dot(flat(state.right()));
        double dipping = ahead * Math.toRadians(motion.frontRate()) + right * Math.toRadians(motion.rightRate());
        return state.velocity().subtract(turning * out.z, -dipping, -turning * out.x);
    }

    /**
     * Down on her legs: her lift runs down evenly, never fighting the ground to stay level, so she does not skate
     * across it on her thrusters, and nothing pushes her anywhere.
     */
    private void runDown(ServerLevel level, BlockPos origin, SableFlight.State state, Vec3 going, double mass, Balance balance) {
        double runDown = Math.max(0, 1 - (double) touchingFor / RUN_DOWN_TICKS);
        lift(level, origin, runDown * hoverShare(state, mass), balance.ahead(), balance.right());
        move(level, origin, state, going, mass, false, 0, 0, 0, false);
    }

    /**
     * Where she goes this tick: the speed she wants ahead and across, in blocks a second, and the heading she wants.
     */
    private record Course(double ahead, double across, double heading) {
    }

    /**
     * Where she goes this tick, as her phase and her orders have it: lifting off and coming down, she holds her spot on
     * the heading she had there, as she does while her climb boost fires; otherwise she goes the way she is set to, to
     * a pin turning toward it until she is near, then sliding onto it on the heading she has.
     */
    private Course course(AutopilotBlockEntity.Phase phase, Orders orders, SableFlight.State state, Vec3 middle) {
        boolean holding = phase == AutopilotBlockEntity.Phase.TAKEOFF || phase == AutopilotBlockEntity.Phase.DESCENT
            || phase == AutopilotBlockEntity.Phase.LAND || orders.climbBoost();
        if (holding && spot != null) {
            return toward(state, middle, spot, spotHeading, MOST_SETTLING);
        }
        return switch (orders.lateral()) {
            case HEADING -> new Course(orders.speed(), 0, orders.heading());
            case ORBIT_LEFT, ORBIT_RIGHT -> new Course(orders.speed(), 0,
                Steering.round(orders.lateral() == AutopilotBlockEntity.Lateral.ORBIT_RIGHT, orders.pinX(), orders.pinZ(), orders.radius(), middle));
            case DESTINATION -> toPin(orders, state, middle);
        };
    }

    /**
     * To a pin: far off, nose toward it at the speed set, slowing as she nears it so she reaches {@link #SLIDE} from it
     * no faster than she settles onto a spot ({@link #MOST_SETTLING}), and only as fast as she points at it, so that
     * pointing well away she turns almost where she is rather than circle it wider than she slides; from there no more
     * turning, sliding onto it on the heading she has, or on the one the spot she found was looked at again with; and,
     * once a spot looked at again has failed, sliding to the next on that heading from wherever she is.
     */
    private Course toPin(Orders orders, SableFlight.State state, Vec3 middle) {
        Vec3 pin = target(orders, middle.y);
        double distance = Math.hypot(pin.x - middle.x, pin.z - middle.z);
        boolean fixed = orders.landAtPin() && (pads.verified() || pads.headingFixed());
        if (distance < SLIDE || fixed) {
            double holding = fixed ? pads.heading() : state.heading();
            return toward(state, middle, pin, holding, Math.min(orders.speed(), Math.sqrt(2 * BRAKING * distance)));
        }
        double bearing = Steering.bearingTo(middle, pin.x, pin.z);
        double pointing = Math.max(0, Math.cos(Math.toRadians(Mth.wrapDegrees(bearing - state.heading()))));
        double speed = Math.min(orders.speed(), Math.sqrt(2 * BRAKING * (distance - SLIDE)) + MOST_SETTLING);
        return new Course(speed * pointing, 0, bearing);
    }

    /**
     * Toward a spot: the speed ahead and across that closes on it, eased in as she nears it and never more than so
     * fast, holding the heading given.
     */
    private static Course toward(SableFlight.State state, Vec3 middle, Vec3 spot, double heading, double fastest) {
        Vec3 off = new Vec3(spot.x - middle.x, 0, spot.z - middle.z);
        Vec3 wanted = off.scale(POSITION_GAIN);
        double speed = wanted.length();
        if (speed > fastest) {
            wanted = wanted.scale(fastest / speed);
        }
        Vec3 front = flat(state.nose());
        Vec3 right = flat(state.right());
        return new Course(wanted.dot(front), wanted.dot(right), heading);
    }

    /**
     * The climb she wants, in blocks a second: lifting off, straight up slowly; climbing and cruising, toward the
     * height set; coming down, sinking fast to {@link #PAD_HOVER} over the ground, then at her landing speed, but with
     * her legs not yet down only to that height, where she hovers until they are, and no lower. Nearing a height she
     * eases off soon enough to stop at it as gently as {@link #CLIMB_EASE} lets her; with her climb boost firing, she
     * climbs up to {@link #CLIMB_BOOST_SPEED}, easing off as {@link #CLIMB_BOOST_EASE} lets her.
     */
    private static double climbWanted(AutopilotBlockEntity.Phase phase, Orders orders, SableFlight.State state, double radio) {
        return switch (phase) {
            case TAKEOFF -> LIFT_OFF_CLIMB;
            case DESCENT -> -Math.max(orders.legsDown() ? LANDING_SINK : 0, approach(radio - PAD_HOVER, MOST_SINK, CLIMB_EASE));
            case LAND -> orders.legsDown() ? -LANDING_SINK : 0;
            default -> {
                double toGo = orders.altitude() - state.position().y;
                if (orders.climbBoost() && toGo > 0) {
                    yield approach(toGo, CLIMB_BOOST_SPEED, CLIMB_BOOST_EASE);
                }
                yield Math.signum(toGo) * approach(Math.abs(toGo), toGo > 0 ? MOST_CLIMB : MOST_SINK, CLIMB_EASE);
            }
        };
    }

    /**
     * How fast to close on a height so far off, in blocks a second: no faster than she can ease off to stop at it,
     * planning on half the ease given, in blocks a second each second, since her lift takes a moment to follow the
     * climb she wants.
     */
    private static double approach(double away, double fastest, double ease) {
        double off = Math.max(0, away);
        return Math.min(fastest, Math.min(HOLD_GAIN * off, Math.sqrt(ease * off)));
    }

    /**
     * The climb she wants this tick, eased from the one she wanted last by at most {@link #CLIMB_EASE}, or by
     * {@link #CLIMB_BOOST_EASE} while her climb boost fires or she still climbs faster than she does without it.
     */
    private double easedClimb(double wanted, boolean climbBoost) {
        boolean hard = climbBoost || Math.abs(easedClimb) > MOST_CLIMB;
        double step = (hard ? CLIMB_BOOST_EASE : CLIMB_EASE) / SharedConstants.TICKS_PER_SECOND;
        easedClimb = Mth.clamp(wanted, easedClimb - step, easedClimb + step);
        return easedClimb;
    }

    /**
     * The phase she goes on in: up off the ground once clear of it, climbing until near the height set, and cruising;
     * over a pin and still a while, coming down if set to land there; down to her hovering height over the ground with
     * her legs down, landing the rest of the way.
     */
    private AutopilotBlockEntity.Phase next(AutopilotBlockEntity.Phase phase, Orders orders, SableFlight.State state, Vec3 going,
                                            Vec3 middle, double radio) {
        return switch (phase) {
            case TAKEOFF -> radio > CLEAR ? AutopilotBlockEntity.Phase.CLIMB : phase;
            case CLIMB -> Math.abs(orders.altitude() - state.position().y) < AT_HEIGHT ? AutopilotBlockEntity.Phase.CRUISE : phase;
            case CRUISE -> arrived(orders, going, middle) && orders.landAtPin() ? comeDownAtPin(orders) : phase;
            case DESCENT -> radio <= PAD_HOVER + 1 && orders.legsDown() ? AutopilotBlockEntity.Phase.LAND : phase;
            default -> phase;
        };
    }

    /** Over the pin: she holds it as her spot and comes down on it. */
    private AutopilotBlockEntity.Phase comeDownAtPin(Orders orders) {
        spot = target(orders, 0);
        spotHeading = pads.verified() ? pads.heading() : lastHeading;
        return AutopilotBlockEntity.Phase.DESCENT;
    }

    /**
     * Whether, flying to a pin, she has been over where she is going a while, hardly drifting, and, set to land, has a
     * spot to come down on, looked at again pointing as she comes down (see {@link PadSearch}).
     */
    private boolean arrived(Orders orders, Vec3 going, Vec3 middle) {
        Vec3 target = target(orders, middle.y);
        boolean known = !orders.landAtPin() || pads.verified();
        boolean over = orders.lateral() == AutopilotBlockEntity.Lateral.DESTINATION && known
            && Math.hypot(target.x - middle.x, target.z - middle.z) < OVER && Math.hypot(going.x, going.z) < STILL;
        overFor = over ? overFor + 1 : 0;
        return overFor >= OVER_TICKS;
    }

    /**
     * Cruising to a pin she is set to land at, the ground round it read and spots tried for her to come down on (see
     * {@link PadSearch}); once she slides onto the spot found, no longer turning (see {@link #SLIDE}), it is looked at
     * again pointing as she points.
     */
    private void lookForPad(ServerLevel level, AutopilotBlockEntity.Phase phase, Orders orders, SableFlight.State state, Vec3 middle) {
        if (phase != AutopilotBlockEntity.Phase.CRUISE || !landingAtPin(orders)) {
            return;
        }
        pads.tick(level, middle, Mth.floor(orders.pinX()), Mth.floor(orders.pinZ()), state.heading());
        PadSearch.Pad pad = pads.pad();
        if (pad != null && Math.hypot(pad.x() + 0.5 - middle.x, pad.z() + 0.5 - middle.z) < SLIDE) {
            pads.verify(state.heading());
        }
        lastPad = pads.pad() == null ? lastPad : pads.pad();
    }

    /** Whether she is set to land at a pin. */
    private static boolean landingAtPin(Orders orders) {
        return orders.landAtPin() && orders.lateral() == AutopilotBlockEntity.Lateral.DESTINATION;
    }

    /**
     * Whether, set to land at a pin, she has looked at every spot round it with all their ground read and found none
     * she fits: she holds over the pin.
     */
    boolean noGroundAtPin(Orders orders) {
        return landingAtPin(orders) && pads.exhausted();
    }

    /**
     * Where she flies to, at a height: the middle of the spot she found to come down on, set to land at a pin and
     * having found one, or else the pin itself.
     */
    private Vec3 target(Orders orders, double y) {
        PadSearch.Pad pad = orders.landAtPin() ? pads.pad() : null;
        return pad == null ? new Vec3(orders.pinX(), y, orders.pinZ()) : new Vec3(pad.x() + 0.5, y, pad.z() + 0.5);
    }

    /**
     * The share of her full lift she wants: what holds up her weight, tilted, and closes on the climb she wants, by her
     * trim, never so little she falls faster than she may. Her trim is learnt only cruising, near the climb she wants.
     */
    private double liftShare(SableFlight.State state, Vec3 going, double mass, double climb, boolean learning) {
        double error = climb - going.y;
        double upright = Math.max(LEAST_UPRIGHT, state.up().y);
        double wanted = mass * (state.gravity() + CLIMB_GAIN * error) / upright;
        double least = mass * state.gravity() * LEAST_LIFT / upright;
        double push = Math.max(least, wanted) * learnt.lift;
        double most = mostLift();
        if (learning && wanted > least && push < most && Math.abs(error) < LEARNING_ERROR) {
            double step = TRIM_RATE / SharedConstants.TICKS_PER_SECOND * error / (Math.abs(climb) + 1);
            learnt.lift = Mth.clamp(learnt.lift + step, 1 / MOST_TRIM, MOST_TRIM);
        }
        return push / Math.max(1, most);
    }

    /** The share of her full lift that would just hold her weight up, by her trim. */
    private double hoverShare(SableFlight.State state, double mass) {
        return mass * state.gravity() * learnt.lift / Math.max(1, mostLift());
    }

    /**
     * Learns what her weight's balance leaves her dipping by, all through her flight: the side that dips is given a
     * little more lift each tick it still dips, until she holds level with none of it.
     */
    private void level(Tilt tilt) {
        double rate = LEVEL_TRIM_RATE / SharedConstants.TICKS_PER_SECOND;
        learnt.pitch = Mth.clamp(learnt.pitch + rate * tilt.front(), -MOST_LEVEL_TRIM, MOST_LEVEL_TRIM);
        learnt.roll = Mth.clamp(learnt.roll + rate * tilt.right(), -MOST_LEVEL_TRIM, MOST_LEVEL_TRIM);
    }

    /**
     * How her lift must be split to hold up her weight where it sits, as shares moved ahead and to her right: her
     * clusters stand round her disc's middle, so her centre of mass so far ahead of it, as a share of how far out they
     * stand, needs as much more on her front ones (and likewise to her right); none while Sable has not weighed her.
     */
    private Balance balance(SableFlight.State state, Vec3 middle, @Nullable Vec3 centreOfMass) {
        if (centreOfMass == null || wiring.clustersOut() <= 0) {
            return Balance.EVEN;
        }
        Vec3 off = centreOfMass.subtract(middle);
        double ahead = off.dot(flat(state.nose())) / wiring.clustersOut();
        double right = off.dot(flat(state.right())) / wiring.clustersOut();
        return new Balance(Mth.clamp(ahead, -MOST_SPLIT, MOST_SPLIT), Mth.clamp(right, -MOST_SPLIT, MOST_SPLIT));
    }

    /**
     * How much of her lift moves to one side, as a share: what her weight's balance and her trim ask, and toward the
     * side that dips, the more the further and faster it dips.
     */
    private static double split(double tilt, double rate, double held) {
        return Mth.clamp(LEVEL_GAIN * tilt + LEVEL_DAMPING * rate + held, -MOST_SPLIT, MOST_SPLIT);
    }

    /**
     * Her lift at a share of full, spread over her clusters with so much more ahead and to her right (less where it is
     * less), each held at its idle signal at least while it lifts at all.
     */
    private void lift(ServerLevel level, BlockPos origin, double share, double ahead, double right) {
        lastShare = Mth.clamp(share, 0, 1);
        List<List<BlockPos>> clusters = wiring.clusters();
        for (int i = 0; i < clusters.size() && i < CLUSTER_SIDES.length; i++) {
            double each = Mth.clamp(lastShare * (1 + CLUSTER_SIDES[i][0] * ahead + CLUSTER_SIDES[i][1] * right), 0, 1);
            List<BlockPos> cluster = clusters.get(i);
            int least = lastShare > 0 ? IDLE_SIGNAL * cluster.size() : 0;
            clusterTotals[i] = Math.max(least, held(each * FULL * cluster.size(), clusterTotals[i]));
            spread(level, origin, cluster, clusterTotals[i]);
        }
    }

    /**
     * How hard her jets turn her, as a share of a jet's full push: toward the turn she wants for the heading she wants,
     * at most {@link #MOST_YAW_RATE}, against how fast she turns now, with her trim for what turns her unasked.
     */
    private double turn(double wanted, double heading, double yawRate, boolean learning) {
        lastHeadingWanted = wanted;
        double off = Mth.wrapDegrees(wanted - heading);
        double rate = Mth.clamp(YAW_GAIN * off, -MOST_YAW_RATE, MOST_YAW_RATE);
        double error = rate - yawRate;
        if (learning && Math.abs(off) < YAW_LEARNING) {
            double step = YAW_TRIM_RATE / SharedConstants.TICKS_PER_SECOND * error * YAW_RATE_GAIN;
            learnt.yaw = Mth.clamp(learnt.yaw + step, -MOST_YAW_TRIM, MOST_YAW_TRIM);
        }
        return YAW_RATE_GAIN * error + learnt.yaw;
    }

    /**
     * Her mains, retros and jets for the speed she wants ahead and across, and a turn: what pushes her ahead or holds
     * her back, against the drag she learns while cruising, is shared out over her mains or her retros, or boosting,
     * her mains flat out and her retros quiet; what pushes her across, half on her nose's jets and half on her tail's,
     * with the turn added at her nose and taken away at her tail.
     */
    private void move(ServerLevel level, BlockPos origin, SableFlight.State state, Vec3 going, double mass, boolean learning,
                      double ahead, double across, double turn, boolean boosting) {
        lastAhead = ahead;
        lastAcross = across;
        Vec3 front = flat(state.nose());
        Vec3 right = flat(state.right());
        double goingAhead = going.x * front.x + going.z * front.z;
        double goingAcross = going.x * right.x + going.z * right.z;
        lastGoingAhead = goingAhead;
        lastGoingAcross = goingAcross;
        double speeding = Mth.clamp(SPEED_GAIN * (ahead - goingAhead), -MOST_ACCELERATION, MOST_ACCELERATION);
        double shove = mass * speeding / Math.max(1, mostMains()) + learnt.drag * goingAhead;
        double error = ahead - goingAhead;
        boolean steady = Math.abs(error) < DRAG_LEARNING_ERROR && Math.abs(shove) < 1;
        boolean overrunning = error < -DRAG_LEARNING_ERROR;
        if (learning && goingAhead > DRAG_LEARNING_SPEED && (steady || overrunning)) {
            double step = DRAG_RATE / SharedConstants.TICKS_PER_SECOND * Math.min(error, DRAG_LEARNING_ERROR) / goingAhead;
            learnt.drag = Mth.clamp(learnt.drag + step, LEAST_DRAG, MOST_DRAG);
        }
        mainsTotal = boosting ? FULL * wiring.mains().size() : held(Math.max(0, shove) * FULL * wiring.mains().size(), mainsTotal);
        double braking = boosting ? 0 : Math.max(0, -shove) * mostMains() / Math.max(1, mostRetros());
        retrosTotal = held(Math.min(1, braking) * FULL * wiring.retros().size(), retrosTotal);
        spread(level, origin, wiring.mains(), mainsTotal);
        spread(level, origin, wiring.retros(), retrosTotal);
        double speedingAcross = Mth.clamp(SPEED_GAIN * (across - goingAcross), -MOST_ACCELERATION, MOST_ACCELERATION);
        double sideways = mass * speedingAcross / Math.max(1, 2 * mostJet());
        Jets jets = wiring.jets();
        pair(level, origin, jets.noseRight(), jets.noseLeft(), sideways + turn, NOSE_PAIR);
        pair(level, origin, jets.tailRight(), jets.tailLeft(), sideways - turn, TAIL_PAIR);
    }

    /**
     * A pair of opposite jets, her nose's or her tail's, for a push one way, as a share of full: the one pushing that
     * way fires, the pair's signal held as a set of thrusters' is (see {@link #held}), their signals noted for her
     * report, pushing right then left.
     */
    private void pair(ServerLevel level, BlockPos origin, @Nullable BlockPos rightward, @Nullable BlockPos leftward, double push,
                      int pair) {
        int signed = held(Mth.clamp(push, -1, 1) * JET_MOST_SIGNAL, jetsHeld[pair]);
        jetsHeld[pair] = signed;
        jetSignals[2 * pair] = Math.max(0, signed);
        jetSignals[2 * pair + 1] = Math.max(0, -signed);
        drive(level, origin, rightward, jetSignals[2 * pair]);
        drive(level, origin, leftward, jetSignals[2 * pair + 1]);
    }

    private static void drive(ServerLevel level, BlockPos origin, @Nullable BlockPos relay, int signal) {
        if (relay != null) {
            Relays.drive(level, origin, relay, signal, false);
        }
    }

    /** A set of thrusters at a total signal, spread so their sum gives it as nearly as it can. */
    private static void spread(ServerLevel level, BlockPos origin, List<BlockPos> relays, int total) {
        int count = relays.size();
        int each = total / Math.max(1, count);
        for (int i = 0; i < count; i++) {
            // The signals left over once each has its even share go one more to the first ones.
            Relays.drive(level, origin, relays.get(i), Math.min(FULL, each + (i < total - each * count ? 1 : 0)), false);
        }
    }

    /** The signal asked for, held at the one set last until it moves {@link #SIGNAL_HOLD} from it, then the nearest. */
    private static int held(double wanted, int last) {
        return Math.abs(wanted - last) < SIGNAL_HOLD ? last : (int) Math.round(wanted);
    }

    /**
     * Moved straight to her pin by a hyperspace jump (see {@link FreighterJump}): what she had read of how she moves
     * and of the ground round her pin is forgotten, since both were where she was, and she counts afresh from here how
     * long she has been over it.
     */
    void jumped() {
        smoothedGoing = null;
        pads.reset();
        lastPad = null;
        overFor = 0;
    }

    /** Where her disc's middle is in the world, from where her control head is and the way she points. */
    Vec3 middle(SableFlight.State state) {
        Vec3 right = flat(state.right()).scale(wiring.middleRight());
        return state.position().add(right).add(flat(state.nose()).scale(wiring.middleAhead()));
    }

    /** A way along the ground, flattened from one of hers and made a unit long. */
    private static Vec3 flat(Vec3 way) {
        Vec3 flat = new Vec3(way.x, 0, way.z);
        return flat.lengthSqr() < NO_WAY ? Vec3.ZERO : flat.normalize();
    }

    /**
     * How hard one of her thrusters pushes at full signal, set to a share of the configured setting, in Sable's units.
     */
    private static double push(double setting) {
        return Thrusters.CREATIVE_PUSH * Math.max(1, Math.round(SkylinersConfig.THRUST_SETTING.get() * setting)) / 99.0;
    }

    /** How hard all her lift thrusters push together at full signal, in Sable's units. */
    private double mostLift() {
        return liftThrusters * push(LIFT_SETTING);
    }

    private double mostMains() {
        return wiring.mains().size() * push(MAIN_SETTING);
    }

    private double mostRetros() {
        return wiring.retros().size() * push(RETRO_SETTING);
    }

    /** How hard one of her jets pushes at the strongest signal it is given, in Sable's units. */
    private static double mostJet() {
        return push(JET_SETTING) * JET_MOST_SIGNAL / FULL;
    }

    /** At rest on the ground or switched off: every thruster let go. */
    void rest(ServerLevel level, BlockPos origin) {
        Relays.drive(level, origin, links(), 0, false);
        lastShare = 0;
        Arrays.fill(clusterTotals, 0);
        Arrays.fill(jetSignals, 0);
        Arrays.fill(jetsHeld, 0);
        mainsTotal = 0;
        retrosTotal = 0;
    }

    /** Landed, or the flight given up: the next flight begins on the ground again, what she learnt kept. */
    void stop(ServerLevel level, BlockPos origin) {
        rest(level, origin);
        primed = false;
        spot = null;
        pads.reset();
        startCounting();
        easedClimb = 0;
    }

    Report report() {
        return new Report(lastShare, learnt.lift, learnt.pitch, learnt.roll, learnt.drag, lastTilt.front(), lastTilt.right(),
            Arrays.stream(clusterTotals).boxed().toList(), mainsTotal, retrosTotal, Arrays.stream(jetSignals).boxed().toList(), lastClimb,
            lastGoingAhead, lastGoingAcross, easedClimb, lastAhead, lastAcross, lastHeadingWanted, lastPad, lastMiddle);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag clusters = new ListTag();
        wiring.clusters().forEach(cluster -> clusters.add(Relays.write(cluster)));
        tag.put("Clusters", clusters);
        tag.put("Mains", Relays.write(wiring.mains()));
        tag.put("Retros", Relays.write(wiring.retros()));
        tag.put("Jets", wiring.jets().save());
        tag.put("Feet", Relays.write(wiring.feet()));
        tag.put("Grille", Relays.write(wiring.grille()));
        tag.putDouble("MiddleRight", wiring.middleRight());
        tag.putDouble("MiddleAhead", wiring.middleAhead());
        tag.putDouble("ClustersOut", wiring.clustersOut());
        learnt.save(tag);
        return tag;
    }

    /** Loads what was saved, turned as her control head has turned since; anything missing starts afresh. */
    void load(CompoundTag tag, Rotation turn) {
        List<List<BlockPos>> clusters = new ArrayList<>();
        for (Tag entry : tag.getList("Clusters", Tag.TAG_LIST)) {
            clusters.add(Turning.turn(Relays.read((ListTag) entry), turn));
        }
        configure(new Wiring(clusters, Turning.turn(Relays.read(tag.getList("Mains", Tag.TAG_INT_ARRAY)), turn),
            Turning.turn(Relays.read(tag.getList("Retros", Tag.TAG_INT_ARRAY)), turn), Jets.load(tag.getCompound("Jets"), turn),
            Turning.turn(Relays.read(tag.getList("Feet", Tag.TAG_INT_ARRAY)), turn),
            Turning.turn(Relays.read(tag.getList("Grille", Tag.TAG_INT_ARRAY)), turn), tag.getDouble("MiddleRight"),
            tag.getDouble("MiddleAhead"), tag.getDouble("ClustersOut")));
        learnt.load(tag);
    }
}
