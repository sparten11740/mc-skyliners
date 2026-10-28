package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The machine of a ship's lift, a steel housing over the top of its glass shaft with the motor and the drum in it (see
 * {@link LiftMachineBlockEntity}, which runs the lift). Only the blueprints place it; broken, it gives back nothing.
 */
public class LiftMachineBlock extends BaseEntityBlock {
    private static final MapCodec<LiftMachineBlock> CODEC = simpleCodec(LiftMachineBlock::new);

    public LiftMachineBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LiftMachineBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != AerolargeRegistry.LIFT_MACHINE_ENTITY.get()) {
            return null;
        }
        return (world, pos, blockState, lift) -> LiftMachineBlockEntity.tick((ServerLevel) world, pos, (LiftMachineBlockEntity) lift);
    }
}
