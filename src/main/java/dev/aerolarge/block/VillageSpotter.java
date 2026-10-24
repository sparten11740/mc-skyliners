package dev.aerolarge.block;

import dev.aerolarge.Aboard;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Finds the villages under an assembled airliner for the passenger screens' route map, and tells everyone aboard
 * (see {@link VillagesBelow}). The server knows where it generated each village, so a village is found whether or
 * not its bell still stands. Every few seconds it looks through the chunks round the airliner for the start of a
 * village, only those already loaded, so looking never loads or generates one; the players aboard keep what they are
 * told, so whoever comes aboard later learns of the villages still near.
 */
public final class VillageSpotter {
    /** How often it looks, in ticks. */
    private static final int LOOK_TICKS = 100;
    /** How far round the airliner it looks, in chunks either way: further than the screens' map of what is seen. */
    private static final int REACH = 12;

    private VillageSpotter() {
    }

    /** One tick of the airliner whose autopilot this is, standing at this place in the world. */
    public static void tick(ServerLevel level, BlockEntity autopilot, Vec3 position) {
        // Spread over the ticks between looks, so the airliners do not all look on the same one.
        if (Math.floorMod(level.getGameTime() + autopilot.getBlockPos().asLong(), LOOK_TICKS) != 0) {
            return;
        }
        List<ServerPlayer> aboard = Aboard.players(autopilot);
        if (aboard.isEmpty()) {
            return;
        }
        List<BlockPos> villages = villages(level, SectionPos.blockToSectionCoord(position.x), SectionPos.blockToSectionCoord(position.z));
        if (!villages.isEmpty()) {
            VillagesBelow below = new VillagesBelow(villages);
            for (ServerPlayer player : aboard) {
                PacketDistributor.sendToPlayer(player, below);
            }
        }
    }

    /** The middles of the villages whose start is in a loaded chunk within reach of this chunk. */
    private static List<BlockPos> villages(ServerLevel level, int chunkX, int chunkZ) {
        Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<BlockPos> villages = new ArrayList<>();
        for (int x = chunkX - REACH; x <= chunkX + REACH; x++) {
            for (int z = chunkZ - REACH; z <= chunkZ + REACH; z++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk != null) {
                    startedIn(chunk, structures, villages);
                }
            }
        }
        return villages;
    }

    /** Adds the middle of each village that starts in the chunk. */
    private static void startedIn(LevelChunk chunk, Registry<Structure> structures, List<BlockPos> villages) {
        for (Map.Entry<Structure, StructureStart> start : chunk.getAllStarts().entrySet()) {
            if (!start.getValue().isValid() || !structures.wrapAsHolder(start.getKey()).is(StructureTags.VILLAGE)) {
                continue;
            }
            BoundingBox box = start.getValue().getBoundingBox();
            villages.add(new BlockPos(box.getCenter().getX(), 0, box.getCenter().getZ()));
        }
    }
}
