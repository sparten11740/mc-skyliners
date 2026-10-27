package dev.aerolarge.autopilot;

import dev.aerolarge.Aboard;
import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.FirstFlight;
import dev.aerolarge.airframe.Livery;
import dev.aerolarge.block.Announcement;
import dev.aerolarge.block.AutopilotBlock;
import dev.aerolarge.block.Control;
import dev.aerolarge.block.GearBayBlockEntity;
import dev.aerolarge.block.GearLeverBlockEntity;
import dev.aerolarge.block.Loaded;
import dev.aerolarge.block.ParkingBrakeBlock;
import dev.aerolarge.block.PilotSeatBlock;
import dev.aerolarge.block.Relays;
import dev.aerolarge.block.Stowing;
import dev.aerolarge.block.ThrustLeverBlock;
import dev.aerolarge.block.ThrustRelayBlock;
import dev.aerolarge.block.TillerBlock;
import dev.aerolarge.block.Turning;
import dev.aerolarge.block.VillageSpotter;
import dev.aerolarge.cabin.AirPockets;
import dev.aerolarge.cabin.Decompression;
import dev.aerolarge.cabin.LightningStrikes;
import dev.aerolarge.cabin.Turbulence;
import dev.aerolarge.compat.PhysicsAssembler;
import dev.aerolarge.compat.SableFlight;
import dev.aerolarge.parachute.SafetyCanopy;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
     * How tightly it circles: as steeply as it ever banks round a circle, or gently, at half that bank, about twice as
     * wide. The circle's size follows from the bank and the speed set, as a pilot's does, so the two always differ in
     * the sky: a circle set in blocks would be flown no tighter than the bank allows, and at the speeds it cruises at
     * most sizes would come out the same.
     */
    public enum CircleSize {
        TIGHT(ORBIT_BANK), GENTLE(ORBIT_BANK / 2);

        /** The bank it circles at, in degrees. */
        public final double bank;

        CircleSize(double bank) {
            this.bank = bank;
        }

        /**
         * The circle's radius, in blocks, at a speed in blocks a second where gravity pulls this hard, in blocks a
         * second each second: the circle that bank turns it round. A gentle circle is kept from growing past
         * {@link #GENTLE_WIDEST}, where the tight one leaves room, so it still comes round in a few minutes; it is
         * always a good share wider than the tight one.
         */
        public double radius(double speed, double gravity) {
            double tight = turning(speed, gravity, TIGHT.bank);
            if (this == TIGHT) {
                return tight;
            }
            return Math.max(tight * GENTLE_WIDER, Math.min(turning(speed, gravity, bank), GENTLE_WIDEST));
        }

        /** The radius a bank in degrees turns at a speed round, no tighter than the tightest circle it flies. */
        private static double turning(double speed, double gravity, double bank) {
            return Math.max(MIN_RADIUS, speed * speed / (Math.max(1, gravity) * Math.tan(Math.toRadians(bank))));
        }
    }

    /**
     * Its controls on the flight deck's sloped touch panel, under the middle screen (see {@link #press}): the on/off
     * key, the speedometer, the steering fan and the sky, which are set by where they are touched, and the five turns
     * along the bottom of the steering, tight and gentle either way round a circle and straight on between them.
     */
    public enum Button {
        ENGAGE, SPEED, HEADING, ALTITUDE, TIGHT_LEFT(Lateral.ORBIT_LEFT, CircleSize.TIGHT), GENTLE_LEFT(Lateral.ORBIT_LEFT, CircleSize.GENTLE),
        STRAIGHT(Lateral.HEADING, CircleSize.TIGHT), GENTLE_RIGHT(Lateral.ORBIT_RIGHT, CircleSize.GENTLE), TIGHT_RIGHT(Lateral.ORBIT_RIGHT, CircleSize.TIGHT);

        /**
         * For a turn, the way it flies and how tightly it circles (straight on, holding a heading); null for the rest.
         */
        @Nullable
        public final Lateral way;
        @Nullable
        public final CircleSize size;

        Button() {
            this(null, null);
        }

        Button(@Nullable Lateral way, @Nullable CircleSize size) {
            this.way = way;
            this.size = size;
        }

        /** Whether it is a turn, and the one that flying this way, this tightly, is: straight on holds any heading. */
        public boolean flies(Lateral lateral, CircleSize circle) {
            return way != null && way == lateral && (way == Lateral.HEADING || size == circle);
        }
    }

    /**
     * What the settings screen sets: the speed in blocks a second, the height to hold in world blocks, the heading as
     * a compass bearing (0 north, 90 east), whether to hold that heading, circle or fly to a destination, how tightly
     * it circles, the destination (its x and z in the world; y unused), and whether to land near the destination
     * rather than circle it; whole numbers, as the screen shows them.
     */
    public record Settings(int speed, int altitude, int heading, Lateral lateral, CircleSize circle, BlockPos destination, boolean landAtPin) {
        public static final StreamCodec<FriendlyByteBuf, Settings> STREAM_CODEC = NeoForgeStreamCodecs.composite(
            ByteBufCodecs.VAR_INT, Settings::speed,
            ByteBufCodecs.VAR_INT, Settings::altitude,
            ByteBufCodecs.VAR_INT, Settings::heading,
            NeoForgeStreamCodecs.enumCodec(Lateral.class), Settings::lateral,
            NeoForgeStreamCodecs.enumCodec(CircleSize.class), Settings::circle,
            BlockPos.STREAM_CODEC, Settings::destination,
            ByteBufCodecs.BOOL, Settings::landAtPin,
            Settings::new
        );

        Settings withSpeed(int to) {
            return new Settings(to, altitude, heading, lateral, circle, destination, landAtPin);
        }

        Settings withAltitude(int to) {
            return new Settings(speed, to, heading, lateral, circle, destination, landAtPin);
        }

        /** Holding this heading, straight on. */
        Settings withHeading(int to) {
            return new Settings(speed, altitude, to, Lateral.HEADING, circle, destination, landAtPin);
        }

        /** Circling this way, this tightly. */
        Settings withCircle(Lateral way, CircleSize size) {
            return new Settings(speed, altitude, heading, way, size, destination, landAtPin);
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Speed", speed);
            tag.putInt("Altitude", altitude);
            tag.putInt("Heading", heading);
            tag.putString("Lateral", lateral.name());
            tag.putString("Circle", circle.name());
            tag.putLong("Destination", destination.asLong());
            tag.putBoolean("LandAtPin", landAtPin);
            return tag;
        }

        /** Saved with a radius in blocks before circles were tight or gentle: those circle tightly. */
        static Settings load(CompoundTag tag) {
            return new Settings(tag.getInt("Speed"), tag.getInt("Altitude"), tag.getInt("Heading"),
                parse(Lateral.class, tag.getString("Lateral"), Lateral.HEADING), parse(CircleSize.class, tag.getString("Circle"), CircleSize.TIGHT),
                BlockPos.of(tag.getLong("Destination")), tag.getBoolean("LandAtPin"));
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
     * Landing: the rate it sinks at lower down, in blocks a second; how much less it sinks per block nearer the ground,
     * from the height the flare starts at; and the rate it touches down at, half what a butter landing has to stay
     * under.
     */
    static final double LANDING_SINK_RATE = 3;
    static final double FLARE_GAIN = 0.2;
    static final double FLARE_HEIGHT = LANDING_SINK_RATE / FLARE_GAIN;
    static final double TOUCHDOWN_SINK = Callouts.BUTTER_SINK / 2;
    /**
     * Landing from high up: above the first height, in blocks above the ground, it sinks at the faster rate, in blocks
     * a second, easing to {@link #LANDING_SINK_RATE} by the second height, so that coming down from cruising height
     * does not take minutes of waiting; from there it sinks gently down to the flare. However it is to sink, it sinks
     * faster by no more than the last, in blocks a second every second, so the nose goes down gently rather than all
     * at once as it starts to land.
     */
    private static final double LANDING_STEEP_HEIGHT = 60;
    private static final double LANDING_SETTLE_HEIGHT = 30;
    private static final double LANDING_FAST_SINK = 4.5;
    private static final double LANDING_SINK_EASE = 0.5;
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
    /** Climbing away after take-off, it raises the gear once this high above the ground, in blocks. */
    private static final double GEAR_UP_HEIGHT = 10;
    /** Set to take off with its gear not down, the longest it holds the brakes for the gear to come down, in ticks. */
    private static final int GEAR_WAIT = 20 * 20;
    /**
     * How far either way from it, in blocks, an airliner built before the autopilot knew where its Physics Assembler
     * is has it looked for, once: the flight deck and a good way round it, as far as the assembler ever stood.
     */
    private static final int ASSEMBLER_SEARCH = 12;
    /**
     * How long after it set its Physics Assembler working it waits for the airliner to be assembled before it will try
     * again, in game ticks: Sable takes a moment to build the new airliner, and asked again meanwhile it would start a
     * second assembly over the first.
     */
    private static final int ASSEMBLY_WAIT = 10 * 20;
    /** Slower than this, in blocks a second, it has stopped. */
    private static final double STOPPED = 0.5;
    /** How long the airliner flies on with nobody aboard before it lands by itself, in ticks: a minute. */
    private static final int UNATTENDED = 60 * 20;
    /** How often it looks whether anyone is aboard, in ticks. */
    private static final int ABOARD_LOOK = 20;
    /** How often a flying zeppelin's autopilot shows what it does on the control car's hand controls, in ticks. */
    private static final int SHOW_CONTROLS = 20;
    /** How far from the autopilot it is heard, in blocks: the flight deck and the cabin just behind it. */
    public static final double FLIGHT_DECK = 24;
    /**
     * The autopilots loaded, so an announcement panel put up by hand finds its airliner's, and both sides tell an
     * airliner's own blocks (see {@link #framing}).
     */
    private static final Loaded<AutopilotBlockEntity> LOADED = new Loaded<>(AutopilotBlockEntity.class, true);
    /**
     * The steps the targets are set in by touch, on the flight deck's controls and the settings screen's: degrees of
     * heading, blocks of height and blocks a second. Typed into the screen, any whole number goes.
     */
    public static final int HEADING_STEP = 5;
    public static final int ALTITUDE_STEP = 10;
    public static final int SPEED_STEP = 5;
    /**
     * The speeds it flies, and the tightest circle it flies, in blocks; the height it holds is kept between
     * {@link #lowestAltitude} and {@link #highestAltitude}.
     */
    public static final int MIN_SPEED = 30;
    public static final int MAX_SPEED = 120;
    public static final int MIN_RADIUS = 150;
    /**
     * A gentle circle: the widest it grows to, in blocks, and the least it is wider than a tight one, as a share of
     * that (see {@link CircleSize#radius}).
     */
    private static final double GENTLE_WIDEST = 2500;
    private static final double GENTLE_WIDER = 1.3;
    private static final double DEFAULT_RADIUS = 600;
    /**
     * A zeppelin engaged on the ground rises this far above it, in blocks, and cruises at this speed, in blocks a
     * second.
     */
    private static final int ZEPPELIN_CLIMB = 60;
    private static final double ZEPPELIN_CRUISE = 15;
    /** How far above the runway the lowest height it can be set to hold is, in blocks. */
    private static final int LOWEST_ABOVE_RUNWAY = 40;
    /**
     * The note a touch on the flight deck's speedometer or sky sounds: its pitch at the slowest or lowest setting, and
     * at the fastest or highest; and the pitch of the thud a control gives with the autopilot off.
     */
    public static final float NOTE_LOW = 0.6F;
    public static final float NOTE_HIGH = 1.8F;
    private static final float THUD_PITCH = 0.6F;
    /** How loud the note, the whoosh of a turn and the thud are. */
    private static final float NOTE_VOLUME = 0.5F;
    private static final float WHOOSH_VOLUME = 0.5F;
    private static final float THUD_VOLUME = 0.8F;
    private static final Channel[] ENGINES = {Channel.ENGINE_1, Channel.ENGINE_2, Channel.ENGINE_3, Channel.ENGINE_4};
    /** How far a control jet's signal may wander, in steps of the 15, before the jet is set to it (see {@link #steady}). */
    private static final int JET_STEADY = 1;

    private Map<Channel, List<BlockPos>> relays = new EnumMap<>(Channel.class);
    /** The control jets' signals as last set, not saved: set afresh on the first tick after loading. */
    private final Map<Channel, Integer> jetSignals = new EnumMap<>(Channel.class);
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
    /** The landing gear lever it throws, as an offset from it; null for an airliner whose gear does not retract. */
    @Nullable
    private BlockPos gearLever;
    /** Whether it has raised the gear on this climb out already, so a pilot lowering it again is left to. */
    private boolean gearRaised;
    /** Set to take off, until when it holds the brakes for the gear to come down, in game ticks. */
    private long gearWaitUntil;
    /** The airliner's Physics Assembler, as an offset from it; null while not known (see {@link #assemblerAt}). */
    @Nullable
    private BlockPos assembler;
    /**
     * What to fly once aboard, asked for before the airliner was assembled: kept as it assembles and goes with the
     * autopilot into the new airliner's blocks, there to engage with on its first tick (see {@link #assembleToFly}).
     */
    @Nullable
    private Settings flyOnceAboard;
    /** When it set its Physics Assembler working for {@link #flyOnceAboard}, in game ticks; not saved. */
    private long assemblingSince = Long.MIN_VALUE;
    /** Who asked for {@link #flyOnceAboard}, to be told how it goes even before they count as aboard. */
    @Nullable
    private UUID flyOnceAboardFor;
    /**
     * The seat they asked from, as an offset from the autopilot: assembling takes the seat out from under them, so
     * once aboard they are sat down in it again; null if they stood.
     */
    @Nullable
    private BlockPos flyOnceAboardSeat;
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
    /**
     * The point an orbit circles, in world blocks, its radius, and how tightly it was set to circle, which the radius
     * follows from at the speed set (see {@link CircleSize#radius}).
     */
    private double orbitX;
    private double orbitZ;
    private double orbitRadius = DEFAULT_RADIUS;
    private CircleSize circle = CircleSize.TIGHT;
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
    /**
     * Landing, the rate it is set to sink at this tick, in blocks a second, eased into (see
     * {@link #LANDING_SINK_EASE}); NaN while it is not landing. Not saved: landing on after the world loads, it starts
     * from the rate it sinks at.
     */
    private double sinkCommand = Double.NaN;
    /** Whether it is still climbing out after take-off, below {@link #TURN_HEIGHT}, holding the runway's heading. */
    private boolean climbingOut;
    /** Whether last tick's readings are real; false after loading, so the first tick does not see a jump. */
    private boolean primed;
    /** How far its block stands above the bottom of the wheels, for the radio altitude. */
    private int wheelsBelow;
    /** Whether the protection has taken the engines from the levers, to slow an airliner going too fast or too high. */
    private boolean governing;
    /**
     * Whether the set parking brake holds the engines at idle, on the ground with the autopilot off (see
     * {@link #holdOnBrake}). Not saved: looked at every tick.
     */
    private boolean heldOnBrake;
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
    private final CabinAnnouncements announcements = new CabinAnnouncements(airtime, callouts);
    private final AtcRadio atc = new AtcRadio(airtime, callouts);
    private final Turbulence turbulence = new Turbulence();
    private final AirPockets airPockets = new AirPockets();
    private final LightningStrikes lightning = new LightningStrikes();
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
    /** What it flies; an autopilot saved before there were zeppelins flies an airliner. */
    private Craft craft = Craft.AIRLINER;
    /** How it flies a zeppelin, and what it works there; unused for an airliner. */
    private final ZeppelinPilot zeppelin = new ZeppelinPilot();
    /**
     * How its zeppelin is painted; while a repaint sweeps along it, how it was painted before, and the game time the
     * repaint began, -1 for none (see {@link Livery}).
     */
    private Livery livery = Livery.SILVER;
    private Livery repaintedFrom = Livery.SILVER;
    private long repaintStart = -1;
    /**
     * Whether it has seen to it that its zeppelin is kept loaded since it was loaded itself (see {@link #keepLoaded});
     * Sable keeps the ticket in the save meanwhile.
     */
    private boolean keptLoaded;

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

    /**
     * Wired when a zeppelin is built: it flies a zeppelin, its gas cells' burners and its engines' relays as offsets
     * from it, how far its hot air stands above it, how far aft of it the cells' middle is, and how far its block
     * stands above the bottom of its wheels.
     */
    public void configureZeppelin(List<ZeppelinPilot.Cell> cells, Map<Integer, List<BlockPos>> ahead, Map<Integer, List<BlockPos>> astern,
                                  double gasAbove, double middleAft, int wheelsBelow) {
        craft = Craft.ZEPPELIN;
        this.wheelsBelow = wheelsBelow;
        zeppelin.configure(cells, ahead, astern, gasAbove, middleAft);
        linksChecked = true;
        changed();
    }

    /** Wired when a zeppelin is built: its control car's hand controls, as offsets from it. */
    public void wireZeppelinControls(BlockPos rudder, BlockPos elevator, BlockPos port, BlockPos starboard, BlockPos lever) {
        zeppelin.wireControls(rudder, elevator, port, starboard, lever);
        setChanged();
    }

    /** What it flies, on the clients as on the server, whose pictures its screens show. */
    public Craft craft() {
        return craft;
    }

    /** How its zeppelin is painted, on the clients as on the server. */
    public Livery livery() {
        return livery;
    }

    /** How its zeppelin was painted before the repaint that began at {@link #repaintStart}. */
    public Livery repaintedFrom() {
        return repaintedFrom;
    }

    /** The game time the repaint sweeping along its zeppelin began, or -1 for none. */
    public long repaintStart() {
        return repaintStart;
    }

    /** Whether a repaint is still sweeping along its zeppelin. */
    private boolean repainting() {
        return repaintStart >= 0 && level != null && level.getGameTime() - repaintStart < Livery.REPAINT_TICKS;
    }

    /** Paints its zeppelin in a livery at once, as it is built in it: no repaint sweeps along it. */
    public void paint(Livery to) {
        livery = to;
        repaintedFrom = to;
        repaintStart = -1;
        changed();
    }

    /**
     * Has its zeppelin repainted, the new livery sweeping along it from the nose to the tail over
     * {@link Livery#REPAINT_TICKS}, over whatever it was painted in a moment ago. Painted as it is, nothing happens.
     */
    public void repaint(Livery to) {
        if (level == null || to == livery && !repainting()) {
            return;
        }
        repaintedFrom = livery;
        livery = to;
        repaintStart = level.getGameTime();
        changed();
    }

    /** How it is working a zeppelin, for play-testing; null for an airliner's. */
    @Nullable
    public ZeppelinPilot.Report zeppelinReport() {
        return craft == Craft.ZEPPELIN ? zeppelin.report() : null;
    }

    /** The autopilots loaded in a level, on either side. */
    public static List<AutopilotBlockEntity> loaded(Level level) {
        return LOADED.in(level);
    }

    /** Wired when the airliner is built: the landing gear lever, as an offset from it; null without retractable gear. */
    public void wireGear(@Nullable BlockPos lever) {
        gearLever = lever;
        setChanged();
    }

    /** Wired when the airliner is built: its Physics Assembler, as an offset from it; null without one. */
    public void wireAssembler(@Nullable BlockPos at) {
        assembler = at;
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
    public void wireSurfaces(Map<Channel, List<BlockPos>> blocks, List<ControlSurfaces.Panel> panels) {
        surfaces.wire(blocks, panels);
        changed();
    }

    /** The control surfaces, which the clients draw from here (see {@link ControlSurfaces#panels}). */
    public ControlSurfaces surfaces() {
        return surfaces;
    }

    /**
     * Whether a block lies in the box round the blocks of an airliner whose autopilot is loaded, on either side: in the
     * world as its blueprint built it, or among its blocks once assembled, where its box goes along with it.
     */
    public static boolean framing(Level level, BlockPos pos) {
        for (AutopilotBlockEntity autopilot : LOADED.in(level)) {
            BoundingBox frame = autopilot.frame();
            if (frame != null && frame.isInside(pos)) {
                return true;
            }
        }
        return false;
    }

    /** The box round the blocks its blueprint laid, where they stand now; null for an autopilot put down by hand. */
    @Nullable
    public BoundingBox frame() {
        return built() ? BoundingBox.fromCorners(worldPosition.offset(frameFrom), worldPosition.offset(frameTo)) : null;
    }

    public Phase phase() {
        return phase;
    }

    /**
     * Whether it is the autopilot of an airliner built from its blueprint, which knows its frame (see
     * {@link #frame}): not one put down by hand.
     */
    public boolean built() {
        return frameFrom != null && frameTo != null;
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

    public CircleSize circle() {
        return circle;
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

    /** Whether any of the airliner's engines is asked for thrust, by its thrust levers or by the autopilot. */
    public boolean enginesRunning() {
        return level != null && Stream.of(ENGINES).anyMatch(this::running);
    }

    /** Whether an engine is asked for thrust, as the first of its relays says: they all carry the same signal. */
    private boolean running(Channel engine) {
        List<BlockPos> offsets = relays.getOrDefault(engine, List.of());
        if (offsets.isEmpty()) {
            return false;
        }
        BlockState relay = level.getBlockState(worldPosition.offset(offsets.get(0)));
        return relay.hasProperty(ThrustRelayBlock.POWER) && relay.getValue(ThrustRelayBlock.POWER) > 0;
    }

    /** The zeppelin whose frame a block lies in, on either side, by her autopilot; null for none. */
    @Nullable
    public static AutopilotBlockEntity zeppelinFraming(Level level, BlockPos pos) {
        for (AutopilotBlockEntity autopilot : LOADED.in(level)) {
            BoundingBox frame = autopilot.craft() == Craft.ZEPPELIN ? autopilot.frame() : null;
            if (frame != null && frame.isInside(pos)) {
                return autopilot;
            }
        }
        return null;
    }

    /** The autopilot nearest to a position, on either side, within {@link #FLIGHT_DECK} of it; null for none. */
    @Nullable
    public static AutopilotBlockEntity nearest(Level level, BlockPos pos) {
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
    public boolean ask(Announcement announcement) {
        if (level == null || announcements.standing(announcement, level.getGameTime()) != Announcement.Standing.READY) {
            return false;
        }
        announcements.announce(announcement, this);
        return true;
    }

    /** Where a cabin announcement stands now, for the announcement panel to show. */
    public Announcement.Standing standing(Announcement announcement) {
        return level == null ? Announcement.Standing.READY : announcements.standing(announcement, level.getGameTime());
    }

    /**
     * Whether nothing is being said in its flight deck's hearing now, nor waits to be: no announcement, callout or
     * transmission.
     */
    public boolean quiet() {
        return level == null || airtime.free(level.getGameTime()) && !announcements.waiting();
    }

    /**
     * Something else aboard is said in its flight deck's hearing for so many ticks from now, as a zeppelin's ground
     * officer calls "Up ship!": the cabin announcements and air traffic control wait for it.
     */
    public void holdFloor(int ticks) {
        if (level != null) {
            airtime.take(level.getGameTime(), ticks);
        }
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
     * Fly asked for while the airliner is not assembled: its Physics Assembler assembles it, worked as a Create
     * deployer works it (see {@link PhysicsAssembler}), and the autopilot, aboard then, flies the settings on its first
     * tick there. Says what happens, or why it cannot; null when the airliner is assembled already, for {@link #set}.
     */
    @Nullable
    public Component assembleToFly(ServerPlayer player, Settings settings) {
        if (!(level instanceof ServerLevel server) || SableFlight.read(this, nose()) != null) {
            return null;
        }
        if (assembling()) {
            return Component.translatable("message.aerolarge.autopilot.still_assembling");
        }
        if (craft == Craft.ZEPPELIN) {
            return assembleZeppelin(server, player, settings);
        }
        BlockPos at = assemblerAt(server);
        if (at == null) {
            return Component.translatable("message.aerolarge.autopilot.not_assembled");
        }
        // Noted before it assembles, so the autopilot takes it along into the new airliner's blocks.
        assemblingSince = server.getGameTime();
        flyOnceAboard = settings;
        flyOnceAboardFor = player.getUUID();
        BlockPos seat = PilotSeatBlock.seatOf(player);
        flyOnceAboardSeat = seat == null ? null : seat.subtract(worldPosition);
        setChanged();
        Component failed = PhysicsAssembler.assemble(server, at, player);
        if (failed == null) {
            return Component.translatable("message.aerolarge.autopilot.assembling");
        }
        flyOnceAboard = null;
        flyOnceAboardFor = null;
        flyOnceAboardSeat = null;
        setChanged();
        return Component.translatable("message.aerolarge.autopilot.assembly_failed", failed);
    }

    /**
     * Fly asked for while the zeppelin is not assembled: assembled through Sable from the blocks in its frame (see
     * {@link ZeppelinAssembly}), too many for the Physics Assembler, the autopilot then flying the settings on its
     * first tick aboard as {@link #assembleToFly} does for an airliner.
     */
    private Component assembleZeppelin(ServerLevel server, ServerPlayer player, Settings settings) {
        BoundingBox frame = frame();
        if (frame == null || !SableFlight.installed()) {
            return Component.translatable("message.aerolarge.autopilot.not_assembled");
        }
        if (!zeppelin.wired()) {
            return Component.translatable("message.aerolarge.autopilot.no_burners");
        }
        assemblingSince = server.getGameTime();
        flyOnceAboard = settings;
        flyOnceAboardFor = player.getUUID();
        flyOnceAboardSeat = null;
        setChanged();
        if (SableFlight.assemble(server, worldPosition, ZeppelinAssembly.blocks(server, worldPosition, frame), frame)) {
            return Component.translatable("message.aerolarge.autopilot.assembling_zeppelin");
        }
        flyOnceAboard = null;
        flyOnceAboardFor = null;
        setChanged();
        return Component.translatable("message.aerolarge.autopilot.not_assembled");
    }

    /**
     * Assembles the airliner standing parked as blocks with its Physics Assembler, as {@link #assembleToFly} does but
     * to fly nowhere: for the self-destruct button, which arms once it is aboard the assembled airliner. Whether it
     * started: not with the airliner assembled already, assembling, or without a Physics Assembler.
     */
    public boolean startAssembly(ServerPlayer player) {
        if (!(level instanceof ServerLevel server) || SableFlight.read(this, nose()) != null || assembling()) {
            return false;
        }
        BlockPos at = assemblerAt(server);
        return at != null && PhysicsAssembler.assemble(server, at, player) == null;
    }

    /** Whether it has its Physics Assembler assembling the airliner, to fly once aboard, and is waiting for it. */
    public boolean assembling() {
        return flyOnceAboard != null && level != null && level.getGameTime() - assemblingSince < ASSEMBLY_WAIT;
    }

    /**
     * Where the airliner's Physics Assembler is: as wired when it was built or, for an airliner built before, the first
     * found round the autopilot, remembered from then on; null for none.
     */
    @Nullable
    private BlockPos assemblerAt(ServerLevel level) {
        if (assembler != null && PhysicsAssembler.is(level.getBlockState(worldPosition.offset(assembler)))) {
            return worldPosition.offset(assembler);
        }
        BlockPos from = worldPosition.offset(-ASSEMBLER_SEARCH, -ASSEMBLER_SEARCH, -ASSEMBLER_SEARCH);
        BlockPos to = worldPosition.offset(ASSEMBLER_SEARCH, ASSEMBLER_SEARCH, ASSEMBLER_SEARCH);
        for (BlockPos at : BlockPos.betweenClosed(from, to)) {
            if (level.isLoaded(at) && PhysicsAssembler.is(level.getBlockState(at))) {
                assembler = at.subtract(worldPosition);
                setChanged();
                return at.immutable();
            }
        }
        return null;
    }

    /**
     * Aboard the airliner just assembled for it: sits whoever asked back down in the seat they asked from, and
     * engages as asked, telling them and everyone aboard.
     */
    private void flyOnceAboard(ServerLevel level) {
        Settings settings = flyOnceAboard;
        UUID askedBy = flyOnceAboardFor;
        BlockPos seat = flyOnceAboardSeat;
        flyOnceAboard = null;
        flyOnceAboardFor = null;
        flyOnceAboardSeat = null;
        setChanged();
        ServerPlayer asker = askedBy != null && level.getPlayerByUUID(askedBy) instanceof ServerPlayer found ? found : null;
        if (asker != null && seat != null) {
            PilotSeatBlock.sitIn(level, worldPosition.offset(seat), asker);
        }
        Component said = set(SetAutopilot.Action.FLY, settings);
        Set<ServerPlayer> told = new HashSet<>(Aboard.players(this));
        if (asker != null) {
            told.add(asker);
        }
        for (ServerPlayer player : told) {
            player.displayClientMessage(said, false);
        }
        level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6F, 1.4F);
    }

    /**
     * A touch on one of its controls on the flight deck's sloped touch panel, on the server, at a place along it (see
     * {@link dev.aerolarge.block.CockpitKeys#along}): for the speedometer and the sky, how far up their scale, 0 the
     * slowest or lowest and 1 the fastest or highest; for the steering fan, how far right of the nose, in degrees;
     * nothing for the keys. Flying, the on/off key switches it off; the speedometer and the sky set the speed and the
     * height touched, and the fan the way touched, flown straight on; the turns circle that way, tightly or gently, and
     * straight on flies on the way it is going. A touch sounds as it does: a note, higher the faster or higher it sets,
     * or a whoosh for a turn. Switched off, the on/off key opens the settings screen, where it is engaged, and the
     * others only thud, as they do while the airliner is not assembled or the setting touched would be refused. Says
     * what it did, or why not.
     */
    public void press(Button button, double along, ServerPlayer player) {
        if (level == null) {
            return;
        }
        if (phase == Phase.OFF) {
            if (button == Button.ENGAGE) {
                openScreen(player);
            } else {
                thud();
            }
            return;
        }
        if (button == Button.ENGAGE) {
            player.displayClientMessage(set(SetAutopilot.Action.OFF, flying()), true);
            return;
        }
        SableFlight.State state = SableFlight.read(this, nose());
        if (state == null) {
            thud();
            return;
        }
        Settings touched = touched(button, along, flying(), state.heading());
        if (refusal() != null) {
            thud();
        } else if (button == Button.SPEED || button == Button.ALTITUDE) {
            float note = Mth.lerp((float) Mth.clamp(along, 0, 1), NOTE_LOW, NOTE_HIGH);
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, NOTE_VOLUME, note);
        } else {
            level.playSound(null, worldPosition, AerolargeRegistry.PAPER_PLANE_WHOOSH.get(), SoundSource.BLOCKS, WHOOSH_VOLUME, 1.0F);
        }
        player.displayClientMessage(set(SetAutopilot.Action.FLY, touched), true);
    }

    /** The thud of a control touched with nothing it can do. */
    private void thud() {
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.BLOCKS, THUD_VOLUME, THUD_PITCH);
        }
    }

    /**
     * The settings a touch on one of the flight deck's controls asks for: what it flies now, with what was touched set,
     * in the steps a touch sets it in; the way is reckoned from the way the airliner is going, heading up as the
     * steering fan shows it.
     */
    private Settings touched(Button button, double along, Settings now, double heading) {
        return switch (button) {
            case SPEED -> now.withSpeed(touchedSpeed(along));
            case ALTITUDE -> now.withAltitude(touchedAltitude(along, runwayAltitude, ceiling(level)));
            case HEADING -> now.withHeading(touchedBearing(bearing(heading), along));
            case STRAIGHT -> now.withHeading(bearing(heading));
            case TIGHT_LEFT, GENTLE_LEFT, GENTLE_RIGHT, TIGHT_RIGHT -> now.withCircle(button.way, button.size);
            case ENGAGE -> now;
        };
    }

    /**
     * The speed a touch a share of the way round the flight deck's speedometer sets, in blocks a second, in the steps a
     * touch sets it in; the same for the server and for the ghost the panel shows before the touch.
     */
    public static int touchedSpeed(double along) {
        return step(Mth.lerp(along, MIN_SPEED, MAX_SPEED), SPEED_STEP);
    }

    /**
     * The height a touch a share of the way up the flight deck's sky sets, in world blocks, in the steps a touch sets
     * it in, within the heights it can be set to hold for a runway this high and the highest it is let go.
     */
    public static int touchedAltitude(double along, double runway, double highest) {
        double lowest = lowestAltitude(runway);
        int stepped = step(Mth.lerp(along, lowest, highest), ALTITUDE_STEP);
        return Mth.clamp(stepped, Mth.ceil(lowest), Mth.floor(Math.max(lowest, highest)));
    }

    /**
     * The compass bearing a touch on the flight deck's steering fan sets: so many degrees right of a bearing, stepped.
     */
    public static int touchedBearing(int bearing, double along) {
        return Math.floorMod(step(bearing + along, HEADING_STEP), 360);
    }

    /** A value to the nearest whole step. */
    private static int step(double value, int step) {
        return (int) Math.round(value / step) * step;
    }

    /** What it flies now, as its settings screen shows it: circling, the heading is the way it is going. */
    private Settings flying() {
        SableFlight.State state = SableFlight.read(this, nose());
        double heading = lateral == Lateral.HEADING || state == null ? targetHeading : state.heading();
        return new Settings((int) Math.round(targetSpeed), (int) Math.round(targetAltitude), bearing(heading), lateral, circle,
            BlockPos.containing(orbitX, 0, orbitZ), landAtPin);
    }

    /** What its settings screen is filled in with now: what it flies, or, switched off, what engaging it would fly. */
    public Settings offered() {
        return level == null ? flying() : offered(offeredPlan(level, SableFlight.read(this, nose())));
    }

    private Plan offeredPlan(Level level, @Nullable SableFlight.State state) {
        if (phase == Phase.OFF) {
            return plan(level, state);
        }
        // Circling, the heading offered is the way it is going, to fly straight on from.
        double heading = lateral == Lateral.HEADING || state == null ? targetHeading : state.heading();
        return new Plan(phase, runwayAltitude, targetAltitude, targetSpeed, heading);
    }

    private Settings offered(Plan plan) {
        return new Settings((int) Math.round(plan.speed()), (int) Math.round(plan.altitude()), bearing(plan.heading()),
            phase != Phase.OFF ? lateral : Lateral.HEADING, circle, BlockPos.containing(orbitX, 0, orbitZ), landAtPin);
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
        Plan plan = offeredPlan(level, state);
        Settings settings = offered(plan);
        double floor = lowestAltitude(plan.runway());
        boolean assembles = state == null
            && (craft == Craft.ZEPPELIN ? built() && SableFlight.installed() && zeppelin.wired() : level instanceof ServerLevel server && assemblerAt(server) != null);
        double gravity = state != null ? state.gravity() : SableFlight.gravity(level, Vec3.atCenterOf(worldPosition));
        OpenAutopilotScreen.Limits limits = new OpenAutopilotScreen.Limits(Mth.ceil(floor), Mth.floor(Math.max(floor, ceiling(level))),
            craft.minSpeed, craft.maxSpeed, craft.amberFrom, craft.redFrom);
        PacketDistributor.sendToPlayer(player, new OpenAutopilotScreen(worldPosition, settings, plan.phase() == Phase.TAKEOFF, assembles, limits,
            (float) gravity));
    }

    /**
     * Sets the autopilot from its settings screen: flies the settings, engaging it first if it is off (to take off, or
     * to fly on from where it is in the air), lands, or switches it off. Each setting is held to what the autopilot
     * flies, and only one that differs from what it flies now is taken, so settings sent back as they were neither
     * round a target off nor start an orbit's circle afresh. Coming in to land, and once down, it takes none (see
     * {@link #refusal}). Says what it does, or why it cannot.
     */
    public Component set(SetAutopilot.Action action, Settings settings) {
        if (action == SetAutopilot.Action.OFF) {
            if (phase != Phase.OFF) {
                disengage();
            }
            return Component.translatable(craft == Craft.ZEPPELIN ? "message.aerolarge.autopilot.off_zeppelin" : "message.aerolarge.autopilot.off");
        }
        SableFlight.State state = SableFlight.read(this, nose());
        if (state == null || level == null) {
            return Component.translatable("message.aerolarge.autopilot.not_assembled");
        }
        if (action == SetAutopilot.Action.LAND) {
            return land(level, state);
        }
        Component refused = refusal();
        if (refused != null) {
            return refused;
        }
        boolean engaging = phase == Phase.OFF;
        if (engaging && craft == Craft.ZEPPELIN && !zeppelin.wired()) {
            return Component.translatable("message.aerolarge.autopilot.no_burners");
        }
        if (engaging) {
            engage(level, state);
            if (phase == Phase.TAKEOFF && craft == Craft.ZEPPELIN) {
                // A zeppelin rises straight up on its hot air, with no runway and no clearance to wait for.
                announcements.announce(Announcement.TAKEOFF, this);
            } else if (phase == Phase.TAKEOFF) {
                // Airstairs and slides down on the ground would hold it where it stands: they go away first.
                if (level instanceof ServerLevel server) {
                    stowing.start(server, worldPosition);
                    gearWaitUntil = server.getGameTime() + GEAR_WAIT;
                }
                // Cleared by the tower first, if it talks; the cabin hears the take-off announcement as the brakes come off.
                awaitingClearance = atc.requestTakeOff(level.getGameTime());
                if (!awaitingClearance) {
                    announcements.announce(Announcement.TAKEOFF, this);
                }
            }
        }
        // Whatever is set, a runway near the pin is given up; still set to land there, it looks for one afresh.
        pinLanding.abandon();
        landAtPin = settings.landAtPin();
        targetSpeed = Mth.clamp(unlessSame(targetSpeed, settings.speed()), craft.minSpeed, craft.maxSpeed);
        double floor = lowestAltitude(runwayAltitude);
        targetAltitude = Mth.clamp(unlessSame(targetAltitude, settings.altitude()), floor, Math.max(floor, ceiling(level)));
        // The circle's size follows from how tightly it is set to circle at the speed set: set faster or slower while
        // circling, it comes round a new circle beside it, as it would picked afresh.
        circle = settings.circle();
        double radius = circle.radius(targetSpeed, state.gravity());
        boolean resized = Math.abs(radius - orbitRadius) >= 1;
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
     * Why the autopilot will not fly new settings now, or null if it will: coming in to land, so that nothing pressed
     * by accident, as young pilots press everything, calls the landing off and climbs away again (switched off, it
     * stops landing); or rolling out after landing. The flight deck's controls thud instead of sounding a setting it
     * would refuse.
     */
    @Nullable
    private Component refusal() {
        if (phase == Phase.ROLLOUT) {
            return Component.translatable("message.aerolarge.autopilot.rolling_out");
        }
        if (comingIn()) {
            return Component.translatable("message.aerolarge.autopilot.coming_in");
        }
        return null;
    }

    /**
     * Whether it is coming in to land: landing ahead, or on final to the runway near the pin and landing on it. It
     * takes no new settings then (see {@link #refusal}) and is not told to land again, and its settings screen leaves
     * Apply and Land dark.
     */
    public boolean comingIn() {
        return phase == Phase.LAND || pinLanding.onFinal();
    }

    /**
     * Lands the airliner ahead, engaging the autopilot if it is off. Out of any circle onto the heading it is on, it
     * comes down, quickly from high up and gently lower down (see {@link #landingSink}), slowing to the speed it flies
     * nose level at, and flares over the ground to touch down softly; then it idles the engines and brakes to a stop,
     * parks and hands back to the flight deck (see {@link #fly}). Only in the air, and not while it comes in to land
     * already, where it would give up the runway near the pin for whatever lies ahead.
     */
    private Component land(Level level, SableFlight.State state) {
        double radio = radioAltitude(level, state);
        if (radio <= AIRBORNE) {
            return Component.translatable("message.aerolarge.autopilot.on_ground");
        }
        if (comingIn()) {
            return Component.translatable("message.aerolarge.autopilot.coming_in");
        }
        if (phase == Phase.OFF) {
            engage(level, state);
        }
        if (craft == Craft.ZEPPELIN) {
            return comeDown(state);
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

    /**
     * A zeppelin told to land: it stops where it is, holding the way it points, and sinks gently straight down to the
     * ground, where it rests on its wheels (see {@link ZeppelinPilot}).
     */
    private Component comeDown(SableFlight.State state) {
        announcements.announce(Announcement.LANDING, this);
        phase = Phase.LAND;
        lateral = Lateral.HEADING;
        targetHeading = state.heading();
        targetSpeed = 0;
        changed();
        return Component.translatable("message.aerolarge.autopilot.coming_down");
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
        if (craft == Craft.ZEPPELIN) {
            zeppelin.engage(state);
        }
        gearRaised = false;
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
                    Math.round(Mth.clamp(state.airspeed(), craft.minSpeed, craft.maxSpeed)), state.heading());
            }
        }
        double runway = state != null ? state.position().y : Vec3.atCenterOf(worldPosition).y;
        double heading = state != null ? state.heading() : nose().toYRot();
        int climb = craft == Craft.ZEPPELIN ? ZEPPELIN_CLIMB : AerolargeConfig.AUTOPILOT_CLIMB.get();
        double cruise = craft == Craft.ZEPPELIN ? ZEPPELIN_CRUISE : AerolargeConfig.AUTOPILOT_CRUISE_SPEED.get();
        return new Plan(Phase.TAKEOFF, runway, Math.min(runway + climb, ceiling(level)), Mth.clamp(cruise, craft.minSpeed, craft.maxSpeed),
            heading);
    }

    /** The lowest height it can be set to hold, in world blocks: a little above the runway it took off from. */
    public static double lowestAltitude(double runway) {
        return runway + LOWEST_ABOVE_RUNWAY;
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
     * the callouts watch it, rough air and air pockets come and go, lightning strikes in a storm, the autopilot flies it if engaged, or keeps it from tipping over
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
            autopilot.showSurfaces(serverLevel);
            return;
        }
        if (autopilot.flyOnceAboard != null) {
            autopilot.flyOnceAboard(serverLevel);
        }
        if (autopilot.craft == Craft.ZEPPELIN) {
            autopilot.zeppelinTick(serverLevel, flight);
            return;
        }
        autopilot.callouts.watch(serverLevel, flight, autopilot.wheelsBelow, () -> Aboard.players(autopilot));
        double radio = autopilot.radioAltitude(serverLevel, flight);
        autopilot.turbulence.tick(serverLevel, autopilot, flight.position(), radio, flight.airspeed());
        autopilot.airPockets.tick(serverLevel, autopilot, flight.position(), radio, flight.airspeed());
        autopilot.lightning.tick(serverLevel, autopilot, flight.position(), radio);
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
        autopilot.showSurfaces(serverLevel);
    }

    /**
     * Each tick of an assembled zeppelin's: lightning strikes it in a storm and it notes the villages below, as an
     * airliner does; it notes who is aboard, for the safety canopy (see {@link SafetyCanopy}); it comes down by itself
     * once nobody has been aboard a while; engaged, it flies (see {@link ZeppelinPilot}), showing what it does on the
     * control car's hand controls, until it is down on the ground and rests there, switching itself off; switched off,
     * it is flown by those controls once handed over to them.
     */
    private void zeppelinTick(ServerLevel level, SableFlight.State flight) {
        keepLoaded(level);
        if (Math.floorMod(level.getGameTime() + worldPosition.asLong(), ABOARD_LOOK) == 0) {
            SafetyCanopy.aboard(Aboard.players(this), level.getGameTime());
        }
        double radio = radioAltitude(level, flight);
        lightning.tick(level, this, flight.position(), radio);
        VillageSpotter.tick(level, this, flight.position());
        watchUnattended(level, flight, radio);
        if (phase != Phase.OFF) {
            ZeppelinPilot.Orders orders = new ZeppelinPilot.Orders(phase, targetAltitude, targetSpeed, targetHeading, lateral, orbitX, orbitZ,
                orbitRadius, landAtPin);
            follow(level, flight, zeppelin.fly(level, worldPosition, orders, flight, radio, SableFlight.mass(this)));
            if (phase != Phase.OFF && Math.floorMod(level.getGameTime() + worldPosition.asLong(), SHOW_CONTROLS) == 0) {
                zeppelin.showControls(level, worldPosition);
            }
        } else {
            zeppelin.flyByHand(level, worldPosition, flight, radio, SableFlight.mass(this));
        }
        watchArrival(flight, radio + flight.apex() - flight.position().y > AIRBORNE);
    }

    /**
     * Goes on in the phase the zeppelin's pilot flies it in: switched off, it has either rested on the ground or, still
     * rising, given up too heavy; coming down at a pin, the cabin is told it is landing.
     */
    private void follow(ServerLevel level, SableFlight.State flight, Phase next) {
        if (next == Phase.OFF && phase == Phase.TAKEOFF) {
            tooHeavy(level, flight);
            return;
        }
        if (next == Phase.OFF) {
            restZeppelin(level, flight);
            return;
        }
        if (next == Phase.LAND && phase != Phase.LAND) {
            announcements.announce(Announcement.LANDING, this);
        }
        if (next != phase) {
            phase = next;
            changed();
        }
    }

    /**
     * Keeps the zeppelin loaded for as long as it is assembled, flying or parked, with anyone aboard or not. Sable
     * loads an assembled body back only once the land under all of it is simulated, which reaches 12 chunks round a
     * player by default, and a zeppelin is 490 blocks long: let go, it would unload where it stands once nobody was
     * near its middle, a pilot riding in its bow included, and never load again. It is let go only when its control
     * head is taken away (see {@link #removed}). Looked at every {@link #ABOARD_LOOK} ticks until it holds.
     */
    private void keepLoaded(ServerLevel level) {
        if (keptLoaded || Math.floorMod(level.getGameTime() + worldPosition.asLong(), ABOARD_LOOK) != 0) {
            return;
        }
        keptLoaded = SableFlight.keepLoaded(this, true);
    }

    /**
     * Taken away for good (see {@link dev.aerolarge.block.AutopilotBlock#onRemove}): it lets go of the zeppelin it kept
     * loaded. Not when its chunk merely unloads, which would forget the ticket Sable saves with it.
     */
    public void removed() {
        SableFlight.keepLoaded(this, false);
        keptLoaded = false;
    }

    /**
     * Still on the ground a minute after it was told to rise, too heavy for its burners: it rests there again and
     * switches itself off with a thud, saying why.
     */
    private void tooHeavy(ServerLevel level, SableFlight.State flight) {
        zeppelin.rest(level, worldPosition, flight, SableFlight.mass(this));
        phase = Phase.OFF;
        changed();
        thud();
        tell(level, flight.position(), Component.translatable("message.aerolarge.autopilot.too_heavy"));
    }

    /** Down on the ground: it rests there on its wheels, the engines stopped, and switches itself off with a bell. */
    private void restZeppelin(ServerLevel level, SableFlight.State flight) {
        zeppelin.rest(level, worldPosition, flight, SableFlight.mass(this));
        phase = Phase.OFF;
        landAtPin = false;
        changed();
        level.playSound(null, worldPosition, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 1.2F);
    }

    /** Tells the clients how far to swing the control surfaces' panels, once that has moved on (see {@link ControlSurfaces}). */
    private void showSurfaces(ServerLevel level) {
        if (surfaces.showMoved(level.getGameTime())) {
            changed();
        }
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
     * back inside the safe attitude once they go past it. On the ground they stay quiet, and the set parking brake
     * holds the engines at idle (see {@link #holdOnBrake}).
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
        double radio = radioAltitude(level, state);
        if (radio > AIRBORNE) {
            double climbLimit = state.apex() > highestAltitude(level) ? 0 : PROTECT_CLIMB;
            double pitchBack = Mth.clamp(pitch, -PROTECT_DIVE, climbLimit) - pitch;
            double rollBack = Mth.clamp(roll, -PROTECT_BANK, PROTECT_BANK) - roll;
            pitchCommand = PITCH_GAIN * pitchBack - PITCH_DAMPING * pitchRate;
            rollCommand = ROLL_GAIN * rollBack - ROLL_DAMPING * rollRate;
        }
        driveJets(level, state, pitchCommand, rollCommand);

        double speed = state.airspeed();
        double apex = state.apex();
        if (speed > MAX_SAFE_SPEED || apex > highestAltitude(level)) {
            if (!governing) {
                governing = true;
                setChanged();
            }
            // Every tick, so a lever moved meanwhile does not get the engines back.
            for (Channel engine : ENGINES) {
                drive(level, engine, 0);
            }
        } else if (governing && speed < MAX_SAFE_SPEED - GOVERNOR_MARGIN && apex < highestAltitude(level) - CEILING_MARGIN) {
            governing = false;
            resyncControls(level);
            setChanged();
        }
        holdOnBrake(level, radio);
    }

    /**
     * On the ground with the parking brake set, the engines stay at idle whatever the thrust levers say: the wheels'
     * brakes cannot hold the airliner against any thrust, and a set brake should keep it where it stands. Released, or
     * once in the air, the engines follow the levers again.
     */
    private void holdOnBrake(ServerLevel level, double radio) {
        if (radio <= AIRBORNE && parkingBrakeSet(level)) {
            heldOnBrake = true;
            // Every tick, so a lever moved meanwhile does not get the engines back.
            for (Channel engine : ENGINES) {
                drive(level, engine, 0);
            }
        } else if (heldOnBrake) {
            heldOnBrake = false;
            if (!governing) {
                resyncControls(level);
            }
        }
    }

    /** Whether the airliner's parking brake is set; false for one built without a parking brake. */
    private boolean parkingBrakeSet(ServerLevel level) {
        for (BlockPos offset : controls) {
            BlockState control = level.getBlockState(worldPosition.offset(offset));
            if (control.getBlock() instanceof ParkingBrakeBlock) {
                return control.getValue(ParkingBrakeBlock.SET);
            }
        }
        return false;
    }

    /**
     * The highest the airliner is let go, and the highest height it can be set to hold: some way below the top of the
     * world.
     */
    public static double highestAltitude(Level level) {
        return level.getMaxBuildHeight() - CEILING_MARGIN;
    }

    /**
     * The highest height it can be set to hold: an airliner's {@link #highestAltitude}, and an assembled zeppelin's no
     * higher than its burners can lift it (see {@link ZeppelinPilot#ceiling}), so it is never sent climbing for good
     * at full burn toward a height it never reaches.
     */
    private double ceiling(Level level) {
        double highest = highestAltitude(level);
        SableFlight.State state = craft == Craft.ZEPPELIN ? SableFlight.read(this, nose()) : null;
        double floats = state == null ? Double.NaN : zeppelin.ceiling(state, SableFlight.mass(this));
        return Double.isNaN(floats) ? highest : Math.min(highest, floats);
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
        boolean holding = stowing.waiting(level) || clearing || phase == Phase.TAKEOFF && waitingForGear(level);
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
            double wantedClimb = wantedClimb(landing, guide, heightToGo, radio, climb);
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

        workGear(level, radio, climb, guide);
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
     * The rate to climb at, in blocks a second, in the air (not taking off or rolling out), climbing at the rate given
     * now. Landing, it sinks (see {@link #easedSink}); on a runway near the pin, over whichever is nearer, the ground
     * below or the runway, so it does not sink below the runway over a dip before it. Down the glide path to such a
     * runway, the rate the path asks for; else the rate the height still to go asks for.
     */
    private double wantedClimb(boolean landing, @Nullable PinLanding.Guide guide, double heightToGo, double radio, double climb) {
        if (landing) {
            return -easedSink(guide != null ? Math.min(radio, guide.aboveRunway()) : radio, climb);
        }
        sinkCommand = Double.NaN;
        if (guide != null && !Double.isNaN(guide.climb())) {
            return guide.climb();
        }
        return Mth.clamp(HEIGHT_GAIN * heightToGo, -MAX_SINK_RATE, MAX_CLIMB_RATE);
    }

    /**
     * The rate it sinks at landing this tick, in blocks a second, this high above the ground and climbing at the rate
     * given: the rate for its height (see {@link #landingSink}), but rising to it from the rate it sank at as it
     * started landing no faster than {@link #LANDING_SINK_EASE}; sinking less, as it eases out over the ground and
     * flares, it does at once.
     */
    private double easedSink(double above, double climb) {
        double from = Double.isNaN(sinkCommand) ? Math.max(0, -climb) : sinkCommand;
        sinkCommand = Math.min(landingSink(above), from + LANDING_SINK_EASE / 20);
        return sinkCommand;
    }

    /**
     * The rate it sinks at landing, in blocks a second, this high above the ground: faster from high up, easing to a
     * gentle sink between {@link #LANDING_STEEP_HEIGHT} and {@link #LANDING_SETTLE_HEIGHT}, and ever more gently the
     * nearer the ground below the flare's height.
     */
    private static double landingSink(double above) {
        if (above > LANDING_SETTLE_HEIGHT) {
            return Mth.clampedMap(above, LANDING_SETTLE_HEIGHT, LANDING_STEEP_HEIGHT, LANDING_SINK_RATE, LANDING_FAST_SINK);
        }
        return Mth.clamp(FLARE_GAIN * above, TOUCHDOWN_SINK, LANDING_SINK_RATE);
    }

    /**
     * The gear, worked as a crew would: raised once it climbs away after take-off, past {@link #GEAR_UP_HEIGHT} (once a
     * climb out), and lowered as it comes in to land, on the plain landing and on final to a runway near the pin.
     */
    private void workGear(ServerLevel level, double radio, double climb, @Nullable PinLanding.Guide guide) {
        GearLeverBlockEntity gear = gear(level);
        if (gear == null) {
            return;
        }
        boolean comingIn = phase == Phase.LAND || guide != null && guide.onFinal();
        if (comingIn && !gear.down()) {
            gear.lower(level);
        } else if (climbingOut && !gearRaised && radio > GEAR_UP_HEIGHT && climb > 0) {
            gearRaised = gear.raise(level);
        }
    }

    /**
     * Set to take off with the gear not down: it throws the lever down and holds the brakes until the gear is down and
     * locked, or it has waited {@link #GEAR_WAIT} ticks. Come down on its belly, the gear has no room to come down, so
     * it waits that long and slides off on its belly, the gear coming down in the air when it next lands.
     */
    private boolean waitingForGear(ServerLevel level) {
        GearLeverBlockEntity gear = gear(level);
        if (gear == null || gear.locked() || level.getGameTime() >= gearWaitUntil) {
            return false;
        }
        gear.lower(level);
        return true;
    }

    /** The landing gear lever it throws, if its airliner has one. */
    @Nullable
    private GearLeverBlockEntity gear(Level level) {
        return gearLever != null && level.getBlockEntity(worldPosition.offset(gearLever)) instanceof GearLeverBlockEntity gear ? gear : null;
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

    /**
     * How high the bottom of the wheels is above the ground below the autopilot. With the gear not all down, no higher
     * than the lowest of its legs' wheels would be, each measured where its leg stands: down on its belly the airliner
     * rests nose up, and the wheels under the nose, where the autopilot is, would seem to be off the ground.
     */
    private double radioAltitude(Level level, SableFlight.State state) {
        Vec3 at = state.position();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
        double radio = at.y - 0.5 - wheelsBelow - ground;
        GearLeverBlockEntity gear = gear(level);
        return gear == null || gear.locked() ? radio : Math.min(radio, gear.lowestWheels(level));
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
            // Split between its jets, the channel no longer has the one signal noted for it.
            jetSignals.remove(roll);
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
        if (craft == Craft.ZEPPELIN) {
            zeppelin.stop(serverLevel, worldPosition);
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
        if (!channel.synced() && steady(channel, power)) {
            return;
        }
        Relays.drive(level, worldPosition, relays.getOrDefault(channel, List.of()), power, channel.synced());
    }

    /**
     * Whether a control jet's signal is left as it is, only a step of {@link #JET_STEADY} off what it was last set to:
     * as the autopilot trims, its jets would flick between neighbouring steps every tick, and each change sends the
     * thruster to every client near. Off is always off. Notes the signal set otherwise.
     */
    private boolean steady(Channel jet, int power) {
        Integer last = jetSignals.get(jet);
        if (last != null && power != 0 && Math.abs(power - last) <= JET_STEADY) {
            return true;
        }
        jetSignals.put(jet, power);
        return false;
    }

    /** Once after loading, as it first works its relays or the controls, mends its links to them if they lead nowhere. */
    private void mendLinks(ServerLevel level) {
        if (!linksChecked) {
            linksChecked = true;
            // The steering and brake relays of a leg folded up are away with it, not lost.
            List<BlockPos> links = Stream.concat(Stream.concat(relays.values().stream().flatMap(List::stream), controls.stream()), zeppelin.links().stream())
                .filter(link -> !GearBayBlockEntity.stows(level, worldPosition.offset(link))).toList();
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
        gearLever = Turning.turn(gearLever, turn);
        assembler = Turning.turn(assembler, turn);
        surfaces.turn(turn);
        zeppelin.turn(turn);
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
        surfaces.saveShown(tag);
        saveFrame(tag);
        Turning.save(tag, getBlockState());
        saveTargets(tag);
        tag.putDouble("Trim", trim);
        tag.putDouble("ThrustTrim", thrustTrim);
        tag.putDouble("PitchHold", pitchHold);
        tag.putDouble("RunwayHeading", runwayHeading);
        tag.putInt("WheelsBelow", wheelsBelow);
        if (gearLever != null) {
            tag.putLong("GearLever", gearLever.asLong());
        }
        if (assembler != null) {
            tag.putLong("Assembler", assembler.asLong());
        }
        if (flyOnceAboard != null) {
            tag.put("FlyOnceAboard", flyOnceAboard.save());
        }
        if (flyOnceAboardFor != null) {
            tag.putUUID("FlyOnceAboardFor", flyOnceAboardFor);
        }
        if (flyOnceAboardSeat != null) {
            tag.putLong("FlyOnceAboardSeat", flyOnceAboardSeat.asLong());
        }
        tag.putString("Craft", craft.name());
        if (craft == Craft.ZEPPELIN) {
            tag.put("Zeppelin", zeppelin.save());
        }
        saveLivery(tag);
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
        tag.putString("Circle", circle.name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // In the save and in the update the clients are sent alike, for the pictures they draw it with; saved before
        // there were zeppelins: an airliner's.
        craft = parse(Craft.class, tag.getString("Craft"), Craft.AIRLINER);
        // Saved and sent alike, as the clients paint by it; saved before there were liveries: silver, with no repaint.
        livery = Livery.named(tag.getString("Livery"));
        repaintedFrom = Livery.named(tag.getString("RepaintedFrom"));
        repaintStart = tag.contains("RepaintStart") ? tag.getLong("RepaintStart") : -1;
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
            // Saved without one by an airliner built before the gear retracted.
            gearLever = tag.contains("GearLever") ? Turning.turn(BlockPos.of(tag.getLong("GearLever")), turn) : null;
            // Saved without one by an airliner built before it could assemble its airliner: looked for when wanted.
            assembler = tag.contains("Assembler") ? Turning.turn(BlockPos.of(tag.getLong("Assembler")), turn) : null;
            flyOnceAboard = tag.contains("FlyOnceAboard") ? Settings.load(tag.getCompound("FlyOnceAboard")) : null;
            flyOnceAboardFor = tag.hasUUID("FlyOnceAboardFor") ? tag.getUUID("FlyOnceAboardFor") : null;
            flyOnceAboardSeat = tag.contains("FlyOnceAboardSeat") ? Turning.turn(BlockPos.of(tag.getLong("FlyOnceAboardSeat")), turn) : null;
            if (craft == Craft.ZEPPELIN) {
                zeppelin.load(tag.getCompound("Zeppelin"), turn);
            }
            governing = tag.getBoolean("Governing");
            climbingOut = tag.getBoolean("ClimbingOut");
            // Held for a clearance the tower, not saved, has forgotten: it goes on at once, with the announcement.
            awaitingClearance = tag.getBoolean("AwaitingClearance");
            flown = tag.getBoolean("Flown");
        }
        // In the save and in the update the clients are sent alike: what they draw the control surfaces with.
        surfaces.loadShown(tag);
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
        // Saved with only a radius before circles were tight or gentle: it flies on round that, called tight.
        circle = parse(CircleSize.class, tag.getString("Circle"), CircleSize.TIGHT);
        // Saved before it could land at the pin: it circles it.
        landAtPin = tag.getBoolean("LandAtPin");
        loadRunway(tag);
        // Only ever in the update sent to clients: the server finds it afresh each tick.
        stuck = tag.getBoolean("Stuck");
        loadFrame(tag);
    }

    /** How its zeppelin is painted, and the repaint sweeping along it, saved and sent to clients alike. */
    private void saveLivery(CompoundTag tag) {
        tag.putString("Livery", livery.name());
        if (repaintStart >= 0) {
            tag.putString("RepaintedFrom", repaintedFrom.name());
            tag.putLong("RepaintStart", repaintStart);
        }
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
        tag.putString("Craft", craft.name());
        saveLivery(tag);
        saveFrame(tag);
        Turning.save(tag, getBlockState());
        surfaces.saveShown(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
