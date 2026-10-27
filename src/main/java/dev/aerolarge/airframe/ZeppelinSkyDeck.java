package dev.aerolarge.airframe;

import static dev.aerolarge.airframe.ZeppelinShape.*;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.CabinRailBlock;
import dev.aerolarge.block.ClimbingRopeBlock;
import dev.aerolarge.block.NavLightBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Sky Shaft and the Sky Deck: a climbing rope up from the gangway, beside it where a ring stands between two gas
 * cells' rounded ends, inside a cage of girders all the way up past the glowing cells, out through a hatch in the
 * crown onto a railed deck on top of the zeppelin, with a lamp at each corner and a mast whose red beacon blinks over
 * it. Standing on top of a flying airship is the point. The rope hangs from a gantry over the hatch, so it can be
 * climbed until the deck is underfoot, and stepped off fore or aft. A rope can be fallen down safely, as a ladder can:
 * climbing slows the fall, and nothing is hurt landing in it; nothing bumps into it either, so the shaft is wide
 * enough to climb. All of it is this mod's own blocks, joined face to face from the gangway to the deck, so it is
 * assembled with the zeppelin.
 */
final class ZeppelinSkyDeck {
    /** Which ring the shaft climbs at: the one just aft of the hull's middle, aft of the decks. */
    private static final int SHAFT_RING = 9;
    /** How far out from the middle the rope hangs, in blocks: beside the gangway, past its rail. */
    private static final int ROPE_OUT = 2;
    /** How far above the gangway's walkway the cage starts on the gangway's side, in blocks: clear of heads. */
    private static final int CAGE_FROM = 3;
    /** How far the deck reaches either way from the middle and from the shaft's ring, in blocks. */
    private static final int DECK_HALF = 3;
    /** How high over the deck the gantry's beam is, in blocks, the rope hanging from it: over a climber's head. */
    private static final int GANTRY_HEIGHT = 4;
    /** How tall the mast stands over the deck under its beacon, in blocks, and how far in from the deck's corner. */
    private static final int MAST_HEIGHT = 3;
    private static final int MAST_IN = 1;

    private final ZeppelinLayout layout;

    ZeppelinSkyDeck(ZeppelinLayout layout) {
        this.layout = layout;
    }

    /** Lays the shaft and the deck, once the gangway it starts from is laid. */
    void build() {
        int z = (int) Math.floor(ringAt(SHAFT_RING) * SCALE);
        int walkway = layout.gangwayRow();
        int deck = deckRow(z, walkway);
        BlockState catwalk = AerolargeRegistry.ZEPPELIN_CATWALK.get().defaultBlockState();
        // The way off the gangway: its rail opened, and the floor under the rope's foot and the cage.
        layout.clear(ROPE_OUT - 1, walkway + 1, z);
        layout.set(ROPE_OUT, walkway, z, catwalk);
        layout.set(ROPE_OUT + 1, walkway, z, catwalk);
        deckFloor(z, deck, catwalk);
        for (int y = walkway + 1; y < deck; y++) {
            cageRow(y, z, walkway);
        }
        gantry(z, deck);
        int top = deck + GANTRY_HEIGHT - 1;
        for (int y = walkway + 1; y <= top; y++) {
            layout.set(ROPE_OUT, y, z, AerolargeRegistry.CLIMBING_ROPE.get().defaultBlockState().setValue(ClimbingRopeBlock.UP, y < top)
                .setValue(ClimbingRopeBlock.DOWN, y > walkway + 1));
        }
        mast(z, deck);
    }

    /** The row the deck is laid in: the first open air above the crown over the gangway. */
    private int deckRow(int z, int walkway) {
        int y = walkway + 1;
        while (!layout.isOpen(0, y, z) || layout.inHull(0, y, z)) {
            y++;
        }
        return y;
    }

    /** A row of the cage round the rope, open on the gangway's side up to over a head. */
    private void cageRow(int y, int z, int walkway) {
        for (int x = ROPE_OUT - 1; x <= ROPE_OUT + 1; x++) {
            cage(x, y, z - 1);
            cage(x, y, z + 1);
        }
        cage(ROPE_OUT + 1, y, z);
        if (y >= walkway + CAGE_FROM) {
            cage(ROPE_OUT - 1, y, z);
        }
    }

    /** A girder of the cage, where nothing else, the cover or the gangway's rail, stands already. */
    private void cage(int x, int y, int z) {
        if (layout.isOpen(x, y, z) && layout.inHull(x, y, z)) {
            layout.set(x, y, z, AerolargeRegistry.ZEPPELIN_GIRDER.get().defaultBlockState());
        }
    }

    /** The deck's grating on the crown, a lamp let into each corner, and its rail round the edge. */
    private void deckFloor(int z, int deck, BlockState catwalk) {
        BlockState lamp = AerolargeRegistry.CABIN_LIGHT.get().defaultBlockState();
        for (int x = -DECK_HALF; x <= DECK_HALF; x++) {
            for (int along = z - DECK_HALF; along <= z + DECK_HALF; along++) {
                boolean corner = Math.abs(x) == DECK_HALF && Math.abs(along - z) == DECK_HALF;
                layout.set(x, deck, along, corner ? lamp : catwalk);
            }
        }
        for (int at = -DECK_HALF; at <= DECK_HALF; at++) {
            rail(-DECK_HALF, deck, z + at, Direction.WEST);
            rail(DECK_HALF, deck, z + at, Direction.EAST);
            rail(at, deck, z - DECK_HALF, Direction.NORTH);
            rail(at, deck, z + DECK_HALF, Direction.SOUTH);
        }
    }

    private void rail(int x, int y, int z, Direction edge) {
        BlockState rail = AerolargeRegistry.CABIN_RAIL.get().defaultBlockState();
        layout.set(x, y + 1, z, CabinRailBlock.along(layout.stateAt(x, y + 1, z), edge, layout.stateAt(x, y, z), rail));
    }

    /** The gantry over the hatch: a post either side of it and the beam across them, which the rope hangs from. */
    private void gantry(int z, int deck) {
        BlockState girder = AerolargeRegistry.ZEPPELIN_GIRDER.get().defaultBlockState();
        for (int y = deck + 1; y < deck + GANTRY_HEIGHT; y++) {
            layout.set(ROPE_OUT - 1, y, z, girder);
            layout.set(ROPE_OUT + 1, y, z, girder);
        }
        for (int x = ROPE_OUT - 1; x <= ROPE_OUT + 1; x++) {
            layout.set(x, deck + GANTRY_HEIGHT, z, girder);
        }
    }

    /** The mast at the deck's forward port corner, a red beacon blinking at its top. */
    private void mast(int z, int deck) {
        int x = -DECK_HALF + MAST_IN;
        int along = z - DECK_HALF + MAST_IN;
        for (int y = deck + 1; y <= deck + MAST_HEIGHT; y++) {
            layout.set(x, y, along, AerolargeRegistry.ZEPPELIN_GIRDER.get().defaultBlockState());
        }
        layout.set(x, deck + MAST_HEIGHT + 1, along, AerolargeRegistry.NAV_LIGHT.get().defaultBlockState()
            .setValue(NavLightBlock.FACING, Direction.UP).setValue(NavLightBlock.KIND, NavLightBlock.Kind.BEACON));
    }
}
