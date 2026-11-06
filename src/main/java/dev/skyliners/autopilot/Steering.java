package dev.skyliners.autopilot;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Steering over the ground that the craft which turn on the spot share, the zeppelin and the freighter: the heading
 * from one point to another, and the heading that keeps one going round a circle and brings it back onto the circle
 * when it is off it.
 */
final class Steering {
    /**
     * Circling: how far it turns in toward the middle at most, in degrees, and how many degrees in it turns per
     * circle's radius it is off the circle, steering back onto it.
     */
    private static final double MOST_TURN_IN = 45;
    private static final double TURN_IN = 90;

    private Steering() {
    }

    /** The heading from a point to another, as Minecraft yaw (0 south, 90 west). */
    static double bearingTo(Vec3 from, double x, double z) {
        return Mth.wrapDegrees(Math.toDegrees(Math.atan2(-(x - from.x), z - from.z)));
    }

    /**
     * The heading round a circle about a middle, so wide, clockwise seen from above when going right: along it, turned
     * in toward the middle as far as it is outside the circle and out as far as it is inside, so it steers onto it.
     */
    static double round(boolean right, double middleX, double middleZ, double radius, Vec3 at) {
        double side = right ? 1 : -1;
        double toMiddle = bearingTo(at, middleX, middleZ);
        double off = Math.hypot(middleX - at.x, middleZ - at.z) - radius;
        double correction = Mth.clamp(off / radius * TURN_IN, -MOST_TURN_IN, MOST_TURN_IN);
        return Mth.wrapDegrees(toMiddle - side * (90 - correction));
    }
}
