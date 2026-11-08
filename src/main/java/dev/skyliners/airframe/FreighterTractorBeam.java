package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.beam.BeamPadBlock;
import dev.skyliners.beam.TractorBeamBlock;
import dev.skyliners.block.Mount;
import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The freighter's tractor beam (see {@link TractorBeamBlock}): hung under her belly beneath her cargo hold, and its pad
 * (see {@link BeamPadBlock}) laid into the hold's floor straight over it, where whatever the beam lifts arrives and
 * whoever steps on it is let down. The pad goes where the hold's floor has nothing but air under it down to her belly
 * and room over it to stand, nearest a spot to port of her bow leg, kept clear of what her rooms are furnished with
 * after; the beam where her belly holds it under the pad, clear of her legs. Laid out nose north, as the rest of her;
 * none with the server config's {@code freighter.tractorBeam} off.
 */
final class FreighterTractorBeam {
    /** Her cargo hold, by the bearing of the room wall on its anticlockwise side, in degrees clockwise from her bow. */
    private static final int CARGO_HOLD = 330;
    /**
     * Where the pad is meant to be: so far to her right of her middle line and astern of her axis, in blocks, negative
     * for her left and toward her bow; between her pallet racks, past the crates, beside her bow leg.
     */
    private static final int PAD_RIGHT = -2;
    private static final int PAD_ASTERN = -17;
    /** How far the pad may be moved from where it is meant, either way, to find it room, in blocks. */
    private static final int NUDGE = 3;
    /** How far the beam's column keeps from any block of her legs' feet, in blocks, clear of the struts. */
    private static final int CLEAR_OF_LEGS = 3;
    /** How many rows over the pad are kept open, for whoever arrives on it to stand in. */
    private static final int HEADROOM = 2;

    private FreighterTractorBeam() {
    }

    /** Lays the beam and its pad in a freighter's layout whose deck's floor is the row given. */
    static void build(FreighterLayout layout, int deck) {
        if (!SkylinersConfig.FREIGHTER_TRACTOR_BEAM.get()) {
            return;
        }
        BlockPos meant = new BlockPos(PAD_RIGHT, deck, PAD_ASTERN);
        Iterable<BlockPos> near = BlockPos.betweenClosed(meant.offset(-NUDGE, 0, -NUDGE), meant.offset(NUDGE, 0, NUDGE));
        List<BlockPos> spots = StreamSupport.stream(near.spliterator(), false).map(BlockPos::immutable)
            .sorted(Comparator.comparingDouble(at -> at.distSqr(meant))).toList();
        for (BlockPos pad : spots) {
            BlockPos beam = padFits(layout, deck, pad) ? hangsUnder(layout, pad) : null;
            if (beam != null) {
                lay(layout, deck, pad, beam);
                return;
            }
        }
    }

    /**
     * The pad in the floor, the rows over it kept open, and the beam hung under her belly, the skin over it made whole.
     */
    private static void lay(FreighterLayout layout, int deck, BlockPos pad, BlockPos beam) {
        layout.set(pad.getX(), deck, pad.getZ(), SkylinersRegistry.BEAM_PAD.get().defaultBlockState());
        for (int row = 1; row <= HEADROOM; row++) {
            layout.keepClear(pad.getX(), deck + row, pad.getZ());
        }
        BlockState hung = SkylinersRegistry.TRACTOR_BEAM.get().defaultBlockState().setValue(TractorBeamBlock.MOUNT, Mount.CEILING);
        layout.set(beam.getX(), beam.getY(), beam.getZ(), hung);
        FreighterExterior.pad(layout, beam, Direction.UP);
    }

    /**
     * Whether the pad fits at a block of her deck's floor: her cargo hold's floor, open over it for whoever arrives to
     * stand, and nothing but air under it down to her belly's skin.
     */
    private static boolean padFits(HullLayout layout, int deck, BlockPos at) {
        boolean holdFloor = FreighterFloors.isRoomFloor(layout.stateAt(at.getX(), deck, at.getZ()));
        if (!holdFloor || !FreighterFurnishings.inRoom(CARGO_HOLD, at.above())) {
            return false;
        }
        for (int row = 1; row <= HEADROOM; row++) {
            if (!layout.isOpen(at.getX(), deck + row, at.getZ())) {
                return false;
            }
        }
        return clearToBelly(layout, deck, at);
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

    /**
     * Where the beam hangs under her belly beneath the pad, clear of her legs (see {@link
     * FreighterExterior#underneath}).
     */
    @Nullable
    private static BlockPos hangsUnder(FreighterLayout layout, BlockPos pad) {
        boolean clear = FreighterLayout.feet().stream()
            .allMatch(foot -> Math.max(Math.abs(foot.right() - pad.getX()), Math.abs(foot.astern() - pad.getZ())) >= CLEAR_OF_LEGS);
        return clear ? FreighterExterior.underneath(layout, pad.getX(), pad.getZ()) : null;
    }
}
