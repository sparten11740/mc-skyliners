package dev.skyliners.block;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;

/**
 * An evacuation slide's build, its inflation and the way down it, as its control lays it out (see
 * {@link SlideControlBlockEntity}), its renderer draws it and its riders follow it. A big inflated slide, as an
 * airliner's is: a wide sliding lane slung between two raised side tubes, the rails, each over a grey beam tube under
 * it, the lane's top yellow and its underside grey, a reflective stripe along the rails' outer sides. Its head is as
 * wide as the doorway, held to the sill by its girt bar, and it widens a block out; it runs straight down to the
 * ground at its angle, rounds out onto it and runs on along it a little, its toe curling up at the end.
 *
 * <p>Packed, it is in the doorway's lining. Coming out it runs through three steps, which going away runs back: the
 * pack drops out of the doorway onto the sill ({@link #DROP}), the slide unrolls down to the ground behind the roll of
 * what is still packed ({@link #UNROLLED}), filling with gas from its head as it goes, and fills out firm, with a last
 * bounce ({@link #FIRM}).
 *
 * <p>Everything is measured in blocks in the served door's own frame, as {@code PlugDoorRenderer} draws it: x across
 * the doorway to the right as seen from outside, y up from the sill, z out, the hull's outer face at z 1. Along the
 * slide, u is how far out from the hull's face, and s how far down the lane's middle from its head.
 */
public final class EvacuationSlide {
    /** How long the slide takes to come out once the door is open, in ticks: two seconds. */
    public static final int INFLATE = 40;
    /** The shares of coming out when the pack has dropped out of the doorway, the slide has unrolled, and it is firm. */
    private static final float DROP = 0.08F;
    private static final float UNROLLED = 0.65F;
    private static final float FIRM = 0.9F;
    /** The lane's width, in blocks: the doorway's, which the slide's head fills. */
    public static final int WIDTH = PlugDoorBlock.COLUMNS;
    /**
     * The slide's angles to try, in degrees, the first that reaches the ground clear of everything taken: the most
     * comfortable first, then steeper, which lands closer in, then shallower.
     */
    private static final float[] ANGLES = {35, 40, 30, 45, 25};
    /** The fewest blocks down from the sill to the ground worth a slide. */
    private static final float LEAST_DROP = 2;
    /** How far out the ground is looked for under the slide each step, in blocks. */
    private static final float PROBE_STEP = 0.25F;
    /** How far over the lane a rider's body reaches, where nothing may stand: the heights looked at, in blocks. */
    private static final float[] BODY = {0.3F, 1.0F, 1.7F};
    /** How far the toe runs along the ground, and the least it may, where something stands at its end. */
    private static final float TOE = 1.5F;
    private static final float LEAST_TOE = 0.5F;
    /** How tight the slide rounds out onto the ground, and how its toe curls up at the end: radii and the curl's angle. */
    private static final float ROUND_OUT = 2.0F;
    private static final float CURL_RADIUS = 0.8F;
    private static final float CURL = (float) Math.toRadians(30);

    /** Half the slide's width at its head, in the doorway, and once it has widened, out past the parked door. */
    private static final float HEAD_HALF_WIDTH = 0.95F;
    private static final float HALF_WIDTH = 1.45F;
    /** Where it widens: from this far out from the hull's face, over as far again. */
    private static final float WIDEN_FROM = 0.9F;
    private static final float WIDEN_OVER = 0.9F;
    /** The rails, the side tubes along the lane's edges, and the beams under them; how thick the lane is. */
    private static final float RAIL = 0.3F;
    private static final float BEAM = 0.25F;
    private static final float LANE = 0.06F;
    /** How high the lane lies over the ground on the toe, which rests on its beams. */
    private static final float LIFT = LANE + 2 * BEAM + 0.02F;
    /** How far the rails stand over the lane, as a share of their height. */
    private static final float RAIL_RISE = 0.55F;
    /** The head tube across under the lane at its head, how far down it from the sill, and how thick. */
    private static final float HEAD_TUBE_AT = 0.2F;
    private static final float HEAD_TUBE = 0.2F;
    /** The girt bar across the sill, holding the head: its ends in from the doorway's edges, its depth and height. */
    private static final float GIRT_INSET = 0.1F;
    private static final float GIRT_DEPTH = 0.08F;
    private static final float GIRT_HEIGHT = 0.08F;
    /** Off the hull's face, so the girt never fights it. */
    private static final float OFF_HULL = 0.005F;
    /** The packed slide's roll: as packed, and the least it shrinks to before it has all unrolled. */
    private static final float PACK = 0.4F;
    private static final float LEAST_ROLL = 0.12F;
    /** How far in from the slide's sides the roll's ends are. */
    private static final float ROLL_INSET = 0.05F;
    /** Where the pack lies packed: on the sill, this far in from the hull's face. */
    private static final float PACKED_AT = -0.35F;
    /** How many sides the tubes are drawn with. */
    private static final int SIDES = 10;
    /** How far apart the tubes' rings are, in blocks along the lane: on the straight, and round the bends and at the head. */
    private static final float STRAIGHT_STEP = 1;
    private static final float BEND_STEP = 0.25F;
    /** How far short of the toe's end a rider stops, in blocks. */
    private static final float RIDE_SHORT = 0.2F;

