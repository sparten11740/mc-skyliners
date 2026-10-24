package dev.aerolarge.block;

import dev.aerolarge.compat.SableFlight;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Where the laser path clearer looks for what is in the airliner's way: rays cast along its flight path from a grid of
 * points across its front (as wide as its wingspan, from under its wheels to the top of its fin), a few each tick, so
 * a whole sweep takes a handful of ticks and costs little in any one. What each ray first meets is an obstacle; the
 * last whole sweep's, nearest first, are what the turret fires at. Each block vaporised spreads the clearing round it,
 * square to the path, as far as the airliner reaches: the blocks just past the bite become the next to fire at, so
 * a hole spreads across a tree, a wall or a hillside until the way is clear, not only where a ray happened to strike.
 * Anything among another airliner's or vehicle's blocks is no obstacle: the laser never fires at another airliner.
 */
final class LaserAim {
    /** The grid of rays across the airliner's front and up it: a ray about every 13 blocks of wingspan at scale 2. */
    private static final int COLUMNS = 13;
    private static final int ROWS = 6;
    /** Rays cast each tick: a whole sweep every thirteen ticks. */
    private static final int RAYS_PER_TICK = 6;
    /**
     * How far from a block vaporised the clearing spreads to next, in blocks, square to the path: just past the edge of
     * the bite (three across), so the next bite meets it.
     */
    private static final int SPREAD = 3;
    /** The most blocks waiting to be fired at as the clearing spreads, so a cliff never keeps more than this many. */
    private static final int MOST_SPREAD = 96;
    /**
     * How far up the airliner's lowest row of blocks (the wheel mounts, with the gear down) the lowest rays run, in
     * blocks: at the wheels' hubs, a block and a half above the bottom of the tires, so they skim level ground and find
     * only what the wheels would run into.
     */
    private static final double WHEEL_HUBS = 0.5;

    /**
     * How far the airliner reaches from the turret to its left (negative) and right, and how far below and above the
     * muzzle the rays run, in blocks.
     */
    private double left;
    private double right;
    private double low;
    private double high;
    /** The next ray of the sweep, the obstacles this sweep has found so far, and the last whole sweep's, nearest first. */
    private int next;
    private final List<Obstacle> sweep = new ArrayList<>();
    private List<Obstacle> obstacles = List.of();
    /** The blocks the clearing spreads to next, oldest first, so the hole grows evenly round where it started. */
    private final Deque<BlockPos> spreading = new ArrayDeque<>();

    /** A block in the way, and how far ahead along the path it is, in blocks. */
    private record Obstacle(BlockPos pos, double distance) {
    }

    /**
     * Measures how far the airliner reaches round the turret, in its plot, from the box round its blocks: the turret at
     * a position in it, the nose pointing the given way, the muzzle at a height in the plot.
     */
    void outline(BoundingBox blocks, BlockPos turret, Direction nose, double muzzleY) {
        Direction across = nose.getClockWise();
        double first = (blocks.minX() - turret.getX()) * across.getStepX() + (blocks.minZ() - turret.getZ()) * across.getStepZ();
        double second = (blocks.maxX() - turret.getX()) * across.getStepX() + (blocks.maxZ() - turret.getZ()) * across.getStepZ();
        left = Math.min(first, second);
        right = Math.max(first, second);
        low = blocks.minY() + WHEEL_HUBS - muzzleY;
        high = blocks.maxY() + 1 - muzzleY;
    }

    /**
     * The height of the bottom of the airliner's wheels in the world, from the muzzle's position and the airliner's up:
     * a block and a half under the lowest rays.
     */
    double wheels(Vec3 muzzle, Vec3 up) {
        return muzzle.add(up.scale(low - WHEEL_HUBS - 1)).y;
    }

    /** How far out from the path the airliner reaches any way, in blocks: the most of its outline. */
    double reach() {
        return Math.max(Math.max(-left, right), Math.max(-low, high));
    }

