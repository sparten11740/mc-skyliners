package dev.skyliners.airframe;

import static dev.skyliners.airframe.ZeppelinShape.*;

import dev.skyliners.autopilot.Craft;
import java.util.ArrayList;
import java.util.List;

/**
 * A zeppelin's outside cameras, which her passenger screens and her camera display look through (see
 * {@link CameraSpot}): one at her bow, one under her control car looking straight down at the land she flies over, and
 * the flyby camera, slowly circling the whole ship from well out and a little above her. Each has a colour of its own,
 * as the airliner's do: orange at the bow, as by the airliner's nose gear, green under the car, and sky blue for the
 * flyby camera, which sees the whole ship as the airliner's fin camera sees it.
 */
public enum ZeppelinCamera implements CameraSpot {
    /** A metre ahead of the tip of the nose, on her axis, looking ahead and a little down. */
    BOW(0, AXIS_HEIGHT, -1, 0, -10, 80, 0, 0, 0, 0xFFFF8A3C),
    /** Half a metre under the floor of the control car's lounge, looking straight down. */
    BELOW(0, CAR_FLOOR - 0.5, (CAR_FRONT + CAR_BACK) / 2, 0, -89, 90, 0, 0, 0, 0xFF46E070),
    /**
     * Circling the middle of the ship once every two minutes, a hundred and twenty metres out from her axis and thirty
     * above it, looking at her: most of her length in view, and not so far out that the land round it is not loaded.
     */
    FLYBY(0, AXIS_HEIGHT, LENGTH / 2, 0, 0, 60, 120, 30, 120, 0xFF46C8F0);

    /** The hull's side view is cut into slices this long, in metres. */
    private static final double SLICE = 5;
    /**
     * The zeppelin seen from her right side, to show where each camera is on her: quads of four corners, each a
     * distance back from the nose and a height above the ground, in metres as the cameras are placed: the hull slice by
     * slice, the upper and lower fins, and the control car under the bow.
     */
    public static final List<double[][]> SIDE_VIEW = sideView();

    private final double across;
    private final double up;
    private final double back;
    private final double yaw;
    private final double pitch;
    private final double fov;
    private final double orbitRadius;
    private final double orbitRise;
    private final double orbitSeconds;
    private final int colour;

    ZeppelinCamera(double across, double up, double back, double yaw, double pitch, double fov, double orbitRadius, double orbitRise,
                   double orbitSeconds, int colour) {
        this.across = across;
        this.up = up;
        this.back = back;
        this.yaw = yaw;
        this.pitch = pitch;
        this.fov = fov;
        this.orbitRadius = orbitRadius;
        this.orbitRise = orbitRise;
        this.orbitSeconds = orbitSeconds;
        this.colour = colour;
    }

    @Override
    public Craft craft() {
        return Craft.ZEPPELIN;
    }

    @Override
    public int colour() {
        return colour;
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

    @Override
    public double orbitRadius() {
        return orbitRadius;
    }

    @Override
    public double orbitRise() {
        return orbitRise;
    }

    @Override
    public double orbitSeconds() {
        return orbitSeconds;
    }

    private static List<double[][]> sideView() {
        List<double[][]> quads = new ArrayList<>();
        for (double z = 0; z < LENGTH; z += SLICE) {
            double to = Math.min(LENGTH, z + SLICE);
            quads.add(new double[][]{
                {z, AXIS_HEIGHT - radius(z)}, {to, AXIS_HEIGHT - radius(to)}, {to, AXIS_HEIGHT + radius(to)}, {z, AXIS_HEIGHT + radius(z)}});
        }
        double tip = AXIS_HEIGHT + finReach(FIN_TIP_FRONT);
        quads.add(new double[][]{
            {FIN_ROOT_FRONT, AXIS_HEIGHT + radius(FIN_ROOT_FRONT)}, {FIN_ROOT_BACK, AXIS_HEIGHT + radius(FIN_ROOT_BACK)},
            {FIN_TIP_BACK, tip}, {FIN_TIP_FRONT, tip}});
        double low = AXIS_HEIGHT - finReach(FIN_TIP_FRONT);
        quads.add(new double[][]{
            {FIN_TIP_FRONT, low}, {FIN_TIP_BACK, low},
            {FIN_ROOT_BACK, AXIS_HEIGHT - radius(FIN_ROOT_BACK)}, {FIN_ROOT_FRONT, AXIS_HEIGHT - radius(FIN_ROOT_FRONT)}});
        quads.add(new double[][]{
            {CAR_FRONT, CAR_FLOOR}, {CAR_BACK, CAR_FLOOR}, {CAR_BACK, belly(CAR_BACK, AXIS_HEIGHT)}, {CAR_FRONT, belly(CAR_FRONT, AXIS_HEIGHT)}});
        return List.copyOf(quads);
    }
}