    private EvacuationSlide() {
    }

    /** What is in a block of the slide's way: nothing to stop it, the ground to land on, or something it must not touch. */
    public enum Cell {
        OPEN, GROUND, BLOCKED
    }

    /** What is in each block round the door, by its place in the door's frame. */
    @FunctionalInterface
    public interface Ground {
        Cell at(int x, int y, int z);
    }

    /** Why a slide cannot come out. */
    public enum Refusal {
        /** No ground within its reach below the door: high up, or out over a drop. */
        NO_GROUND,
        /** Something stands in its way, or it would come down on the airliner itself. */
        BLOCKED,
        /** The ground is right under the door, no slide needed. */
        TOO_LOW
    }

    /**
     * A slide laid out for the ground below a door: its angle (degrees below level), how high its toe's lane lies (up
     * from the sill, so below it), and how far the toe runs along the ground.
     */
    public record Plan(float angle, float toeY, float toe) {
        public Path path() {
            return new Path(this);
        }
    }

    /** What laying out a slide came to: a plan, or why there is none. */
    public record Outcome(Plan plan, Refusal refusal) {
    }

    /**
     * Lays a slide out down to the ground: at the first of its angles whose lane comes down onto the ground within
     * {@code maxDrop} blocks below the sill, with room over it all the way for whoever rides it and room along the
     * ground for its toe, over neither the airliner's own blocks nor anything else in its way.
     */
    public static Outcome plan(Ground ground, float maxDrop) {
        Refusal why = Refusal.NO_GROUND;
        for (float angle : ANGLES) {
            Outcome tried = plan(ground, maxDrop, angle);
            if (tried.plan() != null) {
                return tried;
            }
            // A slide that hits something says so, rather than that there is no ground.
            if (tried.refusal() != Refusal.NO_GROUND) {
                why = tried.refusal();
            }
        }
        return new Outcome(null, why);
    }

    private static Outcome plan(Ground ground, float maxDrop, float angle) {
        float grade = (float) Math.tan(Math.toRadians(angle));
        int groundTop = Integer.MIN_VALUE;
        float touchdown = 0;
        for (int k = 1; groundTop == Integer.MIN_VALUE; k++) {
            float u = k * PROBE_STEP;
            float y = -grade * u;
            if (-y > maxDrop + 1) {
                return new Outcome(null, Refusal.NO_GROUND);
            }
            int z = Mth.floor(1 + u);
            for (int column = 0; column < WIDTH; column++) {
                int under = Mth.floor(y - 0.02F);
                Cell below = ground.at(column, under, z);
                if (below == Cell.BLOCKED) {
                    return new Outcome(null, Refusal.BLOCKED);
                }
                for (float body : BODY) {
                    if (ground.at(column, Mth.floor(y + body), z) != Cell.OPEN) {
                        return new Outcome(null, Refusal.BLOCKED);
                    }
                }
                if (below == Cell.GROUND) {
                    groundTop = Math.max(groundTop, under + 1);
                    touchdown = u;
                }
            }
        }
        if (-groundTop < LEAST_DROP) {
            return new Outcome(null, Refusal.TOO_LOW);
        }
        // Along the ground the toe runs out as far as it has room, a rider's height over the ground.
        Path path = new Plan(angle, groundTop + LIFT, TOE).path();
        float end = path.toeStart + TOE;
        for (float u = touchdown; u <= end + PROBE_STEP; u += PROBE_STEP) {
            int z = Mth.floor(1 + u);
            for (int column = 0; column < WIDTH; column++) {
                if (ground.at(column, groundTop, z) != Cell.OPEN || ground.at(column, groundTop + 1, z) != Cell.OPEN) {
                    end = Math.min(end, u - PROBE_STEP);
                }
            }
        }
        float toe = end - path.toeStart;
        if (toe < LEAST_TOE) {
            return new Outcome(null, Refusal.BLOCKED);
        }
        return new Outcome(new Plan(angle, groundTop + LIFT, toe), null);
    }

