package dev.aerolarge;

import dev.aerolarge.compat.SableFlight;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Who is aboard an airliner, seen from one of its blocks (the flight deck's handset, the autopilot, a seatbelt
 * switch). Once assembled, everyone riding it: standing on it or seated in it, wherever it flies. Standing as blocks
 * in the world, everyone inside its hull (airframe overhead and underfoot) within its length of that block.
 */
public final class Aboard {
    /** How far an airliner's cabin reaches from any of its blocks, in blocks per unit of scale: nose to tail and more. */
    private static final double REACH_PER_SCALE = 80;

    private Aboard() {
    }

    /** Whether the entity is aboard the airliner the block entity is part of. */
    public static boolean isAboard(Entity entity, BlockEntity anchor) {
        Level level = anchor.getLevel();
        if (level == null || entity.level() != level) {
            return false;
        }
        Boolean riding = SableFlight.rides(entity, anchor);
        if (riding != null) {
            return riding;
        }
        double reach = REACH_PER_SCALE * AerolargeConfig.SCALE.get();
        return entity.distanceToSqr(Vec3.atCenterOf(anchor.getBlockPos())) < reach * reach
            && AirlinerSpawnGuard.insideAirliner(level, entity.blockPosition());
    }

    /** Everyone aboard the airliner the block entity is part of. */
    public static List<ServerPlayer> players(BlockEntity anchor) {
        if (!(anchor.getLevel() instanceof ServerLevel level)) {
            return List.of();
        }
        return level.players().stream().filter(player -> isAboard(player, anchor)).toList();
    }
}
