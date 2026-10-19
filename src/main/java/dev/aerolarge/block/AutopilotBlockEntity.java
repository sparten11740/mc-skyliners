package dev.aerolarge.block;

import dev.aerolarge.Aboard;
import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.cabin.Turbulence;
import dev.aerolarge.compat.SableFlight;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * The autopilot: flies the assembled airliner by setting its thrusters. The four engines push it along and, split
 * left against right, help turn it; jets on the tailplane pitch it, standing in for elevators, and jets at the wingtips
 * bank it, standing in for ailerons. It reads where the airliner is and how it moves from Sable each tick.
 *
 * <p>Engaged on the ground it takes off: the roll at full thrust up to rotation speed, holding the runway heading with
 * the nose wheel, then the nose raised until the airliner lifts off. In the air it holds a height and a speed, and
 * either a heading or a circle round a point (an orbit). Height: it sets the nose to climb or sink at the rate the
 * height still to go asks for, around the pitch it learns holds the height at this speed. That pitch is whatever the
 * airliner turns out to need: Sable's wings lift with the airspeed even flying level, more the more the air meets
 * them from below, and the engines push hard along the nose, so no one angle can be worked out beforehand. Following
 * the flight path instead would steepen a climb as it grew steeper. Heading: it banks toward it, turning the
 * wings' lift sideways, and in an orbit it banks into the circle from the start and steers onto it. Engaged in the air
 * it holds the height, speed and heading it found. Its settings screen, opened from the autopilot or the flight deck's
 * autopilot screen, engages it and sets the targets (see {@link #set}); switching it off hands the airliner back to
 * its controls: the thrust levers, the tiller and the parking brake. Asked to land, it comes down ahead, touches down
 * gently and brakes to a stop, then parks and hands back to those controls by itself.
 *
 * <p>Whether it is engaged or not, it gives the flight deck its callouts (see {@link Callouts}), and off it still
 * keeps the airliner safe: in the air its jets steady any quick pitching or rolling and push the nose and the wings
 * back once they go past {@link #PROTECT_CLIMB}, {@link #PROTECT_DIVE} or {@link #PROTECT_BANK} degrees, righting it
 * even from upside down; past {@link #MAX_SAFE_SPEED}, or climbing fast enough to coast past the top of the world, it
 * cuts the engines itself until the airliner has slowed and levelled, then hands them back to the levers. Nothing
 * else holds the airliner while it is flown on the thrust levers: at full thrust its engines would pitch it up until
 * it tumbled, or fling it off far faster than the world can be made around it.
 */
public class AutopilotBlockEntity extends BlockEntity {
    /** Everything the autopilot sets, each a group of relays next to thrusters. */
    public enum Channel {
        ENGINE_1, ENGINE_2, ENGINE_3, ENGINE_4, PITCH_UP, PITCH_DOWN, ROLL_LEFT, ROLL_RIGHT, STEER_LEFT, STEER_RIGHT, BRAKES
    }

    /**
     * What it is doing about height: off, taking off, climbing or descending to the height, holding it, coming down to
     * land, or braking to a stop once down.
     */
    public enum Phase {
        OFF, TAKEOFF, CLIMB, DESCENT, CRUISE, LAND, ROLLOUT
    }

    /** What it is doing about direction: holding a heading, or circling a point to the left or right. */
    public enum Lateral {
        HEADING, ORBIT_LEFT, ORBIT_RIGHT
    }

    /**
     * What the settings screen sets: the speed in blocks a second, the height to hold in world blocks, the heading as
     * a compass bearing (0 north, 90 east), whether to hold that heading or circle, and the circle's radius in blocks;
     * whole numbers, as the screen shows them.
     */
    public record Settings(int speed, int altitude, int heading, Lateral lateral, int radius) {
        public static final StreamCodec<FriendlyByteBuf, Settings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Settings::speed,
            ByteBufCodecs.VAR_INT, Settings::altitude,
            ByteBufCodecs.VAR_INT, Settings::heading,
            NeoForgeStreamCodecs.enumCodec(Lateral.class), Settings::lateral,
            ByteBufCodecs.VAR_INT, Settings::radius,
            Settings::new
        );
    }

    /** What engaging flies: the phase it starts in, the runway's height, and the height, speed and heading to hold. */
    private record Plan(Phase phase, double runway, double altitude, double speed, double heading) {
    }

    /**
     * The attitude the airliner is kept within with the autopilot off, in degrees: the nose up or down, and the bank
     * either way.
     */
    private static final double PROTECT_CLIMB = 25;
    private static final double PROTECT_DIVE = 20;
    private static final double PROTECT_BANK = 35;
    /**
     * The fastest it lets the airliner go on the thrust levers, in blocks a second, and how far below that it must
     * slow before the levers have the engines again.
     */
    private static final double MAX_SAFE_SPEED = 90;
    private static final double GOVERNOR_MARGIN = 15;
    /**
     * How far below the top of the world the airliner is kept, in blocks; once it would coast past that, it must
     * come down as far again under it before the levers have the engines again.
     */
    private static final int CEILING_MARGIN = 30;
    /** Nose attitude at rotation, in degrees: what lifts the airliner off the runway. */
    private static final double ROTATION_PITCH = 9;
    /** Rate of climb asked for, in blocks a second per block of height still to go, and the most up and down. */
    private static final double HEIGHT_GAIN = 0.2;
    private static final double MAX_CLIMB_RATE = 8;
    private static final double MAX_SINK_RATE = 6;
    /**
     * Degrees of pitch added per block a second it climbs too slowly, taken off per block a second each second it is
     * gaining climb (damping), and the range the nose is held in.
     */
    private static final double CLIMB_GAIN = 0.8;
    private static final double CLIMB_DAMPING = 0.5;
    private static final double MIN_PITCH = -15;
    private static final double MAX_PITCH = 20;
    /**
     * How fast the trim learns, in degrees a tick per block a second the climb is off, counting no more than this
     * many blocks a second off, so a big change of height learns no faster than a small one; and only while the nose
     * is within this many degrees of where it is wanted and not held at either end of its range: while the nose is
     * still swinging round, or pinned at a limit, learning would only wind it up.
     */
    private static final double TRIM_RATE = 0.01;
    private static final double TRIM_CLIMB_LIMIT = 3;
    private static final double TRIM_WINDOW = 3;
    /**
     * The engines' signal it starts from at the speed being held, and how much it changes per block a second off that
     * speed.
     */
    private static final double CRUISE_THRUST = 6;
    private static final double SPEED_GAIN = 0.6;
    /**
     * How fast the signal that holds the speed is learnt, in signal a tick per block a second off it, counting no more
     * than this many blocks a second off: the air thickens lower down and the drag changes with the attitude, so no
     * one signal holds every speed at every height.
     */
    private static final double THRUST_TRIM_RATE = 0.005;
    private static final double THRUST_TRIM_LIMIT = 5;
    /** Signal per degree of pitch or bank error, and per degree a second it is changing at (damping). */
    private static final double PITCH_GAIN = 0.75;
    private static final double PITCH_DAMPING = 0.4;
    private static final double ROLL_GAIN = 0.5;
    private static final double ROLL_DAMPING = 0.3;
    /**
     * Degrees of bank per degree off the heading wanted, taken off per degree a second it turns faster than it should
     * (damping, so it rolls out before it swings past), and the steepest bank it flies.
     */
    private static final double BANK_GAIN = 0.8;
    private static final double TURN_DAMPING = 1.0;
    private static final double MAX_BANK = 30;
    /** Engine signal split per degree off the heading, and the most it splits by. */
    private static final double YAW_GAIN = 0.4;
    private static final int YAW_SPLIT = 4;
    /** Nose wheel steering signal per degree off the heading on the ground. */
    private static final double STEER_GAIN = 3;
    /** How hard an orbit steers back onto its circle: as a share of the way inward, per radius off it. */
    private static final double ORBIT_CAPTURE = 2;
    /** Height above the ground that counts as airborne, and how close to the height wanted counts as holding it. */
    private static final double AIRBORNE = 3;
    private static final double LEVEL_OFF = 8;
    /** The roll is still gaining speed while it gains more than this, in blocks per second every second. */
    private static final double STILL_ACCELERATING = 0.4;
    /**
     * Landing: the speed it starts slowing to, as a share of the rotation speed; then the nose attitude it finds the
     * speed for, in degrees above the way it is going, how fast, in blocks a second a tick per degree off, and the
     * slowest it goes, as a share of the rotation speed. Nose about level, as it stands on its wheels: any faster and
     * it flies nose down, comes down on its nose wheel and the wings lift it off again. The speed that gives it changes
     * with how heavy the airliner is loaded and how thin the air is, so it is found, not set.
     */
    private static final double APPROACH_MARGIN = 1.1;
    private static final double APPROACH_ATTITUDE = 1;
    private static final double APPROACH_SPEED_RATE = 0.005;
    private static final double APPROACH_SLOWEST = 0.75;
    /**
     * Landing: the rate it sinks at, in blocks a second; how much less it sinks per block nearer the ground, from the
     * height the flare starts at; and the rate it touches down at, half what a butter landing has to stay under.
     */
    private static final double LANDING_SINK_RATE = 3;
    private static final double FLARE_GAIN = 0.2;
    private static final double FLARE_HEIGHT = LANDING_SINK_RATE / FLARE_GAIN;
    private static final double TOUCHDOWN_SINK = Callouts.BUTTER_SINK / 2;
    /** The steepest bank it flies landing, in degrees per block above the ground. */
    private static final double LANDING_BANK_PER_BLOCK = 0.5;
    /** Slower than this, in blocks a second, it has stopped. */
    private static final double STOPPED = 0.5;
    /** How far from the autopilot it is heard, in blocks: the flight deck and the cabin just behind it. */
    private static final double FLIGHT_DECK = 24;
    /**
     * How the settings screen's buttons step the targets: degrees, blocks of height, blocks a second, and blocks of
     * radius per press.
     */
    public static final int HEADING_STEP = 15;
    public static final int ALTITUDE_STEP = 20;
    public static final int SPEED_STEP = 5;
    public static final int RADIUS_STEP = 50;
    /** The speeds and orbit radii it flies; the height it holds is kept between {@link #floor} and {@link #ceiling}. */
    public static final int MIN_SPEED = 30;
    public static final int MAX_SPEED = 120;
    public static final int MIN_RADIUS = 150;
    public static final int MAX_RADIUS = 1000;
    private static final double DEFAULT_RADIUS = 600;

    private Map<Channel, List<BlockPos>> relays = new EnumMap<>(Channel.class);
    /** The flight deck controls (thrust levers, tiller, parking brake) it hands back to when it lets go. */
    private List<BlockPos> controls = List.of();
    private Phase phase = Phase.OFF;
    private Lateral lateral = Lateral.HEADING;
    /** The heading to hold, as Minecraft yaw; the height to hold, in world blocks; the speed, in blocks a second. */
    private double targetHeading;
    private double targetAltitude;
    private double targetSpeed;
    /** Where it took off from, so the status can say how high it is above the runway. */
    private double runwayAltitude;
    /** The heading it took off on, held down the runway whatever heading is set for after the take-off. */
    private double runwayHeading;
    /** The point an orbit circles, in world blocks, and its radius. */
    private double orbitX;
    private double orbitZ;
    private double orbitRadius = DEFAULT_RADIUS;
    /** The pitch that holds the height at this speed, and the engines' signal that holds the speed, learnt as it flies. */
    private double trim;
    private double thrustTrim = CRUISE_THRUST;
    private double lastPitch;
    private double lastRoll;
    private double lastSpeed;
    private double lastClimb;
    private double lastHeading;
    private int slowTicks;
    /** Whether last tick's readings are real; false after loading, so the first tick does not see a jump. */
    private boolean primed;
    /** How far its block stands above the bottom of the wheels, for the radio altitude. */
    private int wheelsBelow;
    /** Whether the protection has taken the engines from the levers, to slow an airliner going too fast or too high. */
    private boolean governing;
    private final Callouts callouts = new Callouts();
    private final Turbulence turbulence = new Turbulence();

    public AutopilotBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.AUTOPILOT_ENTITY.get(), pos, state);
    }

    /** Hands the autopilot the relays it drives and the controls it gives the airliner back to. */
    public void configure(Map<Channel, List<BlockPos>> relays, List<BlockPos> controls, int wheelsBelow) {
        this.wheelsBelow = wheelsBelow;
        this.relays = new EnumMap<>(Channel.class);
        this.relays.putAll(relays);
        this.controls = List.copyOf(controls);
        setChanged();
    }

    public Phase phase() {
        return phase;
    }

    public Lateral lateral() {
        return lateral;
    }

    public double targetHeading() {
        return targetHeading;
    }

    public double targetAltitude() {
        return targetAltitude;
    }

    public double targetSpeed() {
        return targetSpeed;
    }

    public double runwayAltitude() {
        return runwayAltitude;
    }

    public double orbitX() {
        return orbitX;
    }

    public double orbitZ() {
        return orbitZ;
    }

    public double orbitRadius() {
        return orbitRadius;
    }

    /**
     * Whether a player can work the autopilot from where they are: within reach of it, give or take the few blocks a
     * container's screen stays open past reach, which takes in the flight deck's autopilot screen. With Sable, reach is
     * measured among an assembled airliner's blocks, so it holds however the airliner flies.
     */
    public boolean canBeWorkedBy(Player player) {
        return !player.isSpectator() && Container.stillValidBlockEntity(this, player);
    }

    /**
     * Opens the settings screen for a player, filled in with what the autopilot flies now or, switched off, what
     * engaging it now would fly, and the lowest and highest heights it can be set to hold.
     */
    public void openScreen(ServerPlayer player) {
        if (level == null) {
            return;
        }
        SableFlight.State state = SableFlight.read(this, nose());
        Plan plan;
        if (phase != Phase.OFF) {
            // Circling, the heading offered is the way it is going, to fly straight on from.
            double heading = lateral == Lateral.HEADING || state == null ? targetHeading : state.heading();
            plan = new Plan(phase, runwayAltitude, targetAltitude, targetSpeed, heading);
        } else {
            plan = plan(level, state);
        }
        Settings settings = new Settings((int) Math.round(plan.speed()), (int) Math.round(plan.altitude()), bearing(plan.heading()),
            phase != Phase.OFF ? lateral : Lateral.HEADING, (int) Math.round(orbitRadius));
        double floor = floor(plan.runway());
        PacketDistributor.sendToPlayer(player, new OpenAutopilotScreen(worldPosition, settings, plan.phase() == Phase.TAKEOFF,
            Mth.ceil(floor), Mth.floor(Math.max(floor, ceiling(level)))));
    }

    /**
     * Sets the autopilot from its settings screen: flies the settings, engaging it first if it is off (to take off, or
     * to fly on from where it is in the air), lands, or switches it off. Each setting is held to what the autopilot
     * flies, and only one that differs from what it flies now is taken, so settings sent back as they were neither
     * round a target off nor start an orbit's circle afresh. Flying the settings while landing goes around; once down
     * it only stops. Says what it does, or why it cannot.
     */
    public Component set(SetAutopilot.Action action, Settings settings) {
        if (action == SetAutopilot.Action.OFF) {
            if (phase != Phase.OFF) {
                disengage();
            }
            return Component.translatable("message.aerolarge.autopilot.off");
        }
        SableFlight.State state = SableFlight.read(this, nose());
        if (state == null || level == null) {
            return Component.translatable("message.aerolarge.autopilot.not_assembled");
        }
        if (action == SetAutopilot.Action.LAND) {
            return land(level, state);
        }
        if (phase == Phase.ROLLOUT) {
            return Component.translatable("message.aerolarge.autopilot.rolling_out");
        }
        boolean engaging = phase == Phase.OFF;
        if (engaging) {
            engage(level, state);
        } else if (phase == Phase.LAND) {
            // A go-around: it climbs away again, and the next tick finds whether to climb, descend or hold.
            phase = Phase.CLIMB;
        }
        targetSpeed = Mth.clamp(unlessSame(targetSpeed, settings.speed()), MIN_SPEED, MAX_SPEED);
        double floor = floor(runwayAltitude);
        targetAltitude = Mth.clamp(unlessSame(targetAltitude, settings.altitude()), floor, Math.max(floor, ceiling(level)));
        double radius = Mth.clamp(unlessSame(orbitRadius, settings.radius()), MIN_RADIUS, MAX_RADIUS);
        boolean resized = radius != orbitRadius;
        orbitRadius = radius;
        int bearing = Math.floorMod(settings.heading(), 360);
        if (settings.lateral() == Lateral.HEADING) {
            if (lateral != Lateral.HEADING || bearing != bearing(targetHeading)) {
                targetHeading = Mth.wrapDegrees(bearing + 180.0);
            }
            lateral = Lateral.HEADING;
        } else if (settings.lateral() != lateral || resized) {
            startOrbit(state, settings.lateral());
        }
        changed();
        Component mode = lateral == Lateral.HEADING
            ? Component.translatable("message.aerolarge.autopilot.mode.heading", compass(targetHeading))
            : Component.translatable("message.aerolarge.autopilot.mode." + lateral.name().toLowerCase(Locale.ROOT), Math.round(orbitRadius));
        String said = !engaging ? "set" : phase == Phase.TAKEOFF ? "engaged" : "holding";
        return Component.translatable("message.aerolarge.autopilot." + said, Math.round(targetAltitude), Math.round(targetSpeed), mode);
    }

    /**
     * Lands the airliner ahead, engaging the autopilot if it is off. Out of any circle onto the heading it is on, it
     * comes down gently, slowing to the speed it flies nose level at, and flares over the ground to touch down softly;
     * then it idles the engines and brakes to a stop, parks and hands back to the flight deck (see {@link #fly}). Only
     * in the air.
     */
    private Component land(Level level, SableFlight.State state) {
        if (radioAltitude(level, state) <= AIRBORNE) {
            return Component.translatable("message.aerolarge.autopilot.on_ground");
        }
        if (phase == Phase.OFF) {
            engage(level, state);
        }
        phase = Phase.LAND;
        lateral = Lateral.HEADING;
        targetHeading = state.heading();
        targetSpeed = Mth.clamp(AerolargeConfig.AUTOPILOT_ROTATE_SPEED.get() * APPROACH_MARGIN, MIN_SPEED, MAX_SPEED);
        changed();
        return Component.translatable("message.aerolarge.autopilot.landing", compass(targetHeading));
    }

    /** A setting from the screen, or the target it was shown from if it comes back as the screen showed it. */
    private static double unlessSame(double target, int setting) {
        return Math.round(target) == setting ? target : setting;
    }

    /** Takes the airliner over, flying what engaging finds (see {@link #plan}) from its readings as they are now. */
    private void engage(Level level, SableFlight.State state) {
        Plan plan = plan(level, state);
        phase = plan.phase();
        runwayAltitude = plan.runway();
        targetAltitude = plan.altitude();
        targetSpeed = plan.speed();
        targetHeading = plan.heading();
        runwayHeading = plan.heading();
        lateral = Lateral.HEADING;
        if (phase == Phase.CRUISE) {
            trim = Mth.clamp(state.pitch(), MIN_PITCH, MAX_PITCH);
        }
        thrustTrim = CRUISE_THRUST;
        lastPitch = state.pitch();
        lastRoll = state.roll();
        lastSpeed = state.airspeed();
        lastClimb = state.velocity().y;
        lastHeading = state.heading();
        slowTicks = 0;
        primed = true;
    }

    /**
     * What engaging now would fly. In the air, the height, speed and heading it finds. On the ground, a take-off on
     * the heading it stands on and a climb to the cruising height above the runway at the cruise speed, both from the
     * server settings; not assembled, as if on the ground where it stands.
     */
    private Plan plan(Level level, @Nullable SableFlight.State state) {
        if (state != null) {
            double radio = radioAltitude(level, state);
            if (radio > AIRBORNE) {
                return new Plan(Phase.CRUISE, state.position().y - radio, Math.round(state.position().y),
                    Math.round(Mth.clamp(state.airspeed(), MIN_SPEED, MAX_SPEED)), state.heading());
            }
        }
        double runway = state != null ? state.position().y : Vec3.atCenterOf(worldPosition).y;
        double heading = state != null ? state.heading() : nose().toYRot();
        return new Plan(Phase.TAKEOFF, runway, Math.min(runway + AerolargeConfig.AUTOPILOT_CLIMB.get(), ceiling(level)),
            Mth.clamp(AerolargeConfig.AUTOPILOT_CRUISE_SPEED.get(), MIN_SPEED, MAX_SPEED), heading);
    }

    /** The lowest height it can be set to hold: two of the screen's steps above the runway. */
    private static double floor(double runway) {
        return runway + 2 * ALTITUDE_STEP;
    }

    /** Circles a point beside the airliner, on the side it turns to, so it banks straight into the circle. */
    private void startOrbit(SableFlight.State state, Lateral orbit) {
        double side = orbit == Lateral.ORBIT_RIGHT ? 1 : -1;
        // To the right of the nose, in the horizontal plane: Minecraft's z runs south, so right of north is east.
        Vec3 forward = new Vec3(state.nose().x, 0, state.nose().z).normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        orbitX = state.position().x + right.x * side * orbitRadius;
        orbitZ = state.position().z + right.z * side * orbitRadius;
        lateral = orbit;
    }

    private Direction nose() {
        return getBlockState().getValue(AutopilotBlock.FACING);
    }

    /**
     * Each tick while the airliner is assembled: the callouts watch it, rough air comes and goes, and the autopilot
     * flies it if engaged, or keeps it from tipping over if not.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, AutopilotBlockEntity autopilot) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        SableFlight.State flight = SableFlight.read(autopilot, autopilot.nose());
        if (flight == null) {
            if (autopilot.phase != Phase.OFF) {
                // Disassembled under it: nothing left to fly.
                autopilot.disengage();
            }
            return;
        }
        autopilot.callouts.watch(serverLevel, flight, autopilot.wheelsBelow, () -> Aboard.players(autopilot));
        autopilot.turbulence.tick(serverLevel, autopilot, flight.position(), autopilot.radioAltitude(serverLevel, flight), flight.airspeed());
        if (autopilot.phase != Phase.OFF) {
            autopilot.fly(serverLevel, flight);
        } else {
            autopilot.protect(serverLevel, flight);
        }
    }

    /**
     * With the autopilot off, in the air: the jets damp quick pitching and rolling, and push the nose and the wings
     * back inside the safe attitude once they go past it. On the ground they stay quiet.
     */
    private void protect(ServerLevel level, SableFlight.State state) {
        double pitch = state.pitch();
        double roll = state.roll();
        if (!primed) {
            lastPitch = pitch;
            lastRoll = roll;
            primed = true;
        }
        double pitchRate = (pitch - lastPitch) * 20;
        double rollRate = Mth.wrapDegrees(roll - lastRoll) * 20;
        lastPitch = pitch;
        lastRoll = roll;
        int pitchCommand = 0;
        int rollCommand = 0;
        if (radioAltitude(level, state) > AIRBORNE) {
            double climbLimit = state.apex() > ceiling(level) ? 0 : PROTECT_CLIMB;
            double pitchBack = Mth.clamp(pitch, -PROTECT_DIVE, climbLimit) - pitch;
            double rollBack = Mth.clamp(roll, -PROTECT_BANK, PROTECT_BANK) - roll;
            pitchCommand = (int) Math.round(PITCH_GAIN * pitchBack - PITCH_DAMPING * pitchRate);
            rollCommand = (int) Math.round(ROLL_GAIN * rollBack - ROLL_DAMPING * rollRate);
        }
        drive(level, Channel.PITCH_UP, Mth.clamp(pitchCommand, 0, 15));
        drive(level, Channel.PITCH_DOWN, Mth.clamp(-pitchCommand, 0, 15));
        drive(level, Channel.ROLL_RIGHT, Mth.clamp(rollCommand, 0, 15));
        drive(level, Channel.ROLL_LEFT, Mth.clamp(-rollCommand, 0, 15));

        double speed = state.airspeed();
        double apex = state.apex();
        if (speed > MAX_SAFE_SPEED || apex > ceiling(level)) {
            if (!governing) {
                governing = true;
                setChanged();
            }
            // Every tick, so a lever moved meanwhile does not get the engines back.
            for (Channel engine : new Channel[]{Channel.ENGINE_1, Channel.ENGINE_2, Channel.ENGINE_3, Channel.ENGINE_4}) {
                drive(level, engine, 0);
            }
        } else if (governing && speed < MAX_SAFE_SPEED - GOVERNOR_MARGIN && apex < ceiling(level) - CEILING_MARGIN) {
            governing = false;
            resyncControls(level);
            setChanged();
        }
    }

    /** The highest the airliner is let go: some way below the top of the world. */
    private static double ceiling(Level level) {
        return level.getMaxBuildHeight() - CEILING_MARGIN;
    }

    private void fly(ServerLevel level, SableFlight.State state) {
        double pitch = state.pitch();
        double roll = state.roll();
        double speed = state.airspeed();
        double climb = state.velocity().y;
        if (!primed) {
            lastPitch = pitch;
            lastRoll = roll;
            lastSpeed = speed;
            lastClimb = climb;
            lastHeading = state.heading();
            primed = true;
        }
        double pitchRate = (pitch - lastPitch) * 20;
        double rollRate = Mth.wrapDegrees(roll - lastRoll) * 20;
        double climbRate = (climb - lastClimb) * 20;
        double rotateSpeed = AerolargeConfig.AUTOPILOT_ROTATE_SPEED.get();
        Phase was = phase;

        double radio = radioAltitude(level, state);
        double targetPitch;
        int thrust;
        boolean takingOff = phase == Phase.TAKEOFF;
        boolean rollingOut = phase == Phase.ROLLOUT;
        boolean onGround = takingOff || rollingOut;
        // Down the runway it holds the heading it started on; the heading set is flown once it is off the ground.
        double wanted = takingOff ? runwayHeading : targetHeading;
        double bank = 0;
        if (takingOff) {
            boolean accelerating = speed - lastSpeed > STILL_ACCELERATING / 20;
            slowTicks = accelerating || speed < 2 ? 0 : slowTicks + 1;
            boolean rotate = speed >= rotateSpeed || slowTicks > 40;
            targetPitch = rotate ? ROTATION_PITCH : 0;
            // Full thrust down the runway; from rotation on, the speed it will climb at.
            thrust = rotate ? holdSpeed(speed, targetSpeed) : 15;
            if (radio > AIRBORNE) {
                phase = Phase.CLIMB;
                // The pitch it lifts off at is where the trim starts learning from.
                trim = Mth.clamp(pitch, MIN_PITCH, MAX_PITCH);
            }
        } else if (rollingOut) {
            // Down after landing: the nose lowered onto its wheel so the wings stop lifting, the engines at idle.
            targetPitch = 0;
            thrust = 0;
        } else {
            boolean landing = phase == Phase.LAND;
            double heightToGo = targetAltitude - state.position().y;
            // Landing, it sinks gently, and ever more gently the nearer the ground below: the flare.
            double wantedClimb = landing ? -Mth.clamp(FLARE_GAIN * radio, TOUCHDOWN_SINK, LANDING_SINK_RATE)
                : Mth.clamp(HEIGHT_GAIN * heightToGo, -MAX_SINK_RATE, MAX_CLIMB_RATE);
            double climbError = wantedClimb - climb;
            // Landing, the nose comes up no further than at rotation, so the tail stays clear of the ground.
            double maxPitch = landing ? ROTATION_PITCH : MAX_PITCH;
            targetPitch = Mth.clamp(trim + CLIMB_GAIN * climbError - CLIMB_DAMPING * climbRate, MIN_PITCH, maxPitch);
            boolean pinned = targetPitch <= MIN_PITCH || targetPitch >= maxPitch;
            if (!pinned && Math.abs(targetPitch - pitch) < TRIM_WINDOW) {
                trim = Mth.clamp(trim + TRIM_RATE * Mth.clamp(climbError, -TRIM_CLIMB_LIMIT, TRIM_CLIMB_LIMIT), MIN_PITCH, MAX_PITCH);
            }
            if (landing && radio > FLARE_HEIGHT) {
                // Coming in, slower while the nose is below the way it goes and faster while above; flaring, it keeps
                // the speed it found.
                double horizontal = Math.max(1, Math.hypot(state.velocity().x, state.velocity().z));
                double attitude = pitch - Math.toDegrees(Math.atan2(climb, horizontal));
                targetSpeed = Mth.clamp(targetSpeed + APPROACH_SPEED_RATE * (attitude - APPROACH_ATTITUDE),
                    APPROACH_SLOWEST * rotateSpeed, MAX_SPEED);
            }
            thrust = holdSpeed(speed, targetSpeed);
            if (!landing) {
                phase = Math.abs(heightToGo) < LEVEL_OFF ? Phase.CRUISE : heightToGo > 0 ? Phase.CLIMB : Phase.DESCENT;
            } else if (radio < Callouts.ON_GROUND) {
                phase = Phase.ROLLOUT;
            }
            // The turn it should be making, in degrees a second: round the circle at this speed in an orbit, none on a
            // heading.
            double wantedTurn = 0;
            if (lateral != Lateral.HEADING) {
                wanted = orbitHeading(state);
                // Banked into the circle from the start: the bank that turns at this speed round this radius.
                double side = lateral == Lateral.ORBIT_RIGHT ? 1 : -1;
                double horizontal = Math.hypot(state.velocity().x, state.velocity().z);
                bank = side * Math.toDegrees(Math.atan(horizontal * horizontal / (Math.max(1, state.gravity()) * orbitRadius)));
                wantedTurn = side * Math.toDegrees(horizontal / orbitRadius);
            }
            double turn = Mth.wrapDegrees(state.heading() - lastHeading) * 20;
            // Landing, it banks less the lower it gets, to keep its long wings clear of the ground.
            double maxBank = landing ? Mth.clamp(LANDING_BANK_PER_BLOCK * radio, 0, MAX_BANK) : MAX_BANK;
            bank = Mth.clamp(bank + BANK_GAIN * Mth.wrapDegrees(wanted - state.heading()) - TURN_DAMPING * (turn - wantedTurn), -maxBank, maxBank);
        }

        int pitchCommand = (int) Math.round(PITCH_GAIN * (targetPitch - pitch) - PITCH_DAMPING * pitchRate);
        int rollCommand = (int) Math.round(ROLL_GAIN * (bank - roll) - ROLL_DAMPING * rollRate);
        boolean rolls = !onGround || radio > 0.5;
        drive(level, Channel.PITCH_UP, Mth.clamp(pitchCommand, 0, 15));
        drive(level, Channel.PITCH_DOWN, Mth.clamp(-pitchCommand, 0, 15));
        drive(level, Channel.ROLL_RIGHT, rolls ? Mth.clamp(rollCommand, 0, 15) : 0);
        drive(level, Channel.ROLL_LEFT, rolls ? Mth.clamp(-rollCommand, 0, 15) : 0);

        // The brakes are off while it flies and on once it has landed; on the ground it also steers the nose wheel to
        // hold the heading.
        drive(level, Channel.BRAKES, rollingOut ? 15 : 0);
        double headingError = Mth.wrapDegrees(wanted - state.heading());
        int steer = onGround ? Mth.clamp((int) Math.round(STEER_GAIN * headingError), -15, 15) : 0;
        drive(level, Channel.STEER_RIGHT, Math.max(0, steer));
        drive(level, Channel.STEER_LEFT, Math.max(0, -steer));

        // Turning right means more thrust on the left engines; the heading grows turning right.
        int split = Mth.clamp((int) Math.round(YAW_GAIN * headingError), -YAW_SPLIT, YAW_SPLIT);
        drive(level, Channel.ENGINE_1, thrust - Math.max(0, -split));
        drive(level, Channel.ENGINE_2, thrust - Math.max(0, -split));
        drive(level, Channel.ENGINE_3, thrust - Math.max(0, split));
        drive(level, Channel.ENGINE_4, thrust - Math.max(0, split));

        lastPitch = pitch;
        lastRoll = roll;
        lastSpeed = speed;
        lastClimb = climb;
        lastHeading = state.heading();
        if (rollingOut && state.velocity().length() < STOPPED) {
            // Stopped after landing: parked, and handed back to the flight deck.
            park(level);
            disengage();
            tell(level, state.position(), Component.translatable("message.aerolarge.autopilot.landed"), false);
            return;
        }
        if (level.getGameTime() % 10 == 0) {
            report(level, state);
        }
        if (phase != was) {
            changed();
        } else {
            setChanged();
        }
    }

    /**
     * The heading that keeps an orbit on its circle: along the circle the way it turns, steered in while outside it
     * and out while inside, more the further off it is.
     */
    private double orbitHeading(SableFlight.State state) {
        double dx = state.position().x - orbitX;
        double dz = state.position().z - orbitZ;
        double distance = Math.max(1, Math.hypot(dx, dz));
        double side = lateral == Lateral.ORBIT_RIGHT ? 1 : -1;
        // Out from the middle, and along the circle: a right orbit runs clockwise seen from above.
        double outX = dx / distance;
        double outZ = dz / distance;
        double alongX = -outZ * side;
        double alongZ = outX * side;
        double inward = Mth.clamp(ORBIT_CAPTURE * (distance - orbitRadius) / orbitRadius, -1, 1);
        double x = alongX - outX * inward;
        double z = alongZ - outZ * inward;
        return Math.toDegrees(Math.atan2(-x, z));
    }

    /**
     * The engines' signal to hold a speed: down to idle when too fast, full when too slow, about the signal learnt to
     * hold it. That is learnt only while the engines can still give what it asks; past idle or full, learning would
     * only wind it up.
     */
    private int holdSpeed(double speed, double target) {
        double error = target - speed;
        double signal = thrustTrim + SPEED_GAIN * error;
        if (signal > 0 && signal < 15) {
            thrustTrim = Mth.clamp(thrustTrim + THRUST_TRIM_RATE * Mth.clamp(error, -THRUST_TRIM_LIMIT, THRUST_TRIM_LIMIT), 0, 15);
        }
        return Mth.clamp((int) Math.round(signal), 0, 15);
    }

    /** How high the bottom of the wheels is above the ground below the autopilot. */
    private double radioAltitude(Level level, SableFlight.State state) {
        Vec3 at = state.position();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
        return at.y - 0.5 - wheelsBelow - ground;
    }

    /** A heading as a compass bearing, 0 north and 90 east, three digits. */
    public static String compass(double heading) {
        return String.format(Locale.ROOT, "%03d", bearing(heading));
    }

    /** A heading (Minecraft's yaw) as a compass bearing in whole degrees, 0 north and 90 east. */
    private static int bearing(double heading) {
        return Math.floorMod(Math.round(heading) + 180, 360);
    }

    /** Shows the phase, speed, height and attitude to everyone near the flight deck. */
    private void report(ServerLevel level, SableFlight.State state) {
        Component status = Component.translatable("message.aerolarge.autopilot.status",
            Component.translatable("message.aerolarge.autopilot.phase." + phase.name().toLowerCase(Locale.ROOT)),
            Math.round(state.airspeed()), String.format(Locale.ROOT, "%+d", Math.round(state.position().y - runwayAltitude)),
            Math.round(state.pitch()), Math.round(state.roll()), compass(state.heading()));
        tell(level, state.position(), status, true);
    }

    /** Shows a message to everyone near the flight deck, in the action bar or in chat. */
    private static void tell(ServerLevel level, Vec3 flightDeck, Component message, boolean actionBar) {
        level.players().stream()
            .filter(player -> player.position().distanceToSqr(flightDeck) < FLIGHT_DECK * FLIGHT_DECK)
            .forEach(player -> player.displayClientMessage(message, actionBar));
    }

    /**
     * Sets the flight deck's controls as for a parked airliner, so that it stays where it stopped once they have it
     * back: the thrust levers at idle, the tiller straight and the parking brake set.
     */
    private void park(ServerLevel level) {
        for (BlockPos offset : controls) {
            BlockPos at = worldPosition.offset(offset);
            BlockState control = level.getBlockState(at);
            BlockState parked = control;
            if (control.getBlock() instanceof ThrustLeverBlock) {
                parked = control.setValue(ThrustLeverBlock.THRUST, 0);
            } else if (control.getBlock() instanceof TillerBlock) {
                parked = control.setValue(TillerBlock.STEER, TillerBlock.CENTRE);
            } else if (control.getBlock() instanceof ParkingBrakeBlock) {
                parked = control.setValue(ParkingBrakeBlock.SET, true);
            }
            if (parked != control) {
                level.setBlock(at, parked, Block.UPDATE_ALL);
            }
        }
    }

    /** Switches off: the jets go quiet, and the engines, the nose wheel and the brakes follow their controls again. */
    private void disengage() {
        phase = Phase.OFF;
        changed();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        for (Channel channel : Channel.values()) {
            drive(serverLevel, channel, 0);
        }
        resyncControls(serverLevel);
        serverLevel.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.0F, 0.6F);
    }

    /** The engines, the nose wheel and the brakes follow their controls again. */
    private void resyncControls(ServerLevel level) {
        for (BlockPos offset : controls) {
            if (level.getBlockEntity(worldPosition.offset(offset)) instanceof Control control) {
                control.resync();
            }
        }
    }

    private void drive(ServerLevel level, Channel channel, int power) {
        Relays.drive(level, worldPosition, relays.getOrDefault(channel, List.of()), power);
    }

    /** Saves, and sends what the screen shows to the clients. */
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag channels = new CompoundTag();
        relays.forEach((channel, offsets) -> channels.put(channel.name(), Relays.write(offsets)));
        tag.put("Relays", channels);
        tag.put("Controls", Relays.write(controls));
        saveTargets(tag);
        tag.putDouble("Trim", trim);
        tag.putDouble("ThrustTrim", thrustTrim);
        tag.putDouble("RunwayHeading", runwayHeading);
        tag.putInt("WheelsBelow", wheelsBelow);
        tag.putBoolean("Governing", governing);
    }

    /** What the screen shows: the phase, the modes and the targets. */
    private void saveTargets(CompoundTag tag) {
        tag.putString("Phase", phase.name());
        tag.putString("Lateral", lateral.name());
        tag.putDouble("TargetHeading", targetHeading);
        tag.putDouble("TargetAltitude", targetAltitude);
        tag.putDouble("TargetSpeed", targetSpeed);
        tag.putDouble("RunwayAltitude", runwayAltitude);
        tag.putDouble("OrbitX", orbitX);
        tag.putDouble("OrbitZ", orbitZ);
        tag.putDouble("OrbitRadius", orbitRadius);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // The update sent to clients carries only the targets; the full save also has the wiring.
        if (tag.contains("Relays")) {
            relays = new EnumMap<>(Channel.class);
            CompoundTag channels = tag.getCompound("Relays");
            for (Channel channel : Channel.values()) {
                relays.put(channel, Relays.read(channels.getList(channel.name(), Tag.TAG_INT_ARRAY)));
            }
            // Saved as "Levers" before the tiller and the parking brake joined them.
            controls = Relays.read(tag.getList(tag.contains("Controls") ? "Controls" : "Levers", Tag.TAG_INT_ARRAY));
            trim = tag.getDouble("Trim");
            // Saved before the signal that holds the speed was learnt.
            thrustTrim = tag.contains("ThrustTrim") ? tag.getDouble("ThrustTrim") : CRUISE_THRUST;
            // Saved before a take-off held the runway's heading apart from the one set.
            runwayHeading = tag.contains("RunwayHeading") ? tag.getDouble("RunwayHeading") : tag.getDouble("TargetHeading");
            wheelsBelow = tag.getInt("WheelsBelow");
            governing = tag.getBoolean("Governing");
        }
        phase = parse(Phase.class, tag.getString("Phase"), Phase.OFF);
        lateral = parse(Lateral.class, tag.getString("Lateral"), Lateral.HEADING);
        targetHeading = tag.getDouble("TargetHeading");
        // Saved before the targets were absolute: the height to climb to followed from where it took off.
        runwayAltitude = tag.contains("RunwayAltitude") ? tag.getDouble("RunwayAltitude") : tag.getDouble("StartAltitude");
        targetAltitude = tag.contains("TargetAltitude") ? tag.getDouble("TargetAltitude") : runwayAltitude + AerolargeConfig.AUTOPILOT_CLIMB.get();
        targetSpeed = tag.contains("TargetSpeed") ? tag.getDouble("TargetSpeed") : tag.getDouble("CruiseSpeed");
        orbitX = tag.getDouble("OrbitX");
        orbitZ = tag.getDouble("OrbitZ");
        orbitRadius = tag.contains("OrbitRadius") ? tag.getDouble("OrbitRadius") : DEFAULT_RADIUS;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveTargets(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