    /** A point on the lane's middle line, and the lane's slope there: u out from the hull's face, y up, angle down. */
    public record Point(float u, float y, float slope) {
    }

    /**
     * The lane's middle line, from the sill at its head down to its toe: straight at the slide's angle, round onto the
     * ground, along it, and curling up at the end.
     */
    public static final class Path {
        private final float angle;
        private final float toeY;
        private final float toe;
        private final float grade;
        /** How long the straight run is, the round-out onto the ground and the toe; where the round-out starts and ends. */
        private final float straight;
        private final float roundOut;
        private final float radius;
        private final float bendStartU;
        private final float bendCentreU;
        private final float bendCentreY;
        final float toeStart;

        private Path(Plan plan) {
            angle = (float) Math.toRadians(plan.angle());
            toeY = plan.toeY();
            toe = plan.toe();
            grade = (float) Math.tan(angle);
            float cos = Mth.cos(angle);
            float sin = Mth.sin(angle);
            // Where the straight run would meet the toe's level, and the round-out between them, tangent to both; a
            // shorter one where the slide is too short for the whole of it.
            float corner = -toeY / grade;
            float half = (float) Math.tan(angle / 2);
            radius = Math.max(0.1F, Math.min(ROUND_OUT, (corner - 0.3F * cos) / cos / half));
            float tangent = radius * half;
            bendStartU = corner - tangent * cos;
            straight = bendStartU / cos;
            roundOut = radius * angle;
            bendCentreU = bendStartU + radius * sin;
            bendCentreY = -grade * bendStartU + radius * cos;
            toeStart = corner + tangent;
        }

        /** How far down the lane a rider goes: to just short of the toe's end. */
        public float rideLength() {
            return straight + roundOut + toe - RIDE_SHORT;
        }

        /** How far down the lane its toe starts, out of the round-out along the ground. */
        public float toeAlong() {
            return straight + roundOut;
        }

        /** How long the lane is, its curled tip and all. */
        public float length() {
            return straight + roundOut + toe + CURL_RADIUS * CURL;
        }

        /** How far into the round-out onto the ground a point this far down the lane is, 0 before it to 1 past it. */
        float bend(float s) {
            return Mth.clamp((s - straight) / roundOut, 0, 1);
        }

        /** The lane's middle at this far down it, from its head (0) to the tip of its toe ({@link #length()}). */
        public Point at(float s) {
            if (s <= straight) {
                float u = s * Mth.cos(angle);
                return new Point(u, -grade * u, angle);
            }
            if (s <= straight + roundOut) {
                float slope = angle - (s - straight) / radius;
                return new Point(bendCentreU - radius * Mth.sin(slope), bendCentreY - radius * Mth.cos(slope), slope);
            }
            float along = s - straight - roundOut;
            if (along <= toe) {
                return new Point(toeStart + along, toeY, 0);
            }
            float curl = Math.min(along - toe, CURL_RADIUS * CURL) / CURL_RADIUS;
            return new Point(toeStart + toe + CURL_RADIUS * Mth.sin(curl), toeY + CURL_RADIUS * (1 - Mth.cos(curl)), -curl);
        }

        /** How far down the lane its middle passes this far out from the hull's face, up to where riders stop. */
        public float along(float u) {
            if (u <= bendStartU) {
                return Math.max(0, u / Mth.cos(angle));
            }
            if (u <= toeStart) {
                float slope = (float) Math.asin(Mth.clamp((bendCentreU - u) / radius, -1, 1));
                return Math.min(straight + (angle - slope) * radius, rideLength());
            }
            return Math.min(straight + roundOut + u - toeStart, rideLength());
        }

        /** How high the lane is this far out from the hull's face. */
        public float surface(float u) {
            return at(along(u)).y();
        }

        /** How far out the slide reaches from the hull's face, its curled tip and all. */
        public float reach() {
            return at(length()).u() + RAIL;
        }

        /** How far down it reaches from the sill: to the ground under its toe. */
        public float depth() {
            return LIFT - toeY;
        }

        /** How high the ground is under the toe, up from the sill. */
        public float groundY() {
            return toeY - LIFT;
        }

        /** How far out a rider stops on the toe. */
        public float rideEnd() {
            return at(rideLength()).u();
        }
    }

