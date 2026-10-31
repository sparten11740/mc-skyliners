package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.compat.SableFlight;
import net.minecraft.core.BlockPos;
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
 * The LED console in the rocket's cockpit, setting the strips round any of her stories or all of them (see
 * {@link LedStripBlock}): a screen of dark glass four blocks wide and two tall (a {@link WideScreenBlock}) standing on
 * the cockpit's floor against the glass, and a touch panel (see {@link TouchPanel}). Along its top, her silhouette laid
 * on her side, each story a band glowing as its strips do; touch one to pick that story, or the button beside it with
 * all of her lit to pick every one. Under them the story panels' own controls (see {@link LedControls}) change what is
 * picked, all of her a story at a time up from her entry story. The one the player looks at lights up. The client's
 * {@code LedConsoleRenderer} draws it.
 */
public class LedConsoleBlock extends WideScreenBlock implements TouchPanelBlock {
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

    @Override
    public BlockPos panelOrigin(BlockState state, BlockPos pos) {
        return origin(state, pos);
    }

    /**
     * The tile a hit on the console lands on (see {@link LedControls#console}): its face is drawn as a screen a row
     * tall at its rows' scale, so its tiles are laid out on it at that scale, which the touch is turned into; -1 for
     * none.
     */
    @Override
    public int tileAt(BlockGetter level, BlockState state, BlockPos pos, BlockHitResult hit, Vec3 look) {
        Touch touch = touched(state, pos, hit, look);
        if (touch == null || !(level.getBlockEntity(origin(state, pos)) instanceof LedConsoleBlockEntity console)) {
            return -1;
        }
        double scale = rows(state);
        return console.layout().at(touch.across() / scale, touch.up() / scale);
    }

    /** A touch on a control is the server's to make out, where the player looks from as the rocket lies. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!onFace(state, hit.getDirection())) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(origin(state, pos)) instanceof LedConsoleBlockEntity console) {
            LedControls.Control control = LedControls.onConsole(tileAt(level, state, pos, hit, SableFlight.turnToPlot(level, pos, player.getLookAngle())));
            if (control != null) {
                console.touch(control);
                LedControls.sound(level, pos, control);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
