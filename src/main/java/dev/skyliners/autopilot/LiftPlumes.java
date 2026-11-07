package dev.skyliners.autopilot;

import dev.skyliners.airframe.EngineFlames;
import dev.skyliners.airframe.Thrusters;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * The freighter's lift thrusters' plumes, shown only close over the ground, as she lifts off and comes down onto her
 * legs, and left out higher up (see {@link Thrusters#plume}): every puff of a plume is sent to every player near, and
 * her twenty lift thrusters' would be the most of what anyone near her is sent, all through her flight, unseen under
 * her. Her mains' plumes, behind her, always show.
 */
final class LiftPlumes {
    /**
     * How high over the ground her lift thrusters' plumes show, in blocks, and how much higher she must rise before
     * they are left out, so that hovering about there does not set them back and forth.
     */
    private static final double SHOWN_UNDER = 16;
    private static final double LEFT_OUT_OVER = 20;
    /** The way her lift thrusters push, toward their relays over them. */
    private static final List<Direction> UP = List.of(Direction.UP);

    /**
     * Whether her lift thrusters' plumes show, and whether that is known: not until first set, which sets them whatever
     * they were left at.
     */
    private boolean shown;
    private boolean known;

    /**
     * Each tick of hers: her lift thrusters' plumes, by their relays as offsets from her control head, set by how high
     * she is over the ground, in blocks.
     */
    void tick(ServerLevel level, BlockPos origin, List<List<BlockPos>> clusters, double radio) {
        boolean hidden = known && !shown;
        boolean wanted = radio < (hidden ? SHOWN_UNDER : LEFT_OUT_OVER);
        if (known && shown == wanted) {
            return;
        }
        known = true;
        shown = wanted;
        EngineFlames flames = wanted ? EngineFlames.PROPULSION_PLUME : EngineFlames.NONE;
        for (List<BlockPos> cluster : clusters) {
            for (BlockPos relay : cluster) {
                Thrusters.plume(level, origin.offset(relay), flames, UP);
            }
        }
    }
}
