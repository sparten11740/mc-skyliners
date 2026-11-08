package dev.skyliners.autopilot;

import dev.skyliners.block.EngineGrilleBlock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The freighter's engine grille glowing with her engines (see {@link EngineGrilleBlock}), as her autopilot sets it from
 * how hard they fire as the clients are shown it: dark at rest, idling while her lift thrusters hold her up, and at
 * full once her mains push her ahead past {@link #FULL_FROM}, back to idling only once they drop under
 * {@link #IDLE_UNDER}. Each change sets every block of the grille afresh, and every client redraws her round them, so
 * it changes no more often than every {@link #HOLD} ticks but for going dark, which is at once.
 */
final class EngineGlow {
    /**
     * Her mains' share of full from which the grille glows at full, a step of it as the clients are shown it (see
     * FreighterFlight's SHOWN_STEPS), and under which it idles again: at full whenever her mains push her ahead.
     */
    private static final double FULL_FROM = 0.1;
    private static final double IDLE_UNDER = 0.05;
    /** How long the grille keeps a glow at least before it brightens or dims, in ticks. */
    private static final int HOLD = 40;
    /** Long before any game time: never. */
    private static final long NEVER = Long.MIN_VALUE / 2;

    /** How the grille was last set to glow; unknown until first set, which sets it whatever it was left at. */
    private int glow = -1;
    private long changedAt = NEVER;

    /** Each tick of hers: the grille's blocks, as offsets from her control head, set to glow as her engines fire. */
    void tick(ServerLevel level, BlockPos origin, List<BlockPos> grille, float lift, float mains, long now) {
        int wanted = wanted(lift, mains);
        boolean held = glow >= 0 && wanted != 0 && now - changedAt < HOLD;
        if (wanted == glow || held) {
            return;
        }
        glow = wanted;
        changedAt = now;
        for (BlockPos offset : grille) {
            light(level, origin.offset(offset));
        }
    }

    /** How the grille should glow now: dark at rest, at full while her mains push hard, idling otherwise. */
    private int wanted(float lift, float mains) {
        if (lift <= 0) {
            return 0;
        }
        boolean full = mains >= FULL_FROM || glow == 2 && mains >= IDLE_UNDER;
        return full ? 2 : 1;
    }

    /** One block of the grille set to glow as it should, if it is still one of hers and not one set there by hand. */
    private void light(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        boolean hers = state.getBlock() instanceof EngineGrilleBlock && !state.getValue(EngineGrilleBlock.BY_HAND);
        if (hers && state.getValue(EngineGrilleBlock.GLOW) != glow) {
            level.setBlock(pos, state.setValue(EngineGrilleBlock.GLOW, glow), Block.UPDATE_CLIENTS);
        }
    }
}
