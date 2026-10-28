package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.SpacePoster;
import dev.aerolarge.block.SpacePosterBlock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The space posters on the outside of the rocket's glass lift shaft (see {@link SpacePosterBlock}), one on a story:
 * Earthrise on the entry story, the solar system in the bunk room, the black hole in the lab and the quasar on the
 * observation deck. Each hangs on the shaft's back if it fits there, else on its right or its left, never on its front,
 * where the doorway is; a story she is too short to have has none, and nor does a story where the poster fits on no
 * face. Every block of a poster hangs against the shaft's glass, face to face, so it is assembled with her and rides
 * along.
 */
final class RocketPosters implements RocketPiece {
    /** How many rows over a story's floor a poster's lowest row is: a row of glass showing under it. */
    private static final int UP = 2;
    /** Which poster hangs on which story, by the story's index from the entry story up. */
    private static final Map<Integer, SpacePoster> BY_STORY = Map.of(
        0, SpacePoster.EARTHRISE,
        3, SpacePoster.SOLAR_SYSTEM,
        7, SpacePoster.BLACK_HOLE,
        8, SpacePoster.QUASAR);
    /** The shaft's faces a poster may hang on, the first it fits on taken: its back, then her right and her left. */
    private static final List<Direction> FACES = List.of(Direction.SOUTH, Direction.EAST, Direction.WEST);

    private final RocketLayout layout;

    RocketPosters(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted() {
        return AerolargeConfig.ROCKET_POSTERS.get();
    }

    @Override
    public void build() {
        List<Integer> floors = layout.floors();
        for (Map.Entry<Integer, SpacePoster> hung : BY_STORY.entrySet()) {
            if (hung.getKey() < floors.size()) {
                hang(hung.getValue(), floors.get(hung.getKey()));
            }
        }
    }

    /** Hangs a poster over a story's floor on the first of the shaft's faces it fits on, if any. */
    private void hang(SpacePoster poster, int floor) {
        for (Direction face : FACES) {
            Map<BlockPos, BlockState> pieces = pieces(poster, floor, face);
            if (pieces != null) {
                pieces.forEach((at, piece) -> layout.set(at.getX(), at.getY(), at.getZ(), piece));
                return;
            }
        }
    }

    /**
     * Where each block of a poster goes on a face of the shaft over a story's floor, looking out that way, and which
     * piece of it each is: just outside the glass, its middle column level with her axis, its lowest row so many over
     * the floor; null if any of them would not be in open air inside her with the glass behind it, as where the next
     * floor or something else stands.
     */
    private Map<BlockPos, BlockState> pieces(SpacePoster poster, int floor, Direction face) {
        Direction right = SpacePosterBlock.right(face);
        int out = RocketLayout.SHAFT + 1;
        int top = floor + UP + poster.rows() - 1;
        Map<BlockPos, BlockState> pieces = new HashMap<>();
        for (int piece = 0; piece < poster.columns() * poster.rows(); piece++) {
            int column = piece % poster.columns();
            int row = piece / poster.columns();
            int along = column - poster.columns() / 2;
            BlockPos at = new BlockPos(face.getStepX() * out + right.getStepX() * along, top - row, face.getStepZ() * out + right.getStepZ() * along);
            if (!fits(at, face)) {
                return null;
            }
            pieces.put(at, AerolargeRegistry.SPACE_POSTER.get().piece(poster, face, column, row));
        }
        return pieces;
    }

    /** Whether a block of a poster looking out one way can hang at a position: open air inside her, glass behind it. */
    private boolean fits(BlockPos at, Direction face) {
        BlockPos behind = at.relative(face.getOpposite());
        return layout.isOpen(at.getX(), at.getY(), at.getZ()) && layout.inside(at.getX(), at.getY(), at.getZ())
            && !layout.isOpen(behind.getX(), behind.getY(), behind.getZ());
    }
}
