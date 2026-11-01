package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.autopilot.RocketControls;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A rocket's abort handle, before the commander on the centre console: a red T-handle behind its plate's red U-turn
 * arrow, which glows while pulling it would turn her round (READY), so it reads without a word. Pulled, it stands out a
 * moment (PULLED) as she calls off her landing and climbs back to hold her height, stops where she is and holds there,
 * or scrubs her countdown (see the autopilot's {@code rocketControl}); with nothing to call off, it only thuds.
 */
public class RocketAbortHandleBlock extends RocketControlBlock {
    private static final MapCodec<RocketAbortHandleBlock> CODEC = simpleCodec(RocketAbortHandleBlock::new);
    public static final BooleanProperty READY = BooleanProperty.create("ready");
    public static final BooleanProperty PULLED = BooleanProperty.create("pulled");

    public RocketAbortHandleBlock(Properties properties) {
        super(properties, ConsoleModule.behindPlate(4.5, 4));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(READY, false).setValue(PULLED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, READY, PULLED);
    }

    @Override
    RocketControls.Control control() {
        return RocketControls.Control.ABORT;
    }

    /** Pulled out a moment once a pull is taken; a refused one only thuds. */
    @Override
    boolean blinksAfter(boolean taken) {
        return taken;
    }

    @Override
    BlockState shown(BlockState state, RocketControls.Panel panel, boolean blinking) {
        return state.setValue(READY, panel.abortReady()).setValue(PULLED, blinking);
    }
}