    /**
     * How far down from the sill the slide's steps lie, one in each of the lane's columns for each block out: the top
     * of each just under the lane where it is lowest over that block, so a rider in the doorway steps out onto the
     * slide and anyone finding their way out walks down it. None over the last block above the ground, so the slide
     * never rests on the ground through them. Each is given as {x, y, z, eighths}: the block in the door's frame and
     * how many eighths of it are solid from its foot.
     */
    public static int[][] steps(Plan plan) {
        Path path = plan.path();
        int groundTop = Mth.floor(path.groundY() + 0.01F);
        float end = path.rideEnd();
        List<int[]> steps = new ArrayList<>();
        for (int out = 0; out < end; out++) {
            float top = path.surface(Math.min(out + 1, end));
            int y = Mth.ceil(top) - 1;
            if (y <= groundTop) {
                break;
            }
            int eighths = Mth.clamp(Mth.floor((top - y) * 8), 1, 8);
            for (int column = 0; column < WIDTH; column++) {
                steps.add(new int[]{column, y, 1 + out, eighths});
            }
        }
        return steps.toArray(int[][]::new);
    }

    /** The parts the slide is drawn from, each with its own look. */
    public enum Part {
        /** The lane's top, where riders slide. */
        LANE,
        /** The lane's underside. */
        UNDERSIDE,
        /** The rails along the lane's edges, the head tube and the toe tube. */
        RAIL,
        /** The beams under the rails. */
        BEAM,
        /** The girt bar across the sill. */
        GIRT,
        /** What is still packed: the pack and the roll. */
        PACK
    }

    /**
     * Where the drawn quads go: each a part's, its four corners given as x, y, z, the normal's x, y, z, and u and v
     * across the part's texture (0 to 1), one after the other.
     */
    @FunctionalInterface
    public interface Sink {
        void quad(Part part, float[] corners);
    }

    /** How far through coming out a slide is: the pack's place, how much has unrolled, and how full of gas it is. */
    private record Inflation(float drop, float unrolled, float fill, float bounce) {
        static Inflation at(float progress) {
            float drop = ease(Mth.clamp(progress / DROP, 0, 1));
            float unrolled = ease(Mth.clamp((progress - DROP) / (UNROLLED - DROP), 0, 1));
            float fill = Mth.clamp((progress - DROP) / (FIRM - DROP), 0, 1);
            float bounce = progress > FIRM ? 1 + 0.06F * Mth.sin((float) Math.PI * (progress - FIRM) / (1 - FIRM)) : 1;
            return new Inflation(drop, unrolled, fill, bounce);
        }

        /** How full the slide is this far down it, 0 flat to 1 firm: it fills from its head. */
        float full(float s, float length) {
            return Mth.clamp(fill * 1.3F - 0.3F * s / length, 0, 1) * bounce;
        }
    }

    /** Draws the slide this far through coming out, 0 packed to 1 out and firm. */
    public static void draw(Plan plan, float progress, Sink sink) {
        if (progress <= 0) {
            return;
        }
        Path path = plan.path();
        Inflation inflation = Inflation.at(progress);
        Mesh mesh = new Mesh(path, sink);
        girt(mesh);
        float length = path.length();
        float shown = inflation.unrolled() * length;
        if (shown > 0.01F) {
            float[] stations = mesh.stations(shown);
            lane(mesh, stations, inflation, length);
            for (int side : new int[]{-1, 1}) {
                rail(mesh, stations, inflation, length, side);
            }
            headTube(mesh, inflation, length);
            if (inflation.unrolled() >= 1) {
                toeTube(mesh, inflation, length);
            }
        }
        if (inflation.unrolled() < 1) {
            roll(mesh, path, inflation, shown);
        }
    }

    /** The girt bar across the sill, holding the slide's head to the doorway. */
    private static void girt(Mesh mesh) {
        mesh.box(GIRT_INSET, -GIRT_HEIGHT, 1 + OFF_HULL, WIDTH - GIRT_INSET, -OFF_HULL, 1 + OFF_HULL + GIRT_DEPTH, Part.GIRT);
    }

