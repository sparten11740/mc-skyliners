package dev.aerolarge.compat;

import dev.aerolarge.Aboard;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinhelpers.CanFallAtleastHelper;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.joml.Vector3d;

/**
 * Where an assembled airliner is and how it is moving, read from Sable. A block of an assembled airliner lives in
 * Sable's plot, away from where the airliner is seen; projecting a few points of the plot out into the world gives
 * the airliner's position and the directions its nose, right wing and roof point. The cabin's loose things (trolleys,
 * basketballs, paper planes) live in the plot too while they are inside the hull, carried along with the airliner's
 * blocks (see {@link #keepWithCabin}). This is the only class that touches Sable on the server, and only once Sable is
 * known to be installed; without it nothing is ever assembled, and every answer is the one for blocks standing in the
 * world. {@link #fitsAboard} also serves the client, which works out its own player's pose.
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
     * Whether the entity rides (stands on, or sits in) the assembled airliner the block entity is part of, or is one of
     * its loose things carried among its blocks; null when the block entity is not part of an assembled airliner.
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

    /** Whether the entity lives among an assembled airliner's blocks, in its plot, as the cabin's loose things do. */
    public static boolean inPlot(Entity entity) {
        return SABLE && Access.inPlot(entity);
    }

    /**
     * Whether a box round a player standing aboard an assembled airliner, inside its hull, is clear of its blocks as
     * the airliner lies now; false when they stand aboard none. Sable's own test, for whether a player has room to
     * stand up, looks at where the airliner's blocks were a tick ago: flying fast nose up, as it climbs, its floor
     * stood a good half block higher there than it does now under the player, so a player standing on it is made to
     * crouch or crawl and stays so. This one moves the box back along with the airliner first. It serves the client
     * too, which works out its own player's pose.
     */
    public static boolean fitsAboard(Player player, AABB box) {
        return SABLE && Access.fitsAboard(player, box);
    }

    /**
     * A point in the world seen in the frame of the airliner the plot position is part of, as the airliner lies now or
     * lay a tick ago; the point itself when the position is in the world, or the point is among that airliner's blocks
     * already (where one of its loose things is).
     */
    public static Vec3 toPlot(Level level, BlockPos plot, Vec3 point, boolean lastTick) {
        return SABLE ? Access.toPlot(level, plot, point, lastTick) : point;
    }

    /**
     * A direction in the world turned into the frame of the airliner the plot position is part of, as it lies now; the
     * direction itself when the position is in the world. A push or a look turned for something among its blocks.
     */
    public static Vec3 turnToPlot(Level level, BlockPos plot, Vec3 direction) {
        return SABLE ? Access.turnToPlot(level, plot, direction) : direction;
    }

    /**
     * Keeps one of the cabin's loose things (a trolley, a basketball, a paper plane) in the air around it, on the
     * server. Inside the hull of an assembled airliner the air moves with the airliner, so there the thing lives among
     * the airliner's blocks, in its plot, where Sable carries it along with them as it does Create's seats (its type is
     * in {@code #sable:retain_in_sub_level}, so Sable leaves it there): it rolls, bounces, glides and is pushed relative
     * to the cabin, and collides with the cabin's blocks as they are. Outside the hull it lives in the world. Crossing
     * over, it keeps how it moves in the world: taken in, it moves relative to the cabin as fast as it moved past it;
     * sent out, it moves as fast as the airliner carried it plus its own speed in the cabin (see {@link #sendOut}). The
     * client follows the server.
     */
    public static void keepWithCabin(Entity entity) {
        if (SABLE) {
            Access.keepWithCabin(entity);
        }
    }

    /**
     * Takes something just thrown in the world among the blocks of the assembled airliner whose hull it is inside, its
     * velocity taken as relative to the cabin (the thrower's own, aboard) and turned into the airliner's frame; whether
     * it went aboard one. Before it is added to the level.
     */
    public static boolean takeAboard(Entity entity) {
        return SABLE && Access.takeAboard(entity);
    }

    /**
     * Sends a loose thing living among an assembled airliner's blocks out into the world, where the airliner holds it
     * now: its velocity turned into the world, plus the speed the airliner moves at that point. Sable's own kick turns
     * the velocity but only adds the airliner's speed for projectiles, so in flight anything else would stop dead in
     * the air. Whether it lived among an airliner's blocks; anything in the world stays as it is.
     */
    public static boolean sendOut(Entity entity) {
        return SABLE && Access.sendOut(entity);
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
     * The nearest block to an entity that the test accepts, within the ranges across and up and down of where it is,
     * looking into any assembled airliner there as well as the world: its position in the world, or in the airliner's
     * plot; null if neither has one. For an entity riding an assembled airliner, that airliner's blocks are looked
     * through first, and the world and any other airliner only when none of those will do: what it looks for aboard is
     * at hand there, and the search round it stays among them. Only blocks in a candidate state are tested, and a
     * section of the level holding none is passed over whole by its palette, so a wide search over open country or an
     * airliner's empty spaces costs next to nothing.
     */
    @Nullable
    public static BlockPos nearest(Entity entity, int across, int upDown, Predicate<BlockState> candidate, Predicate<BlockPos> test) {
        return SABLE ? Access.nearest(entity, across, upDown, candidate, test) : closest(entity.level(), entity.position(), across, upDown, candidate, test);
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
            if (airliner == null) {
                return null;
            }
            return airliner == Sable.HELPER.getTrackingOrVehicleSubLevel(entity) || airliner == Sable.HELPER.getContaining(entity);
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

        static boolean inPlot(Entity entity) {
            return Sable.HELPER.getContaining(entity) != null;
        }

        static boolean fitsAboard(Player player, AABB box) {
            SubLevel airliner = Sable.HELPER.getTrackingSubLevel(player);
            if (airliner == null) {
                return false;
            }
            Vec3 middle = box.getCenter();
            Vec3 inPlot = airliner.logicalPose().transformPositionInverse(middle);
            if (!Aboard.insideHull(player.level(), BlockPos.containing(inPlot))) {
                return false;
            }
            Vec3 lastTick = airliner.lastPose().transformPosition(inPlot);
            return CanFallAtleastHelper.canFallAtleastWithSubLevels(player.level(), box.move(lastTick.subtract(middle))) == null;
        }

        /** The assembled airliner whose hull holds a point in the world; null for none. */
        @Nullable
        private static SubLevel hullAround(Level level, Vec3 point) {
            for (SubLevel airliner : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(point, point))) {
                if (Aboard.insideHull(level, BlockPos.containing(airliner.logicalPose().transformPositionInverse(point)))) {
                    return airliner;
                }
            }
            return null;
        }

        static Vec3 toPlot(Level level, BlockPos plot, Vec3 point, boolean lastTick) {
            SubLevel airliner = Sable.HELPER.getContaining(level, plot);
            if (airliner == null || Sable.HELPER.getContaining(level, point) == airliner) {
                return point;
            }
            return (lastTick ? airliner.lastPose() : airliner.logicalPose()).transformPositionInverse(point);
        }

        static Vec3 turnToPlot(Level level, BlockPos plot, Vec3 direction) {
            SubLevel airliner = Sable.HELPER.getContaining(level, plot);
            return airliner == null ? direction : airliner.logicalPose().transformNormalInverse(direction);
        }

        static void keepWithCabin(Entity entity) {
            Level level = entity.level();
            Vec3 middle = entity.getBoundingBox().getCenter();
            if (Sable.HELPER.getContaining(entity) != null) {
                // Among an airliner's blocks already: the hull is looked for there too.
                if (!Aboard.insideHull(level, BlockPos.containing(middle))) {
                    sendOut(entity);
                }
                return;
            }
            SubLevel airliner = hullAround(level, middle);
            if (airliner != null) {
                Vec3 carried = Sable.HELPER.getVelocity(level, airliner, airliner.logicalPose().transformPositionInverse(entity.position()));
                board(airliner, entity, entity.getDeltaMovement().subtract(carried.scale(1.0 / SharedConstants.TICKS_PER_SECOND)));
            }
        }

        static boolean takeAboard(Entity entity) {
            SubLevel airliner = hullAround(entity.level(), entity.getBoundingBox().getCenter());
            if (airliner == null) {
                return false;
            }
            board(airliner, entity, entity.getDeltaMovement());
            return true;
        }

        /**
         * Moves an entity in the world among the airliner's blocks, where it is in the airliner's frame, moving relative
         * to the cabin as given (in the world's directions) and facing the same way, both turned into that frame.
         */
        private static void board(SubLevel airliner, Entity entity, Vec3 relative) {
            Pose3dc pose = airliner.logicalPose();
            // Read before it moves: among the airliner's blocks Sable turns its look with the airliner.
            Vec3 look = pose.transformNormalInverse(entity.getLookAngle());
            Vec3 at = pose.transformPositionInverse(entity.position());
            entity.moveTo(at);
            entity.setDeltaMovement(pose.transformNormalInverse(relative));
            entity.lookAt(EntityAnchorArgument.Anchor.FEET, at.add(look));
            moved(entity);
        }

        static boolean sendOut(Entity entity) {
            SubLevel airliner = Sable.HELPER.getContaining(entity);
            if (airliner == null) {
                return false;
            }
            Vec3 carried = Sable.HELPER.getVelocity(entity.level(), airliner, entity.position()).scale(1.0 / SharedConstants.TICKS_PER_SECOND);
            EntitySubLevelUtil.kickEntity(airliner, entity);
            entity.setDeltaMovement(entity.getDeltaMovement().add(carried));
            moved(entity);
            return true;
        }

        /** Tells the clients at once that the entity moved between the world and an airliner's blocks. */
        private static void moved(Entity entity) {
            entity.hasImpulse = true;
            entity.hurtMarked = true;
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
        static BlockPos nearest(Entity entity, int across, int upDown, Predicate<BlockState> candidate, Predicate<BlockPos> test) {
            Level level = entity.level();
            Vec3 point = entity.position();
            SubLevel ridden = Sable.HELPER.getTrackingOrVehicleSubLevel(entity);
            if (ridden != null) {
                BlockPos aboard = closest(level, ridden.logicalPose().transformPositionInverse(point), across, upDown, candidate, test);
                if (aboard != null) {
                    return aboard;
                }
            }
            // The world's nearest wins a tie with an airliner's. Distances in a plot are the same as in the world: an
            // airliner only moves and turns.
            BlockPos nearest = closest(level, point, across, upDown, candidate, test);
            double distance = nearest == null ? Double.MAX_VALUE : nearest.distToCenterSqr(point);
            BoundingBox3d around = new BoundingBox3d(point.x - across, point.y - upDown, point.z - across, point.x + across, point.y + upDown, point.z + across);
            for (SubLevel airliner : Sable.HELPER.getAllIntersecting(level, around)) {
                if (airliner == ridden) {
                    // Looked through already, with nothing found.
                    continue;
                }
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
