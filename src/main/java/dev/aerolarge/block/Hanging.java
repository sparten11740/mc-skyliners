package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What hangs from a ceiling (the pendant light, the gym rings, the disco ball and the girder decorations): put down by
 * a player only under something to hang from, the underside of a block clicked, or a solid block or a light cable over
 * it, never standing on the floor with its cable or ropes reaching up into the air. The blueprints hang them where they
 * hang them; the Halo Light takes a shape of its own for the floor (see {@code LightFittingItem}).
 */
public final class Hanging {
    private Hanging() {
    }

    /** Whether what a player puts down here would hang from something: a light cable, or a block over it to hold it. */
    static boolean hangs(BlockPlaceContext context) {
        BlockPos above = context.getClickedPos().above();
        return context.getLevel().getBlockState(above).is(AerolargeRegistry.LIGHT_CABLE.get()) || holds(context, Direction.UP);
    }

    /**
     * Whether the block the way given from where a player puts something down holds it there, its face toward it
     * firm enough to fix something to, as a torch's would be, or the skin of an airframe cut to its curve.
     */
    public static boolean holds(BlockPlaceContext context, Direction toward) {
        BlockPos behind = context.getClickedPos().relative(toward);
        BlockState support = context.getLevel().getBlockState(behind);
        return support.getBlock() instanceof ShellBlock
            || support.isFaceSturdy(context.getLevel(), behind, toward.getOpposite(), SupportType.CENTER);
    }

    /**
     * The way something fixed to a wall faces, out from the wall, where a player puts it: the nearest way they look
     * that has a wall holding it, the block clicked first; null where none does, as against a tuft of grass in the
     * open.
     */
    @Nullable
    public static Direction onWall(BlockPlaceContext context) {
        for (Direction toward : context.getNearestLookingDirections()) {
            if (toward.getAxis().isHorizontal() && holds(context, toward)) {
                return toward.getOpposite();
            }
        }
        return null;
    }
}
