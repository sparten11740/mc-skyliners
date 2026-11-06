package dev.skyliners.autopilot;

import dev.skyliners.Aboard;
import dev.skyliners.airframe.ZeppelinSize;
import dev.skyliners.block.Announcement;
import dev.skyliners.compat.SableFlight;
import dev.skyliners.parachute.SafetyCanopy;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

/**
 * The zeppelin in her autopilot's hands (see {@link CraftFlight}): she rises straight off the ground on her hot air
 * and settles back onto it anywhere, flown by {@link ZeppelinPilot}, and off she is flown by her control car's hand
 * controls once handed over to them. How big her hull is goes with her, for the clients to paint her by.
 */
public final class ZeppelinFlight extends CraftFlight {
    /** How often, flying her, it shows what it does on her control car's hand controls, in ticks. */
    private static final int SHOW_CONTROLS = 20;
    /** Engaged on the ground she rises this far above it, in blocks. */
    private static final int CLIMB = 60;
    /** Engaged on the ground she cruises at this speed, in blocks a second. */
    private static final double CRUISE = 15;

    private final ZeppelinPilot pilot = new ZeppelinPilot();
    /** How big her hull is (see {@link ZeppelinSize}); one saved before there were sizes is full size. */
    private ZeppelinSize size = ZeppelinSize.FULL;

    @Override
    public Craft craft() {
        return Craft.ZEPPELIN;
    }

    /**
     * Wired when she is built: her gas cells' burners and her engines' relays as offsets from the autopilot, how far
     * her hot air stands above it, how far aft of it the cells' middle is, and how big her hull is.
     */
    public void configure(List<ZeppelinPilot.Cell> cells, Map<Integer, List<BlockPos>> ahead, Map<Integer, List<BlockPos>> astern,
                          double gasAbove, double middleAft, ZeppelinSize size) {
        this.size = size;
        pilot.configure(cells, ahead, astern, gasAbove, middleAft);
    }

    /** Wired when she is built: her control car's hand controls, as offsets from the autopilot. */
    public void wireControls(BlockPos rudder, BlockPos elevator, BlockPos port, BlockPos starboard, BlockPos lever) {
        pilot.wireControls(rudder, elevator, port, starboard, lever);
    }

    /** How big her hull is. */
    public ZeppelinSize size() {
        return size;
    }

    /** How big the hull of what an autopilot flies is: a zeppelin's own, or full size for anything else. */
    public static ZeppelinSize sizeOf(AutopilotBlockEntity autopilot) {
        ZeppelinFlight zeppelin = autopilot.flight(ZeppelinFlight.class);
        return zeppelin == null ? ZeppelinSize.FULL : zeppelin.size;
    }

    /** What her pilot is doing, for play-testing. */
    public ZeppelinPilot.Report report() {
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
        return "message.skyliners.autopilot.no_burners";
    }

    @Override
    String assemblingKey() {
        return "message.skyliners.autopilot.assembling_zeppelin";
    }

    @Override
    String offKey() {
        return "message.skyliners.autopilot.off_zeppelin";
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
        tag.put("Zeppelin", pilot.save());
    }

    @Override
    void load(CompoundTag tag, Rotation turn) {
        pilot.load(tag.getCompound("Zeppelin"), turn);
    }

    @Override
    void saveShared(CompoundTag tag) {
        tag.putString("ZeppelinSize", size.name());
    }

    @Override
    void loadShared(CompoundTag tag) {
        size = ZeppelinSize.named(tag.getString("ZeppelinSize"));
    }

    @Override
    void engage(AutopilotBlockEntity autopilot, SableFlight.State state) {
        pilot.engage(state);
    }

