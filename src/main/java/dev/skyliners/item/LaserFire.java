package dev.skyliners.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Where a beam held in someone's hand lands, the way they look: the laser pointer's red beam and the laser crossbow's
 * bolt. It lands on the first block it meets, or on someone standing before that block, never a spectator.
 */
final class LaserFire {
    private LaserFire() {
    }

    /** Where a beam from the eyes, reaching so many blocks, lands. */
    static HitResult trace(ServerLevel level, LivingEntity user, Vec3 eye, double range) {
        Vec3 far = eye.add(user.getLookAngle().scale(range));
        HitResult block = level.clip(new ClipContext(eye, far, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, user));
        Vec3 end = block.getLocation();
        AABB swept = new AABB(eye, end).inflate(1.0);
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(user, eye, end, swept, LaserFire::catchesBeam, eye.distanceToSqr(end));
        return entity != null ? entity : block;
    }

    private static boolean catchesBeam(Entity entity) {
        return !entity.isSpectator() && entity.isPickable();
    }
}
