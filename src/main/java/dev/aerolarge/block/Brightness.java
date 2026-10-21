package dev.aerolarge.block;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * How bright the cabin lights are set, from the flight deck's dimmer (see {@link CabinLightsBlock}): full on, dimmed
 * for take-off and landing, or the low glow of a night flight. Every light the dimmer sets also has {@link #ALARM}.
 */
public enum Brightness implements StringRepresentable {
    BRIGHT, DIM, NIGHT;

    public static final EnumProperty<Brightness> PROPERTY = EnumProperty.create("brightness", Brightness.class);
    /**
     * Whether the light glows red, full on, for a door standing open in flight (see {@code Decompression}), whatever
     * the dimmer has it at; the dimmer sets it on its lights as it sets their brightness, and on itself.
     */
    public static final BooleanProperty ALARM = BooleanProperty.create("alarm");
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

    /** The light a lamp in this state gives, from what it gives full on: full on while it glows red for the alarm. */
    public static int light(BlockState state, int full) {
        return alarm(state) ? full : state.getValue(PROPERTY).light(full);
    }

    /** Whether the state is a light glowing red for the alarm. */
    public static boolean alarm(BlockState state) {
        return state.hasProperty(ALARM) && state.getValue(ALARM);
    }

    public Brightness next() {
        return values()[(ordinal() + 1) % values().length];
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