    /** The lane, its top and its underside, each half across from the middle to the rail, from the head down. */
    private static void lane(Mesh mesh, float[] stations, Inflation inflation, float length) {
        for (int i = 0; i + 1 < stations.length; i++) {
            float from = stations[i];
            float to = stations[i + 1];
            float block = (float) Math.floor(from + 1e-4F);
            Frame a = mesh.frame(from);
            Frame b = mesh.frame(to);
            float reachA = a.halfWidth() - RAIL * mesh.wide(inflation.full(from, length));
            float reachB = b.halfWidth() - RAIL * mesh.wide(inflation.full(to, length));
            for (int side : new int[]{-1, 1}) {
                for (Part part : new Part[]{Part.LANE, Part.UNDERSIDE}) {
                    float up = part == Part.LANE ? 0 : -LANE;
                    float facing = part == Part.LANE ? 1 : -1;
                    float[] quad = new float[32];
                    put(quad, 0, 1, a.y(up), a.z(up), facing * a.normalY(), facing * a.normalZ(), 0, from - block);
                    put(quad, 1, 1 + side * reachA, a.y(up), a.z(up), facing * a.normalY(), facing * a.normalZ(), Math.min(1, reachA), from - block);
                    put(quad, 2, 1 + side * reachB, b.y(up), b.z(up), facing * b.normalY(), facing * b.normalZ(), Math.min(1, reachB), to - block);
                    put(quad, 3, 1, b.y(up), b.z(up), facing * b.normalY(), facing * b.normalZ(), 0, to - block);
                    mesh.sink.quad(part, quad);
                }
            }
        }
    }

    /** One side's rail and the beam under it, from the head down to the end shown; rounded off at the toe. */
    private static void rail(Mesh mesh, float[] stations, Inflation inflation, float length, int side) {
        boolean whole = inflation.unrolled() >= 1;
        for (Part part : new Part[]{Part.RAIL, Part.BEAM}) {
            float radius = part == Part.RAIL ? RAIL : BEAM;
            Ring previous = null;
            for (int i = 0; i < stations.length; i++) {
                float s = stations[i];
                Ring ring = mesh.ring(s, side, part, radius, inflation.full(s, length));
                if (i == 0) {
                    mesh.disc(ring, -1);
                } else {
                    mesh.tube(previous, ring);
                }
                previous = ring;
            }
            if (whole) {
                mesh.dome(previous);
            } else {
                mesh.disc(previous, 1);
            }
        }
    }

    /** The head tube across under the lane just below the sill, holding the rails apart at the head. */
    private static void headTube(Mesh mesh, Inflation inflation, float length) {
        float full = inflation.full(HEAD_TUBE_AT, length);
        Frame frame = mesh.frame(HEAD_TUBE_AT);
        float radius = HEAD_TUBE * mesh.tall(full);
        float reach = frame.halfWidth() - RAIL;
        mesh.across(frame, -LANE - radius, 1 - reach, 1 + reach, radius, Part.RAIL, false);
    }

    /** The toe tube across the curled tip of the toe, between the rails' ends. */
    private static void toeTube(Mesh mesh, Inflation inflation, float length) {
        Frame frame = mesh.frame(length);
        float radius = RAIL * mesh.tall(inflation.full(length, length));
        float reach = frame.halfWidth() - RAIL;
        mesh.across(frame, RAIL_RISE * radius, 1 - reach, 1 + reach, radius, Part.RAIL, false);
    }

    /**
     * What is still packed: the pack lying in the doorway on the sill as it drops out onto the head, then the roll the
     * slide unrolls from, lying on the lane where it has got to, shrinking as it pays out.
     */
    private static void roll(Mesh mesh, Path path, Inflation inflation, float shown) {
        if (inflation.unrolled() <= 0) {
            // From in the doorway, on the sill, out onto the head.
            Point head = path.at(0);
            float u = Mth.lerp(inflation.drop(), PACKED_AT, head.u() + Mth.sin(head.slope()) * PACK);
            float y = Mth.lerp(inflation.drop(), PACK, head.y() + Mth.cos(head.slope()) * PACK);
            mesh.across(new Frame(u, y, 0, HEAD_HALF_WIDTH), 0, 1 - HEAD_HALF_WIDTH + ROLL_INSET, 1 + HEAD_HALF_WIDTH - ROLL_INSET, PACK, Part.PACK, true);
            return;
        }
        float radius = LEAST_ROLL + (PACK - LEAST_ROLL) * (float) Math.sqrt(1 - inflation.unrolled());
        Frame frame = mesh.frame(shown);
        mesh.across(frame, radius, 1 - frame.halfWidth() + ROLL_INSET, 1 + frame.halfWidth() - ROLL_INSET, radius, Part.PACK, true);
    }

    /**
     * Writes a corner into a quad's corners: its position, its normal's y and z (none across the slide, which only the
     * tubes' sides and ends have) and its texture position.
     */
    private static void put(float[] quad, int corner, float x, float y, float z, float ny, float nz, float u, float v) {
        int at = corner * 8;
        quad[at] = x;
        quad[at + 1] = y;
        quad[at + 2] = z;
        quad[at + 3] = 0;
        quad[at + 4] = ny;
        quad[at + 5] = nz;
        quad[at + 6] = u;
        quad[at + 7] = v;
    }

