package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

/**
 * A cabin announcement the flight deck makes over the PA (see {@link CabinAnnouncements}), in the order of a flight,
 * as the announcement panel's tiles show them: welcome aboard, take-off, bumpy air, drinks and snacks, the view out of
 * the window, the pool and the gym open, the lights dimmed for a sleep, landing and arrival. The autopilot makes the take-off, landing and arrival ones itself as it flies; the others only the panel.
 * Some work the cabin too, as a crew would: the seatbelt signs on for take-off, bumpy air and landing and off for the
 * pool and the gym and on arrival, and the cabin lights down to their night glow for a sleep.
 */
public enum Announcement {
    WELCOME(AerolargeRegistry.ANNOUNCEMENT_WELCOME, null, null),
    TAKEOFF(AerolargeRegistry.ANNOUNCEMENT_TAKEOFF, true, null),
    TURBULENCE(AerolargeRegistry.ANNOUNCEMENT_TURBULENCE, true, null),
    SNACKS(AerolargeRegistry.ANNOUNCEMENT_SNACKS, null, null),
    WINDOW(AerolargeRegistry.ANNOUNCEMENT_WINDOW, null, null),
    PLAYTIME(AerolargeRegistry.ANNOUNCEMENT_PLAYTIME, false, null),
    SLEEP(AerolargeRegistry.ANNOUNCEMENT_SLEEP, null, Brightness.NIGHT),
    LANDING(AerolargeRegistry.ANNOUNCEMENT_LANDING, true, null),
    ARRIVAL(AerolargeRegistry.ANNOUNCEMENT_ARRIVAL, false, null);

    final Holder<SoundEvent> sound;
    /** Whether it switches the seatbelt signs on or off; null leaves them as they are. */
    @Nullable
    final Boolean seatbelts;
    /** What it sets the cabin lights to; null leaves them as they are. */
    @Nullable
    final Brightness lights;

    Announcement(Holder<SoundEvent> sound, @Nullable Boolean seatbelts, @Nullable Brightness lights) {
        this.sound = sound;
        this.seatbelts = seatbelts;
        this.lights = lights;
    }
}
