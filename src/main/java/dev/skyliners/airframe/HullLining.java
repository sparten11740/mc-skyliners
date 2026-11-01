package dev.skyliners.airframe;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.CabinWindowBlock;
import dev.skyliners.block.LinedShellBlock;
import dev.skyliners.block.Lining;
import dev.skyliners.block.ShellBlock;
import dev.skyliners.block.ShellGeometry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The hull's lining (see {@link Lining}), once the skin is cut to the curve: every face of the skin that looks into
 * the airliner (the cabin on both decks, the flight deck and the crew rooms behind it, the den, the hold) is lined,
 * and every face that looks out stays plated. A whole panel, and a relay in the wall, keep which of their faces are
 * lined; a block cut to the curve that the inside sees becomes a {@link LinedShellBlock}, lined behind its cut where
 * the cut is the outer skin, or on its cut round the flight deck, where the skin's inside is cut to the curve too.
 * Where some of the sides behind an outer cut look out, the block stays plated, or the lining would show outside.
 * Only the blocks' states change: the surface stays as the shell pass cut it. It comes once the gear meets the skin
 * (the gear's legs go up through slivers of plain skin) and before the airstairs keep their hatch's skin to put back.
 */
final class HullLining {
    /**
     * How far out from the middle of a block's cut, in blocks, the fuselage is looked for to tell an inner cut: past
     * where the hull's surface may lie beyond a snapped cut, which takes a corner up to two fifths of a block off the
     * surface as on it.
     */
    private static final double PAST_CUT = 0.75;

    /** Where a face of the skin looks: into the airliner, out of it, or onto more of the skin. */
    private enum Way {
        IN, OUT, SKIN
    }

    private final AirframeLayout layout;

    HullLining(AirframeLayout layout) {
        this.layout = layout;
    }

    void apply() {
        Map<BlockPos, BlockState> lined = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> cell : layout.cells().entrySet()) {
            BlockPos pos = cell.getKey();
            BlockState state = cell.getValue();
            BlockState lining = Lining.keepsSides(state) ? faced(pos, state)
                : state.is(SkylinersRegistry.AIRFRAME_SHELL.get()) ? shell(pos, state) : state;
            if (lining != state) {
                lined.put(pos, lining);
            }
        }
        lined.forEach((pos, state) -> layout.set(pos.getX(), pos.getY(), pos.getZ(), state));
    }

    /**
     * A block of the skin cut to the curve, lined where the inside sees it: on its cut where the cut looks in, and
     * along it too where the cut ends open to the cabin; behind its cut where the sides there look in; plated
     * otherwise. Whole, it is a panel in all but name, and becomes one, which keeps each face's lining.
     */
    private BlockState shell(BlockPos pos, BlockState state) {
        int shape = ShellBlock.shape(state);
        LinedShellBlock linedShell = SkylinersRegistry.AIRFRAME_LINED_SHELL.get();
        if (shape == ShellGeometry.WHOLE) {
            BlockState panel = layout.palette().hull();
            BlockState faced = faced(pos, panel);
            return faced != panel ? faced : state;
        }
        if (cutLooksIn(pos, shape)) {
            int along = Lining.faces(shape, LinedShellBlock.Side.CUT_ALONG) & ~Lining.faces(shape, LinedShellBlock.Side.CUT);
            return linedShell.lined(shape, onlyIn(pos, shape, along) ? LinedShellBlock.Side.CUT_ALONG : LinedShellBlock.Side.CUT);
        }
        boolean back = onlyIn(pos, shape, Lining.faces(shape, LinedShellBlock.Side.BACK));
        return back ? linedShell.lined(shape, LinedShellBlock.Side.BACK) : state;
    }

    /** A block keeping its faces, each lined where it looks in. */
    private BlockState faced(BlockPos pos, BlockState state) {
        BlockState faced = state;
        for (Direction side : Direction.values()) {
            faced = faced.setValue(Lining.side(side), way(pos, side) == Way.IN);
        }
        return faced;
    }

    /**
     * Whether a block cut to the curve is to line the faces on these of its sides: where one of them looks in and none
     * looks out. Lined, it lines all of them, so where the hull is thin or turns in on itself (at the root of the fin)
     * and they look both ways, they stay plated rather than show the lining outside.
     */
    private boolean onlyIn(BlockPos pos, int shape, int faces) {
        List<ShellGeometry.Face> all = ShellGeometry.faces(shape);
        boolean in = false;
        for (int i = 0; i < all.size(); i++) {
            Direction side = all.get(i).side();
            if ((faces & 1 << i) == 0 || side == null) {
                continue;
            }
            Way way = way(pos, side);
            if (way == Way.OUT) {
                return false;
            }
            in |= way == Way.IN;
        }
        return in;
    }

    /**
     * Where the face on one side of a block of the skin looks. In: onto the airliner's open inside or anything
     * standing in it, inside the fuselage and clear of its skin; onto the pane of a cabin window beside it, the inside
     * of the window's opening, which the lining turns in to; or past the cut of a block of the skin's inside cut to
     * the curve beside it, where the cut leaves the face open to the cabin. Out: onto the open air outside the
     * fuselage, or anything there that does not cover the face, a sliver of window glass on the outside included.
     * Onto the rest of the skin otherwise, a doorway and the airstairs' hatch cut through it among them, and the outer
     * skin cut to the curve beside it, which covers it but for a sliver.
     */
    private Way way(BlockPos pos, Direction side) {
        BlockPos next = pos.relative(side);
        BlockState there = layout.cells().get(next);
        if (there == null) {
            return Way.OUT;
        }
        boolean inside = layout.inFuselage(next.getX(), next.getY(), next.getZ());
        if (CabinWindowBlock.isPane(there)) {
            return inside ? Way.IN : Way.OUT;
        }
        if (there.getBlock() instanceof ShellBlock) {
            // Mostly covered by the skin beside it, all but where that is cut away: from outside, the step where two
            // cuts of the fitted skin do not quite meet, from the cabin a side its inner cut leaves open.
            int shape = ShellBlock.shape(there);
            boolean open = shape != ShellGeometry.WHOLE && !ShellGeometry.whole(shape, side.getOpposite());
            return open && cutLooksIn(next, shape) ? Way.IN : Way.SKIN;
        }
        if (!inside) {
            // Against something solid built onto the hull from outside (a wing's root, the fin), the face is covered.
            return there.canOcclude() ? Way.SKIN : Way.OUT;
        }
        return layout.inSkin(next) ? Way.SKIN : Way.IN;
    }

    /**
     * Whether a block's cut looks into the airliner rather than out of it, as where the skin's inside is cut to the
     * curve round the flight deck: a little way out from the middle of its cut is still inside the fuselage.
     */
    private boolean cutLooksIn(BlockPos pos, int shape) {
        for (ShellGeometry.Face face : ShellGeometry.faces(shape)) {
            if (face.side() != null) {
                continue;
            }
            double x = 0;
            double y = 0;
            double z = 0;
            for (float[] corner : face.corners()) {
                x += corner[0];
                y += corner[1];
                z += corner[2];
            }
            int corners = face.corners().size();
            return layout.inFuselageAt(pos.getX() + x / corners + face.nx() * PAST_CUT, pos.getY() + y / corners + face.ny() * PAST_CUT,
                pos.getZ() + z / corners + face.nz() * PAST_CUT);
        }
        return false;
    }
}
