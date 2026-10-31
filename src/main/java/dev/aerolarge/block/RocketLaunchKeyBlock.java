package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.autopilot.RocketControls;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A rocket's launch key, before the pilot on the centre console: a key in its lock with an amber tag hanging from it,
 * a rocket on the tag, and a lamp beside it, amber while it stands off and green once turned. Turned on the ground with
 * her autopilot off, she flies what her screen last set; turned back during her countdown, she scrubs; once she has
 * lifted off it is stuck on and only thuds (see the autopilot's {@code rocketControl}). It stands turned (TURNED) for
 * as long as her autopilot flies her, and drops back off with a click as she lands.
 */
public class RocketLaunchKeyBlock extends RocketControlBlock {
    private static final MapCodec<RocketLaunchKeyBlock> CODEC = simpleCodec(RocketLaunchKeyBlock::new);
    public static final BooleanProperty TURNED = BooleanProperty.create("turned");
    /** How loud and how high the click of the key dropping back off is. */
    private static final float CLICK_VOLUME = 0.6F;
    private static final float CLICK_PITCH = 0.8F;

    public RocketLaunchKeyBlock(Properties properties) {
        super(properties, ConsoleModule.with(2.5, 4));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(TURNED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TURNED);
    }

    @Override
    RocketControls.Control control() {
        return RocketControls.Control.KEY;
    }

    /** It never blinks: a turn refused only thuds. */
    @Override
    boolean blinksAfter(boolean taken) {
        return false;
    }

    @Override
    BlockState shown(BlockState state, RocketControls.Panel panel, boolean blinking) {
        return state.setValue(TURNED, panel.flying());
    }

    /** Dropping back off as she lands, or as her countdown is scrubbed: a click. */
    @Override
    void changed(ServerLevel level, BlockPos pos, BlockState was, BlockState now) {
        if (was.getValue(TURNED) && !now.getValue(TURNED)) {
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, CLICK_VOLUME, CLICK_PITCH);
        }
    }
}
