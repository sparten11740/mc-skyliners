package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.LedColour;
import dev.skyliners.block.LedMode;
import dev.skyliners.block.LedPanelBlock;
import dev.skyliners.block.LedPanelBlockEntity;
import dev.skyliners.block.LedSetting;
import dev.skyliners.block.LedStripBlock;
import dev.skyliners.block.ShellBlock;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The freighter's LED strips (see {@link LedStripBlock}), the rocket's, a ring of them round each of her six rooms
 * lying on her deck where it meets the room's walls, each room in a colour and a way of its own, so anyone stepping in
 * knows at once where they are, and an LED panel on the wall by each room's doorway (see {@link LedPanelBlock}) to set
 * them, as the rocket's stories have: her cargo hold sunshine yellow, chasing as a warehouse's guide lights do, so it
 * differs from her lounge by more than its colour; her entry hall snow white; her lounge a warm
 * tangerine; engineering sky blue, chasing round it as power does; her crew quarters a dim grape, breathing like a night
 * light; and her club pink, pulsing to the beat. Each room is a wedge round her axis between two of her room walls, so
 * its panel is told the wedge as well as the box round its strips, which overlaps its neighbours'.
 *
 * <p>A strip goes in every open block over her deck's floor in a room, past the open floor about her core, that has her
 * padded wall or her shell beside it on a side, and runs along those sides; none where something stands. Laid after
 * her rooms are furnished, so it fills in round their fittings. Laid out nose north, as the rest of her; none with the
 * server config's {@code freighter.ledStrips} off.
 */
final class FreighterLedStrips {
    /** How wide each room is, round her axis, in degrees: one of six. */
    private static final int ROOM_TURN = 60;
    /** How bright a strip is two thirds up, as the rocket's cargo bay's; and dim, as her bunk room's. */
    private static final int TWO_THIRDS = 2;
    private static final int DIM = 1;
    /**
     * Her rooms, each from the bearing of the room wall on its anticlockwise side, in degrees clockwise from her bow,
     * and what its strips are laid set to.
     */
    private static final List<Room> ROOMS = List.of(
        new Room(330, new LedSetting(LedColour.SUNSHINE, TWO_THIRDS, LedMode.CHASE)),
        new Room(30, new LedSetting(LedColour.SNOW, TWO_THIRDS, LedMode.STEADY)),
        new Room(90, new LedSetting(LedColour.TANGERINE, LedStripBlock.BRIGHTEST, LedMode.STEADY)),
        new Room(150, new LedSetting(LedColour.SKY, LedStripBlock.BRIGHTEST, LedMode.CHASE)),
        new Room(210, new LedSetting(LedColour.GRAPE, DIM, LedMode.BREATHE)),
        new Room(270, new LedSetting(LedColour.PINK, LedStripBlock.BRIGHTEST, LedMode.CLUB)));
    /**
     * Where a room's panel may go: so many rows over her deck, at eye height, and from so far out from her axis to so
     * far, just in through its doorway, the nearest to the middle of the doorway taken.
     */
    private static final int PANEL_UP = 2;
    private static final double PANEL_FROM = FreighterLayout.CORRIDOR;
    private static final double PANEL_TO = FreighterLayout.CORRIDOR + 3;
    /** How far out from her axis the middle of a room's doorway is looked for its panel from, in blocks. */
    private static final double DOORWAY = FreighterLayout.CORRIDOR + 1;

    /** A room: the bearing of its anticlockwise wall, in degrees, and what its strips are laid set to. */
    private record Room(int from, LedSetting setting) {
        int to() {
            return from + ROOM_TURN;
        }

        int middle() {
            return from + ROOM_TURN / 2;
        }

        /** The wedge it lies in round her axis, in her layout. */
        LedPanelBlockEntity.Wedge wedge() {
            return new LedPanelBlockEntity.Wedge(BlockPos.ZERO, from, to());
        }
    }

    private final HullLayout layout;
    private final int deck;
    private final int reach;

    private FreighterLedStrips(HullLayout layout, int deck, int reach) {
        this.layout = layout;
        this.deck = deck;
        this.reach = reach;
    }

    /** Lays the strips and panels in a freighter's layout whose deck's floor is the row given, reaching so far out. */
    static void build(HullLayout layout, int deck, int reach) {
        if (SkylinersConfig.FREIGHTER_LED_STRIPS.get()) {
            FreighterLedStrips strips = new FreighterLedStrips(layout, deck, reach);
            ROOMS.forEach(strips::room);
        }
    }

    /** A room's ring of strips and its panel; neither where the panel has no place, as nothing would set them. */
    private void room(Room room) {
        BlockPos panel = panelSpot(room);
        if (panel == null) {
            return;
        }
        List<BlockPos> strips = new ArrayList<>();
        LedPanelBlockEntity.Wedge wedge = room.wedge();
        int across = 2 * reach + 1;
        for (int block = 0; block < across * across; block++) {
            BlockPos at = new BlockPos(block / across - reach, deck + 1, block % across - reach);
            BlockState strip = inRoom(wedge, at) ? strip(at, room.setting().brightness()) : null;
            if (strip != null) {
                layout.set(at.getX(), at.getY(), at.getZ(), strip);
                strips.add(at);
            }
        }
        if (!strips.isEmpty()) {
            setPanel(room, panel, strips);
        }
    }

