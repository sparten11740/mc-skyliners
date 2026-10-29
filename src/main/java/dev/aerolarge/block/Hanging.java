package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What hangs from a ceiling (the pendant light, the gym rings, the disco ball and the girder decorations): put down by
 * a player only under something to hang from, the underside of a block clicked, or a solid block or a light cable over
 * it, never standing on the floor with its cable or ropes reaching up into the air. The blueprints hang them where they
 * hang them; the Halo Light takes a shape of its own for the floor (see {@code LightFittingItem}).
 */
final class Hanging {
    private Hanging() {
    }

    /** Whether what a player puts down here would hang from something. */
    static boolean hangs(BlockPlaceContext context) {
        BlockPos above = context.getClickedPos().above();
        BlockState over = context.getLevel().getBlockState(above);
        return context.getClickedFace() == Direction.DOWN || over.is(AerolargeRegistry.LIGHT_CABLE.get())
            || over.isFaceSturdy(context.getLevel(), above, Direction.DOWN);
    }
}
