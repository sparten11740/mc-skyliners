package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.compat.SableFlight;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
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
 * The slim LED panel beside each of the rocket's lift doors, setting the strips round its story (see
 * {@link LedStripBlock}): a block of dark glass on the shaft's wall (a {@link WideScreenBlock} a block square), its
 * controls all pictures (see {@link LedControls}): the ten colours' swatches, four suns from dark to blazing for the
 * brightness, and four little strips doing what each mode does. Touch one and the story's strips change at once, with a
 * note or a click. The client's {@code LedPanelRenderer} draws it, and the story's strips as they animate.
 */
public class LedPanelBlock extends WideScreenBlock {
    private static final MapCodec<LedPanelBlock> CODEC = simpleCodec(LedPanelBlock::new);

    public LedPanelBlock(Properties properties) {
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
        return new LedPanelBlockEntity(pos, state);
    }

    /** A touch on a control is the server's to make out, where the player looks from as the rocket lies. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!onFace(state, hit.getDirection())) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof LedPanelBlockEntity panel) {
            Touch touch = touched(state, pos, hit, SableFlight.turnToPlot(level, pos, player.getLookAngle()));
            LedControls.Control control = touch == null ? null : LedControls.onPanel(touch.across(), touch.up());
            if (control != null) {
                panel.change(setting -> LedControls.changed(setting, control), 0);
                LedControls.sound(level, pos, control);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** On the server, a change the cockpit's console sent up the rocket is made once its time comes. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof LedPanelBlockEntity panel) {
                panel.tick();
            }
        };
    }
}
