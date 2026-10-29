package dev.aerolarge.client;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps the rain and snow out of ships (Sable's assembled airframes, see {@link SableClient#shelters}). The game lets
 * rain and snow fall in each column of the world down to the highest of the world's own blocks there, which knows
 * nothing of a ship's blocks, so they fell straight through her into her cabins and rooms. Here, each frame, the ships
 * round the camera are looked up: in each column where rain or snow is drawn it falls only down to the highest block of
 * a ship over it within its reach, as on her roof, and its splashes and its patter land there. The clouds are kept out
 * of her by {@link CloudlessCabin}. Its calls come from {@code mixin.LevelRendererMixin}.
 */
public final class ShipWeather {
    /** How far round the camera, across and up and down, rain and snow are drawn at most, in blocks: the game's own. */
    private static final int REACH = 10;

    /** The ships round the camera this frame, and the row the camera is in. */
    private static List<SableClient.Shelter> shelters = List.of();
    private static int cameraY;

    private ShipWeather() {
    }

    /** Looks up the ships round the camera: once a frame before the rain is drawn, once a tick before it splashes. */
    public static void look(Level level, Vec3 camera, float partialTick) {
        cameraY = Mth.floor(camera.y);
        shelters = SableClient.shelters(level, new AABB(camera, camera).inflate(REACH + 1), partialTick);
    }

    /**
     * How low rain and snow fall in a column of the world, given the row the game lets them fall to there: no lower
     * than just over the highest block of a ship in the column within their reach of the camera.
     */
    public static int fallsTo(Level level, int x, int z, int height) {
        if (shelters.isEmpty()) {
            return height;
        }
        for (int y = cameraY + REACH; y >= Math.max(height, cameraY - REACH); y--) {
            if (sheltered(level, x, y, z)) {
                return y + 1;
            }
        }
        return height;
    }

    /** Whether a block of the world is filled by a ship's block that keeps the weather off, as the world's own would. */
    private static boolean sheltered(Level level, int x, int y, int z) {
        for (SableClient.Shelter ship : shelters) {
            if (holds(level, ship, x + 0.5, y + 0.5, z + 0.5)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a point of the world lies in a block of a ship that keeps the weather off: solid, or water. */
    private static boolean holds(Level level, SableClient.Shelter ship, double x, double y, double z) {
        if (!ship.bounds().contains(x, y, z)) {
            return false;
        }
        BlockState state = level.getBlockState(BlockPos.containing(ship.among(x, y, z)));
        return state.blocksMotion() || !state.getFluidState().isEmpty();
    }
}
