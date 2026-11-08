package dev.skyliners.item;

import dev.skyliners.compat.SableFlight;
import dev.skyliners.impact.ShipHits;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The laser crossbow's bolts on their way to a mob or a ship, on the server: each hurts the mob its trace found, or
 * strikes the ship whose block it found (see {@link ShipHits}), only once it has flown there, as fast as its clients
 * draw it flying (see {@link LaserFire#fly}), so a mob flinches and a ship rocks as the bolt is seen to reach them
 * rather than as it leaves the muzzle. Only bolts at a mob they may hurt or at a ship's block are kept, a few at most
 * for each player firing, each for under a second; those of a level unloading go with it.
 */
public final class BoltsInFlight {
    private static final Map<ServerLevel, List<Flying>> FLYING = new HashMap<>();

    /** A bolt on its way: the game tick it gets there, and what it does there. */
    private interface Flying {
        long due();

        void arrive(ServerLevel level);
    }

    /**
     * A bolt on its way to a mob: whoever fired it, the mob it is to strike, the game tick it gets there, how hard it
     * strikes, in half hearts, and how hard it throws the mob back along the way it flew, a level way.
     */
    private record AtMob(@Nullable Entity shooter, LivingEntity target, long due, float damage, double thrown, Vec3 way) implements Flying {
        /** The mob hurt and thrown back, if it is still there to be. */
        @Override
        public void arrive(ServerLevel level) {
            if (target.isRemoved() || target.level() != level || !LaserFire.hurt(level, shooter, target, damage)) {
                return;
            }
            target.knockback(thrown, -way.x, -way.z);
        }
    }

    /**
     * A bolt on its way to a ship: the block of hers it is to strike and where on it, among her blocks so that it
     * strikes her where it was aimed however she has moved since, the game tick it gets there, the way it flew, and the
     * ship it was fired from, none for none.
     */
    private record AtShip(BlockPos block, Vec3 at, long due, Vec3 way, @Nullable UUID firedFrom) implements Flying {
        @Override
        public void arrive(ServerLevel level) {
            ShipHits.struck(level, block, at, way, ShipHits.Shot.CROSSBOW_BOLT, firedFrom);
        }
    }

    private BoltsInFlight() {
    }

    /** A bolt fired at a mob, getting there so many ticks from now. */
    static void launch(ServerLevel level, @Nullable Entity shooter, LivingEntity target, int ticks, float damage, double thrown, Vec3 way) {
        add(level, new AtMob(shooter, target, level.getGameTime() + ticks, damage, thrown, way));
    }

    /**
     * A bolt fired at a block, getting there so many ticks from now along a way in the world: kept only if the block is
     * one of a ship's, by whoever fired it, standing on or seated in the ship they ride, if any.
     */
    static void launchAt(ServerLevel level, Entity shooter, BlockHitResult hit, int ticks, Vec3 way) {
        BlockPos block = hit.getBlockPos();
        if (SableFlight.bodyAt(level, block) != null) {
            Vec3 at = SableFlight.toPlot(level, block, hit.getLocation(), false);
            add(level, new AtShip(block, at, level.getGameTime() + ticks, way, SableFlight.ridden(shooter)));
        }
    }

    private static void add(ServerLevel level, Flying flying) {
        FLYING.computeIfAbsent(level, ignored -> new ArrayList<>()).add(flying);
    }

    /** Each tick of a level: the bolts that have got there strike. */
    public static void tick(ServerLevel level) {
        List<Flying> flying = FLYING.get(level);
        if (flying == null) {
            return;
        }
        long now = level.getGameTime();
        List<Flying> arrived = new ArrayList<>();
        flying.removeIf(bolt -> bolt.due() <= now && arrived.add(bolt));
        arrived.forEach(bolt -> bolt.arrive(level));
        if (flying.isEmpty()) {
            FLYING.remove(level);
        }
    }

    /** Forgets a level's bolts as it unloads. */
    public static void drop(ServerLevel level) {
        FLYING.remove(level);
    }
}
