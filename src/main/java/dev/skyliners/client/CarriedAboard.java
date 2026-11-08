package dev.skyliners.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Carried aboard: after each of the player's own ticks, keeps them in step with the airliner whose cabin they are in,
 * flying, falling or climbing its ladders in flight (see {@link SableClient#keepAboard}); and before anything moves
 * each tick, takes them along with a ship put straight across the world, as a hyperspace jump puts her, so nothing
 * aboard or near her is carried all that way (see {@link SableClient#settleJumps}). Only the player's own client moves
 * them, so only their own player is looked at.
 */
public final class CarriedAboard {
    /** Whether this client tick's ships have yet to be looked at for one put straight across the world. */
    private static boolean jumpsUnsettled;

    private CarriedAboard() {
    }

    public static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof LocalPlayer player) {
            SableClient.keepAboard(player);
        }
    }

    /** At the start of each client tick: the ships are to be looked at again before anything moves. */
    public static void onClientTick(ClientTickEvent.Pre event) {
        jumpsUnsettled = true;
    }

    /**
     * Before the first thing in the level moves each client tick, Sable having moved its ships by then and nothing
     * else firing between: a ship put straight across the world takes the player aboard her there at once.
     */
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (!event.getEntity().level().isClientSide || !jumpsUnsettled) {
            return;
        }
        jumpsUnsettled = false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            SableClient.settleJumps(player);
        }
    }
}
