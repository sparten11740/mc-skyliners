package dev.skyliners.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * The freighter's ramp call button: a lit push-button (see {@link LitButtonBlock}), its amber cap showing her ramp let
 * down from her belly, so it needs no reading. It stands low on her starboard leg, facing her ramp, to call it down
 * from the ground, and keeps nothing of its own: pressed, it finds her ramp through her frame (see
 * {@link RampPanelBlockEntity#call}) and brings it down, or folds it away if it is down; while she flies, or off a
 * freighter, it only sounds a low note.
 */
public class RampButtonBlock extends LitButtonBlock {
    public RampButtonBlock(BlockSetType type, Properties properties) {
        super(type, properties);
    }

    @Override
    public void press(BlockState state, Level level, BlockPos pos, @Nullable Player player) {
        super.press(state, level, pos, player);
        if (level instanceof ServerLevel server) {
            RampPanelBlockEntity.call(server, pos);
        }
    }
}
