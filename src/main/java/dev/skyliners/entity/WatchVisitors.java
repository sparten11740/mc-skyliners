package dev.skyliners.entity;

import dev.skyliners.compat.SableFlight;
import java.util.EnumSet;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A creature living aboard a ship looking a while at whoever comes near, now and then, as vanilla's creatures look at
 * players; but seeing them as it sees the world, in her frame while it lives among her blocks, so aboard her in flight
 * it still finds them. The tardigrade and the Spookie both watch their visitors so.
 */
public final class WatchVisitors extends Goal {
    /** How likely it is to look for someone each tick, and how long it looks, from and to, in ticks. */
    private static final float CHANCE = 0.02F;
    private static final int LOOK_FROM = 40;
    private static final int LOOK_TO = 80;

    private final Mob creature;
    private final double range;
    private final BooleanSupplier awake;
    @Nullable
    private Player visitor;
    private int looking;

    /** Watching visitors as far off as given, in blocks, while it is awake enough to, as it says. */
    public WatchVisitors(Mob creature, double range, BooleanSupplier awake) {
        this.creature = creature;
        this.range = range;
        this.awake = awake;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!awake.getAsBoolean() || creature.getRandom().nextFloat() >= CHANCE) {
            return false;
        }
        visitor = nearest(creature, range);
        return visitor != null;
    }

    @Override
    public boolean canContinueToUse() {
        return visitor != null && visitor.isAlive() && awake.getAsBoolean() && looking > 0
            && seen(creature, visitor.getEyePosition()).distanceTo(creature.getEyePosition()) < range;
    }

    @Override
    public void start() {
        looking = adjustedTickDelay(LOOK_FROM + creature.getRandom().nextInt(LOOK_TO - LOOK_FROM));
    }

    @Override
    public void stop() {
        visitor = null;
    }

    @Override
    public void tick() {
        looking--;
        if (visitor != null) {
            creature.getLookControl().setLookAt(seen(creature, visitor.getEyePosition()));
        }
    }

    /**
     * A point in the world as a creature sees it, in her frame while it lives among her blocks (where its own place
     * is), the point itself otherwise.
     */
    public static Vec3 seen(Mob creature, Vec3 point) {
        return SableFlight.toPlot(creature.level(), creature.blockPosition(), point, false);
    }

    /** The nearest player no further from a creature than given, in blocks, as it sees them; null for none. */
    @Nullable
    public static Player nearest(Mob creature, double range) {
        Player nearest = null;
        double nearestDistance = range;
        for (Player player : creature.level().players()) {
            double distance = seen(creature, player.getEyePosition()).distanceTo(creature.getEyePosition());
            if (!player.isSpectator() && distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        return nearest;
    }
}
