package dev.aerolarge.airport;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.airframe.AirframeLayout;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.block.NavLightBlock;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A runway going down, sized for the airliner at the scale it is built at: as wide as its wingspan and a shoulder
 * either side, clear above as high as its fin and a few blocks more, and twice as long as it needs to take off (the
 * roll on the autopilot up to its rotation speed, the few seconds from there to lifting off, and its own length). Its
 * surface takes the place of the layer it is laid in, painted as an airport's runway is (see {@link RunwayMarkings}),
 * with lights in its centre line; everything above it is cleared away, and holes under it are filled in. White edge
 * lights run down both sides, green ones across its start and red ones across its far end.
 *
 * <p>Laid out a chunk at a time, a few chunks a tick, so the hundreds of chunks a runway crosses are loaded (generated,
 * where nobody has been) without stalling the server. Once done it is kept on the record of runways (see
 * {@link Runways}), for an autopilot flying to a pin near it to land on.
 */
public final class RunwayBuild {
    /** How wide each shoulder is beyond the wingtips, and how high it is cleared above the fin, in blocks. */
    private static final int SPAN_MARGIN = 4;
    private static final int HEADROOM = 6;
    /** How long the airliner takes from rotation to lifting off, in seconds, and how many times its take-off run the runway is. */
    private static final double LIFT_OFF_SECONDS = 3;
    private static final int TAKE_OFFS = 2;
    /** How far apart the edge lights are, and how far apart across the threshold and end lights, in blocks. */
    private static final int EDGE_LIGHTS = 16;
    private static final int END_LIGHTS = 4;
    /** How deep holes under the surface are filled in, at most. */
    private static final int FILL_DEPTH = 8;
    /** How many chunks are laid each tick. */
    private static final int CHUNKS_PER_TICK = 2;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final BlockState ASPHALT = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState PAINT = Blocks.WHITE_CONCRETE.defaultBlockState();
    private static final BlockState SHOULDER = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState CENTRE_LIGHT = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState FILL = Blocks.STONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final Map<ServerLevel, List<RunwayBuild>> ACTIVE = new IdentityHashMap<>();

    /** How big a runway is: how long, how far out either side of its centre line, and how high it is cleared. */
    public record Size(int length, int halfWidth, int clearance) {
        /** The runway for the airliner at the scale it is built at now. */
        public static Size forAirliner() {
            Size airliner = measure(AirframeLayout.create(AerolargeConfig.SCALE.get(), false));
            double rotate = AerolargeConfig.AUTOPILOT_ROTATE_SPEED.get();
            double roll = rotate * rotate / (2 * AutopilotBlockEntity.MAX_ACCELERATION);
            int takeOff = Mth.ceil(roll + LIFT_OFF_SECONDS * rotate + airliner.length());
            return new Size(TAKE_OFFS * takeOff, airliner.halfWidth() + SPAN_MARGIN + RunwayMarkings.SHOULDER, airliner.clearance() + HEADROOM);
        }

        /** The airliner's own length, half its span and its height, from its layout. */
        private static Size measure(AirframeLayout layout) {
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (BlockPos cell : layout.cells().keySet()) {
                minX = Math.min(minX, cell.getX());
                maxX = Math.max(maxX, cell.getX());
                minY = Math.min(minY, cell.getY());
                maxY = Math.max(maxY, cell.getY());
                minZ = Math.min(minZ, cell.getZ());
                maxZ = Math.max(maxZ, cell.getZ());
            }
            return new Size(maxZ - minZ + 1, Math.max(-minX, maxX), maxY - minY + 1);
        }
    }

    private final BlockPos start;
    private final Direction along;
    private final Direction right;
    private final Size size;
    private final RunwayMarkings markings;
    /** The chunks the runway crosses, from its start on, and how many have been laid. */
    private final List<ChunkPos> chunks;
    private int next;
    @Nullable
    private final UUID builder;

    private RunwayBuild(BlockPos start, Direction along, Size size, @Nullable UUID builder) {
        this.start = start;
        this.along = along;
        this.right = along.getClockWise();
        this.size = size;
        this.markings = new RunwayMarkings(size.length(), size.halfWidth(), along);
        this.builder = builder;
        this.chunks = crossed();
    }

    /**
     * Starts laying a runway: its start's middle at {@code start}, in the layer it is laid in, running {@code along}
     * from there. Progress goes to the builder, if a player started it.
     */
    public static void start(ServerLevel level, BlockPos start, Direction along, @Nullable ServerPlayer builder) {
        ACTIVE.computeIfAbsent(level, ignored -> new ArrayList<>())
            .add(new RunwayBuild(start, along, Size.forAirliner(), builder != null ? builder.getUUID() : null));
    }

    public static boolean isRunning(ServerLevel level) {
        List<RunwayBuild> builds = ACTIVE.get(level);
        return builds != null && !builds.isEmpty();
    }

    public static void tick(ServerLevel level) {
        List<RunwayBuild> builds = ACTIVE.get(level);
        if (builds != null) {
            builds.removeIf(build -> build.step(level));
        }
    }

    public static void drop(ServerLevel level) {
        ACTIVE.remove(level);
    }

