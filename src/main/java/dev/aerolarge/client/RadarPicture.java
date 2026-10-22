package dev.aerolarge.client;

import dev.aerolarge.radar.Contact;
import dev.aerolarge.radar.RadarContacts;
import java.lang.ref.WeakReference;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/**
 * The transponders this client was last told of (see {@link RadarContacts}), for the autopilot's map and the tower's
 * radar: those of the dimension the player is in, renewed once a second. Told of in another dimension, or before
 * leaving the world, they are not shown.
 */
public final class RadarPicture {
    private static List<Contact> contacts = List.of();
    /** The level they were told of in. */
    private static WeakReference<Level> told = new WeakReference<>(null);

    private RadarPicture() {
    }

    /** From the server: the transponders of the player's dimension now. */
    public static void receive(RadarContacts payload) {
        contacts = List.copyOf(payload.contacts());
        told = new WeakReference<>(Minecraft.getInstance().level);
    }

    /** The transponders of a level, as last told of; none if the last told of were another level's. */
    static List<Contact> in(Level level) {
        return told.get() == level ? contacts : List.of();
    }
}
