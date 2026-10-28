package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.compat.SableFlight;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The LED console in the rocket's cockpit, setting the strips round any of her stories or all of them (see
 * {@link LedStripBlock}): a screen of dark glass four blocks wide and two tall (a {@link WideScreenBlock}) standing on
 * the cockpit's floor against the glass. Along its top, her silhouette laid on her side, each story a band glowing as
 * its strips do; touch one to pick that story, or the button beside it with all of her lit to pick every one. Under
 * them the story panels' own controls (see {@link LedControls}) change what is picked, all of her a story at a time up
 * from her entry story. The client's {@code LedConsoleRenderer} draws it.
 */
public class LedConsoleBlock extends WideScreenBlock {
    private static final MapCodec<LedConsoleBlock> CODEC = simpleCodec(LedConsoleBlock::new);
    /** How many blocks wide and tall the console is. */
    public static final int CONSOLE_COLUMNS = 4;
    public static final int CONSOLE_ROWS = 2;

    public LedConsoleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected int columnsOf(BlockState state) {
        return CONSOLE_COLUMNS;
    }

    @Override
    protected int rowsOf(BlockState state) {
        return CONSOLE_ROWS;
    }

    @Override
    protected BlockEntity newScreen(BlockPos pos, BlockState state) {
        return new LedConsoleBlockEntity(pos, state);
    }

    /**
     * A touch on a control is the server's to make out, where the player looks from as the rocket lies. Its face is
     * drawn as a screen a row tall at its rows' scale, so the controls are laid out on it at that scale.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!onFace(state, hit.getDirection())) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(origin(state, pos)) instanceof LedConsoleBlockEntity console) {
            Touch touch = touched(state, pos, hit, SableFlight.turnToPlot(level, pos, player.getLookAngle()));
            double scale = rows(state);
            LedControls.Control control = touch == null ? null : LedControls.onConsole(touch.across() / scale, touch.up() / scale, console.spans());
            if (control != null) {
                console.touch(control);
                LedControls.sound(level, pos, control);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
