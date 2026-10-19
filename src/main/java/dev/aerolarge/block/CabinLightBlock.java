package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** A cabin light set into the crown, the walls or the hold, as bright as the flight deck's dimmer has it. */
public class CabinLightBlock extends Block {
    private static final MapCodec<CabinLightBlock> CODEC = simpleCodec(CabinLightBlock::new);

    public CabinLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(Brightness.PROPERTY, Brightness.BRIGHT));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Brightness.PROPERTY);
    }
}
