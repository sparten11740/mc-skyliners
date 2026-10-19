package dev.aerolarge.client;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.parachute.OpenParachute;
import dev.aerolarge.parachute.Parachute;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Opens the player's parachute when they press jump while falling with one packed on: asks the server, and opens it
 * on this side straight away so the canopy catches them without waiting for the answer.
 */
public final class ParachuteControls {
    private static boolean wasJumping;

    private ParachuteControls() {
    }

    public static void tick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        boolean jumping = player != null && Minecraft.getInstance().options.keyJump.isDown();
        if (jumping && !wasJumping && Parachute.canOpen(player)) {
            PacketDistributor.sendToServer(OpenParachute.INSTANCE);
            Parachute.worn(player).set(AerolargeRegistry.PARACHUTE_OPEN.get(), true);
        }
        wasJumping = jumping;
    }
}