    /**
     * The slide's cross-section this far down it: where the lane's middle is (u out, y up), the way down the lane
     * there and its normal, up off the lane (each as y and z), and how far the slide reaches either side of the middle.
     */
    private record Frame(float u, float y, float slope, float halfWidth) {
        float normalY() {
            return Mth.cos(slope);
        }

        float normalZ() {
            return Mth.sin(slope);
        }

        float alongY() {
            return -Mth.sin(slope);
        }

        float alongZ() {
            return Mth.cos(slope);
        }

        /** The height of a point this far up off the lane. */
        float y(float up) {
            return y + up * normalY();
        }

        /** How far out a point this far up off the lane is, in the door's frame. */
        float z(float up) {
            return 1 + u + up * normalZ();
        }
    }

    /**
     * A ring round a tube: its corners, x, y and z for each side, and their normals; how far down the lane it is, the
     * part it is of and the way down the lane there.
     */
    private record Ring(float[] points, float[] normals, float s, Part part, float alongY, float alongZ) {
    }

    /** Builds the slide's quads into the sink, one cross-section and one tube at a time. */
    private static final class Mesh {
        private static final float[] SIN = new float[SIDES + 1];
        private static final float[] COS = new float[SIDES + 1];

        static {
            for (int i = 0; i <= SIDES; i++) {
                SIN[i] = Mth.sin(2 * (float) Math.PI * i / SIDES);
                COS[i] = Mth.cos(2 * (float) Math.PI * i / SIDES);
            }
        }

        private final Path path;
        private final Sink sink;

        Mesh(Path path, Sink sink) {
            this.path = path;
            this.sink = sink;
        }

        Frame frame(float s) {
            Point point = path.at(Math.min(s, path.length()));
            float widen = ease(Mth.clamp((point.u() - WIDEN_FROM) / WIDEN_OVER, 0, 1));
            return new Frame(point.u(), point.y(), point.slope(), Mth.lerp(widen, HEAD_HALF_WIDTH, HALF_WIDTH));
        }

        /**
         * Where the slide is drawn down to the length shown: rings every block on the straight, closer round the head
         * where it widens, the round-out and the curl, never a ring's texture across a block's edge.
         */
        float[] stations(float shown) {
            List<Float> at = new ArrayList<>();
            float s = 0;
            while (s < shown) {
                at.add(s);
                Point point = path.at(s);
                boolean bending = point.u() < WIDEN_FROM + WIDEN_OVER || path.bend(s) > 0 || path.bend(s + STRAIGHT_STEP) > 0;
                float step = bending ? BEND_STEP : STRAIGHT_STEP;
                s = (float) (Math.floor(s / step + 1e-4) + 1) * step;
            }
            at.add(shown);
            float[] stations = new float[at.size()];
            for (int i = 0; i < stations.length; i++) {
                stations[i] = at.get(i);
            }
            return stations;
        }

        /** How tall a tube stands for how full it is: flat to round. */
        float tall(float full) {
            return 0.12F + 0.88F * full;
        }

        /** How wide a tube lies for how full it is: spread flat, then round. */
        float wide(float full) {
            return 1.25F - 0.25F * full;
        }

        /** The ring round one side's rail or beam this far down the slide, as full as it is there. */
        Ring ring(float s, int side, Part part, float radius, float full) {
            Frame frame = frame(s);
            float tall = radius * tall(full);
            float wide = radius * wide(full);
            float across = 1 + side * (frame.halfWidth() - wide);
            float up = part == Part.RAIL ? RAIL_RISE * tall : -LANE - 0.9F * tall;
            float[] points = new float[(SIDES + 1) * 3];
            float[] normals = new float[(SIDES + 1) * 3];
            for (int i = 0; i <= SIDES; i++) {
                // Round from the top, outward first on either side, so the stripe is on the outside of both rails.
                float out = side * SIN[i];
                float lift = up + tall * COS[i];
                points[i * 3] = across + wide * out;
                points[i * 3 + 1] = frame.y(lift);
                points[i * 3 + 2] = frame.z(lift);
                normals[i * 3] = out;
                normals[i * 3 + 1] = COS[i] * frame.normalY();
                normals[i * 3 + 2] = COS[i] * frame.normalZ();
            }
            return new Ring(points, normals, s, part, frame.alongY(), frame.alongZ());
        }

