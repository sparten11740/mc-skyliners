package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.ClimbingRopeBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The climbing ropes up from the zeppelin's gangway, to the Sky Deck (see {@link ZeppelinSkyDeck}) and into a gas cell
 * (see {@link ZeppelinCellGallery}): each hung beside the gangway, past a gap in its rail, from a beam over its top,
 * in a cage of girders open only toward the gangway up to over a head, so nobody steps through the rope, which nothing
 * bumps into, and off into the hull's belly, and a climber always has a wall to push against.
 */
final class ZeppelinRopes {
    /** How far above the gangway's walkway the cage closes on the gangway's side, in blocks: clear of heads. */
    private static final int CAGE_FROM = 3;

    private ZeppelinRopes() {
    }

    /** The way off the gangway to a rope at x: its rail opened, and the floor under the rope's foot and the cage. */
    static void foot(ZeppelinLayout layout, int x, int z) {
        BlockState catwalk = AerolargeRegistry.ZEPPELIN_CATWALK.get().defaultBlockState();
        int walkway = layout.gangwayRow();
        layout.clear(x - 1, walkway + 1, z);
        layout.set(x, walkway, z, catwalk);
        layout.set(x + 1, walkway, z, catwalk);
    }

    /** A row of the cage round a rope at x: fore, aft and outboard of it, and on the gangway's side over a head. */
    static void cageRow(ZeppelinLayout layout, int x, int y, int z) {
        for (int across = x; across <= x + 1; across++) {
            cage(layout, across, y, z - 1);
            cage(layout, across, y, z + 1);
        }
        cage(layout, x + 1, y, z);
        if (y >= layout.gangwayRow() + CAGE_FROM) {
            cage(layout, x - 1, y, z);
        }
    }

    /** A girder of the cage, where nothing else, the cover or a rail, stands already. */
    private static void cage(ZeppelinLayout layout, int x, int y, int z) {
        if (layout.isOpen(x, y, z) && layout.inHull(x, y, z)) {
            layout.set(x, y, z, AerolargeRegistry.ZEPPELIN_GIRDER.get().defaultBlockState());
        }
    }

    /** The rope itself, from over the gangway's walkway up to a top piece hanging from whatever is over it. */
    static void rope(ZeppelinLayout layout, int x, int z, int top) {
        int foot = layout.gangwayRow() + 1;
        for (int y = foot; y <= top; y++) {
            layout.set(x, y, z, AerolargeRegistry.CLIMBING_ROPE.get().defaultBlockState().setValue(ClimbingRopeBlock.UP, y < top)
                .setValue(ClimbingRopeBlock.DOWN, y > foot));
        }
    }
}
