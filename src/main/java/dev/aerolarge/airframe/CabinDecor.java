package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The furnished cabin's finishing touches, laid once the fit-out's rooms stand, from what they left: a diving tower
 * at the moon pool. Like the fit-out it only goes into open cabin space (and the pool's water), and anything that
 * does not fit at the airliner's scale is left out.
 */
final class CabinDecor {
    /** How high the diving tower's platform stands over the cabin floor, in metres: a three-metre springboard's. */
    private static final double PLATFORM_METRES = 3.0;
    /**
     * Where across the tower stands, in blocks from the middle: just to port of the low board on the centreline,
     * under the highest part of the crown.
     */
    private static final int TOWER_X = -1;
    /** Open space over the springboard, in blocks: someone standing on it and the height it throws them. */
    private static final int BOUNCE_ROOM = 5;
    /** Water at least this deep under the springboard. */
    private static final int MIN_DEPTH = 3;

    private final AirframeLayout layout;
    private final int floor;

    CabinDecor(AirframeLayout layout) {
        this.layout = layout;
        this.floor = layout.floorY();
    }

    void apply() {
        highDive();
    }

    /** The first station of a zone, in metres from the nose, as the fit-out rounds it. */
    private int from(double[] zone) {
        return (int) Math.ceil(zone[0] * layout.scale());
    }

    /** The last station of a zone, as the fit-out rounds it. */
    private int to(double[] zone) {
        return (int) Math.floor(zone[1] * layout.scale());
    }

    private boolean isWater(int x, int y, int z) {
        return layout.stateAt(x, y, z).is(Blocks.WATER);
    }

    /**
     * The diving tower (Sprungturm) on the pool's front rim: a white column standing on the pool's bottom at the head
     * of the basin, a ladder up its front from the rim, and from its top a board out over the middle of the pool with
     * a springboard at its tip. Glass rails either side of the top keep anyone from stepping off it onto the rim; off
     * the board, the deck or the springboard there is only water below. Left out where the pool is too shallow or too
     * narrow, or the crown too low to bounce under.
     */
    private void highDive() {
        int fromZ = from(CabinFitOut.POOL);
        int toZ = to(CabinFitOut.POOL);
        int first = -1;
        int last = -1;
        for (int z = fromZ; z <= toZ; z++) {
            if (isWater(0, floor, z)) {
                first = first < 0 ? z : first;
                last = z;
            }
        }
        if (first < 0) {
            return;
        }
        int middle = (first + last) / 2;
        int halfWidth = 0;
        while (isWater(halfWidth + 1, floor, middle)) {
            halfWidth++;
        }
        int x = TOWER_X;
        int rim = first - 1;
        int top = floor + (int) Math.round(PLATFORM_METRES * layout.scale());
        if (halfWidth < Math.abs(x) + 1 || middle - first < 2 || !towerFits(x, rim, first, middle, top)) {
            return;
        }
        BlockState column = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        int bottom = floor;
        while (isWater(x, bottom - 1, first)) {
            bottom--;
        }
        for (int y = bottom; y < top; y++) {
            layout.set(x, y, first, column);
        }
        for (int y = floor + 1; y < top; y++) {
            layout.furnish(x, y, rim, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        }
        for (int z = first + 1; z < middle; z++) {
            layout.furnish(x, top - 1, z, Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        }
        layout.furnish(x, top - 1, middle, AerolargeRegistry.SPRINGBOARD.get().defaultBlockState());
        for (int side : new int[]{1, -1}) {
            BlockState rail = Blocks.GLASS_PANE.defaultBlockState();
            boolean both = layout.isOpen(x + side, top, rim) && layout.isOpen(x + side, top, first);
            layout.furnish(x + side, top, rim, rail.setValue(IronBarsBlock.SOUTH, both));
            layout.furnish(x + side, top, first, rail.setValue(IronBarsBlock.NORTH, both));
        }
    }

    /**
     * Whether the tower fits in this column: the rim open over the floor up to the platform for the ladder, the
     * column at the head of the basin open above the water and water below, the board open out to the middle, room to
     * bounce over the springboard and deep water under it.
     */
    private boolean towerFits(int x, int rim, int first, int middle, int top) {
        for (int y = floor + 1; y < top; y++) {
            if (!layout.isOpen(x, y, rim) || !layout.isOpen(x, y, first)) {
                return false;
            }
        }
        for (int z = first + 1; z <= middle; z++) {
            if (!layout.isOpen(x, top - 1, z)) {
                return false;
            }
        }
        for (int y = top; y < top + BOUNCE_ROOM; y++) {
            if (!layout.isOpen(x, y, middle)) {
                return false;
            }
        }
        for (int y = floor; y > floor - MIN_DEPTH; y--) {
            if (!isWater(x, y, first) || !isWater(x, y, middle)) {
                return false;
            }
        }
        return true;
    }
}
