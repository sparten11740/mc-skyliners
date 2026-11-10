package dev.skyliners.spaceweather;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The rocks an asteroid storm sweeps past a rocket or a freighter (see {@link SpaceWeather}), worked out alike on the
 * server and on every client from the weather's seed, so that every client draws the same rocks at the same moments
 * and the server knows where each one is for her laser cannon to shoot at: a few big ones far off on the horizon the
 * way it comes from while it is told, drifting slowly in, and then dozens sweeping past her while it is on her. A giant
 * asteroid brings fewer of them round it.
 *
 * <p>Each rock flies in from far beyond the farthest anyone draws, past where she would be had nothing struck her, and
 * on out of sight again, setting out from the far end of its way no sooner than the weather is told, so none ever
 * shows out of nowhere near her.
 */
public final class RockField {
    /** Mixed into the weather's seed for its rocks, so they draw on numbers of their own, apart from its streaks. */
    private static final long SALT = 0x2A57E401DL;
    /**
     * The rocks of an asteroid storm: how many show on the horizon while it is told, and how many sweep past while it
     * is on her; and of a giant asteroid, its ordinary rocks round it, fewer.
     */
    private static final int TOLD_ROCKS = 8;
    private static final int STORM_ROCKS = 36;
    private static final int GIANT_TOLD_ROCKS = 4;
    private static final int GIANT_STORM_ROCKS = 16;
    /**
     * The far rocks shown while it is told: how far off they are, at the nearest and farthest, in blocks, out on the
     * horizon the way it comes from, and how far either side of that way; how big they are, in blocks across; and how
     * fast they drift, in blocks a second, from far beyond sight in.
     */
    private static final double FAR_NEAREST = 420;
    private static final double FAR_FARTHEST = 600;
    private static final double FAR_ACROSS = 300;
    private static final double FAR_SMALLEST = 16;
    private static final double FAR_BIGGEST = 34;
    private static final double FAR_SPEED = 14;
    /**
     * How near her the far rocks' ways pass at the least, in blocks, past where rocks become real bodies, so each stays
     * a far-off sight and never comes at her; and how far on they drift once the weather is over, in blocks, out of
     * everyone's sight, the slowest taking a little over a minute.
     */
    private static final double FAR_PASSES_NEAREST = 280;
    private static final double FAR_DRIFTS_ON = 1200;
    /**
     * The rocks sweeping past her while it is on her: how big they are at least and at most, in blocks across, more of
     * them small than big; how fast they go, in blocks a second; how far off they pass her, at the nearest and
     * farthest; and how far they are seen going, from far beyond where anyone draws them to as far again past her.
     */
    private static final double SMALLEST = 2;
    private static final double BIGGEST = 14;
    private static final double SLOWEST = 25;
    private static final double FASTEST = 60;
    private static final double NEAREST = 30;
    private static final double FARTHEST = 260;
    public static final double PATH = 1600;
    /** How far the rocks' ways stray from the weather's, in degrees, round and up or down. */
    private static final double STRAY = 25;
    private static final double STRAY_UP = 15;
    /** How far the far rocks' ways stray, round and up or down, in degrees. */
    private static final double FAR_STRAY = 40;
    private static final double FAR_STRAY_UP = 10;
    /** Ticks a second. */
    private static final double TICKS = 20;

    private RockField() {
    }

    /**
     * A rock: its number among the weather's; when it comes, in ticks after the weather was told; the middle of its
     * way, from where she would be, where one sweeping past passes nearest her; the way it goes, of length 1, and how
     * fast, in blocks a second;
     * how far it is seen going, in blocks; how big it is, in blocks across; and its seed, for its shape and its tumble.
     */
    public record Rock(int id, double born, Vec3 passing, Vec3 way, double speed, double path, double size, long seed) {
        /** How long it is seen, in ticks. */
        public double life() {
            return path / speed * TICKS;
        }

        /** How far along its way it is so long after the weather was told, in ticks, from 0 at its start to 1 at its end. */
        public double age(double since) {
            return (since - born) / life();
        }

        /** Whether it is on its way at so long after the weather was told, in ticks. */
        public boolean flying(double since) {
            double age = age(since);
            return age > 0 && age < 1;
        }

        /** Where its middle is at so long after the weather was told, in ticks, from where she would be then. */
        public Vec3 at(Vec3 anchor, double since) {
            return anchor.add(passing).add(way.scale((age(since) - 0.5) * path));
        }
    }

