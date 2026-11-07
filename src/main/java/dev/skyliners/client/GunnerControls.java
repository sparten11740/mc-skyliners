package dev.skyliners.client;

import dev.skyliners.turret.GunnerTrigger;
import dev.skyliners.turret.LaserCannonBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The gunner's side of the freighter's laser cannon (see {@link LaserCannonBlockEntity}): seated at a cannon, the
 * attack key is its trigger, sent to the server as it is pressed and let go and again every few ticks while held (see
 * {@link GunnerTrigger}), and it does nothing else in the world, so a gunner never breaks the dome's glass or hits
 * whoever stands in reach.
 */
public final class GunnerControls {
    /** How often a held trigger is said again, in ticks, well within the server's lapse. */
    private static final int SAID_AGAIN = 5;

    private static boolean held;
    private static int sinceSaid;

    private GunnerControls() {
    }

    /** Each client tick: the trigger as the attack key stands, said to the server as it changes and while held. */
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean now = player != null && minecraft.screen == null && gunning(player) && minecraft.options.keyAttack.isDown();
        sinceSaid++;
        if (now != held || now && sinceSaid >= SAID_AGAIN) {
            held = now;
            sinceSaid = 0;
            PacketDistributor.sendToServer(new GunnerTrigger(now));
        }
    }

    /** Clicks to attack do nothing in the world while seated at a cannon. */
    public static void input(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.isAttack() && player != null && gunning(player)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** Whether the player sits at a laser cannon: seated, their eyes within its reach, in its frame. */
    private static boolean gunning(LocalPlayer player) {
        if (!player.isPassenger()) {
            return false;
        }
        for (LaserCannonBlockEntity cannon : LaserCannonBlockEntity.loaded(player.level())) {
            Vec3 eye = SableClient.toPlot(player.level(), cannon.getBlockPos(), player.getEyePosition(), false);
            if (Vec3.atCenterOf(cannon.getBlockPos()).distanceTo(eye) <= LaserCannonBlockEntity.GUNNER_REACH) {
                return true;
            }
        }
        return false;
    }
}
