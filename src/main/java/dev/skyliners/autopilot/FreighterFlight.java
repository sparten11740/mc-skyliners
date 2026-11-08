package dev.skyliners.autopilot;

import dev.skyliners.Aboard;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.AutopilotBlock;
import dev.skyliners.block.RetractableLegBlockEntity;
import dev.skyliners.block.Turning;
import dev.skyliners.compat.SableFlight;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The freighter in her autopilot's hands (see {@link StandingFlight}): she lifts straight off her legs and comes
 * straight down onto them, flown by {@link FreighterPilot}, her legs drawn up into her belly as she flies and let down
 * before she lands (see {@link FreighterLegs}); she lands by herself once nobody has been aboard a while, or slams
 * down hard where she is from her console (see {@link FreighterSlam}); and she can climb to space as the rocket does.
 * How hard her lift thrusters and her mains fire is shown to the clients, who play her engines' roar by it (see the
 * client's FreighterRoar), and where her disc's middle is among her blocks goes with her, for them to place it.
 */
public final class FreighterFlight extends StandingFlight {
    /** Engaged on the ground she rises this far above it, in blocks. */
    private static final int CLIMB = 40;
    /** Engaged on the ground she cruises at this speed, in blocks a second. */
    private static final double CRUISE = 20;
    /**
     * How many steps of her lift's and her mains' shares of full the clients are shown, and how often at most a change
     * of step is sent, in ticks: enough for her roar to follow them, few enough that a hover wobbling between two
     * steps sends nothing.
     */
    private static final int SHOWN_STEPS = 10;
    private static final int SHOWN_EVERY = 10;
    /**
     * Her mains' share of full under which they only trim her hover, flickering between nothing and a step as she holds
     * her spot: shown as nothing, so they are not heard spooling up over and over, nor sent each time.
     */
    private static final double MAINS_SHOWN_FROM = 0.05;
    /**
     * What holds her take-off before her thrusters spool up, each asked on the server every tick it waits with her
     * level and her autopilot's place: her ramp not yet all away, for one, which {@code Skyliners} registers once as
     * the mod starts, so the autopilot need know nothing of ramps. Held, she waits on her legs, her thrusters quiet,
     * and her console shows what holds her by its picture (see {@link #heldBy}).
     */
    private static final List<Hold> HOLDS = new CopyOnWriteArrayList<>();
    /**
     * How long her take-off may be held, in ticks (half a minute), before it is called off and her autopilot switched
     * off: someone staying on her ramp keeps it from folding away, and she never lifts off with them on it.
     */
    private static final int TAKEOFF_HELD_LONGEST = 600;
    /**
     * How long her landing waits for her legs to come down at most, in ticks (ten seconds, twice as long as they take):
     * one that cannot lock, with something in its way, would otherwise keep her hovering for ever, so she comes down
     * without it, as an airliner may land on its belly.
     */
    private static final int LEGS_AWAITED_LONGEST = 200;
    /**
     * Her boost (see {@link #boost}): how long it fires her mains flat out, and how long after it she rests before it
     * fires again, slowing back to her autopilot's speed meanwhile, in ticks.
     */
    public static final int BOOST_TICKS = 160;
    public static final int BOOST_REST_TICKS = 200;
    /** No boost begun since she was loaded. */
    private static final long NO_BOOST = Long.MIN_VALUE;
    /** How loud her mains' roar and their cutting out are, heard at her stern as she boosts. */
    private static final float BOOST_VOLUME = 3;
    /** No jump on its way. */
    private static final long NO_JUMP = Long.MIN_VALUE;
    /**
     * What her computer says in place of the flight deck's announcements, after its blip: as she is set to take off,
     * comes down to land and stands on her legs again, and as she reaches space and her gravity comes back on, whether
     * her autopilot or an announcement panel set aboard her makes it; any other announcement is the airliner's.
     */
    private static final Map<Holder<SoundEvent>, Holder<SoundEvent>> COMPUTER_SAYS = Map.of(
        SkylinersRegistry.ANNOUNCEMENT_TAKEOFF, SkylinersRegistry.FREIGHTER_COMPUTER_TAKEOFF,
        SkylinersRegistry.ANNOUNCEMENT_LANDING, SkylinersRegistry.FREIGHTER_COMPUTER_LANDING,
        SkylinersRegistry.ANNOUNCEMENT_ARRIVAL, SkylinersRegistry.FREIGHTER_COMPUTER_ARRIVAL,
        SkylinersRegistry.ANNOUNCEMENT_SPACE, SkylinersRegistry.FREIGHTER_COMPUTER_SPACE,
        SkylinersRegistry.ANNOUNCEMENT_GRAVITY, SkylinersRegistry.FREIGHTER_COMPUTER_GRAVITY
    );

