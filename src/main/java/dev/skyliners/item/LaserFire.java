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
 * drawn for everyone near. A mounted gun's bolt strikes at once, with sparks the server sends; a bolt from a weapon in
 * hand is drawn flying there, and its clients burst it into sparks themselves as it gets there.
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
     * A mounted gun's bolt fired from a point, landing where a trace found: a hostile mob it struck hurt so much, by
     * whoever fired it; the bolt drawn in its glow's colour for everyone within so many blocks, and sparks where it
     * strikes.
     */
    public static void strike(ServerLevel level, @Nullable Entity shooter, Vec3 from, HitResult hit, float damage, int glow, double seen) {
        Vec3 struck = hit.getLocation();
        hurt(level, shooter, struck(hit), damage);
        LaserBolt bolt = new LaserBolt(from, drawnTo(level, hit), glow);
        PacketDistributor.sendToPlayersNear(level, null, from.x, from.y, from.z, seen, bolt);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, struck.x, struck.y, struck.z, SPARKS, SPARK_SPREAD, SPARK_SPREAD, SPARK_SPREAD,
            SPARK_SPEED);
    }

    /**
     * A bolt fired from the muzzle of a weapon in hand, landing where a trace found, sent to everyone within so many
     * blocks to be drawn flying there in its glow's colour, leaving the weapon of whoever holds it as each of them draws
     * it. Where it leaves is given in the frame of the ship its holder rides, and where it lands in the frame of what it
     * strikes, or of that ship where it strikes nothing, so a bolt fired aboard a ship in flight flies along with her.
     * Gives how many ticks it takes to get there, flying at {@link LaserBolt#HELD_SPEED}.
     */
    public static int fly(ServerLevel level, Entity holder, Vec3 muzzle, HitResult hit, int glow, double seen) {
        boolean strikes = hit.getType() != HitResult.Type.MISS;
        Vec3 to = strikes ? drawnTo(level, hit) : SableFlight.toRidden(holder, hit.getLocation());
        LaserBolt bolt = new LaserBolt(SableFlight.toRidden(holder, muzzle), to, glow, holder.getId(), strikes);
        PacketDistributor.sendToPlayersNear(level, null, muzzle.x, muzzle.y, muzzle.z, seen, bolt);
        return (int) Math.ceil(muzzle.distanceTo(hit.getLocation()) / LaserBolt.HELD_SPEED);
    }

    /** Whoever a bolt struck, alive; null where it struck a block or nothing. */
    @Nullable
    public static LivingEntity struck(HitResult hit) {
        return hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target ? target : null;
    }

    /**
     * A bolt's harm to whoever it struck, so much, by whoever fired it, if it may hurt them; whether it did.
     */
    public static boolean hurt(ServerLevel level, @Nullable Entity shooter, @Nullable LivingEntity target, float damage) {
        if (target == null || !hurts(target)) {
            return false;
        }
        target.hurt(laser(level, shooter), damage);
        return true;
    }

    /**
     * Where a bolt is drawn landing: where it struck, but among a ship's blocks where it struck one of hers, or someone
     * aboard her, so that a client draws it landing on her as she is drawn, a little behind where the server has her.
     */
    private static Vec3 drawnTo(ServerLevel level, HitResult hit) {
        if (hit instanceof EntityHitResult entityHit) {
            return SableFlight.toRidden(entityHit.getEntity(), hit.getLocation());
        }
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
