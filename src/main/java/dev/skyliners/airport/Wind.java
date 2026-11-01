package dev.skyliners.airport;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * The wind over an airfield, as its instruments read it: the way it blows from, as a compass bearing (0 north, 90
 * east), and how strong it is, in knots. It drifts slowly round the compass and rises and falls through the day, each
 * quarter of a day drawn afresh from that quarter's number and eased into from the one before, so the same moment of
 * the same day blows the same way on the server and on every client without anything being sent; rain freshens it and
 * a thunderstorm makes a gale of it. The tower's weather screen reports it and its wind sock flies with it.
 */
public record Wind(float from, float knots) {
    /** How long one drawing of the wind lasts before it has eased into the next, in ticks: a quarter of a day. */
    private static final double DRIFT_TICKS = 6000;
    /** How far round the compass the wind can swing from one drawing to the next, in degrees. */
    private static final double SWING = 360;
    /** The strongest wind on a clear day, and how much rain and a thunderstorm add to it, in knots. */
    private static final float CLEAR_KNOTS = 8;
    private static final float RAIN_KNOTS = 9;
    private static final float THUNDER_KNOTS = 12;
    /** The wind a sock stands straight out in, in knots. */
    private static final float FULL_KNOTS = 25;
    /** What tells the direction's drawings from the strength's. */
    private static final long DIRECTION = 0x5EED_D1EL;
    private static final long STRENGTH = 0x5EED_5BEL;

    /** The wind over a level now, freshening smoothly as the rain and the thunder set in. */
    public static Wind at(Level level, float partialTick) {
        double drift = level.getDayTime() / DRIFT_TICKS;
        float from = (float) (drawn(drift, DIRECTION) * SWING % 360);
        float knots = (float) drawn(drift, STRENGTH) * CLEAR_KNOTS + RAIN_KNOTS * level.getRainLevel(partialTick)
            + THUNDER_KNOTS * level.getThunderLevel(partialTick);
        return new Wind(from, knots);
    }

    /** The way it blows toward, as a compass bearing: the way a wind sock points. */
    public float toward() {
        return (from + 180) % 360;
    }

    /** How far a wind sock stands out in it: 0 hanging limp, about half in rain, 1 straight out in a storm. */
    public float fill() {
        return Mth.clamp(knots / FULL_KNOTS, 0, 1);
    }

    /** A value between 0 and 1 at a point in the drift, eased from the drawing before it to the one after. */
    private static double drawn(double drift, long salt) {
        long quarter = (long) Math.floor(drift);
        double along = drift - quarter;
        double eased = along * along * (3 - 2 * along);
        return Mth.lerp(eased, draw(quarter, salt), draw(quarter + 1, salt));
    }

    /** One drawing, between 0 and 1, the same wherever it is drawn. */
    private static double draw(long quarter, long salt) {
        long mixed = (quarter ^ salt) * 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        return (mixed >>> 11) * 0x1.0p-53;
    }
}