    /** Where a spot of the runway is in the world: so far along it and so far right of its centre line, at a height. */
    private BlockPos at(int alongRunway, int across, int y) {
        return new BlockPos(
            start.getX() + along.getStepX() * alongRunway + right.getStepX() * across, y,
            start.getZ() + along.getStepZ() * alongRunway + right.getStepZ() * across);
    }

    /** The chunks the runway and its lights cross, from its start on. */
    private List<ChunkPos> crossed() {
        Set<ChunkPos> found = new LinkedHashSet<>();
        for (int a = -1; a <= size.length(); a++) {
            for (int c = -size.halfWidth(); c <= size.halfWidth(); c += 8) {
                found.add(new ChunkPos(at(a, c, start.getY())));
            }
            found.add(new ChunkPos(at(a, size.halfWidth(), start.getY())));
        }
        return new ArrayList<>(found);
    }

    /** Lays the next few chunks; true once the whole runway is down. */
    private boolean step(ServerLevel level) {
        int end = Math.min(chunks.size(), next + CHUNKS_PER_TICK);
        for (; next < end; next++) {
            layChunk(level, chunks.get(next));
        }
        boolean done = next >= chunks.size();
        ServerPlayer player = builder != null && level.getPlayerByUUID(builder) instanceof ServerPlayer online ? online : null;
        if (done) {
            Runways.add(level, new Runways.Built(start, along, size.length(), size.halfWidth()));
            tell(player, Component.translatable("message.aerolarge.runway.built", size.length(), 2 * size.halfWidth() + 1), false);
        } else if (level.getGameTime() % 10 == 0) {
            tell(player, Component.translatable("message.aerolarge.runway.building", 100 * next / chunks.size()), true);
        }
        return done;
    }

    private static void tell(@Nullable ServerPlayer player, Component message, boolean overlay) {
        if (player != null) {
            player.displayClientMessage(message, overlay);
        }
    }

    /** Lays every column of the runway (and its lights) that lies in one chunk. */
    private void layChunk(ServerLevel level, ChunkPos chunk) {
        level.getChunk(chunk.x, chunk.z);
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                layColumn(level, x, z);
            }
        }
    }

    /** One column, if it is part of the runway: where it is along and across the runway decides what goes there. */
    private void layColumn(ServerLevel level, int x, int z) {
        int dx = x - start.getX();
        int dz = z - start.getZ();
        int alongRunway = dx * along.getStepX() + dz * along.getStepZ();
        int across = dx * right.getStepX() + dz * right.getStepZ();
        if (Math.abs(across) > size.halfWidth() || alongRunway < -1 || alongRunway > size.length()) {
            return;
        }
        BlockPos surface = new BlockPos(x, start.getY(), z);
        if (alongRunway < 0 || alongRunway == size.length()) {
            endLight(level, surface, alongRunway < 0, across);
            return;
        }
        level.setBlock(surface, surface(alongRunway, across), FLAGS);
        clearAbove(level, surface);
        fillUnder(level, surface.below());
        if (Math.abs(across) == size.halfWidth() - RunwayMarkings.SHOULDER && alongRunway % EDGE_LIGHTS == EDGE_LIGHTS / 2) {
            level.setBlock(surface.above(), light(NavLightBlock.Kind.WHITE), FLAGS);
        }
    }

    private BlockState surface(int alongRunway, int across) {
        return switch (markings.at(alongRunway, across)) {
            case WHITE -> PAINT;
            case SHOULDER -> SHOULDER;
            case CENTRE_LIGHT -> CENTRE_LIGHT;
            case ASPHALT -> ASPHALT;
        };
    }

    /** A row of lights just off either end of the runway, across its paved width: green at its start, red at its end. */
    private void endLight(ServerLevel level, BlockPos surface, boolean atStart, int across) {
        int paved = size.halfWidth() - RunwayMarkings.SHOULDER;
        if (Math.abs(across) > paved || Math.floorMod(across, END_LIGHTS) != 0) {
            return;
        }
        level.setBlock(surface, ASPHALT, FLAGS);
        clearAbove(level, surface);
        level.setBlock(surface.above(), light(atStart ? NavLightBlock.Kind.GREEN : NavLightBlock.Kind.RED), FLAGS);
    }

    private static BlockState light(NavLightBlock.Kind kind) {
        return AerolargeRegistry.NAV_LIGHT.get().defaultBlockState().setValue(NavLightBlock.FACING, Direction.UP).setValue(NavLightBlock.KIND, kind);
    }

    /** Clears everything above a spot of the surface, up to the runway's clearance. */
    private void clearAbove(ServerLevel level, BlockPos surface) {
        BlockPos.MutableBlockPos pos = surface.mutable();
        for (int up = 1; up <= size.clearance() && level.isInWorldBounds(pos.move(Direction.UP)); up++) {
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, AIR, FLAGS);
            }
        }
    }

    /** Fills a hole under the surface, down to whatever holds it up, so the runway does not hang over a ravine or a lake. */
    private static void fillUnder(ServerLevel level, BlockPos below) {
        BlockPos.MutableBlockPos pos = below.mutable();
        for (int depth = 0; depth < FILL_DEPTH && level.isInWorldBounds(pos); depth++, pos.move(Direction.DOWN)) {
            BlockState there = level.getBlockState(pos);
            if (!there.isAir() && !there.canBeReplaced()) {
                return;
            }
            level.setBlock(pos, FILL, FLAGS);
        }
    }
}
