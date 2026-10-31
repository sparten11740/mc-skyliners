package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.autopilot.RocketControls;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A rocket's flight mode knob, at the pilot's inboard hand on the centre console: a chunky rotary knob with three
 * detents, a picture at each round it, so the mode reads without a word (see {@link RocketControls.Mode}): a blue
 * arrow straight up (VERTICAL), an orange arc (ARC, a gravity turn) and a green snail (GENTLE). Used, it turns a detent
 * on, and back sneaking; its pointer and the lamp by the picture it points at show the MODE. The lamp blinks red when
 * the mode it is turned to is refused (REFUSED).
 */
public class RocketModeKnobBlock extends RocketControlBlock {
    private static final MapCodec<RocketModeKnobBlock> CODEC = simpleCodec(RocketModeKnobBlock::new);
    public static final EnumProperty<RocketControls.Mode> MODE = EnumProperty.create("mode", RocketControls.Mode.class);
    public static final BooleanProperty REFUSED = BooleanProperty.create("refused");

    public RocketModeKnobBlock(Properties properties) {
        super(properties, ConsoleModule.with(3.5, 3));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(MODE, RocketControls.Mode.VERTICAL)
            .setValue(REFUSED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODE, REFUSED);
    }

    @Override
    RocketControls.Control control() {
        return RocketControls.Control.MODE;
    }

    /** Turned to the mode her autopilot has; linked to none, left as it stands. */
    @Override
    BlockState shown(BlockState state, RocketControls.Panel panel, boolean blinking) {
        BlockState turned = panel.rocket() ? state.setValue(MODE, panel.mode()) : state;
        return turned.setValue(REFUSED, blinking);
    }
}
