package dev.skyliners.autopilot;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.airframe.FreighterLayout;
import dev.skyliners.block.LegBayBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Where the freighter comes down near a pin she is set to land at: the nearest spot to it, within {@link #reach},
 * where her five legs' feet all stand on dry, bare ground, level within {@link #LEVEL}, and nothing under the rest of
 * her reaches up to her hull, as far as the ground round the pin is known (see {@link GroundSurvey}). She looks as she
 * comes, from the pin outward, a few spots a tick, her hull turned the way she points as each round of spots begins; a
 * spot over ground not read yet is looked at again on the next round. Once she slides onto the spot found, pointing as
 * she will come down, it is looked at once more that way (see {@link #verify}), and if her prongs would now reach over
 * something she looks on that way. Having looked at every spot with all their ground read and found none, she has
 * none (see {@link #exhausted}): she holds over the pin rather than come down on whatever stands there. Spots whose
 * ground never loads, past where the world round her is loaded, do not keep her looking for ever: a round in which no
 * more ground came in counts as all of it read.
 */
public final class PadSearch {
    /** How far apart the spots she tries are, in blocks. */
    private static final int STEP = 4;
    /**
     * How much higher one of her feet's ground may be than another's, in blocks: a block less than her legs telescope,
     * for the ground to differ a little where she comes down off the spot; and how much across one foot plate, which
     * stands on the highest of it.
     */
    private static final int LEVEL = LegBayBlockEntity.MOST_REACH - 1;
    private static final int PLATE_LEVEL = 1;
    /**
     * Ground that does not load counts as all there is: how near the pin she must be for that, in blocks, and after how
     * many rounds in a row with no more ground come in, so a slow chunk is not taken for one that never comes.
     */
    private static final double NEAR_PIN = 32;
    private static final int ROUNDS_UNCHANGED = 2;
    /**
     * How far apart the columns under her hull are looked at, in blocks: every one, as a block just under her rim is
     * enough to tip her; and how many spots are tried each tick: most fall at her feet, and a spot she fits takes some
     * 4,000 columns' looks.
     */
    private static final int SAMPLE = 1;
    private static final int PER_TICK = 8;
    /**
     * How far either side of the way she holds she is looked at swinging, in degrees: her jets hold her heading only
     * within a few degrees, and her prongs' tips, over fifty blocks ahead, swing a block for every degree.
     */
    private static final double SWING = 5;
    private static final double[] SWINGS = {0, -SWING, SWING};
    /**
     * How near a spot she gave up landing on she tries no other, in blocks: her disc's radius, so that what stopped her,
     * which her reading of the ground may not have seen (another ship, for one), is out from under her.
     */
    private static final int REFUSED_WITHIN = 30;

    /** Where she comes down: her disc's middle's column. */
    public record Pad(int x, int z) {
    }

    /** Whether she fits over a spot, as far as its ground has been read. */
    private enum Fit { YES, NO, UNREAD }

    /** How a foot plate stands: whether it can, and how high the ground under it is, as the first air over it. */
    private record Standing(Fit fit, int ground) {
    }

    private final GroundSurvey ground = new GroundSurvey();
    /** The spots to try round the pin, nearest first, as offsets from it, and the next to try. */
    private final List<int[]> spots = new ArrayList<>();
    private int next;
    /** How far from the pin the spots were worked out to reach, in blocks; none yet. */
    private int spotsReach = -1;
    /** Whether a spot tried this round stood over ground not read yet, so another round may find it. */
    private boolean unread;
    /**
     * How much ground had been read as this round began, so ground that never loads does not keep her looking, once
     * she is over the pin (see {@link #NEAR_PIN}) and all that will load round it has.
     */
    private int readAtRound;
    private boolean overPin;
    /** How many rounds in a row no more ground has come in. */
    private int roundsUnchanged;
    /** The pin looked round; none, so the next look starts afresh. */
    private int pinX = Integer.MIN_VALUE;
    private int pinZ = Integer.MIN_VALUE;
    /** The way she points for this round of spots, as Minecraft yaw, and whether it is the way she will come down. */
    private double heading;
    private boolean headingFixed;
    @Nullable
    private Pad found;
    /** Whether the spot found has been looked at again pointing as she comes down. */
    private boolean verified;
    /** Whether every spot has been looked at with all its ground read, and none fits. */
    private boolean exhausted;
    /** The spots she gave up landing on this flight (see {@link #refuse}). */
    private final List<Pad> refused = new ArrayList<>();

    /** The spots to try, worked out afresh for how far the server's config has her search reach now. */
    private void readySpots() {
        int reach = reach();
        if (reach == spotsReach) {
            return;
        }
        spotsReach = reach;
        spots.clear();
        for (int dx = -reach; dx <= reach; dx += STEP) {
            addRow(dx, reach);
        }
        spots.sort(Comparator.comparingInt(spot -> spot[0] * spot[0] + spot[1] * spot[1]));
    }

    /** The spots in one row of them, so far across from the pin, within her reach. */
    private void addRow(int dx, int reach) {
        for (int dz = -reach; dz <= reach; dz += STEP) {
            if (dx * dx + dz * dz <= reach * reach) {
                spots.add(new int[]{dx, dz});
            }
        }
    }

    /** Forgets what was found and the ground read, for a new flight: the ground may have changed since. */
    void reset() {
        ground.forget();
        pinX = Integer.MIN_VALUE;
        pinZ = Integer.MIN_VALUE;
        headingFixed = false;
        found = null;
        verified = false;
        exhausted = false;
        refused.clear();
    }

    /**
     * She could not finish coming down with her middle over a column (see {@link BalkedLanding}): no spot near it is
     * tried again this flight, and the next look starts afresh from the pin, the ground already read kept.
     */
    void refuse(int x, int z) {
        refused.add(new Pad(x, z));
        pinX = Integer.MIN_VALUE;
        pinZ = Integer.MIN_VALUE;
    }

    /**
     * Each tick she flies to a pin to land at: the ground round it read as she comes, and, while she has no spot, a few
     * more spots tried with her hull turned the way she points, as Minecraft yaw, as the round began. A new pin starts
     * afresh.
     */
    void tick(ServerLevel level, Vec3 middle, int x, int z, double pointing) {
        if (x != pinX || z != pinZ) {
            restart(x, z, pointing);
        }
        overPin = Math.hypot(middle.x - x, middle.z - z) < NEAR_PIN;
        ground.tick(level, middle);
        for (int tried = 0; tried < PER_TICK && found == null && !exhausted; tried++) {
            tryNext(pointing);
        }
    }

    /**
     * The spot found looked at once more, pointing as she will come down, as she slides onto it: if she no longer fits
     * there, she looks on from the next spot, pointing so from now on.
     */
    void verify(double pointing) {
        if (found == null || verified) {
            return;
        }
        heading = pointing;
        headingFixed = true;
        if (fits(found.x(), found.z()) == Fit.YES) {
            verified = true;
            return;
        }
        found = null;
        next = 0;
        unread = false;
    }

    /** Where she comes down: the spot found; null while she looks, or has none. */
    @Nullable
    Pad pad() {
        return found;
    }

    /** Whether the spot found has been looked at again pointing as she comes down, so she may come down on it. */
    boolean verified() {
        return found != null && verified;
    }

    /**
     * She has stopped short of the spot found and gets no nearer (see {@link FreighterPilot}): if she fits with her
     * middle over the column she has stopped over, pointing as she will come down, that is her spot now. Says if so.
     */
    boolean settleAt(int x, int z) {
        if (!verified() || fits(x, z) != Fit.YES) {
            return false;
        }
        found = new Pad(x, z);
        return true;
    }

    /**
     * The way she pointed as the spot found was looked at again, as Minecraft yaw: the way she must keep pointing to
     * come down on it, her prongs reaching far ahead over ground looked at only that way.
     */
    double heading() {
        return heading;
    }

    /**
     * Whether the way she comes down is fixed (see {@link #heading}): once a spot looked at again has failed, she keeps
     * pointing so and slides sideways to the next, as turning toward it would make the search's way wrong again.
     */
    boolean headingFixed() {
        return headingFixed;
    }

    /** How far from the pin she may come down, in blocks, as the server's config has it now. */
    int reach() {
        return SkylinersConfig.FREIGHTER_PAD_REACH.get();
    }

    /** Whether she has looked at every spot with all their ground read and found none. */
    boolean exhausted() {
        return exhausted;
    }

    private void restart(int x, int z, double pointing) {
        readySpots();
        pinX = x;
        pinZ = z;
        readAtRound = -1;
        roundsUnchanged = 0;
        ground.aim(x, z);
        next = 0;
        unread = false;
        heading = pointing;
        headingFixed = false;
        found = null;
        verified = false;
        exhausted = false;
    }

    /**
     * Tries the next spot; after the last, ending if every spot's ground was read, or beginning another round, pointing
     * as she points now unless the way she comes down is known.
     */
    private void tryNext(double pointing) {
        if (next >= spots.size()) {
            roundsUnchanged = ground.read() == readAtRound ? roundsUnchanged + 1 : 0;
            exhausted = !unread || overPin && roundsUnchanged >= ROUNDS_UNCHANGED;
            readAtRound = ground.read();
            next = 0;
            unread = false;
            heading = headingFixed ? heading : pointing;
            return;
        }
        int[] spot = spots.get(next++);
        Fit fit = fits(pinX + spot[0], pinZ + spot[1]);
        if (fit == Fit.UNREAD) {
            unread = true;
        } else if (fit == Fit.YES) {
            found = new Pad(pinX + spot[0], pinZ + spot[1]);
            verified = false;
        }
    }

    /**
     * Whether she fits with her middle over a column, pointing the round's way and swung either side of it (see
     * {@link #SWING}): her feet on dry, bare, level ground and nothing under her reaching her hull.
     */
    private Fit fits(int x, int z) {
        if (refused.stream().anyMatch(pad -> Math.hypot(pad.x() - x, pad.z() - z) < REFUSED_WITHIN)) {
            return Fit.NO;
        }
        for (double swing : SWINGS) {
            Fit fit = fits(x, z, heading + swing);
            if (fit != Fit.YES) {
                return fit;
            }
        }
        return Fit.YES;
    }

    /** Whether she fits with her middle over a column, pointing a way, as Minecraft yaw. */
    private Fit fits(int x, int z, double pointing) {
        double turn = Math.toRadians(pointing);
        double sin = Math.sin(turn);
        double cos = Math.cos(turn);
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        List<FreighterLayout.Offset> feet = FreighterLayout.feet();
        for (int plate = 0; plate < feet.size(); plate += FreighterLayout.FOOT_CELLS) {
            Standing on = standing(feet.subList(plate, plate + FreighterLayout.FOOT_CELLS), x, z, sin, cos);
            if (on.fit() != Fit.YES) {
                return on.fit();
            }
            lowest = Math.min(lowest, on.ground());
            highest = Math.max(highest, on.ground());
        }
        return highest - lowest > LEVEL ? Fit.NO : clearUnder(x, z, sin, cos, highest);
    }

    /** Whether one foot plate can stand, her middle over a column, and the highest ground under it. */
    private Standing standing(List<FreighterLayout.Offset> plate, int x, int z, double sin, double cos) {
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (FreighterLayout.Offset cell : plate) {
            int cellX = atX(x, cell, sin, cos);
            int cellZ = atZ(z, cell, sin, cos);
            GroundSurvey.Patch patch = ground.at(cellX, cellZ);
            if (patch == null) {
                return new Standing(Fit.UNREAD, 0);
            }
            int under = patch.ground(cellX, cellZ);
            if (patch.wet(cellX, cellZ) || patch.top(cellX, cellZ) != under) {
                return new Standing(Fit.NO, 0);
            }
            lowest = Math.min(lowest, under);
            highest = Math.max(highest, under);
        }
        return new Standing(highest - lowest > PLATE_LEVEL ? Fit.NO : Fit.YES, highest);
    }

    /** Whether nothing under her hull reaches up to it, her feet standing at a height. */
    private Fit clearUnder(int x, int z, double sin, double cos, int feet) {
        for (FreighterLayout.Offset column : FreighterLayout.underHull(SAMPLE)) {
            int columnX = atX(x, column, sin, cos);
            int columnZ = atZ(z, column, sin, cos);
            GroundSurvey.Patch patch = ground.at(columnX, columnZ);
            if (patch == null) {
                return Fit.UNREAD;
            }
            if (patch.top(columnX, columnZ) > feet + column.up()) {
                return Fit.NO;
            }
        }
        return Fit.YES;
    }

    /**
     * Where a point of hers, as her layout gives it from her middle, stands in the world, her middle over a column and
     * her nose at a heading: Minecraft yaw, its nose (-sin, cos) and her right (-cos, -sin).
     */
    private static int atX(int x, FreighterLayout.Offset point, double sin, double cos) {
        return x + (int) Math.round(-cos * point.right() + sin * point.astern());
    }

    private static int atZ(int z, FreighterLayout.Offset point, double sin, double cos) {
        return z + (int) Math.round(-sin * point.right() - cos * point.astern());
    }
}
