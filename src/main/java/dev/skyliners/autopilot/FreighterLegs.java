package dev.skyliners.autopilot;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.block.LegBayBlockEntity;
import dev.skyliners.block.Relays;
import dev.skyliners.block.RetractableLegBlockEntity;
import dev.skyliners.block.Turning;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

/**
 * The freighter's landing legs in her autopilot's hands (see {@link LegBayBlockEntity}): drawn up into her belly once
 * she is well clear of the ground climbing or cruising, and let down again as she comes down to land, or whenever she
 * flies low or sinks toward the ground fast enough to reach it before they would be down, so she always comes down on
 * her feet; her pilot holds her landing over the ground until they are down and locked (see {@link #down}). With the
 * server config's {@code freighter.retractingLegs} off they still come down, but no longer go up. A freighter built
 * before her legs drew up has no bays: her legs stay as they are, and count as down.
 */
final class FreighterLegs {
    /** Climbing or cruising with her feet this far over the ground, in blocks, she draws her legs up: well clear. */
    private static final double RAISE_HEIGHT = 16;
    /**
     * With her feet lower than this over the ground, in blocks, she lets her legs down: a little over the height she
     * hovers at before she comes the last of the way down to land (see {@link FreighterPilot}), so they are down and
     * locked while she hovers there, and short enough of {@link #RAISE_HEIGHT} that she does not raise and lower them
     * over and over flying level near it.
     */
    private static final double LOWER_HEIGHT = 12;
    /**
     * How far ahead she looks at how fast she sinks, in seconds, to let her legs down in time below {@link
     * #RAISE_HEIGHT}, and to leave them down rather than draw them up while she sinks fast above it: as long as they
     * take to come down and lock, and a second more.
     */
    private static final double LOOK_AHEAD = (double) LegBayBlockEntity.LOWER_TICKS / SharedConstants.TICKS_PER_SECOND + 1;

    /** Her leg bays, as offsets from her control head; none for a freighter built before her legs drew up. */
    private List<BlockPos> bays = List.of();

    /** Wired when she is built: her leg bays, as offsets from her control head. */
    void configure(List<BlockPos> bays) {
        this.bays = List.copyOf(bays);
    }

    /** Her bays turned as her control head has turned. */
    void turn(Rotation turn) {
        bays = Turning.turn(bays, turn);
    }

    /**
     * Each tick of hers in the air or on the ground: her legs let down unless she climbs or cruises, and drawn up once
     * she does so well clear of the ground and not sinking toward it, from how high her feet are over it, in blocks, and
     * how fast she climbs, in blocks a second. Otherwise they stay as they are: sinking to a lower height well clear of
     * the ground, she neither lets them down nor draws them up again as she levels off.
     */
    void tick(ServerLevel level, BlockPos origin, AutopilotBlockEntity.Phase phase, double feetOverGround, double climb) {
        if (bays.isEmpty()) {
            return;
        }
        double soon = feetOverGround + Math.min(0, climb) * LOOK_AHEAD;
        boolean flying = phase == AutopilotBlockEntity.Phase.CLIMB || phase == AutopilotBlockEntity.Phase.CRUISE;
        boolean low = feetOverGround < LOWER_HEIGHT || feetOverGround < RAISE_HEIGHT && soon < LOWER_HEIGHT;
        if (!flying || low) {
            legs(level, origin).forEach(leg -> leg.lower(level));
        } else if (feetOverGround > RAISE_HEIGHT && soon > LOWER_HEIGHT && SkylinersConfig.FREIGHTER_RETRACTING_LEGS.get()) {
            legs(level, origin).forEach(leg -> leg.raise(level));
        }
    }

    /**
     * Whether every leg of hers is down and locked, as far as they are loaded; so with no bays, her legs fixed. A leg
     * unloaded counts as down: it is unloaded only with her, and then she is not flown.
     */
    boolean down(Level level, BlockPos origin) {
        return legs(level, origin).stream().allMatch(leg -> leg.motion() == RetractableLegBlockEntity.Motion.DOWN);
    }

    /**
     * Where her legs are, for play-testing: all down, raising, up or lowering together, as that motion; null with them
     * moving apart, or with none.
     */
    @Nullable
    RetractableLegBlockEntity.Motion motion(Level level, BlockPos origin) {
        List<LegBayBlockEntity> legs = legs(level, origin);
        if (legs.isEmpty()) {
            return null;
        }
        RetractableLegBlockEntity.Motion first = legs.get(0).motion();
        return legs.stream().allMatch(leg -> leg.motion() == first) ? first : null;
    }

    /** How many legs she draws up, her bays' count. */
    int count() {
        return bays.size();
    }

    /** Her legs that are there, loaded. */
    private List<LegBayBlockEntity> legs(Level level, BlockPos origin) {
        List<LegBayBlockEntity> legs = new ArrayList<>();
        for (BlockPos bay : bays) {
            if (level.getBlockEntity(origin.offset(bay)) instanceof LegBayBlockEntity leg) {
                legs.add(leg);
            }
        }
        return legs;
    }

    /** Her bays, into her flight's save. */
    void save(CompoundTag tag) {
        tag.put("LegBays", Relays.write(bays));
    }

    /** Her bays as saved, turned as her control head has turned since; none in a save from before her legs drew up. */
    void load(CompoundTag tag, Rotation turn) {
        bays = Turning.turn(Relays.read(tag.getList("LegBays", Tag.TAG_INT_ARRAY)), turn);
    }
}
