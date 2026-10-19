package dev.aerolarge.airframe;

/**
 * The airliner's outline in metres, after the A380: 72.7 m long, 79.8 m across the wings, with a 7.1 m wide and 8.4 m
 * tall double-deck fuselage. The fuselage axis runs along z from the nose (z 0) to the tail; x is across, positive to
 * starboard; y is up from the bottom of the fuselage. Everything is measured here once and cut into blocks by
 * {@link AirframeLayout} at whatever scale the server asks for.
 */
final class AirframeShape {
    static final double LENGTH = 72.7;
    static final double WIDTH = 7.14;
    static final double HEIGHT = 8.41;
    /** The rounded nose ends here; the cabin runs at full section to TAIL_START, then the tail cone narrows and lifts. */
    static final double NOSE_LENGTH = 9.0;
    static final double TAIL_START = 48.0;
    /** Height of the nose tip, as a share of the fuselage height: the radome sits a little below the middle. */
    static final double NOSE_TIP = 0.38;
    /**
     * The cross-section is a superellipse rather than an ellipse: the A380's ovoid section is fuller at the shoulders,
     * and squarer corners leave more of the cabin standing room.
     */
    private static final double SECTION_EXPONENT = 2.4;
    /** The main deck floor, as a share of the fuselage height; the cargo hold is underneath. */
    static final double FLOOR = 0.30;

    static final double WING_ROOT_LEADING_EDGE = 27.0;
    static final double WING_SWEEP = 0.70;
    static final double WING_ROOT_CHORD = 19.0;
    static final double WING_TIP_CHORD = 4.0;
    static final double WING_HALF_SPAN = 39.9;
    static final double WING_ROOT_Y = 1.4;
    static final double WING_DIHEDRAL = 0.098;
    static final double WING_ROOT_THICKNESS = 1.9;
    static final double WING_TIP_THICKNESS = 0.5;

    /** The inboard and outboard engines, each hung below the wing and ahead of it. */
    static final double[] ENGINE_X = {14.6, 25.7};
    static final double NACELLE_RADIUS = 1.55;
    static final double NACELLE_LENGTH = 7.0;
    static final double NACELLE_DROP = 2.1;
    static final double NACELLE_AHEAD = 3.6;
    /** Where along the nacelle the barrel gives way to the exhaust cone, and how much narrower the cone ends. */
    private static final double CONE_START = 0.7 * NACELLE_LENGTH;
    private static final double CONE_NARROWING = 0.35;
    /** The intake's mouth, inside the lip of the cowl, as a share of the nacelle's radius. */
    static final double INTAKE_MOUTH = 0.8;

    static final double FIN_ROOT_LEADING_EDGE = 56.0;
    static final double FIN_SWEEP = 0.9;
    static final double FIN_ROOT_CHORD = 13.0;
    static final double FIN_TIP_CHORD = 5.5;
    static final double FIN_HEIGHT = 15.0;
    static final double FIN_ROOT_THICKNESS = 1.4;
    static final double FIN_TIP_THICKNESS = 0.5;

    static final double STABILIZER_ROOT_LEADING_EDGE = 59.0;
    static final double STABILIZER_SWEEP = 0.75;
    static final double STABILIZER_ROOT_CHORD = 10.0;
    static final double STABILIZER_TIP_CHORD = 3.5;
    static final double STABILIZER_HALF_SPAN = 15.2;
    static final double STABILIZER_Y = 0.6 * HEIGHT;
    static final double STABILIZER_DIHEDRAL = 0.12;
    static final double STABILIZER_ROOT_THICKNESS = 1.1;
    static final double STABILIZER_TIP_THICKNESS = 0.4;

    /** Main deck doors on both sides: ahead of the cabin, between cabin and wing, and behind the wing. */
    static final double[] PASSENGER_DOORS = {11.5, 25.0, 50.0};
    /** Where along the fuselage the anti-collision beacons sit, above and below, over the wing box. */
    static final double BEACON_STATION = 34.0;
    /** The aft cargo door, starboard below the floor, clear behind the wing root like the real one. */
    static final double CARGO_DOOR = 49.0;

    /** The nose gear, on the centreline. */
    static final double NOSE_GEAR_Z = 6.5;
    /**
     * The wing and body gear in pairs, (x, z) for the starboard one, z measured back from the airframe's centre of
     * mass: just behind it, as on the real aircraft, so the airliner rests on its nose gear and does not sit back on
     * its tail.
     */
    static final double[][] MAIN_GEAR = {{5.5, 1.5}, {2.2, 3.5}};
    /** How far the lowest part of the airframe, the inboard engines, is held above the ground. */
    static final double GROUND_CLEARANCE = 1.5;

