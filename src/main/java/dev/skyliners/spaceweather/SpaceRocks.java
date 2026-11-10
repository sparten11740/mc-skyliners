package dev.skyliners.spaceweather;

import dev.skyliners.block.SpaceRockBlock;
import dev.skyliners.compat.SableFlight;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The rocks of every asteroid storm and giant asteroid blowing on the server, as a laser cannon shoots at them (see
 * {@code LaserCannonBlockEntity} and {@code CannonRocket}): a rock near her is a real body of blocks (see {@link
 * RockBodies}), and a bolt that meets one breaks just the block it meets, a rocket a hole's worth round where it went
 * off, cracking a giant's rock right through besides. What is no longer joined on to a rock flies off as a piece of its
 * own; a rock worn through bursts, every client told so once (see {@link RockBurst}).
 *
 * <p>Nothing is kept but the weather blowing or its rocks flying on: each adds itself as it is told and takes itself
 * away once its rocks are gone, after it is over, or is let go once the ship it blew round is gone, unloaded
 * mid-storm. Its bodies are never saved: any found with no weather flying them is taken away.
 */
public final class SpaceRocks {
    /**
     * How far past a rock's edge a shot still strikes it, in blocks: the server finds it a tick or so on from where its
     * gunner saw it, and a quick small rock moves three blocks a tick.
     */
    public static final double GRACE = 1.5;
    /**
     * How big a hole a rocket makes in a rock, in blocks around where it went off: at least, for each block across the
     * rock is, and at most, as a giant's is cracked right through besides, and every block broken out of a body that
     * flies costs the server.
     */
    private static final double BLAST_FROM = 4;
    private static final double BLAST_PER_BLOCK = 0.15;
    private static final double BLAST_MOST = 6;
    /** How often rocks with no weather flying them are looked for, in ticks. */
    private static final int SWEEP_EVERY = 200;
    /** How a broken rock's block is told: to clients, no neighbours prodded, for a rock has none that care. */
    private static final int BREAK_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** The weather blowing now, in any level. */
    private static final List<SpaceWeather> BLOWING = new ArrayList<>();
    /**
     * How many rocks this client has seen burst in the weather it shows; the client sets it, for a play-test to read
     * (see {@code BlockpilotCommands}).
     */
    public static IntSupplier clientBurstsSeen = () -> 0;
    /**
     * How far from its player the farthest rock is that this client holds as a real body, in blocks; the client sets it,
     * for a play-test to read.
     */
    public static DoubleSupplier clientRealFarthest = () -> 0;
    /**
     * How many of the weather's rocks this client still has on their way; the client sets it, for a play-test to read.
     */
    public static IntSupplier clientRocksFlying = () -> 0;

    private SpaceRocks() {
    }

    /**
     * A rock a shot meets: of which weather, its number among the weather's rocks, where the shot meets it, in the
     * world, and how far along the shot that is, in blocks.
     */
    public record Hit(SpaceWeather weather, int rock, Vec3 at, double distance) {
        /** The rock met, to home in on. */
        public Target target() {
            return new Target(weather, rock);
        }
    }

    /** A rock a cannon's rocket homes in on: of which weather, and its number among the weather's rocks. */
    public record Target(SpaceWeather weather, int rock) {
        /** Where its middle is now, in the world; null once it is gone, burst or flown past, or its weather over. */
        @Nullable
        public Vec3 centre(ServerLevel level) {
            return BLOWING.contains(weather) ? weather.rockCentre(level, rock) : null;
        }

        /** How far out from its middle a shot strikes it, in blocks, {@link #GRACE} included. */
        public double reach() {
            return weather.rockSize(rock) / 2 + GRACE;
        }
    }

    /** Weather told, whose rocks may now be shot at. */
    static void blowing(SpaceWeather weather) {
        if (!BLOWING.contains(weather)) {
            BLOWING.add(weather);
        }
    }

    /** Weather whose rocks are all gone, burst or flown on out of sight after it was over. */
    static void over(SpaceWeather weather) {
        BLOWING.remove(weather);
    }

    /** As the server stops: no weather blows in the next world opened. */
    public static void forget() {
        BLOWING.clear();
    }

    /** Each server tick: now and then, every rock with no weather still flying it taken away. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % SWEEP_EVERY == 0) {
            BLOWING.removeIf(SpaceWeather::stale);
            sweep(server, flown());
        }
    }

    /**
     * As the server stops, before the world is saved: every rock taken away, for none to come back when it is opened.
     */
    public static void stopping(MinecraftServer server) {
        sweep(server, Set.of());
    }

    private static void sweep(MinecraftServer server, Set<UUID> flown) {
        for (ServerLevel level : server.getAllLevels()) {
            SableFlight.sweepRocks(level, flown);
        }
    }

    /** Every rock's body and every piece of one that weather blowing is flying. */
    private static Set<UUID> flown() {
        Set<UUID> all = new HashSet<>();
        for (SpaceWeather weather : BLOWING) {
            all.addAll(weather.bodies());
        }
        return all;
    }

