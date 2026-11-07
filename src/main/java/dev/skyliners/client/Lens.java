package dev.skyliners.client;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * What a view from somewhere other than the player's eyes is drawn from, through one of the airliner's outside cameras
 * or the freighter's gunner's sight: an entity of no size that is never added to the world, so nothing is drawn of it
 * and nothing it could suffer (blindness, darkness, a hurt shake) colours the view, its eyes as high as asked.
 */
final class Lens extends Marker {
    private final float eyes;

    Lens(Level level, float eyes) {
        super(EntityType.MARKER, level);
        this.eyes = eyes;
        refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(0, 0).withEyeHeight(eyes);
    }

    /**
     * Puts its eyes at a point in the world, looking a way with an up of its own, as Minecraft turns its camera: round
     * and up, not stepping between frames. Gives how far the view leans from level, in degrees, its up tilted from the
     * up of a camera held level toward its right, for the camera's roll.
     */
    float point(Vec3 eye, Vec3 forward, Vec3 up) {
        float yaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
        float pitch = (float) Math.toDegrees(-Math.asin(Mth.clamp(forward.y, -1, 1)));
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        Vec3 right = new Vec3(-Math.cos(yawRadians), 0, -Math.sin(yawRadians));
        Vec3 level = new Vec3(-Math.sin(pitchRadians) * Math.sin(yawRadians), Math.cos(pitchRadians), Math.sin(pitchRadians) * Math.cos(yawRadians));
        Vec3 feet = eye.subtract(0, getEyeHeight(), 0);
        setPos(feet);
        xo = feet.x;
        yo = feet.y;
        zo = feet.z;
        setYRot(yaw);
        yRotO = yaw;
        setXRot(pitch);
        xRotO = pitch;
        return (float) Math.toDegrees(Math.atan2(up.dot(right), up.dot(level)));
    }
}
