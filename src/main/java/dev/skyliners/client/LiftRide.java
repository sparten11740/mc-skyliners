package dev.skyliners.client;

import dev.skyliners.entity.LiftCar;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Carries the player along on a lift car's floor as it rides (see {@link LiftCar}): before each of the player's own
 * ticks, their game moves them with the floor under their feet, up or down, as the car moves along its shaft, so they
 * stand and walk about in it as in any lift; should the floor have risen past their feet, as when they jump in a car
 * going up, it lifts them back onto it, so no one drops down the shaft. A player just joined hangs where they are
 * until the ground under them has come (see {@link #waitingForGround}). It also counts the level's ticks the cars time
 * their rides by there (see {@link LiftCar#clientTick}). Only the player's own client moves them, so only their own
 * player is looked at.
 */
public final class LiftRide {
    /**
     * How long after joining a player may hang waiting for the ground under them, in ticks: the half minute a world's
     * loading screen waits for it.
     */
    private static final int JOINING = 600;

    private LiftRide() {
    }

    /** The level about to tick: the cars' clock goes on a tick before anything in it ticks. */
    public static void onLevelTick(LevelTickEvent.Pre event) {
        if (event.getLevel().isClientSide) {
            LiftCar.clientTick();
        }
    }

    /**
     * The player about to tick: carried along on the floor of any car they stand in. Someone flying in creative mode is
     * carried too: a double tap of the jump key in a car going up would otherwise leave them hanging as its floor rose
     * through them, under it and in the shaft when they stopped flying.
     */
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || player.isPassenger() || player.isSpectator()) {
            return;
        }
        if (waitingForGround(player)) {
            player.setDeltaMovement(Vec3.ZERO);
            return;
        }
        for (LiftCar car : LiftCar.cars(player.level())) {
            carry(player, car);
        }
    }

    /**
     * Whether a player has only just joined and their game has no ground under them yet. The game moves them from their
     * first tick, behind the loading screen: on a slow start, whoever was saved standing in a car would be too far
     * under its floor to be lifted back by the time it came, and would drop down the shaft.
     */
    private static boolean waitingForGround(LocalPlayer player) {
        return player.tickCount < JOINING && player.level().getChunk(player.getBlockX() >> 4, player.getBlockZ() >> 4, ChunkStatus.FULL, false) == null;
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