    private final FreighterPilot pilot = new FreighterPilot();
    /** Her landing legs, drawn up as she flies and let down to land. */
    private final FreighterLegs legs = new FreighterLegs();
    /** How many ticks in a row her landing has waited for her legs (see {@link #LEGS_AWAITED_LONGEST}). */
    private int legsAwaited;
    /** How many ticks in a row her take-off has been held (see {@link #HOLDS}). */
    private int heldFor;
    /**
     * What holds her take-off now, by the word its picture on her console goes by, on the server and as the clients
     * are shown it; none while nothing does.
     */
    @Nullable
    private String heldBy;
    /** How hard her lift thrusters and her mains fire, as the clients are shown it. */
    private final ShownShare lift = new ShownShare(SHOWN_STEPS, SHOWN_EVERY);
    private final ShownShare mains = new ShownShare(SHOWN_STEPS, SHOWN_EVERY);
    /** Whether she has said she finds no ground to come down on round the pin she holds over. */
    private boolean toldNoGround;
    /** Her engine grille glowing with her engines, and which of her thrusters trail their plumes. */
    private final EngineGlow glow = new EngineGlow();
    private final FreighterPlumes plumes = new FreighterPlumes();
    /**
     * Where her disc's middle is from her control head, so many blocks to its right and ahead of it, on the clients as
     * on the server; unknown for one built before it was kept, until her pilot's wiring gives it.
     */
    private double middleRight;
    private double middleAhead;
    private boolean middleKnown;
    /** When the boost under way ends, in game time, on the server and as the clients are shown it; none begun yet. */
    private long boostUntil = NO_BOOST;
    /** Whether her mains fired flat out for a boost last tick, so they are heard cutting out once it ends. */
    private boolean boosting;
    /** Her hyperspace jump (see {@link #jump}), on the server. */
    private final FreighterJump jump = new FreighterJump();
    /** Her climb boost (see {@link #climbBoost}), on the server and as the clients are shown it. */
    private final FreighterClimbBoost climb = new FreighterClimbBoost();
    /** Her slam landing (see {@link #slam}), on the server and as the clients are shown it. */
    private final FreighterSlam slam = new FreighterSlam();
    /**
     * Her jump drive in engineering as an offset from her control head, set as she is built, which spools up and fires
     * as she jumps; none for one built before, or without engineering's machines.
     */
    @Nullable
    private BlockPos jumpDrive;
    /**
     * As the clients are shown it: when her jump's spool began, in game time, none while she is not jumping; and
     * whether the server lets her jump at all.
     */
    private long jumpingSince = NO_JUMP;
    private boolean jumpsAllowed;

    /** Where her hyperspace jump stands as her console shows it: not to be had, ready, or on its way. */
    public enum JumpStage {
        UNAVAILABLE, READY, SPOOLING
    }

    /** Her jump as a console shows it, and how far into its spool she is, from 0 to 1. */
    public record JumpShown(JumpStage stage, double spooled) {
        public static final JumpShown UNAVAILABLE = new JumpShown(JumpStage.UNAVAILABLE, 0);
    }

    /**
     * Something that may hold her take-off (see {@link #HOLDS}): the word its picture on her console goes by, and
     * whether it holds her now, asked with her level and her autopilot's place.
     */
    private record Hold(String why, BiPredicate<ServerLevel, BlockPos> holds) {
    }

    /** What asking her to boost comes to (see {@link #boost}), each with its own word in the hotbar. */
    public enum BoostAnswer implements StringRepresentable {
        /** Her mains roar into it. */
        BOOSTING,
        /** It is still resting after the last. */
        RESTING,
        /** She is not flying along: on the ground, lifting off, coming down or switched off. */
        NOT_FLYING,
        /** There is no freighter to boost. */
        NO_SHIP;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Where her boost stands as her console shows it: ready, firing or resting, and how much of that is left. */
    public enum BoostStage {
        READY, FIRING, RESTING
    }

    /** Her boost as a console shows it, and the share of its firing or its rest still to come, from 1 down to 0. */
    public record BoostShown(BoostStage stage, double left) {
        public static final BoostShown READY = new BoostShown(BoostStage.READY, 0);
    }

