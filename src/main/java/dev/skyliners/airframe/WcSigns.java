package dev.skyliners.airframe;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.BulkheadSignBlock;
import dev.skyliners.block.CabinRailBlock;
import dev.skyliners.block.OverheadWcSignBlock;
import dev.skyliners.block.OverheadWcSignBlockEntity;
import dev.skyliners.block.WcSignBlock;
import dev.skyliners.block.WcSignBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The WC signs, as an airliner has them. Over the door of every WC built into the cabin (the WC behind the flight deck,
 * the lavatories either side of the gym hall's archway), a WC sign set into the wall, lit for whether anyone is in the
 * WC (see {@link #overDoor}). And in the furnished cabin, up high where someone looking for a WC looks, overhead WC
 * signs pointing the way to the nearest:
 * <ul>
 * <li>on a bulkhead's side facing into its room, over its archway or beside it, where the nearest WC lies through
 * the archway (business class's front wall, the pool's back wall, the lounge's front wall);</li>
 * <li>on the back of each lavatory, facing the gym hall behind it;</li>
 * <li>beside the top of the steps up to the flight deck, facing down them into the cabin;</li>
 * <li>on the upper deck, which has no WC of its own: on the wall beside the top of each staircase, pointing down it,
 * and over the deck's rows, on a bulkhead's side facing them where the nearest staircase lies through it.</li>
 * </ul>
 * Each shows the WCs it points to, lit red only while all of them are taken; those on the upper deck show the WCs
 * nearest the foot of their staircase. A sign only goes up against a flat wall (the cabin's partition, a bulkhead
 * sign, plain hull), into open cabin with open cabin either side of it, so high that nobody's head comes up to it or
 * beside it, out of the doors' aisles and beside no window; where no spot has room for one, there is none.
 */
final class WcSigns {
    /**
     * Rows kept clear under a sign, over the floor or the carpet on it: a player's height, so the sign is over every
     * head.
     */
    private static final int CLEAR_UNDER = 2;
    /** How far either side of the middle a sign on a bulkhead may go for flat wall beside its archway, in blocks. */
    private static final int BESIDE_ARCH = 4;
    /** How far out from the steps up to the flight deck a sign beside them may go, in blocks past the steps' edge. */
    private static final int BESIDE_STEPS = 2;
    /** How far along the wall from the top of a staircase a sign beside it may go, in stations either way. */
    private static final int BESIDE_STAIRS = 3;

    /**
     * A WC built into the cabin: the sign over its door, the way it faces (out of the WC), and two opposite corners of
     * the room inside.
     */
    record Wc(BlockPos sign, Direction facing, BlockPos from, BlockPos to) {
    }

    private final AirframeLayout layout;

    WcSigns(AirframeLayout layout) {
        this.layout = layout;
    }

    /**
     * Sets the WC sign into the wall over a WC's door, in place of the wall's block there, facing out of the WC, and
     * remembers the WC for the overhead signs. The sign watches the room from one corner given to the other.
     */
    static void overDoor(AirframeLayout layout, BlockPos sign, Direction facing, BlockPos from, BlockPos to) {
        layout.set(sign.getX(), sign.getY(), sign.getZ(), SkylinersRegistry.WC_SIGN.get().defaultBlockState().setValue(WcSignBlock.FACING, facing));
        layout.fit(sign, (blockEntity, rotation) -> {
            if (blockEntity instanceof WcSignBlockEntity wc) {
                wc.watch(from.subtract(sign).rotate(rotation), to.subtract(sign).rotate(rotation));
            }
        });
        layout.wcs().add(new Wc(sign, facing, from, to));
    }

    /** Puts up the overhead signs, once every room stands. */
    void apply() {
        List<Wc> wcs = layout.wcs();
        if (wcs.isEmpty()) {
            return;
        }
        UpperDeck upper = layout.upperDeck();
        List<int[]> stairHeads = upper == null ? List.of() : upper.stairHeads();
        bulkheads(wcs, stairHeads);
        for (Wc wc : wcs) {
            backOf(wc);
        }
        steps(wcs);
        for (int[] head : stairHeads) {
            stairHead(head[0], head[1], wcs);
        }
    }

    /**
     * Signs on the bulkheads' sides facing into their rooms. On the main deck, where the nearest WC lies through the
     * archway: over the archway, level with the sign on its other side, or as near over it as there is flat wall, the
     * WC's side first, showing the nearest WCs. On the upper deck, where its floor runs whole over the room, and the
     * nearest staircase down lies through the bulkhead: just over head height, in the middle or as near it as there is
     * flat wall, showing the WCs nearest the stairs.
     */
    private void bulkheads(List<Wc> wcs, List<int[]> stairHeads) {
        List<BlockPos> bulkheadSigns = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> cell : layout.cells().entrySet()) {
            if (cell.getKey().getX() == 0 && cell.getValue().getBlock() instanceof BulkheadSignBlock) {
                bulkheadSigns.add(cell.getKey());
            }
        }
        for (BlockPos sign : bulkheadSigns) {
            // The bulkhead sign faces out of its room, the way through the archway.
            Direction through = layout.stateAt(sign.getX(), sign.getY(), sign.getZ()).getValue(BulkheadSignBlock.FACING);
            Direction facing = through.getOpposite();
            int z = sign.getZ();
            int room = z + facing.getStepZ();
            List<Wc> nearest = nearest(wcs, z);
            if (ahead(nearest.getFirst().sign().getZ(), z, through)) {
                high(room, sign.getY(), layout.floorY(), Integer.signum(nearest.getFirst().sign().getX()), facing, nearest);
            }
            int deck = layout.upperDeckY();
            int[] stairs = stairHeads.stream().min(Comparator.comparingInt((int[] head) -> Math.abs(head[0] - z))).orElse(null);
            if (stairs != null && layout.isCabinFloor(0, deck, room) && ahead(stairs[0], z, through)) {
                int overHead = deck + 1 + CLEAR_UNDER;
                high(room, overHead, overHead - 1, stairs[1], facing, nearest(wcs, stairs[0]));
            }
        }
    }

    /** Whether a station lies through a bulkhead at station z, the way given. */
    private static boolean ahead(int station, int z, Direction through) {
        return (station - z) * through.getStepZ() > 0;
    }

    /**
     * A sign against a bulkhead at station z, facing the way given, in the first spot that fits, from the top row
     * down to the bottom one (not included), each row from the middle outward, the side given first.
     */
    private void high(int z, int top, int bottom, int side, Direction facing, List<Wc> shown) {
        for (int y = top; y > bottom; y--) {
            for (int x : outwards(side, BESIDE_ARCH)) {
                BlockPos at = new BlockPos(x, y, z);
                if (fits(at, facing)) {
                    put(at, facing, OverheadWcSignBlock.Arrow.AHEAD, shown);
                    return;
                }
            }
        }
    }

    /**
     * A sign on the back wall of a WC, facing away from it into the cabin there, when the WC's room ends at a wall with
     * open cabin beyond it, as the lavatories by the gym hall's archway do: in the wall's row over the room, or failing
     * that its top row, in line with the WC's sign. It shows that WC.
     */
    private void backOf(Wc wc) {
        Direction back = wc.facing().getOpposite();
        Direction.Axis axis = back.getAxis();
        int far = back.getAxisDirection() == Direction.AxisDirection.POSITIVE
            ? Math.max(wc.from().get(axis), wc.to().get(axis)) : Math.min(wc.from().get(axis), wc.to().get(axis));
        // Through the room to its far side, then through its back wall.
        BlockPos beyond = wc.sign().relative(back, Math.abs(far - wc.sign().get(axis)) + 2);
        int top = Math.max(wc.from().getY(), wc.to().getY());
        for (int y = top + 1; y >= top; y--) {
            BlockPos at = beyond.atY(y);
            if (fits(at, back)) {
                put(at, back, OverheadWcSignBlock.Arrow.AHEAD, List.of(wc));
                return;
            }
        }
    }

    /**
     * A sign beside the top of the steps up to the flight deck, against the back wall of the WC and the crew room on
     * its raised floor, facing down the steps into the cabin: as high as the sign over the WC's door, or as near under
     * it as there is room, beside the steps on the WC's side first. It shows the WCs on the raised floor.
     */
    private void steps(List<Wc> wcs) {
        int back = layout.flightDeckBack();
        List<Wc> shown = wcs.stream().filter(wc -> wc.sign().getZ() <= back).toList();
        if (shown.isEmpty()) {
            return;
        }
        int side = Integer.signum(shown.getFirst().sign().getX());
        int top = shown.stream().mapToInt(wc -> wc.sign().getY()).max().orElseThrow();
        for (int y = top; y > layout.floorY(); y--) {
            for (int out = 1; out <= BESIDE_STEPS; out++) {
                for (int x : new int[]{side * (AirframeLayout.STEPS_HALF_WIDTH + out), -side * (AirframeLayout.STEPS_HALF_WIDTH + out)}) {
                    BlockPos at = new BlockPos(x, y, back + 1);
                    if (fits(at, Direction.SOUTH)) {
                        put(at, Direction.SOUTH, OverheadWcSignBlock.Arrow.AHEAD, shown);
                        return;
                    }
                }
            }
        }
    }

    /**
     * A sign on the wall beside the top of a staircase down from the upper deck, as the exit sign over it, just over
     * head height and facing into the deck, its arrow pointing down: at the next station along that fits, nearest the
     * stairs first. It shows the WCs nearest the stairs.
     */
    private void stairHead(int station, int side, List<Wc> wcs) {
        int y = layout.upperDeckY() + 1 + CLEAR_UNDER;
        Direction facing = side > 0 ? Direction.WEST : Direction.EAST;
        for (int along = 1; along <= BESIDE_STAIRS; along++) {
            for (int z : new int[]{station + along, station - along}) {
                int x = layout.besideWall(side, y, z);
                BlockPos at = new BlockPos(x, y, z);
                if (x != 0 && fits(at, facing)) {
                    put(at, facing, OverheadWcSignBlock.Arrow.DOWN, nearest(wcs, station));
                    return;
                }
            }
        }
    }

    /**
     * The WCs nearest a station along the cabin: the nearest, and any other at the same station, as the lavatories
     * either side of an archway are.
     */
    private static List<Wc> nearest(List<Wc> wcs, int z) {
        Wc nearest = wcs.stream().min(Comparator.comparingInt((Wc wc) -> Math.abs(wc.sign().getZ() - z))).orElseThrow();
        return wcs.stream().filter(wc -> wc.sign().getZ() == nearest.sign().getZ()).toList();
    }

    /** The middle, then a block further out each way in turn, the given side first, as far as the reach. */
    private static List<Integer> outwards(int side, int reach) {
        int first = side == 0 ? 1 : side;
        List<Integer> columns = new ArrayList<>(List.of(0));
        for (int out = 1; out <= reach; out++) {
            columns.add(first * out);
            columns.add(-first * out);
        }
        return columns;
    }

    /**
     * Whether an overhead sign facing this way fits here: in open cabin, its back to a flat wall, open cabin in front
     * of its face and at either end of it along the wall so it butts against nothing, over everyone's head there and
     * beside it, beside no window, and on the main deck out of the doors' aisles.
     */
    private boolean fits(BlockPos at, Direction facing) {
        BlockPos wall = at.relative(facing.getOpposite());
        BlockPos left = at.relative(facing.getCounterClockWise());
        BlockPos right = at.relative(facing.getClockWise());
        boolean upstairs = layout.upperDeck() != null && at.getY() > layout.upperDeckY();
        return isOpen(at) && flat(wall) && isOpen(at.relative(facing)) && isOpen(left) && isOpen(right)
            && overHeads(at) && overHeads(left) && overHeads(right)
            && (upstairs || !layout.inDoorAisle(at.getZ())) && !layout.besideWindow(at.getX(), at.getY(), at.getZ());
    }

    private boolean isOpen(BlockPos pos) {
        return layout.isOpen(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Whether the block is flat wall a sign can stand against: the cabin's partition, a bulkhead sign, plain hull. */
    private boolean flat(BlockPos pos) {
        BlockState state = layout.stateAt(pos.getX(), pos.getY(), pos.getZ());
        return state.is(SkylinersRegistry.CABIN_PARTITION.get()) || state.getBlock() instanceof BulkheadSignBlock
            || layout.isPlainHull(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Whether nobody's head comes up into the block: the rows under it are open, the lowest but for what nobody stands
     * on top of there, the carpet on a floor or a glass rail along its edge.
     */
    private boolean overHeads(BlockPos at) {
        for (int down = 1; down <= CLEAR_UNDER; down++) {
            BlockPos under = at.below(down);
            Block block = layout.stateAt(under.getX(), under.getY(), under.getZ()).getBlock();
            boolean underfoot = down == CLEAR_UNDER && (block instanceof CarpetBlock || block instanceof CabinRailBlock);
            if (!isOpen(under) && !underfoot) {
                return false;
            }
        }
        return true;
    }

    /** Puts an overhead sign up facing the way given, with the arrow given, showing the WCs given. */
    private void put(BlockPos at, Direction facing, OverheadWcSignBlock.Arrow arrow, List<Wc> shown) {
        layout.set(at.getX(), at.getY(), at.getZ(), SkylinersRegistry.OVERHEAD_WC_SIGN.get().defaultBlockState()
            .setValue(OverheadWcSignBlock.FACING, facing).setValue(OverheadWcSignBlock.ARROW, arrow));
        List<BlockPos> signs = shown.stream().map(Wc::sign).toList();
        layout.fit(at, (blockEntity, rotation) -> {
            if (blockEntity instanceof OverheadWcSignBlockEntity sign) {
                sign.configure(signs.stream().map(wc -> wc.subtract(at).rotate(rotation)).toList());
            }
        });
    }
}
