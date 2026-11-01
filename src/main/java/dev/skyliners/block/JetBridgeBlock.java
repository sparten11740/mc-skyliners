package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The jet bridge, for the terminal: a plate set into the floor at the mouth of the bridge, FACING the way it reaches
 * out. Using it, or a redstone signal, sends the bridge out to an airliner door lined up ahead of it, or folds it back;
 * see {@link JetBridgeBlockEntity}. Broken, it takes the bridge away with it.
 */
public class JetBridgeBlock extends AirstairControllerBlock {
    private static final MapCodec<JetBridgeBlock> CODEC = simpleCodec(JetBridgeBlock::new);

    public JetBridgeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** Facing the way the player placing it looks: out toward the apron. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected void operate(Level level, BlockPos pos, @Nullable Player player) {
        if (level.getBlockEntity(pos) instanceof JetBridgeBlockEntity bridge) {
            bridge.operate(player);
        }
    }

    /** Taken apart, it folds its bridge away; not when it is only moved, as Sable moves blocks, its bridge going with it. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !movedByPiston && !state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof JetBridgeBlockEntity bridge) {
            bridge.foldAway();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new JetBridgeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, SkylinersRegistry.JET_BRIDGE_ENTITY.get(), AirstairControllerBlockEntity::serverTick);
    }
}