    @Override
    public Craft craft() {
        return Craft.FREIGHTER;
    }

    /**
     * Wired when she is built: her thrusters' relays, her legs' feet and where her middle is (see {@link
     * FreighterPilot.Wiring}).
     */
    public void configure(FreighterPilot.Wiring wiring) {
        pilot.configure(wiring);
        knowMiddle(wiring);
    }

    /** Wired when she is built: her leg bays, as offsets from her control head (see {@link FreighterLegs}). */
    public void wireLegs(List<BlockPos> bays) {
        legs.configure(bays);
    }

    /**
     * Where her landing legs are, for play-testing: down, raising, up or lowering, all together; moving with them
     * apart; fixed with none that draw up.
     */
    public String legsShown(AutopilotBlockEntity autopilot) {
        if (legs.count() == 0 || autopilot.getLevel() == null) {
            return "fixed";
        }
        RetractableLegBlockEntity.Motion motion = legs.motion(autopilot.getLevel(), autopilot.getBlockPos());
        return motion == null ? "moving" : motion.name().toLowerCase(Locale.ROOT);
    }

    /** Wired when she is built: her jump drive in engineering, as an offset from her control head, if she has one. */
    public void wireJumpDrive(@Nullable BlockPos offset) {
        jumpDrive = offset;
    }

    /**
     * Asks the freighter an autopilot flies to jump to her pin, on the server, as her console's jump button does (see
     * {@link FreighterJump}): what it came to, for the button to answer with; none for any other craft. Standing as
     * she was built, never yet assembled, she is on the ground, and it says why not as it does there, her computer's
     * voice with it, rather than that there is no freighter.
     */
    @Nullable
    public static FreighterJump.Answer jump(ServerLevel level, @Nullable AutopilotBlockEntity autopilot) {
        FreighterFlight freighter = autopilot == null ? null : autopilot.flight(FreighterFlight.class);
        if (freighter == null) {
            return null;
        }
        SableFlight.State state = SableFlight.read(autopilot, autopilot.getBlockState().getValue(AutopilotBlock.FACING));
        if (state == null) {
            FreighterJump.Answer refused = FreighterJump.refusal(autopilot, Vec3.atCenterOf(autopilot.getBlockPos()));
            return refused == null ? FreighterJump.Answer.NOT_FLYING : refused;
        }
        FreighterJump.Answer answer = freighter.jump.start(level, autopilot, freighter.pilot.middle(state), freighter.jumpDrive);
        if (answer == FreighterJump.Answer.JUMPING) {
            freighter.jumpingSince = level.getGameTime();
            autopilot.changed();
        }
        return answer;
    }

    /**
     * Where the jump of the freighter an autopilot flies stands at a game time, a client's eased one to draw it by:
     * on its way, ready while the server lets her jump and she flies along to a pin, or not to be had; not to be had
     * for any other craft.
     */
    public static JumpShown jumpOf(AutopilotBlockEntity autopilot, double now) {
        FreighterFlight freighter = autopilot.flight(FreighterFlight.class);
        if (freighter == null) {
            return JumpShown.UNAVAILABLE;
        }
        if (freighter.jumpingSince != NO_JUMP) {
            return new JumpShown(JumpStage.SPOOLING, Mth.clamp((now - freighter.jumpingSince) / FreighterJump.SPOOL_TICKS, 0, 1));
        }
        boolean ready = freighter.jumpsAllowed && autopilot.lateral == AutopilotBlockEntity.Lateral.DESTINATION && FreighterJump.flyingAlong(autopilot);
        return ready ? new JumpShown(JumpStage.READY, 0) : JumpShown.UNAVAILABLE;
    }

    /** How many ticks after the button the land round her pin was in last jump, for play-testing; -1 before. */
    public int jumpLoadedAfter() {
        return jump.loadedAfter();
    }

    /** Her jump, once a tick, on the server: on its way, and the clients told once it is over. */
    private void jumpTick(ServerLevel level, AutopilotBlockEntity autopilot, SableFlight.State flight) {
        boolean allowed = SkylinersConfig.FREIGHTER_HYPERSPACE_JUMP.get();
        if (allowed != jumpsAllowed) {
            jumpsAllowed = allowed;
            autopilot.changed();
        }
        jump.tick(level, autopilot, flight, pilot.middle(flight), pilot, jumpDrive);
        if (!jump.jumping() && jumpingSince != NO_JUMP) {
            jumpingSince = NO_JUMP;
            autopilot.changed();
        }
    }

