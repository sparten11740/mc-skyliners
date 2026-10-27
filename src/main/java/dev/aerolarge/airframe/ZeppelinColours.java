package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.FlagHalyardBlock;
import dev.aerolarge.block.FlagstaffBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The ship's colours: a white flagstaff at the aft end of the Sky Deck's middle (see {@link ZeppelinSkyDeck}), tall
 * enough that its flag streams out aft over the crown well above anyone on the deck, and the halyard at its foot that
 * a banner is hoisted at (see {@link FlagHalyardBlock}). The staff stands on the deck's grating, each length on the
 * one under it, so it is assembled with the zeppelin; nothing stands on it. Without the Sky Deck, it is left out.
 */
final class ZeppelinColours implements ZeppelinPiece {
    /** How far aft of the deck's middle the staff stands, in rows: inside its aft rail. */
    private static final int AFT = 2;
    /** How tall the staff stands over the deck, the halyard at its foot included, in blocks. */
    private static final int STAFF_HEIGHT = 15;

    private final ZeppelinLayout layout;

    ZeppelinColours(ZeppelinLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return AerolargeConfig.SHIPS_COLOURS.get();
    }

    @Override
    public void build() {
        int z = ZeppelinSkyDeck.station() + AFT;
        int deck = ZeppelinSkyDeck.floor(layout);
        if (!layout.stateAt(0, deck, z).is(AerolargeRegistry.ZEPPELIN_CATWALK.get()) || !clear(deck, z)) {
            return;
        }
        layout.set(0, deck + 1, z, AerolargeRegistry.FLAG_HALYARD.get().defaultBlockState().setValue(FlagHalyardBlock.FACING, Direction.SOUTH));
        BlockState staff = AerolargeRegistry.FLAGSTAFF.get().defaultBlockState();
        for (int y = deck + 2; y <= deck + STAFF_HEIGHT; y++) {
            layout.set(0, y, z, staff.setValue(FlagstaffBlock.TOP, y == deck + STAFF_HEIGHT));
        }
    }

    /** Whether nothing stands where the staff would, over the deck. */
    private boolean clear(int deck, int z) {
        for (int y = deck + 1; y <= deck + STAFF_HEIGHT; y++) {
            if (!layout.isOpen(0, y, z)) {
                return false;
            }
        }
        return true;
    }
}
