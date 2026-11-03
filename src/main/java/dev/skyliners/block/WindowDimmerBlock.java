package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.compat.SableFlight;
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
 * The window dimmer on each of the rocket's stories, beside its lift door over the story's LED panel (see
 * {@code airframe.RocketWindowDimmers}), which sets the glass of every oval window of the story at once (see
 * {@link OvalWindowPieceBlock}): a block of the LED panel's dark glass on the shaft's wall (a {@link WideScreenBlock} a
 * block square), a touch panel (see {@link TouchPanel}) of four big buttons, each the oval window in its steel rim that
 * the cockpit's LED console has for each darkness (see {@link LedControls#WINDOWS}), the sky and the sun through it
 * dimming from one to the next: clear and light over medium and dark. Touch one and the story's windows take it at
 * once, each piece of every one, with the falling chime a window makes as it dims; the darkness it last set them to is
 * bordered in white (until it is first touched, the one they were built with), and the one the player looks at lights
 * up. Put up by hand it sets the oval windows round it (see {@link WindowDimmerBlockEntity#box}). The client's
 * {@code WindowDimmerRenderer} draws it.
 */
public class WindowDimmerBlock extends WideScreenBlock implements TouchPanelBlock {
    private static final MapCodec<WindowDimmerBlock> CODEC = simpleCodec(WindowDimmerBlock::new);
    /**
     * Where its buttons lie on its face, in blocks from its bottom left corner as the viewer sees it: the outer edges
     * of the four, as the LED panel's controls lie on its face, two rows of two from the top left, each button's tile
     * its darkness, clear first. Each tile takes in the half gap round its button, so a touch in the gap picks the
     * nearer.
     */
    private static final TouchPanel.Tile BUTTONS = new TouchPanel.Tile(0.07, 0.06, 0.93, 0.93);
    private static final int BUTTON_COLUMNS = 2;
    public static final TouchPanel.Layout TILES = TouchPanel.Layout.grid(OvalWindowPieceBlock.DARKEST + 1, BUTTON_COLUMNS,
        BUTTONS.inset(-LedControls.INSET), true);

    public WindowDimmerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected int columnsOf(BlockState state) {
        return 1;
    }

    @Override
    protected BlockEntity newScreen(BlockPos pos, BlockState state) {
        return new WindowDimmerBlockEntity(pos, state);
    }

    @Override
    public BlockPos panelOrigin(BlockState state, BlockPos pos) {
        return origin(state, pos);
    }

    /** The darkness whose button a hit on the dimmer's face lands on, clear ({@code 0}) to darkest; -1 for none. */
    @Override
    public int tileAt(BlockGetter level, BlockState state, BlockPos pos, BlockHitResult hit, Vec3 look) {
        return TILES.at(touched(state, pos, hit, look));
    }

    /** A touch on a button is the server's to make out, where the player looks from as the rocket lies. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!onFace(state, hit.getDirection())) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WindowDimmerBlockEntity dimmer) {
            int dim = tileAt(level, state, pos, hit, SableFlight.turnToPlot(level, pos, player.getLookAngle()));
            if (dim >= 0) {
                dimmer.dim(dim);
                OvalWindowPieceBlock.chime(level, pos, dim);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
