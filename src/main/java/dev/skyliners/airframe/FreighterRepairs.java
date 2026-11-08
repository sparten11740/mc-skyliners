package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.LedPanelBlockEntity;
import dev.skyliners.block.WideScreenBlock;
import dev.skyliners.repairs.FaultPanelBlock;
import dev.skyliners.repairs.RepairBoardBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The freighter's running repairs (see {@link RepairBoardBlock}): her repair board on the wall of engineering just in
 * through its doorway, where whoever comes in sees its stars, and four fault spots round her rooms for it to set off as
 * she flies, each on a room's side wall part way out, seen as soon as anyone steps in: a junction box in engineering
 * and one in her crew quarters, and a leaky pipe low on the wall of her cargo hold and of her lounge. Each goes on the
 * first padded wall with room nearest where it is meant, before her rooms are furnished, which keep clear of them;
 * none with the server config's {@code freighter.repairs} off. Laid out nose north, as the rest of her.
 */
final class FreighterRepairs {
    /** How wide each room is, round her axis, in degrees. */
    private static final int ROOM_TURN = 60;
    /** Engineering, by the bearing of the room wall on its anticlockwise side, in degrees clockwise from her bow. */
    private static final int ENGINEERING = 150;
    /**
     * Where the board is looked for: so many rows over her deck, at eye height, nearest the point at this bearing and
     * so far out from her axis, in blocks, just in through engineering's doorway beside it; and how far out it may go.
     */
    private static final int BOARD_UP = 2;
    private static final int BOARD_BEARING = 162;
    private static final double BOARD_NEAR = FreighterLayout.CORRIDOR + 2.5;
    private static final double BOARD_TO = FreighterLayout.CORRIDOR + 8;
    /** How many blocks before the board and each fault spot must be open, to stand in and reach it from. */
    private static final int ROOM_BEFORE = 2;
    /** How far out from her axis a fault spot may go, in blocks, and the point it is looked for nearest, so far out. */
    private static final double SPOT_FROM = FreighterLayout.CORRIDOR + 1;
    private static final double SPOT_TO = FreighterLayout.CORRIDOR + 12;
    private static final double SPOT_NEAR = FreighterLayout.CORRIDOR + 6;

    /**
     * A fault spot: the room it is in, by its anticlockwise wall's bearing; the bearing it is looked for nearest; how
     * many rows over her deck it goes; and what it is.
     */
    private record Spot(int room, int bearing, int up, FaultPanelBlock.Kind kind) {
    }

    /** Her fault spots, each a few degrees in from a side wall of its room. */
    private static final Spot[] SPOTS = {
        new Spot(ENGINEERING, 202, 2, FaultPanelBlock.Kind.PANEL),
        new Spot(210, 262, 2, FaultPanelBlock.Kind.PANEL),
        new Spot(330, 338, 1, FaultPanelBlock.Kind.PIPE),
        new Spot(90, 98, 1, FaultPanelBlock.Kind.PIPE),
    };

    private FreighterRepairs() {
    }

    /** Lays her repair board and fault spots in a freighter's layout whose deck's floor is the row given. */
    static void build(HullLayout layout, int deck) {
        if (!SkylinersConfig.FREIGHTER_REPAIRS.get()) {
            return;
        }
        board(layout, deck);
        for (Spot spot : SPOTS) {
            faultSpot(layout, deck, spot);
        }
    }

    /** Hangs the board, two blocks wide, on the wall by engineering's doorway; none where it has no room. */
    private static void board(HullLayout layout, int deck) {
        LedPanelBlockEntity.Wedge room = new LedPanelBlockEntity.Wedge(BlockPos.ZERO, ENGINEERING, ENGINEERING + ROOM_TURN);
        double turn = Math.toRadians(BOARD_BEARING);
        BlockPos origin = FreighterWalls.nearest(layout, deck + BOARD_UP, room, FreighterLayout.CORRIDOR, BOARD_TO, Math.sin(turn) * BOARD_NEAR,
            -Math.cos(turn) * BOARD_NEAR, ROOM_BEFORE, at -> rightFits(layout, at));
        Direction behind = origin == null ? null : FreighterWalls.wallBehind(layout, origin);
        if (behind == null) {
            return;
        }
        Direction facing = behind.getOpposite();
        BlockState board = SkylinersRegistry.REPAIR_BOARD.get().defaultBlockState().setValue(WideScreenBlock.FACING, facing)
            .setValue(WideScreenBlock.TILTED, false);
        for (int part = 0; part < RepairBoardBlock.BOARD_COLUMNS; part++) {
            BlockPos at = origin.relative(WideScreenBlock.right(facing), part);
            layout.set(at.getX(), at.getY(), at.getZ(), board.setValue(WideScreenBlock.PART, part));
        }
    }

    /**
     * Whether the board's second block fits to the right of its first, as it is seen: open, the same padded wall behind
     * it and open before it, and out of every doorway.
     */
    private static boolean rightFits(HullLayout layout, BlockPos at) {
        Direction behind = FreighterWalls.wallBehind(layout, at);
        if (behind == null) {
            return false;
        }
        BlockPos right = at.relative(WideScreenBlock.right(behind.getOpposite()));
        BlockPos wall = right.relative(behind);
        BlockPos before = right.relative(behind.getOpposite());
        return layout.isOpen(right.getX(), right.getY(), right.getZ()) && layout.isOpen(before.getX(), before.getY(), before.getZ())
            && layout.stateAt(wall.getX(), wall.getY(), wall.getZ()).is(SkylinersRegistry.PADDED_BULKHEAD.get())
            && !FreighterFurnishings.inDoorway(before) && !FreighterFurnishings.inDoorway(at.relative(behind.getOpposite()));
    }

    /** Puts up a fault spot on the padded wall with room nearest where it is meant; none where no wall has room. */
    private static void faultSpot(HullLayout layout, int deck, Spot spot) {
        LedPanelBlockEntity.Wedge room = new LedPanelBlockEntity.Wedge(BlockPos.ZERO, spot.room(), spot.room() + ROOM_TURN);
        double turn = Math.toRadians(spot.bearing());
        BlockPos at = FreighterWalls.nearest(layout, deck + spot.up(), room, SPOT_FROM, SPOT_TO, Math.sin(turn) * SPOT_NEAR,
            -Math.cos(turn) * SPOT_NEAR, ROOM_BEFORE, place -> !FreighterFurnishings.inDoorway(place));
        Direction behind = at == null ? null : FreighterWalls.wallBehind(layout, at);
        if (behind != null) {
            layout.set(at.getX(), at.getY(), at.getZ(), block(spot.kind()).setValue(FaultPanelBlock.FACING, behind.getOpposite()));
        }
    }

    /** The block of a fault spot of a kind, all well. */
    private static BlockState block(FaultPanelBlock.Kind kind) {
        return (kind == FaultPanelBlock.Kind.PANEL ? SkylinersRegistry.FAULT_PANEL : SkylinersRegistry.LEAKY_PIPE).get().defaultBlockState();
    }
}
