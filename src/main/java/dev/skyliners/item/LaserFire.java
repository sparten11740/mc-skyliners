package dev.skyliners.item;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.LaserBolt;
import dev.skyliners.compat.SableFlight;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Where a laser beam lands, and what a bolt does where it strikes: the laser pointer's red beam and the laser
 * crossbow's bolt from someone's hand the way they look, and the freighter's laser cannon's bolts from its muzzle. It
 * lands on the first block it meets, or on someone standing before that block that may catch it, never a spectator. A
 * bolt hurts only a hostile mob it strikes, never a player, a villager, a pet or an animal, and never a block, and is
 * drawn for everyone near with sparks where it strikes.
 */
public final class LaserFire {
    /** How many sparks fly where a bolt strikes, how far they spread, in blocks, and how fast, in blocks a tick. */
    private static final int SPARKS = 12;
    private static final double SPARK_SPREAD = 0.2;
    private static final double SPARK_SPEED = 0.15;

    private LaserFire() {
    }

    /** Where a beam from the eyes, reaching so many blocks, lands. */
    static HitResult trace(ServerLevel level, LivingEntity user, Vec3 eye, double range) {
        return trace(level, user, eye, user.getLookAngle(), range, entity -> true);
    }

    /**
     * Where a beam from a point along a way, reaching so many blocks, lands, fired by someone it never lands on, and
     * passing through anyone else it must not land on. A beam that meets a ship's block is met among her blocks, in her
     * plot; where it lands is taken back into the world, where the beam and whoever it may strike are. On the server, or
     * on a client to show where one would land, as the freighter's gunner's sight does.
     */
    public static HitResult trace(Level level, Entity shooter, Vec3 from, Vec3 way, double range, Predicate<Entity> catches) {
        Vec3 far = from.add(way.normalize().scale(range));
        BlockHitResult met = level.clip(new ClipContext(from, far, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, shooter));
        Vec3 end = SableFlight.toWorld(level, met.getLocation());
        HitResult block = met.getType() == HitResult.Type.MISS ? BlockHitResult.miss(end, met.getDirection(), met.getBlockPos())
            : new BlockHitResult(end, met.getDirection(), met.getBlockPos(), met.isInside());
        AABB swept = new AABB(from, end).inflate(1.0);
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(shooter, from, end, swept, catches.and(LaserFire::catchesBeam),
            from.distanceToSqr(end));
        return entity != null ? entity : block;
    }

    private static boolean catchesBeam(Entity entity) {
        return !entity.isSpectator() && entity.isPickable();
    }

    /**
     * A bolt fired from a point, landing where a trace found: a hostile mob it struck hurt so much, by whoever fired
     * it; the bolt drawn in its glow's colour for everyone within so many blocks, leaving the weapon of whoever holds
     * it as each of them draws it, if it is held in hand rather than mounted; sparks where it strikes. Gives the mob it
     * hurt, if any, for its shooter to throw back.
     */
    @Nullable
    public static LivingEntity strike(ServerLevel level, @Nullable Entity shooter, Vec3 from, HitResult hit, float damage, int glow, double seen,
        @Nullable Entity holder) {
        Vec3 struck = hit.getLocation();
        LivingEntity hurt = null;
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target && hurts(target)) {
            target.hurt(laser(level, shooter), damage);
            hurt = target;
        }
        LaserBolt bolt = new LaserBolt(from, drawnTo(level, hit), glow, holder == null ? LaserBolt.NO_HOLDER : holder.getId());
        PacketDistributor.sendToPlayersNear(level, null, from.x, from.y, from.z, seen, bolt);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, struck.x, struck.y, struck.z, SPARKS, SPARK_SPREAD, SPARK_SPREAD, SPARK_SPREAD,
            SPARK_SPEED);
        return hurt;
    }

    /**
     * Where a bolt is drawn landing: where it struck, but among a ship's blocks where it struck one of hers, so that a
     * client draws it landing on her as she is drawn there, a little behind where the server has her.
     */
    private static Vec3 drawnTo(ServerLevel level, HitResult hit) {
        return hit instanceof BlockHitResult block ? SableFlight.toPlot(level, block.getBlockPos(), hit.getLocation(), false) : hit.getLocation();
    }

    /** Whether a bolt hurts what it strikes: a hostile mob, alive. */
    private static boolean hurts(LivingEntity target) {
        return target instanceof Enemy && target.isAlive();
    }

    /** A laser's harm, by whoever fired it. */
    private static DamageSource laser(ServerLevel level, @Nullable Entity shooter) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SkylinersRegistry.LASER_DAMAGE),
            shooter);
    }
}
