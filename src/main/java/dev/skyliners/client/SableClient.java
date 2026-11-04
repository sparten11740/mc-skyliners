package dev.skyliners.client;

import dev.skyliners.Aboard;
import dev.skyliners.block.AutopilotBlock;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.airframe.ZeppelinSize;
import dev.skyliners.autopilot.Craft;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ClientSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import dev.ryanhcode.sable.network.client.SubLevelSnapshotInterpolator.Snapshot;
import dev.ryanhcode.sable.render.water_occlusion.WaterOcclusionRenderer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.CommonHooks;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * The client's view of assembled airliners, from Sable: where a block of one is in the world and the light it is drawn
 * with, which one a player is riding or a block is part of and how that one moves, the autopilot of the one a player
 * rides, how one is moving and lying for its instruments, where its outside cameras are as it flies, which ones come
 * near a point or hold it and how they lie as they are drawn, and those round the camera the weather falls on;
 * holding Sable's water occlusion off while a camera's
 * picture is drawn; and keeping the player in step with the airliner whose cabin they fly, fall or climb in. Every call works without Sable too (nothing is assembled then); Sable's classes are only touched
 * once it is known to be installed.
 */
public final class SableClient {
    private static final boolean SABLE = ModList.get().isLoaded("sable");
    /**
     * How fast an airliner has to move where a player is, in blocks a second, for a player inside its hull to be
     * carried along by it whatever they touch: below it, flying or falling there needs no help.
     */
    private static final double CARRIED_SPEED = 1;
    /**
     * The game's climb: the speed a ladder lifts a climber at and the fastest they slide down one, in blocks a tick,
     * and the share of their speed the air leaves them each tick.
     */
    private static final double CLIMB_SPEED = 0.2;
    private static final double SLIDE_SPEED = 0.15;
    private static final double AIR_DRAG = 0.98;

    private SableClient() {
    }

    /** How an airliner is flying, as its instruments show it. Speeds in blocks a second, angles in degrees. */
    public record Attitude(double pitch, double roll, double heading, double airspeed, double verticalSpeed, double altitude) {
    }

    /**
     * Keeps the player in step with the airliner whose cabin they are in, after their tick. Sable carries a player
     * only while some block of the airliner is right round them: flying, falling or jumping through the open cabin of
     * an airliner in flight, it lets go, and the airliner flies on and the back wall hits them. Inside its hull the air
     * moves with the airliner, so there they are carried again. And Sable looks for the ladder they climb where their
     * not yet carried position lands in the airliner as it lies now, a few blocks aft of the ladder in flight, so the
     * climb is done again here against the airliner as it lies, as the game would do it.
     */
    static void keepAboard(LocalPlayer player) {
        if (SABLE) {
            Access.keepAboard(player);
        }
    }

    /** Where a position in an airliner's blocks is in the world; the position itself when it is not in one. */
    static Vec3 toWorld(Level level, Vec3 position) {
        return SABLE ? Access.toWorld(level, position) : position;
    }

    /**
     * Where a point in the world lies in the frame of the airliner a plot position is part of, as it lay last tick or
     * lies now; the point itself when the position is in the world. Where the player stands among its blocks.
     */
    static Vec3 toPlot(Level level, BlockPos plot, Vec3 point, boolean lastTick) {
        return SABLE ? Access.toPlot(level, plot, point, lastTick) : point;
    }

