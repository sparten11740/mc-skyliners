package dev.aerolarge.radar;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * The pictures a transponder can be shown by on the maps, as a beacon in Subnautica is: one for a control tower, one
 * for an airliner, one for a zeppelin, and a handful for anywhere else worth finding again (home, a mine, a harbour).
 * Each is drawn white so it takes the transponder's colour (see {@link MarkerColour}). New ones only ever go on the
 * end: older versions' saves keep their pictures by name, but the network sends them by place.
 */
public enum MarkerIcon {
    TOWER, AIRLINER, HOUSE, STAR, FLAG, HEART, GEM, TREE, ANCHOR, PICKAXE, AIRSHIP;

    /** Its picture's name, under the panels' pictures: "marker/tower". */
    public String texture() {
        return "marker/" + name().toLowerCase(Locale.ROOT);
    }

    public Component title() {
        return Component.translatable("marker.aerolarge.icon." + name().toLowerCase(Locale.ROOT));
    }

    /** The one with this ordinal, or the first for one out of range (from a newer or older version, say). */
    public static MarkerIcon of(int ordinal) {
        MarkerIcon[] icons = values();
        return ordinal >= 0 && ordinal < icons.length ? icons[ordinal] : icons[0];
    }

    /** The one of this name, as saved, or the fallback for a name not known. */
    public static MarkerIcon named(String saved, MarkerIcon fallback) {
        for (MarkerIcon icon : values()) {
            if (icon.name().equals(saved)) {
                return icon;
            }
        }
        return fallback;
    }
}
