package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Modern cabin lighting: a flat round shade lit from below, at the end of a slim cable hung from the ceiling; as
 * bright as the flight deck's dimmer has it.
 */
public class PendantLightBlock extends Block {
    private static final MapCodec<PendantLightBlock> CODEC = simpleCodec(PendantLightBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(7.5, 8, 7.5, 8.5, 16, 8.5), Block.box(3, 4, 3, 13, 8, 13));

    public PendantLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(Brightness.PROPERTY, Brightness.BRIGHT).setValue(Brightness.ALARM, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Brightness.PROPERTY, Brightness.ALARM);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
