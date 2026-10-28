package dev.aerolarge.block;

/**
 * The ten colours the rocket's LED strips are set to (see {@link LedStripBlock}), each shown on the panels as a swatch
 * of itself rather than a name, so a player who cannot read picks one by its look. The strips take it as a tint, as her
 * cover takes her livery, so no block changes when it does. Each has a partner, a colour that stands out against it,
 * which the animated modes bring in beside it: the techno club's pink pulses with ocean blue.
 */
public enum LedColour {
    CHERRY(0xFF2D3C),
    TANGERINE(0xFF8A1F),
    SUNSHINE(0xFFD93B),
    LIME(0x7CFF4F),
    MINT(0x2EE6A0),
    SKY(0x3CC8FF),
    OCEAN(0x3A6BFF),
    GRAPE(0xA24BFF),
    PINK(0xFF5FC8),
    SNOW(0xF4F8FF);

    private final int rgb;

    LedColour(int rgb) {
        this.rgb = rgb;
    }

    /** The colour as RGB, as the strip glows in it full on. */
    public int rgb() {
        return rgb;
    }

    /** The colour the club and the chase bring in beside this one: one that stands out against it. */
    public LedColour partner() {
        return switch (this) {
            case CHERRY, SNOW -> SKY;
            case TANGERINE, MINT -> GRAPE;
            case SUNSHINE, PINK -> OCEAN;
            case LIME, OCEAN -> PINK;
            case SKY -> CHERRY;
            case GRAPE -> MINT;
        };
    }

    /** The colour saved under a name, or the one given where the name is none of them. */
    public static LedColour byName(String name, LedColour fallback) {
        for (LedColour colour : values()) {
            if (colour.name().equals(name)) {
                return colour;
            }
        }
        return fallback;
    }
}
