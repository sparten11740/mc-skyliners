package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.engineering.BreakerBoardBlock;
import dev.skyliners.engineering.BreakerBoardBlockEntity;
import dev.skyliners.engineering.CoolantValveBlock;
import dev.skyliners.engineering.JumpDriveBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The freighter's engineering, the room astern of her core: her jump drive in its middle (see {@link JumpDriveBlock}),
 * facing in toward its doorway so whoever comes in meets its panel, with room all round it to watch its rings turn; and
 * on the wall behind it, at eye height, her breaker board (see {@link BreakerBoardBlock}), wired to each of her rooms'
 * LED panels, laid after them, and further along it her coolant valve (see {@link CoolantValveBlock}). Each is laid
 * where its block has room, the drive with her deck's floor under it and three rows open over it and the row before it
 * open to stand in, the board against her padded wall with the room open before it. Laid out nose north, as the rest of
 * her; none with the server config's {@code freighter.engineering} off.
 */
final class FreighterEngineering {
    /** Where her jump drive stands: astern, so far out from her axis, in blocks, in the middle of the room. */
    private static final int DRIVE_OUT = 13;
    /**
     * Where her breaker board may go: so many rows over her deck, at eye height, and from so far out from her axis to
     * so far, astern, the nearest to straight behind the drive taken.
     */
    private static final int BOARD_UP = 2;
    private static final int BOARD_FROM = DRIVE_OUT + 2;
    private static final int BOARD_TO = DRIVE_OUT + 8;
    /** How far either side of her middle line the board may go, in blocks. */
    private static final int BOARD_ASIDE = 4;
    /** How far to one side of her middle line the valve may go, in blocks, in turn, east positive. */
    private static final int[] VALVE_ASIDES = {2, -2, 3, -3, 1, -1, 4, -4};

    private FreighterEngineering() {
    }

    /** Lays engineering's machines in a freighter's layout whose deck's floor is the row given. */
    static void build(HullLayout layout, int deck) {
        if (!SkylinersConfig.FREIGHTER_ENGINEERING.get()) {
            return;
        }
        BlockPos drive = driveAt(deck);
        if (standsFree(layout, deck, drive) && standsFree(layout, deck, drive.north())) {
            layout.set(drive.getX(), drive.getY(), drive.getZ(),
                SkylinersRegistry.JUMP_DRIVE.get().defaultBlockState().setValue(JumpDriveBlock.FACING, Direction.NORTH));
        }
        valveSpots(deck).stream().filter(at -> onWall(layout, at)).findFirst().ifPresent(at -> layout.set(at.getX(), at.getY(), at.getZ(),
            SkylinersRegistry.COOLANT_VALVE.get().defaultBlockState().setValue(CoolantValveBlock.FACING, Direction.NORTH)));
    }

    /**
     * Where her jump drive stands, as she is laid out, in a freighter whose deck's floor is the row given; there only
     * where her engineering has its machines and room for it, which whoever reads it finds by what stands there.
     */
    static BlockPos driveAt(int deck) {
        return new BlockPos(0, deck + 1, DRIVE_OUT);
    }

    /**
     * Where the valve is looked for on her stern wall, in turn: furthest astern first, where her wall is, and at each
     * distance a little to one side of her middle line or the other, leaving the middle to the board, before further.
     */
    private static List<BlockPos> valveSpots(int deck) {
        List<BlockPos> spots = new ArrayList<>();
        for (int spot = 0; spot < (BOARD_TO - BOARD_FROM + 1) * VALVE_ASIDES.length; spot++) {
            spots.add(new BlockPos(VALVE_ASIDES[spot % VALVE_ASIDES.length], deck + BOARD_UP, BOARD_TO - spot / VALVE_ASIDES.length));
        }
        return spots;
    }

    /**
     * Sets her breaker board against the wall behind the drive, facing in, and wires it to each room's LED panel once
     * she stands; none where the room's panels are not laid, as with her strips switched off, or no wall has room.
     */
    static void breakerBoard(HullLayout layout, int deck, List<BlockPos> panels) {
        if (!SkylinersConfig.FREIGHTER_ENGINEERING.get() || panels.stream().allMatch(Objects::isNull)) {
            return;
        }
        for (BlockPos at : wallSpots(deck)) {
            if (onWall(layout, at)) {
                setBoard(layout, at, panels);
                return;
            }
        }
    }

    /**
     * Where the board, and the valve the other way about, are looked for on her stern wall, in turn: astern of the
     * drive, nearest first, and at each distance on her middle line, then further out either side.
     */
    private static List<BlockPos> wallSpots(int deck) {
        List<BlockPos> spots = new ArrayList<>();
        int across = 2 * BOARD_ASIDE + 1;
        for (int spot = 0; spot < (BOARD_TO - BOARD_FROM + 1) * across; spot++) {
            int step = spot % across;
            int aside = step % 2 == 0 ? step / 2 : -(step + 1) / 2;
            spots.add(new BlockPos(aside, deck + BOARD_UP, BOARD_FROM + spot / across));
        }
        return spots;
    }

    /**
     * Whether something hung on her wall fits at a block: open, her padded wall astern of it, and open before it,
     * toward her bow.
     */
    private static boolean onWall(HullLayout layout, BlockPos at) {
        BlockPos wall = at.south();
        BlockPos before = at.north();
        return layout.isOpen(at.getX(), at.getY(), at.getZ()) && layout.isOpen(before.getX(), before.getY(), before.getZ())
            && layout.stateAt(wall.getX(), wall.getY(), wall.getZ()).is(SkylinersRegistry.PADDED_BULKHEAD.get());
    }

    /** Sets the board, facing toward her bow, and wires it to the rooms' panels once she stands. */
    private static void setBoard(HullLayout layout, BlockPos at, List<BlockPos> panels) {
        layout.set(at.getX(), at.getY(), at.getZ(),
            SkylinersRegistry.BREAKER_BOARD.get().defaultBlockState().setValue(BreakerBoardBlock.FACING, Direction.NORTH));
        List<BlockPos> offsets = panels.stream().map(panel -> panel == null ? null : panel.subtract(at)).toList();
        layout.fit(at, (blockEntity, rotation) -> wire(blockEntity, rotation, offsets));
    }

    private static void wire(BlockEntity blockEntity, Rotation rotation, List<BlockPos> offsets) {
        if (blockEntity instanceof BreakerBoardBlockEntity board) {
            board.wire(offsets, rotation);
        }
    }

    /** Whether a block over her deck's floor has the floor under it and three rows open from it up. */
    private static boolean standsFree(HullLayout layout, int deck, BlockPos at) {
        boolean open = layout.isOpen(at.getX(), deck + 1, at.getZ()) && layout.isOpen(at.getX(), deck + 2, at.getZ())
            && layout.isOpen(at.getX(), deck + 3, at.getZ());
        return open && !layout.isOpen(at.getX(), deck, at.getZ());
    }
}
