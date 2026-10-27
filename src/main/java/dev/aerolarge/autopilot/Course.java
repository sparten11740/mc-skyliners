package dev.aerolarge.autopilot;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Something that steers a zeppelin a while in place of what her autopilot is set to fly, such as skywriting a shape
 * across the sky (see {@link ZeppelinPilot}): asked each tick she flies level, neither taking off nor landing.
 */
@FunctionalInterface
public interface Course {
    /**
     * The heading and speed she flies this tick, from where the middle of her lift is in the world and the heading
     * she has; null to fly as she is set to.
     */
    @Nullable
    Steer steer(ServerLevel level, AutopilotBlockEntity autopilot, Vec3 middle, double heading);

    /** A heading, as Minecraft yaw in degrees (0 south, 90 west), and a speed, in blocks a second. */
    record Steer(double heading, double speed) {
    }
}
