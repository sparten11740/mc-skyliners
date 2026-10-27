package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.autopilot.Craft;
import java.util.List;
import java.util.Locale;

/**
 * An outside camera fixed to an airframe, as its passenger screens look through it, placed in the airframe's own
 * measurements: metres to starboard of the centreline, above the ground under the wheels, and back from the tip of the
 * nose, so it scales with the airframe. It looks so many degrees to the right of the nose and up from level, through a
 * lens so many degrees wide from top to bottom. One that circles the ship instead circles the place it is given, so far
 * out and so far above it, looking at it.
 */
public interface CameraSpot {
    String name();

    /** Where it is: metres to starboard, above the ground and back from the nose. */
    double across();

    double up();

    double back();

    /** Which way it looks, in degrees: right of the nose, and up from level; and its lens, top to bottom. */
    double yaw();

    double pitch();

    double fov();

    /** How far out from its place it circles the ship, in metres; nothing for a camera fixed to her. */
    default double orbitRadius() {
        return 0;
    }

    /** How far above its place it circles, in metres. */
    default double orbitRise() {
        return 0;
    }

    /** How long it takes to go round once, in seconds. */
    default double orbitSeconds() {
        return 0;
    }

    /** The key of the camera's name in the lang file. */
    default String label() {
        return "screen.aerolarge.passenger_screen.camera." + name().toLowerCase(Locale.ROOT);
    }

    /** The cameras an airframe of a kind has, in the order a passenger screen steps through them. */
    static List<CameraSpot> of(Craft craft) {
        return craft == Craft.ZEPPELIN ? List.of(ZeppelinCamera.values()) : List.of(OutsideCamera.values());
    }

    /** How many blocks a metre an airframe of a kind is built at. */
    static double scale(Craft craft) {
        return craft == Craft.ZEPPELIN ? ZeppelinSize.FULL.scale() : AerolargeConfig.SCALE.get();
    }
}
