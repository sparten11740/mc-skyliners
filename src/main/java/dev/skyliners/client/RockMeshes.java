package dev.skyliners.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.skyliners.Skyliners;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The rocks of space weather as the client draws them (see {@link SpaceWeatherSky}): lumpy, uneven stones, each made
 * once from a seed of its own as a ball pushed in and out, stretched long or squat, knobbly with lumps, pitted with
 * craters with raised rims, a ridge running round some, grainy all over, and patched lighter and darker, crater floors
 * darker and rims lighter; now and then one of dark rock cracked with glowing magma. There are {@link #SHAPES} shapes,
 * each cut finer for a rock seen up close and coarser far off; every rock picks one by its seed and is stretched,
 * tinted and set tumbling about two axes at once its own way, so no two near each other look alike.
 *
 * <p>Each is lit by the sun where the game puts it, its far side dark, with a little blue light thrown up from the
 * planet below, and drawn see-through as it fades in from far off, its triangles' corners worked out on the client each
 * frame: a few hundred for a rock far off, more only for the few up close.
 */
final class RockMeshes {
    /** How many shapes of rock there are, each made once from a seed of its own. */
    private static final int SHAPES = 8;
    /** How often each shape's ball is cut finer at its levels of detail: 80, 320 and 1,280 triangles. */
    private static final int[] DETAIL = {1, 2, 3};
    /**
     * How big a rock must look, as its half width over how far off it is, to be drawn at the next finer level of detail:
     * about a degree and a half across, and about five.
     */
    private static final double[] FINER_FROM = {0.012, 0.045};
    /** The seed each shape is made from is this, plus its number. */
    private static final long SHAPE_SEED = 0x5A57E0C3L;
    /**
     * A shape's stretch: how long it may be drawn along its first axis at most, and how squat along its second at the
     * least, as shares of its width; and how much more or less each rock is stretched again its own way, at most.
     */
    private static final float LONGEST = 1.7F;
    private static final float SQUATTEST = 0.6F;
    private static final float ROCK_STRETCH = 0.2F;
    /**
     * A shape's lumps: how many, at least and how many more at most; how wide each is, in radians round the ball, at
     * least and at most; and how far each pushes the ball out or in, at most, as a share of its radius.
     */
    private static final int LUMPS = 6;
    private static final int MORE_LUMPS = 6;
    private static final double LUMP_NARROWEST = 0.35;
    private static final double LUMP_WIDEST = 1.1;
    private static final double LUMP_HEIGHT = 0.22;
    /**
     * Its craters: how many, at least and how many more at most; how wide, in radians round the ball, at least and at
     * most; how deep, as a share of the radius, for each radian across; how high their rims stand, as a share of their
     * depth, and how wide the rims are, as a share of the crater's width.
     */
    private static final int CRATERS = 4;
    private static final int MORE_CRATERS = 6;
    private static final double CRATER_NARROWEST = 0.18;
    private static final double CRATER_WIDEST = 0.6;
    private static final double CRATER_DEPTH = 0.45;
    private static final double RIM_HEIGHT = 0.45;
    private static final double RIM_WIDTH = 0.3;
    /** How likely a shape is to have a ridge round it, how high it stands and how wide it is, as shares of the radius. */
    private static final float RIDGE_CHANCE = 0.5F;
    private static final double RIDGE_HEIGHT = 0.12;
    private static final double RIDGE_WIDTH = 0.12;
    /**
     * Its grain: how many waves of it run over the ball, how many times round the ball each goes, at least and at most,
     * and how far each pushes it out or in, as a share of the radius.
     */
    private static final int GRAIN_WAVES = 7;
    private static final double GRAIN_SLOWEST = 4;
    private static final double GRAIN_FASTEST = 14;
    private static final double GRAIN_HEIGHT = 0.05;
    /**
     * Its colouring: how much lighter or darker its patches are, as a share; how much darker a crater's floor and how
     * much lighter its rim and a ridge.
     */
    private static final double PATCHES = 0.35;
    private static final double FLOOR_DARKER = 0.5;
    private static final double RIM_LIGHTER = 0.3;
    /** The stones a rock may be of: grey, brown, dark basalt and rusty red, one picked by its seed. */
    private static final int[] STONES = {0x8C8781, 0x7D6E60, 0x5B5754, 0x8A6A58, 0x9A948C};
    /**
     * A magma rock: how often one is, as a share; the dark rock it is of; the glow of its magma; and how thin its
     * glowing cracks run, as a share of the grain's height.
     */
    private static final float MAGMA_CHANCE = 0.22F;
    private static final int MAGMA_ROCK = 0x3E3430;
    private static final int MAGMA_GLOW = 0xFF6A1A;
    private static final double CRACKS = 0.06;
    /** How much of each rock's colour it may be tinted lighter or darker, at most, as a share, so no two look alike. */
    private static final float TINT = 0.12F;
    /**
     * How it is lit: the light on its side away from the sun, as a share; how much more the sun gives its face turned
     * square to it; and the blue light thrown up from the planet onto its underside.
     */
    private static final float SHADE = 0.22F;
    private static final float SUNLIT = 0.95F;
    private static final float PLANET_LIGHT = 0.3F;
    private static final float[] PLANET_TINT = {0.45F, 0.65F, 1.0F};
    /** How fast a rock tumbles about each of its two axes, at most, in degrees a second. */
    private static final double MOST_TUMBLE = 70;
    /**
     * How rocks are drawn: in colour alone, lit by the client, see-through as they fade in, their back faces left out,
     * hiding what is behind them.
     */
    static final RenderType ROCK = RenderType.create(Skyliners.MOD_ID + "_space_rock", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES,
        RenderType.TRANSIENT_BUFFER_SIZE, false, false, RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setCullState(RenderStateShard.CULL)
            .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
            .createCompositeState(false));

    /** Each shape at each level of detail, made the first time it is drawn. */
    private static final Map<Integer, Mesh> MESHES = new HashMap<>();

    private RockMeshes() {
    }

    /**
     * A shape cut so fine: each triangle's three corners, as x, y and z on a ball of radius 1 pushed in and out; its
     * face's way out, as x, y and z; and each corner's shade, as a share of its stone's colour, and glow, from 0 to 1.
     */
    private record Mesh(float[] corners, float[] faces, float[] shades, float[] glows) {
        int triangles() {
            return faces.length / 3;
        }
    }

    /**
     * How one rock looks: its shape, whether it is of magma, its stone's colour, as red, green and blue from 0 to 1,
     * how it is stretched along each of its axes, and the two axes it tumbles about, and how fast, in degrees a second.
     */
    record Look(int shape, boolean magma, float[] colour, Vector3f stretch, Vector3f firstAxis, double firstTumble, Vector3f secondAxis,
                double secondTumble) {
        /** How it is turned so many seconds into its tumble. */
        Quaternionf turned(double seconds) {
            float first = (float) (Math.toRadians(firstTumble * seconds) % Mth.TWO_PI);
            float second = (float) (Math.toRadians(secondTumble * seconds) % Mth.TWO_PI);
            return new Quaternionf().rotationAxis(first, firstAxis).rotateAxis(second, secondAxis);
        }
    }

    /** How a rock looks, picked by its seed. */
    static Look look(long seed) {
        RandomSource random = RandomSource.create(seed);
        int shape = random.nextInt(SHAPES);
        boolean magma = random.nextFloat() < MAGMA_CHANCE;
        int stone = magma ? MAGMA_ROCK : STONES[random.nextInt(STONES.length)];
        float tint = 1 + (random.nextFloat() * 2 - 1) * TINT;
        float[] colour = {(stone >> 16 & 0xFF) / 255F * tint, (stone >> 8 & 0xFF) / 255F * tint, (stone & 0xFF) / 255F * tint};
        Vector3f stretch = new Vector3f(1 + spread(random) * ROCK_STRETCH, 1 + spread(random) * ROCK_STRETCH, 1 + spread(random) * ROCK_STRETCH);
        return new Look(shape, magma, colour, stretch, axis(random), spread(random) * MOST_TUMBLE, axis(random), spread(random) * MOST_TUMBLE * 0.5);
    }

    /**
     * A rock drawn with its middle so far from the camera, so big across, as it looks, turned so far, as bright as
     * given from 0 to 1 as it fades in, lit from the sun's way and from below by the planet: cut as finely as how big it
     * looks calls for.
     */
    static void draw(VertexConsumer buffer, PoseStack pose, double x, double y, double z, double size, Look look, Quaternionf turned, float fade,
        Vector3f sun) {
        if (fade <= 0) {
            return;
        }
        double away = Math.sqrt(x * x + y * y + z * z);
        double looks = size / 2 / Math.max(away, 1);
        int detail = looks > FINER_FROM[1] ? 2 : looks > FINER_FROM[0] ? 1 : 0;
        Mesh mesh = mesh(look.shape(), detail);
        float radius = (float) (size / 2);
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(turned);
        pose.scale(radius * look.stretch().x, radius * look.stretch().y, radius * look.stretch().z);
        Matrix4f matrix = pose.last().pose();
        int alpha = Math.round(Mth.clamp(fade, 0, 1) * 255);
        Vector3f face = new Vector3f();
        for (int t = 0; t < mesh.triangles(); t++) {
            face.set(mesh.faces[t * 3] / look.stretch().x, mesh.faces[t * 3 + 1] / look.stretch().y, mesh.faces[t * 3 + 2] / look.stretch().z);
            turned.transform(face.normalize());
            float sunlight = SHADE + SUNLIT * Math.max(0, face.dot(sun));
            float planet = PLANET_LIGHT * Math.max(0, -face.y);
            corner(buffer, matrix, mesh, look, t * 3, sunlight, planet, alpha);
            corner(buffer, matrix, mesh, look, t * 3 + 1, sunlight, planet, alpha);
            corner(buffer, matrix, mesh, look, t * 3 + 2, sunlight, planet, alpha);
        }
        pose.popPose();
    }

    /** One corner of a triangle, lit so by the sun and the planet, its glow added, as opaque as given (0 to 255). */
    private static void corner(VertexConsumer buffer, Matrix4f matrix, Mesh mesh, Look look, int corner, float sunlight, float planet, int alpha) {
        float shade = mesh.shades[corner];
        float glow = look.magma() ? mesh.glows[corner] : 0;
        float[] colour = look.colour();
        float red = colour[0] * shade * (sunlight + planet * PLANET_TINT[0]) + glow * (MAGMA_GLOW >> 16 & 0xFF) / 255F;
        float green = colour[1] * shade * (sunlight + planet * PLANET_TINT[1]) + glow * (MAGMA_GLOW >> 8 & 0xFF) / 255F;
        float blue = colour[2] * shade * (sunlight + planet * PLANET_TINT[2]) + glow * (MAGMA_GLOW & 0xFF) / 255F;
        buffer.addVertex(matrix, mesh.corners[corner * 3], mesh.corners[corner * 3 + 1], mesh.corners[corner * 3 + 2])
            .setColor(Math.min(1, red), Math.min(1, green), Math.min(1, blue), alpha / 255F);
    }

    /** A shape cut at a level of detail, made the first time it is asked for. */
    private static Mesh mesh(int shape, int detail) {
        return MESHES.computeIfAbsent(shape * DETAIL.length + detail, key -> make(new Shape(RandomSource.create(SHAPE_SEED + shape)), DETAIL[detail]));
    }

    /** A number from -1 to 1 at random. */
    private static float spread(RandomSource random) {
        return random.nextFloat() * 2 - 1;
    }

    /** A way at random, of length 1. */
    private static Vector3f axis(RandomSource random) {
        Vector3f way = new Vector3f(spread(random), spread(random), spread(random));
        return way.lengthSquared() < 1e-4F ? new Vector3f(0, 1, 0) : way.normalize();
    }

    /**
     * A shape's makings, drawn from its seed: its stretch, its lumps, craters and ridge, its grain and its patches; how
     * far out its ball is pushed every way, and how it is coloured there.
     */
    private static final class Shape {
        final Vector3f stretch;
        final List<float[]> lumps = new ArrayList<>();
        final List<float[]> craters = new ArrayList<>();
        final List<float[]> grain = new ArrayList<>();
        final List<float[]> patches = new ArrayList<>();
        final Vector3f ridge;
        final boolean ridged;

        Shape(RandomSource random) {
            stretch = new Vector3f(Mth.lerp(random.nextFloat(), 1, LONGEST), Mth.lerp(random.nextFloat(), SQUATTEST, 1), Mth.lerp(random.nextFloat(), 0.8F, 1.15F));
            int lumpCount = LUMPS + random.nextInt(MORE_LUMPS + 1);
            for (int i = 0; i < lumpCount; i++) {
                Vector3f at = axis(random);
                lumps.add(new float[] {at.x, at.y, at.z, (float) Mth.lerp(random.nextDouble(), LUMP_NARROWEST, LUMP_WIDEST), (float) (spread(random) * LUMP_HEIGHT)});
            }
            int craterCount = CRATERS + random.nextInt(MORE_CRATERS + 1);
            for (int i = 0; i < craterCount; i++) {
                Vector3f at = axis(random);
                craters.add(new float[] {at.x, at.y, at.z, (float) Mth.lerp(random.nextDouble() * random.nextDouble(), CRATER_NARROWEST, CRATER_WIDEST)});
            }
            for (int i = 0; i < GRAIN_WAVES; i++) {
                Vector3f way = axis(random);
                grain.add(new float[] {way.x, way.y, way.z, (float) Mth.lerp(random.nextDouble(), GRAIN_SLOWEST, GRAIN_FASTEST), random.nextFloat() * Mth.TWO_PI});
                Vector3f patch = axis(random);
                patches.add(new float[] {patch.x, patch.y, patch.z, (float) Mth.lerp(random.nextDouble(), 1.5, 3.5), random.nextFloat() * Mth.TWO_PI});
            }
            ridged = random.nextFloat() < RIDGE_CHANCE;
            ridge = axis(random);
        }

        /** How far out the ball is pushed the way given, of length 1, as a share of its radius, before its stretch. */
        float radius(Vector3f way) {
            double out = 1;
            for (float[] lump : lumps) {
                double across = Math.acos(Mth.clamp(way.x * lump[0] + way.y * lump[1] + way.z * lump[2], -1, 1)) / lump[3];
                out += across < 1 ? lump[4] * smooth(1 - across) : 0;
            }
            for (float[] crater : craters) {
                out += crater(way, crater);
            }
            if (ridged) {
                double off = way.dot(ridge) / RIDGE_WIDTH;
                out += RIDGE_HEIGHT * Math.exp(-off * off);
            }
            return (float) (out + GRAIN_HEIGHT * grain(way));
        }

        /** How far a crater pushes the ball the way given: in over its floor, out round its rim. */
        private static double crater(Vector3f way, float[] crater) {
            double across = Math.acos(Mth.clamp(way.x * crater[0] + way.y * crater[1] + way.z * crater[2], -1, 1)) / crater[3];
            double depth = CRATER_DEPTH * crater[3];
            double floor = across < 1 ? -depth * (1 - across * across) : 0;
            double fromRim = (across - 1) / RIM_WIDTH;
            return floor + depth * RIM_HEIGHT * Math.exp(-fromRim * fromRim);
        }

        /** Its grain the way given, from -1 to 1 or so: waves running over the ball, crossing one another. */
        double grain(Vector3f way) {
            double sum = 0;
            for (float[] wave : grain) {
                sum += Math.sin(wave[3] * (way.x * wave[0] + way.y * wave[1] + way.z * wave[2]) + wave[4]);
            }
            return sum / Math.sqrt(grain.size());
        }

        /**
         * Its shade the way given, as a share of its stone's colour: patched lighter and darker, a crater's floor
         * darker and its rim lighter, a ridge lighter.
         */
        float shade(Vector3f way) {
            double patch = 0;
            for (float[] wave : patches) {
                patch += Math.sin(wave[3] * (way.x * wave[0] + way.y * wave[1] + way.z * wave[2]) + wave[4]);
            }
            double shade = 1 + PATCHES * patch / Math.sqrt(patches.size());
            for (float[] crater : craters) {
                double pushed = crater(way, crater) / (CRATER_DEPTH * crater[3]);
                shade += pushed < 0 ? pushed * FLOOR_DARKER : pushed * RIM_LIGHTER;
            }
            if (ridged) {
                double off = way.dot(ridge) / RIDGE_WIDTH;
                shade += RIM_LIGHTER * Math.exp(-off * off);
            }
            return (float) Mth.clamp(shade, 0.3, 1.5);
        }

        /** How brightly magma glows the way given, from 0 to 1: along thin cracks, and in crater floors. */
        float glow(Vector3f way) {
            double crack = Math.max(0, 1 - Math.abs(grain(way)) / CRACKS);
            double floor = 0;
            for (float[] crater : craters) {
                floor = Math.max(floor, -crater(way, crater) / (CRATER_DEPTH * crater[3]));
            }
            return (float) Mth.clamp(Math.max(crack, floor * floor * floor), 0, 1);
        }
    }

    private static double smooth(double along) {
        return along * along * (3 - 2 * along);
    }

    /** A shape made from its makings: a ball cut so often, each corner pushed out and coloured, each face lit flat. */
    private static Mesh make(Shape shape, int cuts) {
        List<Vector3f[]> triangles = ball(cuts);
        float[] corners = new float[triangles.size() * 9];
        float[] faces = new float[triangles.size() * 3];
        float[] shades = new float[triangles.size() * 3];
        float[] glows = new float[triangles.size() * 3];
        Vector3f[] pushed = {new Vector3f(), new Vector3f(), new Vector3f()};
        for (int t = 0; t < triangles.size(); t++) {
            Vector3f[] triangle = triangles.get(t);
            for (int c = 0; c < 3; c++) {
                Vector3f way = triangle[c];
                pushed[c].set(way).mul(shape.radius(way)).mul(shape.stretch);
                corners[t * 9 + c * 3] = pushed[c].x;
                corners[t * 9 + c * 3 + 1] = pushed[c].y;
                corners[t * 9 + c * 3 + 2] = pushed[c].z;
                shades[t * 3 + c] = shape.shade(way);
                glows[t * 3 + c] = shape.glow(way);
            }
            Vector3f face = new Vector3f(pushed[1]).sub(pushed[0]).cross(new Vector3f(pushed[2]).sub(pushed[0])).normalize();
            faces[t * 3] = face.x;
            faces[t * 3 + 1] = face.y;
            faces[t * 3 + 2] = face.z;
        }
        return new Mesh(corners, faces, shades, glows);
    }

    /** A ball of radius 1 as triangles, an icosahedron's faces each cut into four so many times, wound outward. */
    private static List<Vector3f[]> ball(int cuts) {
        float golden = (float) ((1 + Math.sqrt(5)) / 2);
        Vector3f[] points = {
            new Vector3f(-1, golden, 0), new Vector3f(1, golden, 0), new Vector3f(-1, -golden, 0), new Vector3f(1, -golden, 0),
            new Vector3f(0, -1, golden), new Vector3f(0, 1, golden), new Vector3f(0, -1, -golden), new Vector3f(0, 1, -golden),
            new Vector3f(golden, 0, -1), new Vector3f(golden, 0, 1), new Vector3f(-golden, 0, -1), new Vector3f(-golden, 0, 1)
        };
        int[][] icosahedron = {
            {0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
            {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}
        };
        List<Vector3f[]> triangles = new ArrayList<>();
        for (int[] face : icosahedron) {
            triangles.add(new Vector3f[] {new Vector3f(points[face[0]]).normalize(), new Vector3f(points[face[1]]).normalize(),
                new Vector3f(points[face[2]]).normalize()});
        }
        for (int cut = 0; cut < cuts; cut++) {
            List<Vector3f[]> finer = new ArrayList<>(triangles.size() * 4);
            for (Vector3f[] triangle : triangles) {
                Vector3f ab = new Vector3f(triangle[0]).add(triangle[1]).normalize();
                Vector3f bc = new Vector3f(triangle[1]).add(triangle[2]).normalize();
                Vector3f ca = new Vector3f(triangle[2]).add(triangle[0]).normalize();
                finer.add(new Vector3f[] {triangle[0], ab, ca});
                finer.add(new Vector3f[] {triangle[1], bc, ab});
                finer.add(new Vector3f[] {triangle[2], ca, bc});
                finer.add(new Vector3f[] {ab, bc, ca});
            }
            triangles = finer;
        }
        return triangles;
    }
}
