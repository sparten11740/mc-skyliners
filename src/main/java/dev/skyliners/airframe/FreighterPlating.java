package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.ShellBlock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The freighter's plating: her cover outside, where nobody inside her sees it, swapped here and there for the fleet's
 * hull plates (see {@link ShellBlock}), each cut to her curve as the cover there was, so her outer ring and her rim are
 * not one flat skin. Riveted seams run out from her middle every {@link #SEAM_EVERY} degrees, over her top, down her
 * rim and under her belly; between them, a few sectors are dark gunmetal plate; welded patches are scattered over the
 * rest; and hazard stripes ring her docking collars and run along the foot of her rim under her engines. Her skin
 * over her rooms and round the passages to her cockpit and along her prongs stays as it was, as her rooms see it from
 * inside. None with the server config's {@code freighter.plating} off.
 */
final class FreighterPlating {
    /** How far past her outer bulkhead her skin is plated from, in blocks: past it her skin closes off her void. */
    private static final double PAST_BULKHEAD = 1;
    /** How far from the solids her passages follow her skin must be to be plated, in blocks. */
    private static final double CLEAR_OF_PASSAGES = 2;
    /** How far apart her seams are round her, in degrees, and how wide each is, in blocks. */
    private static final int SEAM_EVERY = 30;
    private static final double SEAM_WIDTH = 1;
    /** The sectors between her seams plated dark, counted clockwise from the one starting at her bow. */
    private static final Set<Integer> DARK_SECTORS = Set.of(1, 4, 7, 10);
    /** One block of plain cover in so many is a welded patch, picked by where it is. */
    private static final int PATCHED_ONE_IN = 17;
    /** Her engines' arc of her stern, between these bearings, in degrees, along whose rim's foot hazard stripes run. */
    private static final int ENGINES_FROM = 150;
    private static final int ENGINES_TO = 210;
    /** Large primes mixing a block's place into which blocks are patched. */
    private static final int[] MIXING = {73856093, 19349663, 83492791};

    private FreighterPlating() {
    }

    /** Her plating, in place of her cover where it is unseen from inside. */
    static void build(FreighterLayout layout) {
        if (!SkylinersConfig.FREIGHTER_PLATING.get()) {
            return;
        }
        BlockState cover = SkylinersRegistry.FREIGHTER_COVER.get().defaultBlockState();
        for (Map.Entry<BlockPos, BlockState> cell : List.copyOf(layout.cells.entrySet())) {
            BlockState plate = cell.getValue().is(cover.getBlock()) && unseen(cell.getKey()) ? plate(cell.getKey()) : null;
            if (plate != null) {
                BlockPos at = cell.getKey();
                layout.set(at.getX(), at.getY(), at.getZ(), ShellBlock.withShape(plate, ShellBlock.shape(cell.getValue())));
            }
        }
    }

    /**
     * Whether nobody inside her sees a block of her skin: out past her outer bulkhead, over the closed void out to her
     * rim, and clear of the passages crossing it.
     */
    static boolean unseen(BlockPos at) {
        double bulkhead = FreighterLayout.BULKHEAD * FreighterLayout.SCALE;
        return Math.hypot(at.getX(), at.getZ()) > bulkhead + PAST_BULKHEAD && passageDepth(at) > CLEAR_OF_PASSAGES;
    }

    /** How far a block's middle lies outside the nearest of the solids her passages follow, in blocks. */
    private static double passageDepth(BlockPos at) {
        double scale = FreighterLayout.SCALE;
        double least = Double.MAX_VALUE;
        for (FreighterShape.Part part : List.of(FreighterShape.Part.TUBE, FreighterShape.Part.POD, FreighterShape.Part.PRONG)) {
            least = Math.min(least, FreighterShape.depth(part, at.getX() / scale, (at.getY() + 0.5) / scale, at.getZ() / scale) * scale);
        }
        return least;
    }

    /** The plate a block of her cover becomes; null where it stays cover. */
    @Nullable
    private static BlockState plate(BlockPos at) {
        double scale = FreighterLayout.SCALE;
        FreighterShape.Part part = FreighterShape.nearest(at.getX() / scale, (at.getY() + 0.5) / scale, at.getZ() / scale);
        double bearing = Math.toDegrees(Math.atan2(at.getX(), -at.getZ()));
        double round = (bearing + 360) % 360;
        int rimFoot = (int) Math.round((FreighterShape.MIDDLE - FreighterShape.RIM_HALF) * scale);
        boolean underEngines = at.getY() == rimFoot && round >= ENGINES_FROM && round <= ENGINES_TO;
        if (part == FreighterShape.Part.COLLAR || underEngines) {
            return SkylinersRegistry.HAZARD_PLATE.get().defaultBlockState();
        }
        double offSeam = Math.abs(round - SEAM_EVERY * Math.round(round / SEAM_EVERY));
        if (Math.toRadians(offSeam) * Math.hypot(at.getX(), at.getZ()) < SEAM_WIDTH / 2) {
            return SkylinersRegistry.HULL_PLATE_RIVETED.get().defaultBlockState();
        }
        if (DARK_SECTORS.contains((int) (round / SEAM_EVERY))) {
            return SkylinersRegistry.HULL_PLATE_DARK.get().defaultBlockState();
        }
        int mixed = at.getX() * MIXING[0] ^ at.getY() * MIXING[1] ^ at.getZ() * MIXING[2];
        return Math.floorMod(mixed, PATCHED_ONE_IN) == 0 ? SkylinersRegistry.HULL_PLATE_PATCHED.get().defaultBlockState() : null;
    }
}
