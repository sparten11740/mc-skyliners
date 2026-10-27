package dev.aerolarge.client;

import dev.aerolarge.block.BallastValveBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * "Up ship!" as this client sees it (see {@link BallastValveBlockEntity}): as a zeppelin takes off from the ground, her
 * bow's water ballast pours from her belly in a stream while it is heard pouring.
 */
public final class UpShipScene {
    /** How near the water is poured, in blocks, about as far as the land round is drawn. */
    private static final double SEEN = 160;
    /** The water: how many drops a tick, how widely about the outlet, in blocks, and how many spray puffs a tick. */
    private static final int DROPS = 10;
    private static final double DROP_SPREAD = 0.5;
    private static final int SPRAY = 2;

    private UpShipScene() {
    }

    /** Once a tick: the bow's water pouring out of her belly while it pours. */
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return;
        }
        long now = level.getGameTime();
        for (BallastValveBlockEntity valve : BallastValveBlockEntity.loaded(level)) {
            long since = valve.since(now);
            if (since >= BallastValveBlockEntity.POUR_FROM && since < BallastValveBlockEntity.POUR_TO && valve.outlet() != null) {
                pour(level, player, SableClient.toWorld(level, Vec3.atCenterOf(valve.getBlockPos().offset(valve.outlet()))));
            }
        }
    }

    /** A tick of the water pouring from an outlet in the world: drops falling in a stream, and spray. */
    private static void pour(ClientLevel level, LocalPlayer player, Vec3 outlet) {
        if (outlet.distanceTo(player.position()) > SEEN) {
            return;
        }
        for (int drop = 0; drop < DROPS; drop++) {
            level.addAlwaysVisibleParticle(ParticleTypes.FALLING_WATER, true, outlet.x + level.random.nextGaussian() * DROP_SPREAD, outlet.y,
                outlet.z + level.random.nextGaussian() * DROP_SPREAD, 0, 0, 0);
        }
        for (int puff = 0; puff < SPRAY; puff++) {
            level.addAlwaysVisibleParticle(ParticleTypes.SPLASH, true, outlet.x + level.random.nextGaussian() * DROP_SPREAD, outlet.y,
                outlet.z + level.random.nextGaussian() * DROP_SPREAD, 0, 0, 0);
        }
    }
}
