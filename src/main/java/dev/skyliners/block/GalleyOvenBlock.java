package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A galley oven, the stainless kind built into an airliner's galley, where the meals are heated: a dark glass door with
 * a chrome handle under a strip of controls and status lights. It cooks as a smoker does, food only and twice as fast
 * as a furnace (see {@link GalleyOvenBlockEntity}); while it cooks, its window glows. FACING is the way its door faces.
 */
public class GalleyOvenBlock extends AbstractFurnaceBlock {
    private static final MapCodec<GalleyOvenBlock> CODEC = simpleCodec(GalleyOvenBlock::new);

    public GalleyOvenBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends AbstractFurnaceBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GalleyOvenBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createFurnaceTicker(level, type, SkylinersRegistry.GALLEY_OVEN_ENTITY.get());
    }

    @Override
    protected void openContainer(Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof GalleyOvenBlockEntity oven) {
            player.openMenu(oven);
            player.awardStat(Stats.INTERACT_WITH_SMOKER);
        }
    }
}
