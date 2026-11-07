package dev.skyliners.turret;

import dev.skyliners.compat.SableFlight;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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
 * The blast where a laser cannon's bolt strikes a block (see {@link LaserCannonBlockEntity}), and where one of its
 * rockets goes off (see {@link CannonRocket}): an explosion that knocks out the blocks round it, as a well-aimed cannon
 * should, but never a block of any ship, the cannon's own above all, and like the bolt itself it hurts and throws only
 * hostile mobs, never a player, a villager, a pet, an animal or anyone aboard her. It breaks blocks as TNT does, by the
 * world's rules for TNT; none with the server config's {@code freighter.turretBlasts} off, when a rocket's still hurts
 * hostile mobs.
 */
final class TurretBlast {
    /** How strong a bolt's blast is, as an explosion's power: a little under a creeper's 3. */
    private static final float POWER = 1.6F;
    /** How strong a rocket's blast is, as an explosion's power: a creeper's. */
    private static final float ROCKET_POWER = 3.0F;
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

    /**
     * A rocket's blast at a point, set off by the rocket, sparing whoever the predicate says is aboard; breaking blocks
     * only if it may. Silent, with a single puff of the game's own: the rocket's bang and fireball are its own, heard
     * and seen further than the game's explosions are.
     */
    static void rocket(ServerLevel level, Entity rocket, Vec3 at, Predicate<Entity> notAboard, boolean breaks) {
        Level.ExplosionInteraction breaking = breaks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
        level.explode(rocket, null, new Spared(notAboard), at.x, at.y, at.z, ROCKET_POWER, false, breaking, ParticleTypes.EXPLOSION,
            ParticleTypes.EXPLOSION, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.EMPTY));
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