    private AirframeShape() {
    }

    /** The fuselage's cross-section at a station along its length. */
    record Section(double halfWidth, double bottom, double top) {
    }

    /** The fuselage's section at z, or null past either end. */
    static Section section(double z) {
        if (z < 0 || z > LENGTH) {
            return null;
        }
        if (z < NOSE_LENGTH) {
            // A round chin and sides (an ellipse in profile, kept from shrinking to nothing at the very tip), and a crown
            // that rises in a long parabolic slope from the radome: the slope the windscreen sits on.
            double along = 1 - z / NOSE_LENGTH;
            double f = Math.max(0.08, Math.sqrt(1 - along * along));
            double tip = NOSE_TIP * HEIGHT;
            return new Section(WIDTH / 2 * f, tip - tip * f, tip + (HEIGHT - tip) * crown(z));
        }
        if (z <= TAIL_START) {
            return new Section(WIDTH / 2, 0, HEIGHT);
        }
        // The tail cone: the belly sweeps up toward the APU while the crown barely drops.
        double s = (z - TAIL_START) / (LENGTH - TAIL_START);
        return new Section(WIDTH / 2 * (1 - 0.82 * s), HEIGHT * 0.72 * Math.pow(s, 1.15), HEIGHT * (1 - 0.18 * s * s));
    }

    /** How far the nose's crown has risen from the radome toward the full height, 0 at the tip to 1 where the cabin starts. */
    static double crown(double z) {
        double along = 1 - z / NOSE_LENGTH;
        return Math.max(0.08, 1 - along * along);
    }

    static boolean inFuselage(double x, double y, double z) {
        return field(x, y, z) <= 1;
    }

    /** Below 1 inside the fuselage, 1 on its skin, growing outward; 2 past either end. */
    static double field(double x, double y, double z) {
        Section section = section(z);
        if (section == null) {
            return 2;
        }
        double centre = (section.bottom() + section.top()) / 2;
        double radius = (section.top() - section.bottom()) / 2;
        return Math.pow(Math.abs(x) / section.halfWidth(), SECTION_EXPONENT)
            + Math.pow(Math.abs(y - centre) / radius, SECTION_EXPONENT);
    }

    /** The way out of the fuselage from a point near its skin, a unit vector: where its field grows fastest. */
    static double[] outward(double x, double y, double z) {
        double h = 0.02;
        double gx = field(x + h, y, z) - field(x - h, y, z);
        double gy = field(x, y + h, z) - field(x, y - h, z);
        double gz = field(x, y, z + h) - field(x, y, z - h);
        double length = Math.sqrt(gx * gx + gy * gy + gz * gz);
        return length < 1e-9 ? new double[]{0, 1, 0} : new double[]{gx / length, gy / length, gz / length};
    }

    /**
     * An airfoil's thickness along its chord, as a share of its thickest point: blunt at the leading edge, thickest
     * a third of the way back, thinning to nothing at the trailing edge.
     */
    static double airfoil(double chordwise) {
        if (chordwise < 0 || chordwise > 1) {
            return 0;
        }
        return 2.6 * Math.sqrt(chordwise) * (1 - chordwise);
    }

    static double wingLeadingEdge(double x) {
        return WING_ROOT_LEADING_EDGE + WING_SWEEP * x;
    }

    static double wingY(double x) {
        return WING_ROOT_Y + WING_DIHEDRAL * x;
    }

    /** Where the engine at span station x sits: the centre of its intake face. */
    static double nacelleFront(double x) {
        return wingLeadingEdge(x) - NACELLE_AHEAD;
    }

    static double nacelleY(double x) {
        return wingY(x) - NACELLE_DROP;
    }

    /** The nacelle's radius at a distance along it: a straight barrel, then a cone narrowing to the exhaust. */
    static double nacelleRadius(double along) {
        if (along < 0 || along > NACELLE_LENGTH) {
            return 0;
        }
        if (along < CONE_START) {
            return NACELLE_RADIUS;
        }
        return NACELLE_RADIUS * (1 - CONE_NARROWING * (along - CONE_START) / (NACELLE_LENGTH - CONE_START));
    }

    /**
     * How fast the nacelle narrows going aft at a distance along it, in metres of radius a metre: not at all along the
     * barrel.
     */
    static double nacelleTaper(double along) {
        return along < CONE_START ? 0 : NACELLE_RADIUS * CONE_NARROWING / (NACELLE_LENGTH - CONE_START);
    }

    static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }
}
