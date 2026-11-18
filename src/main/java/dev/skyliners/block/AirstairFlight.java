package dev.skyliners.block;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The integral airstairs' build and motion, as the renderer draws them and the airliner leaves room for them. A
 * straight flight of open treads at 45 degrees, as wide as its doorway (the airliner's two blocks, a rocket's three),
 * running straight out from the door: two half-block treads a block, the top one level with the sill, each a non-slip
 * plate with a yellow nosing, between slim aluminium stringers, with a handrail along each side on thin posts. It is
 * made of a few telescoping sections, nested in the top one, and stows under the cabin floor in a bay behind a hatch in
 * the hull below the door. Coming out it runs through five steps, which going away runs back:
 * <ol>
 * <li>{@link #HATCH}: the hatch, hinged along its foot, swings down and out of the way;</li>
 * <li>{@link #SLIDE}: the folded flight slides out of its bay on its rails, level;</li>
 * <li>{@link #SWING}: it rises to the sill and tips down to its slope, swinging about the head of its stringers;</li>
 * <li>{@link #EXTEND}: the lower sections run out down the slope, to the ground or as far as they go;</li>
 * <li>{@link #OUT}: the handrails rise out of the stringers.</li>
 * </ol>
 * Everything is measured in blocks, in the door's own frame as {@code PlugDoorRenderer} draws it: x across the
 * doorway to the right as seen from outside, y up from the sill, z out, the hull's outer face at z 1.
 *
 * <p>The flight is drawn with the machinery that works it (see {@link AirstairMachinery}), so nothing moves it by
 * unseen hands. A rocket's also has a bridge plate: her skin at her sill is cut to her curve, so her floor's edge can
 * stand back from the door's face, and a plate hinged under the top tread swings up as the handrails rise to lie level
 * with the sill across the gap ({@link Motion#sillGap}).
 *
 * <p>A zeppelin's boarding stairs are the same flight hung in her belly ({@link Motion#belly}), their sill the lower
 * deck's floor at the head of the stairs and z out along the way they come down: stowed, the folded flight lies level
 * in the row under the floor, right over the hatch its controller opens in her cover, so it slides nowhere and has no
 * hatch of its own to draw; it swings straight down as soon as the hatch is open, and runs out to the ground.
 */
public final class AirstairFlight {
    /** When each step of coming out ends, in ticks from stowed: 4.6 seconds in all. */
    public static final int HATCH = 10;
    public static final int SLIDE = 30;
    public static final int SWING = 50;
    public static final int EXTEND = 80;
    public static final int OUT = 92;
    /** The airliner's flight's width, and a zeppelin's, in blocks: the airliner's doorway's. */
    public static final int WIDTH = PlugDoorBlock.COLUMNS;
    /** How wide a hatch plate is, in blocks, as its picture is: the airliner's; a wider mouth has them side by side. */
    private static final int HATCH_WIDTH = PlugDoorBlock.COLUMNS;
    /** The fewest sections the flight is made of; more where the bay is short. */
    public static final int FEWEST_SECTIONS = 3;
    /** The most it may be made of, however short the bay. */
    public static final int MOST_SECTIONS = 8;

    static final float PX = 1 / 16F;
    /** A stringer's width, and how much narrower each nested section is on either side. */
    private static final float STRINGER_WIDTH = 2 * PX;
    private static final float NEST_INSET = PX;
    /** How much shallower each nested section's stringers are, top and bottom, so no faces of two meet. */
    private static final float NEST_SHALLOWER = PX / 4;
    /** How deep a stringer is, square to the slope, and how far its top edge stands over the treads' nosings. */
    private static final float STRINGER_DEPTH = 6.5F * PX;
    private static final float STRINGER_RISE = 0.5F * PX;
    private static final float ROOT_TWO = (float) Math.sqrt(2);
    /** How far the stringers' top edges stand over the nosings, upright. */
    private static final float STRINGER_TOP = STRINGER_RISE * ROOT_TWO;
    /**
     * Where the foot of a stringer stands at the bottom of a flight run out to its length: so far up from the ground it
     * was laid for, and so far back from the flight's end toward the door, in blocks. Its legs run down from there.
     */
    static final float FOOT_LIFT = 0.5F + STRINGER_TOP - STRINGER_DEPTH / ROOT_TWO;
    static final float FOOT_BACK = STRINGER_DEPTH / ROOT_TWO;
    /**
     * A foot's leg, as wide as its stringer: how deep it is along the flight; and the rubber pad it stands on, how far
     * it spreads round the leg and how thick it is.
     */
    private static final float LEG_DEPTH = 2 * PX;
    private static final float PAD_SPREAD = PX;
    private static final float PAD_THICKNESS = PX;
    /** The top platform's beams, under the top tread: as deep as the stringers' upright cut. */
    private static final float BEAM_DEPTH = 9 * PX;
    /** A tread's plate, and the nosing along its front: its depth and how thick it is. */
    static final float TREAD_THICKNESS = PX;
    private static final float NOSING_DEPTH = 2 * PX;
    private static final float NOSING_THICKNESS = 1.5F * PX;
    /**
     * The bridge plate: the longest it is, as long as the top tread it folds under; the narrowest gap it is drawn
     * for; how far in from the flight's sides its ends stop, so they never lie in the stringers' faces; and the line
     * it swings about, under the top tread's back edge, low enough that folded it clears the tread's nosing.
     */
    private static final float LIP_LONGEST = 0.5F;
    private static final float LIP_LEAST = PX / 4;
    private static final float LIP_SIDE = PX / 8;
    private static final float LIP_HINGE_Y = -(TREAD_THICKNESS + NOSING_THICKNESS) / 2 - PX / 32;
    /** The handrail: how high it stands over the stringers' tops once up, its tube, and its posts. */
    private static final float RAIL_HEIGHT = 14 * PX;
    private static final float RAIL_SIZE = 2 * PX;
    private static final float POST_SIZE = 1.25F * PX;
    /** How much thinner and lower each nested section's rail lies than the one it nests in while down. */
    private static final float NESTED_RAIL_THINNER = PX / 4;
    private static final float NESTED_RAIL_LOWER = PX / 2;
    /** How thick the lug fixing a post to a stringer it stands clear of is. */
    private static final float LUG_THICKNESS = PX;
    /** Where the platform's post stands, out from the hull's face. */
    private static final float PLATFORM_POST = 3 * PX;
    /** The bay's mouth is the row under the sill: y -2 to -1. The folded flight passes it with this much to spare. */
    static final float MOUTH_TOP = -1;
    static final float MOUTH_BOTTOM = -2;
    private static final float CLEARANCE = 0.06F;
    /** The hatch's plate. */
    private static final float HATCH_THICKNESS = PX;
    /**
     * Where the flight swings about, out from the hull's face and up from the sill: the foot of the platform's beams,
     * at the head of the stringers.
     */
    static final float PIVOT_U = 0.5F;
    static final float PIVOT_Y = STRINGER_TOP - BEAM_DEPTH;
    /** The tilt between level, as it slides out of the bay, and the slope. */
    static final float SLOPE = (float) Math.PI / 4;

    private AirstairFlight() {
    }

    /**
     * The parts the flight is drawn from, each with its own look; its machinery (see {@link AirstairMachinery}) adds
     * its dark steel, its yellow carriage and its chrome rods.
     */
    public enum Part {
        TREAD, NOSING, STRINGER, RAIL, POST, HATCH, TRACK, CARRIAGE, ROD
    }

    /** Where the drawn boxes go. Each box spans {@code from} to {@code to} in the frame {@code pose} gives. */
    @FunctionalInterface
    public interface Sink {
        void box(Matrix4f pose, Vector3f from, Vector3f to, Part part);
    }

    /**
     * The airstairs at a moment: how far through coming out ({@code ticks}, 0 stowed to {@link #OUT}), how many blocks
     * they reach down ({@code length}), how many they are built to reach ({@code rise}), in how many sections and how
     * many blocks wide; how far in from the door's face the hull's skin over the bay's mouth lies, and how deep the
     * mouth is; whether the hatch is off the mouth; whether they hang in a zeppelin's belly rather than in a door; how
     * far the floor's edge at the door stands back from its face, in blocks, the gap the bridge plate spans; and how
     * far the legs of the flight's feet run down to the ground once it is out, in blocks.
     */
    public record Motion(float ticks, int length, int rise, int sections, int width, float hatchInset, int mouth, boolean hatchOpen,
                         boolean belly, float sillGap, float legs) {
        /** Airstairs whose top tread meets their sill: an airliner's or a zeppelin's. */
        public Motion(float ticks, int length, int rise, int sections, int width, float hatchInset, int mouth, boolean hatchOpen,
                      boolean belly, float legs) {
            this(ticks, length, rise, sections, width, hatchInset, mouth, hatchOpen, belly, 0, legs);
        }
    }

    /**
     * How many treads each section has: the whole flight's, shared out, each section run out keeping its top tread
     * under the bottom one of the section it runs out of.
     */
    public static int sectionTreads(int rise, int sections) {
        return Math.max(2, Math.ceilDiv(2 * rise + sections - 1, sections));
    }

    /** The fewest blocks the flight reaches down: the top section's, which comes out whole. */
    public static int shortest(int rise, int sections) {
        return Math.ceilDiv(sectionTreads(rise, sections), 2);
    }

    /**
     * How many rows of steps, a block down each, the flight covers at a moment as it comes out: none until it has
     * swung down to its slope, then the top section's and those the lower ones have run out over, all of them once it
     * is out.
     */
    public static int rowsOut(float ticks, int length, int rise, int sections) {
        if (ticks < SWING) {
            return 0;
        }
        int treads = sectionTreads(rise, sections);
        float extension = Math.max(0, 2 * length - treads) * ease(clamp((ticks - SWING) / (EXTEND - SWING)));
        float covered = Math.min(extension, (sections - 1) * (treads - 1F)) + treads;
        return Math.min(length, (int) (covered / 2));
    }

    /** How long a bay the folded flight needs behind the mouth, in blocks. */
    public static float bayLength(int rise, int sections) {
        Bounds level = Bounds.level(rise, sections);
        return level.maxU - level.minU + 2 * CLEARANCE;
    }

    /** The fewest sections whose folded flight fits a bay this many blocks long, or 0 if none does. */
    public static int sectionsFor(int rise, int bay) {
        for (int sections = FEWEST_SECTIONS; sections <= MOST_SECTIONS; sections++) {
            if (bayLength(rise, sections) <= bay) {
                return sections;
            }
        }
        return 0;
    }

    /**
     * Draws the hatch and the flight as they are at the moment, with the machinery working it and a rocket's bridge
     * plate.
     */
    public static void draw(Motion motion, Sink sink) {
        float t = motion.ticks();
        if (motion.hatchOpen() && !motion.belly()) {
            hatch(motion, ease(clamp(t / HATCH)), sink);
        }
        if (t <= 0 && !motion.hatchOpen()) {
            return;
        }
        int treads = sectionTreads(motion.rise(), motion.sections());
        Pose pose = pose(motion, t);
        float run = clamp((t - SWING) / (EXTEND - SWING));
        float extension = Math.max(0, 2 * motion.length() - treads) * ease(run);
        float up = ease(clamp((t - EXTEND) / (OUT - EXTEND)));
        Matrix4f nest = pose.matrix();
        // Each section runs out of the one above as far as it can, the next one with it, until the bottom one is down.
        float coverEnd = 0;
        float offset = 0;
        for (int section = 0; section < motion.sections(); section++) {
            offset = Math.min(extension, section * (treads - 1F));
            section(nest, section, offset, treads, motion.width(), coverEnd, up, sink);
            coverEnd = offset + treads;
        }
        if (!motion.belly() && motion.sillGap() >= LIP_LEAST) {
            lip(nest, Math.min(motion.sillGap(), LIP_LONGEST), motion.width(), up, sink);
        }
        if (motion.legs() > 0 && up > 0) {
            feet(nest, motion.sections() - 1, offset, treads, motion.width(), motion.legs() * up, sink);
        }
        AirstairMachinery.draw(motion, nest, pose.slid(), offset, treads, sink);
    }

    /**
     * The bridge plate across the gap between the top tread and the floor's edge this far back from the door's face,
     * hinged under the top tread's back edge: folded under the tread until the handrails rise, then swung down and up
     * into the gap with them, so it lies level with the sill and the tread, a yellow edge along the sill. Opened this
     * far (0 folded, 1 down), and split at each block's edge as the treads are.
     */
    private static void lip(Matrix4f nest, float gap, int width, float open, Sink sink) {
        Matrix4f pose = new Matrix4f(nest)
            .translate(0, LIP_HINGE_Y, 0)
            .rotateX((float) -Math.PI * (1 - open))
            .translate(0, -LIP_HINGE_Y, 0);
        float edge = Math.min(NOSING_DEPTH, gap / 2);
        for (int block = 0; block < width; block++) {
            float left = Math.max(LIP_SIDE, block);
            float right = Math.min(width - LIP_SIDE, block + 1);
            sink.box(pose, new Vector3f(left, -TREAD_THICKNESS, edge - gap), new Vector3f(right, 0, 0), Part.TREAD);
            sink.box(pose, new Vector3f(left, -TREAD_THICKNESS, -gap), new Vector3f(right, 0, edge - gap), Part.NOSING);
        }
    }

    /**
     * The hatch over the mouth, hinged along its foot, swung this far open (0 shut, 1 hanging down): a plate for every
     * {@link #HATCH_WIDTH} blocks across it, side by side.
     */
    private static void hatch(Motion motion, float open, Sink sink) {
        float face = 1 - motion.hatchInset();
        Matrix4f pose = new Matrix4f()
            .translate(0, MOUTH_BOTTOM, face)
            .rotateX((float) Math.PI * open)
            .translate(0, -MOUTH_BOTTOM, -face);
        for (int from = 0; from < motion.width(); from += HATCH_WIDTH) {
            float to = Math.min(motion.width(), from + HATCH_WIDTH);
            sink.box(pose, new Vector3f(from + PX / 2, MOUTH_BOTTOM, face - HATCH_THICKNESS), new Vector3f(to - PX / 2, MOUTH_TOP, face), Part.HATCH);
        }
    }

    /**
     * Where the folded flight is: slid, raised and tilted, by its pose's three amounts; and how far out it has slid
     * alone, before the swing draws it back in, which the machinery's carriage follows.
     */
    private record Pose(float tilt, float out, float up, float slid) {
        Matrix4f matrix() {
            return new Matrix4f()
                .translate(0, up, 1 + out)
                .translate(0, PIVOT_Y, PIVOT_U)
                .rotateX(-tilt)
                .translate(0, -PIVOT_Y, -PIVOT_U);
        }
    }

    /**
     * The flight's pose at the moment: in the bay, then slid out level under the sill, then raised and swung down to
     * its slope, where it stays as its sections run out. In a zeppelin's belly it lies over its hatch already, and
     * swings down as soon as the hatch is open.
     */
    private static Pose pose(Motion motion, float t) {
        Bounds level = Bounds.level(motion.rise(), motion.sections());
        // Swung up level, it stands just clear of the hull's face; lowered from there, it runs in the mouth's middle.
        float clear = CLEARANCE - level.minU;
        float lowered = (MOUTH_TOP + MOUTH_BOTTOM) / 2 - (level.maxY + level.minY) / 2;
        float stowed = -(level.maxU + clear) - motion.mouth() - CLEARANCE;
        float slide = motion.belly() ? 1 : ease(clamp((t - HATCH) / (SLIDE - HATCH)));
        float swingFrom = motion.belly() ? HATCH : SLIDE;
        float swing = clamp((t - swingFrom) / (SWING - swingFrom));
        float lift = ease(clamp(swing / 0.6F));
        float tilt = ease(clamp((swing - 0.3F) / 0.7F));
        float slid = clear + stowed * (1 - slide);
        return new Pose(SLOPE * (1 - tilt), slid - clear * tilt, lowered * (1 - lift), slid);
    }

    /**
     * The flight's feet, under the foot of each stringer of its bottom section, run out this many treads: a leg as wide
     * as the stringer, from inside the stringer down so far, and the rubber pad it stands on. The stringers stand back
     * from the ground the flight was laid for, and an airframe can stand a little higher once assembled, so it is the
     * legs that stand the stairs on the ground; they run down as the handrails rise, and back up as they fall.
     */
    private static void feet(Matrix4f nest, int section, float offset, int treads, int width, float down, Sink sink) {
        Matrix4f pose = new Matrix4f(nest).translate(0, -offset / 2, offset / 2);
        float inset = section * NEST_INSET;
        float shrink = section * NEST_SHALLOWER;
        float depth = STRINGER_DEPTH - 2 * shrink;
        // The bottom tread's stringers' lowest corner, at the flight's end: its underside meets the end's cut there.
        float u = treads / 2F - depth / ROOT_TWO;
        float y = (1 - treads) / 2F + (STRINGER_RISE - shrink) * ROOT_TWO - depth / ROOT_TWO;
        float ground = y - down;
        for (float side : new float[]{inset, width - inset - STRINGER_WIDTH}) {
            sink.box(pose, new Vector3f(side, ground + PAD_THICKNESS, u - LEG_DEPTH), new Vector3f(side + STRINGER_WIDTH, y + LEG_DEPTH, u),
                Part.STRINGER);
            sink.box(pose, new Vector3f(side - PAD_SPREAD, ground, u - LEG_DEPTH - PAD_SPREAD),
                new Vector3f(side + STRINGER_WIDTH + PAD_SPREAD, ground + PAD_THICKNESS, u + PAD_SPREAD), Part.TRACK);
        }
    }

    /**
     * One section of a flight so many blocks wide, run out this many treads down the slope from where it nests: its
     * stringers, the treads not hidden under the section above, and its handrails, risen this far (0 down, 1 up). The
     * top section also has the platform: the top tread on beams level with the sill, and the handrail's post at the
     * hull.
     */
    private static void section(Matrix4f nest, int section, float offset, int treads, int width, float coverEnd, float up, Sink sink) {
        Matrix4f pose = new Matrix4f(nest).translate(0, -offset / 2, offset / 2);
        float inset = section * NEST_INSET;
        float left = inset;
        float right = width - inset;
        float inner = STRINGER_WIDTH;
        float shrink = section * NEST_SHALLOWER;
        if (section == 0) {
            for (float side : new float[]{left, right - inner}) {
                sink.box(pose, new Vector3f(side, -BEAM_DEPTH, 0), new Vector3f(side + inner, STRINGER_TOP, 0.5F), Part.STRINGER);
            }
        }
        for (int tread = 1; tread <= treads; tread++) {
            boolean covered = section > 0 && offset + tread <= coverEnd + 0.001F;
            if (!covered) {
                tread(pose, tread, left + inner, right - inner, width, sink);
            }
            if (tread > 1) {
                for (float side : new float[]{left, right - inner}) {
                    slopeBox(pose, side, side + inner, (tread - 1) / 2F, tread / 2F, STRINGER_RISE - shrink, STRINGER_DEPTH - 2 * shrink, Part.STRINGER, sink);
                }
            }
        }
        handrail(pose, section, left + inner / 2, inner / 2, offset, treads, coverEnd, up, sink);
        handrail(pose, section, right - inner / 2, width - inner / 2, offset, treads, coverEnd, up, sink);
    }

    /** A tread across a flight so many blocks wide: its plate, and the yellow nosing along its front edge. */
    private static void tread(Matrix4f pose, int tread, float from, float to, int width, Sink sink) {
        float back = (tread - 1) / 2F;
        float front = tread / 2F;
        float top = (1 - tread) / 2F;
        // Split at each block's edge so no box is wider than a block, as the textures repeat by the block.
        for (int block = 0; block < width; block++) {
            float left = Math.max(from, block);
            float right = Math.min(to, block + 1);
            sink.box(pose, new Vector3f(left, top - TREAD_THICKNESS, back), new Vector3f(right, top, front - NOSING_DEPTH), Part.TREAD);
            sink.box(pose, new Vector3f(left, top - NOSING_THICKNESS, front - NOSING_DEPTH), new Vector3f(right, top, front), Part.NOSING);
        }
    }

    /**
     * A handrail along one side of a section, over the middle of its stringer ({@code stringer}, across) and risen this
     * far: a post at every other tread and the last, and the rail along their tops, where the section runs out past the
     * one it nests in (the top section all along, from its platform post). Down, the rail lies on its own stringer's
     * top, a nested section's a little lower and thinner than the one it nests in. Rising, it moves out to the top
     * section's line ({@code line}, across), height and thickness, so once up the rails of all the sections run on as
     * one: each ran on a little further in and lower than the one above, which ended in a stub in the air where they
     * met. A nested section's posts stand on that line too, each fixed to its own stringer by a lug where it stands
     * clear of it.
     */
    private static void handrail(Matrix4f pose, int section, float stringer, float line, float offset, int treads, float coverEnd, float up,
                                 Sink sink) {
        float down = 1 - up;
        float middle = stringer + (line - stringer) * up;
        float size = RAIL_SIZE - section * NESTED_RAIL_THINNER * down;
        float height = RAIL_HEIGHT * up - section * NESTED_RAIL_LOWER * down;
        boolean risen = height > 0.01F;
        // The rail's centre line: over the top section's stringers' top edge, which runs along the nosings.
        float lift = STRINGER_TOP + height + size / 2;
        // How far this section's stringers' top edge lies under the top section's, upright.
        float sunk = section * NEST_SHALLOWER * ROOT_TWO;
        float start = section == 0 ? 1 : Math.max(1, (float) Math.floor(coverEnd - offset));
        if (section == 0) {
            if (risen) {
                post(pose, middle, PLATFORM_POST, STRINGER_TOP, STRINGER_TOP + height, sink);
            }
            bar(pose, new Vector3f(middle, lift, PLATFORM_POST), new Vector3f(middle, lift, 0.5F), size, sink);
        }
        for (int tread = 1; tread <= treads; tread++) {
            float front = tread / 2F;
            boolean shows = section == 0 || offset + tread > coverEnd;
            if (risen && shows && (tread % 2 == 0 || tread == treads)) {
                float foot = 0.5F - front + STRINGER_TOP - sunk;
                post(pose, middle, front, foot, foot + sunk + height, sink);
                lug(pose, middle, stringer, front, foot, sink);
            }
            if (tread > start) {
                float back = front - 0.5F;
                bar(pose, new Vector3f(middle, 0.5F - back + lift, back), new Vector3f(middle, 0.5F - front + lift, front), size, sink);
            }
        }
    }

    /** A post at a point along a stringer, from its foot up to the rail, heights over the sill. */
    private static void post(Matrix4f pose, float middle, float u, float foot, float top, Sink sink) {
        float half = POST_SIZE / 2;
        sink.box(pose, new Vector3f(middle - half, foot, u - half), new Vector3f(middle + half, top, u + half), Part.POST);
    }

    /**
     * The lug fixing a post standing out on the rail's line to the stringer under it, its top at the stringer's top
     * edge: none where the post stands on the stringer.
     */
    private static void lug(Matrix4f pose, float post, float stringer, float u, float top, Sink sink) {
        if (Math.abs(post - stringer) <= (POST_SIZE + STRINGER_WIDTH) / 2) {
            return;
        }
        float half = POST_SIZE / 2;
        sink.box(pose, new Vector3f(Math.min(post, stringer), top - LUG_THICKNESS, u - half), new Vector3f(Math.max(post, stringer), top, u + half),
            Part.POST);
    }

    /**
     * A round tube from one point to another, drawn as two square bars about the same line, one turned an eighth of a
     * turn on the other, which together read as round.
     */
    private static void bar(Matrix4f pose, Vector3f from, Vector3f to, float size, Sink sink) {
        float length = from.distance(to);
        float pitch = (float) Math.atan2(to.y - from.y, to.z - from.z);
        Matrix4f along = new Matrix4f(pose).translate(from).rotateX(-pitch);
        float half = size / 2;
        sink.box(along, new Vector3f(-half, -half, 0), new Vector3f(half, half, length), Part.RAIL);
        float turned = half * 0.92F;
        sink.box(new Matrix4f(along).rotateZ((float) Math.PI / 4), new Vector3f(-turned, -turned, 0), new Vector3f(turned, turned, length), Part.RAIL);
    }

    /**
     * A box along the slope between two points out from the hull's face, across from {@code x0} to {@code x1}: its top
     * edge {@code above} over the line through the nosings, and {@code depth} deep, square to the slope.
     */
    private static void slopeBox(Matrix4f pose, float x0, float x1, float fromU, float toU, float above, float depth, Part part, Sink sink) {
        // The nosings' line, y = 1/2 - z, falls a block for every block out.
        Vector3f top = new Vector3f(x0, 0.5F - fromU + above * ROOT_TWO, fromU);
        float length = (toU - fromU) * ROOT_TWO;
        Matrix4f along = new Matrix4f(pose).translate(top).rotateX(SLOPE);
        sink.box(along, new Vector3f(0, -depth, 0), new Vector3f(x1 - x0, 0, length), part);
    }

    /**
     * How far the folded flight reaches, out from the hull's face and up from the sill, swung up level about its
     * pivot, its sections nested and its rails down: the same whatever its width, so measured on the airliner's.
     */
    private record Bounds(float minU, float maxU, float minY, float maxY) {
        /** Worked out once for each build of stairs, by rise and sections: every frame draws from them. */
        private static final Map<Long, Bounds> MEASURED = new ConcurrentHashMap<>();

        static Bounds level(int rise, int sections) {
            return MEASURED.computeIfAbsent((long) rise << 32 | sections, key -> measure(rise, sections));
        }

        private static Bounds measure(int rise, int sections) {
            float[] reach = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
            Vector3f corner = new Vector3f();
            Matrix4f nest = new Pose(SLOPE, 0, 0, 0).matrix();
            int treads = sectionTreads(rise, sections);
            Sink measure = (pose, from, to, part) -> {
                for (int i = 0; i < 8; i++) {
                    pose.transformPosition((i & 1) == 0 ? from.x : to.x, (i & 2) == 0 ? from.y : to.y, (i & 4) == 0 ? from.z : to.z, corner);
                    reach[0] = Math.min(reach[0], corner.z - 1);
                    reach[1] = Math.max(reach[1], corner.z - 1);
                    reach[2] = Math.min(reach[2], corner.y);
                    reach[3] = Math.max(reach[3], corner.y);
                }
            };
            for (int section = 0; section < sections; section++) {
                section(nest, section, 0, treads, WIDTH, treads, 0, measure);
            }
            return new Bounds(reach[0], reach[1], reach[2], reach[3]);
        }
    }

    private static float clamp(float t) {
        return Math.clamp(t, 0F, 1F);
    }

    /** Eased in and out, so each move starts and stops gently. */
    private static float ease(float t) {
        return t * t * (3 - 2 * t);
    }
}
