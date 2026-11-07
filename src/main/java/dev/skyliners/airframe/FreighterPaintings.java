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
 * Paintings of the freighter's sister ships round her deck (see {@link SpacePoster}), as each of them has paintings of
 * the others: the airliner in her entry hall, the first room anyone coming up her ramp sees, the zeppelin in her club
 * past the end of its bar, and the rocket over the bunks in her crew quarters. Her only flat walls are the two straight
 * across her beam, along z 0 in her layout nose north, between her entry hall and her lounge to starboard and between
 * her club and her crew quarters to port, so each hangs on a face of one of those, a row over her deck.
 *
 * <p>Each hangs whole or not at all, at the first place along its wall where every block of it is open with the wall
 * behind it and the room open before it, so it stays out of whatever stands there; every block touches the wall face
 * to face, so it is assembled with her. None with the server config's {@code fleetPaintings} off.
 */
final class FreighterPaintings {
    /** How many rows over her deck a painting's lowest row is, in blocks: a row of the wall under it. */
    private static final int UP = 2;
    /** How many blocks before a painting's middle must be open for it to be seen. */
    private static final int IN_VIEW = 2;
    /**
     * Which painting hangs on which face of the wall along z 0, the way it looks out, and how far out along the wall
     * its middle column may stand, the first that fits taken: to starboard (east) for her entry hall, to port (west)
     * for her club and her crew quarters.
     */
    private static final List<Hung> HUNG = List.of(
        new Hung(SpacePoster.AIRLINER, Direction.NORTH, List.of(13, 16, 10, 19)),
        new Hung(SpacePoster.ZEPPELIN, Direction.NORTH, List.of(-18, -19, -17, -5)),
        new Hung(SpacePoster.ROCKET, Direction.SOUTH, List.of(-13, -16, -10, -19)));

    /** A painting, the way it looks out from the wall, and the columns its middle may stand at, in turn. */
    private record Hung(SpacePoster painting, Direction face, List<Integer> middles) {
    }

    private final HullLayout layout;
    private final int deck;

    private FreighterPaintings(HullLayout layout, int deck) {
        this.layout = layout;
        this.deck = deck;
    }

    /** Hangs the paintings in a freighter's layout whose deck's floor is the row given, each where it fits. */
    static void build(HullLayout layout, int deck) {
        if (SkylinersConfig.FLEET_PAINTINGS.get()) {
            FreighterPaintings paintings = new FreighterPaintings(layout, deck);
            HUNG.forEach(paintings::hang);
        }
    }

    /** Hangs a painting at the first of its places that it fits, if any. */
    private void hang(Hung hung) {
        for (int x : hung.middles()) {
            if (hangAt(hung.painting(), hung.face(), x)) {
                return;
            }
        }
    }

    /** Hangs a painting with its middle column at a column along the wall, if it fits there and can be seen. */
    private boolean hangAt(SpacePoster painting, Direction face, int x) {
        int row = deck + UP + painting.rowsUnderMiddle();
        BlockPos middle = new BlockPos(x, row, face.getStepZ());
        Map<BlockPos, BlockState> pieces = SkylinersRegistry.SPACE_POSTER.get().pieces(painting, middle, face);
        boolean fits = inView(middle, face) && pieces.keySet().stream().allMatch(at -> onWall(at, face));
        if (fits) {
            pieces.forEach((at, piece) -> layout.set(at.getX(), at.getY(), at.getZ(), piece));
        }
        return fits;
    }

    /** Whether the room is open before a painting's middle, so it can be seen. */
    private boolean inView(BlockPos middle, Direction face) {
        for (int out = 1; out <= IN_VIEW; out++) {
            BlockPos before = middle.relative(face, out);
            if (!layout.isOpen(before.getX(), before.getY(), before.getZ())) {
                return false;
            }
        }
        return true;
    }

    /** Whether a block of a painting looking out one way can hang at a position: open, with the wall behind it. */
    private boolean onWall(BlockPos at, Direction face) {
        BlockPos behind = at.relative(face.getOpposite());
        return layout.isOpen(at.getX(), at.getY(), at.getZ()) && !layout.isOpen(behind.getX(), behind.getY(), behind.getZ());
    }
}
