package dev.aerolarge.airframe;

import static dev.aerolarge.airframe.AirframeShape.*;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The airliner's nose as it is drawn: a smooth skin over the stepped nose blocks, from the radome back to where the
 * cabin starts, with the flight deck's windscreen set into it. The glass wraps around the sloping crown between two
 * heights, from the centreline back along both sides, and a dark frame divides it into panes the way the A380's is.
 * The layout makes the hull blocks under the glass invisible; a block entity renderer draws the whole skin from one
 * anchor block, every corner worked out in metres from {@link AirframeShape} and turned into blocks relative to it.
 */
public final class Windscreen {
    /** The heights the glass spans, as shares of the fuselage height: around the pilots' eyes on the flight deck. */
    static final double LOW = 0.56 * HEIGHT;
    static final double HIGH = 0.70 * HEIGHT;
    /** How far back from the nose tip the glass reaches along the sides. */
    static final double REAR = 0.62 * NOSE_LENGTH;
    /** The flight deck floor's top lies this many blocks below the middle of the glass: a player's eye height. */
    static final double EYE_HEIGHT = 1.62;
    /**
     * How far the skin stands out from the true surface, in blocks: far enough to cover the corners of the stepped
     * blocks under it. Over the last stretch before the cabin it sinks back to the surface, into the hull blocks.
     */
    private static final double OUTSET = 0.85;
    private static final double BLEND = 0.9;
    /**
     * The frame, in metres: its band around the glass (the crest column, where the two sides meet, is framed too) and
     * the two posts along each side, a share of the way back along the glass.
     */
    private static final double FRAME_BAND = 0.25;
    private static final double SIDE_POST = 0.14;
    private static final double[] SIDE_POSTS = {0.38, 0.7};
    private static final double SECTION_EXPONENT = 2.4;

    private Windscreen() {
    }

    /** One quad: four corners, each x, y, z in blocks relative to the anchor, wound to face out, then its normal. */
    public record Quad(float[] corners, float[] normal) {
    }

    /** The nose drawn in three materials: painted skin, the dark frame and the tinted glass. */
    public record Mesh(List<Quad> skin, List<Quad> frame, List<Quad> glass, float reach) {
    }

    private enum Part {
        SKIN, FRAME, GLASS
    }

    /** Whether a hull block centred at this height and station lies under the glass. */
    static boolean covers(double y, double z) {
        return y >= LOW && y <= HIGH && z <= REAR && z >= front(HIGH) - 0.5;
    }

    /** The flight deck floor's block row at this scale, so the pilots look straight out through the glass. */
    static int deckRow(double scale) {
        return (int) Math.round((LOW + HIGH) / 2 * scale - EYE_HEIGHT) - 1;
    }

