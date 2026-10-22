package dev.aerolarge.block;

/**
 * Whether the airliner can land on a runway: whether, from the end of its glide down to where it has braked to a stop,
 * the ground under it is clear, as far as the survey knows it (see {@link GroundSurvey}), for the airliner as it is
 * measured (see {@link AirlinerShape}). Three parts of the land are looked at, each against how low the airliner
 * passes over it:
 * <ul>
 *     <li>where the wheels roll, from the earliest the airliner may touch down to the latest it may have stopped, and
 *     a little either side of where they would run on the centre line: dry, solid and nearly level, nothing standing
 *     on it, not even leaves;</li>
 *     <li>everywhere else under the airliner on the ground, out to the wingtips and back to the tail: nothing standing
 *     higher than the airliner's underside there (the belly, the engines, the wings), less a margin;</li>
 *     <li>before it touches down, under the end of the glide and the flare: nothing standing higher than the airliner's
 *     underside will pass over it, the airliner as low as it can be there, its tail over it as it comes down.</li>
 * </ul>
 * Under the fuselage, anything in the way rules the runway out; under the wings, it is counted, so that where no
 * runway is clear the one with the fewest things in the way can still be chosen. Land not yet surveyed leaves the
 * runway undecided, except under the glide before the flare, high enough that only a tall tree or tower reaches it.
 */
final class RunwayCheck {
    /** A runway that cannot be landed on, and one whose land has not all been surveyed yet. */
    static final int REJECTED = -1;
    static final int UNKNOWN = -2;
    /**
     * How far the airliner may touch down before or after the point it aims at, in blocks: the flare, flown over the
     * ground by the radio altitude, floats a few seconds longer over ground lower than the runway, or comes down
     * sooner over a rise, and the speed it finds on the way down changes how far it goes in that time. Two seconds at
     * the approach speed and more.
     */
    static final double TOUCHDOWN_SCATTER = 80;
    /**
     * How fast the airliner is reckoned to slow on its brakes, in blocks a second every second: two thirds of the rate
     * it speeds up at on take-off (3), as its engines idle and nothing but its wheels' brakes slow it, with no reverse
     * thrust, which a heavily loaded airliner on a wet runway might manage. From an approach speed of 33 blocks a
     * second that is a roll of some 270 blocks, about twice the airliner's length, as a real A380's landing roll is.
     */
    static final double ROLLOUT_DECELERATION = 2;
    /** How far either side of the centre line the wheels may run, in blocks: how closely the approach is flown. */
    static final int CENTRE_LINE_TOLERANCE = 8;
    /**
     * How far above or below the touchdown point the ground the wheels roll on may stand, and the biggest step it may
     * have between one column and the next along it, in blocks: the wheels ride a step of one, no more.
     */
    static final int LEVEL_TOLERANCE = 2;
    static final int MAX_STEP = 1;
    /**
     * How far clear of the airliner's underside anything under it must stay, in blocks: on its wheels, a block for
     * them riding up a step; in the air before touchdown, more, for the nose raised in the flare dropping the tail,
     * and the airliner sinking a little lower than flown.
     */
    static final int GROUND_MARGIN = 1;
    static final int FLIGHT_MARGIN = 3;
    /**
     * How far apart the land under the wings and the glide is looked at, across and along, in blocks: close enough
     * to find a tree or a house, not so close that looking costs too much. Where the wheels roll, every block.
     */
    private static final int ACROSS_STEP = 3;
    private static final int ALONG_STEP = 2;
    /**
     * Before touchdown, the ground is looked at back to where the airliner would be this high over the runway, in
     * blocks: above that, little stands tall enough to reach it.
     */
    private static final double CHECKED_HEIGHT = 30;

    /**
     * How many blocks apart a coarse check looks, across and along, as many times further apart as the fine one: a
     * quick look that rules out most runways for a fraction of the cost, leaving the fine look for those that pass it.
     */
    static final int COARSE = 4;

    private final GroundSurvey ground;
    private final AirlinerShape shape;
    /** The speed it is reckoned to fly the approach at, in blocks a second. */
    private final double speed;
    /** How many times further apart than the fine check this one looks: 1 for the fine one, {@link #COARSE} for the quick one. */
    private final int stride;
    /** How many things in the way it counts at most before it stops looking: a runway with more is no better than one found. */
    private int cap = Integer.MAX_VALUE;
    /** How many columns it has looked at, so the search can spread the looking over ticks. */
    private int samples;

