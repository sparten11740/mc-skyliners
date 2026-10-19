package dev.aerolarge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Keeps the cabin free of mobs that spawn on their own: nothing natural spawns anywhere with airframe above and
 * below it, which is the cabin, the pool and the hold, but not the ground under the wings. Spawners, eggs and
 * commands still work.
 */
final class AirlinerSpawnGuard {
    /** The hull, floor, windows and panels; not the landing gear, which has open ground right under it. */
    private static final TagKey<Block> AIRFRAME = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "airframe"));
    /** Far enough up to reach the crown from the floor of the largest cabin. */
    private static final int REACH_UP = 48;
    /** Far enough down to reach the hull's belly through the pool and the hold. */
    private static final int REACH_DOWN = 24;

    private AirlinerSpawnGuard() {
    }

    static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        MobSpawnType type = event.getSpawnType();
        if ((type == MobSpawnType.NATURAL || type == MobSpawnType.CHUNK_GENERATION) && insideAirliner(event.getLevel(), event.getPos())) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    /**
     * Whether the position is inside an airliner. Most spawn attempts are under open sky, so those are ruled out first
     * from the heightmap; then the first solid block above has to be airframe (the crown, a light, a window), and
     * airframe has to lie somewhere below, through any pool or hold.
     */
    static boolean insideAirliner(LevelReader level, BlockPos pos) {
        if (pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ())) {
            return false;
        }
        BlockPos.MutableBlockPos at = pos.mutable();
        boolean roofed = false;
        for (int i = 0; i < REACH_UP && !roofed; i++) {
            BlockState above = level.getBlockState(at.move(Direction.UP));
            // Up through the air and the pool's water to the first solid block.
            if (above.getFluidState().isEmpty() && !above.getCollisionShape(level, at).isEmpty()) {
                if (!above.is(AIRFRAME)) {
                    return false;
                }
                roofed = true;
            }
        }
        if (!roofed) {
            return false;
        }
        at.set(pos);
        for (int i = 0; i < REACH_DOWN; i++) {
            if (level.getBlockState(at.move(Direction.DOWN)).is(AIRFRAME)) {
                return true;
            }
        }
        return false;
    }
}
