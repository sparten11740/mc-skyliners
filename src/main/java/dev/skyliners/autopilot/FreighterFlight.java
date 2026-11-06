package dev.skyliners.autopilot;

import dev.skyliners.compat.SableFlight;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

/**
 * The freighter in her autopilot's hands (see {@link StandingFlight}): she lifts straight off her legs and comes
 * straight down onto them, flown by {@link FreighterPilot}; she lands by herself once nobody has been aboard a while;
 * and she can climb to space as the rocket does.
 */
public final class FreighterFlight extends StandingFlight {
    /** Engaged on the ground she rises this far above it, in blocks. */
    private static final int CLIMB = 40;
    /** Engaged on the ground she cruises at this speed, in blocks a second. */
    private static final double CRUISE = 20;

    private final FreighterPilot pilot = new FreighterPilot();

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
