package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * A button for the ground crew, on the nose gear: pressing it works the nearest airstair controller, so someone
 * standing under a closed-up airliner can call its stairs down. Like any button it also powers what it is on.
 */
public class AirstairCallButtonBlock extends ButtonBlock {
    /** Far enough to reach the forward door from the nose gear even on the largest airliner. */
    private static final int REACH = 32;

    public AirstairCallButtonBlock(BlockSetType type, Properties properties) {
        super(type, 20, properties);
    }

    @Override
    public void press(BlockState state, Level level, BlockPos pos, @Nullable Player player) {
        super.press(state, level, pos, player);
        if (!level.isClientSide) {
            BlockPos.findClosestMatch(pos, REACH, REACH, at -> level.getBlockState(at).is(AerolargeRegistry.AIRSTAIR_CONTROLLER.get()))
                .ifPresent(controller -> AirstairControllerBlock.toggle(level, controller));
        }
    }
}
