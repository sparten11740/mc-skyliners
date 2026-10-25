package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.CockpitDisplayBlock;
import dev.aerolarge.block.CockpitDisplayBlock.Shows;
import dev.aerolarge.block.CockpitDisplayBlockEntity;
import dev.aerolarge.block.CockpitDisplayBlockEntity.Desk;
import dev.aerolarge.block.CockpitDisplayBlockEntity.Link;
import dev.aerolarge.block.InstrumentPanelBlock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The flight deck's curved row of screens, as in an airliner's glass cockpit: in the middle, straight, the systems
 * screen, and from each of its side edges a panel turned a step toward the pilots' seats, and from its outer edge
 * another turned a step further, so the row curves round the seats (see {@link CockpitDisplayBlock}). Left to right as
 * the pilots see it: the captain's flight display, the moving map, the systems screen, an outside camera and the first
 * officer's flight display. The screens stand a block over the floor, on a desk whose top slopes down toward the pilots
 * at 45 degrees, and on the slope are the touch keys: the tiller under the captain's flight display, the parking brake
 * and the landing gear under the map, the autopilot's buttons under the systems screen. The middle screen is as wide
 * as {@link #MIDDLE_WIDTHS} allow and the others as wide as {@link #WIDEST}, narrower where the nose closes in, down to
 * {@link #NARROWEST}, the pairs turning as little as lets them fit; a pair that does not fit even then is left out, and
 * the outer pair with it. The row stays ahead of the seats. Behind it the instrument panel carries on round to the
 * hull. Panels and desk only go into open space. Corners are measured in block edges across the floor plan, the
 * layout's nose north.
 */
final class ScreenArc {
    /** How much further each pair turns toward the seats than the one inside it, in degrees: the gentlest that fits. */
    private static final double[] TURNS = {22.5, 30, 37.5};
    /** How wide the middle screen is, the widest that fits first, in blocks. */
    private static final double[] MIDDLE_WIDTHS = {3, 2};
    /** The widest the other panels are, the narrowest, and the step between the widths tried, in blocks. */
    private static final double WIDEST = 2;
    private static final double NARROWEST = 1.25;
    private static final double NARROWER = 0.25;
    /** How far into the row's blocks the screens' faces stand from their front edge, in blocks: the slab behind them. */
    private static final double FACE_BACK = 0.2;
    /** How high the screens' bottom and top edges stand over the floor, in blocks: from the desk's top up a block. */
    private static final float BOTTOM = 1;
    private static final float TOP = 2;
    /** How far the desk's slope reaches out toward the pilots from the screens' faces, in blocks, dropping as far. */
    private static final float DESK = 0.5F;
    /** What the inner pair and then the outer pair show, the captain's side first. */
    private static final Shows[][] SHOWS = {{Shows.MAP, Shows.CAMERA}, {Shows.FLIGHT, Shows.FLIGHT}};

    private final AirframeLayout layout;
    private final int floor;
    private final int screenZ;
    /** How far back from the row's station its desks may reach, in blocks: short of the seats. */
    private final double deepest;
    /** The blocks across the floor plan the row keeps clear of (at height 0): the centre console's, and the legs'. */
    private final Set<BlockPos> clear;

    /**
     * A panel of the row, planned: the bottom corners of its screen's face, left and right as the pilots see it, what
     * it shows, what its slope holds, and how far in from each end its slope's front edge stops (see {@link #mitred}).
     */
    private record Panel(double leftX, double leftZ, double rightX, double rightZ, Shows shows, Desk desk, double leftCut, double rightCut) {
        double width() {
            return Math.hypot(rightX - leftX, rightZ - leftZ);
        }

        /** Out of the face toward the pilots, across the floor plan, this far (back into the slab where negative). */
        double[] out(double distance) {
            double width = width();
            return new double[]{-(rightZ - leftZ) / width * distance, (rightX - leftX) / width * distance};
        }

        /** The blocks its face runs through, across the floor plan (at height 0). */
        Set<BlockPos> face() {
            return new LinkedHashSet<>(along(0));
        }

        /** The blocks its screen needs open, across the floor plan: its face, and its slab's back. */
        Set<BlockPos> screen() {
            Set<BlockPos> room = face();
            room.addAll(along(-CockpitDisplayBlock.DEPTH));
            return room;
        }

        /**
         * The blocks its desk runs through, across the floor plan: the band from the slab's back out to the slope's
         * front edge, found by its edges (it is never wide enough to hold a whole block).
         */
        Set<BlockPos> under() {
            double[] back = out(-CockpitDisplayBlock.DEPTH);
            double[] front = out(DESK);
            Set<BlockPos> room = new LinkedHashSet<>(along(-CockpitDisplayBlock.DEPTH));
            room.addAll(along(DESK));
            room.addAll(squares(leftX + back[0], leftZ + back[1], leftX + front[0], leftZ + front[1]));
            room.addAll(squares(rightX + back[0], rightZ + back[1], rightX + front[0], rightZ + front[1]));
            return room;
        }

        /** The blocks the line along the face runs through, out from it so far. */
        List<BlockPos> along(double distance) {
            double[] by = out(distance);
            return squares(leftX + by[0], leftZ + by[1], rightX + by[0], rightZ + by[1]);
        }

        /** The block holding it, across the floor plan: the one the middle of its face stands in. */
        BlockPos holder() {
            return new BlockPos(Mth.floor((leftX + rightX) / 2), 0, Mth.floor((leftZ + rightZ) / 2));
        }

        Panel holding(Desk to) {
            return new Panel(leftX, leftZ, rightX, rightZ, shows, to, leftCut, rightCut);
        }

        /** With its slope's front edge stopping so far in from either end. */
        Panel cut(double left, double right) {
            return new Panel(leftX, leftZ, rightX, rightZ, shows, desk, left, right);
        }
    }

    /**
     * A row planned: the middle screen, the pairs either side of it that fit, inner pair first, each captain first, and
     * how far each pair turns from the one inside it, in degrees.
     */
    private record Plan(Panel middle, List<Panel[]> pairs, double turn) {
        double width() {
            double width = middle.width();
            for (Panel[] pair : pairs) {
                width += pair[0].width() + pair[1].width();
            }
            return width;
        }

        /** Every panel, the middle one first. */
        List<Panel> panels() {
            List<Panel> panels = new ArrayList<>(List.of(middle));
            pairs.forEach(pair -> panels.addAll(List.of(pair)));
            return panels;
        }
    }

    /**
     * The row on the floor of row floor, its faces in station screenZ, its desks reaching back from there so far at
     * most, and clear of the given blocks across the floor plan.
     */
    ScreenArc(AirframeLayout layout, int floor, int screenZ, double deepest, Set<BlockPos> clear) {
        this.layout = layout;
        this.floor = floor;
        this.screenZ = screenZ;
        this.deepest = deepest;
        this.clear = clear;
    }

    /**
     * How much of the row fits, the more of it the more: a hundred for each pair either side of the middle screen, and
     * the panels' widths, all of them together, in blocks. 0 where not even the inner pair fits.
     */
    double fit() {
        Plan plan = best();
        return plan == null || plan.pairs().isEmpty() ? 0 : plan.pairs().size() * 100 + plan.width();
    }

    /**
     * Puts up the row and the desk behind it, the panels linked to the autopilot and the switches out of sight, at
     * their places in the layout; whether it went up, with the inner pair at least.
     */
    boolean apply(Map<Link, BlockPos> controls) {
        Plan plan = best();
        if (plan == null || plan.pairs().isEmpty()) {
            return false;
        }
        List<Panel> panels = withDesks(plan);
        // Every block a panel runs through first, then the blocks holding them, so none is taken over by another panel.
        panels.forEach(this::standUp);
        for (Panel panel : panels) {
            hold(panel, controls);
        }
        desk(plan);
        return true;
    }

    /**
     * The panels with their slopes' keys: the autopilot's under the middle screen, the parking brake and the landing
     * gear under the captain's inner panel, the tiller under the captain's outer one and the cameras' buttons under
     * the first officer's inner one, the camera's own screen; where the outer pair does not fit, the tiller goes under
     * the camera's screen instead, and the cameras' buttons stay along the bottom of its picture.
     */
    private static List<Panel> withDesks(Plan plan) {
        Plan cut = mitred(plan);
        List<Panel> panels = new ArrayList<>();
        panels.add(cut.middle().holding(Desk.AUTOPILOT));
        Panel[] inner = cut.pairs().get(0);
        boolean outer = cut.pairs().size() > 1;
        panels.add(inner[0].holding(Desk.GROUND));
        panels.add(inner[1].holding(outer ? Desk.CAMERA : Desk.TILLER));
        if (outer) {
            panels.add(cut.pairs().get(1)[0].holding(Desk.TILLER));
            panels.add(cut.pairs().get(1)[1]);
        }
        return panels;
    }

    /**
     * The row with its slopes mitred where they meet: turned toward the pilots, each panel's slope would run into its
     * neighbour's, so at every end a panel shares with one turned further, its slope's front edge stops short by as
     * much as cuts the two along the line halfway between them.
     */
    private static Plan mitred(Plan plan) {
        double cut = DESK * Math.tan(Math.toRadians(plan.turn() / 2));
        List<Panel[]> pairs = new ArrayList<>();
        for (int step = 0; step < plan.pairs().size(); step++) {
            Panel[] pair = plan.pairs().get(step);
            double outer = step + 1 < plan.pairs().size() ? cut : 0;
            pairs.add(new Panel[]{pair[0].cut(outer, cut), pair[1].cut(cut, outer)});
        }
        return new Plan(plan.middle().cut(cut, cut), pairs, plan.turn());
    }

    /** The row that fits best: the most pairs, then the widest; of equals, the widest middle and the gentlest turn. */
    @Nullable
    private Plan best() {
        Plan best = null;
        for (double middleWidth : MIDDLE_WIDTHS) {
            for (double turn : TURNS) {
                Plan plan = plan(middleWidth, turn);
                best = plan != null && better(plan, best) ? plan : best;
            }
        }
        return best;
    }

    private static boolean better(Plan plan, @Nullable Plan than) {
        if (than == null || plan.pairs().size() != than.pairs().size()) {
            return than == null || plan.pairs().size() > than.pairs().size();
        }
        return plan.width() > than.width() + 1e-6;
    }

    /**
     * The row with a middle screen this wide and pairs turning this many degrees a step that fits best: the inner pair
     * as wide as leaves the outer pair the room to fit too, if it can; null where not even the middle screen fits.
     */
    @Nullable
    private Plan plan(double middleWidth, double turn) {
        double faceZ = screenZ + FACE_BACK;
        double left = 0.5 - middleWidth / 2;
        Panel middle = new Panel(left, faceZ, left + middleWidth, faceZ, Shows.SYSTEMS, Desk.NONE, 0, 0);
        if (!fits(middle, Set.of())) {
            return null;
        }
        Plan best = new Plan(middle, List.of(), turn);
        for (double inner = WIDEST; inner > NARROWEST - NARROWER / 2; inner -= NARROWER) {
            Plan plan = withPairs(middle, turn, inner);
            best = better(plan, best) ? plan : best;
        }
        return best;
    }

    /**
     * The middle screen with an inner pair this wide, if it fits, turned this many degrees, and outside it the widest
     * outer pair that fits, turned as far again.
     */
    private Plan withPairs(Panel middle, double turn, double innerWidth) {
        Set<BlockPos> taken = new HashSet<>(blocks(middle));
        Panel[] inner = pair(0, turn, innerWidth, middle, middle);
        if (!fits(inner[0], taken) || !fits(inner[1], taken)) {
            return new Plan(middle, List.of(), turn);
        }
        taken.addAll(blocks(inner[0]));
        taken.addAll(blocks(inner[1]));
        Panel[] outer = widestPair(1, turn, inner[0], inner[1], taken);
        return new Plan(middle, outer == null ? List.<Panel[]>of(inner) : List.of(inner, outer), turn);
    }

    /**
     * The widest pair that fits at this step out from the middle, hinged on the outer edges of the panels inside it
     * and turned so many degrees a step; null for none.
     */
    @Nullable
    private Panel[] widestPair(int step, double turn, Panel captainInside, Panel firstOfficerInside, Set<BlockPos> taken) {
        for (double width = WIDEST; width > NARROWEST - NARROWER / 2; width -= NARROWER) {
            Panel[] pair = pair(step, turn, width, captainInside, firstOfficerInside);
            if (fits(pair[0], taken) && fits(pair[1], taken)) {
                return pair;
            }
        }
        return null;
    }

    /** The pair at this step out from the middle, this wide, hinged on the outer edges of the panels inside it. */
    private static Panel[] pair(int step, double turn, double width, Panel captainInside, Panel firstOfficerInside) {
        double angle = Math.toRadians(turn * (step + 1));
        double across = Math.cos(angle) * width;
        double back = Math.sin(angle) * width;
        Panel captain = new Panel(captainInside.leftX - across, captainInside.leftZ + back, captainInside.leftX, captainInside.leftZ, SHOWS[step][0],
            Desk.NONE, 0, 0);
        Panel firstOfficer = new Panel(firstOfficerInside.rightX, firstOfficerInside.rightZ, firstOfficerInside.rightX + across,
            firstOfficerInside.rightZ + back, SHOWS[step][1], Desk.NONE, 0, 0);
        return new Panel[]{captain, firstOfficer};
    }

    /** Whether every block the panel needs is open or another panel's, and its desk stays ahead of the seats. */
    private boolean fits(Panel panel, Set<BlockPos> taken) {
        double[] front = panel.out(DESK);
        if (Math.max(panel.leftZ, panel.rightZ) + Math.max(front[1], 0) > screenZ + deepest) {
            return false;
        }
        for (BlockPos block : blocks(panel)) {
            boolean free = taken.contains(block) || layout.isOpen(block.getX(), block.getY(), block.getZ());
            if (!free || clear.contains(new BlockPos(block.getX(), 0, block.getZ()))) {
                return false;
            }
        }
        return true;
    }

    /** Every block the panel needs: its desk's in the floor's row, its screen's in the row over it. */
    private List<BlockPos> blocks(Panel panel) {
        List<BlockPos> blocks = new ArrayList<>();
        panel.under().forEach(square -> blocks.add(square.offset(0, floor, 0)));
        panel.screen().forEach(square -> blocks.add(square.offset(0, floor + 1, 0)));
        return blocks;
    }

    /** The panel's blocks: every one its desk runs through, and every one its screen's face runs through over that. */
    private void standUp(Panel panel) {
        BlockState part = part(panel);
        panel.under().forEach(square -> layout.set(square.getX(), floor, square.getZ(), part));
        panel.face().forEach(square -> layout.set(square.getX(), floor + 1, square.getZ(), part));
    }

    /**
     * The block holding the panel, on the floor under the middle of its face, told when the airliner is built where the
     * panel stands from it (in the frame of the way the row faces, which with the row facing south is the layout's
     * own), what its slope holds, and where what it works is, turned with the airliner.
     */
    private void hold(Panel panel, Map<Link, BlockPos> controls) {
        BlockPos holder = panel.holder().offset(0, floor, 0);
        layout.set(holder.getX(), holder.getY(), holder.getZ(), part(panel).setValue(CockpitDisplayBlock.HOLDER, true));
        double middleX = holder.getX() + 0.5;
        double middleZ = holder.getZ() + 0.5;
        CockpitDisplayBlockEntity.Panel where = new CockpitDisplayBlockEntity.Panel((float) (panel.leftX - middleX), (float) (panel.leftZ - middleZ),
            (float) (panel.rightX - middleX), (float) (panel.rightZ - middleZ), BOTTOM, TOP, DESK, (float) panel.leftCut, (float) panel.rightCut);
        layout.fit(holder, (blockEntity, rotation) -> {
            if (blockEntity instanceof CockpitDisplayBlockEntity display) {
                display.configure(where, panel.desk(), turned(controls, holder, rotation));
            }
        });
    }

    /** Where what a panel works is, as offsets from the block holding it, turned with the airliner. */
    private static Map<Link, BlockPos> turned(Map<Link, BlockPos> controls, BlockPos holder, Rotation rotation) {
        Map<Link, BlockPos> links = new EnumMap<>(Link.class);
        controls.forEach((link, at) -> links.put(link, at.subtract(holder).rotate(rotation)));
        return links;
    }

    /**
     * The instrument panel carried on round behind the row: every open block of the floor's row that lies on the
     * nose's side of the panels, from the row's station back to its outer ends and out to the hull. Facing the nose, so
     * the instrument panel's screens, shown on its face, stay hidden against the glareshield.
     */
    private void desk(Plan plan) {
        Panel captainOuter = plan.pairs().get(plan.pairs().size() - 1)[0];
        Panel firstOfficerOuter = plan.pairs().get(plan.pairs().size() - 1)[1];
        int back = Mth.floor(Math.max(captainOuter.leftZ, firstOfficerOuter.rightZ));
        BlockState desk = AerolargeRegistry.INSTRUMENT_PANEL.get().defaultBlockState().setValue(InstrumentPanelBlock.FACING, Direction.NORTH);
        for (BlockPos block : BlockPos.betweenClosed(Mth.floor(captainOuter.leftX) - 1, floor, screenZ, Mth.floor(firstOfficerOuter.rightX) + 1,
            floor, back)) {
            if (layout.isOpen(block.getX(), block.getY(), block.getZ()) && behind(plan, block.getX() + 0.5, block.getZ() + 0.5)) {
                layout.set(block.getX(), block.getY(), block.getZ(), desk);
            }
        }
    }

    /**
     * Whether a point of the floor plan lies on the nose's side of the row: behind a panel, or past the row's outer
     * end and ahead of it.
     */
    private static boolean behind(Plan plan, double x, double z) {
        boolean captainSide = x < plan.middle().leftX;
        Panel outer = plan.pairs().get(plan.pairs().size() - 1)[captainSide ? 0 : 1];
        double outerX = captainSide ? outer.leftX : outer.rightX;
        double outerZ = captainSide ? outer.leftZ : outer.rightZ;
        if (captainSide ? x < outerX : x > outerX) {
            return z < outerZ;
        }
        for (Panel panel : plan.panels()) {
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
