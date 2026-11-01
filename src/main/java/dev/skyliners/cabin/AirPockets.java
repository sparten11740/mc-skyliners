package dev.skyliners.cabin;

import dev.skyliners.Aboard;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.compat.SableFlight;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Air pockets high up: now and then, flying well above the ground, the airliner drops into a pocket of sinking air and
 * falls a few blocks in a moment, until the air catches it again with a thud. Everyone aboard feels it (their view
 * drops and the cabin rattles, see the client's {@code CameraShake}), and whoever is not sitting down floats up off the
 * floor for a moment, as do loose balls and trolleys; belted in, passengers stay put. The higher the airliner flies,
 * the more often it happens, and in rain or a thunderstorm more often still, in a storm deeper too. The autopilot
 * climbs back to the height it holds. One per airliner, worked by its autopilot block beside the rough air (see
 * {@link Turbulence}).
 */
public final class AirPockets {
    /** Only this high above the ground, in blocks: lower down the air is never still enough to fall away under it. */
    private static final double MIN_HEIGHT = 60;
    /** From this high above the ground up, in blocks, pockets come as often as they ever do. */
    private static final double TOP_HEIGHT = 200;
    /** Only this fast, in blocks a second: flying, not hanging in the air on its jets. */
    private static final double MIN_SPEED = 20;
    /** The chance each tick of dropping into one at {@link #MIN_HEIGHT} in clear skies: about every four minutes. */
    private static final double LOW_CHANCE = 1.0 / (4 * 60 * 20);
    /** The chance each tick at {@link #TOP_HEIGHT} and above in clear skies: about every minute. */
    private static final double HIGH_CHANCE = 1.0 / (60 * 20);
    /** In rain they come half again as often, and in a thunderstorm three times as often. */
    private static final double RAIN_CHANCE = 1.5;
    private static final double STORM_CHANCE = 3;
    /** In a thunderstorm they drop half again as deep. */
    private static final double STORM_DEPTH = 1.5;
    /**
     * How long the drop lasts, in ticks, and how much faster it sinks the airliner each tick of it, in blocks a second:
     * some seven blocks a second down in all, a few blocks of fall before the wings catch it again.
     */
    private static final int DROP_TICKS = 12;
    private static final double SINK_PER_TICK = 0.6;
    /** How hard the drop lifts anyone standing, in blocks a tick: up off the floor for a moment, not into the ceiling. */
    private static final double FLOAT = 0.35;

    /** How many ticks of the drop are still to come, and how deep it is, about 1 (deeper in a storm). */
    private int left;
    private double depth;

    /** One tick, with how high the airliner flies above the ground and how fast. */
    public void tick(ServerLevel level, BlockEntity anchor, Vec3 middle, double height, double speed) {
        if (left > 0) {
            left--;
            SableFlight.jolt(anchor, new Vec3(0, -SINK_PER_TICK * depth, 0));
            return;
        }
        if (!SkylinersConfig.AIR_POCKETS.get() || height < MIN_HEIGHT || speed < MIN_SPEED) {
            return;
        }
        Weather weather = Weather.at(level, middle);
        if (level.getRandom().nextDouble() < chance(height, weather)) {
            drop(level, anchor, middle, weather);
        }
    }

    /** The chance each tick of dropping into a pocket at this height above the ground, in this weather. */
    private static double chance(double height, Weather weather) {
        double high = Mth.clamp((height - MIN_HEIGHT) / (TOP_HEIGHT - MIN_HEIGHT), 0, 1);
        double clear = Mth.lerp(high, LOW_CHANCE, HIGH_CHANCE);
        return clear * switch (weather) {
            case CLEAR -> 1;
            case RAIN -> RAIN_CHANCE;
            case STORM -> STORM_CHANCE;
        };
    }

    /** Into a pocket: everyone aboard told, so their view drops with the cabin's rattle, and whoever stands lifted. */
    private void drop(ServerLevel level, BlockEntity anchor, Vec3 middle, Weather weather) {
        RandomSource random = level.getRandom();
        depth = (weather == Weather.STORM ? STORM_DEPTH : 1) * (0.7 + 0.3 * random.nextDouble());
        left = DROP_TICKS;
        AirPocketPayload payload = new AirPocketPayload((float) depth);
        long seed = random.nextLong();
        for (ServerPlayer player : Aboard.players(anchor)) {
            PacketDistributor.sendToPlayer(player, payload);
            player.connection.send(new ClientboundSoundEntityPacket(SkylinersRegistry.AIR_POCKET, SoundSource.AMBIENT, player, 1.0F, 1.0F, seed));
        }
        for (Entity entity : Turbulence.loose(level, anchor, middle)) {
            entity.push(0, FLOAT * Math.min(depth, 1), 0);
            entity.hurtMarked = true;
        }
    }
}
