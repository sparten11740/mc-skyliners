package dev.skyliners.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * This player thrown clear of an airliner as it blows up (see {@link dev.skyliners.selfdestruct.ThrownClearPayload}),
 * as their client stops them where it blew up. Sable keeps the speed an airliner carried a player at once they are off
 * it, losing only a hundredth of it a tick, so at cruise they sailed on some 400 blocks ahead of the fireball and only
 * saw the airliner vanish. The speed is let go of over a few ticks rather than once, as the word that the airliner is
 * gone may come in a tick or two after this does, and until then it still carries them.
 */
public final class ThrownClear {
    /** How many ticks after being thrown clear the airliner's speed is let go of, each tick. */
    private static final int STOPPING_TICKS = 20;

    private static int ticksLeft;

    private ThrownClear() {
    }

    /** Thrown clear: stopped over the next few ticks. */
    public static void thrown() {
        ticksLeft = STOPPING_TICKS;
    }

    /** Once a tick, while being stopped: the airliner's speed let go of. */
    public static void tick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (ticksLeft <= 0 || player == null) {
            return;
        }
        ticksLeft--;
        SableClient.letGo(player);
    }

    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ticksLeft = 0;
    }
}
