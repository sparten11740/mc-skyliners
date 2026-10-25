package dev.aerolarge.block;

import dev.aerolarge.airframe.OutsideCamera;
import net.minecraft.world.phys.Vec3;

/**
 * A screen on the flight deck showing an outside camera's live picture, drawn on the client (see its
 * {@code TailCameraFeed}): which camera it shows, and where its face is among the airliner's blocks, so the client
 * can tell whether anyone is looking at it before drawing the picture.
 */
public interface CameraScreen {
    /** The camera it shows. */
    OutsideCamera camera();

    /** The middle of its face, among the airliner's blocks. */
    Vec3 faceMiddle();

    /** The way its face looks, among the airliner's blocks: a unit length. */
    Vec3 faceOut();

    /** How far round its middle the whole screen lies at most, in blocks, however the airliner is turned. */
    double faceReach();
}
