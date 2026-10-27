package dev.aerolarge.compat;

import dev.aerolarge.Aboard;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.block.AirstairStepBlock;
import dev.aerolarge.block.SlideStepBlock;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintHandle;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinhelpers.CanFallAtleastHelper;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import dev.ryanhcode.sable.neoforge.event.ForgeSableSubLevelContainerReadyEvent;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Where an assembled airliner is and how it is moving, read from Sable. A block of an assembled airliner lives in
 * Sable's plot, away from where the airliner is seen; projecting a few points of the plot out into the world gives
 * the airliner's position and the directions its nose, right wing and roof point. The cabin's loose things (trolleys,
 * basketballs, paper planes) live in the plot too while they are inside the hull, carried along with the airliner's
 * blocks (see {@link #keepWithCabin}). An airliner blown up by its self-destruct button is taken away through here too
 * (see {@link #remove}). This is the only class that touches Sable on the server, and only once Sable is known to be
 * installed; without it nothing is ever assembled, and every answer is the one for blocks standing in the world.
 * {@link #fitsAboard} also serves the client, which works out its own player's pose.
 */
public final class SableFlight {
    private static final boolean SABLE = ModList.get().isLoaded("sable");
    /** How hard Sable's gravity pulls where nothing else is set, in blocks a second each second. */
    private static final double DEFAULT_GRAVITY = 11;

    private SableFlight() {
    }

    /**
     * The airliner's state seen from one of its blocks: positions in blocks, velocity in blocks per second, the
     * strength of gravity where it is, in blocks per second each second, and the air's pressure there as Sable has it,
     * which every lifting block's push and drag are in proportion to: 1 at sea level, thinning higher up.
     */
    public record State(Vec3 position, Vec3 nose, Vec3 right, Vec3 up, Vec3 velocity, double gravity, double air) {
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

    /**
     * Whether two block entities are both part of one assembled airliner: the same one of Sable's flying bodies, not
     * merely two that fly side by side; false when either is not part of one.
     */
    public static boolean sameAirliner(BlockEntity one, BlockEntity other) {
        return SABLE && one.getLevel() != null && one.getLevel() == other.getLevel() && Access.sameAirliner(one, other);
    }

    /**
     * Takes the assembled airliner the block entity is part of out of the world for good, every block of it gone at
     * once and nothing dropped, as Sable's own {@code sable remove} does; whether there was one to take. On the server,
     * between ticks of the level, never while its block entities tick: its plot's chunks go with it.
     */
    public static boolean remove(BlockEntity blockEntity) {
        return SABLE && blockEntity.getLevel() instanceof ServerLevel && Access.remove(blockEntity);
    }

    /** Where a point among an assembled airliner's blocks is in the world; the point itself anywhere else. */
    public static Vec3 toWorld(Level level, Vec3 point) {
        return SABLE ? Access.toWorld(level, point) : point;
    }

    /** Whether Sable is installed, which flies the airframes: without it they stand as buildings. */
    public static boolean installed() {
        return SABLE;
    }

    /**
     * How heavy the assembled airframe the block entity is part of is, in Sable's units (a plain block weighs one); NaN
     * when it is not part of one. What a zeppelin's autopilot works out the hot air it needs from.
     */
    public static double mass(BlockEntity blockEntity) {
        return SABLE && blockEntity.getLevel() instanceof ServerLevel ? Access.mass(blockEntity) : Double.NaN;
    }

    /**
     * A body locked to an assembled airframe (see {@link #lock}): whether it is still held, and letting it go. Sable
     * keeps no lock over a save, so whoever made it makes it again when the level is loaded.
     */
    public interface Lock {
        boolean held();

        void release();
    }

    /**
     * Another of Sable's flying bodies, a player's plane, resting on an assembled airframe round a block of it: one
     * reaching into the space over the block's top, so many blocks across either way and so many up, its middle over
     * the block's top, moving at no more than so many blocks a second against the airframe; its id, or null for none.
     */
    @Nullable
    public static UUID restingOver(BlockEntity blockEntity, double across, double up, double still) {
        return SABLE && blockEntity.getLevel() instanceof ServerLevel ? Access.restingOver(blockEntity, across, up, still) : null;
    }

    /** Which of Sable's flying bodies a position is among the blocks of, the same for all its blocks; null for none. */
    @Nullable
    public static UUID bodyAt(Level level, BlockPos pos) {
        return SABLE ? Access.bodyAt(level, pos) : null;
    }

    /** How heavy one of Sable's flying bodies is, by its id, in Sable's units; NaN if it is not about. */
    public static double massOf(Level level, UUID body) {
        return SABLE && level instanceof ServerLevel server ? Access.massOf(server, body) : Double.NaN;
    }

    /**
     * Locks a flying body to the assembled airframe a block entity is part of as they lie now, joined at that block's
     * top, so the body rides it wherever it flies; null if it could not be locked.
     */
    @Nullable
    public static Lock lock(BlockEntity blockEntity, UUID body) {
        return SABLE && blockEntity.getLevel() instanceof ServerLevel ? Access.lock(blockEntity, body) : null;
    }

    /** The block entities of a flying body, by its id, at most so many; none if it is not about. */
    public static List<BlockEntity> blockEntitiesOf(Level level, UUID body, int most) {
        return SABLE && level instanceof ServerLevel server ? Access.blockEntitiesOf(server, body, most) : List.of();
    }

    /**
     * Assembles blocks standing in the world into one flying body, as Sable's own command does, however many there are:
     * a zeppelin, far more than the Physics Assembler takes. The body's anchor is the given block, its bounds the box
     * round the blocks. Whether it assembled: not without Sable, nor with nothing given.
     */
    public static boolean assemble(ServerLevel level, BlockPos anchor, List<BlockPos> blocks, BoundingBox box) {
        return SABLE && !blocks.isEmpty() && Access.assemble(level, anchor, blocks, box);
    }

    /**
     * Keeps the assembled airframe the block entity is part of loaded, or lets it go again: a zeppelin is far longer
     * than the land loaded round anyone aboard reaches, and left to itself Sable would unload it, everyone aboard
     * dropping out of the sky, once the land under its middle is unloaded behind a pilot riding in its bow. Whether it
     * is kept now: false when it is not part of one.
     */
    public static boolean keepLoaded(BlockEntity blockEntity, boolean kept) {
        return SABLE && blockEntity.getLevel() instanceof ServerLevel && Access.keepLoaded(blockEntity, kept);
    }

    /**
     * Changes how fast the assembled airliner the block entity is part of moves, by a velocity in the world's
     * directions, in blocks a second, as a pocket of sinking air does; nothing when it is not part of one.
     */
    public static void jolt(BlockEntity blockEntity, Vec3 change) {
        if (SABLE && blockEntity.getLevel() instanceof ServerLevel) {
            Access.jolt(blockEntity, change);
        }
    }

    /** Whether a point is among an assembled airliner's blocks, in its plot. */
    public static boolean aboard(Level level, Vec3 point) {
        return SABLE && Access.aboard(level, point);
    }

    /** How fast a point among an assembled airliner's blocks moves, in blocks a second; still anywhere else. */
    public static Vec3 velocity(Level level, Vec3 point) {
        return SABLE ? Access.velocity(level, point) : Vec3.ZERO;
    }

    /**
     * Whether Sable lifts an assembled airliner with the block, as it does with the wings' lifting layer: pushed along
     * the way it faces in proportion to the airspeed. What the autopilot's control surfaces turn over (see
     * {@code ControlSurfaces}); false without Sable.
     */
    public static boolean lifts(BlockState state) {
        return SABLE && Access.lifts(state);
    }

    /**
     * The bottom of the lowest block of the assembled airliner a point among its blocks is part of, in its plot (its
     * wheels'); NaN when the point is not among an assembled airliner's blocks.
     */
    public static double underside(Level level, Vec3 point) {
        return SABLE ? Access.underside(level, point) : Double.NaN;
    }

    /**
     * The box round all the blocks of the assembled airliner a point among its blocks is part of, in its plot, corners
     * included; null when the point is not among an assembled airliner's blocks. What the autopilot measures the
     * airliner by, to know how much room it needs to land (see {@code AirlinerShape}).
     */
    @Nullable
    public static BoundingBox blocks(Level level, Vec3 point) {
        return SABLE ? Access.blocks(level, point) : null;
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
     * Lets go of the speed an assembled airliner carried something alive at, which Sable keeps on it once it is off,
     * losing only a hundredth of it a tick: what is thrown clear as an airliner blows up stops where it blew up.
     */
    public static void letGo(LivingEntity entity) {
        if (SABLE) {
            Access.letGo(entity);
        }
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
     * Where a block that stood at a position, in the world or among an assembled airliner's blocks, stands now that an
     * airliner was assembled round it, or split off the one it was part of: its position among the new airliner's
     * blocks, at the same place in the world, if the test accepts the block there; null if none does.
     */
    @Nullable
    public static BlockPos movedAboard(Level level, BlockPos pos, Predicate<BlockState> test) {
        return SABLE ? Access.findAboard(level, Access.toWorld(level, Vec3.atCenterOf(pos)), test) : null;
    }

    /**
     * Has the server tell of the airliners assembled and done away with in each dimension, once Sable is installed: in
     * the tick after any were assembled (or split off another, or loaded), their blocks moved into their plots by then;
     * and as one goes for good, taken apart or broken up rather than merely unloaded, which positions were in its plot,
     * which the next airliner assembled may be given.
     */
    public static void watch(Consumer<ServerLevel> assembled, BiConsumer<ServerLevel, Predicate<BlockPos>> gone) {
        if (SABLE) {
            Access.watch(assembled, gone);
        }
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

    /**
     * The airstairs' steps: with Sable installed, steps whose contacts Sable's physics lets through (see {@link
     * Access#PASS_THROUGH}), so stairs let down to the ground from an assembled airliner never prop it up and tilt it;
     * without it, the plain steps.
     */
    public static AirstairStepBlock airstairStep(BlockBehaviour.Properties properties) {
        return SABLE ? Access.airstairStep(properties) : new AirstairStepBlock(properties);
    }

    /** An evacuation slide's steps, letting the ground through as the airstairs' do (see {@link #airstairStep}). */
    public static SlideStepBlock slideStep(BlockBehaviour.Properties properties) {
        return SABLE ? Access.slideStep(properties) : new SlideStepBlock(properties);
    }

    /**
     * How hard gravity pulls at a point in the world, in blocks a second each second, as Sable's physics has it there;
     * without Sable, as hard as Sable pulls by default.
     */
    public static double gravity(Level level, Vec3 at) {
        return SABLE ? Access.gravity(level, at) : DEFAULT_GRAVITY;
    }

    /** Kept apart so its Sable references are only loaded when Sable is there. */
    private static final class Access {
        static double gravity(Level level, Vec3 at) {
            return DimensionPhysicsData.getGravity(level, new Vector3d(at.x, at.y, at.z)).length();
        }

        /**
         * What Sable's physics does with a step reaching down to the ground as it touches the ground, or anything else
         * not aboard: drops the contact, so the airliner never comes to rest on its stairs or slides. Players still
         * stand on the steps, their collision with an airliner being worked out apart from its physics.
         */
        private static final BlockSubLevelCollisionCallback PASS_THROUGH =
            (pos, other, contact, impactVelocity) -> new BlockSubLevelCollisionCallback.CollisionResult(new Vector3d(), true);

        static AirstairStepBlock airstairStep(BlockBehaviour.Properties properties) {
            return new PassingAirstairStep(properties);
        }

        static SlideStepBlock slideStep(BlockBehaviour.Properties properties) {
            return new PassingSlideStep(properties);
        }

        /** An airstairs' step the ground passes through, to Sable's physics. */
        private static final class PassingAirstairStep extends AirstairStepBlock implements BlockWithSubLevelCollisionCallback {
            PassingAirstairStep(BlockBehaviour.Properties properties) {
                super(properties);
            }

            @Override
            public BlockSubLevelCollisionCallback sable$getCallback() {
                return PASS_THROUGH;
            }
        }

        /** A slide's step the ground passes through, to Sable's physics. */
        private static final class PassingSlideStep extends SlideStepBlock implements BlockWithSubLevelCollisionCallback {
            PassingSlideStep(BlockBehaviour.Properties properties) {
                super(properties);
            }

            @Override
            public BlockSubLevelCollisionCallback sable$getCallback() {
                return PASS_THROUGH;
            }
        }

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

        static boolean sameAirliner(BlockEntity one, BlockEntity other) {
            SubLevel airliner = Sable.HELPER.getContaining(one);
            return airliner != null && airliner == Sable.HELPER.getContaining(other);
        }

        static boolean remove(BlockEntity blockEntity) {
            if (!(Sable.HELPER.getContaining(blockEntity) instanceof ServerSubLevel airliner) || airliner.isRemoved()
                || !(blockEntity.getLevel() instanceof ServerLevel level)) {
                return false;
            }
            ServerSubLevelContainer airliners = SubLevelContainer.getContainer(level);
            if (airliners == null) {
                return false;
            }
            airliners.removeSubLevel(airliner, SubLevelRemovalReason.REMOVED);
            return true;
        }

        static Vec3 toWorld(Level level, Vec3 point) {
            return Sable.HELPER.projectOutOfSubLevel(level, point);
        }

        static boolean aboard(Level level, Vec3 point) {
            return Sable.HELPER.getContaining(level, point) != null;
        }

        /**
         * The ticket an airframe is kept loaded by, its own kind, so Sable keeps it apart from any other mod's. Sable
         * reads a saved ticket back only by a kind it knows when the level loads: this one is made as this class is
         * first loaded, by {@link SableFlight#watch} while the mod is constructed, before any level is.
         */
        private static final SubLevelLoadingTicketType<Unit> KEPT =
            SubLevelLoadingTicketType.create(ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "kept_aloft"), Unit.CODEC);

        static boolean keepLoaded(BlockEntity blockEntity, boolean kept) {
            if (!(Sable.HELPER.getContaining(blockEntity) instanceof ServerSubLevel airframe) || !(blockEntity.getLevel() instanceof ServerLevel level)) {
                return false;
            }
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) {
                return false;
            }
            if (kept) {
                container.addForceLoadTicket(airframe, KEPT, Unit.INSTANCE);
            } else {
                container.removeForceLoadTicket(airframe, KEPT, Unit.INSTANCE);
            }
            return kept;
        }

        static double mass(BlockEntity blockEntity) {
            return Sable.HELPER.getContaining(blockEntity) instanceof ServerSubLevel airframe ? airframe.getMassTracker().getMass() : Double.NaN;
        }

        @Nullable
        static UUID restingOver(BlockEntity blockEntity, double across, double up, double still) {
            Level level = blockEntity.getLevel();
            SubLevel own = Sable.HELPER.getContaining(blockEntity);
            if (own == null) {
                return null;
            }
            Vec3 top = Vec3.atCenterOf(blockEntity.getBlockPos()).add(0, 0.5, 0);
            Vec3 low = own.logicalPose().transformPosition(top.add(-across, 0, -across));
            Vec3 high = own.logicalPose().transformPosition(top.add(across, up, across));
            for (SubLevel other : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(low, high))) {
                if (other != own && restingOn(level, own, other, top.y, still)) {
                    return other.getUniqueId();
                }
            }
            return null;
        }

        /** Whether a body's middle is over a height among an airframe's blocks, and it moves with the airframe. */
        private static boolean restingOn(Level level, SubLevel own, SubLevel other, double over, double still) {
            Vector3dc middle = other.logicalPose().position();
            Vec3 inOwn = own.logicalPose().transformPositionInverse(new Vec3(middle.x(), middle.y(), middle.z()));
            Vector3dc itsOwn = other.logicalPose().rotationPoint();
            Vec3 moving = Sable.HELPER.getVelocity(level, other, new Vec3(itsOwn.x(), itsOwn.y(), itsOwn.z()));
            return inOwn.y > over && moving.distanceTo(Sable.HELPER.getVelocity(level, own, inOwn)) <= still;
        }

        @Nullable
        static UUID bodyAt(Level level, BlockPos pos) {
            SubLevel body = Sable.HELPER.getContaining(level, pos);
            return body == null ? null : body.getUniqueId();
        }

        static double massOf(ServerLevel level, UUID body) {
            ServerSubLevelContainer bodies = SubLevelContainer.getContainer(level);
            return bodies != null && bodies.getSubLevel(body) instanceof ServerSubLevel found ? found.getMassTracker().getMass() : Double.NaN;
        }

        @Nullable
        static Lock lock(BlockEntity blockEntity, UUID body) {
            ServerSubLevelContainer bodies = SubLevelContainer.getContainer((ServerLevel) blockEntity.getLevel());
            if (bodies == null || !(Sable.HELPER.getContaining(blockEntity) instanceof ServerSubLevel own)
                || !(bodies.getSubLevel(body) instanceof ServerSubLevel other) || other == own) {
                return null;
            }
            Vec3 joint = Vec3.atCenterOf(blockEntity.getBlockPos()).add(0, 0.5, 0);
            Vec3 inOther = other.logicalPose().transformPositionInverse(own.logicalPose().transformPosition(joint));
            Quaterniond turned = new Quaterniond(own.logicalPose().orientation()).conjugate().mul(other.logicalPose().orientation());
            try {
                FixedConstraintHandle handle = bodies.physicsSystem().getPipeline().addConstraint(own, other,
                    new FixedConstraintConfiguration(new Vector3d(joint.x, joint.y, joint.z), new Vector3d(inOther.x, inOther.y, inOther.z), turned));
                return handle == null ? null : new HeldLock(handle);
            } catch (IllegalArgumentException refused) {
                return null;
            }
        }

        /** A lock as Sable holds it. */
        private record HeldLock(FixedConstraintHandle handle) implements Lock {
            @Override
            public boolean held() {
                return handle.isValid();
            }

            @Override
            public void release() {
                if (handle.isValid()) {
                    handle.remove();
                }
            }
        }

        static List<BlockEntity> blockEntitiesOf(ServerLevel level, UUID body, int most) {
            ServerSubLevelContainer bodies = SubLevelContainer.getContainer(level);
            if (bodies == null || !(bodies.getSubLevel(body) instanceof ServerSubLevel found)) {
                return List.of();
            }
            List<BlockEntity> all = new ArrayList<>();
            for (PlotChunkHolder holder : found.getPlot().getLoadedChunks()) {
                LevelChunk chunk = holder.getChunk();
                if (chunk != null) {
                    chunk.getBlockEntities().values().stream().limit(most - all.size()).forEach(all::add);
                }
                if (all.size() >= most) {
                    break;
                }
            }
            return all;
        }

        static boolean assemble(ServerLevel level, BlockPos anchor, List<BlockPos> blocks, BoundingBox box) {
            BoundingBox3i bounds = new BoundingBox3i(box.minX() - 1, box.minY() - 1, box.minZ() - 1, box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
            return !SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks, bounds).getMassTracker().isInvalid();
        }

        static void jolt(BlockEntity blockEntity, Vec3 change) {
            if (Sable.HELPER.getContaining(blockEntity) instanceof ServerSubLevel airliner) {
                RigidBodyHandle.of(airliner).addLinearAndAngularVelocity(new Vector3d(change.x, change.y, change.z), new Vector3d());
            }
        }

        static Vec3 velocity(Level level, Vec3 point) {
            return Sable.HELPER.getContaining(level, point) == null ? Vec3.ZERO : Sable.HELPER.getVelocity(level, point);
        }

        static boolean lifts(BlockState state) {
            return state.getBlock() instanceof BlockSubLevelLiftProvider;
        }

        static double underside(Level level, Vec3 point) {
            SubLevel airliner = Sable.HELPER.getContaining(level, point);
            if (airliner == null) {
                return Double.NaN;
            }
            BoundingBox3ic blocks = airliner.getPlot().getBoundingBox();
            return blocks == BoundingBox3i.EMPTY ? Double.NaN : blocks.minY();
        }

        @Nullable
        static BoundingBox blocks(Level level, Vec3 point) {
            SubLevel airliner = Sable.HELPER.getContaining(level, point);
            if (airliner == null) {
                return null;
            }
            BoundingBox3ic box = airliner.getPlot().getBoundingBox();
            return box == BoundingBox3i.EMPTY ? null : new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
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

        static void letGo(LivingEntity entity) {
            ((LivingEntityMovementExtension) entity).sable$getInheritedVelocity().zero();
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

        static void watch(Consumer<ServerLevel> assembled, BiConsumer<ServerLevel, Predicate<BlockPos>> gone) {
            NeoForge.EVENT_BUS.addListener(ForgeSableSubLevelContainerReadyEvent.class, event -> {
                if (event.getLevel() instanceof ServerLevel level) {
                    event.getContainer().addObserver(new Watch(level, assembled, gone));
                }
            });
        }

        /**
         * Tells of one dimension's airliners as they are assembled and done away with. An airliner is added before its
         * blocks move in, so it is told of in the tick after.
         */
        private static final class Watch implements SubLevelObserver {
            private final ServerLevel level;
            private final Consumer<ServerLevel> assembled;
            private final BiConsumer<ServerLevel, Predicate<BlockPos>> gone;
            private boolean added;

            Watch(ServerLevel level, Consumer<ServerLevel> assembled, BiConsumer<ServerLevel, Predicate<BlockPos>> gone) {
                this.level = level;
                this.assembled = assembled;
                this.gone = gone;
            }

            @Override
            public void onSubLevelAdded(SubLevel airliner) {
                added = true;
            }

            @Override
            public void onSubLevelRemoved(SubLevel airliner, SubLevelRemovalReason reason) {
                if (reason == SubLevelRemovalReason.REMOVED) {
                    LevelPlot plot = airliner.getPlot();
                    gone.accept(level, pos -> plot.contains(pos.getX() + 0.5, pos.getZ() + 0.5));
                }
            }

            @Override
            public void tick(SubLevelContainer airliners) {
                if (added) {
                    added = false;
                    assembled.accept(level);
                }
            }
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
            Vector3d at = new Vector3d(position.x, position.y, position.z);
            double gravity = gravity(level, position);
            return new State(position, forward, right, up, velocity, gravity, DimensionPhysicsData.getAirPressure(level, at));
        }

        private static Vec3 direction(Level level, Vec3 centre, Vec3 position, Direction direction) {
            Vec3 tip = Sable.HELPER.projectOutOfSubLevel(level, centre.add(Vec3.atLowerCornerOf(direction.getNormal())));
            return tip.subtract(position).normalize();
        }
    }
}
