package dev.aerolarge.compat;

import dev.aerolarge.Aboard;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.joml.Vector3d;

/**
 * Where an assembled airliner is and how it is moving, read from Sable. A block of an assembled airliner lives in
 * Sable's plot, away from where the airliner is seen; projecting a few points of the plot out into the world gives
 * the airliner's position and the directions its nose, right wing and roof point. This is the only class that
 * touches Sable on the server, and only once Sable is known to be installed; without it nothing is ever assembled,
 * and every answer is the one for blocks standing in the world. {@link #cabinVelocity} also serves the client, for
 * entities that move the same on both sides.
 */
public final class SableFlight {
    private static final boolean SABLE = ModList.get().isLoaded("sable");

    private SableFlight() {
    }

    /**
     * The airliner's state seen from one of its blocks: positions in blocks, velocity in blocks per second, and the
     * strength of gravity where it is, in blocks per second each second.
     */
    public record State(Vec3 position, Vec3 nose, Vec3 right, Vec3 up, Vec3 velocity, double gravity) {
        /** Degrees above the horizon the nose points. */
        public double pitch() {
            return Math.toDegrees(Math.asin(Math.clamp(nose.y, -1, 1)));
        }

        /** Degrees the airliner banks, positive with the right wing down, all the way round: past 90 it is upside down. */
        public double roll() {
            return Math.toDegrees(Math.atan2(-right.y, up.y));
        }

        /** The compass heading of the nose, as Minecraft yaw: 0 south, 90 west, 180 north, 270 east. */
        public double heading() {
            return Math.toDegrees(Math.atan2(-nose.x, nose.z));
        }

        /** Degrees above the horizon the airliner is actually moving. */
        public double flightPath() {
            double horizontal = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            return horizontal < 1e-3 && Math.abs(velocity.y) < 1e-3 ? 0 : Math.toDegrees(Math.atan2(velocity.y, horizontal));
        }

        /** How fast the airliner moves the way its nose points. */
        public double airspeed() {
            return velocity.dot(nose);
        }

        /** The height it would coast up to if nothing but gravity slowed its climb. */
        public double apex() {
            double climb = Math.max(velocity.y, 0);
            return gravity > 0 ? position.y + climb * climb / (2 * gravity) : position.y;
        }
    }

    /**
     * The state of the assembled airliner the block entity is part of, with its nose pointing the given way in the
     * plot; null when it is not part of one (not assembled, or Sable not installed).
     */
    @Nullable
    public static State read(BlockEntity blockEntity, Direction nose) {
        if (!SABLE || blockEntity.getLevel() == null) {
            return null;
        }
        return Access.read(blockEntity, nose);
    }

    /**
     * Whether the entity rides (stands on, or sits in) the assembled airliner the block entity is part of; null when
     * the block entity is not part of an assembled airliner.
     */
    @Nullable
    public static Boolean rides(Entity entity, BlockEntity blockEntity) {
        return SABLE && blockEntity.getLevel() != null ? Access.rides(entity, blockEntity) : null;
    }

    /** Whether the entity rides any assembled airliner. */
    public static boolean ridesAny(Entity entity) {
        return SABLE && Access.ridesAny(entity);
    }

    /** Where a point among an assembled airliner's blocks is in the world; the point itself anywhere else. */
    public static Vec3 toWorld(Level level, Vec3 point) {
        return SABLE ? Access.toWorld(level, point) : point;
    }

    /** How fast a point among an assembled airliner's blocks moves, in blocks a second; still anywhere else. */
    public static Vec3 velocity(Level level, Vec3 point) {
        return SABLE ? Access.velocity(level, point) : Vec3.ZERO;
    }

    /**
     * How fast the cabin around a point in the world moves, in blocks a second: the airliner's, where the point is
     * inside the hull of an assembled one, and still anywhere else. Worked out from where that spot of the cabin was
     * last tick and is now, so it is the same on the client as on the server, for what moves on both.
     */
    public static Vec3 cabinVelocity(Level level, Vec3 point) {
        return SABLE ? Access.cabinVelocity(level, point) : Vec3.ZERO;
    }