    /** The mesh for an airliner of this scale, relative to the anchor block at layout position (ax, ay, az). */
    public static Mesh mesh(double scale, int ax, int ay, int az) {
        double end = NOSE_LENGTH + BLEND;
        // About two quads a block each way, so the skin's curve stays smooth; the edges of the glass and its frame are
        // grid lines of their own, so they come out straight and clean.
        double step = 0.5 / scale;
        List<Double> stations = lines(0.001, end, step, REAR, REAR + FRAME_BAND);
        List<Double> heights = lines(0.3 * HEIGHT, HEIGHT, step, LOW - FRAME_BAND, LOW, HIGH, HIGH + FRAME_BAND);

        List<Quad> skin = new ArrayList<>();
        List<Quad> frame = new ArrayList<>();
        List<Quad> glass = new ArrayList<>();
        float[] reach = {0};
        Converter blocks = (p, outward, extra) -> {
            double out = outset(p[2]) + extra;
            float[] b = {
                (float) (p[0] * scale + 0.5 + outward[0] * out - ax),
                (float) (p[1] * scale + outward[1] * out - ay),
                (float) (p[2] * scale + outward[2] * out - az)
            };
            reach[0] = Math.max(reach[0], Math.max(Math.abs(b[0]), Math.max(Math.abs(b[1]), Math.abs(b[2]))));
            return b;
        };
        double[] tip = {0, NOSE_TIP * HEIGHT, 0};

        // The upper half, side by side, laid out by station and height: each point at its height on the section,
        // heights past the crest gathered onto the crest line and heights below the middle onto the middle.
        for (int side : new int[]{1, -1}) {
            double[][][] grid = new double[stations.size()][heights.size()][];
            for (int i = 0; i < stations.size(); i++) {
                for (int j = 0; j < heights.size(); j++) {
                    grid[i][j] = upper(stations.get(i), heights.get(j), side);
                }
            }
            for (int i = 0; i + 1 < stations.size(); i++) {
                for (int j = 0; j + 1 < heights.size(); j++) {
                    double[][] corners = {grid[i][j], grid[i + 1][j], grid[i + 1][j + 1], grid[i][j + 1]};
                    if (degenerate(corners)) {
                        continue;
                    }
                    double z = (stations.get(i) + stations.get(i + 1)) / 2;
                    double y = (heights.get(j) + heights.get(j + 1)) / 2;
                    // The column under the crest is where the two sides meet: the centre post, where it is glass.
                    boolean crest = heights.get(j + 1) >= crestHeight(stations.get(i))
                        || heights.get(j + 1) >= crestHeight(stations.get(i + 1));
                    Quad quad = quad(corners, blocks, 0);
                    switch (part(y, z, crest)) {
                        case GLASS -> glass.add(quad);
                        case FRAME -> frame.add(quad);
                        default -> skin.add(quad);
                    }
                }
            }
            for (int j = 0; j + 1 < heights.size(); j++) {
                if (!degenerate(new double[][]{tip, grid[0][j], grid[0][j + 1], tip})) {
                    skin.add(quad(new double[][]{tip, tip, grid[0][j + 1], grid[0][j]}, blocks, 0));
                }
            }
            // The posts between the side panes, laid over the glass along their own curves.
            for (double post : SIDE_POSTS) {
                for (int j = 0; j + 1 < heights.size(); j++) {
                    double y0 = heights.get(j);
                    double y1 = heights.get(j + 1);
                    if (y0 < LOW || y1 > HIGH) {
                        continue;
                    }
                    double z0 = postStation(y0, post);
                    double z1 = postStation(y1, post);
                    frame.add(quad(new double[][]{
                        surface(z0 - SIDE_POST, y0, side), surface(z0 + SIDE_POST, y0, side),
                        surface(z1 + SIDE_POST, y1, side), surface(z1 - SIDE_POST, y1, side)
                    }, blocks, 0.02));
                }
            }
        }

        // The lower half, round the chin, laid out by station and angle; it has no glass.
        int around = Math.max(24, (int) Math.round(20 * scale));
        double[][][] lower = new double[stations.size()][around + 1][];
        for (int i = 0; i < stations.size(); i++) {
            for (int k = 0; k <= around; k++) {
                lower[i][k] = point(stations.get(i), Math.PI + Math.PI * k / around);
            }
        }
        for (int i = 0; i + 1 < stations.size(); i++) {
            for (int k = 0; k < around; k++) {
                skin.add(quad(new double[][]{lower[i][k], lower[i][k + 1], lower[i + 1][k + 1], lower[i + 1][k]}, blocks, 0));
            }
        }
        for (int k = 0; k < around; k++) {
            skin.add(quad(new double[][]{tip, tip, lower[0][k + 1], lower[0][k]}, blocks, 0));
        }
        return new Mesh(List.copyOf(skin), List.copyOf(frame), List.copyOf(glass), reach[0] + 1);
    }

    @FunctionalInterface
    private interface Converter {
        float[] apply(double[] metres, double[] outward, double extraOutset);
    }

    /** Evenly spaced values from start to end, plus the given ones, in order. */
    private static List<Double> lines(double start, double end, double step, double... extra) {
        TreeSet<Double> values = new TreeSet<>();
        int count = (int) Math.ceil((end - start) / step);
        for (int i = 0; i <= count; i++) {
            values.add(start + (end - start) * i / count);
        }
        for (double value : extra) {
            if (value > start && value < end) {
                values.add(value);
            }
        }
        return new ArrayList<>(values);
    }

    /** The crest's height at a station: the top of the section. */
    private static double crestHeight(double z) {
        Section section = section(z);
        return section == null ? 0 : section.top();
    }

    /** A point of the upper half at a station and height, the height held between the middle and the crest. */
    private static double[] upper(double z, double y, int side) {
        Section section = section(z);
        double centre = (section.bottom() + section.top()) / 2;
        return surface(z, Math.clamp(y, centre, section.top()), side);
    }

    /** The point of the surface at this station and height on one side. */
    private static double[] surface(double z, double y, int side) {
        return new double[]{side * halfWidthAt(y, z), y, z};
    }

    private static double halfWidthAt(double y, double z) {
        Section section = section(z);
        if (section == null) {
            return 0;
        }
        double centre = (section.bottom() + section.top()) / 2;
        double radius = (section.top() - section.bottom()) / 2;
        double up = Math.min(1, Math.abs(y - centre) / radius);
        return section.halfWidth() * Math.pow(1 - Math.pow(up, SECTION_EXPONENT), 1 / SECTION_EXPONENT);
    }

    /** Where a side post crosses height y: a share of the way back along the glass, squared like the panes. */
    private static double postStation(double y, double post) {
        double front = front(y);
        return front + (REAR - front) * post * post;
    }

