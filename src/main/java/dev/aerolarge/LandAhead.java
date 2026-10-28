package dev.aerolarge;

import dev.aerolarge.compat.SableFlight;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * The land ahead of a player flying aboard an airliner or a zeppelin (any of Sable's bodies), loaded a little before
 * they see it. Minecraft loads the land round each player out to their view distance, and as they fly on it loads a
 * row of chunks each time they cross into another one, generating at once what nobody has been near: over new land at
 * a zeppelin's 15 blocks a second, that held the server up for 30 to 100 milliseconds of the 50 a tick may take, about
 * once a second, and everything aboard stuttered. So while a player rides a body flying faster than {@link #FLYING},
 * the chunks in a band past the edge of what they see, straight ahead along the way it flies and as wide as their view,
 * are asked for nearest first, a few at once and none while the server is behind, and each is kept loaded for a while,
 * until the player's own view has reached it. Over new land that takes about a third off each crossing and half the
 * longest hold-ups. What is left is mostly the new land settling as it starts to tick round the player, its leaves and
 * its water, which done ahead as well cost the server more than it saved. Experimental, and off unless
 * {@code landAhead} is set in the server config.
 */
public final class LandAhead {
    /** How fast a body must fly, in blocks a second, for the land ahead of those aboard to be loaded. */
    private static final double FLYING = 8;
    /** How many chunks are being loaded at once, at most, and how many more may be asked for each tick. */
    private static final int IN_FLIGHT = 16;
    private static final int ASK_PER_TICK = 4;
    /** How long a chunk asked for may take to load before it is left for later, in ticks. */
    private static final int GIVE_UP = 200;
    /** How long a chunk is kept loaded once asked for, in ticks: longer than a slow body takes to fly up to it. */
    private static final int KEPT = 1200;
    private static final TicketType<ChunkPos> TICKET =
        TicketType.create("aerolarge_land_ahead", Comparator.comparingLong(ChunkPos::toLong), KEPT);
    /** How long a tick may take on average, in milliseconds, for more to be asked for: past it the server is behind. */
    private static final double BUSY_MILLIS = 40;
    /** The fewest chunks Minecraft lets a player see round them. */
    private static final int LEAST_VIEW = 2;
    /** In each dimension, the chunks asked for and not loaded yet, and when each was asked for. */
    private static final Map<ServerLevel, Long2LongOpenHashMap> ASKED = new IdentityHashMap<>();

    private LandAhead() {
    }

    /** Each tick: takes off the list the chunks asked for that have loaded, and asks for more ahead of those flying. */
    public static void tick(ServerLevel level) {
        if (!AerolargeConfig.LAND_AHEAD.get() || !SableFlight.installed()) {
            return;
        }
        Long2LongOpenHashMap asked = ASKED.computeIfAbsent(level, ignored -> new Long2LongOpenHashMap());
        collect(level, asked);
        if (level.getServer().getAverageTickTimeNanos() / 1_000_000.0 > BUSY_MILLIS) {
            return;
        }
        int budget = ASK_PER_TICK;
        for (ServerPlayer player : level.players()) {
            budget = askAhead(level, asked, player, budget);
        }
    }

    /** Forgets what was asked for in a dimension as it unloads. */
    public static void drop(ServerLevel level) {
        ASKED.remove(level);
    }

    private static void collect(ServerLevel level, Long2LongOpenHashMap asked) {
        long now = level.getGameTime();
        for (Long2LongOpenHashMap.Entry entry : new ArrayList<>(asked.long2LongEntrySet())) {
            long at = entry.getLongKey();
            boolean loaded = level.getChunkSource().getChunkNow(ChunkPos.getX(at), ChunkPos.getZ(at)) != null;
            if (loaded || now - entry.getLongValue() > GIVE_UP) {
                asked.remove(at);
            }
        }
    }

    /**
     * Asks for chunks ahead of one player while they ride a body in flight, as many as the budget left this tick
     * allows; how much of it is left after.
     */
    private static int askAhead(ServerLevel level, Long2LongOpenHashMap asked, ServerPlayer player, int budget) {
        Vec3 velocity = SableFlight.ridingVelocity(player);
        double speed = Math.hypot(velocity.x, velocity.z);
        if (speed < FLYING || asked.size() >= IN_FLIGHT) {
            return budget;
        }
        double aheadX = velocity.x / speed;
        double aheadZ = velocity.z / speed;
        int most = level.getServer().getPlayerList().getViewDistance();
        int view = Mth.clamp(player.requestedViewDistance(), LEAST_VIEW, most);
        int deep = AerolargeConfig.LAND_AHEAD_CHUNKS.get();
        double fromX = player.getX() / 16;
        double fromZ = player.getZ() / 16;
        int left = budget;
        // Row by row outward from the edge of the view, each row from the middle of the way ahead out to the sides.
        for (int row = view; row <= view + deep && left > 0; row++) {
            for (int side = 0; side <= 2 * view && left > 0; side++) {
                int across = side % 2 == 0 ? side / 2 : -(side + 1) / 2;
                int x = Mth.floor(fromX + aheadX * row - aheadZ * across);
                int z = Mth.floor(fromZ + aheadZ * row + aheadX * across);
                left -= ask(level, asked, x, z) ? 1 : 0;
            }
        }
        return left;
    }

    /** Asks for one chunk unless it is loaded, already asked for, or too many are being loaded; whether it was. */
    private static boolean ask(ServerLevel level, Long2LongOpenHashMap asked, int x, int z) {
        long at = ChunkPos.asLong(x, z);
        if (asked.size() >= IN_FLIGHT || asked.containsKey(at) || level.getChunkSource().getChunkNow(x, z) != null) {
            return false;
        }
        ChunkPos chunk = new ChunkPos(x, z);
        level.getChunkSource().addRegionTicket(TICKET, chunk, 0, chunk);
        asked.put(at, level.getGameTime());
        return true;
    }
}