    /** Where her disc's middle is, from what her pilot is wired to. */
    private void knowMiddle(FreighterPilot.Wiring wiring) {
        middleRight = wiring.middleRight();
        middleAhead = wiring.middleAhead();
        middleKnown = !wiring.clusters().isEmpty();
    }

    /** How hard the lift thrusters of the freighter an autopilot flies fire, as it shows it; none for another craft. */
    public static float liftOf(AutopilotBlockEntity autopilot) {
        FreighterFlight freighter = autopilot.flight(FreighterFlight.class);
        return freighter == null ? 0 : freighter.lift.shown();
    }

    /** How hard the mains of the freighter an autopilot flies fire, as it shows it; none for any other craft. */
    public static float mainsOf(AutopilotBlockEntity autopilot) {
        FreighterFlight freighter = autopilot.flight(FreighterFlight.class);
        return freighter == null ? 0 : freighter.mains.shown();
    }

    /**
     * Where the disc's middle of the freighter an autopilot flies is among her blocks, level with her control head;
     * null for any other craft, or while it is unknown.
     */
    @Nullable
    public static Vec3 middleOf(AutopilotBlockEntity autopilot) {
        FreighterFlight freighter = autopilot.flight(FreighterFlight.class);
        if (freighter == null || !freighter.middleKnown) {
            return null;
        }
        Direction nose = autopilot.getBlockState().getValue(AutopilotBlock.FACING);
        Vec3 ahead = Vec3.atLowerCornerOf(nose.getNormal()).scale(freighter.middleAhead);
        Vec3 right = Vec3.atLowerCornerOf(nose.getClockWise().getNormal()).scale(freighter.middleRight);
        return Vec3.atCenterOf(autopilot.getBlockPos()).add(ahead).add(right);
    }

    /**
     * Where the stern of the freighter an autopilot flies is among her blocks: astern of her middle in the last row of
     * her frame, where her mains are; her middle without a frame; null where {@link #middleOf} is.
     */
    @Nullable
    public static Vec3 sternOf(AutopilotBlockEntity autopilot) {
        Vec3 middle = middleOf(autopilot);
        BoundingBox frame = autopilot.frame();
        if (middle == null || frame == null) {
            return middle;
        }
        Direction astern = autopilot.getBlockState().getValue(AutopilotBlock.FACING).getOpposite();
        Direction.Axis axis = astern.getAxis();
        boolean positive = astern.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        int last = positive ? axis.choose(frame.maxX(), frame.maxY(), frame.maxZ()) : axis.choose(frame.minX(), frame.minY(), frame.minZ());
        double edge = last + 0.5;
        return axis == Direction.Axis.X ? new Vec3(edge, middle.y, middle.z) : new Vec3(middle.x, middle.y, edge);
    }

    /** What her pilot is doing, for play-testing. */
    public FreighterPilot.Report report() {
        return pilot.report();
    }

    /**
     * What holds the take-off of the freighter an autopilot flies, on either side, by the word its picture on her
     * console goes by: her ramp, say; null while nothing does, and for any other craft.
     */
    @Nullable
    public static String heldBy(AutopilotBlockEntity autopilot) {
        FreighterFlight freighter = autopilot.flight(FreighterFlight.class);
        return freighter == null ? null : freighter.heldBy;
    }

    /**
     * Asks the freighter an autopilot flies to boost, on the server, as her console's boost button does: while she
     * flies along (climbing or cruising) and her boost has rested since the last, her mains fire flat out for {@link
     * #BOOST_TICKS}, roaring at her stern, and she surges ahead holding her height and heading, then slows back to her
     * speed. What it came to, for the button to answer with.
     */
    public static BoostAnswer boost(ServerLevel level, @Nullable AutopilotBlockEntity autopilot) {
        FreighterFlight freighter = autopilot == null ? null : autopilot.flight(FreighterFlight.class);
        return freighter == null ? BoostAnswer.NO_SHIP : freighter.startBoost(level, autopilot);
    }

    private BoostAnswer startBoost(ServerLevel level, AutopilotBlockEntity autopilot) {
        if (!flyingAlong(autopilot)) {
            return BoostAnswer.NOT_FLYING;
        }
        long now = level.getGameTime();
        if (boostUntil != NO_BOOST && now < boostUntil + BOOST_REST_TICKS) {
            return BoostAnswer.RESTING;
        }
        boostUntil = now + BOOST_TICKS;
        roarAtStern(level, autopilot, SkylinersRegistry.SPEED_BOOST.get());
        autopilot.changed();
        return BoostAnswer.BOOSTING;
    }

