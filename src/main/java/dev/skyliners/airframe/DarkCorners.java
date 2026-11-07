package dev.skyliners.airframe;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.LightTone;
import dev.skyliners.block.OvalWindowPieceBlock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The last pass over a ship's layout, so no corner of her is dark enough inside for monsters to spawn in, wherever her
 * rooms' own lights leave it. The light of every lamp of hers is spread through the air inside her hull a block at a
 * time, a level less each block, and through her see-through blocks as far as they let it, as the game spreads it.
 * Then every block inside her that someone could stand in, a floor under it and room for their head over it, lit less
 * than the ship asks for there and in any case unlit, gets a wall washer on a wall beside it at head height, or a floor
 * light where there is no wall, and the light is spread from that in turn, until none is left. A ship can keep her
 * rooms' own moods, dim or bright, and ask more of her passages, so none of them looks black. Laid out in the ship's
 * own frame, as everything of her is.
 */
final class DarkCorners {
    /** The least light a block someone could stand in is left with anywhere, in light levels: none spawns monsters. */
    static final int UNLIT = 1;
    /** All the light there is, which a solid block keeps out, in light levels. */
    private static final int FULL = 15;

    private final HullLayout layout;
    private final HullLayout.Box box;
    private final int width;
    private final int height;
    private final boolean[] inside;
    private final byte[] light;
    /** Every block of the air inside the hull, found once. */
    private final List<BlockPos> interior = new ArrayList<>();

    private DarkCorners(HullLayout layout) {
        this.layout = layout;
        this.box = layout.airBox();
        this.width = box.maxX() - box.minX() + 1;
        this.height = box.maxY() - box.minY() + 1;
        int depth = box.maxZ() - box.minZ() + 1;
        this.inside = new boolean[width * height * depth];
        this.light = new byte[inside.length];
    }

    /**
     * Lights a ship's dark corners, in the tone given, each block someone could stand in up to the least light the
     * ship asks for there, at least {@link #UNLIT}; gives how many lights it added.
     */
    static int light(HullLayout layout, LightTone tone, ToIntFunction<BlockPos> least) {
        DarkCorners pass = new DarkCorners(layout);
        layout.forEachInterior(pass.interior::add);
        pass.interior.forEach(at -> pass.inside[pass.index(at)] = true);
        pass.spreadFromLamps();
        int added = 0;
        for (BlockPos dark : pass.darkSpots(least)) {
            if (pass.lightAt(dark) < Math.max(UNLIT, least.applyAsInt(dark)) && pass.lightUp(dark, tone)) {
                added++;
            }
        }
        return added;
    }

    /** Spreads the light of every lamp the ship has. */
    private void spreadFromLamps() {
        ArrayDeque<BlockPos> lit = new ArrayDeque<>();
        layout.cells.forEach((at, state) -> seed(lit, at, state.getLightEmission()));
        spread(lit);
    }

    /** A lamp's light into the air round it, a level less than its own. */
    private void seed(ArrayDeque<BlockPos> lit, BlockPos lamp, int emission) {
        if (emission <= 1) {
            return;
        }
        for (Direction side : Direction.values()) {
            raise(lit, lamp.relative(side), emission - 1);
        }
    }

    /**
     * Raises the light at a block to so much less what the block keeps out, if it is less, to spread on from there:
     * the air inside the hull keeps none out, a see-through block of hers as much as the game says it does, and her
     * solid blocks all of it.
     */
    private void raise(ArrayDeque<BlockPos> lit, BlockPos at, int level) {
        if (!holds(at)) {
            return;
        }
        int through = level - keptOut(at);
        if (through <= 0 || light[index(at)] >= through) {
            return;
        }
        light[index(at)] = (byte) through;
        lit.add(at);
    }

