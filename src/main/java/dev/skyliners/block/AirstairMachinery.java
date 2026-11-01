package dev.skyliners.block;

import dev.skyliners.block.AirstairFlight.Part;
import dev.skyliners.block.AirstairFlight.Sink;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The machinery that works integral airstairs out of their bay and back (see {@link AirstairFlight}), drawn with them
 * so that nothing moves them by unseen hands. It shows through the bay's mouth as they come out, and from inside a
 * rocket as well, whose bay is the top row of her engine room, open to it. A door's flight, an airliner's or a
 * rocket's, has all of it:
 * <ul>
 * <li>two steel rails along the bay's foot, hung from the floor, out to the skin at the mouth, each with an extension
 * nested in it;</li>
 * <li>a yellow carriage running on them under the head of the folded flight, rolling it out of the bay and pulling the
 * extensions out of the rails as it rolls on past the skin, where it stops while the flight swings;</li>
 * <li>a hinge tube across the head of the stringers, under the second tread, which the flight swings on;</li>
 * <li>a three-stage lift ram either side, from the carriage up to the hinge tube, lying low under the folded flight,
 * which stands up and runs out to lift the flight's head to the sill;</li>
 * <li>a tilt ram in the middle, from the carriage to a lug under the third tread, which draws in to tip the flight
 * down to its slope;</li>
 * <li>a cable drum either side on the hinge tube, its cable running down under the treads to the bottom section's
 * foot, paying out as the sections run out and winding them back in.</li>
 * </ul>
 * A zeppelin's flight, hung in her belly right over its hatch, slides nowhere and swings straight down on its hinge,
 * so it has only the hinge tube with its drums and cables.
 *
 * <p>Each part follows the flight's steps (see {@link AirstairFlight#SLIDE}, {@link AirstairFlight#SWING},
 * {@link AirstairFlight#EXTEND}), so it goes back the same way as the flight folds away, and none of it is drawn once
 * the hatch is shut on it. Everything is measured in blocks, in the door's frame, as the flight is: x across to the
 * right seen from outside, y up from the sill, z out with the outer face at z 1, the bay's mouth the row from y -2 to
 * -1.
 */
final class AirstairMachinery {
    private static final float PX = AirstairFlight.PX;
    /**
     * The rails: how far in from the flight's sides each lies, inside the foot of the platform beams, which pass just
     * over the mouth's foot; how wide it is and how tall.
     */
    private static final float RAIL_IN = 2.5F * PX;
    private static final float RAIL_WIDTH = 2 * PX;
    private static final float RAIL_HEIGHT = 1.5F * PX;
    private static final float RAIL_TOP = AirstairFlight.MOUTH_BOTTOM + RAIL_HEIGHT;
    /** The extension nested in each rail: how much smaller than the rail it is all round, and how long. */
    private static final float EXTENSION_INSET = PX / 4;
    private static final float EXTENSION_LENGTH = 1.25F;
    /**
     * The hangers holding the rails from the floor, outside the flight's sides so it slides between them: how far
     * apart they are along the bay, how far out from the flight's sides, clear of its handrails lying on its
     * stringers, how wide and how thick, and how far behind the mouth the last of them stands.
     */
    private static final float HANGER_SPACING = 2;
    private static final float HANGER_OUT = PX / 2;
    private static final float HANGER_WIDTH = 1.5F * PX;
    private static final float HANGER_DEPTH = 2 * PX;
    private static final float HANGERS_BEHIND_MOUTH = 0.5F;
    /**
     * How far behind the hinge the carriage rides along the bay, in blocks: under the head of the folded flight, clear
     * of its platform beams' foot and within the bay's end.
     */
    private static final float CARRIAGE_BACK = 0.66F;
    /** The carriage's crossbar: half its depth along the bay and how tall it stands on the rails. */
    private static final float CARRIAGE_HALF = 1.5F * PX;
    private static final float CARRIAGE_HEIGHT = 2 * PX;
    /** The shoes at its ends running on the rails: how far past the rail they reach either side and behind. */
    private static final float SHOE = PX;
    /** Where the rams' feet are pinned in the carriage: half way up its crossbar. */
    private static final float RAM_FOOT_Y = RAIL_TOP + CARRIAGE_HEIGHT / 2;
    /**
     * How far in from the flight's sides the lift rams stand, to their middles: inside the stringers of the top three
     * sections, which come down to the rams' eyes on the hinge tube.
     */
    private static final float LIFT_RAM_IN = 6 * PX;
    /** Each of a lift ram's three stages: how long it is, and how thick each is, from the barrel to the rod. */
    private static final float LIFT_STAGE = 0.55F;
    private static final float[] LIFT_SIZES = {3 * PX, 2.25F * PX, 1.5F * PX};
    /**
     * Each of the tilt ram's two stages: how long it is, long enough to stay in one another as the lifted flight's lug
     * swings furthest from the carriage, and how thick each is.
     */
    private static final float TILT_STAGE = 1;
    private static final float[] TILT_SIZES = {3 * PX, 2 * PX};
    /**
     * The tilt ram's lug under the third tread, in the flight's own frame: where along the flight its middle is, just
     * ahead of the tread's back edge; how far under the tread it holds the ram's eye; and half its size.
     */
    private static final float LUG_U = 1 + 1.5F * PX;
    private static final float LUG_Y = -1 - AirstairFlight.TREAD_THICKNESS - 2.5F * PX;
    private static final float LUG_HALF = 1.25F * PX;
    /** The hinge tube's thickness, and how far in from the flight's sides its ends stop, within the stringers. */
    private static final float TUBE_SIZE = 2 * PX;
    private static final float TUBE_INSET = PX / 16;
    /** The cable drums on it: how thick and how wide each is, and how far either side of the flight's middle. */
    private static final float DRUM_SIZE = 3.5F * PX;
    private static final float DRUM_WIDTH = 2 * PX;
    private static final float CABLE_SPREAD = 0.5F;
    /**
     * A cable's thickness, and how far its middle runs under the line through the treads' back edges, below every
     * tread and stringer of every section; the cable leaves its drum on that line.
     */
    private static final float CABLE_SIZE = PX;
    private static final float CABLE_BELOW = AirstairFlight.TREAD_THICKNESS + PX;
    /** How far up from the foot of the bottom section, along it, the cables are made fast under its last tread. */
    private static final float CABLE_END_BACK = 0.25F;
    /** How deep a tread is, along the flight: half a block, two to a block. */
    private static final float TREAD_DEPTH = 0.5F;
    /**
     * Where the hinge tube lies in the flight's own frame, out along it and up: just ahead of the pivot the flight
     * swings about, under the second tread's plate, its drums clear of the plate, so the tread hides it from above
     * and the cables leave the drums' undersides on their line; at the pivot itself, on that tread's back edge, it and
     * its drums would stand across the step.
     */
    private static final float HINGE_U = AirstairFlight.PIVOT_U + DRUM_SIZE / 2 + PX / 2;
    private static final float HINGE_Y = -TREAD_DEPTH - AirstairFlight.TREAD_THICKNESS - DRUM_SIZE / 2 - PX / 8;

    private AirstairMachinery() {
    }

    /**
     * Draws the machinery as it is at the moment, with the flight in its pose ({@code nest}, the flight's own frame in
     * the door's), slid this far out before its swing draws it back in, its bottom section run out this many treads
     * from where it nests, each section so many treads long.
     */
    static void draw(AirstairFlight.Motion motion, Matrix4f nest, float slid, float footOffset, int treads, Sink sink) {
        int width = motion.width();
        hinge(nest, width, sink);
        for (float middle : new float[]{width / 2F - CABLE_SPREAD, width / 2F + CABLE_SPREAD}) {
            cable(nest, middle, footOffset, treads, sink);
        }
        if (motion.belly()) {
            return;
        }
        float railEnd = 1 - motion.hatchInset();
        float deepEnd = 1 - motion.mouth() - AirstairFlight.bayLength(motion.rise(), motion.sections());
        float carriage = 1 + slid + AirstairFlight.PIVOT_U - CARRIAGE_BACK;
        rails(width, deepEnd, railEnd, motion.mouth(), sink);
        carriage(width, carriage, railEnd, sink);
        Vector3f hinge = nest.transformPosition(new Vector3f(0, HINGE_Y, HINGE_U));
        for (float middle : new float[]{LIFT_RAM_IN, width - LIFT_RAM_IN}) {
            ram(new Vector3f(middle, RAM_FOOT_Y, carriage), new Vector3f(middle, hinge.y, hinge.z), LIFT_STAGE, LIFT_SIZES, sink);
        }
        tiltRam(nest, width, carriage, sink);
    }

    /**
     * The two rails along the bay's foot, from its end out to the skin's surface at the mouth, and the hangers holding
     * them from the floor, inside the bay, behind the mouth.
     */
    private static void rails(int width, float deepEnd, float railEnd, int mouth, Sink sink) {
        Matrix4f door = new Matrix4f();
        float hangersTo = 1 - mouth - HANGERS_BEHIND_MOUTH;
        for (float left : new float[]{RAIL_IN, width - RAIL_IN - RAIL_WIDTH}) {
            sink.box(door, new Vector3f(left, AirstairFlight.MOUTH_BOTTOM, deepEnd), new Vector3f(left + RAIL_WIDTH, RAIL_TOP, railEnd), Part.TRACK);
        }
        for (float along = deepEnd + HANGER_SPACING / 2; along <= hangersTo; along += HANGER_SPACING) {
            hanger(door, -HANGER_OUT - HANGER_WIDTH, -HANGER_OUT, RAIL_IN + RAIL_WIDTH, along, sink);
            hanger(door, width + HANGER_OUT, width + HANGER_OUT + HANGER_WIDTH, width - RAIL_IN - RAIL_WIDTH, along, sink);
        }
    }

    /**
     * A hanger beside the bay, a strap from the floor down past the flight's side to under the rail, across from
     * {@code from} to {@code to}, its foot reaching in to {@code foot} under the rail, at a place along the bay.
     */
    private static void hanger(Matrix4f door, float from, float to, float foot, float along, Sink sink) {
        float back = along - HANGER_DEPTH / 2;
        float front = along + HANGER_DEPTH / 2;
        float bottom = AirstairFlight.MOUTH_BOTTOM - PX;
        sink.box(door, new Vector3f(from, bottom, back), new Vector3f(to, AirstairFlight.MOUTH_TOP, front), Part.TRACK);
        sink.box(door, new Vector3f(Math.min(from, foot), bottom, back), new Vector3f(Math.max(to, foot), AirstairFlight.MOUTH_BOTTOM, front),
            Part.TRACK);
    }

    /**
     * The carriage at a place along the bay: its crossbar across the flight, a shoe on each rail, and once it has
     * rolled out past the rails' ends, the extension it pulls out of each rail under it.
     */
    private static void carriage(int width, float along, float railEnd, Sink sink) {
        Matrix4f door = new Matrix4f();
        float top = RAIL_TOP + CARRIAGE_HEIGHT;
        float shoeWidth = RAIL_WIDTH + 2 * SHOE;
        float inner = RAIL_IN - SHOE + shoeWidth;
        sink.box(door, new Vector3f(inner, RAIL_TOP, along - CARRIAGE_HALF), new Vector3f(width - inner, top, along + CARRIAGE_HALF),
            Part.CARRIAGE);
        float pulled = along + CARRIAGE_HALF;
        for (float left : new float[]{RAIL_IN, width - RAIL_IN - RAIL_WIDTH}) {
            sink.box(door, new Vector3f(left - SHOE, RAIL_TOP, along - CARRIAGE_HALF - SHOE), new Vector3f(left + RAIL_WIDTH + SHOE, top, along + CARRIAGE_HALF),
                Part.CARRIAGE);
            if (pulled > railEnd) {
                sink.box(door, new Vector3f(left + EXTENSION_INSET, AirstairFlight.MOUTH_BOTTOM + EXTENSION_INSET, pulled - EXTENSION_LENGTH),
                    new Vector3f(left + RAIL_WIDTH - EXTENSION_INSET, RAIL_TOP - EXTENSION_INSET, pulled), Part.TRACK);
            }
        }
    }

    /**
     * The hinge tube across the head of the stringers, which the flight swings on, and the cable drums on it, in the
     * flight's own frame; round, so they look the same however the flight is turned.
     */
    private static void hinge(Matrix4f nest, int width, Sink sink) {
        tube(nest, TUBE_INSET, width - TUBE_INSET, TUBE_SIZE, Part.ROD, sink);
        for (float middle : new float[]{width / 2F - CABLE_SPREAD, width / 2F + CABLE_SPREAD}) {
            tube(nest, middle - DRUM_WIDTH / 2, middle + DRUM_WIDTH / 2, DRUM_SIZE, Part.TRACK, sink);
        }
    }

    /**
     * A round tube across the flight on the hinge's line, from {@code from} to {@code to} across, so thick: two square
     * bars about the same line, one turned an eighth of a turn on the other, which together read as round.
     */
    private static void tube(Matrix4f nest, float from, float to, float size, Part part, Sink sink) {
        Matrix4f at = new Matrix4f(nest).translate(0, HINGE_Y, HINGE_U);
        float half = size / 2;
        sink.box(at, new Vector3f(from, -half, -half), new Vector3f(to, half, half), part);
        float turned = half * 0.92F;
        sink.box(new Matrix4f(at).rotateX((float) Math.PI / 4), new Vector3f(from, -turned, -turned), new Vector3f(to, turned, turned), part);
    }

    /**
     * The tilt ram in the flight's middle, from the carriage at a place along the bay to its eye in the lug under the
     * third tread, and the lug.
     */
    private static void tiltRam(Matrix4f nest, int width, float carriage, Sink sink) {
        float middle = width / 2F;
        float treadUnder = -1 - AirstairFlight.TREAD_THICKNESS;
        sink.box(nest, new Vector3f(middle - LUG_HALF, LUG_Y - LUG_HALF, LUG_U - LUG_HALF), new Vector3f(middle + LUG_HALF, treadUnder, LUG_U + LUG_HALF),
            Part.CARRIAGE);
        Vector3f eye = nest.transformPosition(new Vector3f(middle, LUG_Y, LUG_U));
        ram(new Vector3f(middle, RAM_FOOT_Y, carriage), eye, TILT_STAGE, TILT_SIZES, sink);
    }

    /**
     * A telescopic ram from its foot to its eye, in the door's frame: its dark steel barrel from the foot, its chrome
     * rod to the eye and any stages between spaced evenly, each stage as long as the others and each narrower than the
     * one it runs out of, so that collapsed they slide into one another out of sight.
     */
    private static void ram(Vector3f foot, Vector3f eye, float stage, float[] sizes, Sink sink) {
        float length = foot.distance(eye);
        float pitch = (float) Math.atan2(eye.y - foot.y, eye.z - foot.z);
        Matrix4f along = new Matrix4f().translate(foot).rotateX(-pitch);
        float step = sizes.length > 1 ? Math.max(0, length - stage) / (sizes.length - 1) : 0;
        for (int i = 0; i < sizes.length; i++) {
            float half = sizes[i] / 2;
            Part part = i == 0 ? Part.TRACK : Part.ROD;
            sink.box(along, new Vector3f(-half, -half, i * step), new Vector3f(half, half, i * step + stage), part);
        }
    }

    /**
     * A cable down the flight's underside, in the flight's own frame, so far across: from its drum on the hinge tube
     * down under the treads to the bottom section, run out this many treads, each section so many treads long, made
     * fast to a lug under that section's last tread.
     */
    private static void cable(Matrix4f nest, float middle, float footOffset, int treads, Sink sink) {
        float from = HINGE_U;
        float to = (footOffset + treads) * TREAD_DEPTH - CABLE_END_BACK;
        float half = CABLE_SIZE / 2;
        Matrix4f along = new Matrix4f(nest).translate(middle, -from - CABLE_BELOW, from).rotateX(AirstairFlight.SLOPE);
        sink.box(along, new Vector3f(-half, -half, 0), new Vector3f(half, half, (to - from) * (float) Math.sqrt(2)), Part.TRACK);
        // The last tread's underside, where the treads' back edges' line passes its back edge.
        float treadUnder = TREAD_DEPTH - CABLE_END_BACK - to - AirstairFlight.TREAD_THICKNESS;
        sink.box(nest, new Vector3f(middle - LUG_HALF, -to - CABLE_BELOW - half, to - LUG_HALF), new Vector3f(middle + LUG_HALF, treadUnder, to + LUG_HALF),
            Part.CARRIAGE);
    }
}
