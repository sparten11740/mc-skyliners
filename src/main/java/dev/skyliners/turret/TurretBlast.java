package dev.skyliners.turret;

import dev.skyliners.compat.SableFlight;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The blast where a laser cannon's bolt strikes a block (see {@link LaserCannonBlockEntity}): a small explosion that
 * knocks out the blocks round it, as a well-aimed cannon should, but never a block of any ship, the cannon's own above
 * all, and like the bolt itself it hurts and throws only hostile mobs, never a player, a villager, a pet, an animal or
 * anyone aboard her. It breaks blocks as TNT does, by the world's rules for TNT; none with the server config's
 * {@code freighter.turretBlasts} off.
 */
final class TurretBlast {
    /** How strong the blast is, as an explosion's power: a little under a creeper's 3. */
    private static final float POWER = 1.6F;
    /** How far back along the bolt from the face it struck the blast goes off, in blocks, so it is not inside the block. */
    private static final double BACK_OFF = 0.2;

    private TurretBlast() {
    }

    /**
     * A blast where a bolt from a point struck, if it struck a block of the world; set off by whoever fired it, sparing
     * whoever the predicate says is aboard.
     */
    static void at(ServerLevel level, @Nullable Entity shooter, Vec3 from, HitResult hit, Predicate<Entity> notAboard) {
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        Vec3 struck = hit.getLocation();
        Vec3 back = from.subtract(struck).normalize().scale(BACK_OFF);
        Vec3 at = struck.add(back);
        level.explode(shooter, null, new Spared(notAboard), at.x, at.y, at.z, POWER, false, Level.ExplosionInteraction.TNT);
    }

    /** What the blast may break and whom it may hurt: no ship's blocks, and only hostile mobs not aboard. */
    private static final class Spared extends ExplosionDamageCalculator {
        private final Predicate<Entity> notAboard;

        Spared(Predicate<Entity> notAboard) {
            this.notAboard = notAboard;
        }

        @Override
        public boolean shouldBlockExplode(Explosion explosion, BlockGetter reader, BlockPos pos, BlockState state, float power) {
            boolean ship = reader instanceof Level level && SableFlight.aboard(level, Vec3.atCenterOf(pos));
            return !ship && super.shouldBlockExplode(explosion, reader, pos, state, power);
        }

        @Override
        public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return hurts(entity);
        }

        @Override
        public float getKnockbackMultiplier(Entity entity) {
            return hurts(entity) ? super.getKnockbackMultiplier(entity) : 0;
        }

        private boolean hurts(Entity entity) {
            return entity instanceof Enemy && notAboard.test(entity);
        }
    }
}
