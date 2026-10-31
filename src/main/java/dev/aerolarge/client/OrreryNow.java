package dev.aerolarge.client;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * Where everything of the rocket's orrery is at a moment of the game's time (see {@link OrreryRenderer}), in its sun's
 * frame, the sun's middle at the origin: the planets circling it in its plane, anticlockwise seen from over it, the
 * nearer the faster, each turning on its own tilted axis as it goes, the Moon going round the Earth, and over the sun
 * the arms that carry them, one over another, the innermost planet's lowest. All of it is sized from how far out its
 * outermost planet, Saturn, circles, as a share of the reach its sizes are given at; but no globe smaller than can be
 * seen, and the Moon only where its path fits between the Earth's neighbours'.
 */
final class OrreryNow {
    /** The reach the sizes below are given at, in blocks: how far out Saturn circles. */
    static final float DESIGN_REACH = 5.8F;
    /** How wide the sun is at the design reach, in blocks. */
    private static final float SUN_SIZE = 1.8F;
    /** The smallest any globe is drawn, in blocks across. */
    private static final float SMALLEST = 0.12F;
    /**
     * How far over the sun's top the lowest arm leaves the column, and each next one over that, and how far over the
     * highest arm the gears turn, in blocks at the design reach.
     */
    private static final float FIRST_ARM = 0.2F;
    private static final float ARM_STEP = 0.13F;
    private static final float GEARS_OVER = 0.15F;
    /** The least room kept between two globes' paths, in blocks, so the Moon never brushes a neighbour. */
    private static final float PATHS_APART = 0.03F;

    /**
     * A globe: its picture, how wide it is and how far out it circles at the design reach, in blocks; how long it
     * takes to go round once and to turn once, in ticks (backward for a negative time); and where round it starts and
     * how far its axis tilts, in radians.
     */
    record Planet(OrreryBrush.Patch patch, float size, float orbit, float year, float day, float start, float tilt) {
    }

    static final Planet MERCURY = new Planet(OrrerySheet.MERCURY, 0.28F, 1.45F, 800, 1200, 0.3F, 0);
    static final Planet VENUS = new Planet(OrrerySheet.VENUS, 0.44F, 2.05F, 2000, -1600, 2.1F, 0.05F);
    static final Planet EARTH = new Planet(OrrerySheet.EARTH, 0.5F, 2.8F, 3200, 160, 4.0F, 0.41F);
    static final Planet MARS = new Planet(OrrerySheet.MARS, 0.36F, 3.5F, 6000, 170, 5.2F, 0.44F);
    static final Planet JUPITER = new Planet(OrrerySheet.JUPITER, 1.1F, 4.35F, 18000, 100, 1.2F, 0.05F);
    static final Planet SATURN = new Planet(OrrerySheet.SATURN, 0.9F, DESIGN_REACH, 36000, 110, 3.3F, 0.47F);
    /** The Moon, going round the Earth: it turns once each time round, so it keeps one face to her. */
    static final Planet MOON = new Planet(OrrerySheet.MOON, 0.14F, 0.4F, 240, 240, 0, 0);
    /** The planets from the sun out. */
    static final List<Planet> PLANETS = List.of(MERCURY, VENUS, EARTH, MARS, JUPITER, SATURN);

    /**
     * A globe where it is now: what it is, where its middle is, how big it is, how it is turned, how far round it has
     * come, in radians, and how high over the sun's middle its arm is, in blocks.
     */
    record Placed(Planet planet, Vector3f middle, float radius, Matrix3f turn, float angle, float arm) {
    }

    /** How far out Saturn circles, in blocks, and that as a share of the design reach. */
    final float reach;
    final float scale;
    /** How big the sun is, from its middle, in blocks. */
    final float sun;
    /** The planets from the sun out, where they are now. */
    final List<Placed> planets;
    /** The Moon where it is now; null where it does not fit. */
    @Nullable
    final Placed moon;
    /** How high over the sun's middle the gears turn, in blocks. */
    final float gears;

    OrreryNow(float reach, OrreryClock clock) {
        this.reach = reach;
        this.scale = reach / DESIGN_REACH;
        this.sun = SUN_SIZE * scale / 2;
        List<Placed> placed = new ArrayList<>();
        for (int i = 0; i < PLANETS.size(); i++) {
            placed.add(around(PLANETS.get(i), new Vector3f(), sun + (FIRST_ARM + ARM_STEP * i) * scale, clock));
        }
        this.planets = List.copyOf(placed);
        this.gears = placed.getLast().arm() + GEARS_OVER * scale;
        this.moon = moon(clock);
    }

    /** How big a globe is drawn, from its middle, in blocks: as its size at this reach, but never too small to see. */
    float radiusOf(Planet planet) {
        return Math.max(SMALLEST, planet.size() * scale) / 2;
    }

    /** A globe going round a middle, where it has come to now, its arm so high. */
    private Placed around(Planet planet, Vector3f middle, float arm, OrreryClock clock) {
        float angle = planet.start() + clock.turned(planet.year());
        float out = planet == MOON ? moonOrbit() : planet.orbit() * scale;
        Vector3f at = new Vector3f(middle).add(Mth.cos(angle) * out, 0, -Mth.sin(angle) * out);
        Matrix3f turn = new Matrix3f().rotateX(planet.tilt()).rotateY(clock.turned(planet.day()));
        return new Placed(planet, at, radiusOf(planet), turn, angle, arm);
    }

    /** How far from the Earth the Moon goes round, in blocks: never so near that it touches her. */
    private float moonOrbit() {
        return Math.max(MOON.orbit() * scale, radiusOf(EARTH) + radiusOf(MOON) + PATHS_APART);
    }

    /**
     * The Moon round the Earth where it is now, its little arm just over her top; none where its path would meet
     * Venus's or Mars's.
     */
    @Nullable
    private Placed moon(OrreryClock clock) {
        float earth = EARTH.orbit() * scale;
        float reaches = moonOrbit() + radiusOf(MOON) + PATHS_APART;
        boolean clearOfVenus = earth - reaches >= VENUS.orbit() * scale + radiusOf(VENUS);
        boolean clearOfMars = earth + reaches <= MARS.orbit() * scale - radiusOf(MARS);
        if (!clearOfVenus || !clearOfMars) {
            return null;
        }
        Placed home = planets.get(PLANETS.indexOf(EARTH));
        return around(MOON, home.middle(), home.radius() + PATHS_APART, clock);
    }
}
