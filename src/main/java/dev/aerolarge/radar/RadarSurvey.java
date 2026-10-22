package dev.aerolarge.radar;

import com.mojang.logging.LogUtils;
import dev.aerolarge.block.Loaded;
import dev.aerolarge.block.TowerRadarBlockEntity;
import dev.aerolarge.compat.SableFlight;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * The land round the towers, as their radar surveys it, one survey for each dimension that every tower in it shares:
 * the server loads the chunks round each tower's radar screen one by one, nearest first, out to {@link #REACH} chunks,
 * takes in each one's land (see {@link LandTile}) and lets it go again. A chunk any tower has surveyed is never
 * surveyed again, so towers near each other share the work, and a new tower near an old one has its land at once. What
 * is surveyed is kept in a file in the dimension's folder, so it survives the server stopping.
 *
 * <p>Loading a chunk nobody has been near generates it, which takes the server's worker threads a moment, so the survey
 * goes slowly: a few chunks at once, a new one every few ticks, and none while the server is falling behind. Players near
 * a tower are sent the land surveyed round it, a few chunks a tick and each chunk once a session (see
 * {@link RadarLand}): their maps (the radar's, the autopilot's and the passenger screens') fill in round the tower
 * without anyone flying over it.
 */
public final class RadarSurvey {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** How far round a radar the land is surveyed, in chunks: some thousand blocks. */
    static final int REACH = 64;
    /** How many chunks are being loaded for the survey at once, and how often another one is asked for, in ticks. */
    private static final int IN_FLIGHT = 4;
    private static final int ASK_EVERY = 4;
    /** How long a chunk asked for may take to load before it is left for later, in ticks; its ticket lasts a little longer. */
    private static final int GIVE_UP = 400;
    private static final TicketType<ChunkPos> TICKET =
        TicketType.create("aerolarge_radar", Comparator.comparingLong(ChunkPos::toLong), GIVE_UP + 100);
    /** How long a tick may take on average, in milliseconds, for the survey to ask for more: past it, the server is behind. */
    private static final double BUSY_MILLIS = 40;
    /** How many chunks round a radar are looked at each tick, for the next one to survey or to send. */
    private static final int LOOK_PER_TICK = 256;
    /** How many chunks of land a player is sent each tick, at most, and how near a radar they must be, in blocks. */
    private static final int SEND_PER_TICK = 8;
    private static final double AUDIENCE = 96;
    /** The file format's version: a file of another is started afresh. */
    private static final int VERSION = 1;
    /** The chunks round one within reach, nearest first, as offsets. */
    private static final List<ChunkPos> ROUND = round();
    /** The radar screens loaded on the server, round which the land is surveyed. */
    public static final Loaded<TowerRadarBlockEntity> RADARS = new Loaded<>(TowerRadarBlockEntity.class);
    private static final Map<ServerLevel, RadarSurvey> SURVEYS = new IdentityHashMap<>();

    private final Path file;
    private final Long2ObjectOpenHashMap<LandTile> land = new Long2ObjectOpenHashMap<>();
    /** The chunks asked for and not loaded yet, and when each was asked for. */
    private final Long2LongOpenHashMap asked = new Long2LongOpenHashMap();
    /** How far round each radar the survey has got, by the radar's position, as an index into {@link #ROUND}. */
    private final Map<BlockPos, Integer> progress = new HashMap<>();
    /** What each player near a tower has been sent this session, and how far round the radar the sending has got. */
    private final Map<UUID, Audience> audiences = new HashMap<>();
    private int nextRadar;
    private boolean dirty;

    private static final class Audience {
        private final LongOpenHashSet sent = new LongOpenHashSet();
        private int next;
    }

    private RadarSurvey(Path file) {
        this.file = file;
    }

    private static RadarSurvey of(ServerLevel level) {
        return SURVEYS.computeIfAbsent(level, RadarSurvey::open);
    }

    private static RadarSurvey open(ServerLevel level) {
        Path folder = DimensionType.getStorageFolder(level.dimension(), level.getServer().getWorldPath(LevelResource.ROOT));
        RadarSurvey survey = new RadarSurvey(folder.resolve("data").resolve("aerolarge_radar.dat"));
        survey.load();
        return survey;
    }

    /**
     * Each tick, while any radar screen is loaded: takes in the chunks asked for that have loaded, asks for more round
     * the radars, and sends the players near them what is new to them.
     */
    public static void tick(ServerLevel level) {
        // A radar screen aboard an airliner stands among its blocks, nowhere near the land it flies over.
        List<TowerRadarBlockEntity> radars = RADARS.in(level).stream()
            .filter(radar -> !SableFlight.aboard(level, Vec3.atCenterOf(radar.getBlockPos())))
            .toList();
        if (radars.isEmpty()) {
            return;
        }
        RadarSurvey survey = of(level);
        survey.collect(level);
        if (level.getGameTime() % ASK_EVERY == 0 && !busy(level)) {
            survey.ask(level, radars);
        }
        for (ServerPlayer player : level.players()) {
            survey.send(player, radars);
        }
    }

    private static boolean busy(ServerLevel level) {
        return level.getServer().getAverageTickTimeNanos() / 1_000_000.0 > BUSY_MILLIS;
    }

    /** Takes in the chunks asked for that have loaded; leaves for later those that have taken too long. */
    private void collect(ServerLevel level) {
        long now = level.getGameTime();
        for (Long2LongOpenHashMap.Entry entry : new ArrayList<>(asked.long2LongEntrySet())) {
            long at = entry.getLongKey();
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(at), ChunkPos.getZ(at));
            if (chunk != null) {
                land.put(at, LandTile.sample(level, chunk));
                dirty = true;
                asked.remove(at);
            } else if (now - entry.getLongValue() > GIVE_UP) {
                asked.remove(at);
            }
        }
    }

    /** Asks for the next chunk not yet surveyed round one of the radars, taking them in turn. */
    private void ask(ServerLevel level, List<TowerRadarBlockEntity> radars) {
        if (asked.size() >= IN_FLIGHT) {
            return;
        }
        nextRadar = (nextRadar + 1) % radars.size();
        BlockPos radar = radars.get(nextRadar).getBlockPos().immutable();
        long at = nextUnsurveyed(radar);
        if (at == Long.MIN_VALUE) {
            return;
        }
        ChunkPos chunk = new ChunkPos(at);
        level.getChunkSource().addRegionTicket(TICKET, chunk, 0, chunk);
        asked.put(at, level.getGameTime());
    }

    /** The next chunk round a radar not surveyed or asked for yet, looking a little further each tick; none if it found none. */
    private long nextUnsurveyed(BlockPos radar) {
        // Round again from the nearest once all have been looked at: a chunk that took too long to load is asked for again.
        int index = progress.getOrDefault(radar, 0) % ROUND.size();
        ChunkPos middle = new ChunkPos(radar);
        long found = Long.MIN_VALUE;
        for (int looked = 0; looked < LOOK_PER_TICK && index < ROUND.size() && found == Long.MIN_VALUE; looked++, index++) {
            ChunkPos offset = ROUND.get(index);
            long at = ChunkPos.asLong(middle.x + offset.x, middle.z + offset.z);
            if (!land.containsKey(at) && !asked.containsKey(at)) {
                found = at;
            }
        }
        progress.put(radar, index);
        return found;
    }

    /**
     * Sends a player near a radar the land round it they have not been sent yet, a few chunks a tick, going round and
     * round, so what is surveyed later reaches them too.
     */
    private void send(ServerPlayer player, List<TowerRadarBlockEntity> radars) {
        TowerRadarBlockEntity radar = nearest(player, radars);
        if (radar == null) {
            return;
        }
        Audience audience = audiences.computeIfAbsent(player.getUUID(), ignored -> new Audience());
        ChunkPos middle = new ChunkPos(radar.getBlockPos());
        List<RadarLand.Chunk> chunks = new ArrayList<>();
        for (int looked = 0; looked < LOOK_PER_TICK && chunks.size() < SEND_PER_TICK; looked++) {
            audience.next = (audience.next + 1) % ROUND.size();
            ChunkPos offset = ROUND.get(audience.next);
            long at = ChunkPos.asLong(middle.x + offset.x, middle.z + offset.z);
            LandTile tile = land.get(at);
            if (tile != null && audience.sent.add(at)) {
                chunks.add(new RadarLand.Chunk(at, tile));
            }
        }
        if (!chunks.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new RadarLand(chunks));
        }
    }

    /** The radar screen within {@link #AUDIENCE} blocks of a player, the nearest; null for none. */
    @Nullable
    private static TowerRadarBlockEntity nearest(ServerPlayer player, List<TowerRadarBlockEntity> radars) {
        TowerRadarBlockEntity nearest = null;
        double best = AUDIENCE * AUDIENCE;
        for (TowerRadarBlockEntity radar : radars) {
            double distance = player.distanceToSqr(Vec3.atCenterOf(radar.getBlockPos()));
            if (distance < best) {
                best = distance;
                nearest = radar;
            }
        }
        return nearest;
    }

    /** A player has left: what they were sent this session is forgotten, so they are sent it all again next time. */
    public static void forget(ServerPlayer player) {
        SURVEYS.values().forEach(survey -> survey.audiences.remove(player.getUUID()));
    }

    /** The level is saved: so is its survey, in the background, if anything new has been surveyed. */
    public static void save(ServerLevel level) {
        RadarSurvey survey = SURVEYS.get(level);
        if (survey != null && survey.dirty) {
            survey.dirty = false;
            Map<Long, LandTile> snapshot = new HashMap<>(survey.land);
            Util.ioPool().execute(() -> survey.write(snapshot));
        }
    }

    /** The level is unloaded: its survey is saved at once, and closed. */
    public static void unload(ServerLevel level) {
        RadarSurvey survey = SURVEYS.remove(level);
        if (survey != null && survey.dirty) {
            survey.write(new HashMap<>(survey.land));
        }
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != VERSION) {
                return;
            }
            for (int count = in.readInt(); count > 0; count--) {
                long at = in.readLong();
                land.put(at, LandTile.read(in));
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("The radar survey in {} could not be read; it starts afresh", file, e);
            land.clear();
        }
    }

    /** Writes the land surveyed to the file, by way of a temporary one, so a write cut short never leaves it broken. */
    private void write(Map<Long, LandTile> tiles) {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(temporary)))) {
                out.writeInt(VERSION);
                out.writeInt(tiles.size());
                for (Map.Entry<Long, LandTile> tile : tiles.entrySet()) {
                    out.writeLong(tile.getKey());
                    tile.getValue().write(out);
                }
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.warn("The radar survey could not be saved to {}", file, e);
        }
    }

    private static List<ChunkPos> round() {
        List<ChunkPos> round = new ArrayList<>();
        for (int x = -REACH; x <= REACH; x++) {
            addRow(round, x);
        }
        round.sort(Comparator.comparingInt(offset -> offset.x * offset.x + offset.z * offset.z));
        return List.copyOf(round);
    }

    private static void addRow(List<ChunkPos> round, int x) {
        for (int z = -REACH; z <= REACH; z++) {
            if (x * x + z * z <= REACH * REACH) {
                round.add(new ChunkPos(x, z));
            }
        }
    }
}
