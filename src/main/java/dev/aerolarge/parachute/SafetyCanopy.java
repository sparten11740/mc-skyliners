package dev.aerolarge.parachute;

import dev.aerolarge.AerolargeConfig;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The zeppelin's safety net: a canopy that opens over anyone who falls from a zeppelin, off the boarding stairs under
 * its belly in flight, say, or from the gangway inside its hull, the parachute's striped canopy, with its snap, so they
 * glide down as under a parachute and land unhurt, steering the way they walk. It opens for anyone aboard a zeppelin
 * a moment ago who has fallen a few blocks, not in creative mode, not flying and not under a parachute of their own,
 * and packs itself away as they land. Some players are children, and a zeppelin is a long way up. The server decides
 * it opens and tells the clients near (see {@link SafetyCanopyPayload}), whose players move themselves.
 */
public final class SafetyCanopy {
    /** How far a player falls before it opens, in blocks: past a jump down, short of a fall that hurts much. */
    private static final double OPENS_AFTER = 4;
    /** How long after a player was last seen aboard a zeppelin a fall still opens it, in ticks: five seconds. */
    private static final long ABOARD_A_MOMENT_AGO = 5 * 20;

    /** When each player was last seen aboard a zeppelin, in game ticks, on the server. */
    private static final Map<UUID, Long> LAST_ABOARD = new HashMap<>();
    /** Whose canopy is open, as each side knows it: the server's, and the client's from what it was told. */
    private static final Set<UUID> SERVER_OPEN = new HashSet<>();
    private static final Set<UUID> CLIENT_OPEN = new HashSet<>();

    private SafetyCanopy() {
    }

    /** Notes the players aboard a zeppelin now (see {@code AutopilotBlockEntity}). */
    public static void aboard(List<ServerPlayer> players, long now) {
        for (ServerPlayer player : players) {
            LAST_ABOARD.put(player.getUUID(), now);
        }
    }

    /** Whether the player's safety canopy is open, as their side knows it. */
    public static boolean isOpen(Player player) {
        return (player.level().isClientSide ? CLIENT_OPEN : SERVER_OPEN).contains(player.getUUID());
    }

    /**
     * Every tick for a player without a parachute of their own, on both sides: on the server it opens once they fall
     * from a zeppelin, and closes as they land; open, they glide (see {@link Parachute#glide}).
     */
    static void tick(Player player) {
        boolean server = player instanceof ServerPlayer;
        if (!isOpen(player)) {
            if (server && falling((ServerPlayer) player)) {
                open((ServerPlayer) player);
            }
            return;
        }
        if (Parachute.landed(player)) {
            if (server) {
                tell((ServerPlayer) player, false);
            }
            return;
        }
        Parachute.glide(player);
    }

    /** Whether the player is falling from a zeppelin they were aboard a moment ago, with nothing else to save them. */
    private static boolean falling(ServerPlayer player) {
        Long seen = LAST_ABOARD.get(player.getUUID());
        if (seen == null || player.level().getGameTime() - seen > ABOARD_A_MOMENT_AGO || !AerolargeConfig.SAFETY_CANOPY.get()) {
            return false;
        }
        return player.fallDistance > OPENS_AFTER && !player.isCreative() && !player.isSpectator() && !Parachute.landed(player);
    }

    private static void open(ServerPlayer player) {
        tell(player, true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_ELYTRA.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    /** Opens or closes the player's canopy on the server, and tells them and everyone near. */
    private static void tell(ServerPlayer player, boolean open) {
        if (open) {
            SERVER_OPEN.add(player.getUUID());
        } else {
            SERVER_OPEN.remove(player.getUUID());
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new SafetyCanopyPayload(player.getUUID(), open));
    }

    /** On a client: a player's canopy has opened or closed. */
    public static void told(SafetyCanopyPayload payload) {
        if (payload.open()) {
            CLIENT_OPEN.add(payload.player());
        } else {
            CLIENT_OPEN.remove(payload.player());
        }
    }

    /** A player has left the server: nothing of theirs is kept. */
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_ABOARD.remove(event.getEntity().getUUID());
        SERVER_OPEN.remove(event.getEntity().getUUID());
    }

    /** The client has left its world: every canopy it was told of is forgotten. */
    public static void forget() {
        CLIENT_OPEN.clear();
    }
}
