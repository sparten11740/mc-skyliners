package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** The flight deck's instrument panel below the windscreen, its screens facing the way FACING points. */
public class InstrumentPanelBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<InstrumentPanelBlock> CODEC = simpleCodec(InstrumentPanelBlock::new);

    public InstrumentPanelBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