    /**
     * Asks the freighter an autopilot flies to climb hard, on the server, as her console's climb button does: while
     * she flies along and her climb boost has rested since the last, her lift fires flat out and she climbs straight up
     * as fast as she can to a good height higher, which her autopilot then holds (see {@link FreighterClimbBoost}).
     * What it came to, for the button to answer with. Standing as she was built, never yet assembled, she is on the
     * ground, and it says so, rather than that there is no freighter.
     */
    public static FreighterClimbBoost.Answer climbBoost(ServerLevel level, @Nullable AutopilotBlockEntity autopilot) {
        FreighterFlight freighter = autopilot == null ? null : autopilot.flight(FreighterFlight.class);
        if (freighter == null) {
            return FreighterClimbBoost.Answer.NO_SHIP;
        }
        SableFlight.State state = SableFlight.read(autopilot, autopilot.getBlockState().getValue(AutopilotBlock.FACING));
        if (state == null) {
            return SkylinersConfig.FREIGHTER_CLIMB_BOOST.get() ? FreighterClimbBoost.Answer.NOT_FLYING : FreighterClimbBoost.Answer.SWITCHED_OFF;
        }
        return freighter.climb.start(level, autopilot, state.position().y);
    }

    /**
     * Where the climb boost of the freighter an autopilot flies stands at a game time, a client's eased one to draw it
     * by, on either side; not to be had for any other craft.
     */
    public static FreighterClimbBoost.Shown climbOf(AutopilotBlockEntity autopilot, double now) {
        FreighterFlight freighter = autopilot.flight(FreighterFlight.class);
        return freighter == null ? FreighterClimbBoost.Shown.UNAVAILABLE : freighter.climb.shown(now, FreighterJump.flyingAlong(autopilot));
    }

    /**
     * Asks the freighter an autopilot flies to slam down, on the server, as her console's slam button does: while she
     * flies along with the ground in reach and nothing would hold her take-off, she comes straight down where she is,
     * driven down hard and struck down with a crash (see {@link FreighterSlam}), and switches off on her legs. What it
     * came to, for the button to answer with. Standing as she was built, never yet assembled, she is on the ground, and
     * it says so, rather than that there is no freighter.
     */
    public static FreighterSlam.Answer slam(ServerLevel level, @Nullable AutopilotBlockEntity autopilot) {
        FreighterFlight freighter = autopilot == null ? null : autopilot.flight(FreighterFlight.class);
        if (freighter == null) {
            return FreighterSlam.Answer.NO_SHIP;
        }
        SableFlight.State state = SableFlight.read(autopilot, autopilot.getBlockState().getValue(AutopilotBlock.FACING));
        if (state == null) {
            return SkylinersConfig.FREIGHTER_SLAM_LANDING.get() ? FreighterSlam.Answer.NOT_FLYING : FreighterSlam.Answer.SWITCHED_OFF;
        }
        double radio = freighter.feetOverGround(level, autopilot);
        FreighterSlam.Answer answer = freighter.slam.start(level, autopilot, radio, heldNow(level, autopilot.getBlockPos()));
        if (answer == FreighterSlam.Answer.SLAMMING) {
            autopilot.phase = AutopilotBlockEntity.Phase.DESCENT;
            freighter.landHere(autopilot, state);
            autopilot.lateral = AutopilotBlockEntity.Lateral.HEADING;
            autopilot.landAtPin = false;
            autopilot.changed();
        }
        return answer;
    }

    /** Where the slam landing of the freighter an autopilot flies stands, on either side; unavailable for others. */
    public static FreighterSlam.Stage slamOf(AutopilotBlockEntity autopilot) {
        FreighterFlight freighter = autopilot.flight(FreighterFlight.class);
        return freighter == null ? FreighterSlam.Stage.UNAVAILABLE : freighter.slam.stage();
    }

    /** Whether her autopilot flies her along, climbing or cruising: not lifting off or coming down. */
    private static boolean flyingAlong(AutopilotBlockEntity autopilot) {
        return (autopilot.phase == AutopilotBlockEntity.Phase.CLIMB || autopilot.phase == AutopilotBlockEntity.Phase.CRUISE) && !autopilot.comingIn();
    }

