package dev.aerolarge.cabin;

import dev.aerolarge.compat.SableFlight;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Room to stand: a player standing aboard an assembled airliner as it climbs is not made to crouch or crawl for a
 * floor that is no longer there. The game makes a player crouch, then crawl, where it finds no room to stand, and Sable
 * looks for that room among the airliner's blocks where they were a tick ago; flying fast nose up, the floor stood
 * higher there than it does now, so a player who got up or walked about on it was crawling along the cabin floor, and
 * stayed so until the airliner levelled off; one standing could not crouch either, as the game only changes the pose
 * where there is room to crawl. After each player's tick this looks again, at the airliner as it lies now, and puts
 * them in the pose they want (standing, or crouching while they sneak) where there is room for it, or at least
 * crouching rather than crawling. It never lowers them itself: under anything really low the game still does. Both
 * sides: the client works out its own player's pose, the server everyone's.
 */
public final class Headroom {
    private Headroom() {
    }

    public static void onTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        Pose pose = player.getPose();
        if (pose != Pose.STANDING && pose != Pose.CROUCHING && pose != Pose.SWIMMING || player.level().isClientSide && !player.isLocalPlayer()) {
            return;
        }
        if (player.isSpectator() || player.isPassenger() || player.isSwimming() || player.isFallFlying() || player.isSleeping()
            || player.isAutoSpinAttack()) {
            return;
        }
        Pose wanted = player.isShiftKeyDown() && !player.getAbilities().flying ? Pose.CROUCHING : Pose.STANDING;
        if (pose == wanted) {
            return;
        }
        if (fits(player, wanted)) {
            player.setPose(wanted);
        } else if (pose == Pose.SWIMMING && fits(player, Pose.CROUCHING)) {
            player.setPose(Pose.CROUCHING);
        }
    }

    /** Whether the player has room for the pose where they stand, in the world and aboard the airliner as it is now. */
    private static boolean fits(Player player, Pose pose) {
        AABB room = player.getDimensions(pose).makeBoundingBox(player.position()).deflate(1.0E-7);
        return player.level().noCollision(player, room) && SableFlight.fitsAboard(player, room);
    }
}
