package dev.skyliners.airframe;

import dev.skyliners.autopilot.Craft;

/**
 * The freighter's outside cameras, which her passenger screens look through (see {@link CameraSpot}), placed as the
 * other ships' are: back from the frontmost of her blocks (her prongs' tips), up from her feet and out to the right of
 * her autopilot's control head in her cockpit, which is well out to her right. One under the middle of her belly looks
 * straight down at the ground passing under her, as her floor window does; one in the gap between her prongs looks out
 * ahead; one astern of her and above looks forward over her top; and one circles her from well out, as the rocket's
 * orbit camera does her. Each has a colour of its own, as the others' do: green under her, orange between her prongs,
 * violet astern and sky blue for the one circling.
 */
public enum FreighterCamera implements CameraSpot {
    /** Just under the middle of her belly, looking straight down at the ground under her. */
    UNDERSIDE(-Frame.HEAD_OUT, Frame.UNDER_BELLY, Frame.TIP_BACK, 0, -89, 80, 0, 0, 0, 0xFF46E070),
    /** At the back of the gap between her prongs, halfway up her side, looking out ahead. */
    PRONGS(-Frame.HEAD_OUT, FreighterShape.MIDDLE, Frame.GAP_BACK, 0, -5, 80, 0, 0, 0, 0xFFFF8A3C),
    /** Astern of her and above her top, looking forward and down over her top dome toward her prongs. */
    ASTERN(-Frame.HEAD_OUT, Frame.ASTERN_UP, Frame.ASTERN_BACK, 0, -18, 75, 0, 0, 0, 0xFFB46CF0),
    /**
     * Circling her middle once every two minutes, seventy metres out from her axis and a little above her, looking at
     * her: the whole of her in view, and not so far out that the land round it is not loaded.
     */
    CIRCLING(-Frame.HEAD_OUT, FreighterShape.MIDDLE, Frame.TIP_BACK, 0, 0, 60, 70, 12, 120, 0xFF46C8F0);

    /**
     * Where her cameras are measured from, in metres: how far out to her right of her axis her autopilot's control head
     * stands, the middle of its block; how far back from her prongs' tips, the front of her frontmost block, her axis
     * is; how far over her feet the camera under her belly is, just under its dome; how far back the camera between her
     * prongs is, half a metre ahead of the back of the gap between them; and how far up and how far back the one astern
     * of her is, eight metres astern of her stern.
     */
    private static final class Frame {
        static final double HEAD_OUT = (Math.round(FreighterShape.TUBE_OUT * FreighterLayout.SCALE) + 0.5) / FreighterLayout.SCALE;
        static final double TIP_BACK = -(Math.floor(FreighterShape.PRONG_TIP * FreighterLayout.SCALE) - 1) / FreighterLayout.SCALE;
        static final double UNDER_BELLY = FreighterShape.BELLY_DOME_FOOT - 0.3;
        static final double GAP_BACK = TIP_BACK + FreighterShape.GAP_BACK - 0.5;
        static final double ASTERN_UP = FreighterShape.TOP_DOME_CROWN + 3;
        static final double ASTERN_BACK = TIP_BACK + FreighterShape.RADIUS + 8;

        private Frame() {
        }
    }

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

    FreighterCamera(double across, double up, double back, double yaw, double pitch, double fov, double orbitRadius, double orbitRise,
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
        return Craft.FREIGHTER;
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
}