    /**
     * A point in the world seen in the frame of the airliner the plot position is part of, as the airliner lies now or
     * lay a tick ago; the point itself when the position is in the world.
     */
    public static Vec3 toPlot(Level level, BlockPos plot, Vec3 point, boolean lastTick) {
        return SABLE ? Access.toPlot(level, plot, point, lastTick) : point;
    }

    /**
     * The block at a point in the world that the test accepts, looking into any assembled airliner there as well as
     * the world: its position in the world, or in the airliner's plot; null if neither has one.
     */
    @Nullable
    public static BlockPos find(Level level, Vec3 point, Predicate<BlockState> test) {
        BlockPos inWorld = BlockPos.containing(point);
        if (test.test(level.getBlockState(inWorld))) {
            return inWorld;
        }
        return SABLE ? Access.findAboard(level, point, test) : null;
    }

    /**
     * The nearest block to a point in the world that the test accepts, within the ranges across and up and down,
     * looking into any assembled airliner there as well as the world: its position in the world, or in the airliner's
     * plot; null if neither has one. Only blocks in a candidate state are tested, and a section of the level holding
     * none is passed over whole by its palette, so a wide search over open country or an airliner's empty spaces costs
     * next to nothing.
     */
    @Nullable
    public static BlockPos nearest(Level level, Vec3 point, int across, int upDown, Predicate<BlockState> candidate, Predicate<BlockPos> test) {
        BlockPos inWorld = closest(level, point, across, upDown, candidate, test);
        BlockPos aboard = SABLE ? Access.nearestAboard(level, point, across, upDown, candidate, test) : null;
        if (inWorld == null || aboard == null) {
            return inWorld == null ? aboard : inWorld;
        }
        // Distances in a plot are the same as in the world: an airliner only moves and turns.
        return inWorld.distToCenterSqr(point) <= aboard.distToCenterSqr(toPlot(level, aboard, point, false)) ? inWorld : aboard;
    }

