package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The top of one of the freighter's landing legs, set into her belly where the leg meets it: a housing of riveted
 * gunmetal ringed in hazard stripes, its socket seen from below, and the leg's hydraulics, which draw the leg up into
 * her belly and let it down again (see {@link LegBayBlockEntity}). Only her blueprint places it.
 */
public class LegBayBlock extends RetractableLegBlock {
    private static final MapCodec<LegBayBlock> CODEC = simpleCodec(LegBayBlock::new);

    public LegBayBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LegBayBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
            ? createTickerHelper(type, SkylinersRegistry.LEG_BAY_ENTITY.get(), RetractableLegBlockEntity::clientTick)
            : createTickerHelper(type, SkylinersRegistry.LEG_BAY_ENTITY.get(), RetractableLegBlockEntity::serverTick);
    }
}
