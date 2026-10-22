package dev.aerolarge.airport;

import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * The sky over a place, as an airfield's weather report gives it: clear, rain, snow where the biome is cold enough,
 * or a thunderstorm; where it never rains, as in a desert, the sky stays clear whatever the level's weather. Each comes
 * with how far one sees through it, in metres. Both sides work it out the same from the weather they both know.
 */
public enum Sky {
    CLEAR(10_000), RAIN(4_000), SNOW(1_500), THUNDER(1_200);

    /** How far one sees through it, in metres. */
    private final int visibility;

    Sky(int visibility) {
        this.visibility = visibility;
    }

    public int visibility() {
        return visibility;
    }

    /** Its word on a weather report: "RAIN". */
    public Component title() {
        return Component.translatable("weather.aerolarge." + name().toLowerCase(Locale.ROOT));
    }

    /** The sky over a position now. */
    public static Sky at(Level level, BlockPos pos) {
        if (!level.isRaining()) {
            return CLEAR;
        }
        Biome.Precipitation falling = level.getBiome(pos).value().getPrecipitationAt(pos);
        if (falling == Biome.Precipitation.NONE) {
            return CLEAR;
        }
        if (level.isThundering()) {
            return THUNDER;
        }
        return falling == Biome.Precipitation.SNOW ? SNOW : RAIN;
    }

    /** The one of this name, as saved, or none for a name not known (nothing saved yet, say). */
    @Nullable
    public static Sky named(String saved) {
        for (Sky sky : values()) {
            if (sky.name().equals(saved)) {
                return sky;
            }
        }
        return null;
    }
}
