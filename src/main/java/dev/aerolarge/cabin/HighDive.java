package dev.aerolarge.cabin;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.FirstFlight;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Diving: a player who comes down into water just after bouncing off a springboard, or after a long enough fall,
 * goes in with a big splash. Dived aboard an airliner flying well clear of the ground, into its moon pool, that is
 * the Cannonball milestone (see {@link FirstFlight}). Its water is aboard, not in the world: the world under an
 * airliner standing on its blocks has the airliner itself on top, so only a pool flying high over the ground is that
 * far above it.
 * <p>
 * The server learns of a player's moves from their client a tick at a time, and a move that ends in water already
 * forgets the fall; so each player's fall and whether they were in water are kept from one tick to the next.
 */
public final class HighDive {
    /** How long after a springboard's bounce, in ticks, coming down into water still counts as a dive off it. */
    private static final int AFTER_BOUNCE = 60;
    /** A fall of at least this many blocks into water is a dive, wherever it started: off the tower's platform too. */
    private static final float DIVE_FALL = 3;
    /** How high above the ground below the pool has to be, in metres scaled like the airliner: well clear of a runway. */
    private static final double AIRBORNE_METRES = 15;
    /** The splash: how many drops, how far they spread across and up, and how fast they fly. */
    private static final int DROPS = 60;
    private static final double SPREAD = 0.8;
    private static final double SPREAD_UP = 0.3;
    private static final double DROP_SPEED = 0.4;
    /** The splash's sound: louder and deeper than an ordinary one. */
    private static final float SPLASH_VOLUME = 1.5F;
    private static final float SPLASH_PITCH = 0.7F;

    private record Fall(float distance, boolean inWater) {
    }

    /** Each player's fall and whether they were in water at the end of their last tick; gone with the player. */
    private static final Map<ServerPlayer, Fall> LAST_TICK = new WeakHashMap<>();
    /** When each player last bounced off a springboard, in game ticks. */
    private static final Map<ServerPlayer, Long> BOUNCED = new WeakHashMap<>();

    private HighDive() {
    }

    /** A player has just bounced off a springboard: if they come down in water next, they dived. */
    public static void bounced(ServerPlayer player) {
        BOUNCED.put(player, player.level().getGameTime());
    }

    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Fall last = LAST_TICK.put(player, new Fall(player.fallDistance, player.isInWater()));
        if (last == null || last.inWater() || !player.isInWater()) {
            return;
        }
        Long bounce = BOUNCED.remove(player);
        boolean offBoard = bounce != null && player.level().getGameTime() - bounce <= AFTER_BOUNCE;
        if (!offBoard && last.distance() < DIVE_FALL) {
            return;
        }
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, SPLASH_VOLUME, SPLASH_PITCH);
        level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.5, player.getZ(), DROPS, SPREAD, SPREAD_UP, SPREAD, DROP_SPEED);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(player.getX()), Mth.floor(player.getZ()));
        if (player.getY() - ground > AIRBORNE_METRES * AerolargeConfig.SCALE.get()) {
            FirstFlight.CANNONBALL.award(player);
        }
    }
}
