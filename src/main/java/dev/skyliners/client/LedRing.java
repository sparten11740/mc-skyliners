package dev.skyliners.client;

import dev.skyliners.block.LedPanelBlockEntity;
import dev.skyliners.block.LedStripBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A story's LED strips as its panel's renderer draws over them (see {@link LedPanelRenderer}): where each strip is,
 * from the panel, the sides it runs along, the walls among them flat enough to glow on, and how far round its ring it
 * is, in blocks, for the chase's bands. Found by looking along the two rows of the story's box the strips run along,
 * and looked over again every few seconds while it is drawn, so a strip broken or put back is soon seen; never more
 * than those two rows.
 */
final class LedRing {
    /**
     * A strip: where it is from the panel, the sides it runs along and those whose wall is flat to glow on, each a bit
     * by its horizontal direction's 2D value, whether it is under the ceiling, and how far round the ring it is.
     */
    record Strip(BlockPos at, int walls, int glows, boolean ceiling, double along) {
        boolean runsAlong(Direction side) {
            return (walls & 1 << side.get2DDataValue()) != 0;
        }

        boolean glowsOn(Direction side) {
            return (glows & 1 << side.get2DDataValue()) != 0;
        }
    }

    /** How long a ring is drawn as it was found before it is looked over again, in ticks. */
    private static final int RESCAN_TICKS = 100;
    /** Each panel's ring as last found, dropped with the panel. Only the render thread reaches it. */
    private static final Map<LedPanelBlockEntity, LedRing> RINGS = new WeakHashMap<>();

    private final BoundingBox box;
    private final long found;
    private final List<Strip> strips;

    private LedRing(BoundingBox box, long found, List<Strip> strips) {
        this.box = box;
        this.found = found;
        this.strips = strips;
    }

    List<Strip> strips() {
        return strips;
    }

    /** A panel's story's strips, as last found, or found again if that was a while ago or its box has changed. */
    static LedRing of(LedPanelBlockEntity panel, Level level, BoundingBox box) {
        LedRing ring = RINGS.get(panel);
        long now = level.getGameTime();
        if (ring == null || !ring.box.equals(box) || now - ring.found >= RESCAN_TICKS || now < ring.found) {
            ring = find(panel.getBlockPos(), level, box, panel.wedge(), now);
            RINGS.put(panel, ring);
        }
        return ring;
    }

    /**
     * The strips along the box's bottom row and its top one, in its wedge for a room shaped as one, each placed round
     * the ring by its angle round the ship's axis (the box's middle for the rocket's stories, the wedge's axis for the
     * freighter's rooms), at the ring's length: so many whole bands and gaps round it that they meet up.
     */
    private static LedRing find(BlockPos panel, Level level, BoundingBox box, @Nullable LedPanelBlockEntity.Wedge wedge, long now) {
        List<BlockPos> found = new ArrayList<>();
        row(level, box, box.minY(), wedge, found);
        if (box.maxY() != box.minY()) {
            row(level, box, box.maxY(), wedge, found);
        }
        double middleX = wedge != null ? wedge.axis().getX() + 0.5 : (box.minX() + box.maxX() + 1) / 2.0;
        double middleZ = wedge != null ? wedge.axis().getZ() + 0.5 : (box.minZ() + box.maxZ() + 1) / 2.0;
        double radius = found.stream().mapToDouble(at -> Math.hypot(at.getX() + 0.5 - middleX, at.getZ() + 0.5 - middleZ)).average().orElse(1);
        int period = 2 * LedLights.CHASE_BAND;
        double length = Math.max(1, Math.round(2 * Math.PI * radius / period)) * period;
        List<Strip> strips = new ArrayList<>();
        for (BlockPos at : found) {
            double angle = Math.atan2(at.getZ() + 0.5 - middleZ, at.getX() + 0.5 - middleX) + Math.PI;
            strips.add(strip(level, at, panel, angle / (2 * Math.PI) * length));
        }
        return new LedRing(box, now, List.copyOf(strips));
    }

    /** The strips along one row of the box, in its wedge if it has one, added to the list. */
    private static void row(Level level, BoundingBox box, int y, @Nullable LedPanelBlockEntity.Wedge wedge, List<BlockPos> found) {
        for (BlockPos at : BlockPos.betweenClosed(box.minX(), y, box.minZ(), box.maxX(), y, box.maxZ())) {
            boolean ours = wedge == null || wedge.contains(at);
            if (ours && level.getBlockState(at).getBlock() instanceof LedStripBlock) {
                found.add(at.immutable());
            }
        }
    }

    /** A strip as it stands, so far round its ring. */
    private static Strip strip(Level level, BlockPos at, BlockPos panel, double along) {
        BlockState state = level.getBlockState(at);
        int walls = 0;
        int glows = 0;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (LedStripBlock.runsAlong(state, side)) {
                walls |= 1 << side.get2DDataValue();
                BlockPos wall = at.relative(side);
                glows |= level.getBlockState(wall).isFaceSturdy(level, wall, side.getOpposite()) ? 1 << side.get2DDataValue() : 0;
            }
        }
        return new Strip(at.subtract(panel), walls, glows, state.getValue(LedStripBlock.CEILING), along);
    }
}
