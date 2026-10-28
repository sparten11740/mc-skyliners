package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The livery selector's block entity, in the part holding the selector: it holds nothing, but its renderer draws the
 * liveries' pictures, the gold frame round the one the zeppelin wears and, while she is being repainted, the painters'
 * cradles the new paint sweeps along behind (see the client's {@code LiverySelectorRenderer}).
 */
public class LiverySelectorBlockEntity extends BlockEntity {
    public LiverySelectorBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.LIVERY_SELECTOR_ENTITY.get(), pos, state);
    }
}
