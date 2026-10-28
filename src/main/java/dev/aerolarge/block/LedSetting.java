package dev.aerolarge.block;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

/**
 * What a story's LED strips are set to (see {@link LedStripBlock}): their colour, how bright they are, from off
 * ({@code 0}) to full ({@link LedStripBlock#BRIGHTEST}), and how they light. The brightness is also each strip's own
 * block state, for the light it gives; the colour and the mode are drawn by the client.
 */
public record LedSetting(LedColour colour, int brightness, LedMode mode) {
    /** A story's strips as they are built unless its room has its own: snow white, lit to two thirds, steady. */
    public static final LedSetting PLAIN = new LedSetting(LedColour.SNOW, 2, LedMode.STEADY);

    public LedSetting {
        brightness = Mth.clamp(brightness, 0, LedStripBlock.BRIGHTEST);
    }

    public LedSetting withColour(LedColour colour) {
        return new LedSetting(colour, brightness, mode);
    }

    public LedSetting withBrightness(int brightness) {
        return new LedSetting(colour, brightness, mode);
    }

    public LedSetting withMode(LedMode mode) {
        return new LedSetting(colour, brightness, mode);
    }

    /** The setting as saved: its colour and mode by name. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Colour", colour.name());
        tag.putInt("Brightness", brightness);
        tag.putString("Mode", mode.name());
        return tag;
    }

    /** A setting as {@link #save} saved it; what it does not say is taken from the fallback. */
    public static LedSetting load(CompoundTag tag, LedSetting fallback) {
        LedColour colour = LedColour.byName(tag.getString("Colour"), fallback.colour());
        int brightness = tag.contains("Brightness", Tag.TAG_INT) ? tag.getInt("Brightness") : fallback.brightness();
        return new LedSetting(colour, brightness, LedMode.byName(tag.getString("Mode"), fallback.mode()));
    }
}
