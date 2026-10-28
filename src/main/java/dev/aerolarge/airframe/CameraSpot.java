package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.autopilot.Craft;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * An outside camera fixed to an airframe, as its passenger screens and camera displays look through it, placed in the
 * airframe's own measurements: metres to starboard of the centreline, above the ground under the wheels, and back from
 * the tip of the nose, so it scales with the airframe. It looks so many degrees to the right of the nose and up from
 * level, through a lens so many degrees wide from top to bottom. One that circles the ship instead circles the place it
 * is given, so far out and so far above it, looking at it. Each is its airframe's, with a colour of its own on the
 * displays' buttons and labels.
 */
public interface CameraSpot {
    String name();

    /** The kind of airframe it is fixed to, whose other cameras a display showing it picks among. */
    Craft craft();

    /** Its own colour, as ARGB: its dot on a display's button and its camera in the picture's label. */
    int colour();

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

    /** Its airframe's cameras, itself among them, in order: as a display's buttons lay them out. */
    default List<CameraSpot> alongside() {
        return of(craft());
    }

    /** The cameras an airframe of a kind has, in the order a passenger screen steps through them. */
    static List<CameraSpot> of(Craft craft) {
        return craft == Craft.ZEPPELIN ? List.of(ZeppelinCamera.values()) : List.of(OutsideCamera.values());
    }

    /**
     * An airframe of a kind seen from its right side, to show where each camera is on it: quads of four corners, each
     * a distance back from the nose and a height above the ground, in metres as the cameras are placed.
     */
    static List<double[][]> sideView(Craft craft) {
        return craft == Craft.ZEPPELIN ? ZeppelinCamera.SIDE_VIEW : OutsideCamera.SIDE_VIEW;
    }

    /** The camera of this name, of whichever airframe it is, or the fallback for a name no camera has. */
    static CameraSpot named(String name, CameraSpot fallback) {
        return Stream.of(Craft.values()).flatMap(craft -> of(craft).stream()).filter(camera -> camera.name().equals(name)).findFirst()
            .orElse(fallback);
    }

    /** How many blocks a metre an airframe of a kind is built at, a zeppelin's hull at its size. */
    static double scale(Craft craft, ZeppelinSize size) {
        return craft == Craft.ZEPPELIN ? size.scale() : AerolargeConfig.SCALE.get();
    }
}