    /**
     * A direction in the world turned into the frame of the airliner a block is part of, as it is drawn now: a look
     * turned for something among its blocks, as the server turns it; the direction itself for a block in the world.
     */
    static Vec3 turnToPlot(BlockPos pos, Vec3 direction) {
        return SABLE ? Access.turnToPlot(pos, direction) : direction;
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

    /** The autopilot of the assembled airliner the entity rides; null when it rides none, or one without an autopilot. */
    @Nullable
    static AutopilotBlockEntity autopilot(Entity entity) {
        return SABLE ? Access.autopilot(entity) : null;
    }

    /**
     * Where an assembled airliner's own frame lies among its blocks: the point on its centreline under the tip of its
     * nose, level with the bottom of its wheels, and the way its nose points, what kind of craft it is, airliner or
     * zeppelin, and how big a zeppelin's hull is. Blocks keep the way they faced when it was assembled, so this is how
     * the airliner was built.
     */
    public record Frame(Vec3 origin, Direction nose, Craft craft, ZeppelinSize size) {
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

    /** Which assembled airliner the block entity is part of, the same for all its blocks; null if it is not part of one. */
    @Nullable
    static UUID airliner(BlockEntity blockEntity) {
        return SABLE && blockEntity.getLevel() != null ? Access.airliner(blockEntity) : null;
    }

    /**
     * Lets go of the speed an assembled airliner carried a player at, which Sable keeps on them once they are off it,
     * losing only a hundredth of it a tick (see {@link ThrownClear}).
     */
    static void letGo(LivingEntity entity) {
        if (SABLE) {
            Access.letGo(entity);
        }
    }

    /**
     * Draws something with Sable's water occlusion (which keeps water from being drawn inside a hull) held off, for a
     * picture of the world drawn into a framebuffer of another size: Sable sizes the occlusion's own buffers to the one
     * being drawn into, and would make them anew for every such picture and again for the frame after it. Without
     * Sable, or a Sable whose water occlusion is not where it was, the drawing runs as it is.
     */
    static void withoutWaterOcclusion(Runnable drawing) {
        if (!SABLE || !Access.waterOcclusionReachable) {
            drawing.run();
            return;
        }
        boolean enabled;
        try {
            enabled = Access.waterOcclusion(false);
        } catch (LinkageError notThere) {
            Access.waterOcclusionReachable = false;
            drawing.run();
            return;
        }
        try {
            drawing.run();
        } finally {
            Access.waterOcclusion(enabled);
        }
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

    /** An assembled airliner near a point in the world: which one, and where that point is among its blocks. */
    public record Near(UUID airliner, Vec3 point) {
    }

    /** Each assembled airliner within reach of a point in the world, as it last moved; none without Sable. */
    static List<Near> near(Level level, Vec3 point, double reach) {
        return SABLE ? Access.near(level, point, reach) : List.of();
    }

    /**
     * An assembled airliner whose bounds hold a point in the world: where that point is among its blocks as the airliner
     * is drawn, and how low and how high its bounds reach in the world.
     */
    public record Around(Vec3 point, double bottom, double top) {
    }

    /** Each assembled airliner whose bounds hold a point in the world, as drawn at this partial tick; none without Sable. */
    static List<Around> around(Level level, Vec3 point, float partialTick) {
        return SABLE ? Access.around(level, point, partialTick) : List.of();
    }

    /**
     * An assembled ship as drawn this frame, for the rain and snow to be kept out of her (see {@link ShipWeather}): her
     * bounds in the world, where a point of the world is among her blocks, and which way the world's up is among them.
     */
    interface Shelter {
        AABB bounds();

        Vec3 among(double x, double y, double z);

        Vec3 up();
    }

    /**
     * Each assembled ship whose bounds reach into a box of the world, as drawn at this partial tick; none without
     * Sable.
     */
    static List<Shelter> shelters(Level level, AABB box, float partialTick) {
        return SABLE ? Access.shelters(level, box, partialTick) : List.of();
    }

    /**
     * How an assembled airliner lies as it is drawn: the transform taking a position among its blocks into the world,
     * and its turn, which takes a direction among its blocks into the world. Only ever forward: its blocks lie twenty
     * million blocks out, and the inverse of this transform rounds by ten blocks once the airliner leans. To go from
     * the world to its blocks, use {@link SableClient#toPlot}.
     */
    public record Placing(Matrix4d transform, Quaterniond turn) {
    }

    /**
     * How the assembled airliner holding a point among its blocks lies as it is drawn at this partial tick; null once
     * no airliner holds the point.
     */
    @Nullable
    static Placing placing(Vec3 point, float partialTick) {
        return SABLE ? Access.placing(point, partialTick) : null;
    }

    private static final class Access {
        /** False once Sable's water occlusion switch was found missing, as a later Sable might move it. */
        static boolean waterOcclusionReachable = true;
        /** False once Sable's store of the places the server sent was found missing, as a later Sable might move it. */
        static boolean sentReachable = true;

        static Vec3 toWorld(Level level, Vec3 position) {
            return Sable.HELPER.projectOutOfSubLevel(level, position);
        }

        static Vec3 toPlot(Level level, BlockPos plot, Vec3 point, boolean lastTick) {
            SubLevel airliner = Sable.HELPER.getContaining(level, plot);
            if (airliner == null || Sable.HELPER.getContaining(level, point) == airliner) {
                return point;
            }
            return (lastTick ? airliner.lastPose() : airliner.logicalPose()).transformPositionInverse(point);
        }

        static Vec3 turnToPlot(BlockPos pos, Vec3 direction) {
            ClientSubLevel airliner = Sable.HELPER.getContainingClient(pos);
            return airliner == null ? direction : airliner.logicalPose().transformNormalInverse(direction);
        }

        static int light(BlockPos pos, int light) {
            ClientSubLevel airliner = Sable.HELPER.getContainingClient(pos);
            return airliner != null ? airliner.scaleLightColor(light) : light;
        }

        static boolean isAboard(Entity player, BlockEntity blockEntity) {
            SubLevel own = Sable.HELPER.getContainingClient(blockEntity);
            return own != null && own == Sable.HELPER.getTrackingOrVehicleSubLevel(player);
        }

        static void keepAboard(LocalPlayer player) {
            if (player.isPassenger() || player.isSpectator() || player.isSleeping() || player.isFallFlying()) {
                return;
            }
            Level level = player.level();
            SubLevel airliner = Sable.HELPER.getTrackingSubLevel(player);
            if (airliner == null) {
                airliner = cabinAround(level, player.getBoundingBox().getCenter());
                if (airliner == null) {
                    return;
                }
                ((EntityMovementExtension) player).sable$setTrackingSubLevel(airliner);
            }
            if (!player.getAbilities().flying) {
                climb(player, airliner);
            }
        }

        /**
         * The assembled airliner in flight whose hull holds a point in the world, null for none: parked, losing hold of
         * a player costs nothing, and a fall there is a fall as anywhere.
         */
        @Nullable
        private static SubLevel cabinAround(Level level, Vec3 point) {
            for (SubLevel airliner : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(point, point))) {
                Vec3 inPlot = airliner.logicalPose().transformPositionInverse(point);
                if (Sable.HELPER.getVelocity(level, airliner, inPlot).lengthSqr() >= CARRIED_SPEED * CARRIED_SPEED
                    && Aboard.insideHull(level, BlockPos.containing(inPlot))) {
                    return airliner;
                }
            }
            return null;
        }

        /**
         * The game's own climb, against a ladder of the airliner as it lies now: on a ladder (or an open hatch over
         * one) the player falls no faster than a slide down it, holds on while sneaking, and goes up pushing against
         * it or jumping, as fast as the game's climb would leave them moving once its gravity and drag have had their
         * tick.
         */
        private static void climb(LocalPlayer player, SubLevel airliner) {
            Level level = player.level();
            BlockPos at = BlockPos.containing(airliner.logicalPose().transformPositionInverse(player.position()));
            if (CommonHooks.isLivingOnLadder(level.getBlockState(at), level, at, player).isEmpty()) {
                return;
            }
            player.resetFallDistance();
            Vec3 motion = player.getDeltaMovement();
            double up;
            if (player.horizontalCollision || player.input.jumping) {
                up = (CLIMB_SPEED - player.getGravity()) * AIR_DRAG;
            } else if (player.isSuppressingSlidingDownLadder()) {
                up = Math.max(motion.y, 0);
            } else {
                up = Math.max(motion.y, -SLIDE_SPEED);
            }
            player.setDeltaMovement(motion.x, up, motion.z);
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
        static AutopilotBlockEntity autopilot(Entity entity) {
            SubLevel airliner = Sable.HELPER.getTrackingOrVehicleSubLevel(entity);
            return airliner == null ? null : autopilot(airliner.getPlot());
        }

        @Nullable
        static Frame frame(BlockEntity blockEntity) {
            ClientSubLevel airliner = Sable.HELPER.getContainingClient(blockEntity);
            if (airliner == null) {
                return null;
            }
            LevelPlot plot = airliner.getPlot();
            AutopilotBlockEntity autopilot = autopilot(plot);
            return autopilot == null ? null : frame(autopilot, plot.getBoundingBox());
        }

        /** The autopilot among an airliner's blocks, or null if it has none. */
        @Nullable
        private static AutopilotBlockEntity autopilot(LevelPlot plot) {
            BoundingBox3ic bounds = plot.getBoundingBox();
            for (PlotChunkHolder holder : plot.getLoadedChunks()) {
                LevelChunk chunk = holder.getChunk();
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity found : chunk.getBlockEntities().values()) {
                    BlockPos at = found.getBlockPos();
                    if (found instanceof AutopilotBlockEntity autopilot && bounds.contains(at.getX(), at.getY(), at.getZ())) {
                        return autopilot;
                    }
                }
            }
            return null;
        }

        private static Frame frame(AutopilotBlockEntity autopilot, BoundingBox3ic bounds) {
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
            return new Frame(origin, nose, autopilot.craft(), autopilot.zeppelinSize());
        }

        @Nullable
        static UUID airliner(BlockEntity blockEntity) {
            ClientSubLevel airliner = Sable.HELPER.getContainingClient(blockEntity);
            return airliner == null ? null : airliner.getUniqueId();
        }

        static void letGo(LivingEntity entity) {
            ((LivingEntityMovementExtension) entity).sable$getInheritedVelocity().zero();
        }

        /** Turns Sable's water occlusion on or off; whether it was on. */
        static boolean waterOcclusion(boolean on) {
            boolean was = WaterOcclusionRenderer.isEnabled();
            WaterOcclusionRenderer.setIsEnabled(on);
            return was;
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

        static List<Near> near(Level level, Vec3 point, double reach) {
            BoundingBox3d around = new BoundingBox3d(point.x - reach, point.y - reach, point.z - reach, point.x + reach, point.y + reach, point.z + reach);
            List<Near> found = new ArrayList<>();
            for (SubLevel airliner : Sable.HELPER.getAllIntersecting(level, around)) {
                found.add(new Near(airliner.getUniqueId(), airliner.logicalPose().transformPositionInverse(point)));
            }
            return found;
        }

        static List<Around> around(Level level, Vec3 point, float partialTick) {
            List<Around> found = new ArrayList<>();
            for (SubLevel airliner : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(BlockPos.containing(point)))) {
                if (airliner instanceof ClientSubLevel drawn) {
                    // Over this tick and the last, so it holds the airliner wherever it is drawn between them.
                    BoundingBox3dc bounds = drawn.boundingBox();
                    found.add(new Around(drawn.renderPose(partialTick).transformPositionInverse(point), bounds.minY(), bounds.maxY()));
                }
            }
            return found;
        }

        static List<Shelter> shelters(Level level, AABB box, float partialTick) {
            List<Shelter> found = new ArrayList<>();
            BoundingBox3d near = new BoundingBox3d(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            for (SubLevel ship : Sable.HELPER.getAllIntersecting(level, near)) {
                if (ship instanceof ClientSubLevel drawn) {
                    BoundingBox3dc bounds = drawn.boundingBox();
                    AABB world = new AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ());
                    Pose3dc pose = drawn.renderPose(partialTick);
                    Vector3d up = pose.orientation().transformInverse(new Vector3d(0, 1, 0));
                    found.add(new Sheltering(world, pose, new Vec3(up.x, up.y, up.z)));
                }
            }
            return found;
        }

        /** A ship's bounds in the world, how she lies as she is drawn, and the world's up among her blocks. */
        private record Sheltering(AABB bounds, Pose3dc pose, Vec3 up) implements Shelter {
            @Override
            public Vec3 among(double x, double y, double z) {
                return pose.transformPositionInverse(new Vec3(x, y, z));
            }
        }

        @Nullable
        static Placing placing(Vec3 point, float partialTick) {
            ClientSubLevel airliner = Sable.HELPER.getContainingClient(point);
            if (airliner == null) {
                return null;
            }
            Pose3dc pose = airliner.renderPose(partialTick);
            return new Placing(pose.bakeIntoMatrix(new Matrix4d()), new Quaterniond(pose.orientation()));
        }

        private static Ride ride(SubLevel airliner, Level level) {
            Vector3dc middle = airliner.logicalPose().position();
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(middle.x()), Mth.floor(middle.z()));
            return new Ride(new Vector3d(middle), velocity(airliner), middle.y() - ground);
        }

        /**
         * Blocks a second, from the places the server sent for the airliner either side of the moment the client
         * shows it at, or else from where it was shown last tick and is now. The client shows the airliner a little
         * behind the server and runs its replay faster or slower to keep that lag as the server's ticks come quicker
         * or slower, so how far it is shown moving in one tick swings even while it flies steadily, and a speed taken
         * from that swung the speedometer's needle back and forth.
         */
        private static Vector3d velocity(SubLevel airliner) {
            Vector3d sent = sentReachable && airliner instanceof ClientSubLevel shown ? sentVelocity(shown) : null;
            return sent != null ? sent : new Vector3d(airliner.logicalPose().position()).sub(airliner.lastPose().position())
                .mul(SharedConstants.TICKS_PER_SECOND);
        }

        /**
         * Blocks a second, from the places the server sent either side of the moment shown; null before two came, or
         * with a Sable that keeps them elsewhere.
         */
        @Nullable
        private static Vector3d sentVelocity(ClientSubLevel airliner) {
            try {
                ClientSubLevelContainer container = SubLevelContainer.getContainer(airliner.getLevel());
                return container == null ? null : between(airliner.getInterpolator().buffer, container.getInterpolation().getTickPointer());
            } catch (LinkageError notThere) {
                sentReachable = false;
                return null;
            }
        }

        /**
         * Blocks a second between the two places sent either side of a moment in the server's ticks, the last two
         * once it is past them all: the server sends one a tick, but one can go missing on the way.
         */
        @Nullable
        private static Vector3d between(List<Snapshot> sent, double tick) {
            Snapshot from;
            Snapshot to;
            synchronized (sent) {
                if (sent.size() < 2) {
                    return null;
                }
                int after = 1;
                while (after < sent.size() - 1 && sent.get(after).gameTick() <= tick) {
                    after++;
                }
                from = sent.get(after - 1);
                to = sent.get(after);
            }
            int ticks = to.gameTick() - from.gameTick();
            return ticks <= 0 ? null : new Vector3d(to.pose().position()).sub(from.pose().position())
                .mul((double) SharedConstants.TICKS_PER_SECOND / ticks);
        }
    }
}
