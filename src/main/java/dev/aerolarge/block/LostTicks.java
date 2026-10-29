package dev.aerolarge.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A busy gadget's next step, taken at once where its scheduled tick was lost, as when the rocket it stands in is
 * assembled, which carries its blocks into her plot without their scheduled ticks: looked for every so often by the
 * gadget's block entity on the server while it is busy, so that it never stays busy for good, still drawing its fog or
 * its waves, until someone happens to use it again.
 */
final class LostTicks {
    /** How often a busy gadget looks for its scheduled tick, in ticks: about once a second. */
    private static final int EVERY = 20;

    private LostTicks() {
    }

    /** Every so often, the gadget's next step on the next tick if none is scheduled. */
    static void resume(Level level, BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel server && server.getGameTime() % EVERY == 0
            && !server.getBlockTicks().hasScheduledTick(pos, state.getBlock())) {
            server.scheduleTick(pos, state.getBlock(), 1);
        }
    }
}
