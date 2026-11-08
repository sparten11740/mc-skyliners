package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.Mount;
import dev.skyliners.bowling.BallReturnBlock;
import dev.skyliners.bowling.BowlingLaneBlock;
import dev.skyliners.bowling.BowlingPinBlock;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The bowling lane down the freighter's starboard prong, which her crew have made of the long, bare tunnel out to the
 * smugglers' hold (see {@link FreighterSmugglerDen}): maple boards four blocks wide down its inner half (see
 * {@link BowlingLaneBlock}) from just short of the wall across its end to its head by her cargo hold, a rack of ten
 * pins at the far end (see {@link BowlingPinBlock}) standing on the boards in a triangle, its point toward the bowler,
 * and a ball return in the inner corner of the head of the lane, facing across it, beside where the bowler stands to
 * take a ball from it (see {@link BallReturnBlock}). The tunnel's outer half is left open, so its portholes and the
 * secret panel at its end are walked to as before, and the pins, which stop nobody, are walked through.
 *
 * <p>Found from her starboard nose jet's relay, as the hold is, and from where her tunnel is open, so it follows her
 * shape. Laid out nose north, the bowler looking north up her prong, after her LED strips along the foot of the
 * tunnel's walls, which the lane keeps clear of, and before her lights, which light whatever it leaves dark. None with
 * the server config's {@code freighter.bowling} off, nor where her tunnel has too little room for a lane.
 */
final class FreighterBowling {
    /** How many blocks wide the lane is: its rack's back row of four. */
    private static final int LANE_WIDTH = 4;
    /** How many rows of boards it runs at most, from the row past its end where the pins fall to the bowler's spot. */
    private static final int LANE_ROWS = 20;
    /** How many rows of boards the lane must have at least, or there is no room for a game down it. */
    private static final int LEAST_ROWS = 12;
    /**
     * The rack, row by row from its back, one row short of the lane's end: in each, the lane's blocks its pins stand
     * in, from its inner edge, and whether that row's pins stand half a block to the bowler's right, between blocks.
     */
    private static final int[][] RACK = {{0, 1, 2, 3}, {0, 1, 2}, {1, 2}, {1}};
    private static final boolean[] RACK_SHIFTED = {false, true, false, true};
    /** How far out from her middle line the tunnel's inner edge is looked for, in blocks. */
    private static final int TUNNEL_REACH = 24;
    /** How far astern of her nose jet's relay the tunnel is first looked across, in blocks: past the hold's wall. */
    private static final int FIRST_ROW_ASTERN = FreighterEngineBays.HOUSING_ALONG + 2;
    /** How far toward her tip from there the lane's end is looked for, at most, in blocks. */
    private static final int TIP_REACH = 16;

    private final HullLayout layout;
    private final int deck;

    private FreighterBowling(HullLayout layout, int deck) {
        this.layout = layout;
        this.deck = deck;
    }

    /**
     * Lays the lane in a freighter's layout whose deck's floor is the row given, down the tunnel of her starboard prong
     * round her nose jet, whose relay is given; none where she has no such jet.
     */
    static void build(HullLayout layout, int deck, @Nullable BlockPos jetRelay) {
        if (SkylinersConfig.FREIGHTER_BOWLING.get() && jetRelay != null) {
            new FreighterBowling(layout, deck).lay(jetRelay.getZ() + FIRST_ROW_ASTERN);
        }
    }

    /**
     * Lays the lane from the tunnel's row given: its inner edge there, its end found walking toward her tip while the
     * lane's blocks are open, its boards astern from there as far as they stay open, the rack, and the ball return.
     */
    private void lay(int firstRow) {
        int inner = tunnelEdge(firstRow);
        if (inner < 0) {
            return;
        }
        int from = inner + 1;
        int end = firstRow;
        while (end > firstRow - TIP_REACH && laneOpen(from, end - 1)) {
            end--;
        }
        int rows = 0;
        while (rows < LANE_ROWS && laneOpen(from, end + 1 + rows)) {
            rows++;
        }
        if (rows < LEAST_ROWS) {
            return;
        }
        boards(from, end + 1, rows);
        rack(from, end + 1);
        BlockState ballReturn = SkylinersRegistry.BALL_RETURN.get().defaultBlockState()
            .setValue(BallReturnBlock.MOUNT, Mount.FLOOR).setValue(BallReturnBlock.FACING, Direction.EAST);
        layout.set(from, deck + 1, end + rows, ballReturn);
    }

    /** The lane's boards, its width across from its inner edge, so many rows astern from its first, along her. */
    private void boards(int from, int first, int rows) {
        BlockState board = SkylinersRegistry.BOWLING_LANE.get().defaultBlockState().setValue(BowlingLaneBlock.FACING, Direction.NORTH);
        for (int cell = 0; cell < LANE_WIDTH * rows; cell++) {
            layout.set(from + cell % LANE_WIDTH, deck + 1, first + cell / LANE_WIDTH, board);
        }
    }

    /** The rack of ten on the boards, its back row on the lane's first, its point toward the bowler, facing them. */
    private void rack(int from, int first) {
        BlockState pin = SkylinersRegistry.BOWLING_PIN.get().defaultBlockState()
            .setValue(BowlingPinBlock.FACING, Direction.SOUTH).setValue(BowlingPinBlock.ON_LANE, true);
        for (int row = 0; row < RACK.length; row++) {
            for (int across : RACK[row]) {
                layout.set(from + across, deck + 1, first + row, pin.setValue(BowlingPinBlock.SHIFTED, RACK_SHIFTED[row]));
            }
        }
    }

    /** Whether a row of the lane, across its width from its inner edge, is open over her deck with headroom over it. */
    private boolean laneOpen(int from, int z) {
        for (int across = 0; across < LANE_WIDTH; across++) {
            if (!openInside(from + across, deck + 1, z) || !openInside(from + across, deck + 2, z)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The column of her starboard tunnel nearest her middle line on a row across her, walking out from it: its LED
     * strip, or its first open block where it has none; -1 for none.
     */
    private int tunnelEdge(int z) {
        for (int x = 0; x <= TUNNEL_REACH; x++) {
            if (openInside(x, deck + 1, z) || layout.stateAt(x, deck + 1, z).is(SkylinersRegistry.LED_STRIP.get())) {
                return x;
            }
        }
        return -1;
    }

    /** Whether a block is open and inside her hull, not out past her skin. */
    private boolean openInside(int x, int y, int z) {
        return layout.isOpen(x, y, z) && layout.surface().depth(x, y + 0.5, z) < 0;
    }
}
