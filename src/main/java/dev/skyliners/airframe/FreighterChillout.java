package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.SunLoungerBlock;
import dev.skyliners.block.TropicalPieceBlock;
import dev.skyliners.floorwindow.FloorWindowBlock;
import dev.skyliners.floorwindow.FloorWindowBlockEntity;
import dev.skyliners.floorwindow.FloorWindowPanelBlock;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The freighter's beach in her lounge: her huge round floor window (see {@link FloorWindowBlock}) in the middle of a
 * patch of sand, which its shut iris blends into, a round steel shaft under it down to the smoked glass of her skin set
 * into her belly, so the ground is seen passing far below her as she flies; sun loungers round the window facing it, a
 * parasol over one of them, potted palms and tiki torches round the sand, and the window's panel at its edge on the
 * side she is come into from, out of her doorway's way.
 *
 * <p>The window goes where the whole of it has her lounge's floor under it, open over it, and nothing but air between
 * it and her belly's skin under it, nearest a point in the middle of her lounge, before her rooms are furnished, which
 * keep round it; each other piece where it has room on the sand. Laid out nose north, as the rest of her; none with the
 * server config's {@code freighter.beach} off.
 */
final class FreighterChillout {
    /** Her lounge, by the bearing of the room wall on its anticlockwise side, in degrees clockwise from her bow. */
    private static final int LOUNGE = 90;
    /** Where the window's middle is meant to be: at this bearing, in degrees, and so far from her axis, in blocks. */
    private static final double WINDOW_BEARING = 120;
    private static final double WINDOW_OUT = 13.9;
    /** How far the window's middle may be moved from where it is meant, either way, to find it room, in blocks. */
    private static final int NUDGE = 2;
    /** How far past the edge of the window's glass the sand reaches, in blocks. */
    private static final double BEACH = 2.6;
    /** How far past the edge of the window's glass her belly is glazed under it, in blocks, wider than its shaft. */
    private static final double BELLY_MARGIN = 1.5;
    /**
     * Where the beach's pieces go round the window: so far out from its middle, in blocks, and at these angles round
     * it, in degrees clockwise from straight out from her axis, the first that have room, up to so many of each.
     */
    private static final double LOUNGER_OUT = FloorWindowBlock.RADIUS + 1.6;
    private static final int[] LOUNGER_ANGLES = {0, 35, -35, 70, -70, 105, -105};
    private static final int LOUNGERS = 3;
    private static final double PALM_OUT = FloorWindowBlock.RADIUS + 2;
    private static final int[] PALM_ANGLES = {50, -50, 140, -140, 95, -95, 20, -20};
    private static final int PALMS = 4;
    private static final double TORCH_OUT = FloorWindowBlock.RADIUS + 2.1;
    private static final int[] TORCH_ANGLES = {75, -75, 160, -160, 120, -120};
    private static final int TORCHES = 3;
    private static final double PANEL_OUT = FloorWindowBlock.RADIUS + 1.3;
    private static final int[] PANEL_ANGLES = {120, -120, 105, -105, 135, -135, 90, -90, 150, -150, 75, -75, 60, -60, 165, -165};

    private final HullLayout layout;
    private final int deck;
    private final BlockPos middle;
    /** Which way is straight out from her axis through the window's middle, in degrees clockwise from her bow. */
    private final double outward;

    private FreighterChillout(HullLayout layout, int deck, BlockPos middle) {
        this.layout = layout;
        this.deck = deck;
        this.middle = middle;
        this.outward = Math.toDegrees(Math.atan2(middle.getX(), -middle.getZ()));
    }

    /** Lays the beach in the lounge of a freighter's layout whose deck's floor is the row given. */
    static void build(HullLayout layout, int deck) {
        if (!SkylinersConfig.FREIGHTER_BEACH.get()) {
            return;
        }
        BlockPos middle = windowMiddle(layout, deck);
        if (middle == null) {
            return;
        }
        FreighterChillout beach = new FreighterChillout(layout, deck, middle);
        beach.sand();
        beach.window();
        beach.belly();
        BlockPos shaded = beach.around(LOUNGER_OUT, LOUNGER_ANGLES, LOUNGERS, 1,
            facing -> SkylinersRegistry.SUN_LOUNGER.get().defaultBlockState().setValue(SunLoungerBlock.FACING, facing));
        beach.parasolBeside(shaded);
        beach.around(PALM_OUT, PALM_ANGLES, PALMS, 2,
            facing -> SkylinersRegistry.POTTED_PALM.get().defaultBlockState().setValue(TropicalPieceBlock.FACING, facing));
        beach.around(TORCH_OUT, TORCH_ANGLES, TORCHES, 2, facing -> SkylinersRegistry.TIKI_TORCH.get().defaultBlockState());
        beach.panel();
    }

