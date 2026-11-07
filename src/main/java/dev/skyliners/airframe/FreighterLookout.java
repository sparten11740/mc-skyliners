package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.CabinSeatBlock;
import dev.skyliners.block.PassengerScreenBlock;
import dev.skyliners.block.PlanterBlock;
import dev.skyliners.block.StandingWindowBlock;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The lookout down the tunnel in the freighter's port prong, past her gallery's posters (see {@link FreighterGallery}):
 * a seat facing each porthole down its outer wall (see {@link FreighterPortholes}), a block of leg room before it, so
 * whoever walks out along her prong can sit and watch the sky go by; and at its tip, past her nose jet's casing (see
 * {@link FreighterEngineBays}), a nook of seats in a row facing the engine bulkhead across the tip, a passenger screen
 * on it before the middle seat, which shows her outside cameras and how she flies, planters either end: a front row in
 * her nose. Every seat leaves the tunnel open beside it to walk on past.
 *
 * <p>Laid out nose north, so her port prong is to the west, after her portholes, whose places it looks for; none with
 * the server config's {@code freighter.prongs} off.
 */
final class FreighterLookout {
    /** The seats' upholstery, by turns. */
    private static final DyeColor[] SEATS = {DyeColor.ORANGE, DyeColor.BROWN};
    /** How many seats sit in the nook's row, and how many blocks of leg room before them, to the bulkhead. */
    private static final int NOOK_SEATS = 3;
    private static final int LEG_ROOM = 1;
    /** How many blocks of the tunnel are left open to walk past a seat, on its far side from the wall, at least. */
    private static final int WAY_PAST = 2;
    /** How far across her the nook is looked along from her middle line, in blocks, and toward her tip, at most. */
    private static final int TUNNEL_REACH = 24;
    private static final int TIP_REACH = 16;
    /** How far ahead of her axis a porthole has to be to be down her prong rather than in her disc, in blocks. */
    private static final double PRONG_FROM = FreighterLayout.BULKHEAD * FreighterLayout.SCALE;

    private final HullLayout layout;
    private final int deck;
    private int seated;

    private FreighterLookout(HullLayout layout, int deck) {
        this.layout = layout;
        this.deck = deck;
    }

    /**
     * Lays the lookout in a freighter's layout whose deck's floor is the row given, round her port prong's nose jet,
     * whose relay is given; only the seats by the portholes where she has no such jet.
     */
    static void build(HullLayout layout, int deck, @Nullable BlockPos jetRelay) {
        if (!SkylinersConfig.FREIGHTER_PRONGS.get()) {
            return;
        }
        FreighterLookout lookout = new FreighterLookout(layout, deck);
        if (jetRelay == null) {
            lookout.byPortholes(Integer.MIN_VALUE);
            return;
        }
        lookout.nook(jetRelay);
        lookout.byPortholes(jetRelay.getZ() + FreighterEngineBays.CASING_ALONG);
    }

    /**
     * A seat facing each porthole down her port prong's outer wall astern of the row given, the nose jet's casing's,
     * where there is room for it and a way past.
     */
    private void byPortholes(int casing) {
        List<BlockPos> portholes = layout.cells.entrySet().stream()
            .filter(cell -> cell.getValue().is(SkylinersRegistry.PORTHOLE.get()) && cell.getValue().getValue(StandingWindowBlock.FACING) == Direction.WEST)
            .map(Map.Entry::getKey).filter(at -> at.getX() < 0 && -at.getZ() > PRONG_FROM && at.getZ() > casing).toList();
        for (BlockPos porthole : portholes) {
            BlockPos seat = new BlockPos(porthole.getX() + LEG_ROOM + 1, deck + 1, porthole.getZ());
            if (open(porthole.getX() + 1, porthole.getZ(), LEG_ROOM) && open(seat.getX(), seat.getZ(), WAY_PAST + 1)) {
                seat(seat, Direction.WEST);
            }
        }
    }

    /**
     * The nook at her prong's tip: its first open row before the bulkhead, found walking toward her tip beside the
     * jet's casing; the seats in a row a block of leg room back from it, the middle one in line with the screen, a
     * planter at either end of the row against the bulkhead.
     */
    private void nook(BlockPos jetRelay) {
        int fromZ = jetRelay.getZ() - FreighterEngineBays.CASING_ALONG - 1;
        int inner = tunnelEdge(fromZ);
        if (inner > 0) {
            return;
        }
        int tip = fromZ;
        while (tip > fromZ - TIP_REACH && openInside(inner, deck + 1, tip - 1)) {
            tip--;
        }
        int outer = inner;
        while (openInside(outer - 1, deck + 1, tip)) {
            outer--;
        }
        int middle = (inner + outer) / 2;
        int row = tip + LEG_ROOM + 1;
        for (int seat = 0; seat < NOOK_SEATS; seat++) {
            int x = middle + seat - NOOK_SEATS / 2;
            if (x > outer && x < inner && openColumn(x, row) && openColumn(x, row - LEG_ROOM)) {
                seat(new BlockPos(x, deck + 1, row), Direction.NORTH);
            }
        }
        if (openInside(middle, deck + 2, tip)) {
            layout.set(middle, deck + 2, tip, SkylinersRegistry.PASSENGER_SCREEN.get().defaultBlockState().setValue(PassengerScreenBlock.FACING, Direction.SOUTH));
        }
        planter(outer, tip, PlanterBlock.Plant.GREENERY);
        planter(inner, tip, PlanterBlock.Plant.FLOWERS);
    }

    /** A seat, both its halves, facing the way given, in the next of the seats' colours. */
    private void seat(BlockPos at, Direction facing) {
        BlockState seat = CabinSeatBlock.seat(SkylinersRegistry.BUSINESS_SEAT.get(), SEATS[seated++ % SEATS.length], facing);
        layout.set(at.getX(), at.getY(), at.getZ(), seat);
        layout.set(at.getX(), at.getY() + 1, at.getZ(), seat.setValue(CabinSeatBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private void planter(int x, int z, PlanterBlock.Plant plant) {
        if (openInside(x, deck + 1, z)) {
            layout.set(x, deck + 1, z, PlanterBlock.planted(SkylinersRegistry.PLANTER.get(), plant, Direction.SOUTH));
        }
    }

    /** Whether the blocks from a column inward, toward her middle line, so many of them, are open two rows high. */
    private boolean open(int x, int z, int across) {
        for (int step = 0; step < across; step++) {
            if (!openColumn(x + step, z)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a column over her deck is open two rows high, as a seat or a standing player takes. */
    private boolean openColumn(int x, int z) {
        return openInside(x, deck + 1, z) && openInside(x, deck + 2, z);
    }

    /** The column of her port tunnel nearest her middle line on a row across her, walking out from it; 1 for none. */
    private int tunnelEdge(int z) {
        for (int x = 0; x >= -TUNNEL_REACH; x--) {
            if (openInside(x, deck + 1, z)) {
                return x;
            }
        }
        return 1;
    }

    private boolean openInside(int x, int y, int z) {
        return layout.isOpen(x, y, z) && layout.surface().depth(x, y + 0.5, z) < 0;
    }
}