    /**
     * The nearest block to a point among the level's own blocks, within the ranges across and up and down of the block
     * the point is in, that is in a candidate state and the test accepts; null for none.
     */
    @Nullable
    private static BlockPos closest(Level level, Vec3 point, int across, int upDown, Predicate<BlockState> candidate, Predicate<BlockPos> test) {
        BlockPos centre = BlockPos.containing(point);
        int minX = centre.getX() - across;
        int maxX = centre.getX() + across;
        int minY = centre.getY() - upDown;
        int maxY = centre.getY() + upDown;
        int minZ = centre.getZ() - across;
        int maxZ = centre.getZ() + across;
        BlockPos closest = null;
        double distance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                // Never load a chunk just to look into it.
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
                if (chunk == null) {
                    continue;
                }
                for (int sectionY = minY >> 4; sectionY <= maxY >> 4; sectionY++) {
                    int index = chunk.getSectionIndexFromSectionY(sectionY);
                    if (index < 0 || index >= chunk.getSectionsCount()) {
                        continue;
                    }
                    LevelChunkSection section = chunk.getSection(index);
                    if (section.hasOnlyAir() || !section.maybeHas(candidate)) {
                        continue;
                    }
                    for (int x = Math.max(minX, chunkX << 4); x <= Math.min(maxX, (chunkX << 4) + 15); x++) {
                        for (int y = Math.max(minY, sectionY << 4); y <= Math.min(maxY, (sectionY << 4) + 15); y++) {
                            for (int z = Math.max(minZ, chunkZ << 4); z <= Math.min(maxZ, (chunkZ << 4) + 15); z++) {
                                if (!candidate.test(section.getBlockState(x & 15, y & 15, z & 15))) {
                                    continue;
                                }
                                double away = pos.set(x, y, z).distToCenterSqr(point);
                                if (away < distance && test.test(pos)) {
                                    closest = pos.immutable();
                                    distance = away;
                                }
                            }
                        }
                    }
                }
            }
        }
        return closest;
    }

    /** Kept apart so its Sable references are only loaded when Sable is there. */
    private static final class Access {
        @Nullable
        static Boolean rides(Entity entity, BlockEntity blockEntity) {
            SubLevel airliner = Sable.HELPER.getContaining(blockEntity);
            return airliner == null ? null : airliner == Sable.HELPER.getTrackingOrVehicleSubLevel(entity);
        }

        static boolean ridesAny(Entity entity) {
            return Sable.HELPER.getTrackingOrVehicleSubLevel(entity) != null;
        }

        static Vec3 toWorld(Level level, Vec3 point) {
            return Sable.HELPER.projectOutOfSubLevel(level, point);
        }

        static Vec3 velocity(Level level, Vec3 point) {
            return Sable.HELPER.getContaining(level, point) == null ? Vec3.ZERO : Sable.HELPER.getVelocity(level, point);
        }

        static Vec3 cabinVelocity(Level level, Vec3 point) {
            for (SubLevel airliner : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(point, point))) {
                Vec3 inPlot = airliner.logicalPose().transformPositionInverse(point);
                if (Aboard.insideHull(level, BlockPos.containing(inPlot))) {
                    return point.subtract(airliner.lastPose().transformPosition(inPlot)).scale(SharedConstants.TICKS_PER_SECOND);
                }
            }
            return Vec3.ZERO;
        }

        static Vec3 toPlot(Level level, BlockPos plot, Vec3 point, boolean lastTick) {
            SubLevel airliner = Sable.HELPER.getContaining(level, plot);
            if (airliner == null) {
                return point;
            }
            return (lastTick ? airliner.lastPose() : airliner.logicalPose()).transformPositionInverse(point);
        }

        @Nullable
        static BlockPos findAboard(Level level, Vec3 point, Predicate<BlockState> test) {
            for (SubLevel airliner : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(point, point))) {
                BlockPos inPlot = BlockPos.containing(airliner.logicalPose().transformPositionInverse(point));
                if (test.test(level.getBlockState(inPlot))) {
                    return inPlot;
                }
            }
            return null;
        }

        @Nullable
        static BlockPos nearestAboard(Level level, Vec3 point, int across, int upDown, Predicate<BlockState> candidate, Predicate<BlockPos> test) {
            BoundingBox3d around = new BoundingBox3d(point.x - across, point.y - upDown, point.z - across, point.x + across, point.y + upDown, point.z + across);
            BlockPos nearest = null;
            double distance = Double.MAX_VALUE;
            for (SubLevel airliner : Sable.HELPER.getAllIntersecting(level, around)) {
                Vec3 inPlot = airliner.logicalPose().transformPositionInverse(point);
                BlockPos found = closest(level, inPlot, across, upDown, candidate, test);
                if (found != null && found.distToCenterSqr(inPlot) < distance) {
                    nearest = found;
                    distance = found.distToCenterSqr(inPlot);
                }
            }
            return nearest;
        }

        @Nullable
        static State read(BlockEntity blockEntity, Direction nose) {
            Level level = blockEntity.getLevel();
            if (Sable.HELPER.getContaining(blockEntity) == null) {
                return null;
            }
            Vec3 centre = Vec3.atCenterOf(blockEntity.getBlockPos());
            Vec3 position = Sable.HELPER.projectOutOfSubLevel(level, centre);
            Vec3 forward = direction(level, centre, position, nose);
            Vec3 right = direction(level, centre, position, nose.getClockWise());
            Vec3 up = direction(level, centre, position, Direction.UP);
            Vec3 velocity = Sable.HELPER.getVelocity(level, centre);
            double gravity = DimensionPhysicsData.getGravity(level, new Vector3d(position.x, position.y, position.z)).length();
            return new State(position, forward, right, up, velocity, gravity);
        }

        private static Vec3 direction(Level level, Vec3 centre, Vec3 position, Direction direction) {
            Vec3 tip = Sable.HELPER.projectOutOfSubLevel(level, centre.add(Vec3.atLowerCornerOf(direction.getNormal())));
            return tip.subtract(position).normalize();
        }
    }
}
