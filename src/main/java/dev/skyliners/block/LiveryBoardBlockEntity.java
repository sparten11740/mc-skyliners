package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The livery board's block entity: it holds nothing, but its renderer draws the gold frame round the swatch the
 * zeppelin wears and, while the zeppelin is being repainted, the painters' cradle the new paint sweeps along behind
 * (see the client's {@code LiveryBoardRenderer}).
 */
public class LiveryBoardBlockEntity extends BlockEntity {
    public LiveryBoardBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.LIVERY_BOARD_ENTITY.get(), pos, state);
    }
}
