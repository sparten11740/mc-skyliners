package dev.skyliners.airframe;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.Mount;
import dev.skyliners.block.SelfDestructButtonBlock;
import dev.skyliners.block.SelfDestructButtonBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The self-destruct button as a blueprint puts it up in a ship, whichever ship it is (see
 * {@link SelfDestructButtonBlock}): the block as it is laid, and its link to the ship's autopilot once it stands.
 */
final class SelfDestructFitting {
    private SelfDestructFitting() {
    }

    /** The button, fixed as given, its cover shut, facing the way whoever presses it faces. */
    static BlockState button(Mount mount, Direction facing) {
        return SkylinersRegistry.SELF_DESTRUCT_BUTTON.get().defaultBlockState().setValue(SelfDestructButtonBlock.MOUNT, mount)
            .setValue(SelfDestructButtonBlock.FACING, facing);
    }

    /** Links the button at a place in the layout to the ship's autopilot, the offset turned as the ship is placed. */
    static Fitting linkedTo(BlockPos button, BlockPos autopilot) {
        BlockPos offset = autopilot.subtract(button);
        return (blockEntity, rotation) -> link(blockEntity, offset.rotate(rotation));
    }

    private static void link(BlockEntity blockEntity, BlockPos offset) {
        if (blockEntity instanceof SelfDestructButtonBlockEntity button) {
            button.configure(offset);
        }
    }
}
