package dev.skyliners.spaceweather;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * The rocks of every asteroid storm and giant asteroid blowing on the server, as a laser cannon shoots at them (see
 * {@code LaserCannonBlockEntity} and {@code CannonRocket}): its bolts and rockets meet a rock where it is, worked out
 * from its weather's seed as every client works it out to draw it (see {@link RockField}), or, for one on its way in to
 * strike her, from its course; each strike chips it, and a rock struck often enough bursts to pieces, every client told
 * so once (see {@link RockBurst}). A rock coming at her is knocked off its course and slowed by each strike besides,
 * and lands softer, or misses her, for it (see {@link SpaceWeather}).
 *
 * <p>Nothing is kept but the weather blowing: each adds itself as it is told and takes itself away as it is over, or
 * is let go once the ship it blew round is gone, unloaded mid-storm.
 */
public final class SpaceRocks {
    /**
     * How far past a rock's edge a shot still strikes it, in blocks: the server finds it a tick or so on from where its
     * gunner saw it, and a quick small rock moves three blocks a tick.
     */
    public static final double GRACE = 1.5;
    /** The weather blowing now, in any level. */
    private static final List<SpaceWeather> BLOWING = new ArrayList<>();
    /**
     * How many rocks this client has seen burst in the weather it shows; the client sets it, for a play-test to read
     * (see {@code BlockpilotCommands}).
     */
    public static IntSupplier clientBurstsSeen = () -> 0;

    private SpaceRocks() {
    }

    /**
     * A rock a shot meets: of which weather, its number among the weather's rocks, where the shot meets it, in the
     * world, and how far along the shot that is, in blocks.
     */
    public record Hit(SpaceWeather weather, int rock, Vec3 at, double distance) {
        /** The rock met, to home in on. */
        public Target target() {
            return new Target(weather, rock);
        }
    }

    /** A rock a cannon's rocket homes in on: of which weather, and its number among the weather's rocks. */
    public record Target(SpaceWeather weather, int rock) {
        /** Where its middle is now, in the world; null once it is gone, burst or flown past, or its weather over. */
        @Nullable
        public Vec3 centre(ServerLevel level) {
            return BLOWING.contains(weather) ? weather.rockCentre(level, rock) : null;
        }

        /** How far out from its middle a shot strikes it, in blocks, {@link #GRACE} included. */
        public double reach() {
            return weather.rockSize(rock) / 2 + GRACE;
        }
    }

    /** Weather told, whose rocks may now be shot at. */
    static void blowing(SpaceWeather weather) {
        if (!BLOWING.contains(weather)) {
            BLOWING.add(weather);
        }
    }

    /** Weather over, whose rocks are gone. */
    static void over(SpaceWeather weather) {
        BLOWING.remove(weather);
    }

    /** As the server stops: no weather blows in the next world opened. */
    public static void forget() {
        BLOWING.clear();
    }

    /**
     * The nearest rock a shot from a point along a way meets within so many blocks, of any weather blowing in the
     * level; none for none.
     */
    @Nullable
    public static Hit along(ServerLevel level, Vec3 from, Vec3 way, double reach) {
        if (BLOWING.isEmpty()) {
            return null;
        }
        BLOWING.removeIf(SpaceWeather::stale);
        Vec3 unit = way.normalize();
        Hit nearest = null;
        for (SpaceWeather weather : BLOWING) {
            Hit hit = weather.along(level, from, unit, nearest == null ? reach : nearest.distance());
            nearest = hit != null ? hit : nearest;
        }
        return nearest;
    }

    /**
     * A rock struck where a shot met it, travelling along a way, with so many bolts' worth of force: chipped, and
     * knocked off its course if it is coming at her; burst to pieces if struck often enough.
     */
    public static void shoot(ServerLevel level, Hit hit, Vec3 way, double force) {
        hit.weather().shot(level, hit.rock(), hit.at(), way.normalize(), force);
    }

    /**
     * Where a shot from a point along a way of length 1 first meets a ball round a middle so far out, within so many
     * blocks along it: how far along, or less than nothing where it misses.
     */
    public static double meets(Vec3 from, Vec3 unit, Vec3 middle, double radius, double reach) {
        Vec3 to = middle.subtract(from);
        double along = to.dot(unit);
        double off = to.lengthSqr() - along * along;
        double radiusSqr = radius * radius;
        if (off > radiusSqr) {
            return -1;
        }
        double entered = along - Math.sqrt(radiusSqr - off);
        double met = entered < 0 ? Math.max(0, along) : entered;
        return along < -radius || met > reach ? -1 : met;
    }
}
