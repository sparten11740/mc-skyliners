package dev.skyliners.autopilot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;

/**
 * How long one of the mod's own spoken sounds runs, in ticks, read once from its Ogg Vorbis file among the mod's
 * resources (its file named as the sound is, as all of them are): the sample its last page ends on, over the sample
 * rate its header gives. The flight deck's voice holds the floor for as long as each one really runs (see
 * {@link Airtime}), so whatever waits for it waits no longer than it must, on a dedicated server too.
 */
public final class VoiceLength {
    /** An Ogg page's capture pattern, and the start of a Vorbis stream's identification header. */
    private static final byte[] PAGE = "OggS".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] IDENTIFICATION = "\u0001vorbis".getBytes(StandardCharsets.US_ASCII);
    /** Where the sample rate lies in the identification header, and where the sample position lies in a page's. */
    private static final int RATE_AT = 12;
    private static final int POSITION_AT = 6;
    private static final int UNKNOWN = -1;
    private static final Map<ResourceLocation, Integer> LENGTHS = new ConcurrentHashMap<>();

    private VoiceLength() {
    }

    /** How long a sound runs, in ticks, rounded up; the fallback for one whose file cannot be read. */
    public static int ticks(Holder<SoundEvent> sound, int fallback) {
        int ticks = LENGTHS.computeIfAbsent(sound.value().getLocation(), VoiceLength::read);
        return ticks == UNKNOWN ? fallback : ticks;
    }

    private static int read(ResourceLocation sound) {
        IModFileInfo mod = ModList.get().getModFileById(sound.getNamespace());
        if (mod == null) {
            return UNKNOWN;
        }
        Path file = mod.getFile().findResource("assets", sound.getNamespace(), "sounds", sound.getPath() + ".ogg");
        try {
            return length(Files.readAllBytes(file));
        } catch (IOException | RuntimeException unreadable) {
            return UNKNOWN;
        }
    }

    /** How long Ogg Vorbis data runs, in ticks, rounded up; {@link #UNKNOWN} for data that is not that. */
    static int length(byte[] ogg) {
        int header = find(ogg, IDENTIFICATION, 0);
        int last = -1;
        for (int page = find(ogg, PAGE, 0); page >= 0; page = find(ogg, PAGE, page + 1)) {
            last = page;
        }
        if (header < 0 || last < 0 || header + RATE_AT + 4 > ogg.length || last + POSITION_AT + 8 > ogg.length) {
            return UNKNOWN;
        }
        long rate = littleEndian(ogg, header + RATE_AT, 4);
        long samples = littleEndian(ogg, last + POSITION_AT, 8);
        return rate > 0 && samples > 0 ? (int) Math.ceil(samples * 20.0 / rate) : UNKNOWN;
    }

    /** Where a run of bytes first comes in data from a place on, or -1 for nowhere. */
    private static int find(byte[] data, byte[] run, int from) {
        for (int at = from; at + run.length <= data.length; at++) {
            if (startsAt(data, run, at)) {
                return at;
            }
        }
        return -1;
    }

    private static boolean startsAt(byte[] data, byte[] run, int at) {
        for (int k = 0; k < run.length; k++) {
            if (data[at + k] != run[k]) {
                return false;
            }
        }
        return true;
    }

    /** A whole number of so many bytes in data, least significant first. */
    private static long littleEndian(byte[] data, int at, int bytes) {
        long value = 0;
        for (int k = bytes - 1; k >= 0; k--) {
            value = value << 8 | data[at + k] & 0xFF;
        }
        return value;
    }
}
