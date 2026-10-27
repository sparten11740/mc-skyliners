package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The trim winch under a zeppelin's gangway: an iron drum hung from the forward end of the trim rail, hauling the trim
 * car fore and aft along it (see {@link TrimWinchBlockEntity}), so the weight under the hull moves and the whole ship
 * pitches with it.
 */
public class TrimWinchBlock extends Block implements EntityBlock {
    private static final MapCodec<TrimWinchBlock> CODEC = simpleCodec(TrimWinchBlock::new);

    public TrimWinchBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrimWinchBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return !level.isClientSide && type == AerolargeRegistry.TRIM_WINCH_ENTITY.get()
            ? (tickLevel, pos, tickState, winch) -> ((TrimWinchBlockEntity) winch).serverTick((ServerLevel) tickLevel)
            : null;
    }
}
