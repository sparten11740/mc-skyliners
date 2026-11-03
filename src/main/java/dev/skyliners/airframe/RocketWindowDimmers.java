package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.OvalWindowPieceBlock;
import dev.skyliners.block.WindowDimmerBlock;
import dev.skyliners.block.WindowDimmerBlockEntity;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The window dimmer on each of the rocket's stories (see {@link WindowDimmerBlock}), on the outside of her lift shaft
 * beside the doorway, on the side away from the call button: just over the story's LED panel there (see
 * {@link RocketLedStrips}), so the room's two panels, its lights and its windows, stand one over the other by the door
 * everyone comes in by; or in the LED panel's place at eye height on a story with none. Each is told where its story's
 * oval windows are, the box round them, as two corners from it turned as she is placed. A story with no oval windows
 * has none, as the observation deck furnished, glass all round; nor has one where both its places are taken. Laid after
 * every other piece on her stories and only into open air, its back on the shaft's glass, it moves nothing and blocks
 * no way; it touches the glass face to face, so it is assembled with her.
 */
final class RocketWindowDimmers implements RocketPiece {
    /**
     * Where a story's dimmer stands, from her axis and the story's floor: beside the lift's doorway on its west side as
     * she is laid out, as the LED panel does (the call button has the east side), its back on the shaft's glass; at the
     * first row of these that is open, at eye height where the story has no LED panel there, else just over it.
     */
    private static final int DIMMER_X = -1;
    private static final int DIMMER_Z = -RocketLayout.SHAFT - 1;
    private static final int[] DIMMER_UP = {2, 3};
    /** How far out from her axis her windows may stand, past her body's radius, in blocks: her skin's own blocks. */
    private static final int SKIN_REACH = 2;

    private final RocketLayout layout;

    RocketWindowDimmers(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted() {
        return SkylinersConfig.ROCKET_WINDOW_DIMMERS.get();
    }

    @Override
    public void build() {
        List<Integer> floors = layout.floors();
        for (int story = 0; story < floors.size(); story++) {
            List<BlockPos> windows = windows(floors.get(story) + 1, layout.storyTop(story));
            BlockPos dimmer = place(floors.get(story));
            if (!windows.isEmpty() && dimmer != null) {
                dimmer(dimmer, windows);
            }
        }
    }

    /** Where a story's dimmer goes, the first of its places that is open; null where both are taken. */
    @Nullable
    private BlockPos place(int floor) {
        for (int up : DIMMER_UP) {
            if (layout.isOpen(DIMMER_X, floor + up, DIMMER_Z)) {
                return new BlockPos(DIMMER_X, floor + up, DIMMER_Z);
            }
        }
        return null;
    }

    /** Every block of oval window in a story's rows, from the lowest to the highest, round her out to her skin. */
    private List<BlockPos> windows(int lowest, int highest) {
        List<BlockPos> windows = new ArrayList<>();
        for (int y = lowest; y <= highest; y++) {
            windowsAt(y, windows);
        }
        return windows;
    }

    /** Every block of oval window in a row, added to the list. */
    private void windowsAt(int y, List<BlockPos> windows) {
        int out = (int) Math.ceil(layout.radius()) + SKIN_REACH;
        int across = 2 * out + 1;
        for (int block = 0; block < across * across; block++) {
            int x = block / across - out;
            int z = block % across - out;
            if (layout.stateAt(x, y, z).getBlock() instanceof OvalWindowPieceBlock) {
                windows.add(new BlockPos(x, y, z));
            }
        }
    }

    /**
     * A story's dimmer at a place facing out from the shaft, told once it stands the box round the story's windows, its
     * corners from it turned as she was placed, and how dark they were laid (see {@link #laid}).
     */
    private void dimmer(BlockPos at, List<BlockPos> windows) {
        BlockPos low = windows.getFirst();
        BlockPos high = windows.getFirst();
        for (BlockPos window : windows) {
            low = BlockPos.min(low, window);
            high = BlockPos.max(high, window);
        }
        layout.set(at.getX(), at.getY(), at.getZ(), SkylinersRegistry.WINDOW_DIMMER.get().defaultBlockState().setValue(WindowDimmerBlock.FACING, Direction.NORTH));
        BlockPos from = low.subtract(at);
        BlockPos to = high.subtract(at);
        int dim = laid(windows);
        layout.fit(at, (blockEntity, rotation) -> {
            if (blockEntity instanceof WindowDimmerBlockEntity dimmer) {
                dimmer.story(from.rotate(rotation), to.rotate(rotation), dim);
            }
        });
    }

    /**
     * How dark a story's windows were laid, so its dimmer shows it from the start: clear on most stories, darkest in
     * the club; {@link WindowDimmerBlockEntity#UNSET} where they differ.
     */
    private int laid(List<BlockPos> windows) {
        int dim = dimAt(windows.getFirst());
        for (BlockPos window : windows) {
            if (dimAt(window) != dim) {
                return WindowDimmerBlockEntity.UNSET;
            }
        }
        return dim;
    }

    /** How dark a block of oval window was laid. */
    private int dimAt(BlockPos window) {
        return layout.stateAt(window.getX(), window.getY(), window.getZ()).getValue(OvalWindowPieceBlock.DIM);
    }
}
