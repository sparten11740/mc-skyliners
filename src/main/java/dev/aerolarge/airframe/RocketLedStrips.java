package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.LedColour;
import dev.aerolarge.block.LedConsoleBlock;
import dev.aerolarge.block.LedConsoleBlockEntity;
import dev.aerolarge.block.LedMode;
import dev.aerolarge.block.LedPanelBlock;
import dev.aerolarge.block.LedPanelBlockEntity;
import dev.aerolarge.block.LedSetting;
import dev.aerolarge.block.LedStripBlock;
import dev.aerolarge.block.OvalWindowBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The LED strips round each of the rocket's stories (see {@link LedStripBlock}), with the panel by each story's lift
 * door that sets them and the console in her cockpit that sets any story or all of them. Two rings of strip go round
 * every story along the inside of her skin, one lying on the floor and one fixed under the ceiling, in every open block
 * that the skin stands beside, out of the way of the oval windows and of whatever else stands there; each strip touches
 * the floor or the ceiling face to face, so it is assembled with her. Each story is set as its room asks: snow white at
 * two thirds by default, the greenhouse a full pink grow light, the techno club pulsing pink and ocean blue to its
 * beat, the observation deck a dim ocean blue for the stars. Laid last, so they fill in round every room's fittings.
 */
final class RocketLedStrips implements RocketPiece {
    /** The stories whose rooms set their strips their own way, counted from the entry story up. */
    private static final int GREENHOUSE = 4;
    private static final int TECHNO_CLUB = 6;
    private static final int OBSERVATION_DECK = 8;
    /** A dim strip, a third bright, as the observation deck's are, so the stars outshine them. */
    private static final int DIM = 1;
    /**
     * Where each story's panel stands, from her axis and the story's floor: at eye height, beside the lift's doorway on
     * its west side as she is laid out (the lift's picture board has the east side), its back on the shaft's glass.
     */
    private static final int PANEL_X = -1;
    private static final int PANEL_UP = 2;
    private static final int PANEL_Z = -RocketLayout.SHAFT - 1;
    /**
     * The windows the strips keep clear of: how many rows over its floor a story's windows begin, as the layout sets
     * them, and how many blocks either side of the way each looks out they reach.
     */
    private static final int WINDOW_FROM = 1;
    private static final int WINDOW_HALF = OvalWindowBlock.COLUMNS / 2;
    /** How far over the cockpit's floor the console's bottom row stands, in blocks: on it. */
    private static final int CONSOLE_UP = 1;

    private final RocketLayout layout;

    RocketLedStrips(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted() {
        return AerolargeConfig.LED_STRIPS.get();
    }

    @Override
    public void build() {
        List<Integer> floors = layout.floors();
        List<BlockPos> panels = new ArrayList<>();
        List<Integer> spans = new ArrayList<>();
        for (int story = 0; story < floors.size(); story++) {
            int floor = floors.get(story);
            int ceiling = story + 1 < floors.size() ? floors.get(story + 1) : layout.cockpitFloor();
            BlockPos panel = story(floor, ceiling, setting(story));
            if (panel != null) {
                panels.add(panel);
                spans.add(floor);
                spans.add(ceiling);
            }
        }
        console(panels, spans.stream().mapToInt(Integer::intValue).toArray());
    }

    /** What a story's strips are laid set to, as its room asks. */
    private static LedSetting setting(int story) {
        return switch (story) {
            case GREENHOUSE -> new LedSetting(LedColour.PINK, LedStripBlock.BRIGHTEST, LedMode.STEADY);
            case TECHNO_CLUB -> new LedSetting(LedColour.PINK, LedStripBlock.BRIGHTEST, LedMode.CLUB);
            case OBSERVATION_DECK -> new LedSetting(LedColour.OCEAN, DIM, LedMode.STEADY);
            default -> LedSetting.PLAIN;
        };
    }

    /**
     * A story's two rings of strip and its panel, which is told where they run; where the panel stands, or null where
     * its place is taken, and then the story has no strips either, with nothing to set them.
     */
    @Nullable
    private BlockPos story(int floor, int ceiling, LedSetting setting) {
        BlockPos panel = new BlockPos(PANEL_X, floor + PANEL_UP, PANEL_Z);
        if (!layout.isOpen(panel.getX(), panel.getY(), panel.getZ())) {
            return null;
        }
        List<BlockPos> strips = new ArrayList<>();
        ring(floor, floor + 1, false, setting.brightness(), strips);
        ring(floor, ceiling - 1, true, setting.brightness(), strips);
        BlockPos low = new BlockPos(panel.getX(), floor + 1, panel.getZ());
        BlockPos high = new BlockPos(panel.getX(), ceiling - 1, panel.getZ());
        for (BlockPos strip : strips) {
            low = BlockPos.min(low, strip);
            high = BlockPos.max(high, strip);
        }
        layout.set(panel.getX(), panel.getY(), panel.getZ(), AerolargeRegistry.LED_PANEL.get().defaultBlockState().setValue(LedPanelBlock.FACING, Direction.NORTH));
        BlockPos from = low.subtract(panel);
        BlockPos to = high.subtract(panel);
        layout.fit(panel, (blockEntity, rotation) -> {
            if (blockEntity instanceof LedPanelBlockEntity panelEntity) {
                panelEntity.story(from.rotate(rotation), to.rotate(rotation), setting);
            }
        });
        return panel;
    }

    /** A ring of strip round a story at a row, on its floor or under its ceiling, each strip added to the list. */
    private void ring(int floor, int y, boolean ceiling, int brightness, List<BlockPos> strips) {
        int out = layout.reach();
        int across = 2 * out + 1;
        for (int block = 0; block < across * across; block++) {
            int x = block / across - out;
            int z = block % across - out;
            BlockState strip = strip(floor, x, y, z, ceiling, brightness);
            if (strip != null) {
                layout.set(x, y, z, strip);
                strips.add(new BlockPos(x, y, z));
            }
        }
    }

    /**
     * The strip at a block, running along each side of it the cover of her skin stands on, further out from her axis;
     * null for none: where something stands, in front of a window, where no floor or ceiling holds it, or where the
     * skin stands beside it on no side.
     */
    @Nullable
    private BlockState strip(int floor, int x, int y, int z, boolean ceiling, int brightness) {
        boolean window = y - floor >= WINDOW_FROM && y - floor < WINDOW_FROM + OvalWindowBlock.ROWS
            && (Math.abs(x) <= WINDOW_HALF || Math.abs(z) <= WINDOW_HALF);
        int held = ceiling ? y + 1 : y - 1;
        if (window || !layout.isOpen(x, y, z) || layout.isOpen(x, held, z)) {
            return null;
        }
        BlockState strip = AerolargeRegistry.LED_STRIP.get().defaultBlockState().setValue(LedStripBlock.CEILING, ceiling)
            .setValue(LedStripBlock.BRIGHTNESS, brightness);
        boolean any = false;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            boolean skin = side.getStepX() * x + side.getStepZ() * z > 0 && skinAt(x + side.getStepX(), y, z + side.getStepZ());
            strip = strip.setValue(LedStripBlock.WALLS.get(side), skin);
            any |= skin;
        }
        return any ? strip : null;
    }

