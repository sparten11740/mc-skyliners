package dev.aerolarge.autopilot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The search for a runway near the pin: touchdown points round it, nearest first, each with every heading in turn,
 * checked against the land as surveyed (see {@link RunwayCheck}) a few at a time each tick: each at a glance, which rules
 * most out and counts what stands under the wings, no more of it than the best one so far has, and one that looks clear
 * closely too. The first clear one found is chosen. A search that goes round them all with none clear chooses the one
 * with the fewest things standing under its wings, if any can be landed on at all, once it has had a fair look at the
 * land: once the airliner has flown over the pin, and either every runway round it was decided or it has been round a
 * few times since. Otherwise it waits a moment, while more land is surveyed, and goes round again.
 */
final class RunwaySearch {
    /**
     * How far from the pin the airliner may touch down, in blocks: far enough to find clear land beside a village or the
     * tower the pin is on, which leave no room for its wings right next to them.
     */
    static final int REACH = 320;
    /** How far apart the touchdown points looked at are, in blocks, across and along. */
    private static final int SPACING = 24;
    /** How far apart the headings looked at are, in degrees. */
    private static final int HEADING_STEP = 15;
    private static final int HEADINGS = 360 / HEADING_STEP;
    /**
     * How many columns of land it looks at each tick, at most, though one runway is always looked at to its end: some
     * tens of thousands of quick looks into what the survey holds, a millisecond or so.
     */
    private static final int SAMPLES_PER_TICK = 40_000;
    /** How long it waits between one search round the pin and the next, in ticks, while more land is surveyed. */
    private static final int REST = 10 * 20;
    /**
     * How many rounds it makes after flying over the pin, with land round it still unsurveyed, before it settles for a
     * runway that is not clear, or says there is nowhere to land.
     */
    private static final int PATIENCE = 3;

    /** What came of a search's tick: still looking, a runway chosen, or nowhere to land found in a round. */
    enum Outcome {
        LOOKING, FOUND, NOWHERE
    }

    /** The touchdown points round the pin, as x and z from it, nearest first. */
    private static final List<int[]> POINTS = points();

    /** The next runway to check: its touchdown point and heading, as one count. */
    private int next;
    /** How long it still rests before going round again, in ticks. */
    private int resting;
    /** How many rounds it has made since the airliner flew over the pin. */
    private int rounds;
    /** Whether a runway in this round was left undecided for want of land surveyed. */
    private boolean undecided;
    /** The runway with the fewest things in the way found this round, and how many; null for none. */
    @Nullable
    private Runway best;
    private int bestObstacles;
    @Nullable
    private Runway found;

    private static List<int[]> points() {
        List<int[]> points = new ArrayList<>();
        int steps = REACH / SPACING;
        for (int i = -steps; i <= steps; i++) {
            addRow(points, i, steps);
        }
        points.sort(Comparator.comparingInt(point -> point[0] * point[0] + point[1] * point[1]));
        return List.copyOf(points);
    }

    /** Adds the touchdown points of one row, so many steps east or west of the pin, that lie within reach of it. */
    private static void addRow(List<int[]> points, int i, int steps) {
        for (int j = -steps; j <= steps; j++) {
            if (i * i + j * j <= steps * steps) {
                points.add(new int[]{i * SPACING, j * SPACING});
            }
        }
    }

    /** Starts the search again from the nearest runway, as it would start for a new pin. */
    void restart() {
        next = 0;
        resting = 0;
        rounds = 0;
        undecided = false;
        best = null;
        found = null;
    }

    /** The runway chosen. */
    @Nullable
    Runway found() {
        return found;
    }

    /**
     * Checks some more runways round the pin, at the pin's x and z, for an airliner flying the approach at a speed;
     * whether the airliner has flown over the pin, so the land round it has been seen.
     */
    Outcome step(GroundSurvey ground, AirlinerShape shape, int pinX, int pinZ, double speed, boolean overflown) {
        if (resting > 0) {
            resting--;
            return Outcome.LOOKING;
        }
        RunwayCheck glance = new RunwayCheck(ground, shape, speed, RunwayCheck.COARSE);
        RunwayCheck close = new RunwayCheck(ground, shape, speed, 1);
        double rollout = RunwayCheck.rollout(speed);
        while (glance.samples() + close.samples() < SAMPLES_PER_TICK && next < POINTS.size() * HEADINGS) {
            Runway runway = candidate(ground, pinX, pinZ, rollout, next++);
            int verdict = runway == null ? RunwayCheck.UNKNOWN : look(glance, close, runway);
            if (verdict == 0) {
                found = runway;
                return Outcome.FOUND;
            }
            weigh(runway, verdict);
        }
        return next < POINTS.size() * HEADINGS ? Outcome.LOOKING : endRound(overflown);
    }

    /**
     * Checks a runway at a glance, counting what stands under its wings only as far as it could still be better than the
     * best so far, and one that looks clear closely too: clear, or with something small in the way the glance stepped
     * over, counted as one. Runways are ranked by what the glance finds, which is enough to tell a few things in the way
     * from many.
     */
    private int look(RunwayCheck glance, RunwayCheck close, Runway runway) {
        int verdict = glance.check(runway, best == null ? Integer.MAX_VALUE : bestObstacles - 1);
        if (verdict != 0) {
            return verdict;
        }
        int closely = close.check(runway, 0);
        return closely <= 0 ? closely : 1;
    }

    /** Notes a runway checked: undecided, or the best so far of those that can be landed on. */
    private void weigh(@Nullable Runway runway, int verdict) {
        if (verdict == RunwayCheck.UNKNOWN) {
            undecided = true;
            return;
        }
        if (verdict > 0 && (best == null || verdict < bestObstacles)) {
            best = runway;
            bestObstacles = verdict;
        }
    }

    /**
     * Ends a round: chooses the best runway found, if it has had a fair look at the land, or else rests before going
     * round again; nowhere to land, if none could be landed on at all.
     */
    private Outcome endRound(boolean overflown) {
        if (overflown) {
            rounds++;
        }
        boolean decided = overflown && (!undecided || rounds >= PATIENCE);
        Runway chosen = best;
        next = 0;
        resting = REST;
        undecided = false;
        best = null;
        if (decided && chosen != null) {
            found = chosen;
            return Outcome.FOUND;
        }
        return decided ? Outcome.NOWHERE : Outcome.LOOKING;
    }

    /**
     * A runway to check, by its count: its touchdown point (the middle of a column), from the pin, and its heading;
     * null while the ground at the touchdown point has not been surveyed, so the runway cannot be told yet. Its height
     * is the ground's there.
     */
    @Nullable
    private static Runway candidate(GroundSurvey ground, int pinX, int pinZ, double rollout, int count) {
        int[] point = POINTS.get(count / HEADINGS);
        int x = pinX + point[0];
        int z = pinZ + point[1];
        GroundSurvey.Patch patch = ground.at(x, z);
        if (patch == null) {
            return null;
        }
        double heading = Math.floorMod(count, HEADINGS) * HEADING_STEP;
        return new Runway(x + 0.5, z + 0.5, heading, patch.top(x, z), rollout);
    }
}
