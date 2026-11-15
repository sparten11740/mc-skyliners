package dev.skyliners.turret;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.compat.SableFlight;
import dev.skyliners.item.LaserFire;
import dev.skyliners.parachute.SafetyCanopy;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The blast where a laser cannon's bolt strikes a block (see {@link LaserCannonBlockEntity}), and where one of its
 * rockets goes off (see {@link CannonRocket}): an explosion that knocks out the blocks round it, as a well-aimed cannon
 * should, but never a block of any ship, the cannon's own above all, and like the bolt itself it hurts only hostile
 * mobs, never a villager, a pet, an animal or anyone aboard her. It throws players near it far and high, in creative
 * mode or flying too, as a bolt striking one does (see {@link #throwStruck}), hurting them as it does a mob unless
 * they are in creative mode, and they come down unhurt by the fall after; never anyone aboard her, and none with the
 * server config's {@code freighter.turretThrowsPlayers} off, when it leaves players be. It breaks blocks as TNT
 * does, by the world's rules for TNT; none with the server config's {@code freighter.turretBlasts} off, when a
 * rocket's still hurts hostile mobs.
 */
final class TurretBlast {
    /**
     * How strong a bolt's blast is, as an explosion's power: enough to knock out the block of stone it strikes, a layer
     * at a time. The game's blast needs over 2.1 to break a block as hard as stone just past it. Well under a creeper's
     * 3, it digs only a block or two into softer ground.
     */
    private static final float POWER = 2.2F;
    /** How strong a rocket's blast is, as an explosion's power: a creeper's. */
    private static final float ROCKET_POWER = 3.0F;
    /** How far back along the bolt from the face it struck the blast goes off, in blocks, so it is not inside the block. */
    private static final double BACK_OFF = 0.2;
    /** How much harder than the game would a blast throws a player near it, as its knockback multiplier: a mighty shove. */
    private static final float PLAYER_THROW = 4;
    /** How fast a bolt striking a player throws them along its way, and how fast up besides, in blocks a tick. */
    private static final double BOLT_THROW = 2.2;
    private static final double BOLT_LIFT = 0.7;
    /**
     * How long someone thrown floats down slowly after, in ticks, so the fall never hurts them, where the server keeps
     * no safety canopy to open over them instead.
     */
    private static final int FLOAT_DOWN_TICKS = 120;
    /** How far out from a blast, as so many times its power, players are let float down after it, in blocks. */
    private static final double THROWN_REACH = 2;

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
        afterBlast(level, at, POWER, notAboard);
    }

    /**
     * A bolt striking a player, not aboard the cannon's ship, hurts them so much, by whoever fired it, unless they are
     * in creative mode, and throws them far along its way and up, flying or not, to come down unhurt by the fall after.
     */
    static void throwStruck(ServerLevel level, @Nullable Entity shooter, HitResult hit, Vec3 along, Predicate<Entity> notAboard,
                            float damage) {
        if (!(hit instanceof EntityHitResult struck) || !throwsPlayer(struck.getEntity(), notAboard)) {
            return;
        }
        Player player = (Player) struck.getEntity();
        LaserFire.hurtPlayer(level, shooter, player, damage);
        Vec3 way = along.normalize();
        player.setDeltaMovement(player.getDeltaMovement().add(way.x * BOLT_THROW, Math.max(0, way.y) * BOLT_THROW + BOLT_LIFT, way.z * BOLT_THROW));
        player.hurtMarked = true;
        floatDown(player);
    }

    /** Whether a bolt or rocket throws someone: a player, not aboard the cannon's ship, with the server letting it. */
    static boolean throwsPlayer(Entity entity, Predicate<Entity> notAboard) {
        return SkylinersConfig.FREIGHTER_TURRET_THROWS_PLAYERS.get() && entity instanceof Player player && !player.isSpectator()
            && notAboard.test(player);
    }

    /**
     * The players a blast of so much power threw, after it: thrown as well where they fly in creative mode, which the
     * game's blast leaves standing, by telling their game the throw it gave them; and come down unhurt by the fall.
     */
    private static void afterBlast(ServerLevel level, Vec3 at, float power, Predicate<Entity> notAboard) {
        AABB near = AABB.ofSize(at, 0, 0, 0).inflate(power * THROWN_REACH);
        for (Player player : level.getEntitiesOfClass(Player.class, near, player -> throwsPlayer(player, notAboard))) {
            if (player.getAbilities().flying) {
                player.hurtMarked = true;
            }
            floatDown(player);
        }
    }

    /**
     * Lets someone thrown come down unhurt from wherever they land, however high they were thrown from, off a ship far
     * up even: their safety canopy opens once they fall a few blocks (see {@link SafetyCanopy#thrown}), or, where the
     * server keeps none, they float down slowly a while.
     */
    private static void floatDown(Player player) {
        player.resetFallDistance();
        if (SkylinersConfig.SAFETY_CANOPY.get() && player instanceof ServerPlayer thrown) {
            SafetyCanopy.thrown(thrown);
        } else {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, FLOAT_DOWN_TICKS, 0, false, false, true));
        }
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
        afterBlast(level, at, ROCKET_POWER, notAboard);
    }

    /**
     * What the blast may break and whom it may hurt or throw: no ship's blocks; hostile mobs not aboard hurt, and
     * players not aboard thrown far and hurt too unless they are in creative mode.
     */
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
            return hurts(entity) || throwsPlayer(entity, notAboard) && !((Player) entity).getAbilities().invulnerable;
        }

        @Override
        public float getKnockbackMultiplier(Entity entity) {
            if (throwsPlayer(entity, notAboard)) {
                return PLAYER_THROW;
            }
            return hurts(entity) ? super.getKnockbackMultiplier(entity) : 0;
        }

        private boolean hurts(Entity entity) {
            return entity instanceof Enemy && notAboard.test(entity);
        }
    }
}
