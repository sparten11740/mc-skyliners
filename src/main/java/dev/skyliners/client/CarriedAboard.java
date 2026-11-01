package dev.skyliners.client;

import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Carried aboard: after each of the player's own ticks, keeps them in step with the airliner whose cabin they are in,
 * flying, falling or climbing its ladders in flight (see {@link SableClient#keepAboard}). Only the player's own client
 * moves them, so only their own player is looked at.
 */
public final class CarriedAboard {
    private CarriedAboard() {
    }

    public static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof LocalPlayer player) {
            SableClient.keepAboard(player);
        }
    }
}
