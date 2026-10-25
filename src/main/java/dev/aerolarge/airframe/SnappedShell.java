package dev.aerolarge.airframe;

import static dev.aerolarge.airframe.AirframeShape.*;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.CabinWindowBlock;
import dev.aerolarge.block.CockpitGlassBlock;
import dev.aerolarge.block.ShellBlock;
import dev.aerolarge.block.ShellGeometry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The fuselage's skin cut to its curve in the snapped style ({@link ShellStyle#SNAPPED}): every block is cut by
 * {@link ShellGeometry#snapped} from where its corners lie against the hull's surface, and two blocks sharing a side
 * share its corners, so the cuts run into each other without a step. Every block whose corners leave something of it
 * within the surface gets that much, whatever it was: the skin is cut, the open air just outside gets a sliver of
 * skin, and so does cabin air that reaches out past the surface at a corner, which would otherwise leave a gap in the
 * skin. In the nose the inside of the skin is cut to the curve the same way, by a second surface a skin's thickness
 * in from the first, so the flight deck's walls, ceiling and windscreen are round from inside too.
 */
final class SnappedShell {
    /**
     * How thick the nose's skin is, in blocks, where its inside is cut to the curve too: the inner surface lies this
     * far in from the outer. Thick enough that no block is cut by both (a block is at most the square root of three,
     * 1.73, through, and a quarter at either end counts as on a surface), so each block has one cut and a whole side
     * between the daylight that lights its cut and the cabin; about as thick as the skin of whole blocks was.
     */
    private static final double SKIN_THICKNESS = 1.25;
    /** How far from a corner, in blocks, the surface is looked for; past it the corner is simply inside or outside. */
    private static final double REACH = 3;
    /** The step, in blocks, the surface is looked for in, fine enough not to step over the thinnest part of the hull. */
    private static final double WALK = 0.25;
    /** Halvings of the step the surface was crossed in: to a thousandth of a block. */
    private static final int HALVINGS = 8;
    /**
     * A corner this much further than {@link ShellGeometry#ON_SURFACE} from the surface, as a share of it, may still
     * be taken as on it, and one on it as just off it, where that is what gives a block a pattern a flat surface makes.
     */
    private static final double SLACK = 0.6;
    /** Rounds of nudging corners until every block has a shape. */
    private static final int SETTLING = 4;
    /** How many blocks round a sliver of skin the skin it closes over is looked for. */
    private static final int SKIN_SEARCH = 2;
    /** How many layers of glass inside the windscreen's outer pane are taken away, at most: deeper than it ever is. */
    private static final int WINDSCREEN_LAYERS = 3;

    private final AirframeLayout layout;
    private final double scale;
    /** How far each block corner lies outside the hull's surface, in blocks (negative inside), by corner. */
    private final Map<Long, Double> depths = new HashMap<>();
    private final Surface outer = new Surface(0);
    private final Surface inner = new Surface(SKIN_THICKNESS);

    SnappedShell(AirframeLayout layout) {
        this.layout = layout;
        this.scale = layout.scale();
    }

    /**
     * One surface the skin is cut by: the hull's, or the inner one {@code offset} blocks in from it. Each block corner
     * lies inside it (toward the hull's surface, kept), on it or beyond it (cut away); the corners are found once and
     * then nudged where a block's corners would make no shape.
     */
    private final class Surface {
        private final double offset;
        private final Map<Long, Integer> corners = new HashMap<>();

        Surface(double offset) {
            this.offset = offset;
        }

        /** How far a corner lies beyond this surface, in blocks: out of the hull, or into the cabin for the inner one. */
        double beyond(int i, int j, int k) {
            double depth = depth(i, j, k);
            return offset == 0 ? depth : -(depth + offset);
        }

        int corner(int i, int j, int k) {
            return corners.computeIfAbsent(key(i, j, k), ignored -> {
                double beyond = beyond(i, j, k);
                return beyond < -ShellGeometry.ON_SURFACE ? ShellGeometry.INSIDE : beyond > ShellGeometry.ON_SURFACE ? ShellGeometry.OUTSIDE : ShellGeometry.ON;
            });
        }

        int[] pattern(BlockPos pos) {
            int[] pattern = new int[8];
            for (int corner = 0; corner < 8; corner++) {
                pattern[corner] = corner(pos.getX() + (corner & 1), pos.getY() + (corner >> 1 & 1), pos.getZ() + (corner >> 2 & 1));
            }
            return pattern;
        }

        int shape(BlockPos pos) {
            return ShellGeometry.snapped(pattern(pos));
        }

        /**
         * Nudges corners of the blocks whose corners make no flat surface's pattern: a corner near enough the surface
         * goes onto it or off it, wherever that gives the block a shape and takes it from none of the other blocks
         * round that corner.
         */
        void settle(List<BlockPos> blocks) {
            for (int round = 0; round < SETTLING; round++) {
                boolean nudged = false;
                for (BlockPos pos : blocks) {
                    if (shape(pos) == ShellGeometry.UNMATCHED) {
                        nudged |= nudge(pos);
                    }
                }
                if (!nudged) {
                    return;
                }
            }
        }

        private boolean nudge(BlockPos pos) {
            for (int corner = 0; corner < 8; corner++) {
                int i = pos.getX() + (corner & 1);
                int j = pos.getY() + (corner >> 1 & 1);
                int k = pos.getZ() + (corner >> 2 & 1);
                int was = corner(i, j, k);
                double beyond = beyond(i, j, k);
                int other = was != ShellGeometry.ON ? ShellGeometry.ON : beyond < 0 ? ShellGeometry.INSIDE : ShellGeometry.OUTSIDE;
                if (other == ShellGeometry.ON && Math.abs(beyond) > ShellGeometry.ON_SURFACE * (1 + SLACK)) {
                    continue;
                }
                List<BlockPos> around = around(i, j, k);
                List<BlockPos> matched = around.stream().filter(next -> shape(next) != ShellGeometry.UNMATCHED).toList();
                corners.put(key(i, j, k), other);
                if (shape(pos) != ShellGeometry.UNMATCHED && matched.stream().allMatch(next -> shape(next) != ShellGeometry.UNMATCHED)) {
                    return true;
                }
                corners.put(key(i, j, k), was);
            }
            return false;
        }
    }

    /** The eight blocks round a corner. */
    private static List<BlockPos> around(int i, int j, int k) {
        List<BlockPos> around = new ArrayList<>(8);
        for (int corner = 0; corner < 8; corner++) {
            around.add(new BlockPos(i - (corner & 1), j - (corner >> 1 & 1), k - (corner >> 2 & 1)));
        }
        return around;
    }

    /** Cuts the skin to the curve, outside all along the fuselage and inside in the nose, into the layout. */
    void apply() {
        int halfWidth = (int) Math.ceil(WIDTH / 2 * scale) + 2;
        int height = (int) Math.ceil(HEIGHT * scale) + 2;
        int length = (int) Math.ceil(LENGTH * scale) + 2;
        int noseEnd = (int) Math.floor(NOSE_LENGTH * scale);
        List<BlockPos> cutOuter = new ArrayList<>();
        List<BlockPos> cutInner = new ArrayList<>();
        for (int z = -2; z <= length; z++) {
            for (int y = -2; y <= height; y++) {
                for (int x = -halfWidth; x <= halfWidth; x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    int shape = outer.shape(pos);
                    if (shape != ShellGeometry.WHOLE && shape != ShellGeometry.NONE) {
                        cutOuter.add(pos);
                    }
                    if (z <= noseEnd && shape == ShellGeometry.WHOLE && inner.shape(pos) != ShellGeometry.NONE) {
                        cutInner.add(pos);
                    }
                }
            }
        }
        outer.settle(cutOuter);
        inner.settle(cutInner);
        Map<BlockPos, BlockState> shaped = new HashMap<>();
        for (BlockPos pos : cutOuter) {
            outside(pos, shaped);
        }
        for (BlockPos pos : cutInner) {
            if (!shaped.containsKey(pos)) {
                inside(pos, shaped);
            }
        }
        shaped.forEach((pos, state) -> layout.set(pos.getX(), pos.getY(), pos.getZ(), state));
        thinWindscreen();
    }

    /**
     * Thins the windscreen to the panes the hull's surface runs through: glass with every corner inside the surface,
     * the skin's inner layer, is taken away from the flight deck's side, a layer at a time, so the pilots look out
     * through one pane instead of past panes standing inside it, which darken what is seen past them. Blocks sharing a
     * side share its corners, so the glass left round such a block shows it whole faces, and nothing opens to the sky.
     */
    private void thinWindscreen() {
        for (int layer = 0; layer < WINDSCREEN_LAYERS; layer++) {
            List<BlockPos> inner = layout.cells().entrySet().stream()
                .filter(cell -> cell.getValue().is(AerolargeRegistry.COCKPIT_GLASS.get()))
                .filter(cell -> underSurface(cell.getKey()) && openInside(cell.getKey()))
                .map(Map.Entry::getKey)
                .toList();
            inner.forEach(pos -> layout.set(pos.getX(), pos.getY(), pos.getZ(), Blocks.AIR.defaultBlockState()));
        }
    }

    /** Whether every corner of a block lies inside the hull's surface. */
    private boolean underSurface(BlockPos pos) {
        return Arrays.stream(outer.pattern(pos)).allMatch(corner -> corner == ShellGeometry.INSIDE);
    }

    /** Whether a block stands against the cabin's open air on any side. */
    private boolean openInside(BlockPos pos) {
        return Arrays.stream(Direction.values()).anyMatch(side -> layout.isOpen(pos.getX() + side.getStepX(), pos.getY() + side.getStepY(),
            pos.getZ() + side.getStepZ()));
    }

    /** A block the hull's surface cuts: whatever stands there cut to it, or a sliver of skin where there is air. */
    private void outside(BlockPos pos, Map<BlockPos, BlockState> shaped) {
        int shape = outer.shape(pos);
        if (shape == ShellGeometry.UNMATCHED) {
            shape = layout.shapeAt(pos.getX(), pos.getY(), pos.getZ());
            if (shape <= ShellGeometry.WHOLE) {
                return;
            }
        }
        BlockState state = layout.stateAt(pos.getX(), pos.getY(), pos.getZ());
        boolean inside = layout.inFuselage(pos.getX(), pos.getY(), pos.getZ());
        if (inside && isSkin(state)) {
            if (!layout.nextToFitting(pos, false) && !layout.nextToDoor(pos) && !nextToGap(pos)) {
                put(shaped, pos, state, shape);
            }
        } else if (!inside && !layout.cells().containsKey(pos)) {
            if (!layout.nextToFitting(pos, true)) {
                put(shaped, pos, material(pos), shape);
            }
        } else if (inside && layout.isOpen(pos.getX(), pos.getY(), pos.getZ()) && fillable(pos)) {
            put(shaped, pos, material(pos), nextToGap(pos) ? ShellGeometry.WHOLE : shape);
        }
    }

    /**
     * Whether open air beside the block reaches out past the hull's surface and stays open: the doorways, cut through
     * the skin behind the doors. Cut to the curve, the block would let daylight in at its cut and on through its side
     * into the doorway, so it keeps that side whole.
     */
    private boolean nextToGap(BlockPos pos) {
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            int shape = outer.shape(next);
            if (shape != ShellGeometry.WHOLE && shape != ShellGeometry.NONE && layout.isOpen(next.getX(), next.getY(), next.getZ())
                && layout.inFuselage(next.getX(), next.getY(), next.getZ()) && !fillable(next)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A block of the nose the inner surface cuts or lies outside of: skin is cut back to it on the inside where
     * nothing but skin stands against it, and cabin air is filled out to it. Not the windscreen's glass: cut back on
     * the inside, a pane would open toward the flight deck and the look through it would skip its tint, so the glass
     * keeps its inner side whole, and the cabin air in front of it stays open.
     */
    private void inside(BlockPos pos, Map<BlockPos, BlockState> shaped) {
        int shape = inner.shape(pos);
        if (shape == ShellGeometry.UNMATCHED || shape == ShellGeometry.NONE) {
            return;
        }
        BlockState state = layout.stateAt(pos.getX(), pos.getY(), pos.getZ());
        if (isSkin(state) && layout.inSkin(pos)) {
            if (shape != ShellGeometry.WHOLE && againstSkinOnly(pos) && !layout.nextToDoor(pos) && !windscreen(state)) {
                put(shaped, pos, state, shape);
            }
        } else if (layout.isOpen(pos.getX(), pos.getY(), pos.getZ()) && fillable(pos) && !windscreen(material(pos))) {
            put(shaped, pos, material(pos), shape);
        }
    }

    /** Whether a block is the windscreen's glass. */
    private static boolean windscreen(BlockState state) {
        return state.is(AerolargeRegistry.COCKPIT_GLASS.get());
    }

    /** Puts the block, of the given kind, cut to the shape; a window pane that cannot take it keeps its fitted cut. */
    private void put(Map<BlockPos, BlockState> shaped, BlockPos pos, BlockState material, int shape) {
        BlockState state = cutAs(material);
        if (!ShellBlock.takes(state, shape)) {
            shape = layout.shapeAt(pos.getX(), pos.getY(), pos.getZ());
            if (shape <= ShellGeometry.WHOLE || !ShellBlock.takes(state, shape)) {
                return;
            }
        }
        shaped.put(pos, ShellBlock.withShape(state, shape));
    }

    /** The block a piece of skin of this kind is cut from: glass stays glass, a window a window, the rest shell. */
    private static BlockState cutAs(BlockState material) {
        if (material.getBlock() instanceof CockpitGlassBlock) {
            return material.getBlock().defaultBlockState();
        }
        if (CabinWindowBlock.isPane(material)) {
            return AerolargeRegistry.CABIN_WINDOW_SHELL.get().defaultBlockState();
        }
        return AerolargeRegistry.AIRFRAME_SHELL.get().defaultBlockState();
    }

    /** Whether the block is skin that can be cut: hull, glass or a window pane. */
    private boolean isSkin(BlockState state) {
        return state.is(layout.palette().hull().getBlock()) || state.getBlock() instanceof CockpitGlassBlock || CabinWindowBlock.isPane(state);
    }

    /**
     * What a sliver of skin or cabin air filled out to the surface is made of: a window or the belly's glass where
     * the skin it closes over is that, otherwise the windscreen's glass wherever the windscreen is, and hull
     * everywhere else. Asking the windscreen rather than the nearest skin ends the glass along the same rows and
     * stations inside and out, however the curve runs past the edge of it. Cabin air under the windscreen is filled
     * with glass even behind a post, so from the flight deck the posts stand as slim as their own skin and the view
     * out to the side stays open.
     */
    private BlockState material(BlockPos pos) {
        BlockState skin = nearestSkin(pos);
        boolean windscreen = skin.is(AerolargeRegistry.COCKPIT_GLASS.get()) || skin.is(layout.palette().hull().getBlock());
        if (!windscreen) {
            return skin;
        }
        boolean glass = layout.inFuselage(pos.getX(), pos.getY(), pos.getZ())
            ? Windscreen.covers((pos.getY() + 0.5) / scale, (pos.getZ() + 0.5) / scale)
            : layout.windscreenAt(pos);
        return glass ? AerolargeRegistry.COCKPIT_GLASS.get().defaultBlockState() : layout.palette().hull();
    }

    /**
     * The skin a block next to the surface closes over: of the blocks round it, out of the cabin (or into the hull,
     * from outside), the one lying most squarely that way, the nearest round first.
     */
    private BlockState nearestSkin(BlockPos pos) {
        double[] out = AirframeShape.outward(pos.getX() / scale, (pos.getY() + 0.5) / scale, (pos.getZ() + 0.5) / scale);
        int toward = layout.inFuselage(pos.getX(), pos.getY(), pos.getZ()) ? 1 : -1;
        for (int reach = 1; reach <= SKIN_SEARCH; reach++) {
            BlockState best = null;
            double square = 0;
            for (BlockPos next : BlockPos.betweenClosed(pos.offset(-reach, -reach, -reach), pos.offset(reach, reach, reach))) {
                int dx = next.getX() - pos.getX();
                int dy = next.getY() - pos.getY();
                int dz = next.getZ() - pos.getZ();
                double way = toward * (dx * out[0] + dy * out[1] + dz * out[2]) / Math.sqrt(dx * dx + dy * dy + dz * dz + 1e-9);
                BlockState state = layout.stateAt(next.getX(), next.getY(), next.getZ());
                if (way > square && isSkin(state)) {
                    square = way;
                    best = state;
                }
            }
            if (best != null) {
                return best;
            }
        }
        return layout.palette().hull();
    }

    /**
     * Whether cabin air may be filled out to the surface: only where nothing but skin, the floor or more air stands
     * beside it, and not where the Physics Assembler goes. A filled side against anything else, a door, a light, a
     * seat or a screen, would bury its face in skin or shut its light in. The announcement panel, close under the
     * crown, faces only down, so air beside it is filled all the same, or the crown would be left open to the sky
     * there; air under it is not.
     */
    private boolean fillable(BlockPos pos) {
        if (pos.equals(layout.assemblerSpot())) {
            return false;
        }
        for (Direction side : Direction.values()) {
            BlockState next = layout.stateAt(pos.getX() + side.getStepX(), pos.getY() + side.getStepY(), pos.getZ() + side.getStepZ());
            boolean besidePanel = next.is(AerolargeRegistry.ANNOUNCEMENT_PANEL.get()) && side != Direction.UP;
            if (!next.isAir() && !isSkin(next) && !next.is(layout.palette().floor().getBlock()) && !besidePanel) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether nothing but skin, cabin air or the outside stands against this piece of skin: cut back on the inside
     * beside a floor, the instrument panel or anything else, it would leave a notch along it.
     */
    private boolean againstSkinOnly(BlockPos pos) {
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            BlockState state = layout.stateAt(next.getX(), next.getY(), next.getZ());
            boolean skin = isSkin(state) && layout.inSkin(next) || state.is(layout.palette().light().getBlock());
            if (!skin && !state.isAir()) {
                return false;
            }
        }
        return true;
    }

    /**
     * How far a block corner lies outside the hull's surface, in blocks, negative inside: the nearest the surface is
     * along the way the fuselage faces there or along any of the six axes, walked out from the corner. Only along the
     * way it faces, the surface would be missed where the fuselage ends flat at the nose and tail, and where the tail
     * cone is too thin to walk through without coming out the other side. A corner with no surface that way within
     * reach, away from the ends, lies deep in the cabin or well clear of the hull, and the axes are not walked.
     */
    private double depth(int i, int j, int k) {
        return depths.computeIfAbsent(key(i, j, k), ignored -> {
            double[] corner = {(i - 0.5) / scale, j / scale, k / scale};
            boolean inside = AirframeShape.inFuselage(corner[0], corner[1], corner[2]);
            double[] out = AirframeShape.outward(corner[0], corner[1], corner[2]);
            double toward = inside ? 1 : -1;
            double nearest = crossing(corner, new double[]{toward * out[0], toward * out[1], toward * out[2]}, inside, REACH);
            boolean nearEnd = k < REACH || k > LENGTH * scale - REACH;
            if (nearest < REACH || nearEnd) {
                for (Direction side : Direction.values()) {
                    nearest = Math.min(nearest, crossing(corner, new double[]{side.getStepX(), side.getStepY(), side.getStepZ()}, inside, nearest));
                }
            }
            return inside ? -nearest : nearest;
        });
    }

    /**
     * How far from a point, in blocks, walking a way out from it first crosses the surface: in steps of {@link #WALK},
     * then halving the step it crossed in; {@code limit} if not that near.
     */
    private double crossing(double[] from, double[] way, boolean inside, double limit) {
        for (double reached = WALK; reached <= limit + WALK / 2; reached += WALK) {
            if (inFuselage(from, way, reached) != inside) {
                double near = reached - WALK;
                double far = reached;
                for (int halving = 0; halving < HALVINGS; halving++) {
                    double middle = (near + far) / 2;
                    if (inFuselage(from, way, middle) == inside) {
                        near = middle;
                    } else {
                        far = middle;
                    }
                }
                return Math.min(limit, (near + far) / 2);
            }
        }
        return limit;
    }

    private boolean inFuselage(double[] from, double[] way, double blocks) {
        return AirframeShape.inFuselage(from[0] + way[0] * blocks / scale, from[1] + way[1] * blocks / scale, from[2] + way[2] * blocks / scale);
    }

    private static long key(int i, int j, int k) {
        return ((long) (i + (1 << 20)) << 42) | ((long) (j + (1 << 20)) << 21) | (k + (1 << 20));
    }
}
