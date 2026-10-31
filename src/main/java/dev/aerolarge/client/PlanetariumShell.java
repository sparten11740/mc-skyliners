package dev.aerolarge.client;

import dev.aerolarge.Aerolarge;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The planetarium's sky inside the rocket's dome (see {@link OrreryDome}): a smooth surface round her axis just inside
 * the dome's lining, hiding its steps, opaque and as bright by day as by night, the night sky painted on it once round
 * from the dome's foot up to the pole star at the tip (see {@code domeSkyTexture} in {@code generate_textures.mjs});
 * and over it a few dozen stars twinkling, each in its own time, added onto it.
 */
final class PlanetariumShell {
    static final ResourceLocation SKY = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/misc/dome_sky.png");
    /** How many faces go round the shell. */
    private static final int SEGMENTS = 48;
    private static final float[] COS = new float[SEGMENTS + 1];
    private static final float[] SIN = new float[SEGMENTS + 1];
    /** How many stars twinkle over the sky, and the seed they are scattered with, the same in every dome. */
    private static final int SPARKLES = 40;
    private static final long SPARKLE_SEED = 0x5747A2L;
    /** How far down the sky's picture the highest of them is, from the pole star (0) to the dome's foot (1). */
    private static final float SPARKLE_TOP = 0.08F;
    /** How fast each twinkles, in radians a tick: once in about four and a half seconds. */
    private static final float TWINKLE_SPEED = 0.07F;
    /** How far its brightness dips as it twinkles, a share of its brightest. */
    private static final float TWINKLE_DEPTH = 0.45F;
    /**
     * How big a twinkling star is, from its middle to its points, as a share of the dome's radius, and at least and
     * most.
     */
    private static final float SPARKLE_SHARE = 0.025F;
    private static final float SPARKLE_LEAST = 0.12F;
    private static final float SPARKLE_MOST = 0.4F;
    /** How far in front of the sky a twinkling star is, in blocks, so the sky never covers it. */
    private static final float SPARKLE_INSET = 0.05F;
    /** A twinkling star's colour, blue-white, each a share. */
    private static final float[] SPARKLE_COLOUR = {0.75F, 0.85F, 1.0F};
    private static final OrreryBrush.Patch SPARKLE = OrreryGlow.SPARKLE;
    /** Where each twinkling star is: so far round the dome and so far down the sky's picture. */
    private static final float[][] SPARKLE_SPOTS = new float[SPARKLES][];

    static {
        for (int k = 0; k <= SEGMENTS; k++) {
            COS[k] = Mth.cos(Mth.TWO_PI * k / SEGMENTS);
            SIN[k] = Mth.sin(Mth.TWO_PI * k / SEGMENTS);
        }
        // Thicker toward the foot, where the dome is wider round, so they lie about as thick all over it.
        RandomSource random = RandomSource.create(SPARKLE_SEED);
        for (int i = 0; i < SPARKLES; i++) {
            SPARKLE_SPOTS[i] = new float[]{random.nextFloat(), Mth.lerp(Mth.sqrt(random.nextFloat()), SPARKLE_TOP, 1)};
        }
    }

    private PlanetariumShell() {
    }

    /** The sky: each run between two of the dome's rings, and over the top one a lid closing it. */
    static void draw(OrreryDome dome, OrreryBrush brush) {
        List<OrreryDome.Ring> rings = dome.shell();
        for (int i = 0; i + 1 < rings.size(); i++) {
            band(brush, rings.get(i), rings.get(i + 1));
        }
        lid(brush, rings.getLast());
    }

    private static void band(OrreryBrush brush, OrreryDome.Ring low, OrreryDome.Ring high) {
        for (int k = 0; k < SEGMENTS; k++) {
            corner(brush, low, k);
            corner(brush, low, k + 1);
            corner(brush, high, k + 1);
            corner(brush, high, k);
        }
    }

    /** The lid over the top ring, each face a wedge to her axis there, where the pole star is. */
    private static void lid(OrreryBrush brush, OrreryDome.Ring top) {
        for (int k = 0; k < SEGMENTS; k++) {
            corner(brush, top, k);
            corner(brush, top, k + 1);
            brush.vertex(OrreryDome.AXIS, top.y(), OrreryDome.AXIS, (k + 1F) / SEGMENTS, 0, 1, 1, 1);
            brush.vertex(OrreryDome.AXIS, top.y(), OrreryDome.AXIS, (float) k / SEGMENTS, 0, 1, 1, 1);
        }
    }

    /** A corner of the sky on a ring, so many faces round: the picture's width goes once round. */
    private static void corner(OrreryBrush brush, OrreryDome.Ring ring, int k) {
        brush.vertex(OrreryDome.AXIS + COS[k] * ring.radius(), ring.y(), OrreryDome.AXIS + SIN[k] * ring.radius(), (float) k / SEGMENTS, ring.v(), 1, 1,
            1);
    }

    /** The twinkling stars over the sky, as bright as each is at this moment of the game's time. */
    static void sparkles(OrreryDome dome, OrreryBrush glow, OrreryBrush.Facing facing, OrreryClock clock) {
        float ticks = clock.ticks();
        float size = Mth.clamp(dome.outer() * SPARKLE_SHARE, SPARKLE_LEAST, SPARKLE_MOST);
        for (int i = 0; i < SPARKLES; i++) {
            float strength = 1 - TWINKLE_DEPTH * (0.5F + 0.5F * Mth.sin(ticks * TWINKLE_SPEED + i));
            float[] at = onSky(dome.shell(), SPARKLE_SPOTS[i][0], SPARKLE_SPOTS[i][1]);
            glow.facingCamera(facing, at[0], at[1], at[2], size * strength, SPARKLE, SPARKLE_COLOUR[0] * strength, SPARKLE_COLOUR[1] * strength,
                SPARKLE_COLOUR[2] * strength);
        }
    }

    /**
     * Where a point of the sky is, so far round the dome and so far down its picture, just in front of it: between the
     * two rings it lies between, as the sky is drawn.
     */
    private static float[] onSky(List<OrreryDome.Ring> rings, float round, float down) {
        OrreryDome.Ring low = rings.getFirst();
        OrreryDome.Ring high = rings.getFirst();
        for (OrreryDome.Ring ring : rings) {
            low = high;
            high = ring;
            if (ring.v() <= down) {
                break;
            }
        }
        float share = low.v() == high.v() ? 0 : Mth.clamp((low.v() - down) / (low.v() - high.v()), 0, 1);
        float radius = Mth.lerp(share, low.radius(), high.radius()) - SPARKLE_INSET;
        float angle = Mth.TWO_PI * round;
        return new float[]{OrreryDome.AXIS + Mth.cos(angle) * radius, Mth.lerp(share, low.y(), high.y()), OrreryDome.AXIS + Mth.sin(angle) * radius};
    }
}
