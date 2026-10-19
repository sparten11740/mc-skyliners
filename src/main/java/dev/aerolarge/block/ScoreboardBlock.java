package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A scoreboard for the court: home and guest, counting the baskets at the hoops near it (see {@link HoopBlock}).
 * Using it sounds the buzzer and starts a new game from nil all; sneaking, it starts hide and seek's countdown
 * instead, as the button by the hoop does (see {@link HideAndSeekButtonBlock}). FACING is the way its face looks.
 */
public class ScoreboardBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<ScoreboardBlock> CODEC = simpleCodec(ScoreboardBlock::new);

    public ScoreboardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ScoreboardBlockEntity board) {
            if (player.isShiftKeyDown()) {
                board.startCountdown();
            } else {
                board.reset();
                level.playSound(null, pos, AerolargeRegistry.SCOREBOARD_BUZZER.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                player.displayClientMessage(Component.translatable("message.aerolarge.scoreboard.reset"), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScoreboardBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof ScoreboardBlockEntity board) {
                board.tick();
            }
        };
    }
}
