package dev.aerolarge.client;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * The client's view of assembled airliners, from Sable: where a block of one is in the world, which one a player is
 * riding and how that one moves, and how one is moving and lying for its instruments. Every call works without Sable
 * too (nothing is assembled then); Sable's classes are only touched once it is known to be installed.
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

    /** The airliner a player is riding: how it moves (blocks a second) and how high its middle is above the ground. */
    public record Ride(Vector3d velocity, double heightAboveGround) {
    }

    /** What the entity is riding, or null when it is not on an assembled airliner. */
    @Nullable
    static Ride ride(Entity entity) {
        return SABLE ? Access.ride(entity) : null;
    }

    private static final class Access {
        static Vec3 toWorld(Level level, Vec3 position) {
            return Sable.HELPER.projectOutOfSubLevel(level, position);
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
            if (airliner == null) {
                return null;
            }
            Vector3dc middle = airliner.logicalPose().position();
            int ground = entity.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(middle.x()), Mth.floor(middle.z()));
            return new Ride(velocity(airliner), middle.y() - ground);
        }

        /** Blocks a second, from where the airliner was last tick and is now. */
        private static Vector3d velocity(SubLevel airliner) {
            return new Vector3d(airliner.logicalPose().position()).sub(airliner.lastPose().position()).mul(20);
        }
    }
}
