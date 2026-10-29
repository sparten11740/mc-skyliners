package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where a player puts up a fitting that is fixed to something: under a ceiling, on a wall or on the floor (see
 * {@link Mount}), never with its cable, arm or stand reaching into the air. A fitting goes against the block the
 * nearest way the player looks that holds it, the block clicked first, so aiming at a tuft of grass in the open puts
 * nothing up beside it; a light cable over it, too thin to click under, holds it as a ceiling would. The blueprints
 * put their fittings where they put them.
 */
public final class Mounting {
    private Mounting() {
    }

    /**
     * The way from where a player puts a fitting up to what holds it there, for a fitting that takes the mounts given;
     * null where nothing does.
     */
    @Nullable
    public static Direction support(BlockPlaceContext context, Collection<Mount> mounts) {
        List<Direction> supports = supports(context, mounts);
        return supports.isEmpty() ? null : supports.getFirst();
    }

    /**
     * Every way from where a player puts a fitting up to something that would hold it there, for a fitting that takes
     * the mounts given, the nearest way they look first: for a fitting that has more than one shape to try, as the
     * Halo Light does when its first would not fit.
     */
    public static List<Direction> supports(BlockPlaceContext context, Collection<Mount> mounts) {
        List<Direction> supports = new ArrayList<>();
        for (Direction toward : context.getNearestLookingDirections()) {
            if (mounts.contains(Mount.against(toward)) && holds(context, toward)) {
                supports.add(toward);
            }
        }
        return supports;
    }

    /** Whether a light cable hangs over where a player puts something: a light hung there hangs from it. */
    public static boolean underCable(BlockPlaceContext context) {
        return context.getLevel().getBlockState(context.getClickedPos().above()).is(AerolargeRegistry.LIGHT_CABLE.get());
    }

    /** Whether what a player puts down here would hang from something: a light cable, or a block over it to hold it. */
    static boolean hangs(BlockPlaceContext context) {
        return support(context, EnumSet.of(Mount.CEILING)) != null;
    }

    /**
     * The way something fixed to a wall faces, out from the wall, where a player puts it; null where no wall holds it,
     * as against a tuft of grass in the open.
     */
    @Nullable
    static Direction onWall(BlockPlaceContext context) {
        Direction toward = support(context, EnumSet.of(Mount.WALL));
        return toward == null ? null : toward.getOpposite();
    }

    /**
     * Whether the block the way given from where a player puts something down holds it there, its face toward it
     * firm enough to fix something to, as a torch's would be, or the skin of an airframe cut to its curve; or, over
     * it, a light cable.
     */
    private static boolean holds(BlockPlaceContext context, Direction toward) {
        if (toward == Direction.UP && underCable(context)) {
            return true;
        }
        BlockPos behind = context.getClickedPos().relative(toward);
        BlockState support = context.getLevel().getBlockState(behind);
        return support.getBlock() instanceof ShellBlock
            || support.isFaceSturdy(context.getLevel(), behind, toward.getOpposite(), SupportType.CENTER);
    }
}
