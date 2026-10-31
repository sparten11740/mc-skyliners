package dev.aerolarge.client;

import dev.aerolarge.AerolargeClientConfig;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Keeps the rain and snow out of ships (Sable's assembled airframes, see {@link SableClient#shelters}), and the weather
 * fair over the clouds. The game lets rain and snow fall in each column of the world down to the highest of the world's
 * own blocks there, which knows nothing of a ship's blocks, so they fell straight through her into her cabins and
 * rooms. Here, each frame it rains, the ships round the camera are looked up: in each column where rain or snow is
 * drawn it falls only down to the highest block of a ship over it, as on her roof, and its splashes and its patter land
 * there; so under a roof high over a story, as in the rocket's lower stories, none is drawn or heard at all. The
 * clouds are kept out of her by {@link CloudlessCabin}.
 *
 * <p>Over the clouds the weather is fair, as it is over a real storm: as the camera climbs out of the top of the cloud
 * layer the rain and the snow fade away, the storm's grey sky clearing and their patter falling silent, and as it comes
 * down into the clouds again they come back. Its lightning keeps to the land under the clouds too: the game sends a
 * bolt, its flash and its thunder to every player near it across, however high, so in space the storm under her was
 * heard thundering; now a client whose camera is out of the clouds drops the bolt as it arrives. Never with the
 * {@code fairOverClouds} setting off. Its calls come from {@code mixin.LevelRendererMixin}, {@code mixin.LevelMixin}
 * and, for the lightning, the client's entities joining their level.
 */
public final class ShipWeather {
    /** How far round the camera, across and up and down, rain and snow are drawn at most, in blocks: the game's own. */
    private static final int REACH = 10;
    /**
     * How far over the camera a ship's roof is looked for, in blocks: past the floors of several stories over the
     * camera's, so that a story under them hears none of the rain on her roof.
     */
    private static final int ROOF_REACH = 48;
    /** How far over the clouds' top the weather has faded away, in blocks. */
    private static final double FAIR_OVER = 8;
    /** How little of the weather a camera sees for a lightning bolt, its flash and its thunder to be kept from it. */
    private static final float BOLTS_SEEN = 0.5F;

    /** The ships round the camera this frame, and the row the camera is in. */
    private static List<SableClient.Shelter> shelters = List.of();
    private static int cameraY;

    private ShipWeather() {
    }

    /**
     * Looks up the ships round the camera while it rains or snows: once a frame before the rain is drawn, once a tick
     * before it splashes.
     */
    public static void look(Level level, Vec3 camera, float partialTick) {
        cameraY = Mth.floor(camera.y);
        boolean falling = level.getRainLevel(partialTick) > 0;
        shelters = falling ? SableClient.shelters(level, new AABB(camera, camera).inflate(REACH + 1).expandTowards(0, ROOF_REACH, 0), partialTick)
            : List.of();
    }

    /**
     * How low rain and snow fall in a column of the world, given the row the game lets them fall to there: no lower
     * than just over the highest block of a ship in the column, as far over the camera as roofs are looked for.
     */
    public static int fallsTo(Level level, int x, int z, int height) {
        int falls = height;
        for (SableClient.Shelter ship : shelters) {
            falls = Math.max(falls, roof(level, ship, x, z, Math.max(height, cameraY - REACH)));
        }
        return falls;
    }

    /**
     * Just over a ship's highest block in a column of the world that keeps the weather off, from as far over the camera
     * as roofs are looked for down to the lowest row given; lower than any row for none. The column is followed down
     * among her blocks as she lies, a block at a time.
     */
    private static int roof(Level level, SableClient.Shelter ship, int x, int z, int lowest) {
        AABB bounds = ship.bounds();
        double middleX = x + 0.5;
        double middleZ = z + 0.5;
        if (middleX < bounds.minX || middleX > bounds.maxX || middleZ < bounds.minZ || middleZ > bounds.maxZ) {
            return Integer.MIN_VALUE;
        }
        int top = Math.min(cameraY + ROOF_REACH, Mth.floor(bounds.maxY));
        int bottom = Math.max(lowest, Mth.floor(bounds.minY));
        Vec3 start = ship.among(middleX, top + 0.5, middleZ);
        Vec3 up = ship.up();
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        for (int y = top; y >= bottom; y--) {
            double down = top - y;
            if (keepsOff(level.getBlockState(cell.set(start.x - up.x * down, start.y - up.y * down, start.z - up.z * down)))) {
                return y + 1;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** Whether a block keeps the weather off, as one of the world's own would: solid, or water. */
    private static boolean keepsOff(BlockState state) {
        return state.blocksMotion() || !state.getFluidState().isEmpty();
    }

    /**
     * Keeps a lightning bolt off the client while its camera is out of the clouds over the storm: it still strikes the
     * land under them on the server, but neither it, nor its flash, nor its thunder reaches up out of the clouds.
     */
    public static void strikeBelow(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ClientLevel level && event.getEntity() instanceof LightningBolt && seen(level) < BOLTS_SEEN) {
            event.setCanceled(true);
        }
    }

    /**
     * How much of the weather is seen where the camera is, from 1 at the clouds' top and below it to nothing a few
     * blocks over it, eased in and out; all of it in a world without clouds, before the camera is placed, and with the
     * {@code fairOverClouds} setting off.
     */
    public static float seen(ClientLevel level) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        float clouds = level.effects().getCloudHeight();
        if (!camera.isInitialized() || Float.isNaN(clouds) || !AerolargeClientConfig.FAIR_OVER_CLOUDS.get()) {
            return 1;
        }
        double over = (camera.getPosition().y - clouds - CloudlessCabin.CLOUD_DEPTH) / FAIR_OVER;
        double cleared = Mth.clamp(over, 0, 1);
        return (float) (1 - cleared * cleared * (3 - 2 * cleared));
    }
}
