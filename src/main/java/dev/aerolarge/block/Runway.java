package dev.aerolarge.block;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A strip of land the autopilot has picked to land on near the pin: where the airliner is to touch down (the point
 * under the autopilot, in world blocks), the way it lands (as Minecraft yaw), the height of the ground there (the
 * first air over it), and how far it rolls on from there while it brakes to a stop. Distances along it are measured
 * from the touchdown point the way it lands, negative before it; distances across it from its centre line, to the
 * right.
 */
public record Runway(double x, double z, double heading, int elevation, double rollout) {
    /** The way along the runway in the world, east and south. */
    double alongX() {
        return -Math.sin(Math.toRadians(heading));
    }

    double alongZ() {
        return Math.cos(Math.toRadians(heading));
    }

    /** To the right of the way it lands: Minecraft's z runs south, so right of north is east. */
    double rightX() {
        return -alongZ();
    }

    double rightZ() {
        return alongX();
    }

    /** The world x and z of the column a distance along and across the runway. */
    int columnX(double along, double across) {
        return Mth.floor(x + along * alongX() + across * rightX());
    }

    int columnZ(double along, double across) {
        return Mth.floor(z + along * alongZ() + across * rightZ());
    }

    /** How far along the runway a point in the world is: negative before the touchdown point. */
    double along(Vec3 point) {
        return (point.x - x) * alongX() + (point.z - z) * alongZ();
    }

    /** How far right of the centre line a point in the world is. */
    double across(Vec3 point) {
        return (point.x - x) * rightX() + (point.z - z) * rightZ();
    }

    /** Where in the world a point a distance along and across the runway is, at no particular height. */
    Vec3 point(double along, double across) {
        return new Vec3(x + along * alongX() + across * rightX(), 0, z + along * alongZ() + across * rightZ());
    }

    /**
     * Where the airliner may touch down at the earliest, and where it has stopped by at the latest: the ends of the
     * strip it lands on, as the autopilot's map draws it.
     */
    public Vec3 start() {
        return point(-RunwayCheck.TOUCHDOWN_SCATTER, 0);
    }

    public Vec3 end() {
        return point(RunwayCheck.TOUCHDOWN_SCATTER + rollout, 0);
    }

    /** Writes it under a key of a tag, as the autopilot saves and sends it. */
    void save(CompoundTag tag, String key) {
        CompoundTag runway = new CompoundTag();
        runway.putDouble("X", x);
        runway.putDouble("Z", z);
        runway.putDouble("Heading", heading);
        runway.putInt("Elevation", elevation);
        runway.putDouble("Rollout", rollout);
        tag.put(key, runway);
    }

    /** The runway written under a key of a tag; null for none. */
    @Nullable
    static Runway load(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return null;
        }
        CompoundTag runway = tag.getCompound(key);
        return new Runway(runway.getDouble("X"), runway.getDouble("Z"), runway.getDouble("Heading"), runway.getInt("Elevation"),
            runway.getDouble("Rollout"));
    }
}
