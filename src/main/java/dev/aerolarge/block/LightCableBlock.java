package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The slim cable a pendant light hangs from, a block of it at a time. */
public class LightCableBlock extends Block {
    private static final MapCodec<LightCableBlock> CODEC = simpleCodec(LightCableBlock::new);
    private static final VoxelShape SHAPE = Block.box(7.5, 0, 7.5, 8.5, 16, 8.5);

    public LightCableBlock(Properties properties) {
        super(properties);
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
