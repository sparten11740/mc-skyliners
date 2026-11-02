package dev.skyliners.block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * The blocks of a kind round a spot, found by looking only where they can be: what a jump light switch put up by hand
 * finds its lights by, and a passenger a seat. Only the chunks already loaded are looked into, and a section of the
 * level holding none of the kind is passed over whole by its palette, so a wide search over open country or an
 * airliner's empty spaces costs next to nothing. Positions are the level's own: round a block of an assembled airliner
 * the search looks through its plot.
 */
public final class Nearby {
    private Nearby() {
    }

    /**
     * Every block in a candidate state within the ranges across and up and down of the spot given, in blocks: chunk by
     * chunk and section by section from the lowest, each section's blocks by x, then y, then z.
     */
    public static List<BlockPos> blocks(Level level, BlockPos centre, int across, int upDown, Predicate<BlockState> candidate) {
        int minX = centre.getX() - across;
        int maxX = centre.getX() + across;
        int minZ = centre.getZ() - across;
        int maxZ = centre.getZ() + across;
        List<BlockPos> found = new ArrayList<>();
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                // Never load a chunk just to look into it.
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
                if (chunk != null) {
                    inChunk(chunk, new BlockPos(Math.max(minX, chunkX << 4), centre.getY() - upDown, Math.max(minZ, chunkZ << 4)),
                        new BlockPos(Math.min(maxX, (chunkX << 4) + 15), centre.getY() + upDown, Math.min(maxZ, (chunkZ << 4) + 15)), candidate, found);
                }
            }
        }
        return found;
    }

    /** Adds the blocks in a candidate state between two corners within one chunk, section by section. */
    private static void inChunk(LevelChunk chunk, BlockPos from, BlockPos to, Predicate<BlockState> candidate, List<BlockPos> found) {
        for (int sectionY = from.getY() >> 4; sectionY <= to.getY() >> 4; sectionY++) {
            int index = chunk.getSectionIndexFromSectionY(sectionY);
            if (index < 0 || index >= chunk.getSectionsCount()) {
                continue;
            }
            LevelChunkSection section = chunk.getSection(index);
            if (!section.hasOnlyAir() && section.maybeHas(candidate)) {
                inSection(section, from.atY(Math.max(from.getY(), sectionY << 4)), to.atY(Math.min(to.getY(), (sectionY << 4) + 15)), candidate, found);
            }
        }
    }

    /** Adds the blocks in a candidate state between two corners within one section. */
    private static void inSection(LevelChunkSection section, BlockPos from, BlockPos to, Predicate<BlockState> candidate, List<BlockPos> found) {
        for (int x = from.getX(); x <= to.getX(); x++) {
            for (int y = from.getY(); y <= to.getY(); y++) {
                inRow(section, x, y, from.getZ(), to.getZ(), candidate, found);
            }
        }
    }

    /** Adds the blocks in a candidate state along one row of a section, from one z to another. */
    private static void inRow(LevelChunkSection section, int x, int y, int fromZ, int toZ, Predicate<BlockState> candidate, List<BlockPos> found) {
        for (int z = fromZ; z <= toZ; z++) {
            if (candidate.test(section.getBlockState(x & 15, y & 15, z & 15))) {
                found.add(new BlockPos(x, y, z));
            }
        }
    }
}