    /** The rocks of weather told, none for weather that brings none. */
    public static List<Rock> of(SpaceWeatherShown told) {
        List<Rock> rocks = new ArrayList<>();
        boolean giant = told.kind() == SpaceWeather.Kind.GIANT_ASTEROID;
        if (told.kind() != SpaceWeather.Kind.ASTEROID_STORM && !giant) {
            return rocks;
        }
        RandomSource random = RandomSource.create(told.seed() ^ SALT);
        Vec3 out = SpaceWeather.toward(told.from());
        far(random, out, told, giant ? GIANT_TOLD_ROCKS : TOLD_ROCKS, rocks);
        passing(random, out, told, giant ? GIANT_STORM_ROCKS : STORM_ROCKS, rocks);
        return rocks;
    }

    /**
     * The big rocks on the horizon while it is told, drifting slowly in from far beyond sight, and once the weather is
     * over drifting on past her, never near, out of sight again.
     */
    private static void far(RandomSource random, Vec3 out, SpaceWeatherShown told, int count, List<Rock> rocks) {
        for (int i = 0; i < count; i++) {
            Vec3 across = new Vec3(-out.z, 0, out.x);
            double off = Mth.lerp(random.nextDouble(), FAR_NEAREST, FAR_FARTHEST);
            Vec3 drawnIn = out.scale(off).add(across.scale((random.nextDouble() * 2 - 1) * FAR_ACROSS)).add(0, (random.nextDouble() - 0.3) * off * 0.15, 0);
            Vec3 way = strayed(random, out.reverse(), FAR_STRAY, FAR_STRAY_UP);
            double size = Mth.lerp(random.nextDouble(), FAR_SMALLEST, FAR_BIGGEST);
            double drift = FAR_SPEED * (told.warningTicks() + told.stormTicks()) / TICKS;
            Vec3 passing = passingClear(drawnIn, way).add(way.scale(FAR_DRIFTS_ON / 2));
            rocks.add(new Rock(rocks.size(), 0, passing, way, FAR_SPEED, drift + FAR_DRIFTS_ON, size, random.nextLong()));
        }
    }

    /**
     * A spot a far rock's way runs through, from her, moved out to one side of its way as far as it takes for the way
     * to pass her no nearer than {@link #FAR_PASSES_NEAREST}.
     */
    private static Vec3 passingClear(Vec3 spot, Vec3 way) {
        Vec3 nearest = spot.subtract(way.scale(spot.dot(way)));
        double off = nearest.length();
        if (off >= FAR_PASSES_NEAREST) {
            return spot;
        }
        Vec3 aside = off > 1e-6 ? nearest.scale(1 / off) : new Vec3(-way.z, 0, way.x).normalize();
        return spot.add(aside.scale(FAR_PASSES_NEAREST - off));
    }

    /**
     * The rocks sweeping past her while it is on her, each passing nearest her at a moment while it is on her, and
     * never before it could have flown in from the far end of its way since the weather was told.
     */
    private static void passing(RandomSource random, Vec3 out, SpaceWeatherShown told, int count, List<Rock> rocks) {
        for (int i = 0; i < count; i++) {
            Vec3 way = strayed(random, out.reverse(), STRAY, STRAY_UP);
            double speed = Mth.lerp(random.nextDouble(), SLOWEST, FASTEST);
            double halfLife = PATH / 2 / speed * TICKS;
            double first = Math.max(told.warningTicks(), halfLife);
            double nearest = Mth.lerp(random.nextDouble(), first, Math.max(first, told.warningTicks() + told.stormTicks()));
            double size = Mth.lerp(random.nextDouble() * random.nextDouble(), SMALLEST, BIGGEST);
            rocks.add(new Rock(rocks.size(), nearest - halfLife, beside(random, way, NEAREST, FARTHEST), way, speed, PATH, size, random.nextLong()));
        }
    }

    /** A way strayed from another at random, so far round and so far up or down at most, in degrees; of length 1. */
    private static Vec3 strayed(RandomSource random, Vec3 way, double round, double up) {
        return way.yRot((float) Math.toRadians((random.nextDouble() * 2 - 1) * round))
            .add(0, Math.sin(Math.toRadians((random.nextDouble() * 2 - 1) * up)), 0).normalize();
    }

    /**
     * A spot so far off at random to one side of a way, more often far than near, its nearest to her along that way.
     */
    private static Vec3 beside(RandomSource random, Vec3 way, double nearest, double farthest) {
        Vec3 side = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5);
        Vec3 across = side.subtract(way.scale(side.dot(way)));
        if (across.lengthSqr() < 1e-6) {
            across = new Vec3(-way.z, 0, way.x);
        }
        return across.normalize().scale(Mth.lerp(Math.sqrt(random.nextDouble()), nearest, farthest));
    }
}
