package dev.skyliners.impact;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.compat.SableFlight;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What a ship feels as a shot strikes her: a laser cannon's bolt or rocket striking a block of any assembled ship but
 * the one it was fired from, a freighter's, an airliner's or a zeppelin's. No shot breaks a ship's blocks, so she takes
 * no harm, but she is shoved where she was struck, along the shot's way, enough to rock her and to read as a hit and
 * never enough to throw her over or out of the sky: a bolt lightly, a rocket harder, a light ship more than a heavy
 * one, and a stream of shots no harder all told than {@link #MOST_GATHERED}, let up again as she rides it out. The hit
 * rings out where it struck, a bolt's ping off her plates or a rocket's boom and crunch, heard aboard her and off;
 * sparks and smoke burst there among her blocks, riding along with her; and the view of everyone aboard her is jolted,
 * the harder the nearer the hit and the heavier the shot, and a little that of anyone standing near her outside. Off
 * with the server setting "freighter.shipHits".
 *
 * <p>Whatever fires at a ship calls {@link #struck} where its shot meets a block: the laser cannon's bolts and rockets
 * do.
 */
public final class ShipHits {
    /**
     * How heavy a ship is, in Sable's units (a plain block weighs one), that a shot shoves exactly as hard as its kind
     * says: a freighter's weight. A lighter ship is shoved harder by the square root of how much lighter, a heavier one
     * less, but never more than {@link #LIGHTEST_SHARE} or less than {@link #HEAVIEST_SHARE} of it.
     */
    private static final double REFERENCE_MASS = 2500;
    private static final double LIGHTEST_SHARE = 2;
    private static final double HEAVIEST_SHARE = 0.5;
    /**
     * How much speed the shots striking one ship of {@link #REFERENCE_MASS} may give her all told, in blocks a second,
     * a lighter or heavier one as much more or less as each shot is, before any more only ring and jolt: one rocket's
     * worth, so no burst of bolts nor salvo of rockets shoves her harder than a rocket does. And how fast she rides it
     * out, in blocks a second each tick: four bolts fill it, and a long burst after them adds a little each second.
     */
    private static final double MOST_GATHERED = 1.2;
    private static final double LET_UP = 0.01;
    /**
     * How far from a hit the view of those aboard her is jolted its fullest, and from there how far it fades to its
     * least, a share of its fullest, at the far end of a long ship, in blocks.
     */
    private static final double JOLT_FULL_WITHIN = 4;
    private static final double JOLT_FADES_OVER = 40;
    private static final float JOLT_LEAST_SHARE = 0.3F;
    /** How far off a hit those standing near her outside feel it, in blocks, and how much of a jolt aboard at most. */
    private static final double JOLT_OUTSIDE_REACH = 16;
    private static final float JOLT_OUTSIDE_SHARE = 0.4F;
    /**
     * How far off a hit's sparks and smoke are seen, in blocks: past a laser cannon's reach, so its gunner sees where
     * their shots land; how far they spread, in blocks, and how fast they fly, in blocks a tick.
     */
    private static final double SEEN = 96;
    private static final double SPREAD = 0.25;
    private static final double SPARK_SPEED = 0.2;
    private static final double SMOKE_SPEED = 0.02;
    /**
     * How far off the server sends a sound for each of its volume, in blocks, as it does any sound of the mod's; a
     * hit's sound is played as loud as reaches as far as it is heard, and each fades out over just as far, its
     * attenuation distance in tools/generate_resources.mjs (IMPACT_SOUNDS) being this.
     */
    private static final double SENT_PER_VOLUME = 16;
    /** How far a hit's pitch wanders either way from its own, as a share of it. */
    private static final float PITCH_WANDER = 0.08F;
    /** What a ship has gathered of the shots that struck her, ship by ship; let go once she has ridden them out. */
    private static final Map<UUID, Gathered> GATHERED = new HashMap<>();

    /**
     * What struck her: how much speed it gives a ship of {@link #REFERENCE_MASS}, in blocks a second, and how fast it
     * sets her turning at most, in degrees a second; how hard it jolts the view aboard her nearest it, from 0 to 1 (see
     * {@link ImpactJolt}); how it sounds, how far off it is heard, in blocks, its pitch; and how many sparks and puffs
     * of smoke burst where it struck, and of what.
     */
    public enum Shot {
        /**
         * A laser bolt: a light tap, a ping off her plates heard as far as a laser cannon reaches, a few sparks and a
         * puff of scorch.
         */
        BOLT(0.3, 3, 0.2F, SkylinersRegistry.IMPACT_BOLT, 64, 1, ParticleTypes.ELECTRIC_SPARK, 10, ParticleTypes.SMOKE, 6),
        /** A rocket: a hard shove, a boom and crunch heard far off, glowing embers and a cloud of smoke. */
        ROCKET(1.2, 8, 0.7F, SkylinersRegistry.IMPACT_ROCKET, 128, 1.05F, ParticleTypes.LAVA, 12, ParticleTypes.LARGE_SMOKE, 14);

        private final double speed;
        private final double turn;
        private final float jolt;
        private final Supplier<SoundEvent> sound;
        private final double heard;
        private final float pitch;
        private final ParticleOptions spark;
        private final int sparks;
        private final ParticleOptions smoke;
        private final int puffs;

        Shot(double speed, double turn, float jolt, Supplier<SoundEvent> sound, double heard, float pitch, ParticleOptions spark, int sparks,
            ParticleOptions smoke, int puffs) {
            this.speed = speed;
            this.turn = turn;
            this.jolt = jolt;
            this.sound = sound;
            this.heard = heard;
            this.pitch = pitch;
            this.spark = spark;
            this.sparks = sparks;
            this.smoke = smoke;
            this.puffs = puffs;
        }
    }

    private ShipHits() {
    }

    /**
     * A shot has struck a block, travelling along a way in the world: if the block is one of an assembled ship's, as a
     * trace or a clip gives it (among her blocks, in her plot), and she is not the ship it was fired from, none for
     * none, she is shoved where it struck, it rings out, sparks fly and those aboard her and near are jolted. Where it
     * struck may be in the world or among her blocks. Whether it struck a ship.
     */
    public static boolean struck(ServerLevel level, BlockPos block, Vec3 at, Vec3 way, Shot shot, @Nullable UUID firedFrom) {
        UUID ship = SkylinersConfig.FREIGHTER_SHIP_HITS.get() ? SableFlight.bodyAt(level, block) : null;
        double mass = ship == null ? Double.NaN : SableFlight.massOf(level, ship);
        if (ship == null || ship.equals(firedFrom) || !(mass > 0) || way.lengthSqr() < Mth.EPSILON) {
            return false;
        }
        Vec3 inWorld = SableFlight.toWorld(level, at);
        double share = Mth.clamp(Math.sqrt(REFERENCE_MASS / mass), HEAVIEST_SHARE, LIGHTEST_SHARE);
        double taken = gather(ship, level.getGameTime(), shot.speed) / shot.speed;
        if (taken > 0) {
            SableFlight.strike(level, block, inWorld, way.normalize().scale(shot.speed * share * taken), Math.toRadians(shot.turn * share * taken));
        }
        Vec3 onHer = SableFlight.toPlot(level, block, inWorld, false);
        ring(level, onHer, inWorld, shot);
        jolt(level, ship, inWorld, shot);
        return true;
    }

    /**
     * How much of so much speed, as a ship of {@link #REFERENCE_MASS} would take it, a ship struck now takes: all of
     * it, until what the shots before it gave her, less what she has ridden out since, comes to {@link #MOST_GATHERED},
     * and none past it.
     */
    private static double gather(UUID ship, long now, double speed) {
        GATHERED.values().removeIf(gathered -> gathered.left(now) <= 0);
        Gathered before = GATHERED.get(ship);
        double gathered = before == null ? 0 : before.left(now);
        double given = Math.min(speed, Math.max(0, MOST_GATHERED - gathered));
        GATHERED.put(ship, new Gathered(gathered + given, now));
        return given;
    }

    /**
     * The hit heard and seen where it struck, among her blocks so that it rides along with her: its sound, by everyone
     * as near as it is heard, and its sparks and smoke, by everyone within {@link #SEEN} of it.
     */
    private static void ring(ServerLevel level, Vec3 at, Vec3 inWorld, Shot shot) {
        float pitch = shot.pitch * (1 + (level.getRandom().nextFloat() * 2 - 1) * PITCH_WANDER);
        level.playSound(null, at.x, at.y, at.z, shot.sound.get(), SoundSource.BLOCKS, (float) (shot.heard / SENT_PER_VOLUME), pitch);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceTo(inWorld) <= SEEN) {
                level.sendParticles(player, shot.spark, true, at.x, at.y, at.z, shot.sparks, SPREAD, SPREAD, SPREAD, SPARK_SPEED);
                level.sendParticles(player, shot.smoke, true, at.x, at.y, at.z, shot.puffs, SPREAD, SPREAD, SPREAD, SMOKE_SPEED);
            }
        }
    }

    /**
     * Jolts the view of everyone aboard the ship struck, its fullest within {@link #JOLT_FULL_WITHIN} of the hit and
     * fading to {@link #JOLT_LEAST_SHARE} of it over {@link #JOLT_FADES_OVER}, and of everyone standing near her
     * outside within {@link #JOLT_OUTSIDE_REACH}, less.
     */
    private static void jolt(ServerLevel level, UUID ship, Vec3 at, Shot shot) {
        for (ServerPlayer player : level.players()) {
            double away = player.getEyePosition().distanceTo(at);
            float strength = ship.equals(SableFlight.ridden(player)) ? aboard(away) : outside(away);
            if (strength > 0) {
                PacketDistributor.sendToPlayer(player, new ImpactJolt(shot.jolt * strength));
            }
        }
    }

    /** How much of a shot's jolt someone aboard her feels, so far from the hit, in blocks. */
    private static float aboard(double away) {
        double fade = Mth.clamp((away - JOLT_FULL_WITHIN) / JOLT_FADES_OVER, 0, 1);
        return (float) Mth.lerp(fade, 1, JOLT_LEAST_SHARE);
    }

    /** How much of a shot's jolt someone standing near her outside feels, so far from the hit, in blocks. */
    private static float outside(double away) {
        return away >= JOLT_OUTSIDE_REACH ? 0 : JOLT_OUTSIDE_SHARE * (float) (1 - away / JOLT_OUTSIDE_REACH);
    }

    /**
     * What the shots that struck a ship gave her all told, in blocks a second as a ship of {@link #REFERENCE_MASS}
     * would take it, as of a game tick.
     */
    private record Gathered(double speed, long at) {
        /** What is left of it at a later tick, ridden out at {@link #LET_UP} a tick; nothing at an earlier one. */
        double left(long now) {
            return now < at ? 0 : speed - (now - at) * LET_UP;
        }
    }
}
