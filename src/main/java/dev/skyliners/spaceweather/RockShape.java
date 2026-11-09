package dev.skyliners.spaceweather;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The shapes the rocks of space weather are made in: lumpy, uneven stones, each made once from a seed of its own as a
 * ball pushed in and out, stretched long or squat, knobbly with lumps, pitted with craters with raised rims, a ridge
 * running round some, grainy all over, and patched lighter and darker, crater floors darker and rims lighter; now and
 * then one of dark rock cracked with glowing magma. There are {@link #SHAPES} shapes; every rock picks one by its seed
 * and is stretched, tinted and set tumbling about two axes at once its own way, so no two near each other look alike.
 * Known alike to the server and every client.
 */
public final class RockShape {
    /** How many shapes of rock there are, each made once from a seed of its own. */
    private static final int SHAPES = 16;
    /** The seed each shape is made from is this, plus its number. */
    private static final long SHAPE_SEED = 0x5A57E0C3L;
    /**
     * A shape's stretch: how long it may be drawn along its first axis at most, and how squat along its second at the
     * least, as shares of its width; and how much more or less each rock is stretched again its own way, at most.
     */
    private static final float LONGEST = 1.45F;
    private static final float SQUATTEST = 0.72F;
    private static final float ROCK_STRETCH = 0.3F;
    /**
     * A shape's lumps: how many, at least and how many more at most; how wide each is, in radians round the ball, at
     * least and at most; and how far each pushes the ball out or in, at most, as a share of its radius.
     */
    private static final int LUMPS = 6;
    private static final int MORE_LUMPS = 6;
    private static final double LUMP_NARROWEST = 0.35;
    private static final double LUMP_WIDEST = 1.1;
    private static final double LUMP_HEIGHT = 0.3;
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
    /**
     * How likely a shape is to have a ridge round it, how high it stands and how wide it is, as shares of the radius.
     */
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
    public static final int[] STONES = {0x8C8781, 0x7D6E60, 0x5B5754, 0x8A6A58, 0x9A948C};
    /**
     * A magma rock: how often one is, as a share; the dark rock it is of; the glow of its magma; and how thin its
     * glowing cracks run, as a share of the grain's height.
     */
    private static final float MAGMA_CHANCE = 0.22F;
    private static final int MAGMA_ROCK = 0x3E3430;
    public static final int MAGMA_GLOW = 0xFF6A1A;
    private static final double CRACKS = 0.06;
    /** How much of each rock's colour it may be tinted lighter or darker, at most, as a share, so no two look alike. */
    private static final float TINT = 0.12F;
    /**
     * How rough a rock's skin is (see {@link #rough}), in blocks either way: for any rock, for each block across it is,
     * and at most; how many waves of it there are to the block, the long and the short; how much of the short wave the
     * long is; and what is mixed into the seed of the short.
     */
    private static final double ROUGH_FROM = 0.35;
    private static final double ROUGH_PER_BLOCK = 0.03;
    private static final double ROUGHEST = 1.1;
    private static final double ROUGH_LONG = 0.22;
    private static final double ROUGH_SHORT = 0.55;
    private static final double ROUGH_LONG_SHARE = 1.5;
    private static final long ROUGH_SALT = 0x51ED270B9L;
    /** How fast a rock tumbles about each of its two axes, at most, in degrees a second. */
    private static final double MOST_TUMBLE = 70;
    /** Each of the shapes, by its number. */
    private static final List<RockShape> MADE = made();

    private final Vector3f stretch;
    private final List<float[]> lumps = new ArrayList<>();
    private final List<float[]> craters = new ArrayList<>();
    private final List<float[]> grain = new ArrayList<>();
    private final List<float[]> patches = new ArrayList<>();
    private final Vector3f ridge;
    private final boolean ridged;

    private RockShape(RandomSource random) {
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
    public float radius(Vector3f way) {
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
    private double grain(Vector3f way) {
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
    public float shade(Vector3f way) {
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
    public float glow(Vector3f way) {
        double crack = Math.max(0, 1 - Math.abs(grain(way)) / CRACKS);
        double floor = 0;
        for (float[] crater : craters) {
            floor = Math.max(floor, -crater(way, crater) / (CRATER_DEPTH * crater[3]));
        }
        return (float) Mth.clamp(Math.max(crack, floor * floor * floor), 0, 1);
    }

    private static List<RockShape> made() {
        List<RockShape> shapes = new ArrayList<>();
        for (int shape = 0; shape < SHAPES; shape++) {
            shapes.add(new RockShape(RandomSource.create(SHAPE_SEED + shape)));
        }
        return List.copyOf(shapes);
    }

    /**
     * The roughness of a rock's skin at a point among its blocks, in blocks, either way, of a size and a seed: two
     * waves of noise, a long one and a short one, so its surface is broken up block by block as well as lump by lump
     * and no two facets side by side lie alike. Bigger rocks are rougher, a small one keeping to its shape.
     */
    public static double rough(double x, double y, double z, double size, long seed) {
        double amount = Math.min(ROUGHEST, ROUGH_FROM + size * ROUGH_PER_BLOCK);
        double broad = noise(x * ROUGH_LONG, y * ROUGH_LONG, z * ROUGH_LONG, seed) * 2 - 1;
        double fine = noise(x * ROUGH_SHORT, y * ROUGH_SHORT, z * ROUGH_SHORT, seed ^ ROUGH_SALT) * 2 - 1;
        return amount * (broad * ROUGH_LONG_SHARE + fine);
    }

    /** A smooth noise from 0 to 1 through a point, of a seed: the same for anyone working it out. */
    public static double noise(double x, double y, double z, long seed) {
        int xi = Mth.floor(x);
        int yi = Mth.floor(y);
        int zi = Mth.floor(z);
        double fx = smooth(x - xi);
        double fy = smooth(y - yi);
        double fz = smooth(z - zi);
        double near = Mth.lerp(fy, Mth.lerp(fx, lattice(xi, yi, zi, seed), lattice(xi + 1, yi, zi, seed)),
            Mth.lerp(fx, lattice(xi, yi + 1, zi, seed), lattice(xi + 1, yi + 1, zi, seed)));
        double far = Mth.lerp(fy, Mth.lerp(fx, lattice(xi, yi, zi + 1, seed), lattice(xi + 1, yi, zi + 1, seed)),
            Mth.lerp(fx, lattice(xi, yi + 1, zi + 1, seed), lattice(xi + 1, yi + 1, zi + 1, seed)));
        return Mth.lerp(fz, near, far);
    }

    /** A number from 0 to 1 for a point of whole numbers, of a seed. */
    private static double lattice(int x, int y, int z, long seed) {
        long hash = seed + x * 0x9E3779B97F4A7C15L + y * 0xC2B2AE3D27D4EB4FL + z * 0x165667B19E3779F9L;
        hash = (hash ^ hash >>> 29) * 0xBF58476D1CE4E5B9L;
        hash = (hash ^ hash >>> 32) * 0x94D049BB133111EBL;
        return ((hash ^ hash >>> 29) >>> 11) / (double) (1L << 53);
    }

    /** A shape by its number. */
    public static RockShape of(int shape) {
        return MADE.get(shape);
    }

    /** How it is stretched along each axis, before each rock's own stretch. */
    public Vector3f stretch() {
        return stretch;
    }

    /**
     * How one rock looks: its shape, which of the {@link #STONES} it is of (none, -1, of magma), whether it is of
     * magma, its stone's colour, as red, green and blue from 0 to 1, how it is stretched along each of its axes, and
     * the two axes it tumbles about, and how fast, in degrees a second.
     */
    public record Look(int shape, int stone, boolean magma, float[] colour, Vector3f stretch, Vector3f firstAxis, double firstTumble, Vector3f secondAxis,
                double secondTumble) {
        /** How fast it turns in all, in radians a second about an axis in its own frame's first turning. */
        public Vec3 spin() {
            return new Vec3(firstAxis).scale(Math.toRadians(firstTumble)).add(new Vec3(secondAxis).scale(Math.toRadians(secondTumble)));
        }

        /** How it is turned so many seconds into its tumble. */
        public Quaternionf turned(double seconds) {
            float first = (float) (Math.toRadians(firstTumble * seconds) % Mth.TWO_PI);
            float second = (float) (Math.toRadians(secondTumble * seconds) % Mth.TWO_PI);
            return new Quaternionf().rotationAxis(first, firstAxis).rotateAxis(second, secondAxis);
        }
    }

    /** How a rock looks, picked by its seed. */
    public static Look look(long seed) {
        RandomSource random = RandomSource.create(seed);
        int shape = random.nextInt(SHAPES);
        boolean magma = random.nextFloat() < MAGMA_CHANCE;
        int number = magma ? -1 : random.nextInt(STONES.length);
        int stone = magma ? MAGMA_ROCK : STONES[number];
        float tint = 1 + (random.nextFloat() * 2 - 1) * TINT;
        float[] colour = {(stone >> 16 & 0xFF) / 255F * tint, (stone >> 8 & 0xFF) / 255F * tint, (stone & 0xFF) / 255F * tint};
        Vector3f stretch = new Vector3f(1 + spread(random) * ROCK_STRETCH, 1 + spread(random) * ROCK_STRETCH, 1 + spread(random) * ROCK_STRETCH);
        return new Look(shape, number, magma, colour, stretch, axis(random), spread(random) * MOST_TUMBLE, axis(random), spread(random) * MOST_TUMBLE * 0.5);
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

    private static double smooth(double along) {
        return along * along * (3 - 2 * along);
    }
}