    /** A sound of her mains heard at her stern, where they are, or at her control head where that is not known. */
    private static void roarAtStern(ServerLevel level, AutopilotBlockEntity autopilot, SoundEvent sound) {
        Vec3 stern = sternOf(autopilot);
        Vec3 at = stern == null ? Vec3.atCenterOf(autopilot.getBlockPos()) : stern;
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, BOOST_VOLUME, 1);
    }

    /**
     * Her boost this tick: firing while it lasts and she flies along, cut short as soon as she does not; then settling
     * while it rests, her pilot not learning her drag from how she slows; her mains heard cutting out as it ends.
     */
    private FreighterPilot.Boost boostNow(ServerLevel level, AutopilotBlockEntity autopilot) {
        long now = level.getGameTime();
        boolean firing = boostUntil != NO_BOOST && now < boostUntil && flyingAlong(autopilot);
        if (boosting && !firing) {
            boostUntil = Math.min(boostUntil, now);
            roarAtStern(level, autopilot, SkylinersRegistry.BOOSTER_CUTOUT.get());
            autopilot.changed();
        }
        boosting = firing;
        if (firing) {
            return FreighterPilot.Boost.FIRING;
        }
        return boostUntil != NO_BOOST && now < boostUntil + BOOST_REST_TICKS ? FreighterPilot.Boost.SETTLING : FreighterPilot.Boost.NONE;
    }

    /**
     * Where the boost of the freighter an autopilot flies stands at a game time, a client's eased one to draw it by, on
     * either side; ready for any other craft.
     */
    public static BoostShown boostOf(AutopilotBlockEntity autopilot, double now) {
        FreighterFlight freighter = autopilot.flight(FreighterFlight.class);
        if (freighter == null || freighter.boostUntil == NO_BOOST || now >= freighter.boostUntil + BOOST_REST_TICKS) {
            return BoostShown.READY;
        }
        if (now < freighter.boostUntil) {
            return new BoostShown(BoostStage.FIRING, (freighter.boostUntil - now) / BOOST_TICKS);
        }
        return new BoostShown(BoostStage.RESTING, (freighter.boostUntil + BOOST_REST_TICKS - now) / BOOST_REST_TICKS);
    }

    @Override
    boolean wired() {
        return pilot.wired();
    }

    @Override
    boolean assembledThroughSable() {
        return true;
    }

    @Override
    String unwiredKey() {
        return "message.skyliners.autopilot.no_thrusters";
    }

    @Override
    String assemblingKey() {
        return "message.skyliners.autopilot.assembling_freighter";
    }

    @Override
    String offKey() {
        return "message.skyliners.autopilot.off_freighter";
    }

    /** Her computer speaks for her flight deck, in its own gruff voice and words (see {@link #COMPUTER_SAYS}). */
    @Override
    Holder<SoundEvent> says(Holder<SoundEvent> announcement) {
        return COMPUTER_SAYS.getOrDefault(announcement, announcement);
    }

    @Override
    List<BlockPos> links() {
        return pilot.links();
    }

    @Override
    void turn(Rotation turn) {
        pilot.turn(turn);
        legs.turn(turn);
        jumpDrive = Turning.turn(jumpDrive, turn);
    }

    @Override
    void save(CompoundTag tag) {
        CompoundTag freighter = pilot.save();
        legs.save(freighter);
        tag.put("Freighter", freighter);
        if (jumpDrive != null) {
            tag.putLong("FreighterJumpDrive", jumpDrive.asLong());
        }
    }

    @Override
    void load(CompoundTag tag, Rotation turn) {
        CompoundTag freighter = tag.getCompound("Freighter");
        pilot.load(freighter, turn);
        legs.load(freighter, turn);
        if (!middleKnown) {
            knowMiddle(pilot.wiring());
        }
        // Saved by one built before she jumped: no drive to spool with it.
        jumpDrive = tag.contains("FreighterJumpDrive") ? Turning.turn(BlockPos.of(tag.getLong("FreighterJumpDrive")), turn) : null;
    }

    @Override
    void saveShared(CompoundTag tag) {
        if (middleKnown) {
            tag.putDouble("FreighterMiddleRight", middleRight);
            tag.putDouble("FreighterMiddleAhead", middleAhead);
        }
    }

    @Override
    void loadShared(CompoundTag tag) {
        middleKnown = tag.contains("FreighterMiddleRight") && tag.contains("FreighterMiddleAhead");
        middleRight = tag.getDouble("FreighterMiddleRight");
        middleAhead = tag.getDouble("FreighterMiddleAhead");
    }

