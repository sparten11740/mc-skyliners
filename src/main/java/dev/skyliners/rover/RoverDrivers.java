package dev.skyliners.rover;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Whoever is driving the rocket's rover, on the server, and keeping them safe while they do: their own body stands
 * where they left it, their view away with the rover, so a driver never takes fall damage, and anything else that
 * hurts them their client notices and gives them their view back (see the client's {@code RoverDriving}). A player
 * counts as driving while their drive has been heard within the last half second (see {@link DriveRover}).
 */
public final class RoverDrivers {
    /** How long a player counts as driving after their last word, in ticks. */
    private static final int DRIVING = 10;
    /** When each driver was last heard, by the server's game time. */
    private static final Map<UUID, Long> HEARD = new HashMap<>();

    private RoverDrivers() {
    }

    /** Listens for drivers' falls, ticks and leaving. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(RoverDrivers::onFall);
        NeoForge.EVENT_BUS.addListener(RoverDrivers::onTick);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> HEARD.remove(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> HEARD.clear());
    }

    /**
     * A driver's word heard now. Only a driver who began on their feet counts as driving, so nobody falling takes up
     * the remote to land unhurt.
     */
    static void heard(ServerPlayer player) {
        if (player.onGround() || driving(player)) {
            HEARD.put(player.getUUID(), player.serverLevel().getGameTime());
        }
    }

    /** Whether a player on the server is driving a rover now. */
    public static boolean driving(Player player) {
        Long heard = HEARD.get(player.getUUID());
        return heard != null && player.level().getGameTime() - heard <= DRIVING;
    }

    private static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && driving(player)) {
            event.setCanceled(true);
        }
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && driving(player)) {
            player.fallDistance = 0;
        }
    }
}