    /**
     * How much light a block keeps out of what reaches it, besides the level it loses on its way, as the game says of
     * the block whole: a slab or a stair is taken as open, which errs on the bright side, the play-test measuring the
     * light the game itself gives.
     */
    private int keptOut(BlockPos at) {
        if (inside[index(at)]) {
            return 0;
        }
        BlockState state = layout.stateAt(at.getX(), at.getY(), at.getZ());
        return state.isAir() ? FULL : state.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    /** Spreads light on from the blocks given, a level less each block, through the air inside the hull. */
    private void spread(ArrayDeque<BlockPos> lit) {
        while (!lit.isEmpty()) {
            BlockPos at = lit.poll();
            int level = light[index(at)] - 1;
            if (level <= 0) {
                continue;
            }
            for (Direction side : Direction.values()) {
                raise(lit, at.relative(side), level);
            }
        }
    }

    /**
     * Every block inside the hull someone could stand in, lit less than the least asked for there: a floor under it
     * they could stand on and room for their head over it; from the lowest up, so a dark spot lit lights the ones round
     * it before they are looked at.
     */
    private List<BlockPos> darkSpots(ToIntFunction<BlockPos> least) {
        List<BlockPos> dark = new ArrayList<>();
        interior.forEach(at -> addIfDark(dark, at, Math.max(UNLIT, least.applyAsInt(at))));
        dark.sort(Comparator.comparingInt((BlockPos at) -> at.getY()).thenComparingInt(at -> at.getX())
            .thenComparingInt(at -> at.getZ()));
        return dark;
    }

    private void addIfDark(List<BlockPos> dark, BlockPos at, int least) {
        if (lightAt(at) < least && standable(at)) {
            dark.add(at);
        }
    }

    /**
     * Whether someone could stand in a block: the air inside the hull, a floor under it that holds them as the game
     * judges it for a monster spawning, and air over it.
     */
    private boolean standable(BlockPos at) {
        BlockPos above = at.above();
        if (!holds(above) || !inside[index(above)]) {
            return false;
        }
        BlockState floor = layout.stateAt(at.getX(), at.getY() - 1, at.getZ());
        return !floor.isAir() && floor.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP);
    }

    /**
     * Lights a dark spot: a wall washer at head height on a wall beside it, shining away from the wall, or a floor
     * light in the spot itself where it has no wall; gives whether it found room for one.
     */
    private boolean lightUp(BlockPos dark, LightTone tone) {
        BlockPos head = dark.above();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (layout.isOpen(head.getX(), head.getY(), head.getZ()) && solidWall(head.relative(side), side.getOpposite())) {
                return place(head, SkylinersRegistry.WALL_LIGHT.get().shining(side.getOpposite(), tone));
            }
        }
        boolean open = layout.isOpen(dark.getX(), dark.getY(), dark.getZ());
        return open && place(dark, SkylinersRegistry.FLOOR_LIGHT.get().defaultBlockState());
    }

    /**
     * Whether a block is a wall a lamp can be fixed to, its face whole on the side looking the way given; never a
     * window (see {@link OvalWindowPieceBlock}), whose glass the lamp would cover.
     */
    private boolean solidWall(BlockPos at, Direction face) {
        BlockState wall = layout.stateAt(at.getX(), at.getY(), at.getZ());
        boolean window = wall.getBlock() instanceof OvalWindowPieceBlock;
        return !wall.isAir() && !window && wall.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, face);
    }

    /** Sets a light in a block and spreads its light from there. */
    private boolean place(BlockPos at, BlockState lamp) {
        layout.set(at.getX(), at.getY(), at.getZ(), lamp);
        inside[index(at)] = false;
        ArrayDeque<BlockPos> lit = new ArrayDeque<>();
        seed(lit, at, lamp.getLightEmission());
        spread(lit);
        return true;
    }

    /** How lit a block of the air inside the hull is; none outside it. */
    private int lightAt(BlockPos at) {
        return holds(at) ? light[index(at)] : 0;
    }

    /** Whether a block lies in the box the hull's air is looked for in, which the pass keeps its light for. */
    private boolean holds(BlockPos at) {
        return at.getX() >= box.minX() && at.getX() <= box.maxX() && at.getY() >= box.minY() && at.getY() <= box.maxY()
            && at.getZ() >= box.minZ() && at.getZ() <= box.maxZ();
    }

    /** Where a block's light is kept, for a block the box holds (see {@link #holds}). */
    private int index(BlockPos at) {
        return ((at.getZ() - box.minZ()) * height + (at.getY() - box.minY())) * width + (at.getX() - box.minX());
    }
}
