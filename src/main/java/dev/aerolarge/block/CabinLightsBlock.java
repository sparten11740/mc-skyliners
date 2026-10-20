package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * The cabin lights' dimmer on the flight deck: each use steps the whole cabin from bright to dimmed (as for take-off
 * and landing) to the low glow of a night flight and back, every light that has a {@link Brightness}.
 */
public class CabinLightsBlock extends CabinControlBlock {
    private static final MapCodec<CabinLightsBlock> CODEC = simpleCodec(CabinLightsBlock::new);

    public CabinLightsBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(Brightness.PROPERTY, Brightness.BRIGHT));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, Brightness.PROPERTY);
    }

    @Override
    protected BlockState next(BlockState control) {
        return control.setValue(Brightness.PROPERTY, control.getValue(Brightness.PROPERTY).next());
    }

    @Override
    protected Component describe(BlockState control) {
        return Component.translatable("message.aerolarge.cabin_lights." + control.getValue(Brightness.PROPERTY).getSerializedName());
    }

    @Override
    public boolean works(BlockState target) {
        return target.hasProperty(Brightness.PROPERTY);
    }

    @Override
    public BlockState applyTo(BlockState control, BlockState target) {
        return works(target) ? target.setValue(Brightness.PROPERTY, control.getValue(Brightness.PROPERTY)) : target;
    }
}
