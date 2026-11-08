package dev.skyliners.repairs;

import com.mojang.serialization.MapCodec;
import dev.skyliners.block.WideScreenBlock;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The repair board of the freighter's running repairs, two blocks wide and one tall (a {@link WideScreenBlock}): a
 * dark steel board with a spanner clipped to its left end, two rows of five stars and a red warning lamp. Each fault
 * fixed near it lights the next star, five lit play a fanfare and ten a bigger one, and the lamp flashes while a fault
 * near it is still waiting (see {@link RepairBoardBlockEntity}, which also sets her faults off as she flies). Used with
 * no spanner about you, it hands you one off its clip. The client's {@code RepairBoardRenderer} draws all of it from
 * part 0.
 */
public class RepairBoardBlock extends WideScreenBlock {
    private static final MapCodec<RepairBoardBlock> CODEC = simpleCodec(RepairBoardBlock::new);
    /** How many blocks wide and tall the board is. */
    public static final int BOARD_COLUMNS = 2;
    public static final int BOARD_ROWS = 1;

    public RepairBoardBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected int columnsOf(BlockState state) {
        return BOARD_COLUMNS;
    }

    @Override
    protected int rowsOf(BlockState state) {
        return BOARD_ROWS;
    }

    @Override
    protected BlockEntity newScreen(BlockPos pos, BlockState state) {
        return new RepairBoardBlockEntity(pos, state);
    }

    /** Used anywhere on it, the board as a whole answers (see {@link RepairBoardBlockEntity#used}). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer
            && server.getBlockEntity(origin(state, pos)) instanceof RepairBoardBlockEntity board) {
            board.used(server, serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || state.getValue(PART) != 0 ? null : (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof RepairBoardBlockEntity board) {
                board.tick((ServerLevel) tickLevel);
            }
        };
    }
}
