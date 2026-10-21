package dev.aerolarge.client;

import com.mojang.logging.LogUtils;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.slf4j.Logger;

/**
 * What the passenger screens' route map (see {@link RouteMap}) knows of the land: the colour and height of the ground
 * in every column of every chunk seen from an airliner, and the villages the server has told of (see
 * {@code VillagesBelow}). It is kept by world and dimension, in a file of its own in the game's folder, so the map goes
 * on growing flight after flight, as a handheld map fills in.
 *
 * <p>The client only has the chunks round the player, so the land is taken in as the airliner flies over it: each tick
 * a few chunks round the airliner not yet taken in are looked at, nearest first, and those the client has are kept.
 * Each column keeps the map colour of its top block that has one (as vanilla's maps colour it), how deep the water
 * over it is, and its height, from which the map shades the slopes.
 */
public final class TerrainMap {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** How far round the airliner the land is taken in, in chunks either way. */
    private static final int REACH = 10;
    /** How many chunks round the airliner are looked at each tick, and how many of them taken in at most. */
    private static final int LOOK_PER_TICK = 48;
    private static final int TAKE_PER_TICK = 4;
    /** Water deeper than this, in blocks, is drawn as deep as this. */
    static final int DEEPEST = 10;
    /** How often what has been taken in is saved, in milliseconds, besides on leaving the world. */
    private static final long SAVE_EVERY = 5 * 60 * 1000;
    /** The file format's version: a file of another is started afresh. */
    private static final int VERSION = 1;
    /** What a column not taken in yet reads as. */
    static final int UNKNOWN = -1;
    /** The chunks round one, nearest first, as offsets. */
    private static final List<ChunkPos> ROUND = round();

    /** The map of the world and dimension the player is in, if one is open. */
    @Nullable
    private static TerrainMap current;

    private final String world;
    private final String dimension;
    /** Each chunk taken in, by its position: per column, its colour (and water depth over it), and its height. */
    private final Map<Long, Tile> tiles = new HashMap<>();
    private final Set<Long> villages = new HashSet<>();
    /** Which of {@link #ROUND} to look at next, and round which chunk. */
    private int next;
    private long lookingRound = Long.MIN_VALUE;
    private boolean dirty;
    private long savedAt = Util.getMillis();

    /** A chunk's columns, row by row: the colour (RGB, the water's depth in the top byte) and the height of each. */
    private record Tile(int[] colours, short[] heights) {
    }

    private TerrainMap(String world, String dimension) {
        this.world = world;
        this.dimension = dimension;
    }

    /** The map of the world and dimension of this level, opened (and the last one saved) as the player moves between them. */
    @Nullable
    static TerrainMap of(Level level) {
        String world = worldKey(Minecraft.getInstance());
        if (world == null) {
            return null;
        }
        String dimension = level.dimension().location().toString();
        if (current == null || !current.world.equals(world) || !current.dimension.equals(dimension)) {
            if (current != null) {
                current.save(false);
            }
            current = new TerrainMap(world, dimension);
            current.load();
        }
        return current;
    }

    /** The villages the server has told of under the airliner: kept on the map of the dimension the player is in. */
    public static void villages(List<BlockPos> found) {
        Level level = Minecraft.getInstance().level;
        TerrainMap map = level != null ? of(level) : null;
        if (map != null) {
            for (BlockPos village : found) {
                map.dirty |= map.villages.add(BlockPos.asLong(village.getX(), 0, village.getZ()));
            }
        }
    }

