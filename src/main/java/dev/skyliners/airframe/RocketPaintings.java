package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.SpacePoster;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Paintings of the rocket's sister ships on her entry story, the first room anyone boarding sees (see
 * {@link SpacePoster}), on the outside of the glass lift shaft: the airliner on her right of it and the zeppelin on her
 * left, just over eye height, a clear row of glass over and under each and nothing else on those faces of the shaft.
 * Each hangs whole or not at all, in open air with the shaft behind it: at a scale where something stands in the way
 * there it hangs in her cargo bay instead, and failing that she goes without. Every block touches the shaft face to
 * face, so it is assembled with her.
 */
final class RocketPaintings implements RocketPiece {
    /** How many rows over the floor a painting's lowest row is, in blocks: a clear row of glass under it. */
    private static final int UP = 3;
    /** The stories a painting may hang on, the first it fits on taken, from the entry story up. */
    private static final List<Integer> STORIES = List.of(RocketEntry.STORY, RocketCargoHold.STORY);
    /** Which painting hangs on which face of the shaft. */
    private static final Map<Direction, SpacePoster> BY_FACE = Map.of(
        Direction.EAST, SpacePoster.AIRLINER,
        Direction.WEST, SpacePoster.ZEPPELIN);

    private final RocketLayout layout;

    RocketPaintings(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted() {
        return SkylinersConfig.FLEET_PAINTINGS.get();
    }

    @Override
    public void build() {
        BY_FACE.forEach(this::hang);
    }

    /** Hangs a painting on a face of the shaft on the first story it fits on and can be seen on, if any. */
    private void hang(Direction face, SpacePoster painting) {
        List<Integer> floors = layout.floors();
        for (int story : STORIES) {
            if (story < floors.size() && hangOver(painting, face, floors.get(story))) {
                return;
            }
        }
    }

    /** Hangs a painting on a face of the shaft over a floor, if it fits there and can be seen. */
    private boolean hangOver(SpacePoster painting, Direction face, int floor) {
        int out = RocketLayout.SHAFT + 1;
        int row = floor + UP + painting.rowsUnderMiddle();
        BlockPos middle = new BlockPos(face.getStepX() * out, row, face.getStepZ() * out);
        Map<BlockPos, BlockState> pieces = SkylinersRegistry.SPACE_POSTER.get().pieces(painting, middle, face);
        boolean fits = RocketPosters.inView(layout, face, row) && pieces.keySet().stream().allMatch(at -> RocketPosters.fits(layout, at, face));
        if (fits) {
            pieces.forEach((at, piece) -> layout.set(at.getX(), at.getY(), at.getZ(), piece));
        }
        return fits;
    }
}
