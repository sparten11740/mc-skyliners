package dev.aerolarge.airframe;

import static dev.aerolarge.airframe.AirframeShape.*;

import java.util.ArrayList;
import java.util.List;

/**
 * The outside cameras the passenger screens and the flight deck's camera display look through, fixed to the airframe
 * like an A380's own. Each is placed in the airliner's measurements (see {@link AirframeShape}): metres to starboard of
 * the centreline, above the ground under the wheels, and back from the tip of the nose, so it scales with the airframe.
 * It looks this many degrees to the right of the nose and up from level, through a lens this many degrees wide from top
 * to bottom.
 */
public enum OutsideCamera implements CameraSpot {
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
    NOSE(0, bellyHeight() - 1, NOSE_GEAR_Z + 4, 0, -10, 80),
    /**
     * Right in front of the flight deck, at the tip of the nose and level with the pilots' eyes, looking ahead and
     * fifteen degrees down at what lies before the airliner: the runway coming up to meet it as it lands.
     */
    FRONT(0, bellyHeight() + Windscreen.EYES, 0, 0, -15, 80);

    /** The fuselage's side view is cut into slices this long, in metres. */
    private static final double SLICE = 2;
    /**
     * The airliner seen from its right side, to show where each camera is on it: quads of four corners, each a distance
     * back from the nose and a height above the ground, in metres as the cameras are placed: the fuselage slice by slice,
     * the wing with an engine under it, and the fin.
     */
    public static final List<double[][]> SIDE_VIEW = sideView();

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

    @Override
    public double across() {
        return across;
    }

    @Override
    public double up() {
        return up;
    }

    @Override
    public double back() {
        return back;
    }

    @Override
    public double yaw() {
        return yaw;
    }

    @Override
    public double pitch() {
        return pitch;
    }

    @Override
    public double fov() {
        return fov;
    }

    /** The camera of this name, or the fallback for a name no camera has. */
    public static OutsideCamera named(String name, OutsideCamera fallback) {
        for (OutsideCamera camera : values()) {
            if (camera.name().equals(name)) {
                return camera;
            }
        }
        return fallback;
    }

    private static List<double[][]> sideView() {
        List<double[][]> quads = new ArrayList<>();
        double belly = bellyHeight();
        double wingTip = WING_ROOT_LEADING_EDGE + WING_SWEEP * WING_HALF_SPAN;
        quads.add(new double[][]{
            {WING_ROOT_LEADING_EDGE, belly + wingY(0)}, {WING_ROOT_LEADING_EDGE + WING_ROOT_CHORD, belly + wingY(0)},
            {wingTip + WING_TIP_CHORD, belly + wingY(WING_HALF_SPAN)}, {wingTip, belly + wingY(WING_HALF_SPAN)}});
        double engineFront = nacelleFront(ENGINE_X[0]);
        double engine = belly + nacelleY(ENGINE_X[0]);
        quads.add(new double[][]{
            {engineFront, engine - NACELLE_RADIUS}, {engineFront + NACELLE_LENGTH, engine - NACELLE_RADIUS},
            {engineFront + NACELLE_LENGTH, engine + NACELLE_RADIUS}, {engineFront, engine + NACELLE_RADIUS}});
        for (double z = 0; z < LENGTH; z += SLICE) {
            quads.add(slice(z, Math.min(LENGTH, z + SLICE), belly));
        }
        double top = belly + HEIGHT;
        double tip = FIN_ROOT_LEADING_EDGE + FIN_SWEEP * FIN_HEIGHT;
        quads.add(new double[][]{
            {FIN_ROOT_LEADING_EDGE, top}, {FIN_ROOT_LEADING_EDGE + FIN_ROOT_CHORD, top},
            {tip + FIN_TIP_CHORD, top + FIN_HEIGHT}, {tip, top + FIN_HEIGHT}});
        return List.copyOf(quads);
    }

    /** A slice of the fuselage seen from the side, between two stations, from its belly to its crown. */
    private static double[][] slice(double from, double to, double belly) {
        Section front = section(from);
        Section rear = section(to);
        return new double[][]{
            {from, belly + front.bottom()}, {to, belly + rear.bottom()}, {to, belly + rear.top()}, {from, belly + front.top()}};
    }
}