    @Override
    void saveShown(CompoundTag tag) {
        lift.save(tag, "FreighterLift");
        mains.save(tag, "FreighterMains");
        if (boostUntil != NO_BOOST) {
            tag.putLong("FreighterBoostUntil", boostUntil);
        }
        if (jumpingSince != NO_JUMP) {
            tag.putLong("FreighterJumpingSince", jumpingSince);
        }
        tag.putBoolean("FreighterJumps", jumpsAllowed);
        if (heldBy != null) {
            tag.putString("FreighterHeldBy", heldBy);
        }
        climb.saveShown(tag);
        slam.saveShown(tag);
    }

    @Override
    void loadShown(CompoundTag tag) {
        lift.load(tag, "FreighterLift");
        mains.load(tag, "FreighterMains");
        boostUntil = tag.contains("FreighterBoostUntil") ? tag.getLong("FreighterBoostUntil") : NO_BOOST;
        jumpingSince = tag.contains("FreighterJumpingSince") ? tag.getLong("FreighterJumpingSince") : NO_JUMP;
        jumpsAllowed = tag.getBoolean("FreighterJumps");
        // Only a word a picture's name can be made of: her console draws the picture it names.
        String why = tag.getString("FreighterHeldBy");
        heldBy = ResourceLocation.isValidPath(why) && !why.isEmpty() ? why : null;
        climb.loadShown(tag);
        slam.loadShown(tag);
    }

    /**
     * Her take-off held too long: her autopilot switches off with a low note, and everyone aboard is told why, so she
     * stays on her legs rather than waiting for ever.
     */
    private void callOffTakeoff(ServerLevel level, AutopilotBlockEntity autopilot) {
        heldFor = 0;
        autopilot.disengage(SoundEvents.NOTE_BLOCK_BASS.value());
        for (ServerPlayer player : Aboard.players(autopilot)) {
            player.displayClientMessage(Component.translatable("message.skyliners.freighter.takeoff_called_off"), true);
        }
    }

    /**
     * Holds her take-off before her thrusters spool up for as long as something says so (see {@link #HOLDS}), her
     * console showing it meanwhile by the picture of the word given, {@code textures/gui/console/held_<why>.png}.
     */
    public static void holdTakeoffWhile(String why, BiPredicate<ServerLevel, BlockPos> hold) {
        HOLDS.add(new Hold(why, hold));
    }

    /**
     * What holds her take-off this tick (see {@link #HOLDS}), set to take off, her autopilot at a place: by its word,
     * or null for nothing.
     */
    @Nullable
    private static String takeoffHeld(ServerLevel level, BlockPos origin, AutopilotBlockEntity.Phase phase) {
        return phase == AutopilotBlockEntity.Phase.TAKEOFF ? heldNow(level, origin) : null;
    }

    /**
     * What would hold her take-off now, whatever she is doing (see {@link #HOLDS}), her autopilot at a place: by its
     * word, or null for nothing. Her slam landing holds off for it.
     */
    @Nullable
    private static String heldNow(ServerLevel level, BlockPos origin) {
        for (Hold hold : HOLDS) {
            if (hold.holds().test(level, origin)) {
                return hold.why();
            }
        }
        return null;
    }

    @Override
    void engage(AutopilotBlockEntity autopilot, SableFlight.State state) {
        pilot.begin(state);
    }

