package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.LedPanelBlockEntity;
import dev.skyliners.block.Mount;
import dev.skyliners.block.RubberDuckBlock;
import dev.skyliners.zerog.GravitySwitchBlock;
import dev.skyliners.zerog.GravitySwitchBlockEntity;
import java.util.List;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The freighter's zero-gravity club (see {@link GravitySwitchBlock}): a gravity switch on the wall just in through her
 * club's doorway, at the eyes of whoever comes in, beside its LED panel, told the club as its room once she stands,
 * the club's wedge between her room walls from her ring corridor out to her outer bulkhead and from her deck up to its
 * ceiling; and a rubber duck on each of the club's cocktail tables, to drift up off it while gravity is off. Laid after her rooms are furnished and their LED panels are up, so it takes a place none of them wanted.
 * Laid out nose north, as the rest of her; none with the server config's {@code freighter.zeroGravity} off.
 */
final class FreighterZeroGravity {
    /** Her club, by the bearing of the room wall on its anticlockwise side, in degrees clockwise from her bow, and its width. */
    private static final int CLUB = 270;
    private static final int ROOM_TURN = 60;
    /**
     * Where the switch may go: so many rows over her deck, at eye height, and from so far out from her axis to so far,
     * just in through the club's doorway, the nearest to the middle of the doorway taken.
     */
    private static final int SWITCH_UP = 2;
    private static final double SWITCH_FROM = FreighterLayout.CORRIDOR;
    private static final double SWITCH_TO = FreighterLayout.CORRIDOR + 3;
    /** How far out from her axis the middle of the club's doorway is looked for the switch from, in blocks. */
    private static final double DOORWAY = FreighterLayout.CORRIDOR + 1;
    /** How many rows over her deck the club's ceiling is looked for in, at most. */
    private static final int MOST_ROWS = 12;

    private final HullLayout layout;
    private final int deck;
    private final int reach;
    private final int ahead;
    private final LedPanelBlockEntity.Wedge club = new LedPanelBlockEntity.Wedge(BlockPos.ZERO, CLUB, CLUB + ROOM_TURN);

    private FreighterZeroGravity(HullLayout layout, int deck, int reach, int ahead) {
        this.layout = layout;
        this.deck = deck;
        this.reach = reach;
        this.ahead = ahead;
    }

    /**
     * Lays the switch and the ducks in a freighter's layout whose deck's floor is the row given, reaching so far out to
     * either side and astern, and so far ahead.
     */
    static void build(HullLayout layout, int deck, int reach, int ahead) {
        if (!SkylinersConfig.FREIGHTER_ZERO_GRAVITY.get()) {
            return;
        }
        FreighterZeroGravity zeroGravity = new FreighterZeroGravity(layout, deck, reach, ahead);
        zeroGravity.ducks();
        zeroGravity.gravitySwitch();
    }

    /** The switch on the club's wall by its doorway, told its room once she stands; none where no wall there has room. */
    private void gravitySwitch() {
        double turn = Math.toRadians(CLUB + ROOM_TURN / 2.0);
        BlockPos at = FreighterWalls.nearest(layout, deck + SWITCH_UP, club, SWITCH_FROM, SWITCH_TO, Math.sin(turn) * DOORWAY,
            -Math.cos(turn) * DOORWAY, 1);
        BlockPos[] box = at == null ? null : roomBox();
        if (box == null) {
            return;
        }
        Direction behind = FreighterWalls.wallBehind(layout, at);
        layout.set(at.getX(), at.getY(), at.getZ(), SkylinersRegistry.GRAVITY_SWITCH.get().defaultBlockState()
            .setValue(GravitySwitchBlock.MOUNT, Mount.WALL).setValue(GravitySwitchBlock.FACING, behind.getOpposite()));
        BlockPos from = box[0].subtract(at);
        BlockPos to = box[1].subtract(at);
        BlockPos axis = new BlockPos(0, deck + 1, 0).subtract(at);
        layout.fit(at, (blockEntity, rotation) -> tell(blockEntity, rotation, from, to, axis));
    }

    /**
     * The corners of the box round the club, lowest first: every block of it standing on her floor, past the ring
     * corridor and inside her outer bulkhead, and up each of those to her ceiling; null for none.
     */
    @Nullable
    private BlockPos[] roomBox() {
        BlockPos low = null;
        BlockPos high = null;
        for (BlockPos at : columns()) {
            if (standsInClub(at)) {
                BlockPos top = at.above(openAbove(at.getX(), at.getZ()));
                low = low == null ? at : BlockPos.min(low, at);
                high = high == null ? top : BlockPos.max(high, top);
            }
        }
        return low == null ? null : new BlockPos[]{low, high};
    }

    /** Every block of the row over her deck within her layout's reach, as far as her rooms might reach. */
    private List<BlockPos> columns() {
        int across = 2 * reach + 1;
        int along = reach - ahead + 1;
        return IntStream.range(0, across * along).mapToObj(block -> new BlockPos(block / along - reach, deck + 1, block % along + ahead)).toList();
    }

    /**
     * Whether a block of the row over her deck is open in the club, standing on her floor, past the ring corridor and
     * inside her outer bulkhead.
     */
    private boolean standsInClub(BlockPos at) {
        boolean inClub = FreighterFurnishings.inRoom(CLUB, at) && Math.hypot(at.getX(), at.getZ()) <= FreighterLayout.BULKHEAD * FreighterLayout.SCALE;
        return inClub && !layout.isOpen(at.getX(), deck, at.getZ()) && layout.isOpen(at.getX(), at.getY(), at.getZ());
    }

    /** How many rows over the first row over her deck are open, up to her ceiling, at a column. */
    private int openAbove(int x, int z) {
        int rows = 0;
        while (rows < MOST_ROWS && layout.isOpen(x, deck + 2 + rows, z)) {
            rows++;
        }
        return rows;
    }

    /** A rubber duck on each of the club's cocktail tables with room over it, facing toward her axis. */
    private void ducks() {
        for (BlockPos table : columns()) {
            boolean bare = layout.stateAt(table.getX(), table.getY(), table.getZ()).is(SkylinersRegistry.COCKTAIL_TABLE.get())
                && layout.isOpen(table.getX(), table.getY() + 1, table.getZ());
            if (bare && FreighterFurnishings.inRoom(CLUB, table)) {
                layout.set(table.getX(), table.getY() + 1, table.getZ(), SkylinersRegistry.RUBBER_DUCK.get().defaultBlockState()
                    .setValue(RubberDuckBlock.FACING, Direction.getNearest(-table.getX(), 0, -table.getZ())));
            }
        }
    }

    /** Tells the switch its room: its box, the axis the club lies round and its bearings, all turned as she was placed. */
    private static void tell(BlockEntity blockEntity, Rotation rotation, BlockPos from, BlockPos to, BlockPos axis) {
        if (blockEntity instanceof GravitySwitchBlockEntity gravitySwitch) {
            gravitySwitch.room(from.rotate(rotation), to.rotate(rotation), axis.rotate(rotation),
                LedPanelBlockEntity.Wedge.turned(CLUB, rotation), LedPanelBlockEntity.Wedge.turned(CLUB + ROOM_TURN, rotation),
                (float) FreighterLayout.CORRIDOR);
        }
    }
}
