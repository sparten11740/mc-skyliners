package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The tail camera display on the flight deck, like an A380's taxiing camera screen: a wide screen (see
 * {@link WideScreenBlock}) for the picture from the camera on top of the fin, looking forward over the whole airliner;
 * its renderer draws it all, upright or hanging tilted from the crown.
 */
public class TailCameraDisplayBlock extends WideScreenBlock {
    private static final MapCodec<TailCameraDisplayBlock> CODEC = simpleCodec(TailCameraDisplayBlock::new);

    public TailCameraDisplayBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntity newScreen(BlockPos pos, BlockState state) {
        return new TailCameraDisplayBlockEntity(pos, state);
    }
}
