package dev.aerolarge.airframe;

import static dev.aerolarge.airframe.RocketShape.*;

import dev.aerolarge.autopilot.Craft;
import java.util.ArrayList;
import java.util.List;

/**
 * A rocket's outside cameras, which her camera display looks through (see {@link CameraSpot}), placed as the
 * zeppelin's are, from her front: back from the frontmost of her blocks (her front fins' tips), up from their feet and
 * out to the right of her front, where her door is. One low by her door looks up her side to her nose, one out on the
 * tip of her front right fin looks in under her at the ground her engines fire at, the landing camera, and the orbit
 * camera circles her from well out, as the zeppelin's flyby camera does her. Each has a colour of
 * its own, as the others' do: orange up her side, green on her leg, sky blue for the orbit.
 */
public enum RocketCamera implements CameraSpot {
    /** Two metres before her front, low by her door, looking back and up along her front to her nose in the sky. */
    SIDE(0, BASE + 2, FRONT_REACH - RADIUS - 2, 180, 62, 70, 0, 0, 0, 0xFFFF8A3C),
    /** A metre out past the tip of her front right fin, over it, looking in and down at the ground under her. */
    LEG((FIN_TIP + 1) * DIAGONAL, FIN_TIP_TOP + 1, FRONT_REACH - (FIN_TIP + 1) * DIAGONAL, -135, -35, 80, 0, 0, 0, 0xFF46E070),
    /**
     * Circling her middle once every two minutes, a hundred and ten metres out from her axis and a little above it,
     * looking at her: the whole of her in view, and not so far out that the land round it is not loaded.
     */
    ORBIT(0, HEIGHT / 2, FRONT_REACH, 0, 0, 60, 110, 10, 120, 0xFF46C8F0);

    /** Her nose's side view is cut into slices this tall, in metres. */
    private static final double SLICE = 2;
    /**
     * The rocket seen from her right side, to show where each camera is on her: quads of four corners, each a distance
     * back from her front and a height above the ground, in metres as the cameras are placed: her body, her nose slice
     * by slice, and her front and back fins as they stand out to either side between the four ways.
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

    RocketCamera(double across, double up, double back, double yaw, double pitch, double fov, double orbitRadius, double orbitRise,
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
        return Craft.ROCKET;
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
        double front = FRONT_REACH - RADIUS;
        double back = FRONT_REACH + RADIUS;
        quads.add(new double[][]{{front, BASE}, {back, BASE}, {back, NOSE_BASE}, {front, NOSE_BASE}});
        for (double h = NOSE_BASE; h < HEIGHT; h += SLICE) {
            double to = Math.min(HEIGHT, h + SLICE);
            double low = RocketShape.radius(h, BASE);
            double high = RocketShape.radius(to - 1e-6, BASE);
            quads.add(new double[][]{{FRONT_REACH - low, h}, {FRONT_REACH + low, h}, {FRONT_REACH + high, to}, {FRONT_REACH - high, to}});
        }
        for (int side = -1; side <= 1; side += 2) {
            quads.add(fin(side));
        }
        return List.copyOf(quads);
    }

    /**
     * A fin seen from her right side, behind her axis or ahead of it: its root on her body out to its foot and its tip,
     * its corners counter-clockwise either way, as the other quads' are.
     */
    private static double[][] fin(int side) {
        double root = FRONT_REACH + side * RADIUS * DIAGONAL;
        double[][] corners = {
            {root, BASE}, {FRONT_REACH + side * FIN_FOOT * DIAGONAL, 0},
            {FRONT_REACH + side * FIN_TIP * DIAGONAL, FIN_TIP_TOP}, {root, FIN_ROOT_TOP}};
        return side > 0 ? corners : new double[][]{corners[3], corners[2], corners[1], corners[0]};
    }
}
