package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.Mount;
import dev.skyliners.block.NavLightBlock;
import dev.skyliners.block.ShellBlock;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The freighter's outside lights, so she shows by night: a glowing band all round her rim, cut to its curve, where her
 * skin is unseen from inside (see {@link FreighterPlating#unseen}), but where her engines' grille, her docking collars
 * and her cockpit's tube break it; light strips (see {@link dev.skyliners.exterior.HullFittingBlock}) along both walls
 * of her loading gap and across its back, as a landing bay is lit, and along the outer edge of each prong's top; a red
 * navigation light on her port beam and a green one on her starboard, a white one at her stern over her engines; white
 * strobes on her prongs' tips; and red beacons blinking on her top and under her belly (see {@link NavLightBlock}).
 * Each strip and light meets her hull face to face, on a block of her skin made whole (see
 * {@link FreighterExterior#pad}). None with the server config's {@code freighter.outsideLights} off.
 */
final class FreighterLights {
    /** The row her light band runs along, in blocks over her feet's soles: the top row of her upright rim. */
    private static final int BAND_ROW = (int) Math.round((FreighterShape.MIDDLE + FreighterShape.RIM_HALF) * FreighterLayout.SCALE) - 1;
    /** How far round her the band is looked along at a time, in degrees, so no block of her rim is missed. */
    private static final double BAND_STEP = 0.5;
    /** The row her loading gap's strips run along, in blocks over her feet's soles: level with her rim's middle. */
    private static final int GAP_ROW = (int) Math.round(FreighterShape.MIDDLE * FreighterLayout.SCALE);
    /** How far in from a prong's outer edge its strip runs, in metres, on the flat of its top. */
    private static final double STRIP_IN = 0.6;
    /** How far back from a prong's tip its strip and her gap's stop, in metres, clear of her retros' exhaust. */
    private static final double TIP_CLEAR = 1;
    /** How far in from a prong's outer edge its strobe stands at its tip, in metres. */
    private static final double STROBE_IN = 0.6;
    /** How far astern of her axis her top beacon stands and how far ahead of it her belly's, in metres. */
    private static final double TOP_BEACON_ASTERN = 4;
    private static final double BELLY_BEACON_AHEAD = 3;

    private FreighterLights() {
    }

    /** Her outside lights, where her hull has room for each. */
    static void build(FreighterLayout layout) {
        if (!SkylinersConfig.FREIGHTER_OUTSIDE_LIGHTS.get()) {
            return;
        }
        band(layout);
        gapStrips(layout);
        prongStrips(layout);
        navigationLights(layout);
    }

    /** Her light band in place of the outermost block of her rim's top row, all round her, where it is plain hull. */
    private static void band(FreighterLayout layout) {
        BlockState band = SkylinersRegistry.HULL_LIGHT_BAND.get().defaultBlockState();
        for (double bearing = 0; bearing < 360; bearing += BAND_STEP) {
            BlockPos at = outermost(layout, bearing, BAND_ROW);
            BlockState there = at == null ? null : layout.stateAt(at.getX(), at.getY(), at.getZ());
            if (there != null && !there.is(band.getBlock()) && FreighterExterior.holds(layout, at.getX(), at.getY(), at.getZ())
                && FreighterPlating.unseen(at)) {
                layout.set(at.getX(), at.getY(), at.getZ(), ShellBlock.withShape(band, ShellBlock.shape(there)));
            }
        }
    }

    /** The outermost of her blocks at a bearing and a row, walking in from past her rim; null where there is none. */
    @Nullable
    private static BlockPos outermost(FreighterLayout layout, double bearing, int y) {
        double[] way = FreighterLayout.way(bearing);
        for (int k = layout.airBox().maxZ() + 1; k > 0; k--) {
            int x = (int) Math.round(way[0] * k);
            int z = (int) Math.round(way[1] * k);
            if (!layout.isOpen(x, y, z)) {
                return new BlockPos(x, y, z);
            }
        }
        return null;
    }

    /**
     * Light strips along both walls of her loading gap, facing into it, from its back wall to near her prongs' tips,
     * and across its back wall, facing out of it.
     */
    private static void gapStrips(FreighterLayout layout) {
        int back = (int) Math.round(FreighterShape.GAP_BACK * FreighterLayout.SCALE);
        int tip = (int) Math.round((FreighterShape.PRONG_TIP + TIP_CLEAR) * FreighterLayout.SCALE);
        for (int z = back; z >= tip; z--) {
            alongGapWall(layout, 1, z);
            alongGapWall(layout, -1, z);
        }
        int across = (int) Math.floor(FreighterShape.GAP_HALF * FreighterLayout.SCALE);
        for (int x = -across; x <= across; x++) {
            acrossGapBack(layout, x, tip);
        }
    }

    /** A strip on one wall of her loading gap, so far astern, in the open air by the wall, facing across the gap. */
    private static void alongGapWall(FreighterLayout layout, int side, int z) {
        for (int x = 0; Math.abs(x) <= layout.airBox().maxX(); x += side) {
            if (!layout.isOpen(x, GAP_ROW, z)) {
                strip(layout, x == 0 ? null : new BlockPos(x, GAP_ROW, z), Mount.WALL, side > 0 ? Direction.WEST : Direction.EAST);
                return;
            }
        }
    }

    /** A strip on the back wall of her loading gap, in a column across it, walking back from near her prongs' tips. */
    private static void acrossGapBack(FreighterLayout layout, int x, int tip) {
        for (int z = tip; z < 0; z++) {
            if (!layout.isOpen(x, GAP_ROW, z)) {
                strip(layout, z == tip ? null : new BlockPos(x, GAP_ROW, z), Mount.WALL, Direction.NORTH);
                return;
            }
        }
    }

    /**
     * A strip fixed to the block of hers given, facing out from it, where her hull holds it (see
     * {@link FreighterExterior#against}); none where null.
     */
    private static void strip(FreighterLayout layout, @Nullable BlockPos wall, Mount mount, Direction facing) {
        BlockPos at = wall == null ? null : FreighterExterior.against(layout, wall, facing.getOpposite());
        if (at != null) {
            layout.set(at.getX(), at.getY(), at.getZ(), SkylinersRegistry.HULL_LIGHT_STRIP.get().mounted(mount, facing));
            FreighterExterior.pad(layout, at, facing.getOpposite());
        }
    }

    /** A strip along the outer edge of each prong's top, from where it meets her disc to near its tip. */
    private static void prongStrips(FreighterLayout layout) {
        int root = (int) Math.round(FreighterShape.PRONG_ROOT * FreighterLayout.SCALE);
        int tip = (int) Math.round((FreighterShape.PRONG_TIP + TIP_CLEAR) * FreighterLayout.SCALE);
        for (int z = root; z >= tip; z--) {
            int x = (int) Math.round((edge(z) - STRIP_IN) * FreighterLayout.SCALE);
            onProng(layout, x, z, Direction.EAST);
            onProng(layout, -x, z, Direction.WEST);
        }
    }

    /** A strip standing on a prong's top in a column, running along the prong, where her hull holds it. */
    private static void onProng(FreighterLayout layout, int x, int z, Direction facing) {
        BlockPos at = FreighterExterior.onTop(layout, x, z);
        if (at != null) {
            layout.set(at.getX(), at.getY(), at.getZ(), SkylinersRegistry.HULL_LIGHT_STRIP.get().mounted(Mount.FLOOR, facing));
            FreighterExterior.pad(layout, at, Direction.DOWN);
        }
    }

    /** How far out from her middle line a prong's outer edge is so far astern, in blocks, in metres. */
    private static double edge(int z) {
        double astern = z / FreighterLayout.SCALE;
        double along = Math.clamp((astern - FreighterShape.PRONG_TIP) / (FreighterShape.PRONG_ROOT - FreighterShape.PRONG_TIP), 0, 1);
        return FreighterShape.PRONG_OUT_TIP + (FreighterShape.PRONG_OUT_ROOT - FreighterShape.PRONG_OUT_TIP) * along;
    }

    /**
     * Her navigation lights: red on her port beam and green on her starboard, on her light band; white at her stern
     * over her engines; strobes on her prongs' tips; and beacons on her top and under her belly.
     */
    private static void navigationLights(FreighterLayout layout) {
        beam(layout, -1, NavLightBlock.Kind.RED);
        beam(layout, 1, NavLightBlock.Kind.GREEN);
        BlockPos stern = outermost(layout, 180, BAND_ROW + 1);
        if (stern != null) {
            light(layout, FreighterExterior.against(layout, stern, Direction.NORTH), Direction.SOUTH, NavLightBlock.Kind.WHITE);
        }
        int strobeX = (int) Math.round((FreighterShape.PRONG_OUT_TIP - STROBE_IN) * FreighterLayout.SCALE);
        int strobeZ = (int) Math.round((FreighterShape.PRONG_TIP + STROBE_IN) * FreighterLayout.SCALE);
        light(layout, FreighterExterior.onTop(layout, strobeX, strobeZ), Direction.UP, NavLightBlock.Kind.STROBE);
        light(layout, FreighterExterior.onTop(layout, -strobeX, strobeZ), Direction.UP, NavLightBlock.Kind.STROBE);
        int topZ = (int) Math.round(TOP_BEACON_ASTERN * FreighterLayout.SCALE);
        light(layout, FreighterExterior.onTop(layout, 0, topZ), Direction.UP, NavLightBlock.Kind.BEACON);
        int bellyZ = (int) -Math.round(BELLY_BEACON_AHEAD * FreighterLayout.SCALE);
        light(layout, FreighterExterior.underneath(layout, 0, bellyZ), Direction.DOWN, NavLightBlock.Kind.BEACON);
    }

    /** A navigation light on her rim's top row on one beam, port (-1) or starboard (1), shining out from her. */
    private static void beam(FreighterLayout layout, int side, NavLightBlock.Kind kind) {
        BlockPos rim = outermost(layout, side > 0 ? 90 : 270, BAND_ROW);
        Direction out = side > 0 ? Direction.EAST : Direction.WEST;
        if (rim != null) {
            light(layout, FreighterExterior.against(layout, rim, out.getOpposite()), out, kind);
        }
    }

    /**
     * A navigation light of a kind where given, where her hull holds it (see {@link FreighterExterior#against}),
     * shining the way given; none where null.
     */
    private static void light(FreighterLayout layout, @Nullable BlockPos at, Direction shines, NavLightBlock.Kind kind) {
        if (at != null) {
            layout.set(at.getX(), at.getY(), at.getZ(), SkylinersRegistry.NAV_LIGHT.get().defaultBlockState()
                .setValue(NavLightBlock.FACING, shines).setValue(NavLightBlock.KIND, kind));
            FreighterExterior.pad(layout, at, shines.getOpposite());
        }
    }
}
