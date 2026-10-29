package dev.aerolarge;

import dev.aerolarge.compat.SableFlight;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Who is aboard an airliner, seen from one of its blocks (the flight deck's handset, the autopilot, a seatbelt switch).
 * Once assembled, everyone riding it: standing on it or seated in it, wherever it flies, and the loose things carried
 * among its blocks (see {@link SableFlight#keepWithCabin}); and anyone but a spectator inside its hull all the same, as
 * a player is in the air mid-jump, whom Sable lets go of until they land: a jump in a rocket's low gravity in space
 * hangs nearly two seconds. Standing as blocks in the world, everyone inside its hull within its length of that block:
 * airframe somewhere overhead and somewhere underfoot, whatever hangs from the ceiling or stands on the floor between.
 */
public final class Aboard {
    /** How far an airliner's cabin reaches from any of its blocks, in blocks per unit of scale: nose to tail and more. */
    private static final double REACH_PER_SCALE = 80;
    /** How far up and down from the eyes the hull is looked for: past the crown and the hold's belly. */
    private static final int HULL_UP = 48;
    private static final int HULL_DOWN = 32;
    /** The hull, floor, windows and panels: not the gear, nor what is fitted into the cabin. */
    public static final TagKey<Block> AIRFRAME = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "airframe"));

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
            return riding || !entity.isSpectator() && SableFlight.withinHull(anchor, entity.getEyePosition());
        }
        double reach = REACH_PER_SCALE * AerolargeConfig.SCALE.get();
        return entity.distanceToSqr(Vec3.atCenterOf(anchor.getBlockPos())) < reach * reach
            && insideHull(level, BlockPos.containing(entity.getEyePosition()));
    }

    /**
     * Whether a spot is inside a standing airliner's hull: airframe somewhere above it and somewhere below. Below is
     * looked at first, the shorter reach: a spot with no hull round it, as a cave's is, is ruled out sooner.
     */
    public static boolean insideHull(BlockGetter level, BlockPos at) {
        return hullWithin(level, at, Direction.DOWN, HULL_DOWN) && hullWithin(level, at, Direction.UP, HULL_UP);
    }

    private static boolean hullWithin(BlockGetter level, BlockPos from, Direction way, int reach) {
        BlockPos.MutableBlockPos at = from.mutable();
        for (int i = 0; i < reach; i++) {
            if (level.getBlockState(at.move(way)).is(AIRFRAME)) {
                return true;
            }
        }
        return false;
    }

    /** Everyone aboard the airliner the block entity is part of. */
    public static List<ServerPlayer> players(BlockEntity anchor) {
        if (!(anchor.getLevel() instanceof ServerLevel level)) {
            return List.of();
        }
        return level.players().stream().filter(player -> isAboard(player, anchor)).toList();
    }
}
