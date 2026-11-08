package dev.skyliners.autopilot;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.airframe.EngineFlames;
import dev.skyliners.airframe.Thrusters;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Which of the freighter's thrusters trail Create Propulsion's plume (see {@link Thrusters#plume}), every puff of which
 * the server sends to every player near and each of their clients then moves along every tick. Her lift thrusters'
 * plumes show only close over the ground, as she lifts off and comes down onto her legs, and are left out higher up,
 * unseen under her. Of her lift thrusters only the middle one of each cluster of five trails one (the first of each
 * cluster as her layout lists them), a jet down to the ground under each corner of her belly, and of her six mains the
 * outer and the inner pairs, four plumes in a row behind her with all six of her engines glowing over them (the
 * client's {@code FreighterEngineGlow}). The server config's {@code freighter.everyPlume} has every one of them trail
 * one, as she was first built, sending half as many puffs again as she flies along and five times as many as she lifts
 * off. Her mains' plumes are set on her first tick after she is loaded, so a freighter built with all six trailing one
 * is thinned out too.
 */
final class FreighterPlumes {
    /**
     * How high over the ground her lift thrusters' plumes show, in blocks, and how much higher she must rise before
     * they are left out, so that hovering about there does not set them back and forth.
     */
    private static final double SHOWN_UNDER = 16;
    private static final double LEFT_OUT_OVER = 20;
    /** The way her lift thrusters push, toward their relays over them. */
    private static final List<Direction> UP = List.of(Direction.UP);
    /** How many mains she is built with, her port's outermost first, and which of them trail no plume: the middle pair. */
    private static final int MAINS = 6;
    private static final List<Integer> MAINS_WITHOUT = List.of(1, 4);

    /**
     * Whether her lift thrusters' plumes show, and whether that is known: not until first set, which sets them whatever
     * they were left at, and her mains' with them.
     */
    private boolean shown;
    private boolean known;

    /**
     * Each tick of hers: her lift thrusters' plumes, by their relays as offsets from her control head, set by how high
     * she is over the ground, in blocks; and on her first, her mains' plumes, by theirs.
     */
    void tick(ServerLevel level, BlockPos origin, List<List<BlockPos>> clusters, List<BlockPos> mains, double radio) {
        boolean hidden = known && !shown;
        boolean wanted = radio < (hidden ? SHOWN_UNDER : LEFT_OUT_OVER);
        if (known && shown == wanted) {
            return;
        }
        boolean every = SkylinersConfig.FREIGHTER_EVERY_PLUME.get();
        if (!known) {
            plumeMains(level, origin, mains, every);
        }
        known = true;
        shown = wanted;
        for (List<BlockPos> cluster : clusters) {
            for (int at = 0; at < cluster.size(); at++) {
                boolean trails = wanted && (every || at == 0);
                Thrusters.plume(level, origin.offset(cluster.get(at)), trails ? EngineFlames.PROPULSION_PLUME : EngineFlames.NONE, UP);
            }
        }
    }

    /**
     * Her mains' plumes, by their relays as offsets from her control head: all but the middle pair's, or every one's,
     * and every one's on a freighter not built with six.
     */
    private static void plumeMains(ServerLevel level, BlockPos origin, List<BlockPos> mains, boolean every) {
        for (int main = 0; main < mains.size(); main++) {
            boolean trails = every || mains.size() != MAINS || !MAINS_WITHOUT.contains(main);
            Thrusters.plume(level, origin.offset(mains.get(main)), trails ? EngineFlames.PROPULSION_PLUME : EngineFlames.NONE,
                Direction.Plane.HORIZONTAL);
        }
    }
}
