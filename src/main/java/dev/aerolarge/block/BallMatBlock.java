package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A ball mat, as laid in a hold by its door and wherever a load is turned: a panel of steel ball-transfer units, each
 * a polished ball in a dark socket, on which a load slides any way at all. It is laid on the floor like a carpet, and
 * like the cargo floor's panels (see {@link CargoFloorBlock}) its balls stand a little proud of it.
 */
public class BallMatBlock extends CarpetBlock {
    private static final MapCodec<BallMatBlock> CODEC = simpleCodec(BallMatBlock::new);

    public BallMatBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends CarpetBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CargoFloorBlock.SHAPE;
    }
}
