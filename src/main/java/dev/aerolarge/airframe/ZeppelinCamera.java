package dev.aerolarge.airframe;

import static dev.aerolarge.airframe.ZeppelinShape.*;

/**
 * A zeppelin's outside cameras, which her passenger screens look through (see {@link CameraSpot}): one at her bow,
 * one under her control car looking straight down at the land she flies over, and the flyby camera, slowly circling
 * the whole ship from well out and a little above her.
 */
public enum ZeppelinCamera implements CameraSpot {
    /** A metre ahead of the tip of the nose, on her axis, looking ahead and a little down. */
    BOW(0, AXIS_HEIGHT, -1, 0, -10, 80, 0, 0, 0),
    /** Half a metre under the floor of the control car's lounge, looking straight down. */
    BELOW(0, CAR_FLOOR - 0.5, (CAR_FRONT + CAR_BACK) / 2, 0, -89, 90, 0, 0, 0),
    /**
     * Circling the middle of the ship once every two minutes, a hundred and twenty metres out from her axis and thirty
     * above it, looking at her: most of her length in view, and not so far out that the land round it is not loaded.
     */
    FLYBY(0, AXIS_HEIGHT, LENGTH / 2, 0, 0, 60, 120, 30, 120);

    private final double across;
    private final double up;
    private final double back;
    private final double yaw;
    private final double pitch;
    private final double fov;
    private final double orbitRadius;
    private final double orbitRise;
    private final double orbitSeconds;

    ZeppelinCamera(double across, double up, double back, double yaw, double pitch, double fov, double orbitRadius, double orbitRise,
                   double orbitSeconds) {
        this.across = across;
        this.up = up;
        this.back = back;
        this.yaw = yaw;
        this.pitch = pitch;
        this.fov = fov;
        this.orbitRadius = orbitRadius;
        this.orbitRise = orbitRise;
        this.orbitSeconds = orbitSeconds;
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
}
