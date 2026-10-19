package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A rubber duck's state on the client: when it last started wiggling, for its renderer, which draws the duck so it
 * can move.
 */
public class RubberDuckBlockEntity extends BlockEntity {
    /** The block event that sets a duck wiggling on every client in sight. */
    static final int WIGGLE = 1;
    /** Long enough ago that a wiggle from then is over, without overflowing when it is taken from the time now. */
    private static final long NEVER = -1_000_000;

    /** When the duck last started wiggling, in game ticks; only ever set on the client. */
    private long wiggled = NEVER;

    public RubberDuckBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.RUBBER_DUCK_ENTITY.get(), pos, state);
    }

    public long wiggled() {
        return wiggled;
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id != WIGGLE) {
            return super.triggerEvent(id, param);
        }
        if (level != null && level.isClientSide) {
            wiggled = level.getGameTime();
        }
        return true;
    }
}
