package dev.aerolarge.block;

/**
 * How a story's LED strips light (see {@link LedStripBlock}): steady, or one of the animated modes, which the client
 * draws over the strips as they stand, timed by the game clock so every strip and every player agrees. None of them
 * ever flashes faster than three times a second, for players whom flashing light can harm.
 */
public enum LedMode {
    /** Lit evenly in its colour. */
    STEADY,
    /** Brightening and dimming again, in steps, a slow breath every few seconds. */
    BREATHE,
    /** Bands of its colour running round the story, the ceiling's against the floor's. */
    CHASE,
    /** Pulsing on the techno club's beat, its colour and its partner taking turns. */
    CLUB;

    /** Whether the client draws it moving over the strips, rather than as they are meshed. */
    public boolean animated() {
        return this != STEADY;
    }

    /** The mode saved under a name, or the one given where the name is none of them. */
    public static LedMode byName(String name, LedMode fallback) {
        for (LedMode mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return fallback;
    }
}
