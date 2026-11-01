package dev.skyliners.airframe;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.CabinRailBlock;
import dev.skyliners.block.CabinStairsBlock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * A staircase up to the upper deck, laid as the walk up it from the main deck's floor to the deck's: carpeted stairs of
 * two half steps to a block (see {@link CabinStairsBlock}), and wherever the walk turns, a landing level with the stair
 * before it, the next stair climbing on from it, so the whole of it is walked up and down without a jump. Over every
 * tread a head's height and a little more stays clear, stepping on to the next tread included; where the upper deck's
 * floor would come lower than that, it is opened. Glass rails stand along its open sides, wherever stepping off it
 * would drop more than a half step (see {@link CabinRailBlock}).
 */
final class DeckStairs {
    /** The clear height kept over every tread, in blocks. */
    private static final int HEADROOM = 2;

    /** A block of the walk: a stair climbing the given way, or a landing (climb null), in a row. */
    private record Tread(int x, int z, int row, @Nullable Direction climb) {
        /** How high its top is. */
        double top() {
            return row + 1;
        }

        /** How high the half of it stepped on first is: a stair's lower step. */
        double first() {
            return climb == null ? row + 1 : row + 0.5;
        }
    }

    private final AirframeLayout layout;
    private final int floor;
    private final int deck;
    /** Where the walk starts on the main deck's floor and where it comes out on the upper deck's, as (x, z). */
    private final int[] entry;
    private final int[] exit;
    private final List<Tread> treads;
    /** The columns the walk winds round, solid from the main deck's floor to the upper deck's. */
    private final List<int[]> newel;
    /** Whether each stair has its underside under it, the same stair turned over, so the flight's soffit slopes. */
    private final boolean soffits;

    private DeckStairs(AirframeLayout layout, int[] entry, List<Tread> treads, int[] exit, List<int[]> newel, boolean soffits) {
        this.layout = layout;
        this.floor = layout.floorY();
        this.deck = layout.upperDeckY();
        this.entry = entry;
        this.treads = treads;
        this.exit = exit;
        this.newel = newel;
        this.soffits = soffits;
    }

    /**
     * A staircase winding up round a newel inside a rectangle so many blocks across and along, its east side at one
     * column and its north end at one station: in at the south-east corner, it climbs north up the east side, west along
     * the north end, south down the west side and east along the south end, round and round, a stair on every block
     * along a side and a landing in every corner, until it comes out level with the upper deck's floor.
     */
    static DeckStairs round(AirframeLayout layout, int east, int north, int across, int along) {
        int west = east - across + 1;
        int south = north + along - 1;
        List<int[]> ring = new ArrayList<>();
        for (int z = south; z > north; z--) {
            ring.add(new int[]{east, z});
        }
        for (int x = east; x > west; x--) {
            ring.add(new int[]{x, north});
        }
        for (int z = north; z < south; z++) {
            ring.add(new int[]{west, z});
        }
        for (int x = west; x < east; x++) {
            ring.add(new int[]{x, south});
        }
        // Round the ring from the corner after the way in, the row the walk has climbed to so far.
        List<Tread> treads = new ArrayList<>();
        int row = layout.floorY();
        int k = 0;
        while (row < layout.upperDeckY()) {
            k++;
            int[] at = ring.get(k % ring.size());
            if ((at[0] == east || at[0] == west) && (at[1] == north || at[1] == south)) {
                treads.add(new Tread(at[0], at[1], row, null));
            } else {
                int[] next = ring.get((k + 1) % ring.size());
                row++;
                treads.add(new Tread(at[0], at[1], row, Direction.fromDelta(next[0] - at[0], 0, next[1] - at[1])));
            }
        }
        List<int[]> newel = new ArrayList<>();
        for (int x = west + 1; x < east; x++) {
            for (int z = north + 1; z < south; z++) {
                newel.add(new int[]{x, z});
            }
        }
        return new DeckStairs(layout, ring.getFirst(), treads, ring.get((k + 1) % ring.size()), newel, false);
    }

    /** A straight flight climbing the given way from its first stair, at this column and station. */
    static DeckStairs straight(AirframeLayout layout, int x, int z, Direction climb) {
        int rise = layout.upperDeckY() - layout.floorY();
        List<Tread> treads = new ArrayList<>();
        for (int k = 0; k < rise; k++) {
            treads.add(new Tread(x + k * climb.getStepX(), z + k * climb.getStepZ(), layout.floorY() + 1 + k, climb));
        }
        int[] entry = {x - climb.getStepX(), z - climb.getStepZ()};
        int[] exit = {x + rise * climb.getStepX(), z + rise * climb.getStepZ()};
        return new DeckStairs(layout, entry, treads, exit, List.of(), true);
    }

    /** The station where it comes out on the upper deck. */
    int exitStation() {
        return exit[1];
    }

    /** How high anyone's feet come over a tread: its top, or the first half of the next one, stepping on to it. */
    private double reach(int i) {
        return Math.max(treads.get(i).top(), i + 1 < treads.size() ? treads.get(i + 1).first() : deck + 1);
    }

