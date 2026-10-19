package dev.aerolarge.client;

import dev.aerolarge.block.AutopilotBlock;
import dev.aerolarge.block.AutopilotBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * The client's view of assembled airliners, from Sable: where a block of one is in the world and the light it is drawn
 * with, which one a player is riding or a block is part of and how that one moves, how one is moving and lying for its
 * instruments, and where its outside cameras are as it flies. Every call works without Sable too (nothing is assembled
 * then); Sable's classes are only touched once it is known to be installed.
 */
public final class SableClient {
    private static final boolean SABLE = ModList.get().isLoaded("sable");

    private SableClient() {
    }

    /** How an airliner is flying, as its instruments show it. Speeds in blocks a second, angles in degrees. */
    public record Attitude(double pitch, double roll, double heading, double airspeed, double verticalSpeed, double altitude) {
    }

    /** Where a position in an airliner's blocks is in the world; the position itself when it is not in one. */
    static Vec3 toWorld(Level level, Vec3 position) {
        return SABLE ? Access.toWorld(level, position) : position;
    }

    /**
     * The light to draw something at a position among an airliner's blocks with: the light there, its sky light
     * dimmed the way Sable dims it for an assembled airliner under cover, as it does for the block entities it draws.
     */
    static int light(Level level, BlockPos pos) {
        int light = LevelRenderer.getLightColor(level, pos);
        return SABLE ? Access.light(pos, light) : light;
    }

    /** Whether the player is riding the assembled airliner the block entity is part of. */
    static boolean isAboard(@Nullable Entity player, BlockEntity blockEntity) {
        return SABLE && player != null && Access.isAboard(player, blockEntity);
    }

    /**
     * The attitude of the airliner the block entity is part of, its nose pointing the given way among its blocks;
     * null if it is not part of one.
     */
    @Nullable
    static Attitude attitude(BlockEntity blockEntity, Direction nose, float partialTick) {
        return SABLE ? Access.attitude(blockEntity, nose, partialTick) : null;
    }

    /**
     * An assembled airliner as one riding it or one of its blocks finds it: where its middle is in the world, how it
     * moves (blocks a second) and how high its middle is above the ground.
     */
    public record Ride(Vector3d position, Vector3d velocity, double heightAboveGround) {
    }

    /** What the entity is riding, or null when it is not on an assembled airliner. */
    @Nullable
    static Ride ride(Entity entity) {
        return SABLE ? Access.ride(entity) : null;
    }

    /** The airliner the block entity is part of, or null when it is not part of an assembled one. */
    @Nullable
    static Ride ride(BlockEntity blockEntity) {
        return SABLE && blockEntity.getLevel() != null ? Access.ride(blockEntity) : null;
    }

    /**
     * Where an assembled airliner's own frame lies among its blocks: the point on its centreline under the tip of its
     * nose, level with the bottom of its wheels, and the way its nose points. Blocks keep the way they faced when it
     * was assembled, so this is how the airliner was built.
     */
    public record Frame(Vec3 origin, Direction nose) {
    }

    /**
     * The frame of the assembled airliner the block entity is part of, found from its autopilot (on the centreline,
     * pointing the way the nose does) and how far its blocks reach (the nose tip the furthest forward, the wheels the
     * lowest); null if it is not part of one, or has no autopilot to go by.
     */
    @Nullable
    static Frame frame(BlockEntity blockEntity) {
        return SABLE && blockEntity.getLevel() != null ? Access.frame(blockEntity) : null;
    }

    /** Where a point fixed to an assembled airliner is in the world as it is drawn, and which ways it faces. */
    public record View(Vec3 eye, Vec3 forward, Vec3 up) {
    }

    /**
     * A point among an assembled airliner's blocks, and a forward and an up direction in its frame, seen in the world
     * as the airliner is drawn at this partial tick; null once no airliner holds the point.
     */
    @Nullable
    static View view(Vec3 point, Vec3 forward, Vec3 up, float partialTick) {
        return SABLE ? Access.view(point, forward, up, partialTick) : null;
    }

    private static final class Access {
        static Vec3 toWorld(Level level, Vec3 position) {
            return Sable.HELPER.projectOutOfSubLevel(level, position);
        }

        static int light(BlockPos pos, int light) {
            ClientSubLevel airliner = Sable.HELPER.getContainingClient(pos);
            return airliner != null ? airliner.scaleLightColor(light) : light;
        }

