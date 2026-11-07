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
 * attack key is its trigger and the use key its rockets' trigger, if it has rocket pods, sent to the server as either
 * is pressed and let go and again every few ticks while held (see {@link GunnerTrigger}), a tap too quick to be seen
 * held still sent as pressed; and no click reaches anything in the world, so a gunner never breaks the dome's glass,
 * hits whoever stands in reach or sets a block down out by the cannon, where their view is (see {@link GunnerView}).
 */
public final class GunnerControls {
    /** How often a held trigger is said again, in ticks, well within the server's lapse. */
    private static final int SAID_AGAIN = 5;

    private static boolean held;
    private static boolean rocketsHeld;
    /** Whether the use key was pressed at a cannon with rocket pods since its trigger was last said. */
    private static boolean tapped;
    private static int sinceSaid;

    private GunnerControls() {
    }

    /**
     * Each client tick: the triggers as the attack and use keys stand, or the use key tapped, said to the server as
     * they change and while either is held.
     */
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        LaserCannonBlockEntity cannon = player == null || minecraft.screen != null ? null : cannon(player);
        boolean now = cannon != null && minecraft.options.keyAttack.isDown();
        boolean rockets = cannon != null && cannon.pods() && (minecraft.options.keyUse.isDown() || tapped);
        tapped = false;
        sinceSaid++;
        if (now != held || rockets != rocketsHeld || (now || rockets) && sinceSaid >= SAID_AGAIN) {
            held = now;
            rocketsHeld = rockets;
            sinceSaid = 0;
            PacketDistributor.sendToServer(new GunnerTrigger(now, rockets));
        }
    }

    /**
     * Clicks do nothing in the world while seated at a cannon. With rocket pods, use launches its rockets instead, a
     * tap noted here to be said; without, what is in hand can still be used, food eaten, as long as it is not used on
     * anything by the cannon.
     */
    public static void input(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        LaserCannonBlockEntity cannon = player == null ? null : cannon(player);
        if (cannon == null) {
            return;
        }
        boolean onSomething = minecraft.hitResult != null && minecraft.hitResult.getType() != HitResult.Type.MISS;
        boolean rockets = event.isUseItem() && cannon.pods();
        if (!event.isUseItem() || onSomething || rockets) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
        tapped |= rockets;
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