    /**
     * Whether it can be laid: its treads and its newel only in open cabin, the deck's floor bar, which it opens where
     * it passes through it; nothing low down in a door's aisle; and a head's room over every tread, the floor at its
     * foot and at its top included, but for the deck's floor, which opens over it.
     */
    boolean fits() {
        Set<BlockPos> laid = new HashSet<>();
        for (Tread tread : treads) {
            laid.add(new BlockPos(tread.x(), tread.row(), tread.z()));
        }
        for (int[] at : newel) {
            for (int y = floor + 1; y < deck; y++) {
                laid.add(new BlockPos(at[0], y, at[1]));
            }
        }
        for (BlockPos pos : laid) {
            if (!openable(pos.getX(), pos.getY(), pos.getZ()) || pos.getY() <= floor + HEADROOM && layout.inDoorAisle(pos.getZ())) {
                return false;
            }
        }
        for (int i = 0; i < treads.size(); i++) {
            Tread tread = treads.get(i);
            for (int y = tread.row() + 1; y < reach(i) + HEADROOM; y++) {
                if (laid.contains(new BlockPos(tread.x(), y, tread.z())) || !openable(tread.x(), y, tread.z())) {
                    return false;
                }
            }
        }
        return clear(entry, floor + 1, floor + HEADROOM) && openable(exit[0], deck, exit[1]) && clear(exit, deck + 1, deck + HEADROOM);
    }

    /** Whether a block is open cabin, or the upper deck's floor, which the staircase can open. */
    private boolean openable(int x, int y, int z) {
        return layout.isOpen(x, y, z) || y == deck && layout.isCabinFloor(x, y, z);
    }

    /** Whether the cabin is open at a column from one row up to another. */
    private boolean clear(int[] at, int from, int to) {
        for (int y = from; y <= to; y++) {
            if (!layout.isOpen(at[0], y, at[1])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Lays it: the newel, the treads (and under each stair of a straight flight its underside), a landing where it comes
     * out if the deck has no floor there, the deck's floor opened where it comes too low over a tread, and the rails.
     */
    void lay() {
        BlockState landing = SkylinersRegistry.CABIN_LANDING.get().defaultBlockState();
        BlockState stair = SkylinersRegistry.CABIN_STAIRS.get().defaultBlockState();
        for (int[] at : newel) {
            for (int y = floor + 1; y < deck; y++) {
                layout.set(at[0], y, at[1], landing);
            }
        }
        for (Tread tread : treads) {
            if (tread.climb() == null) {
                layout.set(tread.x(), tread.row(), tread.z(), landing);
                continue;
            }
            layout.set(tread.x(), tread.row(), tread.z(), stair.setValue(CabinStairsBlock.FACING, tread.climb()));
            if (soffits && layout.isOpen(tread.x(), tread.row() - 1, tread.z())) {
                layout.set(tread.x(), tread.row() - 1, tread.z(),
                    stair.setValue(CabinStairsBlock.FACING, tread.climb().getOpposite()).setValue(CabinStairsBlock.HALF, Half.TOP));
            }
        }
        if (layout.isOpen(exit[0], deck, exit[1])) {
            layout.set(exit[0], deck, exit[1], landing);
        }
        for (int i = 0; i < treads.size(); i++) {
            Tread tread = treads.get(i);
            if (tread.row() < deck && reach(i) + HEADROOM > deck && layout.isCabinFloor(tread.x(), deck, tread.z())) {
                layout.set(tread.x(), deck, tread.z(), Blocks.AIR.defaultBlockState());
            }
        }
        for (int i = 0; i < treads.size(); i++) {
            railOpenSides(at(i), treads.get(i).row(), i == 0 ? entry : at(i - 1), i + 1 < treads.size() ? at(i + 1) : exit);
        }
        railOpenSides(exit, deck, at(treads.size() - 1), exit);
    }

    /** Where a tread is, as (x, z). */
    private int[] at(int i) {
        return new int[]{treads.get(i).x(), treads.get(i).z()};
    }

    /** Rails along the sides of a block of the walk that drop away, but for those it is walked on from and to. */
    private void railOpenSides(int[] at, int row, int[] from, int[] to) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            int x = at[0] + side.getStepX();
            int z = at[1] + side.getStepZ();
            if (!(x == from[0] && z == from[1]) && !(x == to[0] && z == to[1]) && dropsAway(layout, x, row, z)) {
                rail(layout, at[0], row + 1, at[1], side);
            }
        }
    }

    /**
     * Whether stepping across onto this block from a tread or floor in its row would drop more than a half step: it is
     * no floor, landing or stair to step onto, and no wall stands over it to keep anyone from stepping across.
     */
    static boolean dropsAway(AirframeLayout layout, int x, int row, int z) {
        BlockState there = layout.stateAt(x, row, z);
        return layout.inFuselage(x, row, z) && !full(there) && !(there.getBlock() instanceof CabinStairsBlock)
            && !(there.getBlock() instanceof StairBlock) && !full(layout.stateAt(x, row + 1, z));
    }

    private static boolean full(BlockState state) {
        return state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    /** Puts a glass rail along an edge of an open block, or adds the edge to a rail already standing there. */
    static void rail(AirframeLayout layout, int x, int y, int z, Direction side) {
        BlockState rail = SkylinersRegistry.CABIN_RAIL.get().defaultBlockState();
        BlockState there = layout.stateAt(x, y, z);
        if (layout.isOpen(x, y, z) || there.is(rail.getBlock())) {
            layout.set(x, y, z, CabinRailBlock.along(there, side, layout.stateAt(x, y - 1, z), rail));
        }
    }
}
