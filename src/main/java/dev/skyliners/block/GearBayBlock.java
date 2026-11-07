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
 * The top of a retractable landing gear leg, where it meets the hull: the gear doors seen from below, and the leg's
 * hydraulics, which fold the leg up into the belly and lower it again (see {@link GearBayBlockEntity}). FACING is the
 * way the leg swings up: the nose gear forward, the main gear in toward the middle.
 */
public class GearBayBlock extends RetractableLegBlock {
    private static final MapCodec<GearBayBlock> CODEC = simpleCodec(GearBayBlock::new);

    public GearBayBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GearBayBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
            ? createTickerHelper(type, SkylinersRegistry.GEAR_BAY_ENTITY.get(), RetractableLegBlockEntity::clientTick)
            : createTickerHelper(type, SkylinersRegistry.GEAR_BAY_ENTITY.get(), RetractableLegBlockEntity::serverTick);
    }
}
