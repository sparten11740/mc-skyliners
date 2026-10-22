package dev.aerolarge.client;

import dev.aerolarge.radar.Contact;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A transponder as the tower's flight strip board lists it, as a controller's strip for an aircraft: its callsign
 * (a short name for its picture and a number of its own, the same every time), how far it is from the tower across
 * the ground, and what it is doing, worked out from how it moves.
 */
record FlightStrip(Contact contact, Component callsign, double distance, Phase phase) {
    /** Closer than this to the tower, in blocks, a transponder on the ground is the tower's own, and left off. */
    private static final double OWN_STATION = 32;
    /** Climbing or sinking faster than this, in blocks a second, an airliner is no longer flying level. */
    private static final float LEVEL = 0.5F;
    /** Slower than this across the ground, in blocks a second, it stands still; slower than taxiing speed, it taxis. */
    private static final float STILL = 1;
    private static final float TAXI = 15;
    /** Heading within this many degrees of the tower, and this close to it in blocks, it is inbound. */
    private static final double TOWARD = 45;
    private static final double APPROACH = 1000;
    /** The callsigns' numbers run from this, this many of them. */
    private static final int FIRST_NUMBER = 100;
    private static final int NUMBERS = 900;
    private static final String KEY = "screen.aerolarge.flight_strips.";

    /** What it is doing, and the colour its strip says it in. */
    enum Phase {
        PARKED(0xFF8A93A0), TAXI(0xFFF2CF30), CLIMB(0xFF46E070), CRUISE(0xFFF2F2F2), DESCEND(0xFF46C8F0), INBOUND(0xFFE040E0),
        FIX(0xFF5A6370);

        private final int colour;

        Phase(int colour) {
            this.colour = colour;
        }

        int colour() {
            return colour;
        }

        Component title() {
            return Component.translatable(KEY + "phase." + name().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Every transponder of a level within a reach of the tower but its own: those flying first, then those on the
     * ground, nearest first.
     */
    static List<FlightStrip> around(Level level, Vec3 tower, double reach) {
        List<FlightStrip> strips = new ArrayList<>();
        for (Contact contact : RadarPicture.in(level)) {
            double distance = Math.hypot(contact.x() - tower.x, contact.z() - tower.z);
            boolean own = !contact.airborne() && distance < OWN_STATION;
            if (distance <= reach && !own) {
                strips.add(new FlightStrip(contact, callsign(contact), distance, phase(contact, tower, distance)));
            }
        }
        strips.sort(Comparator.comparing((FlightStrip strip) -> !strip.contact().airborne()).thenComparingDouble(FlightStrip::distance));
        return strips;
    }

    /** Its callsign: "AER417", the same for the same transponder every time. */
    private static Component callsign(Contact contact) {
        long mixed = contact.block().asLong() * 0x9E3779B97F4A7C15L;
        int number = FIRST_NUMBER + Math.floorMod(Long.hashCode(mixed ^ mixed >>> 29), NUMBERS);
        Component prefix = Component.translatable(KEY + "callsign." + contact.icon().name().toLowerCase(Locale.ROOT));
        return Component.translatable(KEY + "callsign", prefix, number);
    }

    /**
     * What it is doing: standing on the ground, a fix; aboard an airliner, climbing or sinking (inbound if toward the
     * tower), parked, taxiing, or flying level, inbound if near and toward the tower and cruising if not.
     */
    private static Phase phase(Contact contact, Vec3 tower, double distance) {
        if (!contact.airborne()) {
            return Phase.FIX;
        }
        float climb = RadarPicture.climb(contact);
        boolean toward = toward(contact, tower);
        if (climb > LEVEL) {
            return Phase.CLIMB;
        }
        if (climb < -LEVEL) {
            return toward ? Phase.INBOUND : Phase.DESCEND;
        }
        if (contact.speed() < STILL) {
            return Phase.PARKED;
        }
        if (contact.speed() < TAXI) {
            return Phase.TAXI;
        }
        return toward && distance < APPROACH ? Phase.INBOUND : Phase.CRUISE;
    }

    /** Whether it is heading for the tower. */
    private static boolean toward(Contact contact, Vec3 tower) {
        if (Float.isNaN(contact.heading())) {
            return false;
        }
        // A compass bearing from it to the tower: 0 north (toward -z), 90 east (+x).
        double bearing = Math.toDegrees(Math.atan2(tower.x - contact.x(), contact.z() - tower.z));
        double off = Math.abs(Math.floorMod(Math.round(contact.heading() - bearing) + 180, 360L) - 180);
        return off < TOWARD;
    }
}
