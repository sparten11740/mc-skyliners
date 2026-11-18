package dev.skyliners.block;

import dev.skyliners.compat.SableFlight;
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
 * Where a laser path clearer's turret looks for what is in the airliner's way, and what it fires at next: rays cast a
 * few each tick, so a whole sweep takes a handful of ticks and costs little in any one. What each ray first meets is an
 * obstacle; the last whole sweep's, nearest first, are what the turret fires at. Each block vaporised spreads the
 * clearing every way round it, as far as her way goes: the blocks just past the bite become the next to fire at, so a
 * hole spreads through a tree, a house or a hillside until the way is clear, not only where a ray happened to strike,
 * and nothing is left hanging between the rows of rays. Anything among another airliner's or vehicle's blocks is no
 * obstacle: the laser never fires at another airliner. How the rays lie, and what is in her way, is each kind's own:
 * along her path ({@link LaserAim}), or all round the turret on the ground ({@link LaserRing}).
 */
abstract class LaserLook {
    /** Rows of rays up the airliner, from her wheels' hubs to the top of her fin. */
    static final int ROWS = 6;
    /** Rays cast each tick it looks. */
    private static final int RAYS_PER_TICK = 6;
    /**
     * How far from a block vaporised the clearing spreads to next, in blocks, each way round it: just past the edge of
     * the bite (three across), so the next bite meets it; or, where that is open already, the block just past the bite,
     * so the last row of a wall or a roof is never left standing between them.
     */
    private static final int SPREAD = 3;
    private static final int SPREAD_NEAR = 2;
    /** Those reaches in the order they are tried. */
    private static final int[] SPREAD_REACHES = {SPREAD, SPREAD_NEAR};
    /**
     * How far under her wheels' hubs, where the lowest rays run, the clearing still spreads, in blocks: down past the
     * bottom of her tires, so what stands lower than the rays, the stumps of a wall or a house it has cut down, goes
     * too, as far down as the floor it is given lets it.
     */
    private static final double STUMPS = 3;
    /** The most blocks waiting to be fired at as the clearing spreads, so a cliff never keeps more than this many. */
    private static final int MOST_SPREAD = 96;
    /** The ways round a block the clearing spreads: to its faces, its edges and its corners. */
    private static final List<BlockPos> AROUND = BlockPos.betweenClosedStream(-1, -1, -1, 1, 1, 1)
        .filter(step -> !step.equals(BlockPos.ZERO))
        .map(BlockPos::immutable)
        .toList();
    /**
     * How far up the airliner's lowest row of blocks (the wheel mounts, with the gear down) the lowest rays run, in
     * blocks: at the wheels' hubs, a block and a half above the bottom of the tires, so they skim level ground and find
     * only what the wheels would run into.
     */
    private static final double WHEEL_HUBS = 0.5;

    /** How far below and above the muzzle the rays run, in blocks: from her wheels' hubs to the top of her fin. */
    protected double low;
    protected double high;
    /**
     * The next ray of the sweep, the obstacles this sweep has found so far, and the last whole sweep's, nearest first.
     */
    private int next;
    private final List<Obstacle> found = new ArrayList<>();
    private List<Obstacle> obstacles = List.of();
    /** The blocks the clearing spreads to next, oldest first, so the hole grows evenly round where it started. */
    private final Deque<BlockPos> spreading = new ArrayDeque<>();

    /**
     * Where the turret looks out from and how the airliner lies, in the world: the muzzle, her path (the way she goes,
     * or standing still, the way her nose points), her right and her up, and how far the laser reaches, in blocks.
     */
    record Lookout(Vec3 muzzle, Vec3 path, Vec3 right, Vec3 up, double range) {
    }

    /** One ray: where it starts and the way it goes, in the world. */
    protected record Ray(Vec3 from, Vec3 way) {
    }

    /** A block in the way, and how far from where its ray started it is, in blocks. */
    private record Obstacle(BlockPos pos, double distance) {
    }

    /**
     * Measures how far the airliner reaches round the turret, in its plot, from the box round its blocks: the turret at
     * a position in it, the nose pointing the given way, the muzzle at a height in the plot.
     */
    void outline(BoundingBox blocks, BlockPos turret, Direction nose, double muzzleY) {
        low = blocks.minY() + WHEEL_HUBS - muzzleY;
        high = blocks.maxY() + 1 - muzzleY;
    }

    /** How far above the muzzle a row of rays runs, in blocks, the lowest row first. */
    protected double rise(int row) {
        return low + (high - low) * row / (ROWS - 1);
    }

    /** How many rays a whole sweep casts. */
    protected abstract int rays();

    /** The ray of the sweep with this index. */
    protected abstract Ray ray(int index, Lookout look);

    /** Whether a point in the world lies in the airliner's way, as this kind looks. */
    abstract boolean inWay(Lookout look, Vec3 point);

    /** A whole sweep done, for a kind that turns to look elsewhere for the next. */
    protected void swept() {
    }

    /** This tick's rays. */
    void sweep(Level level, Lookout look) {
        for (int i = 0; i < RAYS_PER_TICK; i++) {
            cast(level, ray(next, look), look.range());
            next++;
            if (next == rays()) {
                finish();
            }
        }
    }

    private void cast(Level level, Ray ray, double range) {
        BlockHitResult hit = level.clip(new ClipContext(ray.from(), ray.from().add(ray.way().scale(range)), ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.BLOCK && !SableFlight.aboard(level, Vec3.atCenterOf(hit.getBlockPos()))) {
            found.add(new Obstacle(hit.getBlockPos(), hit.getLocation().distanceTo(ray.from())));
        }
    }

    /** A whole sweep done: its obstacles are the ones fired at, nearest first, and the next sweep starts. */
    private void finish() {
        found.sort(Comparator.comparingDouble(Obstacle::distance));
        obstacles = List.copyOf(found);
        found.clear();
        next = 0;
        swept();
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
     * A block vaporised: the clearing spreads every way round it, to the blocks in the airliner's way just past the
     * bite, or under it as far as her tires reach, standing above the floor (a height in the world), as long as there
     * are not too many waiting already.
     */
    void spread(Level level, BlockPos struck, Lookout look, double floor) {
        for (BlockPos step : AROUND) {
            BlockPos to = spreadTo(level, look, struck, step, floor);
            if (to != null && spreading.size() < MOST_SPREAD) {
                spreading.addLast(to);
            }
        }
    }

    /**
     * Where the clearing spreads to one way from a block vaporised: {@link #SPREAD} out, or {@link #SPREAD_NEAR} where
     * that is open, whichever stands in the airliner's way, or under it down to the floor; null for neither.
     */
    @Nullable
    private BlockPos spreadTo(Level level, Lookout look, BlockPos struck, BlockPos step, double floor) {
        for (int reach : SPREAD_REACHES) {
            BlockPos at = struck.offset(step.multiply(reach));
            if (at.getY() + 1 > floor && underOrInWay(look, Vec3.atCenterOf(at)) && !level.getBlockState(at).isAir()) {
                return at;
            }
        }
        return null;
    }

    /** Whether a point lies in the airliner's way, or under it no further than {@link #STUMPS} below. */
    private boolean underOrInWay(Lookout look, Vec3 point) {
        return inWay(look, point) || inWay(look, point.add(look.up().scale(STUMPS)));
    }

    /** Forgets what it found and where the clearing was spreading to, as the laser is disarmed or it stops looking. */
    void forget() {
        spreading.clear();
        found.clear();
        obstacles = List.of();
        next = 0;
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
}
