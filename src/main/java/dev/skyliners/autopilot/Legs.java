package dev.skyliners.autopilot;

import dev.skyliners.compat.SableFlight;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * How high a ship that stands on feet of its own stands over the ground, the rocket on her fins and the freighter on
 * her legs: each foot followed out into the world from among her blocks, the lowest the one that counts, so that with a
 * hollow under her control head she is down all the same.
 */
final class Legs {
    private Legs() {
    }

    /** How high the underside of a foot, given in its plot, is above the ground under it, in blocks. */
    static double overGround(Level level, Vec3 foot) {
        Vec3 at = SableFlight.toWorld(level, foot);
        return at.y - level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
    }

    /**
     * How high the lowest of the feet given, the bottom of each block as an offset from a block of the ship, stands
     * over the ground, in blocks; as high as can be with none.
     */
    static double lowest(Level level, BlockPos origin, List<BlockPos> feet) {
        double lowest = Double.MAX_VALUE;
        for (BlockPos foot : feet) {
            BlockPos at = origin.offset(foot);
            lowest = Math.min(lowest, overGround(level, new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5)));
        }
        return lowest;
    }
}
