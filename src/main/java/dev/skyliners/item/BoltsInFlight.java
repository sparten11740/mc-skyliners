package dev.skyliners.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The laser crossbow's bolts on their way to a mob, on the server: each hurts the mob its trace found only once it has
 * flown there, as fast as its clients draw it flying (see {@link LaserFire#fly}), so a mob flinches as the bolt is seen
 * to reach it rather than as it leaves the muzzle. Only bolts at a mob they may hurt are kept, a few at most for each
 * player firing, each for under a second; those of a level unloading go with it.
 */
public final class BoltsInFlight {
    private static final Map<ServerLevel, List<Flying>> FLYING = new HashMap<>();

    /**
     * A bolt on its way: whoever fired it, the mob it is to strike, the game tick it gets there, how hard it strikes, in
     * half hearts, and how hard it throws the mob back along the way it flew, a level way.
     */
    private record Flying(@Nullable Entity shooter, LivingEntity target, long due, float damage, double thrown, Vec3 way) {
    }

    private BoltsInFlight() {
    }

    /** A bolt fired at a mob, getting there so many ticks from now. */
    static void launch(ServerLevel level, @Nullable Entity shooter, LivingEntity target, int ticks, float damage, double thrown, Vec3 way) {
        Flying flying = new Flying(shooter, target, level.getGameTime() + ticks, damage, thrown, way);
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
        arrived.forEach(bolt -> strike(level, bolt));
        if (flying.isEmpty()) {
            FLYING.remove(level);
        }
    }

    /** Forgets a level's bolts as it unloads. */
    public static void drop(ServerLevel level) {
        FLYING.remove(level);
    }

    /** A bolt got there: the mob hurt and thrown back, if it is still there to be. */
    private static void strike(ServerLevel level, Flying bolt) {
        LivingEntity target = bolt.target();
        if (target.isRemoved() || target.level() != level || !LaserFire.hurt(level, bolt.shooter(), target, bolt.damage())) {
            return;
        }
        target.knockback(bolt.thrown(), -bolt.way().x, -bolt.way().z);
    }
}
