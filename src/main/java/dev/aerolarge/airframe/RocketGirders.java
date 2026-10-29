package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Steel girders under her rooms' ceilings (see {@code AerolargeRegistry.STEEL_GIRDER}), so her rooms show the steel
 * they are built round, as the girders up the corners of her lift shaft do: in every furnished story but the
 * observation deck, under whose starry dome nothing hangs, one out from the shaft along each of the four ways to her
 * skin, in the story's highest row. A girder is laid only where that row is open all the way, short of the block by
 * her skin, which the LED strip along the ceiling takes, so it never ends in the middle of a room at a lamp's cable.
 * Laid once the rooms have their furniture, and before the wall washers, the posters and the LED strips.
 */
final class RocketGirders implements RocketPiece {
    /** The stories with nothing hanging from their ceilings: the observation deck. */
    private static final List<Integer> BARE = List.of(RocketObservationDeck.STORY);
    /** How few blocks a girder runs at the least, from the shaft out, in blocks: shorter, it is left out. */
    private static final int SHORTEST = 2;

    private final RocketLayout layout;

    RocketGirders(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted() {
        return layout.rooms();
    }

    @Override
    public void build() {
        for (int story = 0; story < layout.floors().size(); story++) {
            if (!BARE.contains(story)) {
                ceiling(layout.storyTop(story));
            }
        }
    }

    /** A story's girders, under its ceiling, out along each of the four ways. */
    private void ceiling(int y) {
        for (Direction way : Direction.Plane.HORIZONTAL) {
            List<BlockPos> run = run(way, y);
            BlockState girder = AerolargeRegistry.STEEL_GIRDER.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, way.getAxis());
            if (run.size() >= SHORTEST) {
                run.forEach(at -> layout.set(at.getX(), at.getY(), at.getZ(), girder));
            }
        }
    }

    /**
     * The blocks a girder takes along a way out from the shaft, in a row: from the shaft's wall out to the last before
     * the one by her skin; none where something stands in the way before then.
     */
    private List<BlockPos> run(Direction way, int y) {
        List<BlockPos> run = new ArrayList<>();
        BlockPos at = new BlockPos(0, y, 0).relative(way, RocketLayout.SHAFT + 1);
        while (layout.isOpen(at.getX(), at.getY(), at.getZ())) {
            BlockPos next = at.relative(way);
            if (!layout.isOpen(next.getX(), next.getY(), next.getZ())) {
                return byTheSkin(next) ? run : List.of();
            }
            run.add(at);
            at = next;
        }
        return List.of();
    }

    /** Whether a block is her skin, where a girder's run ends: her cover, a window or the glass round her cockpit. */
    private boolean byTheSkin(BlockPos at) {
        return !layout.inFrom(at.getX(), at.getY(), at.getZ(), 0);
    }
}