        /**
         * The tube's skin between two rings, its texture round it from the top and along it a block to a block, from
         * the start of the block the first ring is in.
         */
        void tube(Ring a, Ring b) {
            float block = (float) Math.floor(a.s() + 1e-4F);
            for (int i = 0; i < SIDES; i++) {
                float[] quad = new float[32];
                int[][] corners = {{0, i}, {1, i}, {1, i + 1}, {0, i + 1}};
                for (int k = 0; k < 4; k++) {
                    Ring ring = corners[k][0] == 0 ? a : b;
                    int at = corners[k][1];
                    quad(quad, k, ring, at, (float) at / SIDES, Mth.clamp(ring.s() - block, 0, 1));
                }
                sink.quad(a.part(), quad);
            }
        }

        /** A flat end closing the tube at a ring, facing back up the lane (-1) or on down it (1). */
        void disc(Ring ring, int facing) {
            float cx = 0;
            float cy = 0;
            float cz = 0;
            for (int i = 0; i < SIDES; i++) {
                cx += ring.points()[i * 3] / SIDES;
                cy += ring.points()[i * 3 + 1] / SIDES;
                cz += ring.points()[i * 3 + 2] / SIDES;
            }
            for (int i = 0; i < SIDES; i++) {
                float[] quad = new float[32];
                float ny = facing * ring.alongY();
                float nz = facing * ring.alongZ();
                for (int k = 0; k < 4; k++) {
                    int at = k == 0 ? -1 : k == 1 ? i : i + 1;
                    float x = at < 0 ? cx : ring.points()[at * 3];
                    float y = at < 0 ? cy : ring.points()[at * 3 + 1];
                    float z = at < 0 ? cz : ring.points()[at * 3 + 2];
                    put(quad, k, x, y, z, ny, nz, at < 0 ? 0.5F : (float) at / SIDES, at < 0 ? 0.5F : 0);
                }
                sink.quad(ring.part(), quad);
            }
        }