    /** Whether a quad has collapsed to (nearly) nothing, where its corners were gathered onto the crest. */
    private static boolean degenerate(double[][] corners) {
        double diagonal1 = distance(corners[0], corners[2]);
        double diagonal2 = distance(corners[1], corners[3]);
        return diagonal1 < 1e-4 && diagonal2 < 1e-4;
    }

    private static double distance(double[] a, double[] b) {
        return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]);
    }

    /** A quad with its corners pushed out along the surface normal, wound so its face points outward. */
    private static Quad quad(double[][] corners, Converter blocks, double extraOutset) {
        double[] centre = average(corners);
        double[] outward = normal(centre);
        float[] flat = new float[12];
        for (int k = 0; k < 4; k++) {
            float[] b = blocks.apply(corners[k], normal(corners[k]), extraOutset);
            System.arraycopy(b, 0, flat, k * 3, 3);
        }
        // The winding's own normal, from the diagonals; flip the order if it faces in.
        double[] d1 = {flat[6] - flat[0], flat[7] - flat[1], flat[8] - flat[2]};
        double[] d2 = {flat[9] - flat[3], flat[10] - flat[4], flat[11] - flat[5]};
        double[] cross = {d1[1] * d2[2] - d1[2] * d2[1], d1[2] * d2[0] - d1[0] * d2[2], d1[0] * d2[1] - d1[1] * d2[0]};
        if (cross[0] * outward[0] + cross[1] * outward[1] + cross[2] * outward[2] < 0) {
            float[] reversed = new float[12];
            for (int k = 0; k < 4; k++) {
                System.arraycopy(flat, (3 - k) * 3, reversed, k * 3, 3);
            }
            flat = reversed;
        }
        return new Quad(flat, new float[]{(float) outward[0], (float) outward[1], (float) outward[2]});
    }

    /** What a cell of the upper half is, from its middle: glass, frame or skin. */
    private static Part part(double y, double z, boolean crest) {
        boolean framed = y > LOW - FRAME_BAND && y < HIGH + FRAME_BAND && z < REAR + FRAME_BAND;
        if (!framed) {
            return Part.SKIN;
        }
        boolean glazed = y > LOW && y < HIGH && z < REAR;
        return glazed && !crest ? Part.GLASS : Part.FRAME;
    }

    /** How far out the skin stands at a station: fully over the nose, sinking into the hull blocks behind it. */
    private static double outset(double z) {
        if (z <= NOSE_LENGTH) {
            return OUTSET;
        }
        return OUTSET * Math.max(0, 1 - (z - NOSE_LENGTH) / BLEND);
    }

    /** Where the crown reaches height y: the front edge of the glass at that height, on the centreline. */
    static double front(double y) {
        double tip = NOSE_TIP * HEIGHT;
        double risen = Math.min(1, Math.max(0, (y - tip) / (HEIGHT - tip)));
        return NOSE_LENGTH * (1 - Math.sqrt(1 - risen));
    }

    /** The point of the section at station z, at an angle round it (0 to starboard, a quarter turn to the crown). */
    private static double[] point(double z, double angle) {
        Section section = section(z);
        double centre = (section.bottom() + section.top()) / 2;
        double radius = (section.top() - section.bottom()) / 2;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double x = section.halfWidth() * Math.signum(cos) * Math.pow(Math.abs(cos), 2 / SECTION_EXPONENT);
        double y = centre + radius * Math.signum(sin) * Math.pow(Math.abs(sin), 2 / SECTION_EXPONENT);
        return new double[]{x, y, z};
    }

    private static double[] average(double[][] points) {
        double[] sum = new double[3];
        for (double[] p : points) {
            for (int k = 0; k < 3; k++) {
                sum[k] += p[k] / points.length;
            }
        }
        return sum;
    }

    /** The outward normal of the fuselage surface near a point, from the gradient of its section's superellipse. */
    private static double[] normal(double[] p) {
        double h = 0.01;
        double nx = field(p[0] + h, p[1], p[2]) - field(p[0] - h, p[1], p[2]);
        double ny = field(p[0], p[1] + h, p[2]) - field(p[0], p[1] - h, p[2]);
        double nz = field(p[0], p[1], p[2] + h) - field(p[0], p[1], p[2] - h);
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return length < 1e-9 ? new double[]{0, 0, -1} : new double[]{nx / length, ny / length, nz / length};
    }

    /** Below 1 inside the fuselage, above 1 outside it, growing outward. */
    private static double field(double x, double y, double z) {
        Section section = section(Math.max(0.001, z));
        if (section == null) {
            return 2;
        }
        double centre = (section.bottom() + section.top()) / 2;
        double radius = (section.top() - section.bottom()) / 2;
        return Math.pow(Math.abs(x) / section.halfWidth(), SECTION_EXPONENT) + Math.pow(Math.abs(y - centre) / radius, SECTION_EXPONENT);
    }
}