    /** Whether any rock is a real body now in a level, for a shot there to reach out as far as it could be. */
    public static boolean flying(ServerLevel level) {
        for (SpaceWeather weather : BLOWING) {
            if (weather.hasBodies(level)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The nearest rock a shot from a point along a way meets within so many blocks, of any weather blowing in the
     * level; none for none.
     */
    @Nullable
    public static Hit along(ServerLevel level, Vec3 from, Vec3 way, double reach) {
        if (BLOWING.isEmpty()) {
            return null;
        }
        BLOWING.removeIf(SpaceWeather::stale);
        Vec3 unit = way.normalize();
        Hit nearest = null;
        for (SpaceWeather weather : BLOWING) {
            Hit hit = weather.along(level, from, unit, nearest == null ? reach : nearest.distance());
            nearest = hit != null ? hit : nearest;
        }
        return nearest;
    }

    /**
     * A bolt meeting a block, travelling along a way: if the block is a space rock's, that block alone is broken, chips
     * of its stone flying where it met it, and its rock goes where it is knocked from now on. Whether it was.
     */
    public static boolean breakBlock(ServerLevel level, BlockPos block, Vec3 at, Vec3 way) {
        BlockState stone = level.getBlockState(block);
        if (!(stone.getBlock() instanceof SpaceRockBlock)) {
            return false;
        }
        UUID rock = SableFlight.bodyAt(level, block);
        level.setBlock(block, Blocks.AIR.defaultBlockState(), BREAK_FLAGS);
        SpaceWeather.chips(level, SableFlight.toWorld(level, at), way.normalize(), stone);
        tellShot(rock, way.normalize());
        return true;
    }

    /**
     * A rocket going off against a block, travelling along a way: if the block is a space rock's, a hole blown there,
     * and a rock heavy enough cracked through. Whether it was.
     */
    public static boolean blast(ServerLevel level, BlockPos block, Vec3 at, Vec3 way) {
        BlockState stone = level.getBlockState(block);
        if (!(stone.getBlock() instanceof SpaceRockBlock)) {
            return false;
        }
        UUID rock = SableFlight.bodyAt(level, block);
        Vec3 inWorld = SableFlight.toWorld(level, at);
        crater(level, rock, inWorld, blastRadius(rock));
        SpaceWeather.chips(level, inWorld, way.normalize(), stone);
        tellShot(rock, way.normalize());
        crack(level, rock, inWorld, way);
        return true;
    }

    /**
     * A rocket going off as it passes the rock it homes in on, travelling along a way: a hole blown in it where its
     * blocks lie nearest, along the line from where it went off to the rock's middle, and a rock heavy enough cracked
     * through.
     */
    public static void blast(ServerLevel level, Hit hit, Vec3 way) {
        Vec3 middle = hit.weather().rockCentre(level, hit.rock());
        UUID rock = hit.weather().rockBody(hit.rock());
        if (middle == null || rock == null) {
            return;
        }
        Vec3 inward = middle.subtract(hit.at());
        Vec3 unit = inward.lengthSqr() < 1e-6 ? way.normalize() : inward.normalize();
        Vec3 met = firstBlockAlong(level, rock, hit.at(), unit, hit.weather().rockSize(hit.rock()));
        crater(level, rock, met, blastRadius(rock));
        tellShot(rock, way.normalize());
        crack(level, rock, met, way);
    }

    /** Where a line from a point first meets a block of a rock's, within so far; the point itself if it meets none. */
    private static Vec3 firstBlockAlong(ServerLevel level, UUID rock, Vec3 from, Vec3 unit, double far) {
        for (double along = 0; along < far; along += 0.5) {
            Vec3 point = from.add(unit.scale(along));
            if (level.getBlockState(BlockPos.containing(SableFlight.toRock(level, rock, point))).getBlock() instanceof SpaceRockBlock) {
                return point;
            }
        }
        return from;
    }

    /** How big a hole a rocket makes in a rock, in blocks. */
    private static double blastRadius(@Nullable UUID rock) {
        return Math.min(BLAST_MOST, BLAST_FROM + BLAST_PER_BLOCK * sizeOf(rock));
    }

    /** How big a rock's body is, in blocks across, among those weather is flying; none if it is not one of them. */
    private static double sizeOf(@Nullable UUID rock) {
        for (SpaceWeather weather : BLOWING) {
            double size = weather.sizeOf(rock);
            if (size > 0) {
                return size;
            }
        }
        return 0;
    }

    /** A rock a rocket went off against at a point in the world, flying along a way, cracked if heavy enough. */
    private static void crack(ServerLevel level, @Nullable UUID rock, Vec3 at, Vec3 way) {
        if (rock != null) {
            BLOWING.forEach(weather -> weather.crack(level, rock, at, way));
        }
    }

    private static void tellShot(@Nullable UUID rock, Vec3 way) {
        if (rock != null) {
            BLOWING.forEach(weather -> weather.shotAt(rock, way));
        }
    }

    /**
     * A hole in a rock's body, every block of it so far round a point in the world broken: what it no longer holds on
     * to flies off as Sable finds it has come apart.
     */
    static void crater(ServerLevel level, @Nullable UUID rock, Vec3 at, double radius) {
        if (rock == null) {
            return;
        }
        Vec3 inRock = SableFlight.toRock(level, rock, at);
        BlockPos middle = BlockPos.containing(inRock);
        int reach = Mth.ceil(radius);
        for (BlockPos block : BlockPos.betweenClosed(middle.offset(-reach, -reach, -reach), middle.offset(reach, reach, reach))) {
            if (block.getCenter().distanceToSqr(inRock) <= radius * radius && level.getBlockState(block).getBlock() instanceof SpaceRockBlock) {
                level.setBlock(block, Blocks.AIR.defaultBlockState(), BREAK_FLAGS);
            }
        }
    }

    /**
     * Where a shot from a point along a way of length 1 first meets a ball round a middle so far out, within so many
     * blocks along it: how far along, or less than nothing where it misses.
     */
    public static double meets(Vec3 from, Vec3 unit, Vec3 middle, double radius, double reach) {
        Vec3 to = middle.subtract(from);
        double along = to.dot(unit);
        double off = to.lengthSqr() - along * along;
        double radiusSqr = radius * radius;
        if (off > radiusSqr) {
            return -1;
        }
        double entered = along - Math.sqrt(radiusSqr - off);
        double met = entered < 0 ? Math.max(0, along) : entered;
        return along < -radius || met > reach ? -1 : met;
    }
}