    /** Whether a block lies in a room: in its wedge, past the open floor about her core. */
    private static boolean inRoom(LedPanelBlockEntity.Wedge wedge, BlockPos at) {
        return Math.hypot(at.getX(), at.getZ()) >= FreighterLayout.CORRIDOR && wedge.contains(at);
    }

    /**
     * The strip at a block, running along each side of it that her wall stands on; null for none: where something
     * stands, where no floor holds it, or where no wall stands beside it.
     */
    @Nullable
    private BlockState strip(BlockPos at, int brightness) {
        if (!layout.isOpen(at.getX(), at.getY(), at.getZ()) || layout.isOpen(at.getX(), deck, at.getZ())) {
            return null;
        }
        BlockState strip = SkylinersRegistry.LED_STRIP.get().defaultBlockState().setValue(LedStripBlock.CEILING, false)
            .setValue(LedStripBlock.BRIGHTNESS, brightness);
        boolean any = false;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            boolean wall = wallAt(at.relative(side));
            strip = strip.setValue(LedStripBlock.WALLS.get(side), wall);
            any |= wall;
        }
        return any ? strip : null;
    }

    /** Whether her wall stands at a block: her padded walls or her shell, not anything fitted. */
    private boolean wallAt(BlockPos at) {
        BlockState wall = layout.stateAt(at.getX(), at.getY(), at.getZ());
        return wall.is(SkylinersRegistry.PADDED_BULKHEAD.get()) || wall.getBlock() instanceof ShellBlock;
    }

    /**
     * Where a room's panel goes: an open block at eye height just in through its doorway, her padded wall behind it on
     * one side and open before it on the other, nearest to the middle of the doorway; null for none.
     */
    @Nullable
    private BlockPos panelSpot(Room room) {
        double turn = Math.toRadians(room.middle());
        double doorX = Math.sin(turn) * DOORWAY;
        double doorZ = -Math.cos(turn) * DOORWAY;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int far = (int) Math.ceil(PANEL_TO);
        for (int block = 0; block < (2 * far + 1) * (2 * far + 1); block++) {
            BlockPos at = new BlockPos(block / (2 * far + 1) - far, deck + PANEL_UP, block % (2 * far + 1) - far);
            double distance = Math.hypot(at.getX() + 0.5 - doorX, at.getZ() + 0.5 - doorZ);
            if (distance < bestDistance && panelFits(room, at)) {
                best = at;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Whether a room's panel fits at a block: in the room just in through its doorway, against her wall, open before. */
    private boolean panelFits(Room room, BlockPos at) {
        double out = Math.hypot(at.getX(), at.getZ());
        boolean near = out >= PANEL_FROM && out <= PANEL_TO && inRoom(room.wedge(), at);
        return near && layout.isOpen(at.getX(), at.getY(), at.getZ()) && wallBehind(at) != null;
    }

    /** The side of a block her padded wall stands on, with the block on the side opposite open; null for none. */
    @Nullable
    private Direction wallBehind(BlockPos at) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos before = at.relative(side.getOpposite());
            BlockPos behind = at.relative(side);
            boolean wall = layout.stateAt(behind.getX(), behind.getY(), behind.getZ()).is(SkylinersRegistry.PADDED_BULKHEAD.get());
            if (wall && layout.isOpen(before.getX(), before.getY(), before.getZ())) {
                return side;
            }
        }
        return null;
    }

    /** Sets the panel against its wall, facing into the room, and tells it its room once she stands. */
    private void setPanel(Room room, BlockPos panel, List<BlockPos> strips) {
        Direction behind = wallBehind(panel);
        layout.set(panel.getX(), panel.getY(), panel.getZ(),
            SkylinersRegistry.LED_PANEL.get().defaultBlockState().setValue(LedPanelBlock.FACING, behind.getOpposite()));
        BlockPos low = strips.get(0);
        BlockPos high = strips.get(0);
        for (BlockPos strip : strips) {
            low = BlockPos.min(low, strip);
            high = BlockPos.max(high, strip);
        }
        BlockPos from = low.subtract(panel);
        BlockPos to = high.subtract(panel);
        BlockPos axis = new BlockPos(0, deck + 1, 0).subtract(panel);
        layout.fit(panel, (blockEntity, rotation) -> tell(blockEntity, rotation, from, to, axis, room));
    }

    /** Tells a room's panel its box, the axis its wedge lies round and its bearings, all turned as she was placed. */
    private static void tell(BlockEntity blockEntity, Rotation rotation, BlockPos from, BlockPos to, BlockPos axis, Room room) {
        if (blockEntity instanceof LedPanelBlockEntity panel) {
            float bearingFrom = LedPanelBlockEntity.Wedge.turned(room.from(), rotation);
            float bearingTo = LedPanelBlockEntity.Wedge.turned(room.to(), rotation);
            panel.room(from.rotate(rotation), to.rotate(rotation), axis.rotate(rotation), bearingFrom, bearingTo, room.setting());
        }
    }
}
