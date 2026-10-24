package dev.aerolarge.cabin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

/**
 * The weather an airliner flies through, and what it makes of rough air (see {@link Turbulence}): how many times as
 * often rough air comes as in clear skies, how many times as hard it shakes and throws, and how many times as long a
 * patch of it lasts. Air pockets and lightning go by it too (see {@link AirPockets} and {@link LightningStrikes}).
 */
enum Weather {
    CLEAR(1, 1, 1),
    RAIN(Turbulence.RAIN_ONSET, Turbulence.RAIN_STRENGTH, Turbulence.RAIN_LENGTH),
    STORM(Turbulence.STORM_ONSET, Turbulence.STORM_STRENGTH, Turbulence.STORM_LENGTH);

    final double onset;
    final float strength;
    final double length;

    Weather(double onset, float strength, double length) {
        this.onset = onset;
        this.strength = strength;
        this.length = length;
    }

    /**
     * The weather at a point: rain or a thunderstorm only where something falls there, so a storm passing over a desert
     * brings no more than clear skies. Read from the biome rather than {@code isRainingAt}, so snow (which falls high up
     * over cooler land) counts as much as rain; an airliner in flight is out under the sky anyway.
     */
    static Weather at(ServerLevel level, Vec3 point) {
        BlockPos at = BlockPos.containing(point);
        if (!level.isRaining() || level.getBiome(at).value().getPrecipitationAt(at) == Biome.Precipitation.NONE) {
            return CLEAR;
        }
        return level.isThundering() ? STORM : RAIN;
    }
}
