package dev.aerolarge.client;

import dev.aerolarge.radar.Contact;
import dev.aerolarge.radar.RadarContacts;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The transponders this client was last told of (see {@link RadarContacts}), for the autopilot's map and the tower's
 * radar: those of the dimension the player is in, renewed once a second. Told of in another dimension, or before
 * leaving the world, they are not shown. Each one told of again is also known by how fast it climbs or sinks, from
 * how much higher or lower it is than the time before, for the tower's flight strips.
 */
public final class RadarPicture {
    /** Told of again after longer than this, in milliseconds, a transponder's climb is not worked out: too stale. */
    private static final long STALE = 5000;
    private static List<Contact> contacts = List.of();
    /** The level they were told of in, and when. */
    private static WeakReference<Level> told = new WeakReference<>(null);
    private static long toldAt;
    /** How fast each was climbing when last told of, in blocks a second (sinking below 0), by its block. */
    private static Map<BlockPos, Float> climbs = Map.of();

    private RadarPicture() {
    }

    /** From the server: the transponders of the player's dimension now. */
    public static void receive(RadarContacts payload) {
        Level level = Minecraft.getInstance().level;
        long now = Util.getMillis();
        climbs = climbs(in(level), payload.contacts(), (now - toldAt) / 1000.0F);
        contacts = List.copyOf(payload.contacts());
        told = new WeakReference<>(level);
        toldAt = now;
    }

    /** How fast each transponder told of before and again now climbed in between, in blocks a second. */
    private static Map<BlockPos, Float> climbs(List<Contact> before, List<Contact> now, float seconds) {
        if (before.isEmpty() || seconds <= 0 || seconds * 1000 > STALE) {
            return Map.of();
        }
        Map<BlockPos, Double> heights = new HashMap<>();
        before.forEach(contact -> heights.put(contact.block(), contact.y()));
        Map<BlockPos, Float> climbs = new HashMap<>();
        for (Contact contact : now) {
            Double height = heights.get(contact.block());
            if (height != null) {
                climbs.put(contact.block(), (float) ((contact.y() - height) / seconds));
            }
        }
        return climbs;
    }

    /** The transponders of a level, as last told of; none if the last told of were another level's. */
    static List<Contact> in(Level level) {
        return told.get() == level ? contacts : List.of();
    }

    /** How fast a transponder last told of was climbing, in blocks a second (sinking below 0); 0 if not known. */
    static float climb(Contact contact) {
        return climbs.getOrDefault(contact.block(), 0F);
    }
}