        /** The rounded end of a tube at a ring, bulging on down the lane: a few rings narrowing to its tip. */
        void dome(Ring ring) {
            float[] centre = new float[3];
            for (int i = 0; i < SIDES; i++) {
                for (int axis = 0; axis < 3; axis++) {
                    centre[axis] += ring.points()[i * 3 + axis] / SIDES;
                }
            }
            float reach = 0;
            for (int i = 0; i < SIDES; i++) {
                float dx = ring.points()[i * 3] - centre[0];
                float dy = ring.points()[i * 3 + 1] - centre[1];
                float dz = ring.points()[i * 3 + 2] - centre[2];
                reach = Math.max(reach, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
            // Its texture runs on from where the tube's left off, back to the start of the block if that is too near its end.
            float start = ring.s() - (float) Math.floor(ring.s() + 1e-4F) > 1 - reach ? (float) Math.floor(ring.s() + 1e-4F) : ring.s();
            Ring previous = new Ring(ring.points(), ring.normals(), start, ring.part(), ring.alongY(), ring.alongZ());
            int bands = 3;
            for (int band = 1; band <= bands; band++) {
                float angle = (float) Math.PI / 2 * band / bands;
                float keep = Mth.cos(angle);
                float sin = Mth.sin(angle);
                float push = reach * sin;
                float[] points = new float[(SIDES + 1) * 3];
                float[] normals = new float[(SIDES + 1) * 3];
                for (int i = 0; i <= SIDES; i++) {
                    points[i * 3] = centre[0] + keep * (ring.points()[i * 3] - centre[0]);
                    points[i * 3 + 1] = centre[1] + keep * (ring.points()[i * 3 + 1] - centre[1]) + push * ring.alongY();
                    points[i * 3 + 2] = centre[2] + keep * (ring.points()[i * 3 + 2] - centre[2]) + push * ring.alongZ();
                    normals[i * 3] = keep * ring.normals()[i * 3];
                    normals[i * 3 + 1] = keep * ring.normals()[i * 3 + 1] + sin * ring.alongY();
                    normals[i * 3 + 2] = keep * ring.normals()[i * 3 + 2] + sin * ring.alongZ();
                }
                Ring next = new Ring(points, normals, start + push, ring.part(), ring.alongY(), ring.alongZ());
                tube(previous, next);
                previous = next;
            }
        }

        /**
         * A tube across the slide from one x to another, lying on the lane's cross-section at a frame this far up off
         * the lane, closed at its ends if asked: the head tube, the toe tube, the roll.
         */
        void across(Frame frame, float up, float fromX, float toX, float radius, Part part, boolean closed) {
            float[][] rings = new float[2][(SIDES + 1) * 3];
            float[] normals = new float[(SIDES + 1) * 3];
            for (int i = 0; i <= SIDES; i++) {
                // Round the x axis, from the top of the tube toward the toe.
                float off = radius * COS[i];
                float ahead = radius * SIN[i];
                float y = frame.y(up + off) + ahead * frame.alongY();
                float z = frame.z(up + off) + ahead * frame.alongZ();
                for (int end = 0; end < 2; end++) {
                    rings[end][i * 3] = end == 0 ? fromX : toX;
                    rings[end][i * 3 + 1] = y;
                    rings[end][i * 3 + 2] = z;
                }
                normals[i * 3 + 1] = COS[i] * frame.normalY() + SIN[i] * frame.alongY();
                normals[i * 3 + 2] = COS[i] * frame.normalZ() + SIN[i] * frame.alongZ();
            }
            // Its texture round it, as the rails' is, and along it up to a block.
            float length = Math.min(1, toX - fromX);
            for (int i = 0; i < SIDES; i++) {
                float[] quad = new float[32];
                for (int k = 0; k < 4; k++) {
                    int end = k == 1 || k == 2 ? 1 : 0;
                    int at = k < 2 ? i : i + 1;
                    put(quad, k, rings[end][at * 3], rings[end][at * 3 + 1], rings[end][at * 3 + 2], normals[at * 3 + 1], normals[at * 3 + 2],
                        (float) at / SIDES, end * length);
                }
                sink.quad(part, quad);
            }
            if (!closed) {
                return;
            }
            for (int end = 0; end < 2; end++) {
                float[] ring = rings[end];
                float cy = 0;
                float cz = 0;
                for (int i = 0; i < SIDES; i++) {
                    cy += ring[i * 3 + 1] / SIDES;
                    cz += ring[i * 3 + 2] / SIDES;
                }
                float nx = end == 0 ? -1 : 1;
                for (int i = 0; i < SIDES; i++) {
                    float[] quad = new float[32];
                    put(quad, 0, ring[0], cy, cz, 0, 0, 0.5F, 0.5F);
                    put(quad, 1, ring[i * 3], ring[i * 3 + 1], ring[i * 3 + 2], 0, 0, 0.5F + 0.5F * SIN[i], 0.5F - 0.5F * COS[i]);
                    put(quad, 2, ring[(i + 1) * 3], ring[(i + 1) * 3 + 1], ring[(i + 1) * 3 + 2], 0, 0, 0.5F + 0.5F * SIN[i + 1], 0.5F - 0.5F * COS[i + 1]);
                    put(quad, 3, ring[(i + 1) * 3], ring[(i + 1) * 3 + 1], ring[(i + 1) * 3 + 2], 0, 0, 0.5F + 0.5F * SIN[i + 1], 0.5F - 0.5F * COS[i + 1]);
                    for (int k = 0; k < 4; k++) {
                        quad[k * 8 + 3] = nx;
                    }
                    sink.quad(part, quad);
                }
            }
        }

        /** A box from one corner to the other, every face showing as much of the texture as it is big. */
        void box(float x0, float y0, float z0, float x1, float y1, float z1, Part part) {
            float dx = Math.min(1, x1 - x0);
            float dy = Math.min(1, y1 - y0);
            float dz = Math.min(1, z1 - z0);
            face(part, 0, 0, 1, dx, dy, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
            face(part, 0, 0, -1, dx, dy, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
            face(part, 0, 1, 0, dx, dz, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
            face(part, 0, -1, 0, dx, dz, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
            face(part, 1, 0, 0, dz, dy, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
            face(part, -1, 0, 0, dz, dy, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        }

        private void face(Part part, float nx, float ny, float nz, float du, float dv, float... corners) {
            float[] quad = new float[32];
            float[][] uv = {{0, dv}, {du, dv}, {du, 0}, {0, 0}};
            for (int k = 0; k < 4; k++) {
                put(quad, k, corners[k * 3], corners[k * 3 + 1], corners[k * 3 + 2], ny, nz, uv[k][0], uv[k][1]);
                quad[k * 8 + 3] = nx;
            }
            sink.quad(part, quad);
        }

        private static void quad(float[] quad, int corner, Ring ring, int at, float u, float v) {
            put(quad, corner, ring.points()[at * 3], ring.points()[at * 3 + 1], ring.points()[at * 3 + 2],
                ring.normals()[at * 3 + 1], ring.normals()[at * 3 + 2], u, v);
            quad[corner * 8 + 3] = ring.normals()[at * 3];
        }
    }

    /** Eased in and out, so each move starts and stops gently. */
    private static float ease(float t) {
        return t * t * (3 - 2 * t);
    }
}