    /**
     * Each tick of hers assembled: lightning strikes her in a storm and she notes the villages below, as an airliner
     * does; she notes who is aboard, for the safety canopy (see {@link SafetyCanopy}); she comes down by herself once
     * nobody has been aboard a while; engaged, she flies (see {@link ZeppelinPilot}), showing what she does on her
     * control car's hand controls, until she is down on the ground and rests there, switching herself off; switched
     * off, she is flown by those controls once handed over to them.
     */
    @Override
    void tick(ServerLevel level, AutopilotBlockEntity autopilot, SableFlight.State flight) {
        BlockPos origin = autopilot.getBlockPos();
        if (Math.floorMod(level.getGameTime() + origin.asLong(), AutopilotBlockEntity.ABOARD_LOOK) == 0) {
            SafetyCanopy.aboard(Aboard.players(autopilot), level.getGameTime());
        }
        double radio = keptAloft(level, autopilot, flight);
        autopilot.watchUnattended(level, flight, radio);
        if (autopilot.phase != AutopilotBlockEntity.Phase.OFF) {
            ZeppelinPilot.Orders orders = new ZeppelinPilot.Orders(autopilot.phase, autopilot.targetAltitude, autopilot.targetSpeed,
                autopilot.targetHeading, autopilot.lateral, autopilot.orbitX, autopilot.orbitZ, autopilot.orbitRadius, autopilot.landAtPin);
            follow(level, autopilot, flight, pilot.fly(level, origin, orders, flight, radio, SableFlight.mass(autopilot)));
            if (autopilot.phase != AutopilotBlockEntity.Phase.OFF
                && Math.floorMod(level.getGameTime() + origin.asLong(), SHOW_CONTROLS) == 0) {
                pilot.showControls(level, origin);
            }
        } else {
            pilot.flyByHand(level, origin, flight, radio, SableFlight.mass(autopilot));
        }
        watchArrival(autopilot, flight, radio);
    }

    /**
     * Goes on in the phase her pilot flies her in: switched off, she has either rested on the ground or, still rising,
     * given up too heavy; coming down at a pin, the cabin is told she is landing.
     */
    private void follow(ServerLevel level, AutopilotBlockEntity autopilot, SableFlight.State flight, AutopilotBlockEntity.Phase next) {
        if (next == AutopilotBlockEntity.Phase.OFF && autopilot.phase == AutopilotBlockEntity.Phase.TAKEOFF) {
            tooHeavy(level, autopilot, flight);
            return;
        }
        if (next == AutopilotBlockEntity.Phase.OFF) {
            pilot.rest(level, autopilot.getBlockPos(), flight, SableFlight.mass(autopilot));
            autopilot.settle(level);
            return;
        }
        autopilot.advance(next, AutopilotBlockEntity.Phase.LAND);
    }

    /**
     * Still on the ground a minute after she was told to rise, too heavy for her burners: she rests there again and
     * switches herself off with a thud, saying why.
     */
    private void tooHeavy(ServerLevel level, AutopilotBlockEntity autopilot, SableFlight.State flight) {
        pilot.rest(level, autopilot.getBlockPos(), flight, SableFlight.mass(autopilot));
        autopilot.phase = AutopilotBlockEntity.Phase.OFF;
        autopilot.changed();
        autopilot.thud();
        AutopilotBlockEntity.tell(level, flight.position(), Component.translatable("message.skyliners.autopilot.too_heavy"));
    }

    @Override
    void stop(ServerLevel level, AutopilotBlockEntity autopilot) {
        pilot.stop(level, autopilot.getBlockPos());
    }

    /**
     * Told to land: she stops where she is, holding the way she points, and sinks gently straight down to the ground,
     * where she rests on her wheels (see {@link ZeppelinPilot}).
     */
    @Nullable
    @Override
    Component comeDown(AutopilotBlockEntity autopilot, SableFlight.State state) {
        autopilot.announcements.announce(Announcement.LANDING, autopilot);
        autopilot.phase = AutopilotBlockEntity.Phase.LAND;
        autopilot.lateral = AutopilotBlockEntity.Lateral.HEADING;
        autopilot.targetHeading = state.heading();
        autopilot.targetSpeed = 0;
        autopilot.changed();
        return Component.translatable("message.skyliners.autopilot.coming_down");
    }

    @Override
    int climb() {
        return CLIMB;
    }

    @Override
    double cruise() {
        return CRUISE;
    }

    /**
     * No higher than her burners can lift her (see {@link ZeppelinPilot#ceiling}) once she is assembled, so she is
     * never sent climbing for good at full burn toward a height she never reaches.
     */
    @Override
    double ceiling(Level level, AutopilotBlockEntity autopilot, double highest) {
        SableFlight.State state = SableFlight.read(autopilot, autopilot.nose());
        double floats = state == null ? Double.NaN : pilot.ceiling(state, SableFlight.mass(autopilot));
        return Double.isNaN(floats) ? highest : Math.min(highest, floats);
    }
}
