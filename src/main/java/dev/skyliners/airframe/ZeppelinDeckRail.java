package dev.skyliners.airframe;

import net.minecraft.core.Direction;

/**
 * A glass rail along the front edge of the zeppelin's upper deck, wherever its floor ends there over the hull's open
 * inside (see {@link ZeppelinLayout#railAlong}): the deck has no front wall of its own, the panelled wall with the
 * fleet's paintings closing only its middle, and a player walking its promenades stepped off its front corners and fell
 * four blocks into the dark among her girders. Laid after everything else, so it stands only where nothing else does.
 */
final class ZeppelinDeckRail implements ZeppelinPiece {
    private final ZeppelinLayout layout;

    ZeppelinDeckRail(ZeppelinLayout layout) {
        this.layout = layout;
    }

    /** At every size, as her decks and the paintings' wall are laid at each. */
    @Override
    public boolean fits(ZeppelinSize size) {
        return true;
    }

    @Override
    public void build() {
        int floor = layout.upperDeckRow();
        int front = layout.deckFront();
        int reach = (int) Math.ceil(layout.radiusAt(front));
        for (int x = -reach; x <= reach; x++) {
            if (overDrop(x, floor, front)) {
                layout.railAlong(x, floor, front, Direction.NORTH);
            }
        }
    }

    /**
     * Whether a block of the deck's front row has its floor under it and open room over it, inside the hull, and in
     * front of it nothing but the drop: no floor and no wall.
     */
    private boolean overDrop(int x, int floor, int front) {
        boolean standing = !layout.isOpen(x, floor, front) && layout.isOpen(x, floor + 1, front) && layout.inHull(x, floor + 1, front);
        return standing && layout.isOpen(x, floor, front - 1) && layout.isOpen(x, floor + 1, front - 1);
    }
}
