package dev.aerolarge.airframe;

import static dev.aerolarge.airframe.ZeppelinShape.*;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.CarrierLeverBlock;
import dev.aerolarge.block.ShellBlock;
import dev.aerolarge.block.ShellGeometry;
import dev.aerolarge.block.NavLightBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The flying carrier's flight deck: a flat strip laid along the crown forward of the Sky Deck, where the hull runs at
 * its full width and so its crown level, a dark non-slip deck with white lines along its edges and a dashed line down
 * its middle, a bulb at each corner, four docking clamps set into it either side of the middle line (see
 * {@link dev.aerolarge.block.DeckClampBlock}) and the release lever at its forward port corner. A plane lands on it
 * along her, over her bow, and is locked down and refuelled. The same size at every size, as the planes are; laid over
 * the crown's line of bulbs where it runs, on the crown's top row, so it is assembled with her. The
 * {@code flyingCarrier} switch, off until switched on, leaves it off a zeppelin built from then on.
 */
final class ZeppelinFlightDeck implements ZeppelinPiece {
    /**
     * How far either side of the middle the deck reaches, how long it is, and how far forward of the Sky Deck it ends,
     * in blocks.
     */
    private static final int HALF_WIDTH = 7;
    private static final int DECK_LENGTH = 60;
    private static final int GAP = 4;
    /** How far either side of the middle the clamps are, and how far fore and aft of the deck's middle, in blocks. */
    private static final int CLAMP_OUT = 3;
    private static final int CLAMP_ALONG = 9;
    /** How many rows the middle line's dashes and their gaps are long. */
    private static final int DASH = 2;
    /** How high over the crown the deck is looked for, in blocks: past the bulbs along it. */
    private static final int LOOK_FROM = 3;

    private final ZeppelinLayout layout;

    ZeppelinFlightDeck(ZeppelinLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return AerolargeConfig.FLYING_CARRIER.get();
    }

    /** Along the crown, wherever it runs level at the size. */
    @Override
    public boolean fits(ZeppelinSize size) {
        return true;
    }

    @Override
    public void build() {
        int aft = Math.min(ZeppelinSkyDeck.front(layout) - GAP, (int) Math.floor(TAPER * LENGTH * layout.scale()));
        int front = aft - DECK_LENGTH + 1;
        int y = deckRow(front, aft);
        for (int z = front; z <= aft; z++) {
            clearBulb(z);
            for (int x = -HALF_WIDTH; x <= HALF_WIDTH; x++) {
                plinth(x, y, z);
                layout.set(x, y, z, deckAt(x, z, front).defaultBlockState());
            }
        }
        int middle = (front + aft) / 2;
        for (int side = -1; side <= 1; side += 2) {
            layout.set(side * CLAMP_OUT, y, middle - CLAMP_ALONG, AerolargeRegistry.DECK_CLAMP.get().defaultBlockState());
            layout.set(side * CLAMP_OUT, y, middle + CLAMP_ALONG, AerolargeRegistry.DECK_CLAMP.get().defaultBlockState());
            bulb(side * HALF_WIDTH, y + 1, front);
            bulb(side * HALF_WIDTH, y + 1, aft);
        }
        layout.set(-HALF_WIDTH, y + 1, front + 1, AerolargeRegistry.CARRIER_LEVER.get().defaultBlockState().setValue(CarrierLeverBlock.FACING, Direction.NORTH));
    }

    /** The row the deck is laid in: one over the crown's top, at its highest along the deck's length. */
    private int deckRow(int front, int aft) {
        int highest = layout.axis();
        for (int z = front; z <= aft; z++) {
            highest = Math.max(highest, crownTop(z));
        }
        return highest + 1;
    }

    /** The crown's top at a row, the cover or fabric, under any bulb on it. */
    private int crownTop(int z) {
        return crownTop(0, z);
    }

    /** The crown's top at a column, the cover or fabric, under any bulb on it. */
    private int crownTop(int x, int z) {
        int top = layout.axis() + (int) Math.ceil(RADIUS * layout.scale()) + LOOK_FROM;
        while (top > layout.axis() && !crown(layout.stateAt(x, top, z))) {
            top--;
        }
        return top;
    }

    /**
     * Cover laid up from the crown to under the deck at a column, where the crown curves away under the deck's edge,
     * so the deck stands on her rather than over a gap.
     */
    private void plinth(int x, int deck, int z) {
        BlockState cover = ShellBlock.withShape(AerolargeRegistry.ZEPPELIN_COVER.get().defaultBlockState(), ShellGeometry.WHOLE);
        for (int y = crownTop(x, z) + 1; y < deck; y++) {
            layout.set(x, y, z, cover);
        }
    }

    /** The crown's bulb at a row, if one stands there, taken away: the deck is laid over the line of bulbs. */
    private void clearBulb(int z) {
        int over = crownTop(z) + 1;
        if (layout.stateAt(0, over, z).is(AerolargeRegistry.NAV_LIGHT.get())) {
            layout.clear(0, over, z);
        }
    }

    private static boolean crown(BlockState state) {
        return state.is(AerolargeRegistry.ZEPPELIN_COVER.get()) || state.is(AerolargeRegistry.ZEPPELIN_FABRIC.get());
    }

    /** The deck's block at a place on it: a white line along its edges and dashed down its middle, else plain deck. */
    private static Block deckAt(int x, int z, int front) {
        boolean edge = Math.abs(x) == HALF_WIDTH;
        boolean dash = x == 0 && (z - front) / DASH % 2 == 0;
        return edge || dash ? AerolargeRegistry.FLIGHT_DECK_STRIPE.get() : AerolargeRegistry.FLIGHT_DECK.get();
    }

    private void bulb(int x, int y, int z) {
        layout.set(x, y, z, AerolargeRegistry.NAV_LIGHT.get().defaultBlockState().setValue(NavLightBlock.FACING, Direction.UP)
            .setValue(NavLightBlock.KIND, NavLightBlock.Kind.BULB));
    }
}
