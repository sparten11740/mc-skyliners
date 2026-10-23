package dev.aerolarge.block;

import dev.aerolarge.Aboard;
import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.FirstFlight;
import dev.aerolarge.cabin.Decompression;
import dev.aerolarge.cabin.Turbulence;
import dev.aerolarge.compat.SableFlight;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * The autopilot: flies the assembled airliner by setting its thrusters. The four engines push it along and, split
 * left against right, help turn it; jets on the tailplane pitch it, standing in for elevators, and jets at the wingtips
 * bank it, standing in for ailerons. It reads where the airliner is and how it moves from Sable each tick. With
 * {@code controlSurfaces} on in the server config, an experiment, it pitches and rolls with control surfaces instead,
 * once the airliner flies fast enough, and the jets only add what the surfaces cannot give (see
 * {@link ControlSurfaces}).
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
 * gently and brakes to a stop, then parks and hands back to those controls by itself. Set to fly to a pin and land
 * there, it looks for dry, level land near the pin with nothing in the way of its wings, and flies a proper approach to
 * it, down onto it along its centre line (see {@link PinLanding}).
 *
 * <p>Whether it is engaged or not, it gives the flight deck its callouts (see {@link Callouts}), and off it still
 * keeps the airliner safe: in the air its jets steady any quick pitching or rolling and push the nose and the wings
 * back once they go past {@link #PROTECT_CLIMB}, {@link #PROTECT_DIVE} or {@link #PROTECT_BANK} degrees, righting it
 * even from upside down; past {@link #MAX_SAFE_SPEED}, or climbing fast enough to coast past the top of the world, it
 * cuts the engines itself until the airliner has slowed and levelled, then hands them back to the levers. Nothing
 * else holds the airliner while it is flown on the thrust levers: at full thrust its engines would pitch it up until
 * it tumbled, or fling it off far faster than the world can be made around it.
 *
 * <p>Set to take off or to land, and once it has landed and stopped, it makes the cabin announcements (see
 * {@link CabinAnnouncements}); and air traffic control talks to the airliner (see {@link AtcRadio}), so set to take
 * off it holds the brakes until the tower has cleared it.
 *
 * <p>Stuck on the ground with its engines running, the airliner can be hopped free from the settings screen: for a
 * moment the control jets push it up off whatever holds it (see {@link Hop}), then go back to flying it.
 */
public class AutopilotBlockEntity extends BlockEntity {
    /** Everything the autopilot sets, each a group of relays next to thrusters. */
    public enum Channel {
        ENGINE_1, ENGINE_2, ENGINE_3, ENGINE_4, PITCH_UP, PITCH_DOWN, ROLL_LEFT, ROLL_RIGHT, STEER_LEFT, STEER_RIGHT, BRAKES;

        /**
         * Whether the clients are sent its relays' signal as it changes. Not the control jets': nothing on a client
         * reads them (a thruster takes its signal on the server and sends the clients its own state), and they change
         * often enough in flight that every client redrawing the tailplane and the wings each time would cost far more
         * than the change itself. The engines' are: an engine's core on the client spools up to its own signal.
         */
        boolean synced() {
            return this != PITCH_UP && this != PITCH_DOWN && this != ROLL_LEFT && this != ROLL_RIGHT;
        }
    }

    /**
     * What it is doing about height: off, taking off, climbing or descending to the height, holding it, coming down to
     * land, or braking to a stop once down.
     */
    public enum Phase {
        OFF, TAKEOFF, CLIMB, DESCENT, CRUISE, LAND, ROLLOUT
    }

    /**
     * What it is doing about direction: holding a heading, circling a point to the left or right, or flying to a
     * destination picked on the map and circling it to the right once there.
     */
    public enum Lateral {
        HEADING, ORBIT_LEFT, ORBIT_RIGHT, DESTINATION
    }

    /**
     * What the settings screen sets: the speed in blocks a second, the height to hold in world blocks, the heading as
     * a compass bearing (0 north, 90 east), whether to hold that heading, circle or fly to a destination, the circle's
     * radius in blocks, the destination (its x and z in the world; y unused), and whether to land near the destination
     * rather than circle it; whole numbers, as the screen shows them.
     */
    public record Settings(int speed, int altitude, int heading, Lateral lateral, int radius, BlockPos destination, boolean landAtPin) {
        public static final StreamCodec<FriendlyByteBuf, Settings> STREAM_CODEC = NeoForgeStreamCodecs.composite(
            ByteBufCodecs.VAR_INT, Settings::speed,
            ByteBufCodecs.VAR_INT, Settings::altitude,
            ByteBufCodecs.VAR_INT, Settings::heading,
            NeoForgeStreamCodecs.enumCodec(Lateral.class), Settings::lateral,
            ByteBufCodecs.VAR_INT, Settings::radius,
            BlockPos.STREAM_CODEC, Settings::destination,
            ByteBufCodecs.BOOL, Settings::landAtPin,
            Settings::new
        );

        /** Whether these settings fly to the pin to land near it. */
        public boolean landsAtPin() {
            return lateral == Lateral.DESTINATION && landAtPin;
        }
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
    static final double HEIGHT_GAIN = 0.2;
    static final double MAX_CLIMB_RATE = 8;
    static final double MAX_SINK_RATE = 6;
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
    /**
     * How fast the push the nose needs to stay where it is wanted is learnt, in signal a tick per degree it is off, and
     * the most that is learnt. Pushed back only as hard as it is off, the nose would rest off wherever the airliner
     * pitches by itself, as far off as it needs to be pushed back; and the trim, which only learns with the nose near
     * where it is wanted, might then never learn the climb at all, the airliner holding a height off the one set.
     */
    private static final double PITCH_HOLD_RATE = 0.01;
    private static final double PITCH_HOLD_LIMIT = 8;
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
    static final double MAX_BANK = 25;
    /**
     * The bank a circle is flown at, at most, in degrees: a circle set too tight to fly at this bank at the speed it
     * goes is flown wider, round the same middle, so there is bank to spare for steering back onto it and nobody aboard
     * is thrown about.
     */
    private static final double ORBIT_BANK = 20;
    /**
     * The most the speed it holds is changed by, in blocks a second every second: about a third of the pull of gravity,
     * what an airliner's passengers feel on the runway, so nobody standing is thrown down the cabin.
     */
    public static final double MAX_ACCELERATION = 3;
    /**
     * Engine signal split per degree off the heading, taken off per degree a second it turns faster than it should
     * (damping: without it the airliner swung to and fro about the heading, rocking its wings as it went, the faster
     * wing lifting the more), and the most it splits by.
     */
    private static final double YAW_GAIN = 0.4;
    private static final double YAW_DAMPING = 0.5;
    private static final int YAW_SPLIT = 4;
    /** Nose wheel steering signal per degree off the heading on the ground. */
    private static final double STEER_GAIN = 3;
    /** How hard an orbit steers back onto its circle: as a share of the way inward, per radius off it. */
    private static final double ORBIT_CAPTURE = 2;
    /**
     * Flying to a destination, further from it than this many times the circle's radius it heads for the circle on the
     * line that just touches it, so it arrives already going round it; nearer, it flies the circle.
     */
    private static final double APPROACH = 1.3;
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
    static final double LANDING_SINK_RATE = 3;
    static final double FLARE_GAIN = 0.2;
    static final double FLARE_HEIGHT = LANDING_SINK_RATE / FLARE_GAIN;
    static final double TOUCHDOWN_SINK = Callouts.BUTTER_SINK / 2;
    /**
     * The steepest bank it flies, in degrees per block above the ground, up to {@link #MAX_BANK}: low down, after
     * take-off or landing, its long wings must stay clear of the ground.
     */
    private static final double BANK_PER_BLOCK = 0.5;
    /**
     * Climbing out after take-off, it flies straight on along the runway, wings level, until this high above the
     * ground, or up at the height set where the ground below has risen to meet it; only then does it turn onto the
     * heading or circle set.
     */
    private static final double TURN_HEIGHT = 30;
    /** Slower than this, in blocks a second, it has stopped. */
    private static final double STOPPED = 0.5;
    /** How long the airliner flies on with nobody aboard before it lands by itself, in ticks: a minute. */
    private static final int UNATTENDED = 60 * 20;
    /** How often it looks whether anyone is aboard, in ticks. */
    private static final int ABOARD_LOOK = 20;
    /** How far from the autopilot it is heard, in blocks: the flight deck and the cabin just behind it. */
    static final double FLIGHT_DECK = 24;
    /**
     * The autopilots loaded, so an announcement panel put up by hand finds its airliner's, and both sides tell an
     * airliner's own blocks (see {@link #framing}).
     */
    private static final Loaded<AutopilotBlockEntity> LOADED = new Loaded<>(AutopilotBlockEntity.class, true);
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
    private static final Channel[] ENGINES = {Channel.ENGINE_1, Channel.ENGINE_2, Channel.ENGINE_3, Channel.ENGINE_4};

    private Map<Channel, List<BlockPos>> relays = new EnumMap<>(Channel.class);
    /** The flight deck controls (thrust levers, tiller, parking brake) it hands back to when it lets go. */
    private List<BlockPos> controls = List.of();
    /**
     * Two opposite corners of the box round its airliner's blocks, as offsets from it in the world's directions, as
     * its blueprint built it; null for one built before. What tells the airliner's own blocks from the same blocks
     * anywhere else (see {@link #framing}).
     */
    @Nullable
    private BlockPos frameFrom;
    @Nullable
    private BlockPos frameTo;
    /** Whether its links have been looked at since it was loaded, to mend them (see {@link Turning#mend}). */
    private boolean linksChecked;
    private Phase phase = Phase.OFF;
    private Lateral lateral = Lateral.HEADING;
    /** The heading to hold, as Minecraft yaw; the height to hold, in world blocks; the speed, in blocks a second. */
    private double targetHeading;
    private double targetAltitude;
    private double targetSpeed;
    /** Where it took off from: the lowest height it can be set to hold is measured from it. */
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
    /** The push the nose needs to stay where it is wanted, in signal, learnt as it flies (see {@link #PITCH_HOLD_RATE}). */
    private double pitchHold;
    private double lastPitch;
    private double lastRoll;
    private double lastSpeed;
    private double lastClimb;
    private double lastHeading;
    private int slowTicks;
    /**
     * The speed the engines are set to hold this tick: the speed set, reached from the speed it was going at no faster
     * than {@link #MAX_ACCELERATION}.
     */
    private double speedCommand;
    /** Whether it is still climbing out after take-off, below {@link #TURN_HEIGHT}, holding the runway's heading. */
    private boolean climbingOut;
    /** Whether last tick's readings are real; false after loading, so the first tick does not see a jump. */
    private boolean primed;
    /** How far its block stands above the bottom of the wheels, for the radio altitude. */
    private int wheelsBelow;
    /** Whether the protection has taken the engines from the levers, to slow an airliner going too fast or too high. */
    private boolean governing;
    /** Whether, set to take off, it still holds the brakes for the tower's clearance (see {@link AtcRadio#clearing}). */
    private boolean awaitingClearance;
    /**
     * Whether it has been in the air since it last arrived: come down and stopped after that, however it was flown, it
     * has arrived (see {@link #watchArrival}).
     */
    private boolean flown;
    /** How long it has flown with nobody aboard, in ticks (see {@link #watchUnattended}). */
    private int unattended;
    private final Airtime airtime = new Airtime();
    private final Callouts callouts = new Callouts(airtime);
    private final CabinAnnouncements announcements = new CabinAnnouncements(airtime);
    private final AtcRadio atc = new AtcRadio(airtime, callouts);
    private final Turbulence turbulence = new Turbulence();
    /** The airstairs and slides it has put away before taking off, which it holds the brakes for till they are. */
    private final Stowing stowing = new Stowing();
    /** Hopping the airliner free when it is stuck. */
    private final Hop hop = new Hop();
    /** The control surfaces it pitches and rolls with, switched on, beside the jets. */
    private final ControlSurfaces surfaces = new ControlSurfaces();
    /** Whether the settings screen offers to hop the airliner free: found on the server each tick, sent to the clients. */
    private boolean stuck;
    /** Whether, flying to a pin, it lands near it rather than circling it (see {@link PinLanding}). */
    private boolean landAtPin;
    /** Landing near the pin: the land surveyed, the runway found and the approach to it. */
    private final PinLanding pinLanding = new PinLanding();

    public AutopilotBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.AUTOPILOT_ENTITY.get(), pos, state);
    }

    /** Hands the autopilot the relays it drives and the controls it gives the airliner back to. */
    public void configure(Map<Channel, List<BlockPos>> relays, List<BlockPos> controls, int wheelsBelow) {
        this.wheelsBelow = wheelsBelow;
        this.relays = new EnumMap<>(Channel.class);
        this.relays.putAll(relays);
        this.controls = List.copyOf(controls);
        linksChecked = true;
        setChanged();
    }

    /** Wired when the airliner is built: the corners of the box round its blocks, as offsets from the autopilot. */
    public void frame(BlockPos from, BlockPos to) {
        frameFrom = from;
        frameTo = to;
        setChanged();
    }

    /**
     * Wired when the airliner is built: the lifting blocks its control surfaces turn over, by the way they turn the
     * airliner, each in the order they are turned over, as offsets from the autopilot (see {@link ControlSurfaces}).
     */
    public void wireSurfaces(Map<Channel, List<BlockPos>> blocks) {
        surfaces.wire(blocks);
        setChanged();
    }

    /**
     * Whether a block lies in the box round the blocks of an airliner whose autopilot is loaded, on either side: in the
     * world as its blueprint built it, or among its blocks once assembled, where its box goes along with it.
     */
    public static boolean framing(Level level, BlockPos pos) {
        for (AutopilotBlockEntity autopilot : LOADED.in(level)) {
            if (autopilot.frameFrom != null && autopilot.frameTo != null
                && BoundingBox.fromCorners(autopilot.worldPosition.offset(autopilot.frameFrom), autopilot.worldPosition.offset(autopilot.frameTo)).isInside(pos)) {
                return true;
            }
        }
        return false;
    }

    public Phase phase() {
        return phase;
    }

    public Lateral lateral() {
        return lateral;
    }

    /** Whether, flying to a pin, it lands near it rather than circling it. */
    public boolean landAtPin() {
        return landAtPin;
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

    /** Whether the airliner is stuck with its engines running, and the jets ready to hop it free. */
    public boolean stuck() {
        return stuck;
    }

    /** The runway it has picked to land on near the pin, while it flies to land there; null for none. */
    @Nullable
    public Runway runway() {
        return pinLanding.runway();
    }

    /**
     * Whether a player can work the autopilot from where they are: within reach of it, give or take the few blocks a
     * container's screen stays open past reach, which takes in the flight deck's autopilot screen. With Sable, reach is
     * measured among an assembled airliner's blocks, so it holds however the airliner flies.
     */
    public boolean canBeWorkedBy(Player player) {
        return !player.isSpectator() && Container.stillValidBlockEntity(this, player);
    }

    /** The autopilot nearest to a position on the server, within {@link #FLIGHT_DECK} of it; null for none. */
    @Nullable
    static AutopilotBlockEntity nearest(Level level, BlockPos pos) {
        AutopilotBlockEntity nearest = null;
        for (AutopilotBlockEntity autopilot : LOADED.in(level)) {
            double distance = autopilot.getBlockPos().distSqr(pos);
            if (distance < FLIGHT_DECK * FLIGHT_DECK && (nearest == null || distance < nearest.getBlockPos().distSqr(pos))) {
                nearest = autopilot;
            }
        }
        return nearest;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED.remove(this);
    }

    /**
     * The announcement panel asks for a cabin announcement: made, or waiting its turn behind whatever is being said on
     * the flight deck, only if it stands ready (see {@link Announcement.Standing}); whether it was.
     */
    boolean ask(Announcement announcement) {
        if (level == null || announcements.standing(announcement, level.getGameTime()) != Announcement.Standing.READY) {
            return false;
        }
        announcements.announce(announcement, this);
        return true;
    }

    /** Where a cabin announcement stands now, for the announcement panel to show. */
    Announcement.Standing standing(Announcement announcement) {
        return level == null ? Announcement.Standing.READY : announcements.standing(announcement, level.getGameTime());
    }

    /**
     * The autopilot a player's settings screen asks for at a position: one standing there, already loaded, that they
     * can work; null for none. Checked loaded first, so that a position from a client never loads a chunk.
     */
    @Nullable
    public static AutopilotBlockEntity workedBy(ServerPlayer player, BlockPos pos) {
        Level level = player.level();
        if (!level.isLoaded(pos) || !level.getBlockState(pos).is(AerolargeRegistry.AUTOPILOT.get())) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof AutopilotBlockEntity autopilot && autopilot.canBeWorkedBy(player) ? autopilot : null;
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
            phase != Phase.OFF ? lateral : Lateral.HEADING, (int) Math.round(orbitRadius), BlockPos.containing(orbitX, 0, orbitZ), landAtPin);
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
        if (tooLowToGoAround(level, state, settings)) {
            return Component.translatable("message.aerolarge.autopilot.pin.too_low");
        }
        boolean engaging = phase == Phase.OFF;
        if (engaging) {
            engage(level, state);
            if (phase == Phase.TAKEOFF) {
                // Airstairs and slides down on the ground would hold it where it stands: they go away first.
                if (level instanceof ServerLevel server) {
                    stowing.start(server, worldPosition);
                }
                // Cleared by the tower first, if it talks; the cabin hears the take-off announcement as the brakes come off.
                awaitingClearance = atc.requestTakeOff(level.getGameTime());
                if (!awaitingClearance) {
                    announcements.announce(Announcement.TAKEOFF, this);
                }
            }
        } else if (phase == Phase.LAND) {
            // A go-around: it climbs away again, and the next tick finds whether to climb, descend or hold.
            phase = Phase.CLIMB;
            if (level instanceof ServerLevel serverLevel) {
                atc.goAround(serverLevel, this, state, radioAltitude(level, state));
            }
        } else if (pinLanding.onFinal() && level instanceof ServerLevel serverLevel) {
            // On final to a runway near the pin, not yet flaring: it goes around too, climbing away off the glide path.
            atc.goAround(serverLevel, this, state, radioAltitude(level, state));
        }
        // Whatever is set, a runway near the pin is given up; still set to land there, it looks for one afresh.
        pinLanding.abandon();
        landAtPin = settings.landAtPin();
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
        } else if (settings.lateral() == Lateral.DESTINATION) {
            orbitX = settings.destination().getX() + 0.5;
            orbitZ = settings.destination().getZ() + 0.5;
            lateral = Lateral.DESTINATION;
        } else if (settings.lateral() != lateral || resized) {
            startOrbit(state, settings.lateral());
        }
        changed();
        String said = !engaging ? "set" : phase == Phase.TAKEOFF ? "engaged" : "holding";
        return Component.translatable("message.aerolarge.autopilot." + said, Math.round(targetAltitude), Math.round(targetSpeed), modeSaid());
    }

    /** The mode it flies, as it says it: the heading, the circle and its radius, or the pin and what it does there. */
    private Component modeSaid() {
        if (lateral == Lateral.HEADING) {
            return Component.translatable("message.aerolarge.autopilot.mode.heading", compass(targetHeading));
        }
        if (lateral == Lateral.DESTINATION && landAtPin) {
            return Component.translatable("message.aerolarge.autopilot.mode.destination_land");
        }
        return Component.translatable("message.aerolarge.autopilot.mode." + lateral.name().toLowerCase(Locale.ROOT), Math.round(orbitRadius));
    }

    /**
     * Whether it is landing on the runway near the pin, below the height the flare starts at, and the settings would
     * stop it landing there: too low to go around then, it lands, and the settings are not taken. Settings that still
     * land there go around, as they do for the plain landing.
     */
    private boolean tooLowToGoAround(Level level, SableFlight.State state, Settings settings) {
        return phase == Phase.LAND && pinLanding.landing() && !settings.landsAtPin() && radioAltitude(level, state) < FLARE_HEIGHT;
    }

    /**
     * Lands the airliner ahead, engaging the autopilot if it is off. Out of any circle onto the heading it is on, it
     * comes down gently, slowing to the speed it flies nose level at, and flares over the ground to touch down softly;
     * then it idles the engines and brakes to a stop, parks and hands back to the flight deck (see {@link #fly}). Only
     * in the air.
     */
    private Component land(Level level, SableFlight.State state) {
        double radio = radioAltitude(level, state);
        if (radio <= AIRBORNE) {
            return Component.translatable("message.aerolarge.autopilot.on_ground");
        }
        if (phase == Phase.OFF) {
            engage(level, state);
        }
        if (phase != Phase.LAND && level instanceof ServerLevel serverLevel) {
            // The tower clears it to land, then the cabin hears the landing announcement.
            atc.approach(serverLevel, this, state, radio);
            announcements.announce(Announcement.LANDING, this);
        }
        phase = Phase.LAND;
        lateral = Lateral.HEADING;
        // Down ahead, wherever that is: any runway found near the pin is given up.
        pinLanding.abandon();
        targetHeading = state.heading();
        targetSpeed = approachSpeed();
        changed();
        return Component.translatable("message.aerolarge.autopilot.landing", compass(targetHeading));
    }

    /** The speed it starts the approach at: a little above the rotation speed, within the speeds it flies. */
    static double approachSpeed() {
        return Mth.clamp(AerolargeConfig.AUTOPILOT_ROTATE_SPEED.get() * APPROACH_MARGIN, MIN_SPEED, MAX_SPEED);
    }

    /**
     * Hops the airliner free from its settings screen, if it is stuck: with its engines running and its brakes off, once
     * it has stood still a moment wherever it rests, and not again till the jets have rested (see {@link Hop}). A
     * parking brake set is left set: holding the airliner is what it is for. Asked again while the jets fire, the
     * button still held, keeps them firing. Says what it does, or why it cannot; nothing while it keeps them firing.
     */
    @Nullable
    public Component unstick() {
        SableFlight.State state = SableFlight.read(this, nose());
        if (state == null || !(level instanceof ServerLevel serverLevel)) {
            return Component.translatable("message.aerolarge.autopilot.not_assembled");
        }
        if (hop.firing()) {
            hop.keep();
            return null;
        }
        Hop.Hold hold = hop.hold(stance(serverLevel, state));
        if (hold == Hop.Hold.NONE) {
            hop.start(serverLevel, worldPosition, state);
            offerHop(false);
        }
        return Component.translatable(hold.message());
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
        pitchHold = 0;
        climbingOut = phase == Phase.TAKEOFF;
        lastPitch = state.pitch();
        lastRoll = state.roll();
        lastSpeed = state.airspeed();
        lastClimb = state.velocity().y;
        lastHeading = state.heading();
        speedCommand = state.airspeed();
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
     * Each tick: a cabin announcement waiting for the one before it has its turn; and while the airliner is assembled,
     * the callouts watch it, rough air comes and goes, the autopilot flies it if engaged, or keeps it from tipping over
     * if not, and air traffic control talks to it.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, AutopilotBlockEntity autopilot) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        autopilot.announcements.tick(autopilot);
        SableFlight.State flight = SableFlight.read(autopilot, autopilot.nose());
        if (flight == null) {
            // Disassembled under it: nothing left to fly, nor to hop free, and no air rushing out of it.
            if (autopilot.phase != Phase.OFF) {
                autopilot.disengage();
            }
            Decompression.stop(autopilot);
            if (autopilot.hop.stop()) {
                autopilot.setJets(serverLevel, 0, 0);
            }
            autopilot.offerHop(false);
            // Its control surfaces stand as built again.
            autopilot.surfaces.rest(serverLevel, pos);
            return;
        }
        autopilot.callouts.watch(serverLevel, flight, autopilot.wheelsBelow, () -> Aboard.players(autopilot));
        double radio = autopilot.radioAltitude(serverLevel, flight);
        autopilot.turbulence.tick(serverLevel, autopilot, flight.position(), radio, flight.airspeed());
        Decompression.tick(serverLevel, autopilot, autopilot.nose(), flight, radio);
        VillageSpotter.tick(serverLevel, autopilot, flight.position());
        boolean clear = radio + flight.apex() - flight.position().y > AIRBORNE;
        // Before the jets are set: a burst ending now hands them back this tick.
        autopilot.hop.tick(flight, serverLevel.getGameTime());
        // Offered on while the jets fire, so the button stays under the finger holding it.
        Hop.Hold hold = autopilot.hop.watch(autopilot.stance(serverLevel, flight));
        autopilot.offerHop(hold == Hop.Hold.NONE || hold == Hop.Hold.FIRING);
        autopilot.watchUnattended(serverLevel, flight, radio);
        if (autopilot.flyingToLand()) {
            autopilot.followPin(serverLevel, flight, radio);
        }
        if (autopilot.phase != Phase.OFF) {
            autopilot.fly(serverLevel, flight);
        } else {
            autopilot.protect(serverLevel, flight);
        }
        autopilot.watchArrival(flight, clear);
        autopilot.atc.tick(serverLevel, autopilot, flight, radio, autopilot.phase);
    }

    /**
     * Lands the airliner by itself once it has flown on for {@link #UNATTENDED} ticks with nobody aboard, everyone gone
     * out of it in flight (by parachute, say), so it does not fly on for ever: as Land on the settings screen does,
     * engaging the autopilot if it is off. The count stops and starts over as soon as anyone is aboard, and while it is
     * on the ground or landing already. Looked at every {@link #ABOARD_LOOK} ticks.
     */
    private void watchUnattended(ServerLevel level, SableFlight.State flight, double radio) {
        if (radio <= AIRBORNE || phase == Phase.LAND || phase == Phase.ROLLOUT || pinLanding.runway() != null) {
            unattended = 0;
            return;
        }
        if (Math.floorMod(level.getGameTime() + worldPosition.asLong(), ABOARD_LOOK) != 0) {
            return;
        }
        unattended = Aboard.players(this).isEmpty() ? unattended + ABOARD_LOOK : 0;
        if (unattended >= UNATTENDED) {
            unattended = 0;
            land(level, flight);
        }
    }

    /**
     * Notes the airliner in the air, and once it is down again and stopped, however it was flown (the autopilot's own
     * landing says so itself as it parks), welcomes everyone to their destination and switches the seatbelt signs off.
     */
    private void watchArrival(SableFlight.State flight, boolean airborne) {
        if (airborne) {
            flown = true;
        } else if (flown && flight.velocity().length() < STOPPED) {
            arrive();
        }
    }

    /**
     * Whether it flies to the pin to land near it: set to, in the air (not landing yet), and done climbing out after
     * take-off.
     */
    private boolean flyingToLand() {
        boolean flying = phase == Phase.CLIMB || phase == Phase.DESCENT || phase == Phase.CRUISE;
        return flying && landAtPin && lateral == Lateral.DESTINATION && !climbingOut;
    }

    /** Whether landing near the pin flies it this tick: on its way there, or landing on the runway it found. */
    private boolean guided() {
        return flyingToLand() || pinLanding.landing() && (phase == Phase.LAND || phase == Phase.ROLLOUT);
    }

    /** The height of the bottom of the wheels in the world. */
    private double wheels(SableFlight.State state) {
        return state.position().y - 0.5 - wheelsBelow;
    }

    /**
     * Flying to the pin to land near it: surveys the land and looks for a runway (see {@link PinLanding}), and acts on
     * what that comes to: says a runway is found, or that there is nowhere clear to land; turning onto final, the tower
     * clears it to land and the cabin hears the landing announcement, as for the plain landing; then it flares and
     * lands, or goes around to try again.
     */
    private void followPin(ServerLevel level, SableFlight.State state, double radio) {
        pinLanding.aim(Mth.floor(orbitX), Mth.floor(orbitZ), orbitRadius);
        PinLanding.Event event = pinLanding.tick(level, worldPosition, nose(), state, radio, wheels(state), approachSpeed());
        switch (event) {
            case FOUND -> foundRunway(level, state);
            case NOWHERE -> tell(level, state.position(), Component.translatable("message.aerolarge.autopilot.pin.nowhere"));
            case FINAL -> turnFinal(level, state, radio);
            case FLARE -> flare();
            case MISSED -> goAroundAgain(level, state, radio);
            case NONE -> {
            }
        }
    }

    /** A runway found near the pin: it says where, and slows to the approach speed, to turn tighter on the way in. */
    private void foundRunway(ServerLevel level, SableFlight.State state) {
        Runway runway = pinLanding.runway();
        if (runway == null) {
            return;
        }
        targetSpeed = approachSpeed();
        long fromPin = Math.round(Math.hypot(runway.x() - orbitX, runway.z() - orbitZ));
        tell(level, state.position(), Component.translatable("message.aerolarge.autopilot.pin.found", fromPin, compass(runway.heading())));
        changed();
    }

    /** Turning onto final: cleared to land, the landing announcement, and the runway's heading to show as set. */
    private void turnFinal(ServerLevel level, SableFlight.State state, double radio) {
        Runway runway = pinLanding.runway();
        if (runway != null) {
            targetHeading = runway.heading();
        }
        atc.approach(level, this, state, radio);
        announcements.announce(Announcement.LANDING, this);
        changed();
    }

    /** At the flare's start, lined up: it lands, as the plain landing does from there. */
    private void flare() {
        phase = Phase.LAND;
        changed();
    }

    /** Not lined up, or too high, at the flare's start: it goes around, back to the start of final, and tries again. */
    private void goAroundAgain(ServerLevel level, SableFlight.State state, double radio) {
        atc.goAround(level, this, state, radio);
        tell(level, state.position(), Component.translatable("message.aerolarge.autopilot.pin.go_around"));
        changed();
    }

    /** Arrived: the arrival announcement, which switches the seatbelt signs off, and nothing more until it flies again. */
    private void arrive() {
        flown = false;
        announcements.announce(Announcement.ARRIVAL, this);
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
        double pitchCommand = 0;
        double rollCommand = 0;
        if (radioAltitude(level, state) > AIRBORNE) {
            double climbLimit = state.apex() > ceiling(level) ? 0 : PROTECT_CLIMB;
            double pitchBack = Mth.clamp(pitch, -PROTECT_DIVE, climbLimit) - pitch;
            double rollBack = Mth.clamp(roll, -PROTECT_BANK, PROTECT_BANK) - roll;
            pitchCommand = PITCH_GAIN * pitchBack - PITCH_DAMPING * pitchRate;
            rollCommand = ROLL_GAIN * rollBack - ROLL_DAMPING * rollRate;
        }
        driveJets(level, state, pitchCommand, rollCommand);

        double speed = state.airspeed();
        double apex = state.apex();
        if (speed > MAX_SAFE_SPEED || apex > ceiling(level)) {
            if (!governing) {
                governing = true;
                setChanged();
            }
            // Every tick, so a lever moved meanwhile does not get the engines back.
            for (Channel engine : ENGINES) {
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
            speedCommand = speed;
            primed = true;
        }
        boolean clearing = holdingForClearance(level.getGameTime());
        boolean holding = stowing.waiting(level) || clearing;
        if (holding) {
            // On the brakes, the speed held starts from where it stands once they come off.
            speedCommand = speed;
        } else {
            // The speed held moves toward the one set no faster than an airliner speeds up or slows down.
            speedCommand += Mth.clamp(targetSpeed - speedCommand, -MAX_ACCELERATION / 20, MAX_ACCELERATION / 20);
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
        // Held at the height set over high ground, it would never get that high above it.
        if (climbingOut && !takingOff && (radio >= TURN_HEIGHT || phase == Phase.CRUISE)) {
            climbingOut = false;
        }
        // Down the runway and climbing out it holds the heading it started on; the heading set is flown once it is
        // high enough to turn.
        double wanted = takingOff || climbingOut ? runwayHeading : targetHeading;
        // Landing near the pin, that steers, and sets the height or the climb, until it has stopped.
        PinLanding.Guide guide = guided() ? pinLanding.guide(state, radio, wheels(state), targetAltitude, onGround) : null;
        if (guide != null) {
            wanted = guide.heading();
        }
        double bank = 0;
        // The turn it should be making, in degrees a second (round the circle at this speed in an orbit, none on a
        // heading), and the turn it is making.
        double wantedTurn = 0;
        double turn = Mth.wrapDegrees(state.heading() - lastHeading) * 20;
        if (takingOff) {
            boolean accelerating = speed - lastSpeed > STILL_ACCELERATING / 20;
            slowTicks = accelerating || holding || speed < 2 ? 0 : slowTicks + 1;
            boolean rotate = !holding && (speed >= rotateSpeed || slowTicks > 40);
            targetPitch = rotate ? ROTATION_PITCH : 0;
            // Speeding up down the runway as fast as is comfortable, on to the speed it will climb at; idle while it
            // holds for the clearance.
            thrust = holding ? 0 : holdSpeed(speed, speedCommand);
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
            boolean onFinal = guide != null && guide.onFinal();
            double heightToGo = (guide != null ? guide.altitude() : targetAltitude) - state.position().y;
            double wantedClimb = wantedClimb(landing, guide, heightToGo, radio);
            double climbError = wantedClimb - climb;
            // Landing, the nose comes up no further than at rotation, so the tail stays clear of the ground.
            double maxPitch = landing ? ROTATION_PITCH : MAX_PITCH;
            targetPitch = Mth.clamp(trim + CLIMB_GAIN * climbError - CLIMB_DAMPING * climbRate, MIN_PITCH, maxPitch);
            boolean pinned = targetPitch <= MIN_PITCH || targetPitch >= maxPitch;
            if (!pinned && Math.abs(targetPitch - pitch) < TRIM_WINDOW) {
                trim = Mth.clamp(trim + TRIM_RATE * Mth.clamp(climbError, -TRIM_CLIMB_LIMIT, TRIM_CLIMB_LIMIT), MIN_PITCH, MAX_PITCH);
            }
            if ((landing || onFinal) && radio > FLARE_HEIGHT) {
                // Coming in, slower while the nose is below the way it goes and faster while above; flaring, it keeps
                // the speed it found. Down the glide path to a runway near the pin, it finds the speed there already.
                double horizontal = Math.max(1, Math.hypot(state.velocity().x, state.velocity().z));
                double attitude = pitch - Math.toDegrees(Math.atan2(climb, horizontal));
                targetSpeed = Mth.clamp(targetSpeed + APPROACH_SPEED_RATE * (attitude - APPROACH_ATTITUDE),
                    APPROACH_SLOWEST * rotateSpeed, MAX_SPEED);
            }
            thrust = holdSpeed(speed, speedCommand);
            if (onFinal) {
                phase = Phase.DESCENT;
            } else if (!landing) {
                phase = Math.abs(heightToGo) < LEVEL_OFF ? Phase.CRUISE : heightToGo > 0 ? Phase.CLIMB : Phase.DESCENT;
            } else if (radio < Callouts.ON_GROUND) {
                phase = Phase.ROLLOUT;
            }
            if (lateral != Lateral.HEADING && !climbingOut && guide == null) {
                double horizontal = Math.hypot(state.velocity().x, state.velocity().z);
                double gravity = Math.max(1, state.gravity());
                double tightest = horizontal * horizontal / (gravity * Math.tan(Math.toRadians(ORBIT_BANK)));
                double radius = Math.max(orbitRadius, tightest);
                if (approaching(state, radius)) {
                    wanted = approachHeading(state, radius);
                } else {
                    wanted = orbitHeading(state, radius);
                    // Banked into the circle from the start: the bank that turns at this speed round this radius.
                    bank = side() * Math.toDegrees(Math.atan(horizontal * horizontal / (gravity * radius)));
                    wantedTurn = side() * Math.toDegrees(horizontal / radius);
                }
            }
            // It banks less the lower it gets, to keep its long wings clear of the ground.
            double maxBank = Mth.clamp(BANK_PER_BLOCK * radio, 0, MAX_BANK);
            bank = Mth.clamp(bank + BANK_GAIN * Mth.wrapDegrees(wanted - state.heading()) - TURN_DAMPING * (turn - wantedTurn), -maxBank, maxBank);
        }

        // In the air the push the nose needs is learnt, kept right down to the touchdown: let go in the flare, a few
        // blocks up, the nose rose and the airliner floated along over the ground. On the ground the wheels hold it.
        double pitchError = targetPitch - pitch;
        pitchHold = onGround ? 0 : Mth.clamp(pitchHold + PITCH_HOLD_RATE * pitchError, -PITCH_HOLD_LIMIT, PITCH_HOLD_LIMIT);
        double pitchCommand = pitchHold + PITCH_GAIN * pitchError - PITCH_DAMPING * pitchRate;
        double rollCommand = ROLL_GAIN * (bank - roll) - ROLL_DAMPING * rollRate;
        boolean rolls = !onGround || radio > 0.5;
        driveJets(level, state, pitchCommand, rolls ? rollCommand : 0);

        // The brakes are off while it flies and on while it holds for the take-off clearance and once it has landed; on
        // the ground it also steers the nose wheel to hold the heading.
        drive(level, Channel.BRAKES, rollingOut || holding ? 15 : 0);
        double headingError = Mth.wrapDegrees(wanted - state.heading());
        int steer = onGround ? Mth.clamp((int) Math.round(STEER_GAIN * headingError), -15, 15) : 0;
        drive(level, Channel.STEER_RIGHT, Math.max(0, steer));
        drive(level, Channel.STEER_LEFT, Math.max(0, -steer));

        // Turning right means more thrust on the left engines; the heading grows turning right.
        int split = Mth.clamp((int) Math.round(YAW_GAIN * headingError - YAW_DAMPING * (turn - wantedTurn)), -YAW_SPLIT, YAW_SPLIT);
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
            // Stopped after landing: parked, and handed back to the flight deck; on a laid runway, on the numbers.
            if (pinLanding.laid()) {
                Aboard.players(this).forEach(FirstFlight.ON_THE_NUMBERS::award);
            }
            park(level);
            disengage();
            tell(level, state.position(), Component.translatable("message.aerolarge.autopilot.landed"));
            arrive();
            return;
        }
        if (phase != was) {
            changed();
        } else {
            setChanged();
        }
    }

    /**
     * The rate to climb at, in blocks a second, in the air (not taking off or rolling out). Landing, it sinks gently,
     * and ever more gently the nearer the ground below: the flare; on a runway near the pin, flared over whichever is
     * nearer, the ground below or the runway, so it does not sink below the runway over a dip before it. Down the glide
     * path to such a runway, the rate the path asks for; else the rate the height still to go asks for.
     */
    private static double wantedClimb(boolean landing, @Nullable PinLanding.Guide guide, double heightToGo, double radio) {
        if (landing) {
            double above = guide != null ? Math.min(radio, guide.aboveRunway()) : radio;
            return -Mth.clamp(FLARE_GAIN * above, TOUCHDOWN_SINK, LANDING_SINK_RATE);
        }
        if (guide != null && !Double.isNaN(guide.climb())) {
            return guide.climb();
        }
        return Mth.clamp(HEIGHT_GAIN * heightToGo, -MAX_SINK_RATE, MAX_CLIMB_RATE);
    }

    /**
     * Whether, set to take off, it still holds the brakes for the tower's clearance; once the tower has given it, or
     * has had to leave it out, the cabin hears the take-off announcement as the brakes come off.
     */
    private boolean holdingForClearance(long now) {
        if (awaitingClearance && !atc.clearing(now)) {
            awaitingClearance = false;
            announcements.announce(Announcement.TAKEOFF, this);
        }
        return awaitingClearance;
    }

    /** Which way it circles: 1 to the right (clockwise seen from above, as a destination is circled), -1 to the left. */
    private double side() {
        return lateral == Lateral.ORBIT_LEFT ? -1 : 1;
    }

    /** Whether it is flying to a destination still far enough off to head for its circle rather than fly it. */
    private boolean approaching(SableFlight.State state, double radius) {
        return lateral == Lateral.DESTINATION
            && Math.hypot(orbitX - state.position().x, orbitZ - state.position().z) > radius * APPROACH;
    }

    /**
     * The heading to a destination's circle: along the line from here that just touches the circle with the middle on
     * its right, so it comes onto the circle already going round it clockwise, with no sharp turn.
     */
    private double approachHeading(SableFlight.State state, double radius) {
        double dx = orbitX - state.position().x;
        double dz = orbitZ - state.position().z;
        double distance = Math.hypot(dx, dz);
        double toMiddle = Math.toDegrees(Math.atan2(-dx, dz));
        return toMiddle - Math.toDegrees(Math.asin(Math.min(1, radius / distance)));
    }

    /**
     * The heading that keeps an orbit on a circle of this radius round its middle: along the circle the way it turns,
     * steered in while outside it and out while inside, more the further off it is.
     */
    private double orbitHeading(SableFlight.State state, double radius) {
        double dx = state.position().x - orbitX;
        double dz = state.position().z - orbitZ;
        double distance = Math.max(1, Math.hypot(dx, dz));
        double side = side();
        // Out from the middle, and along the circle: a right orbit runs clockwise seen from above.
        double outX = dx / distance;
        double outZ = dz / distance;
        double alongX = -outZ * side;
        double alongZ = outX * side;
        double inward = Mth.clamp(ORBIT_CAPTURE * (distance - radius) / radius, -1, 1);
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

    /**
     * Sets the control jets to pitch and roll the airliner as asked, in signal up and to the right, the control
     * surfaces taking what they can of it if switched on (see {@link ControlSurfaces}); while it hops free, the jets as
     * the hop needs them instead, the surfaces standing as built.
     */
    private void driveJets(ServerLevel level, SableFlight.State state, double pitch, double roll) {
        if (hop.firing()) {
            surfaces.rest(level, worldPosition);
            hopJets(level, state);
            return;
        }
        ControlSurfaces.Jets jets = surfaces.fly(level, worldPosition, nose(), state, pitch, roll, upset(state));
        setJets(level, jets.pitch(), jets.roll());
    }

    /**
     * Whether the airliner is past the attitude it is kept within with the autopilot off: then, whatever the control
     * surfaces could do, the jets right it, as they always have.
     */
    private static boolean upset(SableFlight.State state) {
        double pitch = state.pitch();
        return pitch > PROTECT_CLIMB || pitch < -PROTECT_DIVE || Math.abs(state.roll()) > PROTECT_BANK;
    }

    private void setJets(ServerLevel level, int pitch, int roll) {
        drive(level, Channel.PITCH_UP, Mth.clamp(pitch, 0, 15));
        drive(level, Channel.PITCH_DOWN, Mth.clamp(-pitch, 0, 15));
        drive(level, Channel.ROLL_RIGHT, Mth.clamp(roll, 0, 15));
        drive(level, Channel.ROLL_LEFT, Mth.clamp(-roll, 0, 15));
    }

    /**
     * The control jets for a hop: those under the wings at full and those over them quiet, so both wings lift alike;
     * the tailplane's as the hop holds the nose. A jet under a wing rolls the airliner away from it, so rolling left
     * fires the right wing's jets under it, and rolling right the left wing's.
     */
    private void hopJets(ServerLevel level, SableFlight.State state) {
        Direction right = nose().getClockWise();
        for (Channel roll : new Channel[]{Channel.ROLL_LEFT, Channel.ROLL_RIGHT}) {
            int under = roll == Channel.ROLL_LEFT ? 1 : -1;
            Map<Boolean, List<BlockPos>> lifting = relays.getOrDefault(roll, List.of()).stream()
                .collect(Collectors.partitioningBy(relay -> Integer.signum(relay.getX() * right.getStepX() + relay.getZ() * right.getStepZ()) == under));
            Relays.drive(level, worldPosition, lifting.get(true), 15, roll.synced());
            Relays.drive(level, worldPosition, lifting.get(false), 0, roll.synced());
        }
        int tail = hop.tail(state.pitch());
        drive(level, Channel.PITCH_DOWN, Mth.clamp(tail, 0, 15));
        drive(level, Channel.PITCH_UP, Mth.clamp(-tail, 0, 15));
    }

    /**
     * How the airliner stands for hopping it free: how fast it goes, and whether its engines are running and its brakes
     * on, whoever set them: the thrust levers and the parking brake, or the autopilot. Not how high it is over the
     * ground: stuck on trees, whose leaves the radio altitude looks through, it would seem to be flying.
     */
    private Hop.Stance stance(ServerLevel level, SableFlight.State state) {
        return new Hop.Stance(level.getGameTime(), state.velocity().length(), powered(level, ENGINES), powered(level, Channel.BRAKES));
    }

    /** Whether any relay of the channels gives off a signal. */
    private boolean powered(Level level, Channel... channels) {
        for (Channel channel : channels) {
            for (BlockPos offset : relays.getOrDefault(channel, List.of())) {
                BlockState relay = level.getBlockState(worldPosition.offset(offset));
                if (relay.getBlock() instanceof ThrustRelayBlock && relay.getValue(ThrustRelayBlock.POWER) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Offers to hop the airliner free on the settings screen, or stops offering it, telling the clients when that changes. */
    private void offerHop(boolean offered) {
        if (offered != stuck) {
            stuck = offered;
            changed();
        }
    }

    /** A heading as a compass bearing, 0 north and 90 east, three digits. */
    public static String compass(double heading) {
        return String.format(Locale.ROOT, "%03d", bearing(heading));
    }

    /** A heading (Minecraft's yaw) as a compass bearing in whole degrees, 0 north and 90 east. */
    public static int bearing(double heading) {
        return Math.floorMod(Math.round(heading) + 180, 360);
    }

    /**
     * Tells everyone near the flight deck something in chat. How it flies from moment to moment is the pilots' HUD's to
     * show, on their own screens (see {@code PilotHud}).
     */
    private static void tell(ServerLevel level, Vec3 flightDeck, Component message) {
        level.players().stream()
            .filter(player -> player.position().distanceToSqr(flightDeck) < FLIGHT_DECK * FLIGHT_DECK)
            .forEach(player -> player.displayClientMessage(message, false));
    }

    /**
     * Sets the flight deck's controls as for a parked airliner, so that it stays where it stopped once they have it
     * back: the thrust levers at idle, the tiller straight and the parking brake set.
     */
    private void park(ServerLevel level) {
        mendLinks(level);
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
        awaitingClearance = false;
        pinLanding.abandon();
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
        mendLinks(level);
        for (BlockPos offset : controls) {
            if (level.getBlockEntity(worldPosition.offset(offset)) instanceof Control control) {
                control.resync();
            }
        }
    }

    private void drive(ServerLevel level, Channel channel, int power) {
        mendLinks(level);
        Relays.drive(level, worldPosition, relays.getOrDefault(channel, List.of()), power, channel.synced());
    }

    /** Once after loading, as it first works its relays or the controls, mends its links to them if they lead nowhere. */
    private void mendLinks(ServerLevel level) {
        if (!linksChecked) {
            linksChecked = true;
            List<BlockPos> links = Stream.concat(relays.values().stream().flatMap(List::stream), controls.stream()).toList();
            Turning.mend(level, worldPosition, links, AutopilotBlockEntity::linked, this::turnLinks);
        }
    }

    /** Whether a link leads where it should: to a relay by the thrusters, or to one of the controls it hands back to. */
    private static boolean linked(BlockState state) {
        Block block = state.getBlock();
        return Relays.isRelay(state) || block instanceof ThrustLeverBlock || block instanceof TillerBlock || block instanceof ParkingBrakeBlock;
    }

    /** Turns its links to the relays, the controls and the control surfaces, mending them. */
    private void turnLinks(Rotation turn) {
        relays.replaceAll((channel, offsets) -> Turning.turn(offsets, turn));
        controls = Turning.turn(controls, turn);
        surfaces.turn(turn);
        setChanged();
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
        surfaces.save(tag);
        saveFrame(tag);
        Turning.save(tag, getBlockState());
        saveTargets(tag);
        tag.putDouble("Trim", trim);
        tag.putDouble("ThrustTrim", thrustTrim);
        tag.putDouble("PitchHold", pitchHold);
        tag.putDouble("RunwayHeading", runwayHeading);
        tag.putInt("WheelsBelow", wheelsBelow);
        tag.putBoolean("Governing", governing);
        tag.putBoolean("ClimbingOut", climbingOut);
        tag.putBoolean("AwaitingClearance", awaitingClearance);
        tag.putBoolean("Flown", flown);
    }

    /**
     * What the screen shows: the phase, the modes and the targets; whether it lands at the pin, and the runway it has
     * found there and how far along the approach to it it is, which the map shows.
     */
    private void saveTargets(CompoundTag tag) {
        tag.putBoolean("LandAtPin", landAtPin);
        Runway runway = pinLanding.runway();
        if (runway != null) {
            runway.save(tag, "Runway");
            tag.putBoolean("LaidRunway", pinLanding.laid());
            tag.putString("Approach", pinLanding.stage().name());
        }
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
            Rotation turn = Turning.since(tag, getBlockState());
            relays = new EnumMap<>(Channel.class);
            CompoundTag channels = tag.getCompound("Relays");
            for (Channel channel : Channel.values()) {
                relays.put(channel, Turning.turn(Relays.read(channels.getList(channel.name(), Tag.TAG_INT_ARRAY)), turn));
            }
            // Saved as "Levers" before the tiller and the parking brake joined them.
            controls = Turning.turn(Relays.read(tag.getList(tag.contains("Controls") ? "Controls" : "Levers", Tag.TAG_INT_ARRAY)), turn);
            surfaces.load(tag, turn);
            trim = tag.getDouble("Trim");
            // Saved before the signal that holds the speed was learnt.
            thrustTrim = tag.contains("ThrustTrim") ? tag.getDouble("ThrustTrim") : CRUISE_THRUST;
            // Saved before the push the nose needs was learnt: none, learnt afresh in a few seconds.
            pitchHold = tag.getDouble("PitchHold");
            // Saved before a take-off held the runway's heading apart from the one set.
            runwayHeading = tag.contains("RunwayHeading") ? tag.getDouble("RunwayHeading") : tag.getDouble("TargetHeading");
            wheelsBelow = tag.getInt("WheelsBelow");
            governing = tag.getBoolean("Governing");
            climbingOut = tag.getBoolean("ClimbingOut");
            // Held for a clearance the tower, not saved, has forgotten: it goes on at once, with the announcement.
            awaitingClearance = tag.getBoolean("AwaitingClearance");
            flown = tag.getBoolean("Flown");
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
        // Saved before it could land at the pin: it circles it.
        landAtPin = tag.getBoolean("LandAtPin");
        loadRunway(tag);
        // Only ever in the update sent to clients: the server finds it afresh each tick.
        stuck = tag.getBoolean("Stuck");
        loadFrame(tag);
    }

    /** The box round the airliner's blocks, saved and sent to clients alike, so both tell its blocks the same way. */
    private void saveFrame(CompoundTag tag) {
        if (frameFrom != null && frameTo != null) {
            tag.putLong("FrameFrom", frameFrom.asLong());
            tag.putLong("FrameTo", frameTo.asLong());
        }
    }

    /** Turned as its block has turned since it was saved, as its other links are (see {@link Turning}). */
    private void loadFrame(CompoundTag tag) {
        if (!tag.contains("FrameFrom")) {
            return;
        }
        Rotation turn = Turning.since(tag, getBlockState());
        frameFrom = Turning.turn(BlockPos.of(tag.getLong("FrameFrom")), turn);
        frameTo = Turning.turn(BlockPos.of(tag.getLong("FrameTo")), turn);
    }

    /**
     * The runway near the pin and the stage of the approach to it, as saved or sent: taken up again where it was, or,
     * with none, any runway given up (the survey of the land is not saved: it is seen again as the airliner flies).
     */
    private void loadRunway(CompoundTag tag) {
        Runway runway = Runway.load(tag, "Runway");
        if (runway == null) {
            pinLanding.abandon();
            return;
        }
        PinLanding.Stage stage = parse(PinLanding.Stage.class, tag.getString("Approach"), PinLanding.Stage.ENTRY);
        pinLanding.resume(runway, tag.getBoolean("LaidRunway"), stage, Mth.floor(orbitX), Mth.floor(orbitZ));
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
        tag.putBoolean("Stuck", stuck);
        saveFrame(tag);
        Turning.save(tag, getBlockState());
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
