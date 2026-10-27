package dev.aerolarge.client;

import dev.aerolarge.airframe.CameraSpot;
import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;

/**
 * Where one of an assembled airliner's outside cameras is fixed among its blocks, and the ways it faces in the
 * airliner's frame, held level in it: its place, the way it looks and its up. The airliner carries it as it flies; see
 * {@link #view} for where it is in the world as the airliner is drawn. One that circles the ship circles its place in
 * the world, so far out and so far above it, once in so many ticks of the view's own clock, looking at it, round and
 * round without a break.
 */
record CameraMount(Vec3 point, Vec3 forward, Vec3 up, double orbitRadius, double orbitRise, double orbitTicks) {
    /** The camera fixed to the airliner with this frame, built at this many blocks a metre. */
    static CameraMount on(SableClient.Frame frame, double scale, CameraSpot camera) {
        Vec3 nose = Vec3.atLowerCornerOf(frame.nose().getNormal());
        Vec3 starboard = Vec3.atLowerCornerOf(frame.nose().getClockWise().getNormal());
        Vec3 above = new Vec3(0, 1, 0);
        Vec3 point = frame.origin().add(starboard.scale(camera.across() * scale)).add(above.scale(camera.up() * scale))
            .add(nose.scale(-camera.back() * scale));
        double yaw = Math.toRadians(camera.yaw());
        double pitch = Math.toRadians(camera.pitch());
        Vec3 level = nose.scale(Math.cos(yaw)).add(starboard.scale(Math.sin(yaw)));
        Vec3 forward = level.scale(Math.cos(pitch)).add(above.scale(Math.sin(pitch)));
        Vec3 up = level.scale(-Math.sin(pitch)).add(above.scale(Math.cos(pitch)));
        return new CameraMount(point, forward, up, camera.orbitRadius() * scale, camera.orbitRise() * scale, camera.orbitSeconds() * 20);
    }

    /** The camera in the world as the airliner is drawn at this partial tick; null once no airliner holds it. */
    @Nullable
    SableClient.View view(float partialTick) {
        return view(partialTick, 0);
    }

    /**
     * The camera in the world as the airliner is drawn at this partial tick, one that circles having come so far round,
     * in ticks; null once no airliner holds it. The view's own ticks, not the game's time, which the server sets back
     * now and then, so the circle never jumps.
     */
    @Nullable
    SableClient.View view(float partialTick, double circled) {
        SableClient.View fixed = SableClient.view(point, forward, up, partialTick);
        return fixed == null || orbitTicks <= 0 ? fixed : circling(fixed.eye(), circled);
    }

    /** Circling a place in the world, round from the north having come so far round, in ticks, looking at it. */
    private SableClient.View circling(Vec3 middle, double circled) {
        double angle = circled % orbitTicks / orbitTicks * 2 * Math.PI;
        Vec3 eye = middle.add(Math.sin(angle) * orbitRadius, orbitRise, -Math.cos(angle) * orbitRadius);
        Vec3 looking = middle.subtract(eye).normalize();
        Vec3 right = looking.cross(new Vec3(0, 1, 0)).normalize();
        return new SableClient.View(eye, looking, right.cross(looking));
    }
}
