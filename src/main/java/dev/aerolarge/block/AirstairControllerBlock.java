package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The airstairs' control panel, set into the hull beside the forward door. Using it, pressing a button on either
 * face of it, or any redstone signal switching on folds the stairs away or brings them out again; see
 * {@link AirstairBlockEntity}. Broken, it takes the stairs away with it.
 */
public class AirstairControllerBlock extends BaseEntityBlock {
    private static final MapCodec<AirstairControllerBlock> CODEC = simpleCodec(AirstairControllerBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public AirstairControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            operate(level, pos, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Toggles on the rising edge only, so a button's one press is one fold or unfold. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
            if (powered && !level.isClientSide) {
                operate(level, pos, null);
            }
        }
    }

    /** Works the controller for the player using it, or for a redstone signal (no player). */
    protected void operate(Level level, BlockPos pos, @Nullable Player player) {
        toggle(level, pos);
    }

    static void toggle(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof AirstairControllerBlockEntity controller) {
            controller.toggle();
        }
    }

    /**
     * Taken apart on purpose, it takes its stairs away at once, steps, hatch and all; not when it is only moved, as
     * Sable moves it into an assembled airliner's plot and back, or it would shut its hatch where it stood.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !movedByPiston && !state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof AirstairBlockEntity airstairs) {
            airstairs.stowAway();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AirstairBlockEntity(pos, state);
    }

    /** The server runs the stairs; every client runs the same clock, to draw them moving in between. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
            ? createTickerHelper(type, AerolargeRegistry.AIRSTAIR_CONTROLLER_ENTITY.get(), AirstairBlockEntity::clientTick)
            : createTickerHelper(type, AerolargeRegistry.AIRSTAIR_CONTROLLER_ENTITY.get(), AirstairControllerBlockEntity::serverTick);
    }
}
