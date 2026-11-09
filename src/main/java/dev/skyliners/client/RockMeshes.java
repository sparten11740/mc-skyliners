package dev.skyliners.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.skyliners.Skyliners;
import dev.skyliners.spaceweather.RockShape;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The rocks of space weather as the client draws them (see {@link SpaceWeatherSky}), each one of {@link RockShape}'s
 * lumpy, cratered stones, cut finer for a rock seen up close and coarser far off.
 *
 * <p>Each is lit by the sun where the game puts it, its far side dark, with a little blue light thrown up from the
 * planet below, and drawn see-through as it fades in from far off, its triangles' corners worked out on the client each
 * frame: a few hundred for a rock far off, more only for the few up close.
 */
final class RockMeshes {
    /** How often each shape's ball is cut finer at its levels of detail: 80, 320 and 1,280 triangles. */
    private static final int[] DETAIL = {1, 2, 3};
    /**
     * How big a rock must look, as its half width over how far off it is, to be drawn at the next finer level of detail:
     * about a degree and a half across, and about five.
     */
    private static final double[] FINER_FROM = {0.012, 0.045};
    /**
     * How it is lit: the light on its side away from the sun, as a share; how much more the sun gives its face turned
     * square to it; and the blue light thrown up from the planet onto its underside.
     */
    private static final float SHADE = 0.22F;
    private static final float SUNLIT = 0.95F;
    private static final float PLANET_LIGHT = 0.3F;
    private static final float[] PLANET_TINT = {0.45F, 0.65F, 1.0F};
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
     * A rock drawn with its middle so far from the camera, so big across, as it looks, turned so far, as bright as
     * given from 0 to 1 as it fades in, lit from the sun's way and from below by the planet: cut as finely as how big it
     * looks calls for.
     */
    static void draw(VertexConsumer buffer, PoseStack pose, double x, double y, double z, double size, RockShape.Look look, Quaternionf turned, float fade,
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
    private static void corner(VertexConsumer buffer, Matrix4f matrix, Mesh mesh, RockShape.Look look, int corner, float sunlight, float planet, int alpha) {
        float shade = mesh.shades[corner];
        float glow = look.magma() ? mesh.glows[corner] : 0;
        float[] colour = look.colour();
        float red = colour[0] * shade * (sunlight + planet * PLANET_TINT[0]) + glow * (RockShape.MAGMA_GLOW >> 16 & 0xFF) / 255F;
        float green = colour[1] * shade * (sunlight + planet * PLANET_TINT[1]) + glow * (RockShape.MAGMA_GLOW >> 8 & 0xFF) / 255F;
        float blue = colour[2] * shade * (sunlight + planet * PLANET_TINT[2]) + glow * (RockShape.MAGMA_GLOW & 0xFF) / 255F;
        buffer.addVertex(matrix, mesh.corners[corner * 3], mesh.corners[corner * 3 + 1], mesh.corners[corner * 3 + 2])
            .setColor(Math.min(1, red), Math.min(1, green), Math.min(1, blue), alpha / 255F);
    }

    /** A shape cut at a level of detail, made the first time it is asked for. */
    private static Mesh mesh(int shape, int detail) {
        return MESHES.computeIfAbsent(shape * DETAIL.length + detail, key -> make(RockShape.of(shape), DETAIL[detail]));
    }

    /** A shape made from its makings: a ball cut so often, each corner pushed out and coloured, each face lit flat. */
    private static Mesh make(RockShape shape, int cuts) {
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
                pushed[c].set(way).mul(shape.radius(way)).mul(shape.stretch());
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
