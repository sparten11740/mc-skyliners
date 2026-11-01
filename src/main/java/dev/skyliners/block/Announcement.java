package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.CabinAnnouncements;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

/**
 * A cabin announcement the flight deck makes over the PA (see {@link CabinAnnouncements}), in the order of a flight,
 * as the announcement panel's tiles show them (see {@link AnnouncementPanelBlock}): welcome aboard, take-off, bumpy
 * air, drinks and snacks, the view out of the window, the pool and the gym open, the lights dimmed for a sleep, landing
 * and arrival. The autopilot makes the take-off, landing and arrival ones itself as it flies; the others only the panel.
 * Some work the cabin too, as a crew would: the seatbelt signs on for take-off, bumpy air and landing and off for the
 * pool and the gym and on arrival, and the cabin lights down to their night glow for a sleep.
 */
public enum Announcement {
    WELCOME(SkylinersRegistry.ANNOUNCEMENT_WELCOME, null, null),
    TAKEOFF(SkylinersRegistry.ANNOUNCEMENT_TAKEOFF, true, null),
    TURBULENCE(SkylinersRegistry.ANNOUNCEMENT_TURBULENCE, true, null),
    SNACKS(SkylinersRegistry.ANNOUNCEMENT_SNACKS, null, null),
    WINDOW(SkylinersRegistry.ANNOUNCEMENT_WINDOW, null, null),
    PLAYTIME(SkylinersRegistry.ANNOUNCEMENT_PLAYTIME, false, null),
    SLEEP(SkylinersRegistry.ANNOUNCEMENT_SLEEP, null, Brightness.NIGHT),
    LANDING(SkylinersRegistry.ANNOUNCEMENT_LANDING, true, null),
    ARRIVAL(SkylinersRegistry.ANNOUNCEMENT_ARRIVAL, false, null);

    /**
     * Where an announcement stands, as the panel shows it: ready to be made; being made; waiting for what is being said
     * on the flight deck to end; hushed while another one is made or waits; or resting, made a moment ago and not
     * again so soon (see {@link CabinAnnouncements}). Only a ready one is made when its tile is pressed.
     */
    public enum Standing {
        READY, ON_AIR, WAITING, HUSHED, RESTING
    }

    public final Holder<SoundEvent> sound;
    /** Whether it switches the seatbelt signs on or off; null leaves them as they are. */
    @Nullable
    public final Boolean seatbelts;
    /** What it sets the cabin lights to; null leaves them as they are. */
    @Nullable
    public final Brightness lights;

    Announcement(Holder<SoundEvent> sound, @Nullable Boolean seatbelts, @Nullable Brightness lights) {
        this.sound = sound;
        this.seatbelts = seatbelts;
        this.lights = lights;
    }

    /** Its name in the resources: its picture's and its label's. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
