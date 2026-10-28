package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.airframe.Livery;
import dev.aerolarge.block.AutopilotScreenBlockEntity.Box;
import dev.aerolarge.compat.SableFlight;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The livery selector in a zeppelin's control car, under her camera display: a wooden board four blocks wide (a
 * {@link WideScreenBlock}) bearing the livery board's six liveries in two rows of three (see
 * {@link LiveryBoardBlock#SWATCHES}), each as a picture of the zeppelin side on, painted in it as she would be, so a
 * livery is seen before it is picked; the client's {@code LiverySelectorRenderer} draws them and frames the one she
 * wears in gold. Use a picture and the zeppelin is repainted in it, as from the livery board.
 */
public class LiverySelectorBlock extends WideScreenBlock {
    private static final MapCodec<LiverySelectorBlock> CODEC = simpleCodec(LiverySelectorBlock::new);
    /** How many blocks wide the selector is. */
    public static final int SELECTOR_COLUMNS = 4;
    /** The margin round the pictures and the gap between two, in blocks on its face. */
    private static final double MARGIN = 0.06;
    private static final double GAP = 0.06;

    public LiverySelectorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected int columnsOf(BlockState state) {
        return SELECTOR_COLUMNS;
    }

    @Override
    protected BlockEntity newScreen(BlockPos pos, BlockState state) {
        return new LiverySelectorBlockEntity(pos, state);
    }

    /** Touching a picture is the server's to make out, where the player looks from as the zeppelin lies. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!onFace(state, hit.getDirection())) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            Touch touch = touched(state, pos, hit, SableFlight.turnToPlot(level, pos, player.getLookAngle()));
            if (touch != null) {
                LiveryBoardBlock.repaint(server, pos, player, swatch(touch.across(), touch.up(), rowHeight(state)));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Where a livery's picture lies on a face this tall, in blocks from its bottom left corner: in its row from the top
     * and its column from the left, as {@link LiveryBoardBlock#SWATCHES} lists them.
     */
    public static Box cell(int row, int column, double height) {
        int columns = LiveryBoardBlock.SWATCHES[0].length;
        int rows = LiveryBoardBlock.SWATCHES.length;
        double width = (SELECTOR_COLUMNS - 2 * MARGIN - (columns - 1) * GAP) / columns;
        double tall = (height - 2 * MARGIN - (rows - 1) * GAP) / rows;
        double left = MARGIN + column * (width + GAP);
        double top = height - MARGIN - row * (tall + GAP);
        return new Box(left, top - tall, left + width, top);
    }

    /** The livery whose picture is at a place on a face this tall; null between them. */
    @Nullable
    static Livery swatch(double across, double up, double height) {
        int columns = LiveryBoardBlock.SWATCHES[0].length;
        for (int k = 0; k < LiveryBoardBlock.SWATCHES.length * columns; k++) {
            if (cell(k / columns, k % columns, height).contains(across, up)) {
                return LiveryBoardBlock.SWATCHES[k / columns][k % columns];
            }
        }
        return null;
    }
}
