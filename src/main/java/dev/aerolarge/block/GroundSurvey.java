package dev.aerolarge.block;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * What the autopilot knows of the ground round the pin it is to land near: for every column of the chunks it has seen
 * there, how high the top is (trees, roofs, water and all), how high the ground is under any leaves, and whether the
 * top is water or lava. The ground near the pin is only there to be read while chunks round it are loaded, which is
 * while someone aboard flies close enough for the game to send it to them (or the airliner itself is over it), so the
 * survey looks at the chunks round the airliner as it flies, reads each one round the pin as it comes to be loaded,
 * and keeps it. A chunk not loaded is never read: reading it would load it, or make the world generate it, a long wait
 * on the server. Nothing is saved: after loading, the land is seen again as the airliner flies over it.
 *
 * <p>Only the chunks within {@link #RADIUS} of the pin are kept, at about a kilobyte each: at most some 10,000 of
 * them, ten megabytes, though an airliner flying over the pin sees only a band of them, a tenth of that. Each tick it
 * looks whether {@link #LOOKED_AT} of the chunks within {@link #SIGHT} of the airliner are loaded, all of them in under
 * a second, and reads at most {@link #READ} new ones: a few hundred heights.
 */
final class GroundSurvey {
    /**
     * How far round the pin the ground is surveyed, in blocks: as far as a touchdown is looked for (see
     * {@link RunwaySearch#REACH}), and beyond it as far as a landing there must know the ground: back past where the
     * flare starts, and on past where the airliner has braked to a stop (see {@link RunwayCheck}).
     */
    static final int RADIUS = RunwaySearch.REACH + 700;
    /**
     * How far round the airliner it looks for loaded chunks, in chunks: past the farthest a server sends players the
     * land (its view distance, 10 chunks unless set otherwise), so all they are sent is seen.
     */
    private static final int SIGHT = 16;
    private static final int SQUARE = 2 * SIGHT + 1;
    /** How many of the chunks round the airliner it looks at each tick, to see if they are loaded, and how many it reads. */
    private static final int LOOKED_AT = 64;
    private static final int READ = 8;

    /** The next chunk to look at round the airliner, counted across the square round it a row at a time. */
    private int next;
    /** The pin's chunk, so a pin dropped elsewhere starts a new survey. */
    private long pinChunk = Long.MIN_VALUE;
    private final Long2ObjectOpenHashMap<Patch> patches = new Long2ObjectOpenHashMap<>();
    /** The chunk last asked about, as columns are mostly asked about a few at a time in the same chunk. */
    private long lastKey = Long.MIN_VALUE;
    @Nullable
    private Patch last;

    /** The ground of one chunk, as it was when read: a column's index is its x and z in the chunk, z sixteen times. */
    static final class Patch {
        private final short[] top = new short[256];
        private final short[] ground = new short[256];
        private final long[] wet = new long[4];

        /** The height of the first air over the top of a column: over trees, roofs and water. */
        int top(int x, int z) {
            return top[index(x, z)];
        }

        /** The height of the first air over the ground, looking through leaves. */
        int ground(int x, int z) {
            return ground[index(x, z)];
        }

        /** Whether the top of a column is water or lava, not dry land. */
        boolean wet(int x, int z) {
            int index = index(x, z);
            return (wet[index >> 6] & 1L << (index & 63)) != 0;
        }

        private static int index(int x, int z) {
            return (z & 15) << 4 | x & 15;
        }
    }

    /** Surveys round a pin: from scratch if the pin is in a chunk other than the one surveyed round so far. */
    void aim(int x, int z) {
        long chunk = ChunkPos.asLong(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        if (chunk == pinChunk) {
            return;
        }
        pinChunk = chunk;
        patches.clear();
        last = null;
        lastKey = Long.MIN_VALUE;
    }

    /**
     * Looks at the next few chunks round the airliner, at a point in the world, reading those loaded within the
     * survey's radius of the pin and not yet read.
     */
    void tick(ServerLevel level, Vec3 airliner) {
        int middleX = SectionPos.blockToSectionCoord(airliner.x);
        int middleZ = SectionPos.blockToSectionCoord(airliner.z);
        int read = 0;
        for (int looked = 0; looked < LOOKED_AT && read < READ; looked++) {
            long chunk = ChunkPos.asLong(middleX + next % SQUARE - SIGHT, middleZ + next / SQUARE - SIGHT);
            next = (next + 1) % (SQUARE * SQUARE);
            if (wanted(chunk) && read(level, chunk)) {
                read++;
            }
        }
    }

    /** Whether a chunk is one to read: within the survey's radius of the pin, and not read yet. */
    private boolean wanted(long chunk) {
        long dx = ChunkPos.getX(chunk) - ChunkPos.getX(pinChunk);
        long dz = ChunkPos.getZ(chunk) - ChunkPos.getZ(pinChunk);
        return (dx * dx + dz * dz) * 256 <= (long) RADIUS * RADIUS && !patches.containsKey(chunk);
    }

    /** Reads a chunk's ground, if it is loaded: whether it was. */
    private boolean read(ServerLevel level, long key) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
        if (chunk == null) {
            return false;
        }
        Patch patch = new Patch();
        for (int index = 0; index < 256; index++) {
            readColumn(chunk, patch, index);
        }
        patches.put(key, patch);
        return true;
    }

    /** Reads one column of a chunk: the heights of its top and of its ground, and whether its top is wet. */
    private static void readColumn(LevelChunk chunk, Patch patch, int index) {
        int x = index & 15;
        int z = index >> 4;
        // A chunk's height is that of its topmost block; the first air over it is one higher.
        int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 1;
        patch.top[index] = (short) top;
        patch.ground[index] = (short) (chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1);
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        if (!chunk.getFluidState(baseX + x, top - 1, baseZ + z).isEmpty()) {
            patch.wet[index >> 6] |= 1L << (index & 63);
        }
    }

    /** The ground of the chunk a column is in, if it has been read; null while it has not been seen. */
    @Nullable
    Patch at(int x, int z) {
        long key = ChunkPos.asLong(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        // A chunk not seen yet is asked after afresh each time: it may have been read since.
        if (key != lastKey || last == null) {
            lastKey = key;
            last = patches.get(key);
        }
        return last;
    }
}
