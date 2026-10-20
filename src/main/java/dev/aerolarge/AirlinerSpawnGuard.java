package dev.aerolarge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Keeps the cabin free of mobs that spawn on their own: nothing natural spawns anywhere with airframe above and
 * below it, which is the cabin, the pool and the hold, but not the ground under the wings. Spawners, eggs and
 * commands still work. It goes by the airframe alone, not by light, so the cabin stays empty with its lights set for
 * the night and its shades down.
 */
final class AirlinerSpawnGuard {
    private AirlinerSpawnGuard() {
    }

    static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        MobSpawnType type = event.getSpawnType();
        // A spawn already turned down, by the mob's own rules (too light, the wrong ground) or by another mod, is left
        // alone: the hull is only looked for round spawns that would go ahead.
        if ((type == MobSpawnType.NATURAL || type == MobSpawnType.CHUNK_GENERATION) && event.getPlacementCheckResult()
            && insideAirliner(event.getLevel(), event.getPos())) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    /**
     * Whether the position is inside an airliner. Most spawn attempts are under open sky, so those are ruled out first
     * from the heightmap; then it has to be inside the hull the way someone aboard is: airframe somewhere above, past
     * whatever hangs from the ceiling (a pendant light, a climbing rope or a hoop is no roof of its own, and the floor
     * under one is cabin like the rest), and somewhere below, through any pool or hold.
     */
    private static boolean insideAirliner(LevelReader level, BlockPos pos) {
        return pos.getY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) && Aboard.insideHull(level, pos);
    }
}
