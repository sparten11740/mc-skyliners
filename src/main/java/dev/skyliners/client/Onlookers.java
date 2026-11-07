package dev.skyliners.client;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Who stands near a block on this client, for one whose head turns to follow them (see the rocket's bartender and the
 * freighter's deck robots): worked out among the block's own blocks, its ship's plot aboard one, spectators left out.
 */
final class Onlookers {
    private Onlookers() {
    }

    /**
     * The player whose eyes are nearest a point among a block's blocks, within so many blocks of it; null for none.
     */
    @Nullable
    static Player nearest(Level level, BlockPos pos, Vec3 from, double range) {
        Player nearest = null;
        double best = range * range;
        for (Player player : level.players()) {
            double distance = SableClient.toPlot(level, pos, player.getEyePosition(), false).distanceToSqr(from);
            if (!player.isSpectator() && distance < best) {
                nearest = player;
                best = distance;
            }
        }
        return nearest;
    }
}
