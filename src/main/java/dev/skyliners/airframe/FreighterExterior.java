package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.Mount;
import dev.skyliners.block.ShellBlock;
import dev.skyliners.block.ShellGeometry;
import dev.skyliners.exterior.HullFittingBlock;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The freighter's outside fitted out with the fleet's outside fittings (see {@link HullFittingBlock}), laid once the
 * rest of her stands: each piece in the open sky just outside her skin and face to face with it, so her assembly takes
 * it along as she lifts off, the block of her skin it stands against made whole (see {@link #pad}), so it meets her
 * hull rather than standing over her skin's cut. Her top carries a big satellite dish turning port of her stern, a farm
 * of antenna masts off her port bow and a pair to starboard, sensor domes, banks of heat-sink fins either side of her
 * stern over her engines with exhaust vents between them and banks of vent grilles inboard of them, and runs of
 * machinery out from her middle with runs of grilles beside them; her prongs' flat tops more runs of machinery, fins
 * and grilles, vents, a mast and a dome; her upright rim a small dish sweeping off her port side, whip antennas, runs
 * of conduit and of grilles, vents, fins and floodlights; and under her belly and her prongs floodlights, a radome, a
 * small dish, a blade antenna, tanks and grilles under her engines. Each is where it keeps clear of her ramp, her legs,
 * her thrusters' plumes and her cannon's level sweep over her top, and stands on solid hull, never glass, her grille or
 * an engine: where it would not, or another piece already stands there, it is left out. None with the server config's
 * {@code freighter.exterior} off.
 */
final class FreighterExterior {
    /** How far over the ground under her feet her rim's fittings are fixed, in metres: the middle of her rim. */
    private static final double RIM_UP = FreighterShape.MIDDLE;
    /** How far out from her axis a rim's piece is looked for from, in metres: past the outermost part of her hull. */
    private static final double PAST_HER = FreighterShape.SIDE_REACH + 1;
    /** The lowest row a piece may hang in under her belly, in blocks over her feet's soles: clear of the ground. */
    private static final int LOWEST_HANGING = 2;
    /**
     * How far apart the pieces of a bank or a run stand, in metres: a block, so they stand side by side as one radiator
     * or one run of works.
     */
    private static final double APART = 1 / FreighterLayout.SCALE;
    /**
     * Each piece and where it goes, in metres from her axis: on her top and under her belly at a bearing clockwise from
     * her bow and so far out, singly, in a bank round her at the same distance out, or in a run out from her axis; on
     * her prongs so far to her right and astern; on her rim on her port side (-1) or her starboard (1), so far astern.
     */
    private static final List<Spot> SPOTS = Stream.of(
        List.of(top(SkylinersRegistry.BIG_SATELLITE_DISH, 215, 8.0), top(SkylinersRegistry.SENSOR_DOME, 332, 7.0),
            top(SkylinersRegistry.SENSOR_DOME, 60, 6.0)),
        List.of(top(SkylinersRegistry.ANTENNA_MAST, 300, 9.6), top(SkylinersRegistry.ANTENNA_MAST, 306, 10.4),
            top(SkylinersRegistry.ANTENNA_MAST, 294, 10.6), top(SkylinersRegistry.ANTENNA_MAST, 128, 9.8),
            top(SkylinersRegistry.ANTENNA_MAST, 122, 10.6)),
        bank(SkylinersRegistry.HEAT_SINK, 150, 172, 10.6),
        bank(SkylinersRegistry.HEAT_SINK, 188, 210, 10.6),
        List.of(top(SkylinersRegistry.EXHAUST_VENT, 180, 9.6), top(SkylinersRegistry.EXHAUST_VENT, 176, 8.6),
            top(SkylinersRegistry.EXHAUST_VENT, 184, 8.6), top(SkylinersRegistry.EXHAUST_VENT, 245, 9.2),
            top(SkylinersRegistry.EXHAUST_VENT, 250, 9.8), top(SkylinersRegistry.EXHAUST_VENT, 158, 7.6),
            top(SkylinersRegistry.EXHAUST_VENT, 202, 7.6)),
        bank(SkylinersRegistry.VENT_GRILLE, 154, 170, 9.4),
        bank(SkylinersRegistry.VENT_GRILLE, 190, 206, 9.4),
        run(SkylinersRegistry.HULL_MACHINERY, 95, 5.5, 8.5),
        run(SkylinersRegistry.HULL_MACHINERY, 262, 5.0, 8.0),
        run(SkylinersRegistry.HULL_MACHINERY, 30, 5.0, 7.5),
        run(SkylinersRegistry.VENT_GRILLE, 100, 5.5, 8.0),
        run(SkylinersRegistry.VENT_GRILLE, 267, 5.0, 7.5),
        prongRun(SkylinersRegistry.HEAT_SINK, -3.7, -12.2, -13.4),
        prongRun(SkylinersRegistry.HULL_MACHINERY, -3.7, -14.6, -16.2),
        prongRun(SkylinersRegistry.EXHAUST_VENT, -3.6, -17.6, -18.4),
        List.of(prong(SkylinersRegistry.ANTENNA_MAST, 3.7, -12.9), prong(SkylinersRegistry.SENSOR_DOME, 3.6, -17.2),
            prong(SkylinersRegistry.EXHAUST_VENT, 3.6, -18.4)),
        prongRun(SkylinersRegistry.HULL_MACHINERY, 3.7, -14.2, -15.8),
        prongRun(SkylinersRegistry.VENT_GRILLE, -2.6, -12.4, -13.6),
        prongRun(SkylinersRegistry.VENT_GRILLE, 2.6, -16.4, -17.6),
        List.of(rim(SkylinersRegistry.SATELLITE_DISH, -1, -1.0), rim(SkylinersRegistry.ANTENNA_MAST, -1, -2.6),
            rim(SkylinersRegistry.HULL_FLOODLIGHT, -1, -5.8), rim(SkylinersRegistry.HULL_FLOODLIGHT, 1, -1.0),
            rim(SkylinersRegistry.ANTENNA_MAST, 1, -5.4), rim(SkylinersRegistry.EXHAUST_VENT, 1, 6.0),
            rim(SkylinersRegistry.HEAT_SINK, -1, 6.0)),
        rimRun(SkylinersRegistry.HULL_MACHINERY, -1, -3.8, -4.6),
        rimRun(SkylinersRegistry.HULL_MACHINERY, 1, -2.2, -3.8),
        rimRun(SkylinersRegistry.VENT_GRILLE, -1, 5.4, 4.6),
        rimRun(SkylinersRegistry.VENT_GRILLE, 1, 5.4, 4.6),
        List.of(under(SkylinersRegistry.HULL_FLOODLIGHT, 45, 10.3), under(SkylinersRegistry.HULL_FLOODLIGHT, 135, 10.3),
            under(SkylinersRegistry.HULL_FLOODLIGHT, 225, 10.3), under(SkylinersRegistry.HULL_FLOODLIGHT, 315, 10.3),
            underProng(SkylinersRegistry.HULL_FLOODLIGHT, -3.6, -20.0), underProng(SkylinersRegistry.HULL_FLOODLIGHT, 3.6, -20.0),
            under(SkylinersRegistry.SENSOR_DOME, 270, 5.0), under(SkylinersRegistry.SATELLITE_DISH, 200, 5.0),
            under(SkylinersRegistry.ANTENNA_MAST, 160, 5.0), under(SkylinersRegistry.HULL_MACHINERY, 330, 5.0),
            under(SkylinersRegistry.HULL_MACHINERY, 20, 5.0), under(SkylinersRegistry.VENT_GRILLE, 170, 8.0),
            under(SkylinersRegistry.VENT_GRILLE, 190, 8.0))
    ).flatMap(List::stream).toList();

    private FreighterExterior() {
    }

    /**
     * A piece, how it is put up, and its column: so far to her right of her axis and astern of it, in metres, either
     * negative for her left and toward her bow.
     */
    private record Spot(Supplier<? extends HullFittingBlock> piece, Mount mount, double right, double astern) {
    }

    /** A piece standing on her top at a bearing clockwise from her bow, so far out from her axis, in metres. */
    private static Spot top(Supplier<? extends HullFittingBlock> piece, double bearing, double out) {
        double[] way = FreighterLayout.way(bearing);
        return new Spot(piece, Mount.FLOOR, way[0] * out, way[1] * out);
    }

    /** A piece hung under her belly at a bearing clockwise from her bow, so far out from her axis, in metres. */
    private static Spot under(Supplier<? extends HullFittingBlock> piece, double bearing, double out) {
        double[] way = FreighterLayout.way(bearing);
        return new Spot(piece, Mount.CEILING, way[0] * out, way[1] * out);
    }

    /** A piece standing on a prong's flat top, so far to her right of her middle line and astern of her axis. */
    private static Spot prong(Supplier<? extends HullFittingBlock> piece, double right, double astern) {
        return new Spot(piece, Mount.FLOOR, right, astern);
    }

    /** A piece hung under a prong, so far to her right of her middle line and astern of her axis. */
    private static Spot underProng(Supplier<? extends HullFittingBlock> piece, double right, double astern) {
        return new Spot(piece, Mount.CEILING, right, astern);
    }

    /** A piece fixed to her rim on her port side (-1) or her starboard side (1), so far astern of her axis. */
    private static Spot rim(Supplier<? extends HullFittingBlock> piece, int side, double astern) {
        return new Spot(piece, Mount.WALL, side * PAST_HER, astern);
    }

    /** A bank of pieces side by side on her top, from one bearing round to another, so far out from her axis. */
    private static List<Spot> bank(Supplier<? extends HullFittingBlock> piece, double from, double to, double out) {
        double step = Math.toDegrees(APART / out);
        int count = (int) Math.floor((to - from) / step) + 1;
        return IntStream.range(0, count).mapToObj(i -> top(piece, from + i * step, out)).toList();
    }

    /** A run of pieces on her top along a bearing, from so far out from her axis to so far. */
    private static List<Spot> run(Supplier<? extends HullFittingBlock> piece, double bearing, double from, double to) {
        int count = (int) Math.floor((to - from) / APART) + 1;
        return IntStream.range(0, count).mapToObj(i -> top(piece, bearing, from + i * APART)).toList();
    }

    /** A run of pieces along a prong's top, so far to her right, from so far astern of her axis to so far ahead. */
    private static List<Spot> prongRun(Supplier<? extends HullFittingBlock> piece, double right, double from, double to) {
        int count = (int) Math.floor((from - to) / APART) + 1;
        return IntStream.range(0, count).mapToObj(i -> prong(piece, right, from - i * APART)).toList();
    }

    /** A run of pieces along her rim on one side of her, from so far astern of her axis to so far ahead. */
    private static List<Spot> rimRun(Supplier<? extends HullFittingBlock> piece, int side, double from, double to) {
        int count = (int) Math.floor((from - to) / APART) + 1;
        return IntStream.range(0, count).mapToObj(i -> rim(piece, side, from - i * APART)).toList();
    }

    /** Her outside fittings, where her hull has room for each. */
    static void build(FreighterLayout layout) {
        if (!SkylinersConfig.FREIGHTER_EXTERIOR.get()) {
            return;
        }
        for (Spot spot : SPOTS) {
            place(layout, spot);
        }
    }

    /** One piece, where her hull holds it, facing out from her axis, or out from her rim. */
    private static void place(FreighterLayout layout, Spot spot) {
        int x = (int) Math.round(spot.right() * FreighterLayout.SCALE);
        int z = (int) Math.round(spot.astern() * FreighterLayout.SCALE);
        BlockPos at = switch (spot.mount()) {
            case FLOOR -> onTop(layout, x, z);
            case CEILING -> underneath(layout, x, z);
            case WALL -> beside(layout, Integer.signum(x), z);
        };
        if (at == null) {
            return;
        }
        Direction outFromRim = at.getX() > 0 ? Direction.EAST : Direction.WEST;
        Direction facing = spot.mount() == Mount.WALL ? outFromRim : Direction.getNearest(at.getX(), 0, at.getZ());
        Direction against = switch (spot.mount()) {
            case FLOOR -> Direction.DOWN;
            case CEILING -> Direction.UP;
            case WALL -> facing.getOpposite();
        };
        layout.set(at.getX(), at.getY(), at.getZ(), spot.piece().get().mounted(spot.mount(), facing));
        pad(layout, at, against);
    }

    /**
     * Makes the block of her skin a piece stands against, the way given from the piece, whole where it was cut to her
     * curve: a pad the piece meets face to face. Standing on the edge of its block over the cut, it would show a gap
     * all round under it.
     */
    static void pad(FreighterLayout layout, BlockPos piece, Direction toward) {
        BlockPos at = piece.relative(toward);
        BlockState skin = layout.stateAt(at.getX(), at.getY(), at.getZ());
        if (ShellBlock.shape(skin) != ShellGeometry.WHOLE && ShellBlock.takes(skin, ShellGeometry.WHOLE)) {
            layout.set(at.getX(), at.getY(), at.getZ(), ShellBlock.withShape(skin, ShellGeometry.WHOLE));
        }
    }

    /** Where a piece stands on the topmost of her blocks in a column (see {@link #against}); null where it cannot. */
    @Nullable
    static BlockPos onTop(FreighterLayout layout, int x, int z) {
        for (int y = layout.airBox().maxY(); y > 0; y--) {
            if (!layout.isOpen(x, y, z)) {
                return against(layout, new BlockPos(x, y, z), Direction.DOWN);
            }
        }
        return null;
    }

    /** Where a piece hangs under the lowest of her blocks in a column (see {@link #against}); null where it cannot. */
    @Nullable
    static BlockPos underneath(FreighterLayout layout, int x, int z) {
        for (int y = LOWEST_HANGING + 1; y <= layout.airBox().maxY(); y++) {
            if (!layout.isOpen(x, y, z)) {
                return against(layout, new BlockPos(x, y, z), Direction.UP);
            }
        }
        return null;
    }

    /**
     * Where a piece is fixed to her rim on one side of her, level with its middle, so far astern, walking in from past
     * her (see {@link #against}); null where it cannot be.
     */
    @Nullable
    private static BlockPos beside(FreighterLayout layout, int side, int z) {
        int y = (int) Math.round(RIM_UP * FreighterLayout.SCALE);
        for (int x = side * layout.airBox().maxX(); x != 0; x -= side) {
            if (!layout.isOpen(x, y, z)) {
                return against(layout, new BlockPos(x, y, z), side > 0 ? Direction.WEST : Direction.EAST);
            }
        }
        return null;
    }

    /**
     * Where a piece goes against the first of her blocks met walking in toward her a way: the open block before it,
     * where that block is her solid hull; or, where it is only a sliver of her skin, its middle out in the open sky
     * over her curve, in place of the sliver, against her hull behind it. So a piece stands at most half a block off
     * her true surface once the block it stands against is made whole (see {@link #pad}), rather than as much as a
     * block over a sliver. Null where neither is her solid hull, or the block before it is taken.
     */
    @Nullable
    static BlockPos against(FreighterLayout layout, BlockPos first, Direction walking) {
        BlockPos behind = first.relative(walking);
        if (sliver(layout, first) && holds(layout, behind.getX(), behind.getY(), behind.getZ())) {
            return first;
        }
        BlockPos before = first.relative(walking.getOpposite());
        boolean free = layout.isOpen(before.getX(), before.getY(), before.getZ());
        return free && holds(layout, first.getX(), first.getY(), first.getZ()) ? outside(layout, before) : null;
    }

    /** Whether a block of hers is only a sliver of her solid hull, cut to her curve, its middle in the open sky. */
    private static boolean sliver(FreighterLayout layout, BlockPos at) {
        BlockState there = layout.stateAt(at.getX(), at.getY(), at.getZ());
        return ShellBlock.shape(there) != ShellGeometry.WHOLE && holds(layout, at.getX(), at.getY(), at.getZ()) && outside(layout, at) != null;
    }

    /**
     * Whether a block of hers holds a piece against it: her skin, solid, not glass, over her disc or a prong rather
     * than her cockpit, a dome or a docking collar.
     */
    static boolean holds(FreighterLayout layout, int x, int y, int z) {
        BlockState there = layout.stateAt(x, y, z);
        FreighterShape.Part part = FreighterShape.nearest(x / FreighterLayout.SCALE, (y + 0.5) / FreighterLayout.SCALE, z / FreighterLayout.SCALE);
        boolean onDiscOrProng = part == FreighterShape.Part.DISC || part == FreighterShape.Part.PRONG;
        return HullLayout.skin(there) && there.canOcclude() && onDiscOrProng;
    }

    /** A block, if it lies in the open sky outside her hull; null if it lies inside it. */
    @Nullable
    static BlockPos outside(FreighterLayout layout, BlockPos at) {
        return layout.surface().depth(at.getX(), at.getY() + 0.5, at.getZ()) > 0 ? at : null;
    }
}