        static boolean isAboard(Entity player, BlockEntity blockEntity) {
            SubLevel own = Sable.HELPER.getContainingClient(blockEntity);
            return own != null && own == Sable.HELPER.getTrackingOrVehicleSubLevel(player);
        }

        @Nullable
        static Attitude attitude(BlockEntity blockEntity, Direction noseWay, float partialTick) {
            ClientSubLevel airliner = Sable.HELPER.getContainingClient(blockEntity);
            if (airliner == null) {
                return null;
            }
            Pose3dc pose = airliner.renderPose(partialTick);
            Quaterniond orientation = new Quaterniond(pose.orientation());
            Vector3d nose = orientation.transform(new Vector3d(noseWay.getStepX(), 0, noseWay.getStepZ()));
            Direction starboard = noseWay.getClockWise();
            Vector3d right = orientation.transform(new Vector3d(starboard.getStepX(), 0, starboard.getStepZ()));
            Vector3d velocity = velocity(airliner);
            double pitch = Math.toDegrees(Math.asin(Math.clamp(nose.y, -1, 1)));
            double roll = Math.toDegrees(Math.asin(Math.clamp(-right.y, -1, 1)));
            double heading = (Math.toDegrees(Math.atan2(nose.x, -nose.z)) + 360) % 360;
            return new Attitude(pitch, roll, heading, velocity.dot(nose), velocity.y, pose.position().y());
        }

        @Nullable
        static Ride ride(Entity entity) {
            SubLevel airliner = Sable.HELPER.getTrackingOrVehicleSubLevel(entity);
            return airliner == null ? null : ride(airliner, entity.level());
        }

        @Nullable
        static Ride ride(BlockEntity blockEntity) {
            SubLevel airliner = Sable.HELPER.getContainingClient(blockEntity);
            return airliner == null ? null : ride(airliner, blockEntity.getLevel());
        }

        @Nullable
        static Frame frame(BlockEntity blockEntity) {
            ClientSubLevel airliner = Sable.HELPER.getContainingClient(blockEntity);
            if (airliner == null) {
                return null;
            }
            LevelPlot plot = airliner.getPlot();
            BoundingBox3ic bounds = plot.getBoundingBox();
            for (PlotChunkHolder holder : plot.getLoadedChunks()) {
                LevelChunk chunk = holder.getChunk();
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity found : chunk.getBlockEntities().values()) {
                    BlockPos at = found.getBlockPos();
                    if (found instanceof AutopilotBlockEntity && bounds.contains(at.getX(), at.getY(), at.getZ())) {
                        return frame(found, bounds);
                    }
                }
            }
            return null;
        }

        private static Frame frame(BlockEntity autopilot, BoundingBox3ic bounds) {
            Direction nose = autopilot.getBlockState().getValue(AutopilotBlock.FACING);
            Vec3 centreline = Vec3.atCenterOf(autopilot.getBlockPos());
            // The far face of the blocks furthest the way the nose points.
            double tip = switch (nose) {
                case NORTH -> bounds.minZ();
                case SOUTH -> bounds.maxZ() + 1;
                case WEST -> bounds.minX();
                default -> bounds.maxX() + 1;
            };
            Vec3 origin = nose.getAxis() == Direction.Axis.Z
                ? new Vec3(centreline.x, bounds.minY(), tip)
                : new Vec3(tip, bounds.minY(), centreline.z);
            return new Frame(origin, nose);
        }

        @Nullable
        static View view(Vec3 point, Vec3 forward, Vec3 up, float partialTick) {
            ClientSubLevel airliner = Sable.HELPER.getContainingClient(point);
            if (airliner == null) {
                return null;
            }
            Pose3dc pose = airliner.renderPose(partialTick);
            return new View(pose.transformPosition(point), pose.transformNormal(forward).normalize(), pose.transformNormal(up).normalize());
        }

        private static Ride ride(SubLevel airliner, Level level) {
            Vector3dc middle = airliner.logicalPose().position();
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(middle.x()), Mth.floor(middle.z()));
            return new Ride(new Vector3d(middle), velocity(airliner), middle.y() - ground);
        }

        /** Blocks a second, from where the airliner was last tick and is now. */
        private static Vector3d velocity(SubLevel airliner) {
            return new Vector3d(airliner.logicalPose().position()).sub(airliner.lastPose().position()).mul(20);
        }
    }
}