    /** The window's middle block nearest where it is meant to be at which the whole window fits; null for none. */
    @Nullable
    private static BlockPos windowMiddle(HullLayout layout, int deck) {
        double turn = Math.toRadians(WINDOW_BEARING);
        BlockPos meant = new BlockPos((int) Math.round(Math.sin(turn) * WINDOW_OUT), deck, (int) Math.round(-Math.cos(turn) * WINDOW_OUT));
        BlockPos best = null;
        for (BlockPos near : BlockPos.betweenClosed(meant.offset(-NUDGE, 0, -NUDGE), meant.offset(NUDGE, 0, NUDGE))) {
            boolean nearer = best == null || near.distSqr(meant) < best.distSqr(meant);
            if (nearer && windowFits(layout, deck, near)) {
                best = near.immutable();
            }
        }
        return best;
    }

    /** Whether the whole window fits round a middle block: her lounge's floor under every block of it, as below. */
    private static boolean windowFits(HullLayout layout, int deck, BlockPos middle) {
        for (BlockPos offset : FloorWindowBlock.footprint()) {
            BlockPos at = middle.offset(offset);
            boolean floor = floor(layout.stateAt(at.getX(), deck, at.getZ())) && layout.isOpen(at.getX(), deck + 1, at.getZ());
            if (!floor || !inLounge(at) || !clearToBelly(layout, deck, at)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a block is a floor of her deck: her carpet, a room's own floor or the beach's sand. */
    private static boolean floor(BlockState state) {
        return state.is(SkylinersRegistry.CABIN_FLOOR.get()) || FreighterFloors.isRoomFloor(state) || state.is(SkylinersRegistry.BEACH_SAND.get());
    }

    /** Whether nothing but air stands between a block of her deck's floor and her belly's skin under it. */
    private static boolean clearToBelly(HullLayout layout, int deck, BlockPos at) {
        for (int y = deck - 1; y >= 0; y--) {
            BlockState there = layout.stateAt(at.getX(), y, at.getZ());
            if (HullLayout.skin(there)) {
                return true;
            }
            if (!there.isAir()) {
                return false;
            }
        }
        return false;
    }

    private static boolean inLounge(BlockPos at) {
        return FreighterFurnishings.inRoom(LOUNGE, at);
    }

    /** Her lounge's floor turned to sand round the window, as far as the beach reaches. */
    private void sand() {
        int reach = (int) Math.ceil(FloorWindowBlock.RADIUS + BEACH);
        BlockState sand = SkylinersRegistry.BEACH_SAND.get().defaultBlockState();
        for (BlockPos at : BlockPos.betweenClosed(middle.offset(-reach, 0, -reach), middle.offset(reach, 0, reach))) {
            boolean near = Math.hypot(at.getX() - middle.getX(), at.getZ() - middle.getZ()) <= FloorWindowBlock.RADIUS + BEACH;
            if (near && inLounge(at) && floor(layout.stateAt(at.getX(), deck, at.getZ()))) {
                layout.set(at.getX(), deck, at.getZ(), sand);
            }
        }
    }

    /**
     * The window's blocks in her deck's floor, each told where the window's middle is and that it blends into the sand,
     * the row over it kept clear, so nothing her rooms are furnished with after stands on it.
     */
    private void window() {
        BlockState sand = SkylinersRegistry.BEACH_SAND.get().defaultBlockState();
        BlockState block = SkylinersRegistry.FLOOR_WINDOW.get().defaultBlockState();
        int shaft = shaftDepth();
        for (BlockPos offset : FloorWindowBlock.footprint()) {
            BlockPos at = middle.offset(offset);
            BlockPos toMiddle = BlockPos.ZERO.subtract(offset);
            layout.set(at.getX(), deck, at.getZ(), block);
            layout.keepClear(at.getX(), deck + 1, at.getZ());
            layout.fit(at, (blockEntity, rotation) -> configure(blockEntity, toMiddle.rotate(rotation), sand.rotate(rotation), shaft));
        }
    }

    /**
     * Tells a block of the window, once it stands, where the window's middle is, the floor it blends into and how deep
     * its shaft runs down.
     */
    private static void configure(BlockEntity blockEntity, BlockPos toMiddle, BlockState sand, int shaft) {
        if (blockEntity instanceof FloorWindowBlockEntity window) {
            window.configure(toMiddle, sand, sand, shaft);
        }
    }

    /**
     * How deep the window's shaft runs down to her belly, in blocks: through the open air under her deck to the top of
     * the highest of her belly's skin under the window, so it reaches her belly's glass everywhere without passing it.
     */
    private int shaftDepth() {
        int highest = 0;
        for (BlockPos offset : FloorWindowBlock.footprint()) {
            highest = Math.max(highest, bellyTop(middle.offset(offset)));
        }
        return Math.max(0, deck - highest - 1);
    }

    /** The highest of her belly's skin under a block of her deck, its row; none, 0. */
    private int bellyTop(BlockPos at) {
        for (int y = deck - 1; y >= 0; y--) {
            if (HullLayout.skin(layout.stateAt(at.getX(), y, at.getZ()))) {
                return y;
            }
        }
        return 0;
    }

    /**
     * Her belly's skin under the window and a little way round it turned to her smoked glass, to see the ground
     * through, wherever only air lies between it and her deck.
     */
    private void belly() {
        BlockState glass = SkylinersRegistry.SMOKED_GLASS.get().defaultBlockState();
        int reach = (int) Math.ceil(FloorWindowBlock.RADIUS + BELLY_MARGIN);
        for (BlockPos at : BlockPos.betweenClosed(middle.offset(-reach, 0, -reach), middle.offset(reach, 0, reach))) {
            boolean near = Math.hypot(at.getX() - middle.getX(), at.getZ() - middle.getZ()) <= FloorWindowBlock.RADIUS + BELLY_MARGIN;
            if (near && clearToBelly(layout, deck, at)) {
                glazeColumn(at, glass);
            }
        }
    }

    /** Every block of her skin in a column under her deck turned to glass. */
    private void glazeColumn(BlockPos at, BlockState glass) {
        for (int y = deck - 1; y >= 0; y--) {
            if (HullLayout.skin(layout.stateAt(at.getX(), y, at.getZ()))) {
                layout.glaze(at.getX(), y, at.getZ(), glass);
            }
        }
    }

    /**
     * Up to so many of a piece round the window, so far out from its middle, at the first of the angles given with room
     * for it, so many rows of it over the sand, each facing the window; gives where the first went, or null for none.
     */
    @Nullable
    private BlockPos around(double out, int[] angles, int most, int rows, Function<Direction, BlockState> piece) {
        BlockPos first = null;
        int laid = 0;
        for (int angle = 0; angle < angles.length && laid < most; angle++) {
            BlockPos at = roundWindow(out, angles[angle]);
            if (hasRoom(at, rows)) {
                layout.set(at.getX(), at.getY(), at.getZ(), piece.apply(towardWindow(at)));
                first = first == null ? at : first;
                laid++;
            }
        }
        return first;
    }

    /**
     * The window's panel at the edge of the sand, its face away from the window, where there is room to stand behind it
     * and look over it at the window.
     */
    private void panel() {
        for (int angle : PANEL_ANGLES) {
            BlockPos at = roundWindow(PANEL_OUT, angle);
            Direction face = towardWindow(at).getOpposite();
            if (hasRoom(at, 1) && canStand(at.relative(face))) {
                layout.set(at.getX(), at.getY(), at.getZ(), SkylinersRegistry.FLOOR_WINDOW_PANEL.get().defaultBlockState()
                    .setValue(FloorWindowPanelBlock.FACING, face));
                return;
            }
        }
    }

    /** Whether someone can stand at a block: her deck's floor under it, two rows open over it, in the lounge. */
    private boolean canStand(BlockPos at) {
        boolean open = layout.isOpen(at.getX(), at.getY(), at.getZ()) && layout.isOpen(at.getX(), at.getY() + 1, at.getZ());
        return open && floor(layout.stateAt(at.getX(), deck, at.getZ())) && inLounge(at);
    }

    /** A parasol beside a lounger, to its side as it faces, where it has room; none for no lounger. */
    private void parasolBeside(@Nullable BlockPos lounger) {
        if (lounger == null) {
            return;
        }
        Direction facing = towardWindow(lounger);
        for (Direction side : new Direction[]{facing.getClockWise(), facing.getCounterClockWise()}) {
            BlockPos at = lounger.relative(side);
            if (hasRoom(at, 2)) {
                BlockState parasol = SkylinersRegistry.PARASOL.get().defaultBlockState().setValue(TropicalPieceBlock.FACING, facing);
                layout.set(at.getX(), at.getY(), at.getZ(), parasol);
                return;
            }
        }
    }

    /** The block over her deck's floor so far out from the window's middle at an angle round it from straight out. */
    private BlockPos roundWindow(double out, double angle) {
        double turn = Math.toRadians(outward + angle);
        int right = (int) Math.round(Math.sin(turn) * out);
        int astern = (int) Math.round(-Math.cos(turn) * out);
        return new BlockPos(middle.getX() + right, deck + 1, middle.getZ() + astern);
    }

    /** Whether a piece has room at a block: on the sand, rows open over it, in the lounge, out of the doorway. */
    private boolean hasRoom(BlockPos at, int rows) {
        boolean onSand = layout.stateAt(at.getX(), deck, at.getZ()).is(SkylinersRegistry.BEACH_SAND.get());
        if (!onSand || !inLounge(at) || FreighterFurnishings.inDoorway(at)) {
            return false;
        }
        for (int row = 0; row < rows; row++) {
            if (!layout.isOpen(at.getX(), at.getY() + row, at.getZ())) {
                return false;
            }
        }
        return true;
    }

    /** The way from a block toward the window's middle, the way a piece round it faces. */
    private Direction towardWindow(BlockPos at) {
        return Direction.getNearest(middle.getX() - at.getX(), 0, middle.getZ() - at.getZ());
    }
}
