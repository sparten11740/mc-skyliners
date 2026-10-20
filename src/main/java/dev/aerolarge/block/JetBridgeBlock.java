package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * The jet bridge, for the terminal: a plate set into the floor at the mouth of the bridge, FACING the way it reaches
 * out. Using it, or a redstone signal, sends the bridge out to an airliner door lined up ahead of it, or folds it back;
 * see {@link JetBridgeBlockEntity}. Broken, it takes the bridge away with it.
 */
public class JetBridgeBlock extends AirstairControllerBlock {
    private static final MapCodec<JetBridgeBlock> CODEC = simpleCodec(JetBridgeBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public JetBridgeBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    /** Facing the way the player placing it looks: out toward the apron. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
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
        return level.isClientSide ? null : createTickerHelper(type, AerolargeRegistry.JET_BRIDGE_ENTITY.get(), AirstairControllerBlockEntity::serverTick);
    }
}
