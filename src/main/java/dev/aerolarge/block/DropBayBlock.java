package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The drop bay in the airliner's belly, under the hold: its doors, and what waits behind them to be dropped (see
 * {@link DropBayBlockEntity}). Use it from the hold, holding anything or nothing, to open its screen and load it or
 * take things back out. The drop release on the flight deck opens the doors and drops the loads.
 */
public class DropBayBlock extends BaseEntityBlock {
    private static final MapCodec<DropBayBlock> CODEC = simpleCodec(DropBayBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public DropBayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false));
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN);
    }

    /** Opens the bay's screen, with anything in hand or nothing: no item is used on the bay itself. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof DropBayBlockEntity bay) {
            player.openMenu(bay);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Taken apart, it spills whatever was still aboard; not when it is only moved, as Sable moves it into an assembled
     * airliner's plot and back, its load going with it.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving) {
            Containers.dropContentsOnDestroy(state, newState, level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DropBayBlockEntity(pos, state);
    }
}
