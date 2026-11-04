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
 * Paintings of the zeppelin's sister ships at the head of a furnished zeppelin's dining room (see {@link SpacePoster}),
 * as the Hindenburg's dining room had pictures on its walls. Her decks have no flat wall of their own, so a panelled
 * one closes the middle of the upper deck's front, from under its floor's edge to over its ceiling's, so that it is
 * held to both face to face and is assembled with her; just aft of it, on the deck, hang the airliner to port and the
 * rocket to starboard, looking down the room, with a clear block of the wall on every side of each, and between them,
 * where the deck is wide enough, the freighter to come after them, as a teaser. It goes in only
 * where the hull is open, the wall and both paintings whole or none of them, so it stays out of anything else laid
 * there.
 */
final class ZeppelinPaintings implements ZeppelinPiece {
    /**
     * How many rows over the deck a painting's lowest row is, in blocks: a row of the wall under it and, the deck being
     * five rows high, one over it.
     */
    private static final int UP = 2;
    /** How far a wall with three paintings reaches out to either side of her middle, and one with two, in blocks. */
    private static final int WIDE_WALL_REACH = 11;
    private static final int NARROW_WALL_REACH = 7;
    /**
     * The walls that may close the deck's front, the first to fit taken: the widest has room for the freighter to come
     * after her sisters, as a teaser, between them; where the deck is narrower only her sisters hang.
     */
    private static final List<Wall> WALLS = List.of(
        new Wall(WIDE_WALL_REACH, List.of(new Hung(SpacePoster.AIRLINER, -8), new Hung(SpacePoster.FREIGHTER, 0), new Hung(SpacePoster.ROCKET, 8))),
        new Wall(NARROW_WALL_REACH, List.of(new Hung(SpacePoster.AIRLINER, -4), new Hung(SpacePoster.ROCKET, 4))));

    /** A wall, how far it reaches out to either side of her middle, in blocks, and the paintings on it. */
    private record Wall(int reach, List<Hung> hung) {
    }

    /** A painting on the wall, and how far its middle column stands out to starboard of her middle, in blocks. */
    private record Hung(SpacePoster painting, int across) {
    }

    private final ZeppelinLayout layout;

    ZeppelinPaintings(ZeppelinLayout layout) {
        this.layout = layout;
    }

    /** Only on a furnished zeppelin, its dining room furnished, and as the server's config has it. */
    @Override
    public boolean wanted(ZeppelinOptions options) {
        return options.furnished() && SkylinersConfig.FLEET_PAINTINGS.get();
    }

    /** At every size, the deck's front being wide enough at each. */
    @Override
    public boolean fits(ZeppelinSize size) {
        return true;
    }

    @Override
    public void build() {
        for (Wall wall : WALLS) {
            if (put(wall)) {
                return;
            }
        }
    }

    /** Puts up a wall and its paintings at the deck's front, whole, if it all fits there. Gives whether it did. */
    private boolean put(Wall wall) {
        int front = layout.deckFront();
        int floor = layout.upperDeckRow();
        List<BlockPos> panels = panels(wall.reach(), front - 1, floor, ceiling(floor, front));
        List<Map<BlockPos, BlockState>> paintings = wall.hung().stream().map(hung -> pieces(hung, front, floor)).toList();
        if (!panels.stream().allMatch(this::open) || !paintings.stream().allMatch(pieces -> pieces.keySet().stream().allMatch(this::open))) {
            return false;
        }
        BlockState panel = SkylinersRegistry.CABIN_PARTITION.get().defaultBlockState();
        panels.forEach(at -> layout.set(at.getX(), at.getY(), at.getZ(), panel));
        paintings.forEach(pieces -> pieces.forEach((at, piece) -> layout.set(at.getX(), at.getY(), at.getZ(), piece)));
        return true;
    }

    /** Every block of a wall at a station along the hull, from the deck's floor row up to its ceiling's. */
    private static List<BlockPos> panels(int reach, int z, int floor, int ceiling) {
        return BlockPos.betweenClosedStream(-reach, floor, z, reach, ceiling, z).map(BlockPos::immutable).toList();
    }

    /** The row of the deck's ceiling over her middle at a station: the first over its floor that is not open. */
    private int ceiling(int floor, int z) {
        int row = floor + 1;
        while (open(new BlockPos(0, row, z))) {
            row++;
        }
        return row;
    }

    /** Where each block of a painting goes on the deck's first row, looking aft down the room. */
    private static Map<BlockPos, BlockState> pieces(Hung hung, int z, int floor) {
        BlockPos middle = new BlockPos(hung.across(), floor + UP + hung.painting().rowsUnderMiddle(), z);
        return SkylinersRegistry.SPACE_POSTER.get().pieces(hung.painting(), middle, Direction.SOUTH);
    }

    /** Whether a block is open air inside the hull. */
    private boolean open(BlockPos at) {
        return layout.isOpen(at.getX(), at.getY(), at.getZ()) && layout.inHull(at.getX(), at.getY(), at.getZ());
    }
}
