package dev.skyliners.radar;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** The colours a transponder can be shown in on the maps: bright ones, that stand out over any land and over water. */
public enum MarkerColour {
    WHITE(0xF2F2F2), RED(0xE8382C), ORANGE(0xF28C28), YELLOW(0xF2D230), LIME(0x8CE030), GREEN(0x2EB84A),
    CYAN(0x46C8F0), BLUE(0x3C64E6), PURPLE(0x9646DC), MAGENTA(0xE040E0), PINK(0xF08CC0), BROWN(0x9A6A3C);

    /** Its colour, as 0xRRGGBB. */
    private final int rgb;

    MarkerColour(int rgb) {
        this.rgb = rgb;
    }

    public int rgb() {
        return rgb;
    }

    /** Its colour, opaque: 0xFFRRGGBB. */
    public int argb() {
        return 0xFF000000 | rgb;
    }

    public Component title() {
        return Component.translatable("marker.skyliners.colour." + name().toLowerCase(Locale.ROOT));
    }

    /** The one with this ordinal, or the first for one out of range (from a newer or older version, say). */
    public static MarkerColour of(int ordinal) {
        MarkerColour[] colours = values();
        return ordinal >= 0 && ordinal < colours.length ? colours[ordinal] : colours[0];
    }

    /** The one of this name, as saved, or the fallback for a name not known. */
    public static MarkerColour named(String saved, MarkerColour fallback) {
        for (MarkerColour colour : values()) {
            if (colour.name().equals(saved)) {
                return colour;
            }
        }
        return fallback;
    }
}
