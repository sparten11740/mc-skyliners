package dev.skyliners.stickers;

import com.mojang.serialization.MapCodec;
import dev.skyliners.block.WideScreenBlock;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The sticker map, an explorer's board four blocks wide and two tall (a {@link WideScreenBlock}, always upright): an
 * old parchment map in a dark wooden frame with a frame for each {@link Sticker} on it, three rows of six, an empty one
 * showing its picture as a grey shadow. Aboard a ship it fills as she lands, a sticker lighting up for each kind of
 * land she comes down in for the first time, and space as she climbs to its edge (see {@link StickerMapBlockEntity});
 * on the ground, or aboard a ship with no autopilot, using it puts up a sticker for each kind of land whoever uses it
 * has been to. Its renderer, the client's {@code StickerMapRenderer}, draws all of it from part 0.
 */
public class StickerMapBlock extends WideScreenBlock {
    private static final MapCodec<StickerMapBlock> CODEC = simpleCodec(StickerMapBlock::new);
    /** How many blocks wide and tall the board is. */
    public static final int BOARD_COLUMNS = 4;
    public static final int BOARD_ROWS = 2;

    public StickerMapBlock(Properties properties) {
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

    /**
     * It hangs upright wherever it is put, as a board more than a row tall does, even against a ceiling's underside.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placed = super.getStateForPlacement(context);
        return placed == null ? null : placed.setValue(TILTED, false);
    }

    @Override
    protected BlockEntity newScreen(BlockPos pos, BlockState state) {
        return new StickerMapBlockEntity(pos, state);
    }

    /** Used anywhere on it, the board as a whole answers (see {@link StickerMapBlockEntity#used}). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer
            && server.getBlockEntity(origin(state, pos)) instanceof StickerMapBlockEntity board) {
            board.used(server, serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || state.getValue(PART) != 0 ? null : (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof StickerMapBlockEntity board) {
                board.tick((ServerLevel) tickLevel);
            }
        };
    }
}