    /** Whether the cover of her skin stands at a block: not a window, glass or anything fitted. */
    private boolean skinAt(int x, int y, int z) {
        return layout.stateAt(x, y, z).is(AerolargeRegistry.ZEPPELIN_COVER.get());
    }

    /**
     * The console in the cockpit, on her left against the glass, its middle level with her axis, facing in, as the
     * camera display stands on her right: wired to each story's panel, as offsets from it; none where the glass is not
     * there all across it, or there are no stories.
     */
    private void console(List<BlockPos> panels, int[] spans) {
        int columns = LedConsoleBlock.CONSOLE_COLUMNS;
        int y = layout.cockpitFloor() + CONSOLE_UP;
        // Facing east, the viewer's right is north: the console reaches north and up from its first part.
        int first = columns - 1 - columns / 2;
        OptionalInt x = againstLeftSkin(y, y + LedConsoleBlock.CONSOLE_ROWS - 1, first - columns + 1, first);
        if (panels.isEmpty() || x.isEmpty()) {
            return;
        }
        BlockState console = AerolargeRegistry.LED_CONSOLE.get().defaultBlockState().setValue(LedConsoleBlock.FACING, Direction.EAST);
        for (int part = 0; part < columns * LedConsoleBlock.CONSOLE_ROWS; part++) {
            layout.set(x.getAsInt(), y + part / columns, first - part % columns, console.setValue(LedConsoleBlock.PART, part));
        }
        BlockPos origin = new BlockPos(x.getAsInt(), y, first);
        layout.fit(origin, (blockEntity, rotation) -> {
            if (blockEntity instanceof LedConsoleBlockEntity consoleEntity) {
                consoleEntity.wire(panels.stream().map(panel -> panel.subtract(origin).rotate(rotation)).toList(), spans);
            }
        });
    }

    /**
     * Where something flat standing against the skin on her left, out to the west, goes over a span of rows and of
     * blocks along it, as {@link RocketLayout#againstRightSkin} finds on her right: just inside the skin where it comes
     * furthest in; nothing where the skin is not there across them.
     */
    private OptionalInt againstLeftSkin(int fromY, int toY, int fromZ, int toZ) {
        int along = toZ - fromZ + 1;
        int found = Integer.MIN_VALUE;
        for (int block = 0; block < along * (toY - fromY + 1); block++) {
            OptionalInt x = insideLeftSkin(fromY + block / along, fromZ + block % along);
            if (x.isEmpty()) {
                return OptionalInt.empty();
            }
            found = Math.max(found, x.getAsInt());
        }
        return OptionalInt.of(found);
    }

    /**
     * The last open block going out to the left from beside the lift shaft at a row and a block along, inside the skin.
     */
    private OptionalInt insideLeftSkin(int y, int z) {
        int beside = -RocketLayout.SHAFT - 1;
        for (int x = beside; x >= -layout.reach(); x--) {
            if (!layout.isOpen(x, y, z)) {
                return x < beside ? OptionalInt.of(x + 1) : OptionalInt.empty();
            }
        }
        return OptionalInt.empty();
    }
}
