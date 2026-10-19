package dev.aerolarge.block;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * How bright the cabin lights are set, from the flight deck's dimmer (see {@link CabinLightsBlock}): full on, dimmed
 * for take-off and landing, or the low glow of a night flight.
 */
public enum Brightness implements StringRepresentable {
    BRIGHT, DIM, NIGHT;

    public static final EnumProperty<Brightness> PROPERTY = EnumProperty.create("brightness", Brightness.class);
    /** The glow left at night, in light levels. */
    private static final int NIGHT_GLOW = 2;

    /** The light a lamp gives at this setting, from what it gives full on. */
    public int light(int full) {
        return switch (this) {
            case BRIGHT -> full;
            case DIM -> full / 2;
            case NIGHT -> Math.min(full, NIGHT_GLOW);
        };
    }

    public Brightness next() {
        return values()[(ordinal() + 1) % values().length];
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