    /** Leaving the world: the map is saved, and closed. */
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (current != null) {
            current.save(false);
            current = null;
        }
    }

    /** One tick of an airliner over this place: takes in a few more of the chunks round it, and saves now and then. */
    void see(Level level, double x, double z) {
        long round = ChunkPos.asLong(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        if (round != lookingRound) {
            lookingRound = round;
            next = 0;
        }
        int chunkX = ChunkPos.getX(round);
        int chunkZ = ChunkPos.getZ(round);
        int taken = 0;
        for (int looked = 0; looked < LOOK_PER_TICK && taken < TAKE_PER_TICK; looked++) {
            ChunkPos offset = ROUND.get(next);
            next = (next + 1) % ROUND.size();
            long at = ChunkPos.asLong(chunkX + offset.x, chunkZ + offset.z);
            if (!tiles.containsKey(at) && takeIn(level, chunkX + offset.x, chunkZ + offset.z)) {
                taken++;
            }
        }
        if (dirty && Util.getMillis() - savedAt > SAVE_EVERY) {
            save(true);
        }
    }

    /** Takes in the chunk, if the client has it; whether it did. */
    private boolean takeIn(Level level, int chunkX, int chunkZ) {
        LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
        if (chunk == null || chunk.isEmpty()) {
            return false;
        }
        Tile tile = new Tile(new int[256], new short[256]);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 256; i++) {
            int x = i & 15;
            int z = i >> 4;
            pos.set((chunkX << 4) + x, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), (chunkZ << 4) + z);
            column(level, chunk, pos, tile, i);
        }
        tiles.put(ChunkPos.asLong(chunkX, chunkZ), tile);
        dirty = true;
        return true;
    }

    /** Takes in one column, from its top block down: its colour, the water's depth over it and its height. */
    private static void column(Level level, LevelChunk chunk, BlockPos.MutableBlockPos pos, Tile tile, int index) {
        int bottom = level.getMinBuildHeight();
        BlockState state = chunk.getBlockState(pos);
        MapColor colour = state.getMapColor(level, pos);
        // Down through whatever has no colour on a map (glass, say) to what has.
        while (colour == MapColor.NONE && pos.getY() > bottom) {
            state = chunk.getBlockState(pos.move(Direction.DOWN));
            colour = state.getMapColor(level, pos);
        }
        int depth = state.getFluidState().isEmpty() ? 0 : depth(chunk, pos.mutable(), bottom);
        tile.colours()[index] = colour.col | depth << 24;
        tile.heights()[index] = (short) pos.getY();
    }

    /** How deep the water is under its surface here, in blocks, up to {@link #DEEPEST}. */
    private static int depth(LevelChunk chunk, BlockPos.MutableBlockPos under, int bottom) {
        int depth = 0;
        while (depth < DEEPEST && under.getY() > bottom && !chunk.getBlockState(under.move(Direction.DOWN)).getFluidState().isEmpty()) {
            depth++;
        }
        return depth;
    }

    /** The column's colour (RGB, the water's depth over it in the top byte), or {@link #UNKNOWN} if not taken in yet. */
    int colour(int x, int z) {
        Tile tile = tiles.get(ChunkPos.asLong(x >> 4, z >> 4));
        return tile == null ? UNKNOWN : tile.colours()[(z & 15) << 4 | x & 15];
    }

    /** The column's height, or {@link Integer#MIN_VALUE} if not taken in yet. */
    int height(int x, int z) {
        Tile tile = tiles.get(ChunkPos.asLong(x >> 4, z >> 4));
        return tile == null ? Integer.MIN_VALUE : tile.heights()[(z & 15) << 4 | x & 15];
    }

    /** The villages known on the map, each by its middle (y 0). */
    List<BlockPos> villages() {
        return villages.stream().map(BlockPos::of).toList();
    }

    /** The folder the game keeps the maps in, one file for each dimension of each world. */
    private Path file() {
        String name = dimension.replace(':', '_').replace('/', '_') + ".dat";
        return Minecraft.getInstance().gameDirectory.toPath().resolve("aerolarge").resolve("route_maps").resolve(world).resolve(name);
    }

    private void load() {
        Path file = file();
        if (!Files.exists(file)) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != VERSION) {
                return;
            }
            for (int count = in.readInt(); count > 0; count--) {
                long at = in.readLong();
                tiles.put(at, readTile(in));
            }
            for (int count = in.readInt(); count > 0; count--) {
                villages.add(in.readLong());
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("The route map in {} could not be read; it starts afresh", file, e);
            tiles.clear();
            villages.clear();
        }
    }

    /**
     * Saves what has been taken in, if anything new has been: in the background while flying, or at once on leaving.
     * The tiles never change once taken in, so the background save writes them as they were.
     */
    private void save(boolean background) {
        if (!dirty) {
            return;
        }
        dirty = false;
        savedAt = Util.getMillis();
        Path file = file();
        List<Map.Entry<Long, Tile>> tiles = new ArrayList<>(this.tiles.entrySet());
        List<Long> villages = new ArrayList<>(this.villages);
        Runnable write = () -> write(file, tiles, villages);
        if (background) {
            Util.ioPool().execute(write);
        } else {
            write.run();
        }
    }

    private static void write(Path file, List<Map.Entry<Long, Tile>> tiles, List<Long> villages) {
        try {
            Files.createDirectories(file.getParent());
            Path written = file.resolveSibling(file.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(written)))) {
                write(out, tiles, villages);
            }
            Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.warn("The route map could not be saved to {}", file, e);
        }
    }

    private static void write(DataOutputStream out, List<Map.Entry<Long, Tile>> tiles, List<Long> villages) throws IOException {
        out.writeInt(VERSION);
        out.writeInt(tiles.size());
        for (Map.Entry<Long, Tile> tile : tiles) {
            out.writeLong(tile.getKey());
            writeTile(out, tile.getValue());
        }
        out.writeInt(villages.size());
        for (long village : villages) {
            out.writeLong(village);
        }
    }

    private static Tile readTile(DataInputStream in) throws IOException {
        Tile tile = new Tile(new int[256], new short[256]);
        for (int i = 0; i < 256; i++) {
            tile.colours()[i] = in.readInt();
        }
        for (int i = 0; i < 256; i++) {
            tile.heights()[i] = in.readShort();
        }
        return tile;
    }

    private static void writeTile(DataOutputStream out, Tile tile) throws IOException {
        for (int colour : tile.colours()) {
            out.writeInt(colour);
        }
        for (short height : tile.heights()) {
            out.writeShort(height);
        }
    }

    /**
     * The world's name for its maps' folder: the save's folder in single player, the server's address in multiplayer;
     * null with neither, as on the title screen.
     */
    @Nullable
    private static String worldKey(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        String key;
        if (server != null) {
            key = "local_" + server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
        } else {
            ServerData data = minecraft.getCurrentServer();
            if (data == null) {
                return null;
            }
            key = "server_" + data.ip;
        }
        return key.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static List<ChunkPos> round() {
        List<ChunkPos> round = new ArrayList<>();
        for (int x = -REACH; x <= REACH; x++) {
            for (int z = -REACH; z <= REACH; z++) {
                round.add(new ChunkPos(x, z));
            }
        }
        round.sort(Comparator.comparingInt(offset -> offset.x * offset.x + offset.z * offset.z));
        return round;
    }
}