    /**
     * This tick's rays, along the path (a direction in the world) from points across the airliner's front round the
     * muzzle, the airliner's right and up as given, so far ahead.
     */
    void sweep(Level level, Vec3 muzzle, Vec3 path, Vec3 across, Vec3 up, double range) {
        for (int i = 0; i < RAYS_PER_TICK; i++) {
            int column = next % COLUMNS;
            int row = next / COLUMNS;
            double out = left + (right - left) * column / (COLUMNS - 1);
            double rise = low + (high - low) * row / (ROWS - 1);
            cast(level, muzzle.add(across.scale(out)).add(up.scale(rise)), path, range);
            next++;
            if (next == COLUMNS * ROWS) {
                finish();
            }
        }
    }

    private void cast(Level level, Vec3 from, Vec3 path, double range) {
        BlockHitResult hit = level.clip(new ClipContext(from, from.add(path.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
            CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.BLOCK && !SableFlight.aboard(level, Vec3.atCenterOf(hit.getBlockPos()))) {
            sweep.add(new Obstacle(hit.getBlockPos(), hit.getLocation().distanceTo(from)));
        }
    }

    /** A whole sweep done: its obstacles are the ones fired at, nearest first, and the next sweep starts. */
    private void finish() {
        sweep.sort(Comparator.comparingDouble(Obstacle::distance));
        obstacles = List.copyOf(sweep);
        sweep.clear();
        next = 0;
    }

    /**
     * The next block to fire at: where the clearing spreads to, oldest first, and otherwise the nearest obstacle of the
     * last whole sweep still standing; taken off its list. Null for none.
     */
    @Nullable
    BlockPos next(Level level) {
        while (!spreading.isEmpty()) {
            BlockPos pos = spreading.removeFirst();
            if (!level.getBlockState(pos).isAir()) {
                return pos;
            }
        }
        return nearest(level);
    }

    /**
     * A block vaporised: the clearing spreads round it, square to the path (the airliner's right and up as given), to
     * the blocks in the airliner's way just past the bite, as long as there are not too many waiting already.
     */
    void spread(Level level, BlockPos struck, Vec3 across, Vec3 up, Vec3 muzzle, Vec3 path, double range) {
        Vec3 middle = Vec3.atCenterOf(struck);
        for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
            BlockPos next = BlockPos.containing(middle.add(across.scale(step[0] * SPREAD)).add(up.scale(step[1] * SPREAD)));
            boolean inWay = inWay(muzzle, path, across, up, range, Vec3.atCenterOf(next));
            if (spreading.size() < MOST_SPREAD && inWay && !level.getBlockState(next).isAir()) {
                spreading.addLast(next);
            }
        }
    }

    /** Forgets where the clearing was spreading to, as the laser is disarmed. */
    void forget() {
        spreading.clear();
        obstacles = List.of();
    }

    /** The nearest obstacle of the last whole sweep still standing, taken off the list; null for none. */
    @Nullable
    private BlockPos nearest(Level level) {
        List<Obstacle> queue = new ArrayList<>(obstacles);
        BlockPos found = null;
        while (!queue.isEmpty() && found == null) {
            BlockPos pos = queue.removeFirst().pos();
            if (!level.getBlockState(pos).isAir()) {
                found = pos;
            }
        }
        obstacles = List.copyOf(queue);
        return found;
    }

    /**
     * Whether a point lies in the airliner's way: ahead of the muzzle along the path, within range, and within its
     * outline round the path (the airliner's right and up as given): out to its wingtips, and from its wheels' hubs to
     * the top of its fin, so nothing under its wheels, the ground it rolls on, is ever in the way.
     */
    boolean inWay(Vec3 muzzle, Vec3 path, Vec3 across, Vec3 up, double range, Vec3 point) {
        Vec3 offset = point.subtract(muzzle);
        double ahead = offset.dot(path);
        double side = offset.dot(across);
        double rise = offset.dot(up);
        return ahead > 0 && ahead < range && side >= left && side <= right && rise >= low && rise <= high;
    }
}
