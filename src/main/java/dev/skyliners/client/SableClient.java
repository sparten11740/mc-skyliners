package dev.skyliners.client;

import dev.skyliners.Aboard;
import dev.skyliners.autopilot.ZeppelinFlight;
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
import dev.ryanhcode.sable.sublevel.render.dispatcher.VanillaSubLevelRenderDispatcher;
import dev.ryanhcode.sable.sublevel.render.vanilla.VanillaChunkedSubLevelRenderData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
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
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * The client's view of assembled airliners, from Sable: where a block of one is in the world and the light it is drawn
 * with, which one a player is riding or a block is part of and how that one moves, the autopilot of the one a player
 * rides, how one is moving and lying for its instruments, where its outside cameras are as it flies, which ones come
 * near a point or hold it and how they lie as they are drawn, where one's blocks end along a line, and those round the
 * camera the weather falls on; drawing one's blocks again through a matrix of our own, as a freighter is drawn
 * stretching into a hyperspace jump; holding Sable's water occlusion off while a camera's
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
     * The fastest a player may go on with what a ship they leave gave them, in blocks a tick: some five times what any
     * ship flies at. Past it, it is a ship moved straight across the world, as a hyperspace jump moves her, not flown.
     */
    private static final double MOST_INHERITED = 32;
    /**
     * The furthest a ship moves in a tick flying, in blocks: some five times what any ship flies at. A ship this client
     * sees go further in a tick was put there, as a hyperspace jump puts her, not flown there.
     */
    private static final double FURTHEST_FLOWN = 32;
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
     *
     * <p>And a ship moved straight across the world, as a hyperspace jump moves her, carries whoever is aboard all that
     * way in one tick, which Sable then keeps them going at once they are not carried any more, a jump's length a tick,
     * its collisions searching all of that each tick so the game all but stops: so what they go on with is let go
     * when it is more than {@link #MOST_INHERITED}.
     */
    static void keepAboard(LocalPlayer player) {
        if (SABLE) {
            Access.keepAboard(player);
        }
    }

    /**
     * Once a client tick, after Sable has moved the ships this client sees and before anything in the level moves: a
     * ship that went further than any ship flies in a tick, as a hyperspace jump moves her, was put there, and is
     * taken to have been there all along, the player aboard her as she went put where they stood among her blocks.
     * Otherwise Sable carries everyone on her the whole way in a tick, looking along all of it among her blocks for
     * something to bump into, every block of a box a jump's length long and, for a ship askew to her jump, hundreds of
     * blocks wide, which held the game up for seconds and in some jumps for over half a minute, the flash as she came
     * out frozen on the screen; and she is drawn smeared across it for that tick.
     */
    static void settleJumps(LocalPlayer player) {
        if (SABLE) {
            Access.settleJumps(player);
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
     * Where a point in the world lies in the frame of the airliner holding a point among its blocks, as she is drawn at
     * this partial tick; the point itself when no airliner holds that point. Something seen beside her as she is drawn,
     * as a laser crossbow's muzzle in the hands of someone aboard, taken among her blocks to be carried along with her.
     */
    static Vec3 toDrawnPlot(Vec3 aboard, Vec3 point, float partialTick) {
        return SABLE ? Access.toDrawnPlot(aboard, point, partialTick) : point;
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

    /** Whether the entity rides the assembled ship that holds a point among her blocks; never without Sable. */
    static boolean rides(Entity entity, Vec3 aboard) {
        return SABLE && Access.rides(entity, aboard);
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

    /**
     * Where an assembled ship's blocks end either way along a line through a point among them, as she is drawn: that
     * point in the world, and the furthest her blocks reach behind it and ahead of it along the line.
     */
    public record Ends(Vec3 middle, Vec3 back, Vec3 front) {
    }

    /**
     * Where the assembled ship holding a point among her blocks ends either way along a way through the world (a unit
     * long), as she is drawn at this partial tick, by the box round her blocks; null once no ship holds the point.
     */
    @Nullable
    static Ends ends(Vec3 aboard, Vec3 way, float partialTick) {
        return SABLE ? Access.ends(aboard, way, partialTick) : null;
    }

    /**
     * Draws one layer of an assembled ship's blocks again, as Sable draws them, but taking the world, around the
     * camera, through a model-view matrix of the caller's: a matrix that stretches the world draws her stretched. The
     * layer's render state is set up and its shader applied by the caller. Whether she was drawn: not without Sable,
     * once no ship holds the point, with a Sable that draws ships some other way (as it does alongside Sodium), or one
     * whose drawing is no longer where it was.
     */
    static boolean drawBlocks(Vec3 aboard, RenderType layer, ShaderInstance shader, Matrix4f modelView, Vec3 camera) {
        if (!SABLE || !Access.drawingReachable) {
            return false;
        }
        try {
            return Access.drawBlocks(aboard, layer, shader, modelView, camera);
        } catch (LinkageError notThere) {
            Access.drawingReachable = false;
            return false;
        }
    }

    private static final class Access {
        /** False once Sable's water occlusion switch was found missing, as a later Sable might move it. */
        static boolean waterOcclusionReachable = true;
        /** False once Sable's drawing of a ship's blocks was found missing, as a later Sable might move it. */
        static boolean drawingReachable = true;
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

        static Vec3 toDrawnPlot(Vec3 aboard, Vec3 point, float partialTick) {
            ClientSubLevel airliner = Sable.HELPER.getContainingClient(aboard);
            return airliner == null ? point : airliner.renderPose(partialTick).transformPositionInverse(point);
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
            Vector3d inherited = ((LivingEntityMovementExtension) player).sable$getInheritedVelocity();
            if (inherited.lengthSquared() > MOST_INHERITED * MOST_INHERITED) {
                inherited.zero();
            }
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

        static void settleJumps(LocalPlayer player) {
            ClientSubLevelContainer container = SubLevelContainer.getContainer(player.clientLevel);
            if (container == null) {
                return;
            }
            for (ClientSubLevel ship : container.getAllSubLevels()) {
                if (ship.lastPose().position().distanceSquared(ship.logicalPose().position()) > FURTHEST_FLOWN * FURTHEST_FLOWN) {
                    carryOver(player, ship);
                    ship.updateLastPose();
                }
            }
        }

        /**
         * The player, standing on a ship put somewhere new or among her blocks as she lay last tick, put where they
         * were among them as she lies now, and drawn there from the start of the tick; one riding a seat goes with it.
         */
        private static void carryOver(LocalPlayer player, ClientSubLevel ship) {
            Vec3 among = ship.lastPose().transformPositionInverse(player.position());
            BoundingBox3ic blocks = ship.getPlot().getBoundingBox();
            boolean aboard = Sable.HELPER.getTrackingSubLevel(player) == ship
                || blocks != null && blocks.contains(new Vector3d(among.x, among.y, among.z));
            if (!aboard || player.isPassenger()) {
                return;
            }
            player.setPos(ship.logicalPose().transformPosition(among));
            player.setOldPosAndRot();
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

        static boolean rides(Entity entity, Vec3 aboard) {
            SubLevel ridden = Sable.HELPER.getTrackingOrVehicleSubLevel(entity);
            return ridden != null && ridden == Sable.HELPER.getContainingClient(aboard);
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
            return new Frame(origin, nose, autopilot.craft(), ZeppelinFlight.sizeOf(autopilot));
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

        @Nullable
        static Ends ends(Vec3 aboard, Vec3 way, float partialTick) {
            ClientSubLevel ship = Sable.HELPER.getContainingClient(aboard);
            BoundingBox3ic blocks = ship == null ? null : ship.getPlot().getBoundingBox();
            if (blocks == null) {
                return null;
            }
            Pose3dc pose = ship.renderPose(partialTick);
            Vec3 middle = pose.transformPosition(aboard);
            double back = 0;
            double front = 0;
            for (int corner = 0; corner < 8; corner++) {
                Vector3d at = new Vector3d((corner & 1) == 0 ? blocks.minX() : blocks.maxX() + 1, (corner & 2) == 0 ? blocks.minY() : blocks.maxY() + 1,
                    (corner & 4) == 0 ? blocks.minZ() : blocks.maxZ() + 1);
                pose.transformPosition(at, at);
                double along = (at.x - middle.x) * way.x + (at.y - middle.y) * way.y + (at.z - middle.z) * way.z;
                back = Math.min(back, along);
                front = Math.max(front, along);
            }
            return new Ends(middle, middle.add(way.scale(back)), middle.add(way.scale(front)));
        }

        static boolean drawBlocks(Vec3 aboard, RenderType layer, ShaderInstance shader, Matrix4f modelView, Vec3 camera) {
            ClientSubLevel ship = Sable.HELPER.getContainingClient(aboard);
            if (ship == null || !(ship.getRenderData() instanceof VanillaChunkedSubLevelRenderData blocks)) {
                return false;
            }
            VanillaSubLevelRenderDispatcher.setupDynamicEffects(shader, true, true);
            blocks.renderChunkedSubLevel(layer, shader, modelView, camera.x, camera.y, camera.z);
            VanillaSubLevelRenderDispatcher.setupDynamicEffects(shader, false, true);
            return true;
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
