package dev.skyliners.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A busy gadget's next step, taken where its scheduled tick was lost, as when the rocket it stands in is assembled,
 * which carries its blocks into her plot without their scheduled ticks: set again when it is used while busy, and, for
 * the fog and wave machines, looked for every so often by their block entities on the server while they are busy, so
 * that they never stay busy for good, still drawing their fog or their waves, until someone happens to use them.
 */
final class LostTicks {
    /** How often a busy gadget looks for its scheduled tick, in ticks: about once a second. */
    private static final int EVERY = 20;

    private LostTicks() {
    }

    /** Every so often, the gadget's next step on the next tick if none is scheduled. */
    static void resume(Level level, BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel server && server.getGameTime() % EVERY == 0) {
            rearm(server, pos, state.getBlock(), 1);
        }
    }

    /**
     * A gadget's next step so many ticks from now if none is scheduled: where it was lost, as a gadget used while
     * busy, or looked at now and then, sets it.
     */
    static void rearm(ServerLevel level, BlockPos pos, Block block, int delay) {
        if (!level.getBlockTicks().hasScheduledTick(pos, block)) {
            level.scheduleTick(pos, block, delay);
        }
    }
}
