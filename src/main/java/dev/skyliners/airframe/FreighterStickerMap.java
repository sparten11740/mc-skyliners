package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.LedPanelBlockEntity;
import dev.skyliners.block.WideScreenBlock;
import dev.skyliners.stickers.StickerMapBlock;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The freighter's sticker map (see {@link StickerMapBlock}), on the wall of the room where her ramp comes up, so
 * whoever comes aboard sees it first: four blocks wide and two tall, from a block over her deck up, on a straight run
 * of her padded wall with room before it to stand in and see it from, nearest a point on the wall between her entry
 * hall and her lounge; in her lounge, on its side of that wall, where the entry hall has no room. Laid before her rooms
 * are furnished, which keep clear of it, and none with the server config's {@code freighter.stickerMap} off. Laid out
 * nose north, as the rest of her.
 */
final class FreighterStickerMap {
    /** Her entry hall and her lounge, each by the bearing of the room wall on its anticlockwise side, in degrees. */
    private static final int[] ROOMS = {30, 90};
    /** How wide each room is, round her axis, in degrees. */
    private static final int ROOM_TURN = 60;
    /**
     * Where the board is looked for: nearest a point on the wall between the two rooms, along her starboard beam, so
     * far out from her axis, in blocks; and so far from her axis at most, past which her hull curves in overhead.
     */
    private static final double TOWARD_OUT = 14;
    private static final double FARTHEST = 22;
    /** The rows over her deck the board's bottom row is tried in, the one that is easiest to see first. */
    private static final int[] BOTTOM_ROWS = {2, 1};
    /** How many blocks before each of the board's blocks must be open, to stand in and see it from. */
    private static final int ROOM_BEFORE = 2;

    private FreighterStickerMap() {
    }

    /** Hangs the board in a freighter's layout whose deck's floor is the row given. */
    static void build(HullLayout layout, int deck) {
        if (!SkylinersConfig.FREIGHTER_STICKER_MAP.get()) {
            return;
        }
        Spot spot = spot(layout, deck);
        if (spot != null) {
            hang(layout, spot);
        }
    }

    /** Where the board goes: the first room and row with room for it, in the order they are tried; null for none. */
    @Nullable
    private static Spot spot(HullLayout layout, int deck) {
        for (int tried = 0; tried < ROOMS.length * BOTTOM_ROWS.length; tried++) {
            Spot spot = nearest(layout, deck + BOTTOM_ROWS[tried % BOTTOM_ROWS.length], ROOMS[tried / BOTTOM_ROWS.length]);
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    /** Where the board's part 0, its bottom left as it is seen, goes, and the way it faces. */
    private record Spot(BlockPos origin, Direction facing) {
    }

    /** The spot in a room with its bottom row in the row given nearest the point it is looked for at; null for none. */
    @Nullable
    private static Spot nearest(HullLayout layout, int y, int room) {
        LedPanelBlockEntity.Wedge wedge = new LedPanelBlockEntity.Wedge(BlockPos.ZERO, room, room + ROOM_TURN);
        int far = (int) Math.ceil(FARTHEST);
        int across = 2 * far + 1;
        Spot best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int block = 0; block < across * across; block++) {
            Spot spot = fits(layout, wedge, new BlockPos(block / across - far, y, block % across - far));
            double distance = spot == null ? Double.MAX_VALUE : Math.hypot(middleX(spot) - TOWARD_OUT, middleZ(spot));
            if (distance < bestDistance) {
                best = spot;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Where the middle of a board at a spot is, across her and along her, in blocks from her axis. */
    private static double middleX(Spot spot) {
        Direction right = WideScreenBlock.right(spot.facing());
        return spot.origin().getX() + 0.5 + right.getStepX() * (StickerMapBlock.BOARD_COLUMNS - 1) / 2.0;
    }

    private static double middleZ(Spot spot) {
        Direction right = WideScreenBlock.right(spot.facing());
        return spot.origin().getZ() + 0.5 + right.getStepZ() * (StickerMapBlock.BOARD_COLUMNS - 1) / 2.0;
    }

    /** The spot with its part 0 at a block, facing whichever way the whole board fits; null where it fits no way. */
    @Nullable
    private static Spot fits(HullLayout layout, LedPanelBlockEntity.Wedge room, BlockPos origin) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Spot spot = new Spot(origin, facing);
            if (fitsFacing(layout, room, spot)) {
                return spot;
            }
        }
        return null;
    }

    /**
     * Whether every block of the board fits at a spot: in the room, open, her padded wall behind it, open before it.
     */
    private static boolean fitsFacing(HullLayout layout, LedPanelBlockEntity.Wedge room, Spot spot) {
        for (int part = 0; part < StickerMapBlock.BOARD_COLUMNS * StickerMapBlock.BOARD_ROWS; part++) {
            if (!partFits(layout, room, part(spot, part), spot.facing())) {
                return false;
            }
        }
        return true;
    }

    private static boolean partFits(HullLayout layout, LedPanelBlockEntity.Wedge room, BlockPos at, Direction facing) {
        double out = Math.hypot(at.getX() + 0.5, at.getZ() + 0.5);
        if (out < FreighterLayout.CORRIDOR || out > FARTHEST || !room.contains(at) || !layout.isOpen(at.getX(), at.getY(), at.getZ())) {
            return false;
        }
        BlockPos behind = at.relative(facing.getOpposite());
        if (!layout.stateAt(behind.getX(), behind.getY(), behind.getZ()).is(SkylinersRegistry.PADDED_BULKHEAD.get())) {
            return false;
        }
        for (int step = 1; step <= ROOM_BEFORE; step++) {
            BlockPos before = at.relative(facing, step);
            if (!layout.isOpen(before.getX(), before.getY(), before.getZ()) || FreighterFurnishings.inDoorway(before)) {
                return false;
            }
        }
        return true;
    }

    /** Where a part of the board at a spot is, counting along its bottom row from the viewer's left and then up. */
    private static BlockPos part(Spot spot, int part) {
        return spot.origin().relative(WideScreenBlock.right(spot.facing()), part % StickerMapBlock.BOARD_COLUMNS)
            .above(part / StickerMapBlock.BOARD_COLUMNS);
    }

    /** Sets every part of the board at a spot. */
    private static void hang(HullLayout layout, Spot spot) {
        BlockState board = SkylinersRegistry.STICKER_MAP.get().defaultBlockState().setValue(WideScreenBlock.FACING, spot.facing())
            .setValue(WideScreenBlock.TILTED, false);
        for (int part = 0; part < StickerMapBlock.BOARD_COLUMNS * StickerMapBlock.BOARD_ROWS; part++) {
            BlockPos at = part(spot, part);
            layout.set(at.getX(), at.getY(), at.getZ(), board.setValue(WideScreenBlock.PART, part));
        }
    }
}
