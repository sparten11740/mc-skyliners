package dev.aerolarge.client;

import dev.aerolarge.airframe.OutsideCamera;
import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

/**
 * Where one of an assembled airliner's outside cameras is fixed among its blocks, and the ways it faces in the
 * airliner's frame, held level in it: its place, the way it looks and its up. The airliner carries it as it flies; see
 * {@link #view} for where it is in the world as the airliner is drawn.
 */
record CameraMount(Vec3 point, Vec3 forward, Vec3 up) {
    /** The camera fixed to the airliner with this frame, built at this many blocks a metre. */
    static CameraMount on(SableClient.Frame frame, double scale, OutsideCamera camera) {
        Vec3 nose = Vec3.atLowerCornerOf(frame.nose().getNormal());
        Vec3 starboard = Vec3.atLowerCornerOf(frame.nose().getClockWise().getNormal());
        Vec3 above = new Vec3(0, 1, 0);
        Vec3 point = frame.origin().add(starboard.scale(camera.across * scale)).add(above.scale(camera.up * scale)).add(nose.scale(-camera.back * scale));
        double yaw = Math.toRadians(camera.yaw);
        double pitch = Math.toRadians(camera.pitch);
        Vec3 level = nose.scale(Math.cos(yaw)).add(starboard.scale(Math.sin(yaw)));
        Vec3 forward = level.scale(Math.cos(pitch)).add(above.scale(Math.sin(pitch)));
        Vec3 up = level.scale(-Math.sin(pitch)).add(above.scale(Math.cos(pitch)));
        return new CameraMount(point, forward, up);
    }

    /** The camera in the world as the airliner is drawn at this partial tick; null once no airliner holds it. */
    @Nullable
    SableClient.View view(float partialTick) {
        return SableClient.view(point, forward, up, partialTick);
    }
}
