package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.SpacePoster;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Paintings of the airliner's sister ships in the furnished airliner (see {@link SpacePoster}), each high on a bulkhead
 * over its archway, looked at from a long way down the cabin: the rocket in the forward door hall over the way into
 * business class, straight ahead coming out of the flight deck, and the zeppelin across the moon pool over the way out
 * of it, straight ahead coming in from business class, the overhead WC sign hanging just under it; and the freighter
 * to come after them, a teaser, over the way into the lounge, straight ahead coming out of the gym hall. The panels
 * beside the archways are too narrow and low for a margin round a picture, so each hangs in the middle of the cabin
 * at the lowest row where the bulkhead carries on past every side of it, never up against the hull, a ceiling or an
 * archway. Each goes in open cabin space, whole or not at all, so where it does not fit at the airliner's scale it is
 * left out; and a bulkhead the cabin dividers found no place for carries none (see {@link CabinDividers}).
 */
final class CabinPaintings {
    /** How far outside its room a bulkhead may stand, in metres. */
    private static final double BULKHEAD_REACH = 2.5;
    /** The ways the bulkhead must carry on past a painting: over it and under it, and along it to either side. */
    private static final List<Direction> ROUND = List.of(Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST);

    private final AirframeLayout layout;
    private final int floor;

    CabinPaintings(AirframeLayout layout) {
        this.layout = layout;
        this.floor = layout.floorY();
    }

    void apply() {
        if (!SkylinersConfig.FLEET_PAINTINGS.get()) {
            return;
        }
        hang(SpacePoster.ROCKET, divider(CabinFitOut.BUSINESS, -1));
        hang(SpacePoster.ZEPPELIN, divider(CabinFitOut.POOL, 1));
        hang(SpacePoster.FREIGHTER, divider(CabinFitOut.LOUNGE, -1));
    }

    /** The first divider's station from the middle of a room one way along the cabin; -1 if there is none near it. */
    private int divider(double[] room, int way) {
        int middle = (int) Math.round((room[0] + room[1]) / 2 * layout.scale());
        int reach = (int) Math.ceil(((room[1] - room[0]) / 2 + BULKHEAD_REACH) * layout.scale());
        for (int along = 0; along <= reach; along++) {
            if (CabinDividers.standsAt(layout, middle + way * along)) {
                return middle + way * along;
            }
        }
        return -1;
    }

    /** Hangs a painting looking forward from a bulkhead, at the lowest row with a margin of the bulkhead round it. */
    private void hang(SpacePoster painting, int bulkhead) {
        if (bulkhead < 0) {
            return;
        }
        for (int lowest = floor + 1; layout.inFuselage(0, lowest, bulkhead - 1); lowest++) {
            if (hangAt(painting, lowest, bulkhead)) {
                return;
            }
        }
    }

    /** Hangs a painting with its lowest row at a row, in the middle of the cabin, if it fits there with its margin. */
    private boolean hangAt(SpacePoster painting, int lowest, int bulkhead) {
        BlockPos middle = new BlockPos(0, lowest + painting.rowsUnderMiddle(), bulkhead - 1);
        Map<BlockPos, BlockState> pieces = SkylinersRegistry.SPACE_POSTER.get().pieces(painting, middle, Direction.NORTH);
        boolean fits = pieces.keySet().stream().allMatch(at -> layout.isOpen(at.getX(), at.getY(), at.getZ())
            && isBulkhead(at.getX(), at.getY(), bulkhead) && bulkheadRound(at, pieces.keySet(), bulkhead));
        if (fits) {
            pieces.forEach((at, piece) -> layout.set(at.getX(), at.getY(), at.getZ(), piece));
        }
        return fits;
    }

    /** Whether, each way from a block of a painting, there is either more of the painting or more of the bulkhead. */
    private boolean bulkheadRound(BlockPos at, Set<BlockPos> painting, int bulkhead) {
        return ROUND.stream().map(at::relative).allMatch(next -> painting.contains(next) || isBulkhead(next.getX(), next.getY(), bulkhead));
    }

    /** Whether a block is the bulkhead's own wall: its panelling or a sign set into it, not its archway or the hull. */
    private boolean isBulkhead(int x, int y, int z) {
        BlockState state = layout.stateAt(x, y, z);
        return state.is(SkylinersRegistry.CABIN_PARTITION.get()) || state.is(SkylinersRegistry.BULKHEAD_SIGN.get());
    }
}