    RunwayCheck(GroundSurvey ground, AirlinerShape shape, double speed, int stride) {
        this.ground = ground;
        this.shape = shape;
        this.speed = speed;
        this.stride = stride;
    }

    /** How far the airliner rolls on its brakes from touching down at a speed, in blocks. */
    static double rollout(double speed) {
        return speed * speed / (2 * ROLLOUT_DECELERATION);
    }

    /** How many columns it has looked at so far. */
    int samples() {
        return samples;
    }

    /**
     * Whether the airliner can land on the runway: how many things stand in the way under its wings (0 for a clear
     * runway), or {@link #REJECTED} or {@link #UNKNOWN}. Past {@code cap} things it stops counting, and gives a
     * count past the cap.
     */
    int check(Runway runway, int cap) {
        this.cap = cap;
        int wheels = wheels(runway);
        if (wheels != 0) {
            return wheels;
        }
        int underneath = underneath(runway);
        if (underneath < 0 || underneath > cap) {
            return underneath;
        }
        int before = beforeTouchdown(runway, underneath);
        return before < 0 ? before : underneath + before;
    }

    /** How far behind the touchdown point the wheels may first roll, and how far beyond it at the latest. */
    private double wheelsFrom() {
        return -TOUCHDOWN_SCATTER + shape.gearRear();
    }

    private double wheelsTo(Runway runway) {
        return TOUCHDOWN_SCATTER + runway.rollout() + shape.gearFront();
    }

    /** Whether a point under the airliner is where its wheels may run, the land there checked as for them. */
    private boolean underWheels(int across, double along, Runway runway) {
        return inCorridor(across) && along >= wheelsFrom() && along <= wheelsTo(runway);
    }

    /** Whether a distance from the centre line is between the wheels, or by them: under the fuselage. */
    private boolean inCorridor(int across) {
        return across >= shape.gearLeft() - CENTRE_LINE_TOLERANCE && across <= shape.gearRight() + CENTRE_LINE_TOLERANCE;
    }

    /** Whether the land the wheels roll on is fit for them: 0 if it is, else {@link #REJECTED} or {@link #UNKNOWN}. */
    private int wheels(Runway runway) {
        for (int across = shape.gearLeft() - CENTRE_LINE_TOLERANCE; across <= shape.gearRight() + CENTRE_LINE_TOLERANCE; across += stride) {
            int verdict = wheelTrack(runway, across);
            if (verdict != 0) {
                return verdict;
            }
        }
        return 0;
    }

    /** One line along the runway where wheels may roll, a distance across it. */
    private int wheelTrack(Runway runway, int across) {
        int previous = Integer.MIN_VALUE;
        for (double along = wheelsFrom(); along <= wheelsTo(runway); along += stride) {
            samples++;
            int x = runway.columnX(along, across);
            int z = runway.columnZ(along, across);
            GroundSurvey.Patch patch = ground.at(x, z);
            if (patch == null) {
                return UNKNOWN;
            }
            if (!rollable(patch, x, z, runway.elevation(), previous)) {
                return REJECTED;
            }
            // The coarse look's columns are too far apart to tell a step.
            previous = stride == 1 ? patch.top(x, z) : Integer.MIN_VALUE;
        }
        return 0;
    }

    /**
     * Whether wheels can roll over a column: dry, nothing on it, near the touchdown point's height, and no more than a
     * step from the column before it (none for the first).
     */
    private static boolean rollable(GroundSurvey.Patch patch, int x, int z, int elevation, int previous) {
        int top = patch.top(x, z);
        boolean level = Math.abs(top - elevation) <= LEVEL_TOLERANCE && (previous == Integer.MIN_VALUE || Math.abs(top - previous) <= MAX_STEP);
        return level && top == patch.ground(x, z) && !patch.wet(x, z);
    }

    /**
     * What stands in the way under the airliner on the ground, other than where its wheels roll: from its tail at the
     * earliest touchdown to its nose where it has stopped at the latest, out to the wingtips. How many things, or
     * {@link #REJECTED} for one under the fuselage, or {@link #UNKNOWN}.
     */
    private int underneath(Runway runway) {
        int obstacles = 0;
        for (int across = -shape.halfSpan(); across <= shape.halfSpan() && obstacles <= cap; across += ACROSS_STEP * stride) {
            int found = underneathLine(runway, across);
            if (found < 0) {
                return found;
            }
            obstacles += found;
        }
        return obstacles;
    }

