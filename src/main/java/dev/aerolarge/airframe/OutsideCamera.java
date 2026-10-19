package dev.aerolarge.airframe;

import static dev.aerolarge.airframe.AirframeShape.*;

import java.util.Locale;

/**
 * The outside cameras the passenger screens look through, fixed to the airframe like an A380's own. Each is placed in
 * the airliner's measurements (see {@link AirframeShape}): metres to starboard of the centreline, above the ground
 * under the wheels, and back from the tip of the nose, so it scales with the airframe. It looks this many degrees to
 * the right of the nose and up from level, through a lens this many degrees wide from top to bottom.
 */
public enum OutsideCamera {
    /**
     * Four metres over the tip of the fin and five behind it, looking forward and down over the whole airliner, its
     * wingtips at the edges of the wide lens.
     */
    TAIL(0, bellyHeight() + HEIGHT + FIN_HEIGHT + 4, FIN_ROOT_LEADING_EDGE + FIN_SWEEP * FIN_HEIGHT + FIN_TIP_CHORD + 5, 0, -20, 100),
    /**
     * A metre and a half out from the right side of the fuselage, above the trailing edge of the wing root, looking
     * out and a little forward along the wing at both engines, the wingtip at the right edge.
     */
    WING(WIDTH / 2 + 1.5, bellyHeight() + 6.5, WING_ROOT_LEADING_EDGE + WING_ROOT_CHORD, 75, -15, 90),
    /** A metre under the belly, four behind the nose gear, looking ahead past its wheels and down the runway. */
    NOSE(0, bellyHeight() - 1, NOSE_GEAR_Z + 4, 0, -10, 80);

    /** Where the camera is: metres to starboard, above the ground and back from the nose. */
    public final double across;
    public final double up;
    public final double back;
    /** Which way it looks, in degrees: right of the nose, and up from level; and its lens, top to bottom. */
    public final double yaw;
    public final double pitch;
    public final double fov;

    OutsideCamera(double across, double up, double back, double yaw, double pitch, double fov) {
        this.across = across;
        this.up = up;
        this.back = back;
        this.yaw = yaw;
        this.pitch = pitch;
        this.fov = fov;
    }

    /** The key of the camera's name in the lang file. */
    public String label() {
        return "screen.aerolarge.passenger_screen.camera." + name().toLowerCase(Locale.ROOT);
    }
}
