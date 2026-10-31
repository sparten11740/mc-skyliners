package dev.aerolarge.client;

import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * A ball for the orrery's sun and planets (see {@link OrreryRenderer}): a globe of rings from pole to pole and faces
 * round each, its corners worked out once, and its picture laid on it once round from west to east and from its north
 * pole down, as a globe's map is. Each corner's colour is how the sun lights it there, worked out as it is drawn: full
 * on the side facing the sun, falling to the faint blue of the night side, so each planet shows its day and night and
 * the moon its phases as they go round.
 */
final class Globe {
    /** The finest globes, for the sun, and for the planets, and the coarsest, for any small on the screen. */
    static final Globe SUN = new Globe(20, 10);
    static final Globe PLANET = new Globe(16, 8);
    static final Globe SMALL = new Globe(8, 4);
    /** How dim a planet's night side is, in each colour: a little blue, as if lit by the stars. */
    private static final float[] NIGHT = {0.10F, 0.12F, 0.18F};
    /** The sun's light on a planet's day side, in each colour: white. */
    private static final float[] DAY = {1, 1, 1};

    private final int round;
    private final int rings;
    /** Each corner's way out from the middle, ring by ring from the north pole, round from west to east. */
    private final Vector3f[][] corners;

    private Globe(int round, int rings) {
        this.round = round;
        this.rings = rings;
        this.corners = new Vector3f[rings + 1][round + 1];
        for (int ring = 0; ring <= rings; ring++) {
            float lat = Mth.HALF_PI - Mth.PI * ring / rings;
            for (int k = 0; k <= round; k++) {
                float lon = Mth.TWO_PI * k / round;
                // Eastward is anticlockwise seen from over the north pole, as on a real globe.
                corners[ring][k] = new Vector3f(Mth.cos(lat) * Mth.cos(lon), Mth.sin(lat), -Mth.cos(lat) * Mth.sin(lon));
            }
        }
    }

    /** How a globe's corners are lit. */
    sealed interface Light permits SunLit, Glowing {
        /** How bright a corner facing the way given is, in each colour, into the colour given. */
        void at(Vector3f facing, float[] colour);
    }

    /**
     * Lit by the sun, which lies that way from the globe: as bright as its night side in each colour, a share, and on
     * its day side its colour more where it faces the sun more.
     */
    record SunLit(Vector3f toSun, float[] night, float[] colour) implements Light {
        /** A planet's light: full on its day side, faint blue on its night side. */
        SunLit(Vector3f toSun) {
            this(toSun, NIGHT, DAY);
        }

        @Override
        public void at(Vector3f facing, float[] lit) {
            float day = Math.max(0, facing.dot(toSun));
            for (int i = 0; i < 3; i++) {
                lit[i] = Math.min(1, night[i] + day * colour[i]);
            }
        }
    }

    /**
     * Glowing of itself in a colour, each a share, as the sun does or light added on: darker toward its rim by so much,
     * a share, seen from the camera, which lies that way from it, so it looks round rather than flat.
     */
    record Glowing(float r, float g, float b, Vector3f toCamera, float limb) implements Light {
        @Override
        public void at(Vector3f facing, float[] colour) {
            float rim = 1 - limb * (1 - Math.abs(facing.dot(toCamera)));
            colour[0] = r * rim;
            colour[1] = g * rim;
            colour[2] = b * rim;
        }
    }

    /**
     * The globe so big round a middle, turned as given (its spin and tilt), its picture the patch given, each corner as
     * the light has it.
     */
    void draw(OrreryBrush brush, Vector3f middle, float radius, Matrix3f turn, OrreryBrush.Patch patch, Light light) {
        Vector3f[] facing = new Vector3f[round + 1];
        Vector3f[] above = new Vector3f[round + 1];
        float[] colour = new float[3];
        for (int k = 0; k <= round; k++) {
            above[k] = turn.transform(corners[0][k], new Vector3f());
        }
        for (int ring = 1; ring <= rings; ring++) {
            for (int k = 0; k <= round; k++) {
                facing[k] = turn.transform(corners[ring][k], new Vector3f());
            }
            float v0 = patch.v((ring - 1F) / rings);
            float v1 = patch.v((float) ring / rings);
            for (int k = 0; k < round; k++) {
                float u0 = patch.u((float) k / round);
                float u1 = patch.u((k + 1F) / round);
                corner(brush, middle, radius, above[k], u0, v0, light, colour);
                corner(brush, middle, radius, facing[k], u0, v1, light, colour);
                corner(brush, middle, radius, facing[k + 1], u1, v1, light, colour);
                corner(brush, middle, radius, above[k + 1], u1, v0, light, colour);
            }
            Vector3f[] done = above;
            above = facing;
            facing = done;
        }
    }

    private static void corner(OrreryBrush brush, Vector3f middle, float radius, Vector3f facing, float u, float v, Light light, float[] colour) {
        light.at(facing, colour);
        brush.vertex(middle.x + facing.x * radius, middle.y + facing.y * radius, middle.z + facing.z * radius, u, v, colour[0], colour[1], colour[2]);
    }
}
