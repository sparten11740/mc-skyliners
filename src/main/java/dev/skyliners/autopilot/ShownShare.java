package dev.skyliners.autopilot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * A share of full, from 0 at rest to 1, as the clients are shown it in the autopilot's update, for what they play and
 * draw by it, such as how hard a ship's engines fire: in so many steps, sent at once as it starts or stops, otherwise
 * once it is a step off what they were shown and not more often than so many ticks, so a share wobbling between two
 * steps costs next to nothing. Kept in the update to the clients only, never in the world's save: a ship loaded again
 * is at rest until she is flown.
 */
final class ShownShare {
    /** Long before any game time: never. */
    private static final long NEVER = Long.MIN_VALUE / 2;

    private final int steps;
    private final int every;
    /** The share as the clients were last shown it, and when it was sent. */
    private float shown;
    private long shownAt = NEVER;

    /** A share shown in so many steps from none to full, a change of step sent at most every so many ticks. */
    ShownShare(int steps, int every) {
        this.steps = steps;
        this.every = every;
    }

    /**
     * Whether the clients are to be shown the share afresh, now it is this: at once as it starts or stops, otherwise
     * once it is a step off what they were shown, and not too often.
     */
    boolean take(double share, long now) {
        int step = share <= 0 ? 0 : Math.max(1, (int) Math.round(share * steps));
        int was = Math.round(shown * steps);
        boolean starting = (step > 0) != (was > 0);
        if (step == was || !starting && now - shownAt < every) {
            return false;
        }
        shown = (float) step / steps;
        shownAt = now;
        return true;
    }

    /** The share as the clients are shown it; 0 at rest. */
    float shown() {
        return shown;
    }

    /** The share, into the update sent to the clients under its key. */
    void save(CompoundTag tag, String key) {
        tag.putFloat(key, shown);
    }

    /** The share from the update the server sent; missing, as from a save, at rest. */
    void load(CompoundTag tag, String key) {
        shown = Mth.clamp(tag.getFloat(key), 0, 1);
    }
}