    private int underneathLine(Runway runway, int across) {
        int clearance = shape.body(across);
        if (clearance == AirlinerShape.NOTHING) {
            return 0;
        }
        int limit = runway.elevation() + clearance - GROUND_MARGIN;
        boolean fuselage = inCorridor(across);
        int obstacles = 0;
        double to = TOUCHDOWN_SCATTER + runway.rollout() + shape.ahead();
        for (double along = -TOUCHDOWN_SCATTER - shape.behind(); along <= to; along += ALONG_STEP * stride) {
            if (underWheels(across, along, runway)) {
                continue;
            }
            samples++;
            int top = top(runway, along, across);
            if (top == Integer.MIN_VALUE) {
                return UNKNOWN;
            }
            if (top <= limit) {
                continue;
            }
            if (fuselage) {
                return REJECTED;
            }
            obstacles++;
        }
        return obstacles;
    }

    /**
     * What stands in the way under the airliner before it touches down: back from where its wheels first roll at the
     * earliest touchdown, under the flare and the end of the glide, until even its tail, the last of it over the land
     * and so the lowest, would pass {@link #CHECKED_HEIGHT} over the runway; no further once the things in the way,
     * with those already found under it on the ground, are past the cap.
     */
    private int beforeTouchdown(Runway runway, int already) {
        int obstacles = 0;
        for (double along = wheelsFrom() - ALONG_STEP; already + obstacles <= cap; along -= ALONG_STEP * stride) {
            // How high the rearmost wheels and the tail pass over this row, as low as they come over it.
            double gear = PinLanding.wheelHeight(-TOUCHDOWN_SCATTER - (along - shape.gearRear()), speed);
            double tailBefore = -TOUCHDOWN_SCATTER - (along + shape.behind());
            double tail = tailBefore > 0 ? PinLanding.wheelHeight(tailBefore, speed) : Double.NaN;
            if (tail > CHECKED_HEIGHT) {
                break;
            }
            int found = beforeRow(runway, along, gear, tail);
            if (found < 0) {
                return found;
            }
            obstacles += found;
        }
        return obstacles;
    }

    /**
     * One row across the land before touchdown, a distance along the runway, the airliner's wheels and its tail
     * passing over it at heights above the runway (the tail's NaN while it is down already as it passes, the land under
     * it looked at as for the airliner on the ground). The wheels may meet land no higher than the runway may be:
     * that is touching down a little early. Unsurveyed land under the flare leaves the runway undecided; higher, it
     * is passed over.
     */
    private int beforeRow(Runway runway, double along, double gear, double tail) {
        int obstacles = 0;
        double lowest = Double.isNaN(tail) ? gear : Math.min(gear, tail);
        for (int across = -shape.halfSpan(); across <= shape.halfSpan(); across += ACROSS_STEP * stride) {
            double limit = passingOver(runway, across, gear, tail);
            if (Double.isNaN(limit)) {
                continue;
            }
            samples++;
            int top = top(runway, along, across);
            if (top == Integer.MIN_VALUE && lowest <= AutopilotBlockEntity.FLARE_HEIGHT) {
                return UNKNOWN;
            }
            if (top <= limit) {
                continue;
            }
            if (inCorridor(across)) {
                return REJECTED;
            }
            obstacles++;
        }
        return obstacles;
    }

    /**
     * The highest land may stand a distance across the runway, with the wheels and the tail passing over at heights
     * above it (the tail's NaN while it is down already); NaN where nothing of the airliner passes over.
     */
    private double passingOver(Runway runway, int across, double gear, double tail) {
        double limit = inCorridor(across) ? runway.elevation() + gear + LEVEL_TOLERANCE : Double.NaN;
        int clearance = shape.body(across);
        if (Double.isNaN(tail) || clearance == AirlinerShape.NOTHING) {
            return limit;
        }
        // Never below the runway itself: land no higher than where the wheels touch down is passed over, whatever the
        // margin, as low as the airliner's underside comes there.
        double body = Math.max(runway.elevation(), runway.elevation() + tail + clearance - FLIGHT_MARGIN);
        return Double.isNaN(limit) ? body : Math.min(limit, body);
    }

    /** The top of the column a distance along and across the runway; {@link Integer#MIN_VALUE} while not surveyed. */
    private int top(Runway runway, double along, double across) {
        int x = runway.columnX(along, across);
        int z = runway.columnZ(along, across);
        GroundSurvey.Patch patch = ground.at(x, z);
        return patch == null ? Integer.MIN_VALUE : patch.top(x, z);
    }
}
