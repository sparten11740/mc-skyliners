package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.block.SearchlightBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The beams of the zeppelins' lit searchlights, as this client sees them (see {@link SearchlightBlockEntity}): a cone
 * of warm light widening from the lens the way the light points, and where it meets the land a pool of light on the
 * ground, both glowing over whatever is behind them, however dark the night. The ground is found along the beam from
 * the land's heights; under a zeppelin standing on the ground, whose own blocks those heights would be, it is the
 * ground under the light.
 */
public final class SearchlightBeams {
    /** How near a lit searchlight is drawn, in blocks, and how far its beam reaches at most. */
    private static final double SEEN = 256;
    private static final double REACH = 256;
    /** How far apart the ground is sought along the beam, and how far under a light it is sought, in blocks. */
    private static final double MARCH_STEP = 1;
    private static final int GROUND_REACH = 64;
    /**
     * The beam: how wide it leaves the lens, in blocks, and how much wider it grows each block it goes, a share; the
     * pool: how wide it is for each block the beam has come, a share, and how far over the ground it is drawn.
     */
    private static final float LENS = 0.35F;
    private static final float SPREAD = 0.08F;
    private static final float POOL = 0.11F;
    private static final double POOL_LIFT = 0.06;
    /** How many sides the beam and the pool are drawn with. */
    private static final int SIDES = 16;
    /** The light's warm white, faint along the beam and brighter in the pool. */
    private static final int BEAM = 0x2CFFF1C8;
    private static final int GLOW = 0x6CFFF1C8;

    private SearchlightBeams() {
    }

    /** After the particles are drawn: every lit searchlight near enough to see, its beam and its pool. */
    public static void stage(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || level == null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        Matrix4f pose = event.getPoseStack().last().pose();
        for (SearchlightBlockEntity light : lit(level)) {
            SableClient.View seen = SableClient.view(Vec3.atCenterOf(light.getBlockPos()).add(0, -0.3, 0), light.aim(), new Vec3(0, 1, 0), partialTick);
            Vec3 from = seen != null ? seen.eye() : Vec3.atCenterOf(light.getBlockPos());
            Vec3 way = seen != null ? seen.forward() : light.aim();
            if (from.distanceTo(camera) <= SEEN) {
                draw(consumer, pose, camera, from, way, ground(level, from, way, REACH));
            }
        }
        buffers.endBatch(RenderType.lightning());
    }

    /** The searchlights loaded on this client that are lit. */
    private static Iterable<SearchlightBlockEntity> lit(ClientLevel level) {
        return SearchlightBlockEntity.loaded(level).stream().filter(SearchlightBlockEntity::lit).toList();
    }

    /**
     * Where a beam meets the land, within so many blocks of where it leaves; null where it meets none first or reaches
     * land not loaded.
     */
    @Nullable
    static Vec3 ground(ClientLevel level, Vec3 from, Vec3 way, double reach) {
        double under = groundUnder(level, from);
        for (double along = MARCH_STEP; along <= reach; along += MARCH_STEP) {
            Vec3 point = from.add(way.scale(along));
            int x = Mth.floor(point.x);
            int z = Mth.floor(point.z);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                return null;
            }
            double top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            double land = top > from.y + 1 ? under : top;
            if (point.y <= land) {
                return new Vec3(point.x, land, point.z);
            }
        }
        return null;
    }

    /** The top of the first solid block under a point, within reach; far below for none. */
    private static double groundUnder(ClientLevel level, Vec3 point) {
        BlockPos at = BlockPos.containing(point);
        for (int down = 1; down <= GROUND_REACH; down++) {
            BlockPos below = at.below(down);
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return below.getY() + 1;
            }
        }
        return Double.NEGATIVE_INFINITY;
    }

    /** A beam from the lens to the ground, or as far as it reaches, and the pool where it meets the ground. */
    private static void draw(VertexConsumer consumer, Matrix4f pose, Vec3 camera, Vec3 from, Vec3 way, @Nullable Vec3 hit) {
        double length = hit != null ? hit.distanceTo(from) : REACH;
        Vec3 to = from.add(way.scale(length));
        Vector3f side = new Vector3f((float) way.z, 0, (float) -way.x);
        if (side.lengthSquared() < 1.0E-4F) {
            side.set(1, 0, 0);
        }
        side.normalize();
        Vector3f across = new Vector3f((float) way.x, (float) way.y, (float) way.z).cross(side).normalize();
        float end = LENS + (float) length * SPREAD;
        for (int k = 0; k < SIDES; k++) {
            double a = 2 * Math.PI * k / SIDES;
            double b = 2 * Math.PI * (k + 1) / SIDES;
            vertex(consumer, pose, camera, ring(from, side, across, a, LENS), BEAM);
            vertex(consumer, pose, camera, ring(from, side, across, b, LENS), BEAM);
            vertex(consumer, pose, camera, ring(to, side, across, b, end), BEAM & 0x00FFFFFF);
            vertex(consumer, pose, camera, ring(to, side, across, a, end), BEAM & 0x00FFFFFF);
        }
        if (hit != null) {
            pool(consumer, pose, camera, hit, (float) length * POOL);
        }
    }

    /** A disc of light lying on the ground round where a beam meets it, brightest in its middle. */
    static void pool(VertexConsumer consumer, Matrix4f pose, Vec3 camera, Vec3 ground, float radius) {
        Vec3 middle = ground.add(0, POOL_LIFT, 0);
        Vector3f east = new Vector3f(1, 0, 0);
        Vector3f south = new Vector3f(0, 0, 1);
        for (int k = 0; k < SIDES; k++) {
            double a = 2 * Math.PI * k / SIDES;
            double b = 2 * Math.PI * (k + 1) / SIDES;
            vertex(consumer, pose, camera, middle, GLOW);
            vertex(consumer, pose, camera, middle, GLOW);
            vertex(consumer, pose, camera, ring(middle, east, south, b, radius), GLOW & 0x00FFFFFF);
            vertex(consumer, pose, camera, ring(middle, east, south, a, radius), GLOW & 0x00FFFFFF);
        }
    }

    /** A point on a ring round a middle, so far round it and so wide. */
    private static Vec3 ring(Vec3 middle, Vector3f one, Vector3f other, double angle, float radius) {
        double cos = Math.cos(angle) * radius;
        double sin = Math.sin(angle) * radius;
        return middle.add(one.x * cos + other.x * sin, one.y * cos + other.y * sin, one.z * cos + other.z * sin);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Vec3 camera, Vec3 at, int colour) {
        consumer.addVertex(pose, (float) (at.x - camera.x), (float) (at.y - camera.y), (float) (at.z - camera.z)).setColor(colour);
    }
}
