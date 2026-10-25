package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.AutopilotScreenBlock;
import dev.aerolarge.block.CockpitDisplayBlock;
import dev.aerolarge.block.CockpitDisplayBlockEntity;
import dev.aerolarge.block.InstrumentPanelBlock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The flight deck's curved row of screens either side of the autopilot screen, as in an airliner's glass cockpit:
 * from each side edge of the autopilot screen a panel turned a step toward the pilots' seats, and from its outer edge
 * another turned a step further, so the row curves round the seats (see {@link CockpitDisplayBlock}). Left to right as
 * the pilots see it: the captain's flight display, the moving map, the autopilot screen, an outside camera and the
 * first officer's flight display. Each panel stands on the floor with its face lined up with the autopilot screen's,
 * as high as the instrument panel behind it, so the pilots look out over it through the windscreen. It is as wide as
 * {@link #WIDEST} where the nose leaves room and narrower where it closes in, down to {@link #NARROWEST}; a pair that
 * does not fit even then is left out, and the outer pair with it. Behind the panels the instrument panel carries on
 * round to the hull, so they stand in front of a desk rather than over bare floor. Panels and desk only go into open
 * space. Corners are measured in block edges across the floor plan, the layout's nose north.
 */
final class ScreenArc {
    /** How much further each panel along the row turns toward the seats than the one inside it, in degrees. */
    private static final double TURN = 22.5;
    /** The widest a panel is, the narrowest, and the step between the widths tried, in blocks. */
    private static final double WIDEST = 2;
    private static final double NARROWEST = 1.25;
    private static final double NARROWER = 0.25;
    /**
     * How high a panel's top edge stands over the floor, in blocks: as high as the instrument panel, below the seated
     * pilots' eyes, so the panels hide no more of the windscreen than the instrument panel does.
     */
    private static final float TOP = 1;
    /** How many rows of blocks a panel stands in. */
    private static final int ROWS = (int) Math.ceil(TOP);
    /** How many pairs of panels the row has at most, either side of the autopilot screen. */
    private static final int PAIRS = 2;
    /** What the inner pair and then the outer pair show, the captain's side first. */
    private static final CockpitDisplayBlock.Shows[][] SHOWS = {
        {CockpitDisplayBlock.Shows.MAP, CockpitDisplayBlock.Shows.CAMERA},
        {CockpitDisplayBlock.Shows.FLIGHT, CockpitDisplayBlock.Shows.FLIGHT}
    };

    private final AirframeLayout layout;
    private final int bottom;
    private final int screenZ;

    /**
     * A panel of the row, planned: the bottom corners of its face, left and right as the pilots see it, and what it
     * shows.
     */
    private record Panel(double leftX, double leftZ, double rightX, double rightZ, CockpitDisplayBlock.Shows shows) {
        double width() {
            return Math.hypot(rightX - leftX, rightZ - leftZ);
        }

        /** The blocks its face runs through, across the floor plan (at height 0). */
        List<BlockPos> face() {
            return squares(leftX, leftZ, rightX, rightZ);
        }

        /** The blocks its face and the back of its slab run through, across the floor plan: those it needs open. */
        Set<BlockPos> room() {
            double width = width();
            double backX = (rightZ - leftZ) / width * CockpitDisplayBlock.DEPTH;
            double backZ = -(rightX - leftX) / width * CockpitDisplayBlock.DEPTH;
            Set<BlockPos> room = new LinkedHashSet<>(face());
            room.addAll(squares(leftX + backX, leftZ + backZ, rightX + backX, rightZ + backZ));
            return room;
        }

        /** The block holding it, across the floor plan: the one the middle of its face stands in. */
        BlockPos holder() {
            return new BlockPos(Mth.floor((leftX + rightX) / 2), 0, Mth.floor((leftZ + rightZ) / 2));
        }
    }

    /** The row on the floor of row bottom, the autopilot screen standing in station screenZ. */
    ScreenArc(AirframeLayout layout, int bottom, int screenZ) {
        this.layout = layout;
        this.bottom = bottom;
        this.screenZ = screenZ;
    }

    /** Puts up the panels that fit; whether any did, the outside camera's among them. */
    boolean apply() {
        List<Panel[]> pairs = plan();
        // Every block a panel runs through first, then the blocks holding them, so none is taken over by another panel.
        for (Panel[] pair : pairs) {
            for (Panel panel : pair) {
                standUp(panel);
            }
        }
        for (Panel[] pair : pairs) {
            for (Panel panel : pair) {
                hold(panel);
            }
        }
        if (!pairs.isEmpty()) {
            desk(pairs);
        }
        return !pairs.isEmpty();
    }

    /** How wide the panels that fit are, all of them together, in blocks: the more of the row fits, the wider. */
    double width() {
        double width = 0;
        for (Panel[] pair : plan()) {
            width += pair[0].width() + pair[1].width();
        }
        return width;
    }

    /** The pairs of panels that fit, from the inner pair out, each the captain's side first. */
    private List<Panel[]> plan() {
        List<Panel[]> pairs = new ArrayList<>();
        Set<BlockPos> taken = new HashSet<>();
        double faceZ = screenZ + AutopilotScreenBlock.DEPTH / 16.0;
        double left = -(AutopilotScreenBlock.COLUMNS / 2);
        // The autopilot screen, standing straight, as the panel the first pair hinges on.
        Panel[] inside = {
            new Panel(left, faceZ, left, faceZ, SHOWS[0][0]),
            new Panel(left + AutopilotScreenBlock.COLUMNS, faceZ, left + AutopilotScreenBlock.COLUMNS, faceZ, SHOWS[0][1])
        };
        for (int step = 0; step < PAIRS; step++) {
            Panel[] pair = widestPair(step, inside[0], inside[1], taken);
            if (pair == null) {
                break;
            }
            pairs.add(pair);
            taken.addAll(blocks(pair[0]));
            taken.addAll(blocks(pair[1]));
            inside = pair;
        }
        return pairs;
    }

    /**
     * The widest pair that fits at this step out from the middle, hinged on the outer edges of the pair inside it;
     * null for none.
     */
    @Nullable
    private Panel[] widestPair(int step, Panel captainInside, Panel firstOfficerInside, Set<BlockPos> taken) {
        double turn = Math.toRadians(TURN * (step + 1));
        for (double width = WIDEST; width > NARROWEST - NARROWER / 2; width -= NARROWER) {
            double across = Math.cos(turn) * width;
            double back = Math.sin(turn) * width;
            Panel captain = new Panel(captainInside.leftX - across, captainInside.leftZ + back, captainInside.leftX, captainInside.leftZ,
                SHOWS[step][0]);
            Panel firstOfficer = new Panel(firstOfficerInside.rightX, firstOfficerInside.rightZ, firstOfficerInside.rightX + across,
                firstOfficerInside.rightZ + back, SHOWS[step][1]);
            if (fits(captain, taken) && fits(firstOfficer, taken)) {
                return new Panel[]{captain, firstOfficer};
            }
        }
        return null;
    }

    /** Whether every block the panel needs is open, or another panel of the row's. */
    private boolean fits(Panel panel, Set<BlockPos> taken) {
        for (BlockPos block : blocks(panel)) {
            if (!taken.contains(block) && !layout.isOpen(block.getX(), block.getY(), block.getZ())) {
                return false;
            }
        }
        return true;
    }

    /** Every block the panel needs, its rows over the floor plan. */
    private List<BlockPos> blocks(Panel panel) {
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos square : panel.room()) {
            for (int row = 0; row < ROWS; row++) {
                blocks.add(square.offset(0, bottom + row, 0));
            }
        }
        return blocks;
    }

    /** The panel's blocks, every one its face runs through. */
    private void standUp(Panel panel) {
        BlockState part = part(panel);
        for (BlockPos square : panel.face()) {
            for (int row = 0; row < ROWS; row++) {
                layout.set(square.getX(), bottom + row, square.getZ(), part);
            }
        }
    }

    /**
     * The block holding the panel, at the floor, told when the airliner is built where the panel stands from it: in
     * the frame of the way the row faces, which with the row facing south is the layout's own.
     */
    private void hold(Panel panel) {
        BlockPos holder = panel.holder().offset(0, bottom, 0);
        layout.set(holder.getX(), holder.getY(), holder.getZ(), part(panel).setValue(CockpitDisplayBlock.HOLDER, true));
        double middleX = holder.getX() + 0.5;
        double middleZ = holder.getZ() + 0.5;
        CockpitDisplayBlockEntity.Panel where = new CockpitDisplayBlockEntity.Panel((float) (panel.leftX - middleX), (float) (panel.leftZ - middleZ),
            (float) (panel.rightX - middleX), (float) (panel.rightZ - middleZ), 0, TOP);
        layout.fit(holder, (blockEntity, rotation) -> {
            if (blockEntity instanceof CockpitDisplayBlockEntity display) {
                display.configure(where);
            }
        });
    }

    /**
     * The instrument panel carried on round behind the panels: every open block of the floor's row that lies on the
     * nose's side of the panels, from the row's station back to its outer ends and out to the hull. Facing the nose, so
     * the instrument panel's screens, shown on its face, stay hidden against the glareshield.
     */
    private void desk(List<Panel[]> pairs) {
        Panel captainOuter = pairs.get(pairs.size() - 1)[0];
        Panel firstOfficerOuter = pairs.get(pairs.size() - 1)[1];
        int back = Mth.floor(Math.max(captainOuter.leftZ, firstOfficerOuter.rightZ));
        BlockState desk = AerolargeRegistry.INSTRUMENT_PANEL.get().defaultBlockState().setValue(InstrumentPanelBlock.FACING, Direction.NORTH);
        for (BlockPos block : BlockPos.betweenClosed(Mth.floor(captainOuter.leftX) - 1, bottom, screenZ, Mth.floor(firstOfficerOuter.rightX) + 1,
            bottom, back)) {
            if (layout.isOpen(block.getX(), block.getY(), block.getZ()) && behind(pairs, block.getX() + 0.5, block.getZ() + 0.5)) {
                layout.set(block.getX(), block.getY(), block.getZ(), desk);
            }
        }
    }

    /**
     * Whether a point of the floor plan lies on the nose's side of the panels on its side of the autopilot screen, or
     * past their outer end and ahead of it; not if it lies between the screen's edges.
     */
    private static boolean behind(List<Panel[]> pairs, double x, double z) {
        double hinge = pairs.get(0)[0].rightX;
        int side = x < hinge ? 0 : 1;
        Panel outer = pairs.get(pairs.size() - 1)[side];
        double outerX = side == 0 ? outer.leftX : outer.rightX;
        double outerZ = side == 0 ? outer.leftZ : outer.rightZ;
        if (side == 0 ? x < outerX : x > outerX) {
            return z < outerZ;
        }
        for (Panel[] pair : pairs) {
            Panel panel = pair[side];
            double share = (x - panel.leftX) / (panel.rightX - panel.leftX);
            if (share >= 0 && share <= 1) {
                return z < panel.leftZ + share * (panel.rightZ - panel.leftZ);
            }
        }
        return false;
    }

    private static BlockState part(Panel panel) {
        return AerolargeRegistry.COCKPIT_DISPLAY.get().defaultBlockState()
            .setValue(CockpitDisplayBlock.FACING, Direction.SOUTH)
            .setValue(CockpitDisplayBlock.SHOWS, panel.shows());
    }

    /** The blocks a line across the floor plan runs through, not only along their edges (at height 0). */
    private static List<BlockPos> squares(double x0, double z0, double x1, double z1) {
        List<BlockPos> squares = new ArrayList<>();
        for (BlockPos square : BlockPos.betweenClosed(Mth.floor(Math.min(x0, x1)), 0, Mth.floor(Math.min(z0, z1)), Mth.floor(Math.max(x0, x1)), 0,
            Mth.floor(Math.max(z0, z1)))) {
            if (CockpitDisplayBlock.crossesSquare(x0, z0, x1, z1, square.getX(), square.getZ())) {
                squares.add(square.immutable());
            }
        }
        return squares;
    }
}
