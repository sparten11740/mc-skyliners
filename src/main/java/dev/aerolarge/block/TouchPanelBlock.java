package dev.aerolarge.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A block that is, or is part of, a touch panel (see {@link TouchPanel}): it says which of its tiles a hit lands on, the
 * same on the server, where a touch works it, and on a client, where the tile under the crosshair lights up, and the
 * game's own outline round the block is left off it.
 */
public interface TouchPanelBlock {
    /**
     * The tile of the panel a hit on one of its blocks lands on, the player looking along a direction turned into the
     * panel's frame (which only a tilted face needs); -1 for none.
     */
    int tileAt(BlockGetter level, BlockState state, BlockPos pos, BlockHitResult hit, Vec3 look);

    /** The block whose block entity draws the panel, from any of its blocks. */
    default BlockPos panelOrigin(BlockState state, BlockPos pos) {
        return pos;
    }
}
