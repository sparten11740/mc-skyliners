package dev.aerolarge.client;

import dev.aerolarge.entity.LiftCar;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Carries the player along on a lift car's floor as it rides (see {@link LiftCar}): before each of the player's own
 * ticks, their game moves them with the floor under their feet, up or down, as the car moves along its shaft, so they
 * stand and walk about in it as in any lift; should the floor have risen past their feet, as when they jump in a car
 * going up, it lifts them back onto it, so no one drops down the shaft. It also counts the level's ticks the cars time
 * their rides by there (see {@link LiftCar#clientTick}). Only the player's own client moves them, so only their own
 * player is looked at.
 */
public final class LiftRide {
    private LiftRide() {
    }

    /** The level about to tick: the cars' clock goes on a tick before anything in it ticks. */
    public static void onLevelTick(LevelTickEvent.Pre event) {
        if (event.getLevel().isClientSide) {
            LiftCar.clientTick();
        }
    }

    /** The player about to tick: carried along on the floor of any car they stand in. */
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || player.isPassenger() || player.isSpectator() || player.getAbilities().flying) {
            return;
        }
        for (LiftCar car : LiftCar.cars(player.level())) {
            carry(player, car);
        }
    }

    /**
     * Carried along by a car, where they stand on its floor or have sunk under it (see {@link LiftCar#standing}): their
     * feet found among the ship's blocks as she lay when the player last moved, and moved along the shaft as she lies.
     */
    private static void carry(LocalPlayer player, LiftCar car) {
        Level level = player.level();
        Vec3 feet = SableClient.toPlot(level, car.machine(), player.position(), true);
        double stand = car.standing(feet);
        if (Double.isNaN(stand) || stand == feet.y) {
            return;
        }
        Vec3 along = SableClient.toWorld(level, new Vec3(feet.x, stand, feet.z)).subtract(SableClient.toWorld(level, feet));
        player.setPos(player.position().add(along));
        if (stand > feet.y + car.rose()) {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, Math.max(0, motion.y), motion.z);
            player.resetFallDistance();
        }
    }
}
