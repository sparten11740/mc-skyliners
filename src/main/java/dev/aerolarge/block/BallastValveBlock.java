package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The water ballast valve under a zeppelin's gangway at her bow: a brass wheel on the pipe from her bow's ballast bags
 * down through her belly. As she takes off from the ground her handlers hold her on their lines while it lets her
 * bow's water go, and let go together on the call "Up ship!" (see {@link BallastValveBlockEntity}).
 */
public class BallastValveBlock extends Block implements EntityBlock {
    private static final MapCodec<BallastValveBlock> CODEC = simpleCodec(BallastValveBlock::new);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);

    public BallastValveBlock(Properties properties) {
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BallastValveBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return !level.isClientSide && type == AerolargeRegistry.BALLAST_VALVE_ENTITY.get()
            ? (tickLevel, pos, tickState, valve) -> ((BallastValveBlockEntity) valve).serverTick((ServerLevel) tickLevel)
            : null;
    }
}
