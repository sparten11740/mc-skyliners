package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.airframe.Livery;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.Craft;
import dev.aerolarge.compat.SableFlight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The livery selector in a zeppelin's control car or a rocket's cockpit, under her camera display: a wooden board four
 * blocks wide (a {@link WideScreenBlock}) bearing her six liveries in two rows of three (see {@link Livery#swatches}),
 * each as a picture of her side on, painted in it as she would be, so a livery is seen before it is picked. It is a
 * touch panel (see {@link TouchPanel}), its pictures its tiles: the client's {@code LiverySelectorRenderer} draws
 * them, the one she wears marked as picked and the one looked at lit up. Use a picture and she is repainted in it, as
 * from the livery board.
 */
public class LiverySelectorBlock extends WideScreenBlock implements TouchPanelBlock {
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
            int picture = tileAt(level, state, pos, hit, SableFlight.turnToPlot(level, pos, player.getLookAngle()));
            AutopilotBlockEntity painted = LiveryBoardBlock.painted(server, pos);
            LiveryBoardBlock.repaint(server, pos, player, Livery.swatch(painted == null ? Craft.ZEPPELIN : painted.craft(), picture));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockPos panelOrigin(BlockState state, BlockPos pos) {
        return origin(state, pos);
    }

    /** The picture a hit on the selector lands on, by its place in {@link #pictures}; -1 off them. */
    @Override
    public int tileAt(BlockGetter level, BlockState state, BlockPos pos, BlockHitResult hit, Vec3 look) {
        return pictures(rowHeight(state)).at(touched(state, pos, hit, look));
    }

    /**
     * The liveries' pictures on a face this tall, in blocks from its bottom left corner (see {@link TouchPanel}): two
     * rows of three within the margin, a gap between each two, in the order {@link Livery#swatch} counts them. Laid out
     * as a grid over the face grown by half a gap all round, each picture is its cell less half a gap all round.
     */
    public static TouchPanel.Layout pictures(double height) {
        TouchPanel.Tile area = new TouchPanel.Tile(MARGIN - GAP / 2, MARGIN - GAP / 2, SELECTOR_COLUMNS - MARGIN + GAP / 2,
            height - MARGIN + GAP / 2);
        TouchPanel.Layout cells = TouchPanel.Layout.grid(Livery.SWATCH_ROWS * Livery.SWATCH_COLUMNS, Livery.SWATCH_COLUMNS, area, true);
        return new TouchPanel.Layout(cells.tiles().stream().map(cell -> cell.inset(GAP / 2)).toList());
    }
}