    /**
     * Each tick of hers assembled: she is kept loaded, as a zeppelin is; lightning strikes her in a storm and she notes
     * the villages below; she lands by herself once nobody has been aboard a while; everyone aboard is welcomed to
     * space as she climbs past its edge; engaged, she flies (see {@link FreighterPilot}) until she has come down on her
     * legs and stood there a moment, switching herself off with a bell.
     */
    @Override
    void tick(ServerLevel level, AutopilotBlockEntity autopilot, SableFlight.State flight) {
        double radio = aloft(level, autopilot, flight);
        autopilot.watchUnattended(level, flight, radio);
        String why = takeoffHeld(level, autopilot.getBlockPos(), autopilot.phase);
        boolean held = why != null;
        heldFor = held ? heldFor + 1 : 0;
        if (!Objects.equals(why, heldBy)) {
            heldBy = why;
            autopilot.changed();
        }
        if (heldFor > TAKEOFF_HELD_LONGEST) {
            callOffTakeoff(level, autopilot);
        }
        AutopilotBlockEntity.Phase phase = autopilot.phase;
        boolean legsDown = legsDown(level, autopilot, phase);
        climb.tick(level, autopilot, flight.position().y);
        if (phase != AutopilotBlockEntity.Phase.OFF && !held) {
            FreighterPilot.Orders orders = new FreighterPilot.Orders(phase, autopilot.targetAltitude, autopilot.targetSpeed,
                autopilot.targetHeading, autopilot.lateral, autopilot.orbitX, autopilot.orbitZ, autopilot.orbitRadius, autopilot.landAtPin,
                legsDown, boostNow(level, autopilot), climb.firing(), slam.firing(), slam.legsDueIn());
            AutopilotBlockEntity.Phase next = pilot.fly(level, autopilot.getBlockPos(), orders, flight, radio,
                SableFlight.mass(autopilot), SableFlight.centreOfMass(autopilot));
            if (next == AutopilotBlockEntity.Phase.OFF) {
                pilot.stop(level, autopilot.getBlockPos());
                autopilot.settle(level);
            } else {
                autopilot.advance(next, AutopilotBlockEntity.Phase.DESCENT);
            }
            sayNoGround(level, autopilot, pilot.noGroundAtPin(orders));
        }
        slam.tick(level, autopilot, flight, pilot.middle(flight), radio, pilot.wiring().clusters(), pilot.wiring().feet(),
            heldNow(level, autopilot.getBlockPos()));
        legs.tick(level, autopilot.getBlockPos(), autopilot.phase, radio, flight.velocity().y);
        long now = level.getGameTime();
        boolean liftShown = lift.take(pilot.report().lift(), now);
        double mainsShare = pilot.mainsShare();
        boolean mainsShown = mains.take(mainsShare < MAINS_SHOWN_FROM ? 0 : mainsShare, now);
        if (liftShown || mainsShown) {
            autopilot.changed();
        }
        glow.tick(level, autopilot.getBlockPos(), pilot.wiring().grille(), lift.shown(), mains.shown(), now);
        plumes.tick(level, autopilot.getBlockPos(), pilot.wiring().clusters(), pilot.wiring().mains(), radio);
        watchArrival(autopilot, flight, radio);
        jumpTick(level, autopilot, flight);
    }

    /**
     * Whether her legs are down for her to land on: all down and locked, or awaited too long as she comes down to land
     * (see {@link #LEGS_AWAITED_LONGEST}).
     */
    private boolean legsDown(ServerLevel level, AutopilotBlockEntity autopilot, AutopilotBlockEntity.Phase phase) {
        boolean down = legs.down(level, autopilot.getBlockPos());
        boolean landing = phase == AutopilotBlockEntity.Phase.DESCENT || phase == AutopilotBlockEntity.Phase.LAND;
        legsAwaited = landing && !down ? legsAwaited + 1 : 0;
        return down || legsAwaited > LEGS_AWAITED_LONGEST;
    }

    /**
     * Set to land at a pin with no clear, level ground round it, she says so once to everyone aboard as she holds over
     * it, the sky's lowest note saying it to those who cannot read: set another pin, or land her where she is.
     */
    private void sayNoGround(ServerLevel level, AutopilotBlockEntity autopilot, boolean noGround) {
        if (noGround && !toldNoGround) {
            Component message = Component.translatable("message.skyliners.autopilot.no_ground_freighter");
            Aboard.players(autopilot).forEach(player -> player.displayClientMessage(message, false));
            level.playSound(null, autopilot.getBlockPos(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS,
                AutopilotBlockEntity.NOTE_VOLUME, AutopilotBlockEntity.NOTE_LOW);
        }
        toldNoGround = noGround;
    }

    @Override
    void stop(ServerLevel level, AutopilotBlockEntity autopilot) {
        pilot.stop(level, autopilot.getBlockPos());
    }

    /** Told to land, or switched off in the air: she stops where she is, holding the way she points, and comes down. */
    @Override
    void landHere(AutopilotBlockEntity autopilot, SableFlight.State state) {
        pilot.landHere(state);
        autopilot.targetHeading = state.heading();
    }

    @Override
    String comingDownKey() {
        return "message.skyliners.autopilot.coming_down_freighter";
    }

    @Override
    String offOnceDownKey() {
        return "message.skyliners.autopilot.off_once_down_freighter";
    }

    @Override
    int climb() {
        return CLIMB;
    }

    @Override
    double cruise() {
        return CRUISE;
    }

    @Override
    double feetOverGround(Level level, AutopilotBlockEntity autopilot) {
        return pilot.feetOverGround(level, autopilot.getBlockPos());
    }
}
