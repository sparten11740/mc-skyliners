package dev.aerolarge.cabin;

import dev.aerolarge.Aboard;
import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.entity.Basketball;
import dev.aerolarge.entity.Trolley;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Rough air, now and then, for an airliner in flight: a few seconds of it about once a minute in clear skies, a little
 * more in rain, and much more, longer and harder in a thunderstorm. Everyone aboard feels it (their camera shakes, see
 * the client's {@code CameraShake}), and whoever is not sitting down is thrown about the cabin, a bump every so often,
 * so it pays to be seated when the seatbelt signs come on. One per airliner, worked by its autopilot block, which
 * reads the airliner each tick anyway.
 */
public final class Turbulence {
    /** The chance each tick of running into rough air, cruising in clear skies: about once a minute. */
    private static final double ONSET_CHANCE = 1.0 / 1200;
    /** How long a patch of rough air lasts in clear skies, in ticks. */
    private static final int MIN_TICKS = 60;
    private static final int MAX_TICKS = 140;
    /** Only this high above the ground and this fast, in blocks and blocks a second. */
    private static final double MIN_HEIGHT = 20;
    private static final double MIN_SPEED = 20;
    /** Ticks between bumps, and how hard the strongest throws someone standing in clear skies, in blocks a tick. */
    private static final int BUMP_EVERY = 12;
    private static final double BUMP = 0.4;
    /**
     * The hardest any bump throws, in blocks a tick, however rough the air: half again the strongest of clear skies,
     * so a storm knocks people off their feet without ever throwing them fast enough to carry them through the
     * cabin's walls or far down the aisle.
     */
    private static final double MAX_BUMP = 0.6;
    /** How far around the airliner's middle to look for anyone aboard, in blocks per unit of scale. */
    private static final double SEARCH_PER_SCALE = 80;
    /** In rain (or snow), rough air comes half again as often as in clear skies: about every 40 seconds. */
    private static final double RAIN_ONSET = 1.5;
    /** In rain, it shakes and throws a quarter harder than in clear skies. */
    private static final float RAIN_STRENGTH = 1.25F;
    /** In rain, a patch of it lasts a quarter longer than in clear skies. */
    private static final double RAIN_LENGTH = 1.25;
    /** In a thunderstorm, rough air comes four times as often as in clear skies: about every 15 seconds. */
    private static final double STORM_ONSET = 4;
    /** In a thunderstorm, it shakes and throws up to 1.8 times as hard (the bumps no harder than {@link #MAX_BUMP}). */
    private static final float STORM_STRENGTH = 1.8F;
    /** In a thunderstorm, a patch of it lasts twice as long, so the air is rough for a good part of the storm. */
    private static final double STORM_LENGTH = 2;

    private int left;
    private float strength;

    /** One tick, with how high the airliner flies above the ground and how fast. */
    public void tick(ServerLevel level, BlockEntity anchor, Vec3 middle, double height, double speed) {
        RandomSource random = level.getRandom();
        if (left == 0) {
            if (!AerolargeConfig.TURBULENCE.get() || height < MIN_HEIGHT || speed < MIN_SPEED) {
                return;
            }
            Weather weather = Weather.at(level, middle);
            if (random.nextDouble() >= ONSET_CHANCE * weather.onset) {
                return;
            }
            left = (int) Math.round(weather.length * (MIN_TICKS + random.nextInt(MAX_TICKS - MIN_TICKS + 1)));
            strength = weather.strength * (0.5F + 0.5F * random.nextFloat());
            TurbulencePayload payload = new TurbulencePayload(strength, left);
            for (ServerPlayer player : Aboard.players(anchor)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
        left--;
        if (left % BUMP_EVERY != 0) {
            return;
        }
        double reach = SEARCH_PER_SCALE * AerolargeConfig.SCALE.get();
        List<Entity> around = level.getEntities((Entity) null, new AABB(middle, middle).inflate(reach), entity -> shaken(entity) && Aboard.isAboard(entity, anchor));
        double push = Math.min(BUMP * strength, MAX_BUMP);
        for (Entity entity : around) {
            entity.push((random.nextDouble() * 2 - 1) * push, random.nextDouble() * push * 0.6, (random.nextDouble() * 2 - 1) * push);
            entity.hurtMarked = true;
        }
    }

    /**
     * Whether rough air throws the entity about: anything alive standing about, not seated or flying, and loose balls
     * and trolleys.
     */
    private static boolean shaken(Entity entity) {
        boolean loose = entity instanceof LivingEntity || entity instanceof Basketball || entity instanceof Trolley;
        return loose && !entity.isPassenger() && !entity.isSpectator() && !(entity instanceof Player player && player.getAbilities().flying);
    }

    /**
     * The weather the airliner flies through, and what it makes of rough air: how many times as often it comes as in
     * clear skies, how many times as hard it shakes and throws, and how many times as long a patch of it lasts.
     */
    private enum Weather {
        CLEAR(1, 1, 1),
        RAIN(RAIN_ONSET, RAIN_STRENGTH, RAIN_LENGTH),
        STORM(STORM_ONSET, STORM_STRENGTH, STORM_LENGTH);

        private final double onset;
        private final float strength;
        private final double length;

        Weather(double onset, float strength, double length) {
            this.onset = onset;
            this.strength = strength;
            this.length = length;
        }

        /**
         * The weather at a point: rain or a thunderstorm only where something falls there, so a storm passing over a
         * desert brings no more than clear skies. Read from the biome rather than {@code isRainingAt}, so snow (which
         * falls high up over cooler land) counts as much as rain; an airliner in flight is out under the sky anyway.
         */
        private static Weather at(ServerLevel level, Vec3 point) {
            BlockPos at = BlockPos.containing(point);
            if (!level.isRaining() || level.getBiome(at).value().getPrecipitationAt(at) == Biome.Precipitation.NONE) {
                return CLEAR;
            }
            return level.isThundering() ? STORM : RAIN;
        }
    }
}
