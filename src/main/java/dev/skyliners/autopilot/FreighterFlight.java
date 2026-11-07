package dev.skyliners.autopilot;

import dev.skyliners.block.AutopilotBlock;
import dev.skyliners.compat.SableFlight;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The freighter in her autopilot's hands (see {@link StandingFlight}): she lifts straight off her legs and comes
 * straight down onto them, flown by {@link FreighterPilot}; she lands by herself once nobody has been aboard a while;
 * and she can climb to space as the rocket does. How hard her lift thrusters and her mains fire is shown to the
 * clients, who play her engines' roar by it (see the client's FreighterRoar), and where her disc's middle is among her
 * blocks goes with her, for them to place it.
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

    private final FreighterPilot pilot = new FreighterPilot();
    /** How hard her lift thrusters and her mains fire, as the clients are shown it. */
    private final ShownShare lift = new ShownShare(SHOWN_STEPS, SHOWN_EVERY);
    private final ShownShare mains = new ShownShare(SHOWN_STEPS, SHOWN_EVERY);
    /** Her engine grille glowing with her engines, and her lift thrusters' plumes shown close over the ground. */
    private final EngineGlow glow = new EngineGlow();
    private final LiftPlumes liftPlumes = new LiftPlumes();
    /**
     * Where her disc's middle is from her control head, so many blocks to its right and ahead of it, on the clients as
     * on the server; unknown for one built before it was kept, until her pilot's wiring gives it.
     */
    private double middleRight;
    private double middleAhead;
    private boolean middleKnown;

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

    @Override
    List<BlockPos> links() {
        return pilot.links();
    }

    @Override
    void turn(Rotation turn) {
        pilot.turn(turn);
    }

    @Override
    void save(CompoundTag tag) {
        tag.put("Freighter", pilot.save());
    }

    @Override
    void load(CompoundTag tag, Rotation turn) {
        pilot.load(tag.getCompound("Freighter"), turn);
        if (!middleKnown) {
            knowMiddle(pilot.wiring());
        }
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
    }

    @Override
    void loadShown(CompoundTag tag) {
        lift.load(tag, "FreighterLift");
        mains.load(tag, "FreighterMains");
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
        AutopilotBlockEntity.Phase phase = autopilot.phase;
        if (phase != AutopilotBlockEntity.Phase.OFF) {
            FreighterPilot.Orders orders = new FreighterPilot.Orders(phase, autopilot.targetAltitude, autopilot.targetSpeed,
                autopilot.targetHeading, autopilot.lateral, autopilot.orbitX, autopilot.orbitZ, autopilot.orbitRadius, autopilot.landAtPin);
            AutopilotBlockEntity.Phase next = pilot.fly(level, autopilot.getBlockPos(), orders, flight, radio,
                SableFlight.mass(autopilot), SableFlight.centreOfMass(autopilot));
            if (next == AutopilotBlockEntity.Phase.OFF) {
                pilot.stop(level, autopilot.getBlockPos());
                autopilot.settle(level);
            } else {
                autopilot.advance(next, AutopilotBlockEntity.Phase.DESCENT);
            }
        }
        long now = level.getGameTime();
        boolean liftShown = lift.take(pilot.report().lift(), now);
        double mainsShare = pilot.mainsShare();
        boolean mainsShown = mains.take(mainsShare < MAINS_SHOWN_FROM ? 0 : mainsShare, now);
        if (liftShown || mainsShown) {
            autopilot.changed();
        }
        glow.tick(level, autopilot.getBlockPos(), pilot.wiring().grille(), lift.shown(), mains.shown(), now);
        liftPlumes.tick(level, autopilot.getBlockPos(), pilot.wiring().clusters(), radio);
        watchArrival(autopilot, flight, radio);
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
