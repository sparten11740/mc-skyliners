package dev.skyliners.client;

import dev.skyliners.turret.GunnerTrigger;
import dev.skyliners.turret.LaserCannonBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The gunner's side of the freighter's laser cannon (see {@link LaserCannonBlockEntity}): seated at a cannon, the
 * attack key is its trigger, sent to the server as it is pressed and let go and again every few ticks while held (see
 * {@link GunnerTrigger}), and no click reaches anything in the world, so a gunner never breaks the dome's glass, hits
 * whoever stands in reach or sets a block down out by the cannon, where their view is (see {@link GunnerView}).
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
        boolean now = player != null && minecraft.screen == null && cannon(player) != null && minecraft.options.keyAttack.isDown();
        sinceSaid++;
        if (now != held || now && sinceSaid >= SAID_AGAIN) {
            held = now;
            sinceSaid = 0;
            PacketDistributor.sendToServer(new GunnerTrigger(now));
        }
    }

    /**
     * Clicks do nothing in the world while seated at a cannon; what is in hand can still be used, food eaten, as long
     * as it is not used on anything by the cannon.
     */
    public static void input(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean onSomething = minecraft.hitResult != null && minecraft.hitResult.getType() != HitResult.Type.MISS;
        if (player != null && (!event.isUseItem() || onSomething) && cannon(player) != null) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /** The laser cannon the player sits at: seated, their eyes within its reach, in its frame; none if they sit at none. */
    @Nullable
    static LaserCannonBlockEntity cannon(LocalPlayer player) {
        if (!player.isPassenger()) {
            return null;
        }
        for (LaserCannonBlockEntity cannon : LaserCannonBlockEntity.loaded(player.level())) {
            Vec3 eye = SableClient.toPlot(player.level(), cannon.getBlockPos(), player.getEyePosition(), false);
            if (Vec3.atCenterOf(cannon.getBlockPos()).distanceTo(eye) <= LaserCannonBlockEntity.GUNNER_REACH) {
                return cannon;
            }
        }
        return null;
    }
}
